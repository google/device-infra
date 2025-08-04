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

import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Locale;

/** xTs directory relevant util. */
public class XtsDirUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Path EMPTY_PATH = Path.of("");

  /** Gets a {@link String} to use for directory suffixes created from the given time. */
  public static String getDirSuffix(Instant time) {
    return new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss", Locale.getDefault())
        .format(new Timestamp(time.toEpochMilli()));
  }

  /** Gets the JDK directory for the given {@code xtsType}. */
  public static Path getXtsJdkDir(Path xtsRootDir, String xtsType) {
    Path androidDir = getAndroidDir(xtsRootDir, xtsType);
    return androidDir.resolve("jdk");
  }

  /** Gets the Java binary path for the given {@code xtsType}. */
  public static Path getXtsJavaBinary(Path xtsRootDir, String xtsType) {
    return getXtsJdkDir(xtsRootDir, xtsType).resolve("bin/java");
  }

  /** Gets the lib directory for the given {@code xtsType}. */
  public static Path getXtsLibDir(Path xtsRootDir, String xtsType) {
    Path androidDir = getAndroidDir(xtsRootDir, xtsType);
    return androidDir.resolve("lib");
  }

  /** Gets the lib64 directory for the given {@code xtsType}. */
  public static Path getXtsLib64Dir(Path xtsRootDir, String xtsType) {
    Path androidDir = getAndroidDir(xtsRootDir, xtsType);
    return androidDir.resolve("lib64");
  }

  /** Gets the logs directory for the given {@code xtsType}. */
  public static Path getXtsLogsDir(Path xtsRootDir, String xtsType) {
    Path androidDir = getAndroidDir(xtsRootDir, xtsType);
    return androidDir.resolve("logs");
  }

  /** Gets the "results" directory for the given {@code xtsType}. */
  public static Path getXtsResultsDir(Path xtsRootDir, String xtsType) {
    Path androidDir = getAndroidDir(xtsRootDir, xtsType);
    return androidDir.resolve("results");
  }

  /** Gets the subplans directory for the given {@code xtsType}. */
  public static Path getXtsSubPlansDir(Path xtsRootDir, String xtsType) {
    Path androidDir = getAndroidDir(xtsRootDir, xtsType);
    return androidDir.resolve("subplans");
  }

  /** Gets the test cases directory for the given {@code xtsType}. */
  public static Path getXtsTestCasesDir(Path xtsRootDir, String xtsType) {
    Path androidDir = getAndroidDir(xtsRootDir, xtsType);
    return androidDir.resolve("testcases");
  }

  /** Gets the tools directory for the given {@code xtsType}. */
  public static Path getXtsToolsDir(Path xtsRootDir, String xtsType) {
    Path androidDir = getAndroidDir(xtsRootDir, xtsType);
    return androidDir.resolve("tools");
  }

  private static Path getAndroidDir(Path xtsRootDir, String xtsType) {
    if (!xtsType.equals(XtsConstants.ATS_NO_OP_TEST_TYPE)) {
      return xtsRootDir.resolve(String.format("android-%s", toLowerCase(xtsType)));
    }
    try (java.util.stream.Stream<Path> stream = Files.list(xtsRootDir)) {
      return stream
          .filter(path -> path.getFileName().toString().startsWith("android-"))
          .findFirst()
          .orElse(EMPTY_PATH);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to list directory %s", xtsRootDir);
      return EMPTY_PATH;
    }
  }

  private XtsDirUtil() {}
}
