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

import static com.google.common.base.Ascii.toLowerCase;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Locale;

/** xTs directory relevant util. */
public class XtsDirUtil {

  /** Gets a {@link String} to use for directory suffixes created from the given time. */
  public static String getDirSuffix(Instant time) {
    return new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss", Locale.getDefault())
        .format(new Timestamp(time.toEpochMilli()));
  }

  /** Gets the JDK directory for the given {@code xtsType}. */
  public static Path getXtsJdkDir(Path xtsRootDir, String xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/jdk", toLowerCase(xtsType)));
  }

  /** Gets the Java binary path for the given {@code xtsType}. */
  public static Path getXtsJavaBinary(Path xtsRootDir, String xtsType) {
    return getXtsJdkDir(xtsRootDir, xtsType).resolve("bin/java");
  }

  /** Gets the lib directory for the given {@code xtsType}. */
  public static Path getXtsLibDir(Path xtsRootDir, String xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/lib", toLowerCase(xtsType)));
  }

  /** Gets the lib64 directory for the given {@code xtsType}. */
  public static Path getXtsLib64Dir(Path xtsRootDir, String xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/lib64", toLowerCase(xtsType)));
  }

  /** Gets the logs directory for the given {@code xtsType}. */
  public static Path getXtsLogsDir(Path xtsRootDir, String xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/logs", toLowerCase(xtsType)));
  }

  /** Gets the "results" directory for the given {@code xtsType}. */
  public static Path getXtsResultsDir(Path xtsRootDir, String xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/results", toLowerCase(xtsType)));
  }

  /** Gets the subplans directory for the given {@code xtsType}. */
  public static Path getXtsSubPlansDir(Path xtsRootDir, String xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/subplans", toLowerCase(xtsType)));
  }

  /** Gets the test cases directory for the given {@code xtsType}. */
  public static Path getXtsTestCasesDir(Path xtsRootDir, String xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/testcases", toLowerCase(xtsType)));
  }

  /** Gets the tools directory for the given {@code xtsType}. */
  public static Path getXtsToolsDir(Path xtsRootDir, String xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/tools", toLowerCase(xtsType)));
  }

  private XtsDirUtil() {}
}
