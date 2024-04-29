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

package com.google.wireless.qa.mobileharness.shared.api.step.android;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.validator.job.JobValidator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** {@link JobValidator} for {@code InstallApkStep}. */
public class InstallApkStepJobValidator implements JobValidator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String APK_EXT_NAME = ".apk";

  private final LocalFileUtil fileUtil = new LocalFileUtil();

  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    List<String> errors = new ArrayList<>();

    // {apk_path, apk_tag} mapping.
    Map<String, String> apks = new HashMap<>();
    ImmutableSet<String> buildApkPaths = job.files().get(InstallApkStepConstants.TAG_BUILD_APK);
    if (buildApkPaths != null) {
      for (String buildApkPath : buildApkPaths) {
        apks.put(buildApkPath, InstallApkStepConstants.TAG_BUILD_APK);
      }
    }

    ImmutableSet<String> extraApkPaths = job.files().get(InstallApkStepConstants.TAG_EXTRA_APK);
    if (extraApkPaths != null) {
      for (String extraApkPath : extraApkPaths) {
        apks.put(extraApkPath, InstallApkStepConstants.TAG_EXTRA_APK);
      }
    }

    // Appends .apk extension name if missing. Otherwise, adb(version 21) won't be able to install
    // the apk.
    for (Map.Entry<String, String> apk : apks.entrySet()) {
      String path = apk.getKey();
      String tag = apk.getValue();
      if (!path.toLowerCase(Locale.ROOT).endsWith(APK_EXT_NAME)) {
        logger.atInfo().log("Missing %s extension name with %s %s", APK_EXT_NAME, tag, path);
        String newPath = null;
        try {
          newPath = PathUtil.join(job.setting().getRunFileDir(), path + APK_EXT_NAME);
          fileUtil.prepareParentDir(newPath);
          fileUtil.copyFileOrDir(path, newPath);
          job.files().replace(tag, path, ImmutableList.of(newPath));
        } catch (MobileHarnessException e) {
          errors.add(
              String.format(
                  "%s error found by InstallApkStep:%nOriginal path: %s%nNew path: %s%nerror: %s",
                  tag, path, newPath, e.getMessage()));
        }
      }
    }

    // Check the APK installation timeouts parameter
    if (job.params().has(InstallApkStepConstants.PARAM_INSTALL_APK_TIMEOUT_SEC)) {
      try {
        job.params()
            .checkInt(InstallApkStepConstants.PARAM_INSTALL_APK_TIMEOUT_SEC, 0, Integer.MAX_VALUE);
      } catch (MobileHarnessException e) {
        errors.add(
            "Illegal job param integer format: "
                + InstallApkStepConstants.PARAM_INSTALL_APK_TIMEOUT_SEC);
      }
    }

    // Check the APK installation skip downgrade parameter
    if (job.params().has(InstallApkStepConstants.PARAM_SKIP_GMS_DOWNGRADE)) {
      try {
        job.params().checkBool(InstallApkStepConstants.PARAM_SKIP_GMS_DOWNGRADE, true);
      } catch (MobileHarnessException e) {
        errors.add(
            "Illegal job param boolean format: "
                + InstallApkStepConstants.PARAM_SKIP_GMS_DOWNGRADE);
      }
    }

    // Check the APK installation clear data parameter
    if (job.params().has(InstallApkStepConstants.PARAM_CLEAR_GMS_APP_DATA)) {
      try {
        job.params().checkBool(InstallApkStepConstants.PARAM_CLEAR_GMS_APP_DATA, true);
      } catch (MobileHarnessException e) {
        errors.add(
            "Illegal job param boolean format: "
                + InstallApkStepConstants.PARAM_CLEAR_GMS_APP_DATA);
      }
    }
    return errors;
  }
}
