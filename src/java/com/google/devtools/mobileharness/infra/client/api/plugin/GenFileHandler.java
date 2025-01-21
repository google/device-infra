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

package com.google.devtools.mobileharness.infra.client.api.plugin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndedEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/** Saves test log to file. */
public class GenFileHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LocalFileUtil localFileUtil;
  private final Splitter fileNameSplitter = Splitter.on(",").trimResults().omitEmptyStrings();

  public GenFileHandler() {
    this(new LocalFileUtil());
  }

  @VisibleForTesting
  GenFileHandler(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  @Subscribe
  public void onTestEnded(TestEndedEvent event) throws MobileHarnessException {
    TestInfo testInfo = event.getTest();
    testInfo.log().atInfo().alsoTo(logger).log("Start uploading test generated files");
    generateTestLogFile(testInfo);
    for (TestInfo subTest : testInfo.subTests().getAll().values()) {
      generateTestLogFile(subTest);
    }

    testInfo.log().atInfo().alsoTo(logger).log("Finish uploading test generated files");
  }

  @Subscribe
  public void onJobEnd(JobEndEvent event) throws MobileHarnessException {
    JobInfo jobInfo = event.getJob();
    jobInfo.log().atInfo().alsoTo(logger).log("GenFileHandler started to ack JobEndEvent.");
    String genFileDir = null;
    try {
      genFileDir = jobInfo.setting().getGenFileDir();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.CLIENT_GEN_FILE_HANDLER_GET_JOB_GEN_FILE_DIR_ERROR,
          "Failed to open test gen file dir: " + e.getMessage());
    }
    String filePath = PathUtil.join(genFileDir, "job_output.txt");
    try {
      localFileUtil.writeToFile(filePath, jobInfo.log().get(0));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.CLIENT_GEN_FILE_HANDLER_WRITE_JOB_OUTPUT_ERROR,
          "Failed to save test log to " + genFileDir + ": " + e.getMessage());
    }
    jobInfo.log().atInfo().alsoTo(logger).log("GenFileHandler finished to ack JobEndEvent.");
  }

  private void generateTestLogFile(TestInfo testInfo) throws MobileHarnessException {
    if (testInfo.log().size() == 0) {
      // See b/80112683.
      return;
    }
    String genFileDir;
    try {
      genFileDir = testInfo.getGenFileDir();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.CLIENT_GEN_FILE_HANDLER_GET_TEST_GEN_FILE_DIR_ERROR,
          "Failed to open test gen file dir: " + e.getMessage());
    }
    String filePath = PathUtil.join(genFileDir, "test_output.txt");
    try {
      localFileUtil.writeToFile(filePath, testInfo.log().get(0));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.CLIENT_GEN_FILE_HANDLER_WRITE_TEST_OUTPUT_ERROR,
          "Failed to save test log to " + genFileDir + ": " + e.getMessage());
    }
    testInfo.log().atInfo().alsoTo(logger).log("Saved the test log to %s", filePath);
  }
}
