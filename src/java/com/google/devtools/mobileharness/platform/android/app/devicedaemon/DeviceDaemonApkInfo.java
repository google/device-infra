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

package com.google.devtools.mobileharness.platform.android.app.devicedaemon;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

/**
 * Provide methods to get MH Daemon apk path, activity name, package name, apk version code and
 * extra permission name for Android devices.
 */
public interface DeviceDaemonApkInfo {

  /** Gets the activity name of the target Android device daemon app. */
  String getActivityName();

  /** Gets the apk path of the target Android device daemon app. */
  String getApkPath() throws MobileHarnessException;

  /** Gets the versoin code of the target Android device daemon app. */
  int getApkVersionCode();

  /** Gets the extra permission names of the target Android device daemon app. */
  ImmutableList<String> getExtraPermissionNames();

  /** Gets the package name of the target Android device daemon app. */
  String getPackageName();
}
