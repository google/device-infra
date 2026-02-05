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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.wireless.qa.mobileharness.shared.util.ScreenResolution;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class to get screen resolution of the Android device. */
public class ScreenResolutionUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Regex pattern that is used for detecting device height for api level < 15. */
  private static final Pattern PATTERN_DISPLAY_HEIGHT_MATCH =
      Pattern.compile("DisplayHeight=([0-9]+)");

  /** Regex pattern that is used for detecting device width for api level < 15. */
  private static final Pattern PATTERN_DISPLAY_WIDTH_MATCH =
      Pattern.compile("DisplayWidth=([0-9]+)");

  /** Regex pattern that is used for detecting device resolution for api level >= 15. */
  private static final Pattern PATTERN_RESOLUTION_MATCH =
      getResolutionMatchPattern(/* hasDisplayIdZero= */ true);

  /**
   * Regex pattern that is used for detecting device resolution for api level >= 15 if no
   * mDisplayId=0 is found.
   */
  private static final Pattern PATTERN_RESOLUTION_MATCH_NO_ID =
      getResolutionMatchPattern(/* hasDisplayIdZero= */ false);

  private final AndroidAdbUtil adbUtil;

  public ScreenResolutionUtil() {
    this(new AndroidAdbUtil());
  }

  @VisibleForTesting
  ScreenResolutionUtil(AndroidAdbUtil adbUtil) {
    this.adbUtil = adbUtil;
  }

  /**
   * Get the screen resolution of the given device. It works on api level >=15 and 10.
   *
   * @param serial the serial number of the device
   * @return the screen resolution
   * @throws MobileHarnessException if window information dumped can't be parsed as expected.
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public ScreenResolution getScreenResolution(String serial)
      throws MobileHarnessException, InterruptedException {
    String windowInfo = "";
    try {
      windowInfo = adbUtil.dumpSys(serial, DumpSysType.WINDOW);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_DUMPSYS_WINDOW_ERROR, e.getMessage(), e);
    }
    /*
     * Sample segment of windowInfo for PATTERN_RESOLUTION_MATCH:
     * WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)
     * Display: mDisplayId=0
     * init=1080x1920 420dpi cur=1080x1920 app=1080x1794 rng=1080x1017-1794x1731
     * deferred=false layoutNeeded=false
     * mStacks[1]1
     * mStackId=1
     * mDeferDetach=false
     */
    Matcher matcher = PATTERN_RESOLUTION_MATCH.matcher(windowInfo);
    boolean resolutionFound = matcher.find();
    if (!resolutionFound) {
      logger.atInfo().log(
          "Failed to find mDisplayId=0, falling back to any display for device %s", serial);
      matcher = PATTERN_RESOLUTION_MATCH_NO_ID.matcher(windowInfo);
    }
    if (resolutionFound || matcher.find()) {
      int width = Integer.parseInt(matcher.group("initWidth"));
      int height = Integer.parseInt(matcher.group("initHeight"));
      int curWidth = Integer.parseInt(matcher.group("curWidth"));
      int curHeight = Integer.parseInt(matcher.group("curHeight"));
      ScreenResolution resolution =
          ScreenResolution.createWithOverride(width, height, curWidth, curHeight);
      logger.atInfo().log("Detect device %s resolution: %s", serial, resolution);
      return resolution;
    } else {
      /*
       * Sample segment of windowInfo for PATTERN_DISPLAY_WIDTH_MATCH:
       *  mSystemBooted=true mDisplayEnabled=true
       *  mLayoutNeeded=false mBlurShown=false
       *  no DimAnimator
       *  mInputMethodAnimLayerAdjustment=0  mWallpaperAnimLayerAdjustment=0
       *  mLastWallpaperX=0.5 mLastWallpaperY=0.0
       *  mDisplayFrozen=false mWindowsFreezingScreen=false mAppsFreezingScreen=0
       *  mRotation=0, mForcedAppOrientation=5, mRequestedRotation=0
       *  mAnimationPending=false mWindowAnimationScale=1.0 mTransitionWindowAnimationScale=1.0
       *  mNextAppTransition=0xffffffff, mAppTransitionReady=false, mAppTransitionRunning=false
       *  mStartingIconInTransition=false, mSkipAppTransitionAnimation=false
       *  DisplayWidth=480 DisplayHeight=800
       */
      Matcher widthMatcher = PATTERN_DISPLAY_WIDTH_MATCH.matcher(windowInfo);
      Matcher heightMatcher = PATTERN_DISPLAY_HEIGHT_MATCH.matcher(windowInfo);
      if (widthMatcher.find() && heightMatcher.find()) {
        int width = Integer.parseInt(widthMatcher.group(1));
        int height = Integer.parseInt(heightMatcher.group(1));
        ScreenResolution resolution = ScreenResolution.create(width, height);
        logger.atInfo().log("Detect device %s resolution: %s", serial, resolution);
        return resolution;
      } else {
        logger.atWarning().log("Invalid window information: %s", windowInfo);
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_SYSTEM_SETTING_PARSE_RESOLUTION_ERROR,
            "Fail to parse device resolution from sys window information: " + windowInfo);
      }
    }
  }

  private static Pattern getResolutionMatchPattern(boolean hasDisplayIdZero) {
    return Pattern.compile(
        String.format(
            "Display:%s.*(?:\\r\\n|\\n|\\r)"
                + "\\s*init=(?<initWidth>[0-9]+)x(?<initHeight>[0-9]+).*"
                + "cur=(?<curWidth>[0-9]+)x(?<curHeight>[0-9]+)",
            hasDisplayIdZero ? " mDisplayId=0" : ""));
  }
}
