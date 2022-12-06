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

package com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.shared;

/** Class to get resource id for Android resource, like views, layout, string, etc. */
public interface ResId {

  /** Get resource id for view DeviceId. */
  int getDeviceId();

  /** Get resource id for view MirroredDeviceId. */
  int getMirroredDeviceId();

  /** Get resource id for view BuildInfo. */
  int getBuildInfo();

  /** Get resource id for view HostInfo. */
  int getHostInfo();

  /** Get resource id for view CustomLabels. */
  int getCustomLabels();

  /** Get resource id for view Owners. */
  int getOwners();

  /** Get resource id for view WifiInfo. */
  int getWifiInfo();

  /** Get resource id for view PhoneInfo. */
  int getPhoneInfo();

  /** Get resource id for daemon activity main layout. */
  int getMainLayout();

  /** Get resource id for finish button. */
  int getFinishButton();
}
