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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Utilities for reading and writing {@code test-report.properties} files. */
public final class TestReportPropertiesUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private TestReportPropertiesUtil() {}

  /**
   * Loads the {@code test-report.properties} file from the given file path.
   *
   * @param testReportPropertiesFile path to the test report properties file
   * @return loaded {@link Properties}
   * @throws MobileHarnessException if reading the properties file fails
   */
  public static Properties loadTestReportProperties(Path testReportPropertiesFile)
      throws MobileHarnessException {
    Properties properties = new Properties();
    try (InputStream inputStream = new FileInputStream(testReportPropertiesFile.toFile())) {
      properties.load(inputStream);
    } catch (IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_RETRY_COMMAND_TEST_REPORT_PROPERTIES_FILE_READ_ERROR,
          String.format("Failed to read test report properties file %s", testReportPropertiesFile),
          e);
    }
    return properties;
  }

  /**
   * Writes the test report properties to the {@code test-report.properties} file in the given
   * parent directory.
   *
   * @param testReportProperties map of properties to write
   * @param parentDir parent directory where {@code test-report.properties} should be created
   */
  public static void writeTestReportProperties(
      Map<String, String> testReportProperties, File parentDir) {
    File file = new File(parentDir, SuiteCommon.TEST_REPORT_PROPERTIES_FILE_NAME);
    try {
      file.createNewFile();
      Properties properties = new Properties();
      testReportProperties.forEach(properties::setProperty);
      try (FileOutputStream outputStream = new FileOutputStream(file)) {
        properties.store(
            outputStream, /* comments= */ "Auto generated test report properties. Do NOT modify.");
      }
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to write test report properties to %s: %s", file, testReportProperties);
    }
  }

  /** Returns whether the previous session had tradefed modules. */
  public static boolean hasTfModule(Properties properties) {
    return Boolean.parseBoolean(
        properties.getProperty(SuiteCommon.TEST_REPORT_PROPERTY_HAS_TF_MODULE));
  }

  /** Returns whether the previous session had non-tradefed modules. */
  public static boolean hasNonTfModule(Properties properties) {
    return Boolean.parseBoolean(
        properties.getProperty(SuiteCommon.TEST_REPORT_PROPERTY_HAS_NON_TF_MODULE));
  }

  /**
   * Returns the xTS test plan stored in properties, if present.
   *
   * <p>For an initial session run, this is the session's test plan. For a retry session, this is
   * the original test plan of the previous session being retried rather than the literal "retry".
   */
  public static Optional<String> getTestPlan(Properties properties) {
    return Optional.ofNullable(properties.getProperty(SuiteCommon.TEST_REPORT_PROPERTY_TEST_PLAN));
  }
}
