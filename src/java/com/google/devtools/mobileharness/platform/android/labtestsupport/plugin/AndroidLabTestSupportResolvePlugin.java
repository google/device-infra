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

package com.google.devtools.mobileharness.platform.android.labtestsupport.plugin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.client.api.event.JobStartEvent;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.util.ResultUtil;

/**
 * A Mobile Harness client plugin to resolve the LabTestSupport APK from the bundled resources to a
 * local file and pass it into the job files, so it will be transferred to the remote lab
 * automatically later.
 *
 * <p>If you want to use the LabTestSupport to turn on/off specific features on the device, use this
 * plugin along with the AndroidLabTestSupportSettingsDecorator.
 */
@Plugin(type = PluginType.CLIENT)
public class AndroidLabTestSupportResolvePlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @ParamAnnotation(
      required = false,
      help =
          "Whether to resolve the LabTestSupport APK from the bundled resources to a local file and"
              + " pass it into the job files. Should be 'true' or 'false'.")
  public static final String LAB_TEST_SUPPORT_RESOLVE = "lab_test_support_resolve";

  @FileAnnotation(help = "File tag for the LabTestSupport APK.")
  public static final String TAG_LAB_TEST_SUPPORT_APK = "lab_test_support_apk";

  @VisibleForTesting
  static final String LAB_TEST_SUPPORT_APK_RES_PATH =
      "/testing/helium/devicemaster/docker/deps_rules/labtestsupport/labtestsupport.apk";

  private final ResUtil resUtil;

  public AndroidLabTestSupportResolvePlugin() {
    this(new ResUtil());
  }

  @VisibleForTesting
  AndroidLabTestSupportResolvePlugin(ResUtil resUtil) {
    this.resUtil = resUtil;
  }

  /**
   * Checks if this plugin is enabled. Only when the lab_test_support_resolve parameter is true
   *
   * @param jobInfo job information.
   * @return whether this plugin is enabled
   */
  public static boolean isEnabled(JobInfo jobInfo) {
    return jobInfo.params().getBool(LAB_TEST_SUPPORT_RESOLVE, false);
  }

  @Subscribe
  @VisibleForTesting
  void onJobStart(JobStartEvent event) throws SkipTestException, InterruptedException {
    JobInfo jobInfo = event.getJob();
    try {
      resolveLabTestSupportApk(jobInfo);
    } catch (MobileHarnessException exception) {
      throw SkipTestException.create(
          "Failed to resolve LabTestSupport APK.",
          ResultUtil.getDesiredTestResultByException(exception),
          AndroidErrorId.ANDROID_LAB_TEST_SUPPORT_RESOLVE_PLUGIN_RESOLVE_LTS_ERROR,
          exception);
    }
  }

  private void resolveLabTestSupportApk(JobInfo jobInfo) throws MobileHarnessException {
    String labTestSupportApkPath =
        resUtil.getResourceFile(getClass(), LAB_TEST_SUPPORT_APK_RES_PATH);
    logger.atInfo().log(
        "Adding LabTestSupport APK to job files with tag \"%s\": %s",
        TAG_LAB_TEST_SUPPORT_APK, labTestSupportApkPath);
    jobInfo.files().add(TAG_LAB_TEST_SUPPORT_APK, labTestSupportApkPath);
    // Enables file cache in the lab.
    jobInfo.params().add(String.format("file_accessor_%s", TAG_LAB_TEST_SUPPORT_APK), "lab");
  }
}
