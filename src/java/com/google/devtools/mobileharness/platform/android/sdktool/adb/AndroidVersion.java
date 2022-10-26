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

package com.google.devtools.mobileharness.platform.android.sdktool.adb;

/**
 * Android versions with their basic information.
 *
 * <p>Follow the codename in https://source.android.com/setup/start/build-numbers
 */
public enum AndroidVersion {
  CUPCAKE(3, 3),
  DONUT(4, 4),
  ECLAIR(5, 7),
  FROYO(8, 8),
  GINGER_BREAD(9, 10),
  HONEYCOMB(11, 13),
  ICE_CREAM_SANDWICH(14, 15),
  JELLY_BEAN(16, 18),
  KITKAT(19, 20),
  LOLLIPOP(21, 22),
  MARSHMALLOW(23, 23),
  NOUGAT(24, 25),
  OREO(26, 27),
  PI(28, 28),
  ANDROID_10(29, 29),
  ANDROID_11(30, 30),
  ANDROID_12(31, 31),
  ANDROID_12L(32, 32);

  private final int startSdkVersion;
  private final int endSdkVersion;

  private static final AndroidVersion LATEST_ANDROID_VERSION;

  static {
    AndroidVersion tmpAndroidVersion = AndroidVersion.ANDROID_12L;
    for (AndroidVersion androidVersion : AndroidVersion.values()) {
      if (androidVersion.getStartSdkVersion() > tmpAndroidVersion.getStartSdkVersion()) {
        tmpAndroidVersion = androidVersion;
      }
    }
    LATEST_ANDROID_VERSION = tmpAndroidVersion;
  }

  private AndroidVersion(int startSdkVersion, int endSdkVersion) {
    this.startSdkVersion = startSdkVersion;
    this.endSdkVersion = endSdkVersion;
  }

  /** Returns start SDK version for given {@link AndroidVersion}. */
  public int getStartSdkVersion() {
    return this.startSdkVersion;
  }

  /** Returns end SDK version for given {@link AndroidVersion}. */
  public int getEndSdkVersion() {
    return this.endSdkVersion;
  }

  public static AndroidVersion getLatestAndroidVersion() {
    return LATEST_ANDROID_VERSION;
  }
}
