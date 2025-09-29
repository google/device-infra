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

import com.google.common.base.Splitter;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.ats.common.plan.JarFileUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * A class that resolves loading of build related metadata for test suite.
 *
 * <p>To properly expose related info, a test suite must include a <code>test-suite-info.properties
 * </code> file in its jar resources
 */
public class TestSuiteInfo {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Expected property filename in jar resource. */
  private static final String SUITE_INFO_PROPERTY = "test-suite-info.properties";

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

  private volatile Optional<TestSuiteVersion> testSuiteVersion = null;
  private Properties testSuiteInfoProps;
  private final JarFileUtil jarFileUtil;
  private final String xtsRootDir;
  private final String xtsTypeStr;

  TestSuiteInfo(String xtsRootDir, String xtsType, JarFileUtil jarFileUtil) {
    this.jarFileUtil = jarFileUtil;
    this.xtsRootDir = xtsRootDir;
    this.xtsTypeStr = xtsType;
    loadSuiteInfo();
  }

  private void loadSuiteInfo() {
    Path xtsTfJar = getToolsDir().resolve(String.format("%s-tradefed.jar", xtsTypeStr));
    try {
      Optional<InputStream> testSuiteInfoPropsInputStream =
          jarFileUtil.getZipEntryInputStream(xtsTfJar, SUITE_INFO_PROPERTY);
      if (testSuiteInfoPropsInputStream.isEmpty()) {
        logger.atWarning().log(
            "Unable to load suite info from jar resource %s, using stub info instead",
            SUITE_INFO_PROPERTY);
        testSuiteInfoProps = new Properties();
        testSuiteInfoProps.setProperty(BUILD_NUMBER, STUB_BUILD_NUMBER);
        testSuiteInfoProps.setProperty(TARGET_ARCH, STUB_TARGET_ARCH);
        testSuiteInfoProps.setProperty(NAME, STUB_NAME);
        testSuiteInfoProps.setProperty(FULLNAME, STUB_FULLNAME);
        testSuiteInfoProps.setProperty(VERSION, STUB_VERSION);
      } else {
        testSuiteInfoProps = loadSuiteInfoFromInputStream(testSuiteInfoPropsInputStream.get());
      }
    } catch (IOException e) {
      throw new IllegalStateException(
          String.format(
              "Error loading jar resource file \"%s\" for test suite info from the jar \"%s\"",
              SUITE_INFO_PROPERTY, xtsTfJar),
          e);
    }
  }

  private Properties loadSuiteInfoFromInputStream(InputStream testSuiteInfoPropsInputStream)
      throws IOException {
    Properties props = new Properties();
    props.load(testSuiteInfoPropsInputStream);
    return props;
  }

  /** Gets the build number of the test suite. */
  public String getBuildNumber() {
    return testSuiteInfoProps.getProperty(BUILD_NUMBER);
  }

  /** Gets the target archs supported by the test suite. */
  public List<String> getTargetArchs() {
    String testSuiteInfoArch = testSuiteInfoProps.getProperty(TARGET_ARCH);
    return Splitter.on(",").trimResults().omitEmptyStrings().splitToList(testSuiteInfoArch);
  }

  /** Gets the short name of the test suite. */
  public String getName() {
    return testSuiteInfoProps.getProperty(NAME);
  }

  /** Gets the full name of the test suite. */
  public String getFullName() {
    return testSuiteInfoProps.getProperty(FULLNAME);
  }

  /** Gets the version name of the test suite. */
  public String getVersion() {
    return testSuiteInfoProps.getProperty(VERSION);
  }

  public Optional<TestSuiteVersion> getTestSuiteVersion() {
    if (testSuiteVersion == null) {
      synchronized (this) {
        if (testSuiteVersion == null) {
          String version = getVersion();
          if (isNullOrEmpty(version) || version.trim().isEmpty() || version.equals(STUB_VERSION)) {
            testSuiteVersion = Optional.empty();
          } else {
            testSuiteVersion = Optional.of(TestSuiteVersion.create(version.trim()));
          }
        }
      }
    }
    return testSuiteVersion;
  }

  /**
   * Retrieves test information keyed with the provided name. Or null if not property associated.
   */
  public String get(String name) {
    return testSuiteInfoProps.getProperty(name);
  }

  public Path getToolsDir() {
    return XtsDirUtil.getXtsToolsDir(Path.of(xtsRootDir), xtsTypeStr);
  }
}
