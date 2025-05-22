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

import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidRoboTest.ANDROID_ROBO_TEST_TEST_END_EPOCH_MS;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidRoboTest.ANDROID_ROBO_TEST_TEST_START_EPOCH_MS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test;
import com.google.devtools.mobileharness.platform.android.appcrawler.PostProcessor;
import com.google.devtools.mobileharness.platform.android.appcrawler.PreProcessor;
import com.google.devtools.mobileharness.platform.android.appcrawler.UtpBinariesExtractor;
import com.google.devtools.mobileharness.platform.android.appcrawler.UtpBinariesExtractor.UtpBinaries;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandTimeoutException;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.protobuf.ExtensionRegistry;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.TestAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

/** Driver for running Android Robo Tests using the UTP Android Robo Driver. */
@DriverAnnotation(
    help =
        "Driver to run Android Robo tests on real devices/emulators using "
            + "the UTP AndroidRoboDriver.")
@TestAnnotation(required = false, help = "Crawls the app. No specific test to execute.")
public class AndroidRoboTest extends BaseDriver implements SpecConfigable<AndroidRoboTestSpec> {

  private static final String MH_EXCEPTION_DETAIL_PROTO_FILE_NAME = "exception-detail.pb";

  private static final String ANDROID_ROBO_TEST_SPEC_PROTO_FILE_NAME = "android-robo-test-spec.pb";

  private static final Duration CLI_EXECUTION_PADDING_TIMEOUT = Duration.ofMinutes(6);

  private static final ImmutableMap<Integer, TestResult> EXIT_CODE_TO_TEST_RESULT_MAP =
      ImmutableMap.of(
          0, TestResult.PASS,
          1, TestResult.SKIP,
          2, TestResult.FAIL,
          3, TestResult.ERROR);
  private final Aapt aapt;
  private final Adb adb;
  private final PreProcessor preProcessor;

  private final UtpBinariesExtractor utpBinariesExtractor;
  private final CommandExecutor commandExecutor;
  private final PostProcessor postProcessor;
  private final Clock clock;

  @Inject
  AndroidRoboTest(
      Device device,
      TestInfo testInfo,
      Adb adb,
      Aapt aapt,
      Clock clock,
      PreProcessor preProcessor,
      UtpBinariesExtractor utpBinariesExtractor,
      CommandExecutor commandExecutor,
      PostProcessor postProcessor) {
    super(device, testInfo);
    this.aapt = aapt;
    this.adb = adb;
    this.clock = clock;
    this.preProcessor = preProcessor;
    this.utpBinariesExtractor = utpBinariesExtractor;
    this.commandExecutor = commandExecutor;
    this.postProcessor = postProcessor;
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Running Android Robo Driver on %s.", this.getDevice().getDeviceId());
    testInfo.log().atInfo().alsoTo(logger).log("Job Info: %s", this.getTest().jobInfo());

    AndroidRoboTestSpec spec = testInfo.jobInfo().combinedSpec(this);
    testInfo.log().atInfo().alsoTo(logger).log("\n\nAndroid Robo Test Spec: \n\n%s", spec);
    UtpBinaries utpBinaries = utpBinariesExtractor.setUpUtpBinaries();
    preProcessor.installApks(testInfo, getDevice(), spec, utpBinaries.stubAppPath());

    TestResult result = runCli(testInfo, utpBinaries, spec);

    setResult(testInfo, result);
    postProcessor.uninstallApks(testInfo, getDevice(), spec);
  }

