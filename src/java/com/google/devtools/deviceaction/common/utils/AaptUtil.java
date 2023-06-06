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

package com.google.devtools.deviceaction.common.utils;

import static com.google.devtools.deviceaction.common.utils.Constants.APEX_SUFFIX;

import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import java.io.File;
import javax.inject.Inject;

/** An utility class to use aapt. */
public class AaptUtil {
  private final Aapt aapt;

  @Inject
  AaptUtil(ResourceHelper resourceHelper) throws DeviceActionException {
    this(
        resourceHelper
            .getAapt()
            .orElseThrow(
                () ->
                    new DeviceActionException(
                        "RESOURCE_NOT_FOUND", ErrorType.DEPENDENCY_ISSUE, "Failed to get aapt")));
  }

  AaptUtil(Aapt aapt) {
    this.aapt = aapt;
  }

  /** Gets package info from an apk/apex file. */
  public PackageInfo getPackageInfo(File apkFile)
      throws DeviceActionException, InterruptedException {
    try {
      String packageName = aapt.getApkPackageName(apkFile.getAbsolutePath());
      long versionCode = aapt.getApkVersionCode(apkFile.getAbsolutePath());
      return PackageInfo.builder()
          .setPackageName(packageName)
          .setVersionCode(versionCode)
          .setSourceDir(apkFile.getAbsolutePath())
          .setIsApex(apkFile.getName().endsWith(APEX_SUFFIX))
          .build();
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(
          e, "Failed to use aapt on package %s.", apkFile.getAbsolutePath());
    }
  }
}
