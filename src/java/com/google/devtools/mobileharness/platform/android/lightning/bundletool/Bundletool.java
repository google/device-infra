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

package com.google.devtools.mobileharness.platform.android.lightning.bundletool;

import static com.google.common.base.StandardSystemProperty.JAVA_IO_TMPDIR;
import static com.google.devtools.mobileharness.shared.util.error.MoreThrowables.shortDebugCurrentStackTrace;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.command.CommandTimeoutException;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.inject.Inject;

/** Wrapper for Bundletool binary. */
public class Bundletool {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Path of bundletool in the resource archive or the jar. */
  private static final String BUNDLETOOL_RESOURCE_PATH =
      "/android_appbundle/bundletool/bundletool_deploy.jar";

  /** Singleton supplier of the unpacked bundletool path. */
  private static final Supplier<Path> UNPACKED_BUNDLETOOL_PATH_SUPPLIER =
      Suppliers.memoize(
          () -> {
            logger.atInfo().log("Initializing bundetool %s", shortDebugCurrentStackTrace(0L));
            ResUtil resUtil = new ResUtil();
            try {
              Optional<String> externalFile =
                  resUtil.getExternalResourceFile(BUNDLETOOL_RESOURCE_PATH);
              if (externalFile.isPresent()) {
                return Path.of(externalFile.get());
              }
              return Path.of(resUtil.getResourceFile(Bundletool.class, BUNDLETOOL_RESOURCE_PATH));
            } catch (MobileHarnessException e) {
              throw new IllegalStateException("Failed to initialize bundletool.", e);
            }
          });

  private final SystemUtil systemUtil;
  private final Adb adb;
  private final Aapt aapt;
  private final CommandExecutor commandExecutor;
  private final Supplier<Path> bundletoolPathSupplier;
  private final Optional<Path> javaTmpDir;

  @Inject
  Bundletool(SystemUtil systemUtil, Adb adb, Aapt aapt, CommandExecutor commandExecutor) {
    this(
        systemUtil,
        adb,
        aapt,
        commandExecutor,
        UNPACKED_BUNDLETOOL_PATH_SUPPLIER,
        Optional.empty());
  }

  /**
   * @deprecated Needed for backwards compatibility for non-Guice based usage. Will be removed after
   *     all usages are migrated to Guice.
   */
  @Deprecated
  public Bundletool() {
    this(new SystemUtil(), new Adb(), new Aapt(), new CommandExecutor());
  }

  @VisibleForTesting
  public Bundletool(
      SystemUtil systemUtil, Adb adb, Aapt aapt, CommandExecutor commandExecutor, Path bundletool) {
    this(
        systemUtil,
        adb,
        aapt,
        commandExecutor,
        Suppliers.memoize(() -> bundletool),
        Optional.empty());
  }

  private Bundletool(
      SystemUtil systemUtil,
      Adb adb,
      Aapt aapt,
      CommandExecutor commandExecutor,
      Supplier<Path> bundletoolPathSupplier,
      Optional<Path> javaTmpDir) {
    this.systemUtil = systemUtil;
    this.adb = adb;
    this.aapt = aapt;
    this.commandExecutor = commandExecutor;
    this.bundletoolPathSupplier = bundletoolPathSupplier;
    this.javaTmpDir = javaTmpDir;
  }

  /**
   * Returns a new Bundletool instance which uses the given Bundletool jar instead of the packaged
   * one.
   */
  public Bundletool withCustomBundletoolJar(Path bundletoolJar) {
    return new Bundletool(
        systemUtil, adb, aapt, commandExecutor, Suppliers.memoize(() -> bundletoolJar), javaTmpDir);
  }

  /** Returns a new Bundletool instance which uses the given temporary directory for the JVM. */
  public Bundletool withJavaTmpDir(Path javaTmpDir) {
    return new Bundletool(
        systemUtil, adb, aapt, commandExecutor, bundletoolPathSupplier, Optional.of(javaTmpDir));
  }

  public void buildApks(BuildApksArgs args) throws MobileHarnessException, InterruptedException {
    run(args.toBundletoolCommand(adb.getAdbPath(), aapt.getAaptPath()), args.commandTimeout());
  }

  public void getDeviceSpec(GetDeviceSpecArgs args)
      throws MobileHarnessException, InterruptedException {
    run(args.toBundletoolCommand(adb.getAdbPath()), args.commandTimeout());
  }

  public void extractApks(ExtractApksArgs args)
      throws MobileHarnessException, InterruptedException {
    run(args.toBundletoolCommand(), args.commandTimeout());
  }

  public void installApks(InstallApksArgs args)
      throws MobileHarnessException, InterruptedException {
    run(args.toBundletoolCommand(adb.getAdbPath()), args.commandTimeout());
  }