  private static int pickUnusedPort() throws InterruptedException, MobileHarnessException {
    try {
      return PortProber.pickUnusedPort();
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ROBO_TEST_FREE_PORT_UNAVAILABLE, "Unable to find unused port", e);
    }
  }

  private String setUpAndroidRoboTestSpecProtoFile(TestInfo testInfo, AndroidRoboTestSpec spec)
      throws MobileHarnessException {
    Path specPath = Path.of(testInfo.getGenFileDir(), ANDROID_ROBO_TEST_SPEC_PROTO_FILE_NAME);
    try {
      Files.write(specPath, spec.toByteArray());
      return specPath.toString();
    } catch (IOException ex) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ROBO_TEST_MH_ROBO_TEST_SPEC_WRITE_ERROR,
          "Unable to write android robo test spec proto.",
          ex);
    }
  }

  private TestResult runCli(TestInfo testInfo, UtpBinaries utpBinaries, AndroidRoboTestSpec spec)
      throws MobileHarnessException, InterruptedException {
    CommandProcess commandProcess = null;
    try {
      SystemUtil systemUtil = new SystemUtil();
      String javaBinary = systemUtil.getJavaBin();
      ImmutableList.Builder<String> argsBuilder = ImmutableList.builder();
      argsBuilder.add(
          "-jar",
          utpBinaries.cliPath(),
          "--test-id",
          testInfo.locator().getId(),
          "--utp-launcher-path",
          utpBinaries.launcherPath(),
          "--utp-main-path",
          utpBinaries.mainPath(),
          "--utp-device-provider-path",
          utpBinaries.providerPath(),
          "--utp-robo-driver-path",
          utpBinaries.driverPath(),
          "--gen-files-dir",
          testInfo.getGenFileDir(),
          "--tmp-dir",
          testInfo.getTmpFileDir(),
          "--device-id",
          getDevice().getDeviceId(),
          "--adb-path",
          adb.getAdbPath(),
          "--adb-server-port",
          String.valueOf(adb.getAdbServerPort()),
          "--adb-server-host",
          adb.getAdbServerHost(),
          "--aapt-path",
          aapt.getAaptPath(),
          "--proxy-port",
          String.valueOf(pickUnusedPort()),
          "--robo-spec-path",
          setUpAndroidRoboTestSpecProtoFile(testInfo, spec));
      Path outputDir = Path.of(testInfo.getGenFileDir());
      try (BufferedWriter stdoutWriter =
          Files.newBufferedWriter(outputDir.resolve("cli-stdout.log"))) {
        var timeout =
            Duration.ofSeconds(spec.getCrawlTimeoutSecs()).plus(CLI_EXECUTION_PADDING_TIMEOUT);
        Command command =
            Command.of(javaBinary, argsBuilder.build())
                // Allowed Exit codes for CLI.
                // 0 -> PASS, 1 -> SKIP, 2 -> FAIL, 3 -> ERROR
                .successExitCodes(0, 1, 2, 3)
                .redirectStderr(true)
                .onStdout(LineCallback.writeTo(stdoutWriter))
                .timeout(timeout); // Set it here explicitly otherwise the default in the command
        // executor will be used which is 5 minutes.
        logger.atInfo().log("Command: %s", command);
        testInfo
            .properties()
            .add(
                ANDROID_ROBO_TEST_TEST_START_EPOCH_MS,
                Long.toString(clock.instant().toEpochMilli()));

        commandProcess = commandExecutor.start(command);
        var commandResult = commandProcess.await(timeout);
        return EXIT_CODE_TO_TEST_RESULT_MAP.getOrDefault(
            commandResult.exitCode(), TestResult.ERROR);
      }
    } catch (TimeoutException | CommandTimeoutException tex) {
      // Explicitly kill the process if command times out.
      if (commandProcess != null) {
        commandProcess.killAndThenKillForcibly(Duration.ofSeconds(30));
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ROBO_TEST_COMMAND_EXECUTION_ERROR, "Robo Cli timed out.", tex);
    } catch (CommandException cex) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ROBO_TEST_COMMAND_EXECUTION_ERROR, "Failed to run Robo Cli.", cex);
    } catch (IOException ioex) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ROBO_TEST_UTP_LOG_WRITER_ERROR,
          "Failed to write output files while running CLI.",
          ioex);
    } catch (InterruptedException iex) {
      Thread.currentThread().interrupt();
      // Explicitly kill the process if thread interrupted.
      if (commandProcess != null) {
        commandProcess.killAndThenKillForcibly(Duration.ofSeconds(30));
      }
      throw iex;
    } finally {
      testInfo
          .properties()
          .add(ANDROID_ROBO_TEST_TEST_END_EPOCH_MS, Long.toString(clock.instant().toEpochMilli()));
    }
  }

  private void setResult(TestInfo testInfo, TestResult result) throws MobileHarnessException {
    if (!result.equals(TestResult.ERROR)) {
      testInfo.result().set(result);
      return;
    }
    // If Error, read the exception detail proto.
    Path exceptionDetailProtoPath =
        Path.of(testInfo.getGenFileDir(), MH_EXCEPTION_DETAIL_PROTO_FILE_NAME);
    try {
      var exceptionDetail =
          ExceptionDetail.parseFrom(
              Files.readAllBytes(exceptionDetailProtoPath), ExtensionRegistry.getEmptyRegistry());
      testInfo.resultWithCause().setNonPassing(Test.TestResult.ERROR, exceptionDetail);
    } catch (IOException ex) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ROBO_TEST_MH_EXCEPTION_DETAIL_READ_ERROR,
          "Unable to read exception detail proto.",
          ex);
    }
  }
}
