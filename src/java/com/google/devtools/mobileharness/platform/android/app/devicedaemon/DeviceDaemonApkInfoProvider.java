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

import com.google.devtools.mobileharness.platform.android.app.devicedaemon.impl.DeviceDaemonApkInfoImplV1;
import com.google.devtools.mobileharness.platform.android.app.devicedaemon.impl.DeviceDaemonApkInfoImplV2;
import com.google.devtools.mobileharness.shared.util.flags.Flags;

/** Provider for instance of {@link DeviceDaemonApkInfo}. */
public class DeviceDaemonApkInfoProvider {

  private static final int ANDROID_P_API_LEVEL = 28;

  /**
   * For Android devices with API level 28 (Pie) and above, it provides {@link
   * DeviceDaemonApkInfoImplV2}, otherwise {@link DeviceDaemonApkInfoImplV1}
   */
  public DeviceDaemonApkInfo getDeviceDaemonApkInfoInstance(int sdk) {
    return sdk < ANDROID_P_API_LEVEL
        ? new DeviceDaemonApkInfoImplV1()
        : new DeviceDaemonApkInfoImplV2();
  }

  /** Returns {@code true} if device daemon app is enabled. */
  public static boolean isDeviceDaemonEnabled() {
    return Flags.instance().enableDaemon.get();
  }
}
