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

package com.google.devtools.mobileharness.shared.util.testresult.loader;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;

/** Loader for test suite result across platforms (Android and iOS). */
public class TestSuiteResultLoader {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableList<String> RESULT_FILE_NAMES =
      ImmutableList.of("instrument_test_result.pb", "xctest_test_result.pb");

  private final LocalFileUtil localFileUtil;

  public TestSuiteResultLoader() {
    this(new LocalFileUtil());
  }

  @VisibleForTesting
  public TestSuiteResultLoader(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  /**
   * Loads test result from the gen file directory of the test.
   *
   * <p>It tries to load from {@code instrument_test_result.pb} (Android) or {@code
   * xctest_test_result.pb} (iOS).
   */
  public Optional<TestSuiteResult> loadTestResult(TestInfo testInfo) {
    try {
      String genFileDir = testInfo.getGenFileDir();
      for (String fileName : RESULT_FILE_NAMES) {
        Optional<TestSuiteResult> result = loadFromFile(testInfo, genFileDir, fileName);
        if (result.isPresent()) {
          return result;
        }
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to get gen file directory for test %s.", testInfo.locator().getId());
    }
    return Optional.empty();
  }

  private Optional<TestSuiteResult> loadFromFile(
      TestInfo testInfo, String genFileDir, String fileName) {
    try {
      String pbPath = PathUtil.join(genFileDir, fileName);
      if (localFileUtil.isFileExist(pbPath)) {
        byte[] bytes = localFileUtil.readBinaryFile(pbPath);
        return Optional.of(
            TestSuiteResult.parseFrom(bytes, ExtensionRegistryLite.getEmptyRegistry()));
      } else {
        logger.atInfo().log("No %s found for test %s.", fileName, testInfo.locator().getId());
      }
    } catch (InvalidProtocolBufferException | MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to load test result from %s for test %s.", fileName, testInfo.locator().getId());
    }
    return Optional.empty();
  }
}
