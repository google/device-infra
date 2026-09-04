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

package com.google.devtools.mobileharness.platform.android.xts.plugin;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Built-in plugin for System-Vendor Reuse (SVR) test planning. */
public class SystemVendorReusePlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Gson GSON = new Gson();

  // Mock response for SVR prototype before APA backend API is ready.
  private static final String MOCK_APA_RESPONSE_JSON =
      """
      {
        "modules": [
          "CtsSampleDeviceTestCases",
          "CtsGestureTestCases"
        ]
      }
      """;

  private final LocalFileUtil localFileUtil;

  public SystemVendorReusePlugin() {
    this(new LocalFileUtil());
  }

  @VisibleForTesting
  SystemVendorReusePlugin(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  /**
   * Handles the test starting event to generate the SVR (System-Vendor Reuse) subplan during setup
   * jobs.
   */
  @Subscribe
  public void onTestStarting(LocalTestStartingEvent event)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = event.getTest();
    JobInfo jobInfo = testInfo.jobInfo();

    if (!jobInfo.params().getBool(XtsConstants.IS_SYSTEM_VENDOR_REUSE_ENABLED, false)) {
      return;
    }

    String sessionId = getSessionId(testInfo);
    String subPlanFilePath = getSubPlanFilePath(testInfo);

    logger
        .atInfo()
        .with(IMPORTANCE, IMPORTANT)
        .log(
            "Starting SVR dynamic test planning for session [%s], subplan file path [%s]...",
            sessionId, subPlanFilePath);

    ImmutableList<String> modules = fetchSvrModules();
    if (modules.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.SYSTEM_VENDOR_REUSE_PLUGIN_NO_CANDIDATE_MODULES,
          String.format(
              "No candidate modules returned for SVR dynamic test planning in job [%s]",
              jobInfo.locator().getId()));
    }
    logger.atInfo().log("SVR candidate modules to run: %s", modules);

    generateSubPlan(Path.of(subPlanFilePath), modules);

    logger
        .atInfo()
        .with(IMPORTANCE, IMPORTANT)
        .log("Successfully generated SVR subplan at [%s]", subPlanFilePath);
  }

  private String getSessionId(TestInfo testInfo) throws MobileHarnessException {
    return testInfo
        .jobInfo()
        .properties()
        .getOptional(Job.SESSION_ID)
        .filter(id -> !id.isEmpty())
        .orElseThrow(
            () ->
                new MobileHarnessException(
                    AndroidErrorId.SYSTEM_VENDOR_REUSE_PLUGIN_PROPERTY_NOT_FOUND,
                    String.format("Job property %s is not set or empty.", Job.SESSION_ID)));
  }

  private String getSubPlanFilePath(TestInfo testInfo) throws MobileHarnessException {
    return testInfo
        .jobInfo()
        .properties()
        .getOptional(Job.SVR_SUBPLAN_FILE_PATH)
        .filter(path -> !path.isEmpty())
        .orElseThrow(
            () ->
                new MobileHarnessException(
                    AndroidErrorId.SYSTEM_VENDOR_REUSE_PLUGIN_PROPERTY_NOT_FOUND,
                    String.format(
                        "Job property %s is not set or empty.", Job.SVR_SUBPLAN_FILE_PATH)));
  }

  /** Fetches candidate module names to include in the SVR (System-Vendor Reuse) subplan. */
  private ImmutableList<String> fetchSvrModules() {
    // TODO: Call APA backend API when ready.
    return parseModulesFromJson(MOCK_APA_RESPONSE_JSON);
  }

  /** Parses candidate module names from an APA backend JSON response string. */
  @VisibleForTesting
  ImmutableList<String> parseModulesFromJson(String json) {
    ApaResponse response;
    try {
      response = GSON.fromJson(json, ApaResponse.class);
    } catch (JsonSyntaxException e) {
      logger.atWarning().withCause(e).log("Failed to parse SVR modules from APA JSON: %s", json);
      return ImmutableList.of();
    }
    if (response == null || response.modules == null) {
      return ImmutableList.of();
    }
    return response.modules.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(module -> !module.isEmpty())
        .distinct()
        .collect(toImmutableList());
  }

  /**
   * Generates and serializes the SVR (System-Vendor Reuse) subplan XML file containing the
   * candidate modules.
   */
  private void generateSubPlan(Path subPlanPath, ImmutableList<String> modules)
      throws MobileHarnessException {
    SubPlan subPlan = new SubPlan();
    modules.forEach(subPlan::addIncludeFilter);

    localFileUtil.prepareParentDir(subPlanPath);

    try (OutputStream outputStream = new FileOutputStream(subPlanPath.toFile())) {
      subPlan.serialize(outputStream, /* tfFiltersOnly= */ true);
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.SYSTEM_VENDOR_REUSE_PLUGIN_WRITE_SUBPLAN_ERROR,
          String.format("Failed to write SVR subplan xml file at %s", subPlanPath),
          e);
    }
  }

  private static class ApaResponse {
    List<String> modules;
  }
}
