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

package com.google.devtools.mobileharness.platform.android.xts.suite.retry;

import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.report.TestResultProtoUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.result.ResultListerHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;

/** Class to load xTS previous results. */
public class PreviousResultLoader {

  private final LocalFileUtil localFileUtil;
  private final ResultListerHelper resultListerHelper;

  @Inject
  PreviousResultLoader(LocalFileUtil localFileUtil, ResultListerHelper resultListerHelper) {
    this.localFileUtil = localFileUtil;
    this.resultListerHelper = resultListerHelper;
  }

  /**
   * Loads the result for the given session.
   *
   * @param resultsDir path to the "results" directory
   * @param previousSessionIndex index of the previous session being loaded
   */
  public Result loadPreviousResult(Path resultsDir, int previousSessionIndex)
      throws MobileHarnessException {
    Path testResultProtoFile = getPrevSessionTestResultProtoFile(resultsDir, previousSessionIndex);

    try {
      return TestResultProtoUtil.readFromFile(testResultProtoFile.toFile());
    } catch (IOException e) {
      throw new MobileHarnessException(
          ExtErrorId.PREV_RESULT_LOADER_LOAD_TEST_RESULT_PROTO_FILE_ERROR,
          String.format(
              "Failed to load test result proto file %s for session %s.",
              testResultProtoFile.toAbsolutePath(), previousSessionIndex),
          e);
    }
  }

  private Path getPrevSessionTestResultProtoFile(Path resultsDir, int previousSessionIndex)
      throws MobileHarnessException {
    List<File> allResultDirs =
        resultListerHelper.listResultDirsInOrder(resultsDir.toAbsolutePath().toString());
    if (allResultDirs.isEmpty()) {
      throw new MobileHarnessException(
          ExtErrorId.PREV_RESULT_LOADER_NO_PREVIOUS_SESSION_FOUND, "No previous session found.");
    }
    if (previousSessionIndex < 0 || previousSessionIndex >= allResultDirs.size()) {
      throw new MobileHarnessException(
          ExtErrorId.PREV_RESULT_LOADER_SESSION_INDEX_OUT_OF_RANGE,
          String.format(
              "The given previous session index %s is out of index. The session index range is [%d,"
                  + " %d]",
              previousSessionIndex, 0, allResultDirs.size() - 1));
    }
    Path testResultProtoFile =
        allResultDirs
            .get(previousSessionIndex)
            .toPath()
            .resolve(SuiteCommon.TEST_RESULT_PB_FILE_NAME);
    if (!localFileUtil.isFileExist(testResultProtoFile)) {
      throw new MobileHarnessException(
          ExtErrorId.PREV_RESULT_LOADER_MISSING_TEST_RESULT_PROTO_FILE_IN_SESSION,
          String.format(
              "The test result proto file %s does not exist for session %s.",
              testResultProtoFile.toAbsolutePath(), previousSessionIndex));
    }
    return testResultProtoFile;
  }
}
