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

package com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.realdevice;

import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import java.time.Duration;
import java.util.regex.Pattern;

/** Constants used by {@link AndroidRealDeviceDelegate}. */
public class AndroidRealDeviceConstants {

  @ParamAnnotation(
      required = false,
      help =
          "Whether to disable activity controller service before running each test. By default, "
              + "it is false so activity controller will be kept running.")
  public static final String PARAM_DISABLE_ACTIVITY_CONTROLLER_ON_TEST =
      "disable_activity_controller_on_test";

  @ParamAnnotation(
      required = false,
      help = "Whether to clear gservice flag overrides before the test (default true)")
  public static final String PARAM_CLEAR_GSERVICES_OVERRIDES = "clear_gservices_overrides";

  /** Name of the device type that indicates the Android device is booted and online */
  public static final String ANDROID_ONLINE_DEVICE = "AndroidOnlineDevice";

  /** Name of the device type whose indicates Android device in fastboot mode. */
  public static final String ANDROID_FASTBOOT_DEVICE = "AndroidFastbootDevice";

  /** Name of the dimension whose indicates Android device could be flashed. */
  public static final String ANDROID_FLASHABLE_DEVICE = "AndroidFlashableDevice";

  /** Name of the device type whose indicates Android device in recovery mode. */
  public static final String ANDROID_RECOVERY_DEVICE = "AndroidRecoveryDevice";

  /** Default user id for system user. */
  public static final int DEFAULT_SYSTEM_USER = 0;

  /** The timeout shift added to default device setup. */
  public static final Duration DEVICE_SETUP_TIMEOUT_SHIFT = Duration.ofSeconds(30);

  /** Device property name for headless system user. */
  public static final String DEVICE_PROP_NAME_HEADLESS_USER = "ro.fw.mu.headless_system_user";

  /** Legacy (P.car) property name for headless system user. */
  public static final String DEVICE_PROP_NAME_HEADLESS_USER_LEGACY =
      "android.car.systemuser.headless";

  /** Android property for network provider of the SIM card. */
  public static final String DEVICE_PROP_NAME_MCC_MNC = "gsm.sim.operator.numeric";

  /** The timeout for waiting the Internet to be ready. */
  public static final Duration WAIT_FOR_INTERNET = Duration.ofMinutes(1);

  /** Name of the launcher 1 package */
  public static final String PACKAGE_NAME_LAUNCHER_1 = "com.android.launcher";

  /** Name of the launcher 3 package */
  public static final String PACKAGE_NAME_LAUNCHER_3 = "com.android.launcher3";

  /** Name of the GEL package */
  public static final String PACKAGE_NAME_LAUNCHER_GEL = "com.google.android.launcher";

  /** Device property(in host machine memory) names of labels to show on device daemon. */
  public static final String PROP_LABELS = "labels";

  /** Device property(in host machine memory) names of hostname to show on device daemon. */
  public static final String PROP_HOSTNAME = "hostname";

  /** Device property(in host machine memory) names of owner list to show on device daemon. */
  public static final String PROP_OWNERS = "owners";

  /** Deprecated device feature indicating it is an AndroidThings (IoT) device. */
  public static final String FEATURE_IOT = "feature:android.hardware.type.iot";

  /** Device feature indicating it is an AndroidThings (IoT) device. */
  public static final String FEATURE_EMBEDDED = "feature:android.hardware.type.embedded";

  /** Device feature indicating it is an Android Automotive device. */
  public static final String FEATURE_AUTOMOTIVE = "feature:android.hardware.type.automotive";

  /** Device feature indicating it is an AndroidVR device. */
  public static final String FEATURE_DAYDREAM_STANDALONE = "feature:android.software.vr.mode";

  /** Device feature indicating it is an AndroidTV device. */
  public static final String FEATURE_LEANBACK = "feature:android.software.leanback";

  /** The device use wipe method to recovery. */
  public static final String RECOVERY_TYPE_WIPE = "wipe";

  /** The device use factory reset via test harness for recovery. */
  public static final String RECOVERY_TYPE_TEST_HARNESS = "test_harness";

  /** Max memory that a device can have and still be considered a Svelte(low-end) device. */
  public static final int MAX_SVELTE_MEMORY_IN_MB = 512;

  /** Number of consecutive setup failure need to reboot devices to fastboot mode. */
  public static final long CONSECUTIVE_SETUP_FAILURE_NUM_TO_FASTBOOT_MODE = 5;