  /**
   * Extracts the package name from an APK Set (.apks) file.
   *
   * @param apksPath path to the .apks file
   * @return the package name extracted from the toc.pb file within the APK Set
   * @throws MobileHarnessException if the package name cannot be extracted or a file operation
   *     fails
   */
  public String getPackageNameFromApks(Path apksPath) throws MobileHarnessException {
    // Bundletool has no command to get the package name from an APK Set, and we don't want to take
    // a dependency on the bundletool library, rather include the binary as a resource.
    // The proto type of the toc.pb file is BuildApksResult, so we can just parse the binary
    // proto and extract the field at position 4 containing the package name.
    final int packageNameTag = (4 << 3) | WireFormat.WIRETYPE_LENGTH_DELIMITED;
    try (ZipFile zipFile = new ZipFile(apksPath.toFile())) {
      ZipEntry tocEntry = zipFile.getEntry("toc.pb");
      if (tocEntry == null) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_BUNDLETOOL_EXTRACT_PACKAGE_NAME_PARSING_ERROR,
            "toc.pb, containing the package name, not found in " + apksPath);
      }
      try (InputStream inputStream = zipFile.getInputStream(tocEntry)) {
        CodedInputStream codedInputStream = CodedInputStream.newInstance(inputStream);
        while (!codedInputStream.isAtEnd()) {
          int tag = codedInputStream.readTag();
          if (tag == 0) {
            break;
          }
          if (tag == packageNameTag) {
            return codedInputStream.readString();
          }
          codedInputStream.skipField(tag);
        }
      }
    } catch (ZipException | InvalidProtocolBufferException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_BUNDLETOOL_EXTRACT_PACKAGE_NAME_PARSING_ERROR,
          "Failed to parse package name from toc.pb in " + apksPath,
          e);
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_BUNDLETOOL_EXTRACT_PACKAGE_NAME_IO_ERROR,
          "Failed to read package name from toc.pb in " + apksPath,
          e);
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_BUNDLETOOL_EXTRACT_PACKAGE_NAME_PARSING_ERROR,
        "Package name not found in " + apksPath);
  }

  /**
   * Runs bundletool with the given arguments.
   *
   * @param args bundletool command arguments
   * @param timeout command timeout
   * @return the stdout and stderr of the command
   * @throws MobileHarnessException if the command fails to start, execute, or times out
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  @CanIgnoreReturnValue
  private String run(ImmutableList<String> args, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    // Tier 1: Use the test-specific temp dir if explicitly provided with withJavaTmpDir()
    String tmpDir =
        javaTmpDir
            .map(Path::toString)
            .orElseGet(
                () -> {
                  // Tier 2: fall back to the lab server's persistent NVMe temp dir (preventing the
                  // 14GB RAM disk explosion callers like ApkInstaller)
                  String labTmpDir = Flags.tmpDirRoot.get();
                  if (labTmpDir != null && !labTmpDir.isEmpty()) {
                    logger.atInfo().log("Bundletool falls back to lab temp dir %s", labTmpDir);
                    return labTmpDir;
                  }
                  // Tier 3: last resort fallback (e.g., for local workstation execution)
                  logger.atInfo().log(
                      "Bundletool falls back to system temp dir %s", JAVA_IO_TMPDIR.value());
                  return JAVA_IO_TMPDIR.value();
                });
    logger.atInfo().log("Bundletool is going to use temp dir %s", tmpDir);
    ImmutableList<String> argsBuilder =
        ImmutableList.<String>builder()
            .add(systemUtil.getJavaBin())
            // -D flag must be place before -jar to make sure the arguments is passed to the JVM
            // instead of the main method of the program.
            .add(String.format("-Djava.io.tmpdir=%s", tmpDir))
            .add("-jar")
            .add(bundletoolPathSupplier.get().toString())
            .addAll(args)
            .build();
    Command command =
        Command.of(argsBuilder)
            .redirectStderr(true)
            .timeout(timeout)
            .showFullResultInException(true);
    CommandProcess commandProcess = null;
    try {
      commandProcess = commandExecutor.start(command);
      return commandProcess.await(timeout).stdout();
    } catch (TimeoutException | CommandTimeoutException e) {
      if (commandProcess != null) {
        commandProcess.killAndThenKillForcibly(Duration.ofSeconds(30));
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_BUNDLETOOL_COMMAND_TIMEOUT, "Bundletool command timeout", e);
    } catch (CommandStartException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_BUNDLETOOL_COMMAND_START_ERROR,
          "Bundletool command start error",
          e);
    } catch (CommandFailureException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_BUNDLETOOL_COMMAND_EXEC_ERROR, "Bundletool command exec error", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (commandProcess != null) {
        commandProcess.killAndThenKillForcibly(Duration.ofSeconds(30));
      }
      throw e;
    }
  }
}
