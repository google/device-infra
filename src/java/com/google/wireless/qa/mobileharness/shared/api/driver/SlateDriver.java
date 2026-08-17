/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.CompositeDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.SlateDriverSpec;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javax.inject.Inject;

/** Driver for running SLATE tests. */
@DriverAnnotation(help = "Running SLATE tests.")
public class SlateDriver extends BaseDriver implements SpecConfigable<SlateDriverSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CommandExecutor cmdExecutor;
  private final LocalFileUtil localFileUtil;
  private final SystemUtil systemUtil;
  private final Adb adb;

  @Inject
  SlateDriver(
      Device device,
      TestInfo testInfo,
      CommandExecutor cmdExecutor,
      LocalFileUtil localFileUtil,
      SystemUtil systemUtil,
      Adb adb) {
    super(device, testInfo);
    this.cmdExecutor = cmdExecutor;
    this.localFileUtil = localFileUtil;
    this.systemUtil = systemUtil;
    this.adb = adb;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    SlateDriverSpec spec = testInfo.jobInfo().combinedSpec(this);
    testInfo.log().atInfo().alsoTo(logger).log(">>> SLATE DRIVER ACTIVATED <<<");

    // 1. Prepare Workspace
    String workDirName = String.format("SlateDriver_work_%s", testInfo.locator().getId());
    Path workDir = Path.of(testInfo.getTmpFileDir()).resolve(workDirName);
    localFileUtil.prepareDir(workDir.toString());

    // 2. Copy Binary
    String srcBinaryPathStr = spec.hasSlateBinary() ? spec.getSlateBinary().getOutput(0) : null;
    if (srcBinaryPathStr == null) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND,
          "Could not find SLATE binary. Please provide slate_binary in spec.");
    }
    Path srcBinaryPath = Path.of(srcBinaryPathStr);
    Path binaryPath = workDir.resolve(srcBinaryPath.getFileName());
    localFileUtil.copyFileOrDir(srcBinaryPath.toString(), binaryPath.toString());

    // Ensure executable
    binaryPath.toFile().setExecutable(true);

    // 3. Copy Config (Optional)
    String srcConfigPath = spec.hasSlateConfig() ? spec.getSlateConfig().getOutput(0) : null;
    String configPath = null;
    if (srcConfigPath != null) {
      Path destConfigPath = workDir.resolve("config.yaml");
      localFileUtil.copyFileOrDir(srcConfigPath, destConfigPath.toString());
      configPath = destConfigPath.toString();
    }

    ImmutableList<String> deviceSerials = getDeviceSerials();
    List<String> targets = spec.getTargetList();
    if (targets.isEmpty()) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_PARAM_VALUE_NOT_FOUND,
          "No targets specified in SlateDriverSpec. Please provide 'target' parameter.");
    }

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Preparing to run SLATE binary: %s on device %s with targets %s",
            binaryPath, deviceSerials, targets);

    // 4. Prepare Output Directory
    String genFileDir = testInfo.getGenFileDir();
    Path slateHistoryDir = Path.of(genFileDir, "slate_history");
    try {
      localFileUtil.prepareDir(slateHistoryDir.toString());
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("Failed to prepare slate history directory %s: %s", slateHistoryDir, e.getMessage());
    }

    // 5. Execute
    Path logFile = Path.of(genFileDir).resolve("slate_run.log");
    try (BufferedWriter writer =
        Files.newBufferedWriter(logFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
      testInfo.log().atInfo().alsoTo(logger).log("SlateDriver: Writing logs to %s", logFile);

      ImmutableList.Builder<String> args = ImmutableList.builder();
      args.add("--target").addAll(targets).add("--device").addAll(deviceSerials);
      if (configPath != null) {
        args.add("--config", configPath);
      }
      args.add("--output_base_dir", slateHistoryDir.toString());
      ImmutableList<String> argsList = args.build();

      int timeoutMins = spec.getTimeoutMins() > 0 ? spec.getTimeoutMins() : 60;
      Duration timeout = Duration.ofMinutes(timeoutMins);

      ImmutableMap.Builder<String, String> envVars = ImmutableMap.<String, String>builder();
      String envPath = getEnvPath();
      if (!envPath.isEmpty()) {
        envVars.put("PATH", envPath);
      }
      envVars.put("PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION", "python");

      Command command =
          Command.of(binaryPath.toString())
              .args(argsList)
              .timeout(timeout)
              .redirectStderr(true)
              .workDir(workDir)
              .extraEnv(envVars.buildOrThrow())
              .onStdout(
                  LineCallback.does(
                      line -> {
                        testInfo.log().atInfo().alsoTo(logger).log("Slate Output: %s", line);
                        try {
                          writer.write(line);
                          writer.newLine();
                          writer.flush();
                        } catch (IOException e) {
                          testInfo
                              .log()
                              .atWarning()
                              .alsoTo(logger)
                              .log("Failed to write to log file: %s", e.getMessage());
                        }
                      }));

      testInfo.log().atInfo().alsoTo(logger).log("SlateDriver: Executing command: %s", command);

      int exitCode = cmdExecutor.exec(command).exitCode();

      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("SlateDriver: Execution finished with exit code %d", exitCode);

      // 6. Set Result
      if (exitCode == 0) {
        testInfo.resultWithCause().setPass();
      } else {
        testInfo
            .log()
            .at(Level.SEVERE)
            .alsoTo(logger)
            .log("Slate binary failed. Full log at %s", logFile);
        testInfo
            .resultWithCause()
            .setNonPassing(
                TestResult.FAIL,
                new MobileHarnessException(
                    BasicErrorId.COMMAND_EXEC_FAIL,
                    "Slate binary failed with exit code "
                        + exitCode
                        + ". Check logs at: "
                        + logFile));
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.COMMAND_EXEC_FAIL, "Failed to execute SLATE binary due to IO error", e);
    }
  }

  private String getEnvPath() {
    ImmutableSet.Builder<String> paths = new ImmutableSet.Builder<>();
    getSdkToolDir(adb.getAdbPath()).ifPresent(paths::add);
    Optional.ofNullable(systemUtil.getEnv("PATH")).ifPresent(paths::add);
    return Joiner.on(':').join(paths.build());
  }

  private static Optional<String> getSdkToolDir(String sdkToolPath) {
    if (Strings.isNullOrEmpty(sdkToolPath)) {
      return Optional.empty();
    }
    return Optional.ofNullable(new File(sdkToolPath).getParent());
  }

  private ImmutableList<String> getDeviceSerials() {
    Device device = getDevice();
    if (device instanceof CompositeDevice compositeDevice) {
      return compositeDevice.getManagedDevices().stream()
          .map(this::getDeviceSerial)
          .collect(toImmutableList());
    }
    return ImmutableList.of(getDeviceSerial(device));
  }

  private String getDeviceSerial(Device device) {
    String id = device.getDeviceId();
    if (id.startsWith(AndroidAdbInternalUtil.OUTPUT_USB_ID_TOKEN)) {
      List<String> serials = device.getDimension("serial");
      if (!serials.isEmpty()) {
        id = serials.get(0);
      }
    }
    return id;
  }
}