  /** The disk free percentage threshold of disk alert. */
  public static final double DISK_ALERT_FREE_PERCENTAGE = 0.1;

  /** The features used for check gmscore compatibility. */
  public static final ImmutableList<String> GMSCORE_FEATURES_PATTERNS =
      ImmutableList.of(
          "android\\.hardware\\.type\\.watch",
          "android\\.software\\.leanback",
          "android\\.hardware\\.type\\.automotive",
          "android\\.hardware\\.ram\\.low",
          "com\\.google\\.android\\.play\\.feature\\.HPE_EXPERIENCE",
          "android\\.software\\.xr\\.immersive");

  /** The keywords whitelist of features on device. Could be extended. */
  public static final ImmutableList<String> FEATURES_KEYWORDS =
      ImmutableList.<String>builder()
          .addAll(GMSCORE_FEATURES_PATTERNS)
          .add("PIXEL_EXPERIENCE")
          .add("android\\.hardware\\.nfc$")
          .build();

  /** The free external storage space threshold of alert. */
  public static final int FREE_EXTERNAL_STORAGE_ALERT_MB = 200;

  /** Interval of calling google ping command. */
  public static final Duration GOOGLE_PING_INTERVAL = Duration.ofMinutes(30L);

  /** The timeout for retrying fast recovery from fastboot mode. */
  public static final Duration AUTO_RECOVERY_TIMEOUT = Duration.ofMinutes(30L);

  /** The timeout for retrying fast wipe from fastboot mode. */
  public static final Duration AUTO_FASTWIPE_TIMEOUT = Duration.ofMinutes(30L);

  /** The path of lost and found which will be cleaned while device checking. */
  public static final String LOST_FOUND_FILES_PATH = "/data/lost+found/*";

  /** The pattern for choosing certain features. */
  public static final Pattern PATTERN_FEATURES =
      Pattern.compile("(.*)(" + Joiner.on('|').join(FEATURES_KEYWORDS) + ")(.*)");

  /** The path of temp apks which will be cleaned while device checking. */
  public static final String TEMP_APK_PATH = "/data/local/tmp/*.apk";

  public static final String SMLOG_PATH = "/data/smlog_*";

  /**
   * The path of temp screenshot file on device, which will be cleaned after being pulled by host.
   */
  public static final String TEMP_SCREEN_SHOT_PATH = "/data/local/tmp/";

  /** Name of the driver type which indicates no ops for the test. */
  public static final String NO_OP_DRIVER = "NoOpDriver";

  /** Name of the Mobly driver for running tests as .par packages. */
  public static final String MOBLY_TEST_DRIVER = "MoblyTest";

  /** Name of the Mobly driver for running tests from AOSP source distributions. */
  public static final String MOBLY_AOSP_TEST_DRIVER = "MoblyAospTest";

  /** Name of the ACID driver for remote direct access. */
  public static final String ACID_REMOTE_DRIVER = "AcidRemoteDriver";

  /** These services must be available when device gets ready, otherwise cause device reboot. */
  public static final ImmutableList<String> ONLINE_DEVICE_AVAILABLE_SERVICES =
      ImmutableList.of(
          Ascii.toLowerCase(DumpSysType.ACTIVITY.name()),
          Ascii.toLowerCase(DumpSysType.PACKAGE.name()));

  /** Timeout for waiting for reboot. */
  public static final Duration WAIT_FOR_REBOOT_TIMEOUT = Duration.ofMinutes(15);

  public static final String PROPERTY_NAME_REBOOT_TO_STATE = "reboot_to_state";

  public static final String PROPERTY_NAME_ROOTED = "rooted";

  public static final String STRING_INTERNAL = "INTERNAL";

  public static final String STRING_EXTERNAL = "EXTERNAL";

  /** Output signal when file or dir does not exist. */
  public static final String OUTPUT_NO_FILE_OR_DIR = "No such file or directory";

  /** Output of starting characters of system features on device. */
  public static final String OUTPUT_FEATURE_STARTING_PATTERN = "feature:";

  /** Android package manager UID threshold for reboot (10000 ~ 19999) */
  public static final int ANDROID_PACKAGE_MANAGER_UID_THRESHOLD = 19990;

  private AndroidRealDeviceConstants() {}
}
