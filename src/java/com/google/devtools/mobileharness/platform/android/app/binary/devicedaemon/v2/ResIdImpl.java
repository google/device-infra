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

package com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v2;

import com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.shared.ResId;

/** Implementation of {@link ResId} for Mobile Harness device daemon version 2. */
public final class ResIdImpl implements ResId {

  @Override
  public int getDeviceId() {
    return R.id.DeviceId;
  }

  @Override
  public int getMirroredDeviceId() {
    return R.id.MirroredDeviceId;
  }

  @Override
  public int getBuildInfo() {
    return R.id.BuildInfo;
  }

  @Override
  public int getHostInfo() {
    return R.id.HostInfo;
  }

  @Override
  public int getCustomLabels() {
    return R.id.CustomLabels;
  }

  @Override
  public int getOwners() {
    return R.id.Owners;
  }

  @Override
  public int getWifiInfo() {
    return R.id.WifiInfo;
  }

  @Override
  public int getPhoneInfo() {
    return R.id.PhoneInfo;
  }

  @Override
  public int getMainLayout() {
    return R.layout.main;
  }

  @Override
  public int getFinishButton() {
    return R.id.finish;
  }
}
