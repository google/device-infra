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

package com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.platform.android.app.ActivityManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.wireless.qa.mobileharness.shared.android.Sqlite;
import com.google.wireless.qa.mobileharness.shared.api.device.BaseDevice;

/** Default implementation of {@code AndroidDeviceDelegate}. */
public class AndroidDeviceDelegateImpl extends AndroidDeviceDelegate {

  public AndroidDeviceDelegateImpl(BaseDevice device) {
    this(
        device,
        new ActivityManager(),
        new Sqlite(),
        new AndroidAdbUtil(),
        new AndroidSystemStateUtil(),
        new AndroidPackageManagerUtil(),
        new AndroidSystemSettingUtil(),
        new AndroidProcessUtil());
  }

  @VisibleForTesting
  AndroidDeviceDelegateImpl(
      BaseDevice device,
      ActivityManager am,
      Sqlite sqlite,
      AndroidAdbUtil androidAdbUtil,
      AndroidSystemStateUtil androidSystemStateUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidProcessUtil androidProcessUtil) {
    super(
        device,
        am,
        sqlite,
        androidAdbUtil,
        androidSystemStateUtil,
        androidPackageManagerUtil,
        androidSystemSettingUtil,
        androidProcessUtil);
  }

  @Override
  protected boolean ifEnableFullStackFeatures() {
    return true;
  }
}
