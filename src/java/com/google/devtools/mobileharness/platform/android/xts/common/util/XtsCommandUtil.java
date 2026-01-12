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

package com.google.devtools.mobileharness.platform.android.xts.common.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.JavaVersion;
import java.nio.file.Path;
import javax.inject.Inject;

/** The util to get xts command. */
public class XtsCommandUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String COMPATIBILITY_CONSOLE_CLASS =
      "com.android.compatibility.common.tradefed.command.CompatibilityConsole";

  private final SystemUtil systemUtil;
  private final LocalFileUtil localFileUtil;

  @Inject
  @VisibleForTesting
  XtsCommandUtil(SystemUtil systemUtil, LocalFileUtil localFileUtil) {
    this.systemUtil = systemUtil;
    this.localFileUtil = localFileUtil;
  }

  public ImmutableList<String> getTradefedJavaCommand(
      String javaBinary,
      ImmutableList<String> jvmFlags,
      String concatenatedJarPath,
      ImmutableList<String> jvmDefines,
      String mainClass,
      ImmutableList<String> xtsRunCommandArgs) {
    return ImmutableList.<String>builder()
        .add(javaBinary)
        .addAll(jvmFlags)
        .add("-cp")
        .add(concatenatedJarPath)
        .addAll(jvmDefines)
        .add(mainClass)
        .addAll(xtsRunCommandArgs)
        .build();
  }

  public static String getConsoleClassName() {
    return COMPATIBILITY_CONSOLE_CLASS;
  }

  /**
   * Gets the Java property argument for the xTS root directory, e.g., "-DCTS_ROOT=/path/to/cts".
   */
  public static String getXtsRootJavaProperty(String xtsType, Path xtsRootDir) {
    return String.format("-D%s_ROOT=%s", Ascii.toUpperCase(xtsType).replace('-', '_'), xtsRootDir);
  }

  /** The logic should be consistent with cts-tradefed shell. */
  public Path getJavaBinary(String xtsType, Path xtsRootDir) {
    if (useXtsJavaBinary(xtsType, xtsRootDir)) {
      return XtsDirUtil.getXtsJavaBinary(xtsRootDir, xtsType);
    } else {
      return Path.of(systemUtil.getJavaBin());
    }
  }

  /**
   * Whether to use Java binary from xTS root dir rather than from system.
   *
   * <p>The logic should be consistent with cts-tradefed shell.
   */
  public boolean useXtsJavaBinary(String xtsType, Path xtsRootDir) {
    Path xtsJavaBinary = XtsDirUtil.getXtsJavaBinary(xtsRootDir, xtsType);
    boolean useXtsJava =
        systemUtil.isOnLinux() && systemUtil.isX8664() && localFileUtil.isFileExist(xtsJavaBinary);
    if (!useXtsJava) {
      return false;
    }

    try {
      JavaVersion xtsJavaVersion = systemUtil.getJavaVersion(xtsJavaBinary);
      JavaVersion systemJavaVersion = systemUtil.getJavaVersion(Path.of(systemUtil.getJavaBin()));

      return xtsJavaVersion.majorVersion() >= systemJavaVersion.majorVersion();
    } catch (MobileHarnessException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      // Fallback to true if version check fails, to keep previous behavior
      // where we use XTS java if it exists on supported platforms.
      logger.atWarning().withCause(e).log("Failed Java version check. Fallback to use XTS java.");
      return true;
    }
  }
}
