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

/*
 * Copyright 2024 Google LLC
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

package com.google.devtools.mobileharness.platform.androiddesktop.device;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import javax.annotation.Nullable;

/** Utility class for managing CIPD packages and execution environment in Mobile Harness. */
public final class CrosCipdUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String CIPD_DIR = "/usr/local/google/mobileharness/ate/cipd_packages";
  public static final Duration DEFAULT_CIPD_TIMEOUT = Duration.ofMinutes(5);
  public static final Duration VERSION_TIMEOUT = Duration.ofSeconds(10);

  /**
   * The default CIPD tag used to pull production packages at runtime when no custom tag is
   * specified.
   */
  public static final String DEFAULT_CIPD_TAG = "prod";

  private CrosCipdUtil() {}

  /**
   * Locates the {@code cipd} binary executable.
   *
   * <p>First checks {@link #CIPD_DIR}/cipd (the standard location in ATE Mobile Harness
   * environments), then searches directories in the system {@code PATH}, and finally falls back to
   * {@code new File("cipd")}.
   */
  public static File getCipdBinary() {
    File cipdInPkgPath = new File(CIPD_DIR, "cipd");
    if (cipdInPkgPath.isFile() && cipdInPkgPath.canExecute()) {
      return cipdInPkgPath;
    }
    String pathEnv = System.getenv("PATH");
    if (pathEnv != null) {
      for (String dir : Splitter.on(File.pathSeparator).omitEmptyStrings().split(pathEnv)) {
        File candidate = new File(dir, "cipd");
        if (candidate.isFile() && candidate.canExecute()) {
          return candidate;
        }
      }
    }
    return new File("cipd");
  }

  /**
   * Gets the command string used to invoke the {@code cipd} executable.
   *
   * @return the absolute path to the cipd binary if found on the filesystem, or {@code "cipd"} as
   *     fallback.
   */
  public static String getCipdCommand() {
    File cipdBinary = getCipdBinary();
    return cipdBinary.isAbsolute() ? cipdBinary.getAbsolutePath() : cipdBinary.getPath();
  }

  /**
   * Helper to print the version of a CIPD package binary.
   *
   * <p>This method runs the binary with the "version" argument to retrieve and log its version.
   */
  public static void printVersion(
      CommandExecutor commandExecutor, String binaryPath, @Nullable TestInfo testInfo)
      throws InterruptedException {
    if (commandExecutor == null || Strings.isNullOrEmpty(binaryPath)) {
      return;
    }
    logInfo(testInfo, String.format("Checking version of binary: %s", binaryPath));
    try {
      CommandResult result =
          commandExecutor.exec(Command.of(binaryPath, "version").timeout(VERSION_TIMEOUT));
      if (result != null && !Strings.isNullOrEmpty(result.stdout())) {
        String msg =
            String.format(
                "%s version:\n%s", new File(binaryPath).getName(), result.stdout().trim());
        logInfo(testInfo, msg);
      }
    } catch (MobileHarnessException e) {
      logWarning(testInfo, String.format("Failed to get version for binary: %s", binaryPath), e);
    }
  }

  /**
   * Downloads and installs a CIPD package into a designated directory using {@code cipd install}.
   *
   * @param commandExecutor the {@link CommandExecutor} instance to execute commands.
   * @param fileUtil the {@link LocalFileUtil} helper.
   * @param packageName the full CIPD package name.
   * @param version the CIPD tag, version ref, or instance ID to pull.
   * @param destDir the root directory where the package will be installed. If {@code null}, a
   *     temporary directory will be created.
   * @param binaryName the name of the executable binary in the package.
   * @param testInfo the test info context for logging (optional).
   * @param timeout the timeout for the CIPD install command.
   * @return the {@link Path} pointing to the installed binary within the target directory. When
   *     {@code destDir} is {@code null}, the package root directory can be retrieved via {@link
   *     #getPackageRootDir(Path, String)} for teardown cleanup.
   * @throws MobileHarnessException if parameters are invalid, installation fails, or binary is not
   *     found.
   * @throws InterruptedException if command execution is interrupted.
   */
  public static Path downloadPackage(
      CommandExecutor commandExecutor,
      @Nullable LocalFileUtil fileUtil,
      String packageName,
      String version,
      @Nullable Path destDir,
      String binaryName,
      @Nullable TestInfo testInfo,
      Duration timeout)
      throws MobileHarnessException, InterruptedException {
    if (commandExecutor == null) {
      throw new MobileHarnessException(
          BasicErrorId.NON_MH_EXCEPTION, "Cannot download CIPD package: commandExecutor is null");
    }
    if (Strings.isNullOrEmpty(packageName)) {
      throw new MobileHarnessException(
          BasicErrorId.NON_MH_EXCEPTION,
          "Cannot download CIPD package: packageName is null or empty");
    }
    if (Strings.isNullOrEmpty(version)) {
      throw new MobileHarnessException(
          BasicErrorId.NON_MH_EXCEPTION, "Cannot download CIPD package: version is null or empty");
    }
    if (Strings.isNullOrEmpty(binaryName)) {
      throw new MobileHarnessException(
          BasicErrorId.NON_MH_EXCEPTION,
          "Cannot download CIPD package: binaryName is null or empty");
    }
    Path binaryPathRelative = Path.of(binaryName);
    if (binaryPathRelative.isAbsolute()) {
      throw new MobileHarnessException(
          BasicErrorId.NON_MH_EXCEPTION,
          String.format(
              "Cannot download CIPD package: binaryName must be a relative path: %s", binaryName));
    }
    if (fileUtil == null) {
      fileUtil = new LocalFileUtil();
    }

    boolean isTempDir = false;
    Path targetDir = destDir != null ? destDir.toAbsolutePath().normalize() : null;
    if (targetDir == null) {
      try {
        String sanitizedPrefix = "cipd_" + binaryPathRelative.getFileName();
        targetDir =
            fileUtil.createTempDir(Path.of(System.getProperty("java.io.tmpdir")), sanitizedPrefix);
        isTempDir = true;
        targetDir = targetDir.toAbsolutePath().normalize();
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            BasicErrorId.LOCAL_DIR_CREATE_TMP_ERROR,
            String.format("Failed to create temporary directory for CIPD package %s", packageName),
            e);
      }
    } else {
      fileUtil.prepareDir(targetDir.toString());
    }

    Throwable thrown = null;
    try {
      Path binaryPath = targetDir.resolve(binaryPathRelative).normalize();
      if (!binaryPath.startsWith(targetDir)) {
        throw new MobileHarnessException(
            BasicErrorId.NON_MH_EXCEPTION,
            String.format(
                "Cannot download CIPD package: binaryName %s escapes target directory %s",
                binaryName, targetDir));
      }

      logInfo(
          testInfo,
          String.format(
              "Installing CIPD package '%s' with version '%s' into '%s'...",
              packageName, version, targetDir));

      String cipdCmd = getCipdCommand();
      Command cipdInstallCommand =
          Command.of(cipdCmd, "install", packageName, version, "-root", targetDir.toString())
              .timeout(timeout);

      CommandResult result = commandExecutor.exec(cipdInstallCommand);
      if (result != null && !Strings.isNullOrEmpty(result.stdout())) {
        logInfo(testInfo, "CIPD install output: \n" + result.stdout().trim());
      }

      if (!Files.isRegularFile(binaryPath)) {
        throw new MobileHarnessException(
            BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND,
            String.format(
                "CIPD package %s installed, but binary %s not found at %s",
                packageName, binaryName, binaryPath));
      }

      try {
        fileUtil.grantFileOrDirFullAccess(binaryPath.toString());
      } catch (MobileHarnessException e) {
        logWarning(
            testInfo, String.format("Failed to set full access permissions on %s", binaryPath), e);
      }

      logInfo(
          testInfo,
          String.format(
              "Successfully installed CIPD package %s version %s to %s",
              packageName, version, binaryPath));
      return binaryPath;
    } catch (MobileHarnessException | InterruptedException | RuntimeException | Error t) {
      thrown = t;
      throw t;
    } finally {
      if (thrown != null && isTempDir) {
        safeRemoveTempDir(fileUtil, targetDir, testInfo, thrown);
      }
    }
  }

  /**
   * Cleans up the temporary directory.
   *
   * <p>Warning: This method deletes the specified directory and all its contents. It should only be
   * invoked with temporary directories created for package downloads, never shared directories.
   *
   * @param fileUtil the {@link LocalFileUtil} instance, or null to instantiate a default one
   * @param dir the temporary directory path to remove, or null
   * @param testInfo the {@link TestInfo} for logging, or null
   */
  public static void cleanupTempDir(
      @Nullable LocalFileUtil fileUtil, @Nullable Path dir, @Nullable TestInfo testInfo) {
    if (dir == null) {
      return;
    }
    if (fileUtil == null) {
      fileUtil = new LocalFileUtil();
    }
    safeRemoveTempDir(fileUtil, dir, testInfo, null);
  }

  /**
   * Resolves the root package directory from a downloaded binary path given the binary's relative
   * name.
   *
   * <p>For example, if {@code binaryName} is {@code "bin/lsnexus"} and {@code binaryPath} is {@code
   * "/tmp/cipd_lsnexus123/bin/lsnexus"}, this returns {@code "/tmp/cipd_lsnexus123"}.
   *
   * @param binaryPath the path returned by {@link #downloadPackage}.
   * @param binaryName the relative binary name passed to {@link #downloadPackage}.
   * @return the root directory of the installed package.
   */
  @Nullable
  public static Path getPackageRootDir(@Nullable Path binaryPath, @Nullable String binaryName) {
    if (binaryPath == null || Strings.isNullOrEmpty(binaryName)) {
      return null;
    }
    Path rel = Path.of(binaryName).normalize();
    if (rel.toString().isEmpty() || rel.toString().equals(".")) {
      return null;
    }
    Path p = binaryPath.toAbsolutePath().normalize();
    int count = rel.getNameCount();
    for (int i = 0; i < count; i++) {
      if (p != null) {
        p = p.getParent();
      }
    }
    Path workingDir = Path.of("").toAbsolutePath().normalize();
    if (p == null
        || p.getParent() == null
        || p.equals(p.getRoot())
        || p.getNameCount() <= 1
        || p.equals(workingDir)) {
      return null;
    }
    return p;
  }

  private static void safeRemoveTempDir(
      LocalFileUtil fileUtil,
      Path targetDir,
      @Nullable TestInfo testInfo,
      @Nullable Throwable primaryException) {
    if (targetDir == null) {
      return;
    }
    Path normalizedDir = targetDir.toAbsolutePath().normalize();
    Path workingDir = Path.of("").toAbsolutePath().normalize();
    if (normalizedDir.getParent() == null
        || normalizedDir.equals(normalizedDir.getRoot())
        || normalizedDir.getNameCount() <= 1
        || normalizedDir.equals(workingDir)) {
      logWarning(
          testInfo,
          String.format("Refusing to clean up filesystem root or system directory: %s", targetDir),
          primaryException);
      return;
    }
    try {
      fileUtil.removeFileOrDir(targetDir);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (primaryException != null) {
        primaryException.addSuppressed(e);
      }
      logWarning(testInfo, "Interrupted while removing temporary directory: " + targetDir, e);
    } catch (MobileHarnessException e) {
      if (primaryException != null) {
        primaryException.addSuppressed(e);
      }
      logWarning(testInfo, "Failed to remove temporary directory: " + targetDir, e);
    }
  }

  private static void logInfo(TestInfo testInfo, String message) {
    if (testInfo != null && testInfo.log() != null) {
      var atInfo = testInfo.log().atInfo();
      if (atInfo != null) {
        atInfo.alsoTo(logger).log("%s", message);
        return;
      }
    }
    logger.atInfo().log("%s", message);
  }

  private static void logWarning(TestInfo testInfo, String message, @Nullable Throwable cause) {
    if (testInfo != null && testInfo.log() != null) {
      var atWarning = testInfo.log().atWarning();
      if (atWarning != null) {
        if (cause != null) {
          atWarning.alsoTo(logger).withCause(cause).log("%s", message);
        } else {
          atWarning.alsoTo(logger).log("%s", message);
        }
        return;
      }
    }
    if (cause != null) {
      logger.atWarning().withCause(cause).log("%s", message);
    } else {
      logger.atWarning().log("%s", message);
    }
  }
}
