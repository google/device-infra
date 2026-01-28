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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Driver for running SLATE tests. */
@DriverAnnotation(help = "Running SLATE tests.")
public class SlateDriver extends BaseDriver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration ZIP_UNCOMPRESS_TIMEOUT = Duration.ofHours(2L);

  @FileAnnotation(required = true, help = "The zip file containing the slate binary.")
  public static final String FILE_SLATE_ZIP = "slate_zip";

  @ParamAnnotation(required = true, help = "The target task to run in SLATE.")
  public static final String PARAM_TARGET = "target";

  @ParamAnnotation(required = false, help = "The root directory of the unzipped SLATE binary.")
  public static final String PARAM_SLATE_ROOT_DIR = "slate_root_dir";

  private final CommandExecutor cmdExecutor;
  private final LocalFileUtil localFileUtil;
  private final Clock clock;

  @Inject
  SlateDriver(
      Device device,
      TestInfo testInfo,
      CommandExecutor cmdExecutor,
      LocalFileUtil localFileUtil,
      Clock clock) {
    super(device, testInfo);
    this.cmdExecutor = cmdExecutor;
    this.localFileUtil = localFileUtil;
    this.clock = clock;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    logger.atInfo().log(">>> SLATE DRIVER ACTIVATED <<<");

    // Ensure the zip is unzipped
    Path unzippedDir = getSlateRootDir(testInfo);
    logger.atInfo().log("SlateDriver: Unzipped dir: %s", unzippedDir);

    // 1. Resolve the binary path.
    Optional<Path> runScript = resolveBinaryPath(unzippedDir);
    if (runScript.isEmpty()) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND,
          "Could not find slate_binary/run.sh or run script in " + unzippedDir);
    }

    Path binaryPath = runScript.get();

    // Ensure executable
    binaryPath.toFile().setExecutable(true);

    String deviceSerial = getDevice().getDeviceId();
    String target = testInfo.jobInfo().params().get(PARAM_TARGET);

    logger.atInfo().log(
        "Preparing to run SLATE binary: %s on device %s with target %s",
        binaryPath, deviceSerial, target);

    // 2. Prepare Output Directory
    String logDirName = String.format("SlateDriver_test_%s", testInfo.locator().getId());
    Path outputDir = Path.of(testInfo.getGenFileDir()).resolve(logDirName);
    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      logger.atWarning().log("Failed to create log directory: %s", e.getMessage());
    }

    // 3. Execute
    Path logFile = outputDir.resolve("slate_run.log");
    try (BufferedWriter writer =
        Files.newBufferedWriter(logFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
      logger.atInfo().log("SlateDriver: Writing logs to %s", logFile);
      logger.atInfo().log("SlateDriver: Executing command: %s", binaryPath);

      StringBuilder logBuilder = new StringBuilder();
      int exitCode =
          cmdExecutor
              .exec(
                  Command.of(binaryPath.toString(), "--target", target, "--d", deviceSerial)
                      .timeout(Duration.ofMinutes(60))
                      .redirectStderr(true)
                      .workDir(binaryPath.getParent())
                      .extraEnv("PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION", "python")
                      .onStdout(
                          LineCallback.does(
                              line -> {
                                logBuilder.append(line).append("\n");
                                try {
                                  writer.write(line);
                                  writer.newLine();
                                } catch (IOException e) {
                                  logger.atWarning().log(
                                      "Failed to write to log file: %s", e.getMessage());
                                }
                              })))
              .exitCode();

      logger.atInfo().log("SlateDriver: Execution finished with exit code %d", exitCode);

      // 4. Set Result
      if (exitCode == 0) {
        testInfo.resultWithCause().setPass();
      } else {
        logger.atSevere().log("Slate binary failed. Output:\n%s", logBuilder);
        testInfo
            .resultWithCause()
            .setNonPassing(
                TestResult.FAIL,
                new MobileHarnessException(
                    BasicErrorId.COMMAND_EXEC_FAIL,
                    "Slate binary failed with exit code " + exitCode + ". Output:\n" + logBuilder));
      }
    } catch (InterruptedException e) {
      throw e;
    } catch (Exception e) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.ERROR,
              new MobileHarnessException(
                  BasicErrorId.COMMAND_EXEC_FAIL, "Failed to execute SLATE binary", e));
    }
  }

  private Path getSlateRootDir(TestInfo testInfo) throws MobileHarnessException {
    String rootDir = testInfo.jobInfo().params().get(PARAM_SLATE_ROOT_DIR);
    if (rootDir != null) {
      return Path.of(rootDir);
    } else {
      String zipPath = testInfo.jobInfo().files().getSingle(FILE_SLATE_ZIP);
      if (zipPath != null) {
        Path slateZip = Path.of(zipPath);
        long startTime = clock.instant().toEpochMilli();
        try {
          String unzippedPath =
              PathUtil.join(
                  testInfo.getTmpFileDir(), slateZip.toString().replace('.', '_') + "_unzipped");
          localFileUtil.prepareDir(unzippedPath);
          localFileUtil.unzipFile(slateZip.toString(), unzippedPath, ZIP_UNCOMPRESS_TIMEOUT);
          return Path.of(unzippedPath);
        } catch (MobileHarnessException | InterruptedException e) {
          if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
          }
          throw new MobileHarnessException(
              BasicErrorId.LOCAL_FILE_UNZIP_ERROR, "Failed to unzip " + slateZip, e);
        } finally {
          logger.atInfo().log(
              "Unzipping %s took %d seconds",
              slateZip,
              Duration.between(Instant.ofEpochMilli(startTime), clock.instant()).toSeconds());
        }
      }
    }
    throw new MobileHarnessException(
        BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND,
        "Failed to get the slate root dir. Please provide "
            + FILE_SLATE_ZIP
            + " or "
            + PARAM_SLATE_ROOT_DIR);
  }

  private Optional<Path> resolveBinaryPath(Path unzippedDir) {
    Path runScript = unzippedDir.resolve("slate_binary/run.sh");
    if (!Files.exists(runScript)) {
      runScript = unzippedDir.resolve("slate_binary/run");
    }

    if (!Files.exists(runScript)) {
      logger.atInfo().log("SlateDriver: Binary not found at root paths, searching recursively...");
      try (Stream<Path> stream = Files.walk(unzippedDir)) {
        return stream
            .filter(
                p ->
                    p.endsWith(Path.of("slate_binary", "run.sh"))
                        || p.endsWith(Path.of("slate_binary", "run")))
            .findFirst();
      } catch (IOException e) {
        logger.atWarning().withCause(e).log("SlateDriver: Error during recursive search");
      }
    }
    return Files.exists(runScript) ? Optional.of(runScript) : Optional.empty();
  }
}
