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

package com.google.devtools.mobileharness.platform.android.systemsetting;

import android.util.DisplayMetrics;
import com.google.wireless.qa.mobileharness.shared.proto.AndroidDeviceSpec.ScreenDensity;

/**
 * Utility class to get screen density from dpi.
 *
 * <p><b>Note: Please do not introduce adb denpendencies to this class. </b>
 */
public class AndroidScreenDensities {

  private AndroidScreenDensities() {}

  public static ScreenDensity getScreenDensityLevel(int dpi) {
    // These decision logic is for an approximate estimate based on
    // http://developer.android.com/guide/practices/screens_support.html
    if (dpi <= (DisplayMetrics.DENSITY_LOW + DisplayMetrics.DENSITY_MEDIUM) / 2) {
      return ScreenDensity.LDPI;
    }
    if (dpi <= (DisplayMetrics.DENSITY_MEDIUM + DisplayMetrics.DENSITY_HIGH) / 2) {
      return ScreenDensity.MDPI;
    }
    if (dpi <= (DisplayMetrics.DENSITY_HIGH + DisplayMetrics.DENSITY_XHIGH) / 2) {
      return ScreenDensity.HDPI;
    }
    if (dpi <= (DisplayMetrics.DENSITY_XHIGH + DisplayMetrics.DENSITY_XXHIGH) / 2) {
      return ScreenDensity.XHDPI;
    }
    if (dpi <= (DisplayMetrics.DENSITY_XXHIGH + DisplayMetrics.DENSITY_XXXHIGH) / 2) {
      return ScreenDensity.XXHDPI;
    }
    return ScreenDensity.XXXHDPI;
  }
}
