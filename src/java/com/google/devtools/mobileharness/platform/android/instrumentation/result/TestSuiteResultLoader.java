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

package com.google.devtools.mobileharness.platform.android.instrumentation.result;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;

/** Loader for Android instrumentation test result. */
public class TestSuiteResultLoader {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LocalFileUtil localFileUtil;

  public TestSuiteResultLoader() {
    this(new LocalFileUtil());
  }

  @VisibleForTesting
  TestSuiteResultLoader(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  public Optional<TestSuiteResult> loadTestResult(TestInfo testInfo) {
    try {
      String genFileDir = testInfo.getGenFileDir();
      String pbPath = PathUtil.join(genFileDir, "instrument_test_result.pb");
      if (localFileUtil.isFileExist(pbPath)) {
        byte[] bytes = localFileUtil.readBinaryFile(pbPath);
        return Optional.of(
            TestSuiteResult.parseFrom(bytes, ExtensionRegistryLite.getEmptyRegistry()));
      } else {
        logger.atInfo().log(
            "No instrument_test_result.pb found for test %s.", testInfo.locator().getId());
      }
    } catch (InvalidProtocolBufferException | MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to load test result for test %s.", testInfo.locator().getId());
    }
    return Optional.empty();
  }
}
