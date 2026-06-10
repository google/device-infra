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

package com.google.devtools.mobileharness.platform.android.xts.suite;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.ats.common.plan.JarFileUtil;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.TestSuiteVersion;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/** Utility class for loading build related metadata for test suite. */
public final class TestSuiteInfoUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Expected property filename in jar resource. */
  private static final String SUITE_INFO_PROPERTY = "test-suite-info.properties";

  private static final String VERSION_FILE_NAME = "version.txt";

  private static final String STUB_BUILD_NUMBER = "[stub build number]";
  private static final String STUB_TARGET_ARCH = "[stub target arch]";
  private static final String STUB_NAME = "[stub name]";
  private static final String STUB_FULLNAME = "[stub fullname]";
  private static final String STUB_VERSION = "[stub version]";

  // Suite info keys
  private static final String BUILD_NUMBER = "build_number";
  private static final String TARGET_ARCH = "target_arch";
  private static final String NAME = "name";
  private static final String FULLNAME = "fullname";
  private static final String VERSION = "version";

  private TestSuiteInfoUtil() {}

  /** Loads and returns the TestSuiteInfo. */
  public static TestSuiteInfo loadTestSuiteInfo(
      String xtsRootDir, String xtsType, JarFileUtil jarFileUtil) {
    ImmutableMap<String, String> properties = loadSuiteInfo(xtsRootDir, xtsType, jarFileUtil);
    Optional<TestSuiteVersion> version = parseTestSuiteVersion(properties);
    return TestSuiteInfo.create(xtsRootDir, xtsType, properties, version);
  }

  private static ImmutableMap<String, String> loadSuiteInfo(
      String xtsRootDir, String xtsType, JarFileUtil jarFileUtil) {
    Properties propsValue;
    Path toolsDir = XtsDirUtil.getXtsToolsDir(Path.of(xtsRootDir), xtsType);
    Path xtsTfJar = toolsDir.resolve(String.format("%s-tradefed.jar", xtsType));
    try {
      Optional<InputStream> testSuiteInfoPropsInputStream =
          jarFileUtil.getZipEntryInputStream(xtsTfJar, SUITE_INFO_PROPERTY);
      if (testSuiteInfoPropsInputStream.isEmpty()) {
        logger.atWarning().log(
            "Unable to load suite info from jar resource %s, using stub info instead",
            SUITE_INFO_PROPERTY);
        propsValue = new Properties();
        propsValue.setProperty(BUILD_NUMBER, STUB_BUILD_NUMBER);
        propsValue.setProperty(TARGET_ARCH, STUB_TARGET_ARCH);
        propsValue.setProperty(NAME, STUB_NAME);
        propsValue.setProperty(FULLNAME, STUB_FULLNAME);
        propsValue.setProperty(VERSION, STUB_VERSION);
      } else {
        propsValue = loadSuiteInfoFromInputStream(testSuiteInfoPropsInputStream.get());
      }
    } catch (IOException e) {
      throw new IllegalStateException(
          String.format(
              "Error loading jar resource file \"%s\" for test suite info from the jar \"%s\"",
              SUITE_INFO_PROPERTY, xtsTfJar),
          e);
    }

    loadBuildNumberFromVersionFile(toolsDir)
        .ifPresent(buildNumber -> propsValue.setProperty(BUILD_NUMBER, buildNumber));
    return Maps.fromProperties(propsValue);
  }

  private static Properties loadSuiteInfoFromInputStream(InputStream testSuiteInfoPropsInputStream)
      throws IOException {
    Properties props = new Properties();
    props.load(testSuiteInfoPropsInputStream);
    return props;
  }

  private static Optional<String> loadBuildNumberFromVersionFile(Path toolsDir) {
    Path versionFile = toolsDir.resolve(VERSION_FILE_NAME);
    try (BufferedReader reader = Files.newBufferedReader(versionFile, UTF_8)) {
      String buildNumber = reader.readLine();
      if (buildNumber != null) {
        buildNumber = buildNumber.trim();
        if (!buildNumber.isEmpty()) {
          return Optional.of(buildNumber);
        }
      }
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to read build number from %s", versionFile.toAbsolutePath());
    }
    return Optional.empty();
  }

  private static Optional<TestSuiteVersion> parseTestSuiteVersion(
      ImmutableMap<String, String> properties) {
    try {
      String version = properties.get(VERSION);
      if (isNullOrEmpty(version) || version.trim().isEmpty() || version.equals(STUB_VERSION)) {
        return Optional.empty();
      } else {
        return Optional.of(TestSuiteVersionUtil.parse(version.trim()));
      }
    } catch (IllegalArgumentException e) {
      logger.atWarning().withCause(e).log(
          "Failed to parse test suite version \"%s\"", properties.get(VERSION));
      return Optional.empty();
    }
  }
}
