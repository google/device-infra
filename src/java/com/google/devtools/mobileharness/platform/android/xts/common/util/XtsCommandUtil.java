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
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;

/** The util to get xts command. */
public final class XtsCommandUtil {
  private static final String COMPATIBILITY_CONSOLE_CLASS =
      "com.android.compatibility.common.tradefed.command.CompatibilityConsole";

  private static final SystemUtil SYSTEM_UTIL = new SystemUtil();
  private static final LocalFileUtil LOCAL_FILE_UTIL = new LocalFileUtil();

  /** Gets xts command. */
  public static ImmutableList<String> getXtsJavaCommand(
      String xtsType,
      String xtsRootDir,
      ImmutableList<String> jvmFlags,
      String concatenatedJarPath,
      ImmutableList<String> xtsRunCommandArgs) {
    String javaBinary = getJavaBinary(xtsType, xtsRootDir);
    return ImmutableList.<String>builder()
        .add(javaBinary)
        .addAll(jvmFlags)
        .add("-cp")
        .add(concatenatedJarPath)
        .add(String.format("-D%s_ROOT=%s", Ascii.toUpperCase(xtsType), xtsRootDir))
        .add(COMPATIBILITY_CONSOLE_CLASS)
        .addAll(xtsRunCommandArgs)
        .build();
  }

  /** The logic should be consistent with cts-tradefed shell. */
  private static String getJavaBinary(String xtsType, String xtsRootDir) {
    String xtsJavaBinary = PathUtil.join(xtsRootDir, "android-" + xtsType, "jdk/bin/java");
    if (SYSTEM_UTIL.isOnLinux()
        && SYSTEM_UTIL.isX8664()
        && LOCAL_FILE_UTIL.isFileExist(xtsJavaBinary)) {
      return xtsJavaBinary;
    } else {
      return SYSTEM_UTIL.getJavaBin();
    }
  }

  private XtsCommandUtil() {}
}
