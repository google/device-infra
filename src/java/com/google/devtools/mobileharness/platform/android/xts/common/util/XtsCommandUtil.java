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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import java.nio.file.Path;

/** The util to get xts command. */
public final class XtsCommandUtil {
  private static final String COMPATIBILITY_CONSOLE_CLASS =
      "com.android.compatibility.common.tradefed.command.CompatibilityConsole";

  private static final SystemUtil SYSTEM_UTIL = new SystemUtil();
  private static final LocalFileUtil LOCAL_FILE_UTIL = new LocalFileUtil();

  public static ImmutableList<String> getTradefedJavaCommand(
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
  public static Path getJavaBinary(String xtsType, Path xtsRootDir) {
    if (useXtsJavaBinary(xtsType, xtsRootDir)) {
      return XtsDirUtil.getXtsJavaBinary(xtsRootDir, xtsType);
    } else {
      return Path.of(SYSTEM_UTIL.getJavaBin());
    }
  }

  /**
   * Whether to use Java binary from xTS root dir rather than from system.
   *
   * <p>The logic should be consistent with cts-tradefed shell.
   */
  public static boolean useXtsJavaBinary(String xtsType, Path xtsRootDir) {
    return SYSTEM_UTIL.isOnLinux()
        && SYSTEM_UTIL.isX8664()
        && LOCAL_FILE_UTIL.isFileExist(XtsDirUtil.getXtsJavaBinary(xtsRootDir, xtsType));
  }

  private XtsCommandUtil() {}
}
