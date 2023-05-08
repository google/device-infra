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

package com.google.wireless.qa.mobileharness.shared.constant;

import com.google.common.base.Ascii;

/** Mobile Harness device dimension constants. */
public final class Dimension {
  /** Dimension names. */
  public enum Name {
    // Shared
    /** Allocation key of the device. */
    ALLOCATION_KEY,
    /** The API level of the device. */
    API_LEVEL,
    /** The cpu architecture of the device, such as armv7, arm64, x86_64. */
    ARCHITECTURE,
    /** The battery level of the device. The value is 0-100. */
    BATTERY_LEVEL,
    /** The battery status of the device. The common value is OK. */
    BATTERY_STATUS,
    /** The battery temperature of the device. The value is in ℃. */
    BATTERY_TEMPERATURE,
    /** The mac address of Bluetooth. */
    BLUETOOTH_MAC_ADDRESS,
    /** The health of CloudRpc2. */
    CLOUDRPC_FAILURE,
    /**
     * The communication type of a device, such as USB, ADB or SSH. The communication details are
     * saved in device properties.
     */
    COMMUNICATION_TYPE,
    /** The control id of the device. */
    CONTROL_ID,
    /** The device name. */
    DEVICE,
    /** Whether a device supports running test in container mode. */
    DEVICE_SUPPORTS_CONTAINER,
    /** Whether a device is supports running on Moreto. */
    DEVICE_SUPPORTS_MORETO,
    /** Whether a device supports running test in sandbox mode. */
    DEVICE_SUPPORTS_SANDBOX,
    /** The disk type. The value can be one of [SSD, HDD, UNKNOWN]. */
    DISK_TYPE,
    /** The id of the device. */
    ID,
    /** Host IP of the lab server. */
    HOST_IP,
    /** Host name of the lab server. */
    HOST_NAME,
    /** OS of the lab server machine. */
    HOST_OS,
    /** OS version of the lab server machine. */
    HOST_OS_VERSION,
    /** Total memory size of the lab server in GB. */
    HOST_TOTAL_MEM,
    /** Version of the lab server machine. */
    HOST_VERSION,
    /** If the wifi is connected. */
    INTERNET,
    /** The lab location of the device. */
    LAB_LOCATION,
    /** The location type of the device. */
    LOCATION_TYPE,
    /** A customized dimension to make devices easier to identify on device list page of FE. */
    LABEL,
    /** Network connection stability. */
    MAC_ADDRESS,
    /** Network provider of the SIM card. */
    MCC_MNC,
    /** The model name of the device. */
    MODEL,
    /** Whether this device is monitored by the lab it connects to. */
    MONITORED,
    /** The network address. */
    NETWORK_ADDRESS,
    /** The network SSID. */
    NETWORK_SSID,
    /** Customized dimension, SSID for network simulation. */
    NETWORK_SIMULATION_NETTROL_BASE_URL,
    /** Customized dimension, PSK for network simulation. */
    NETWORK_SIMULATION_PSK,
    /** Customized dimension, SSID for network simulation. */
    NETWORK_SIMULATION_SSID,
    /** Customized dimension, SSID for network simulation. */
    NETWORK_SIMULATION_USE_NETTROL,
    PING_GOOGLE_STABILITY,
    /** The mac address */
    /** Device pool name. Values include: shared/shared_without_recovery/group_shared/dedicated. */
    POOL,
    /** The release version of the device. */
    RELEASE_VERSION,
    /**
     * The major release version of the device: first two numbers from a 3-number version. For
     * example, if release_version is "6.0.1", then release_version_major is "6.0".
     */
    RELEASE_VERSION_MAJOR,
    /** If the device is rooted or not. */
    ROOTED,
    /** Whether to enable taking screenshot from FE. */
    SCREENSHOT_ABLE,
    /** Whether a lab/device supports running tests on ad hoc testbeds. */
    SUPPORTS_ADHOC,
    /** Whethere a device is responsive on serial. */
    SERIAL_RESPONSIVE,
    /** The total memory size in MB. */
    TOTAL_MEMORY,
    /** Type, usually the product code, e.g., 'shamu', 'marlin'. */
    TYPE,
    /** The wifi strength of the device. */
    WIFI_RSSI,
    /** The uuid of the device. */
    UUID,
    /** Whether the uuid is volatile. */
    UUID_VOLATILE,
    /** IMEI of the device. */
    IMEI,

    // Android
    /** Customized dimension: Android Application Binary Interface. */
    ABI,
    /** Customized dimension: Android Google Search App version */
    AGSA_VERSION,
    /** The brand of the device. */
    BRAND,
    /** The build name of the device. */
    BUILD,
    /** Customized dimension: Chrome version */
    CHROME_VERSION,
    /** The current development codename, or the string "REL" if this is a release build. */
    CODENAME,
    /**
     * The disk status of the device. The common value is OK. If free space is less than 1.0G, this
     * value will be LOW.
     */
    EXTERNAL_STORAGE_STATUS,
    /** FEATURE of the device. */
    FEATURE,
    /** The percentage of free disk on the device. */
    FREE_EXTERNAL_STORAGE_PERCENTAGE,
    /** The free disk space on the device. */
    FREE_EXTERNAL_STORAGE,
    /** The percentage of free internal storage on the device. */
    FREE_INTERNAL_STORAGE_PERCENTAGE,
    /** The free internal storage space on the device. */
    FREE_INTERNAL_STORAGE,
    /** The version of gms core on the device. */
    GMS_VERSION,
    /** GServices Android ID(go/android-id). */
    GSERVICES_ANDROID_ID,
    /** The hardware name of the device. */
    HARDWARE,
    /**
     * The internal storage status of the device. The common value is OK. If free space is less than
     * 1.0G, this value will be LOW.
     */
    INTERNAL_STORAGE_STATUS,
    /** Launcher 1 package. */
    LAUNCHER_1,
    /** Launcher 3 package. */
    LAUNCHER_3,
    /** The Google Experience Launcher package. */
    LAUNCHER_GEL,
    /** Moreto lab version for Moreto remote control protocol backward compatibility. */
    MORETO_LAB_VERSION,
    /** Number of CPUs on the android device */
    NUM_CPUS,
    /** The oem unlock statue of the device. Only used in fastboot mode. */
    OEM_UNLOCK,
    /** The recovery tag name of the device. */
    RECOVERY,
    /** The screen density. */
    SCREEN_DENSITY,
    /** The screen size. */
    SCREEN_SIZE,
    /**
     * Serial no of the device. For normal Android device, SERIAL = ID. For Arc++, SERIAL is the
     * value of Build.SERIAL while ID is the ip of the device
     */
    SERIAL,
    /** Android SDK version. */
    SDK_VERSION,
    /** Whether the system build is signed. Values includes: dev-keys/release-keys/test-keys. */
    SIGN,
    /** Whether the device is a svelte device. */
    SVELTE_DEVICE,
    /** Supports GmsCore. */
    SUPPORTS_GMSCORE,
    /** The product_board dimension. */
    PRODUCT_BOARD,
    /** The path of writable external storage on the device. */
    WRITABLE_EXTERNAL_STORAGE,

    // iOS
    /** Whether the device has blocked the iOS version upgrade successfully. */
    BLOCKED_IOS_VERSION_UPGRADE,
    /** Whether the device model is a China model(b/35742879). */
    CHINA_MODEL,
    /** Whether the device can run test with custom provisioning profile. */
    CUSTOM_PROFILE,
    /** The free disk storage of the device. */
    DEVICE_FREE_STORAGE,
    /** The free disk storage percentage of the device. */
    DEVICE_FREE_STORAGE_PERCENTAGE,
    /** Google dev identity status on the host mac machine. */
    HOST_IDENTITY_STATUS,
    /** The version of xcode on the host. */
    HOST_XCODE_VERSION,
    /** Whether the device has installed the MH daemon app. */
    INSTALLED_DAEMON,
    /** Network connection stability. */
    NETWORK_CONNECTION_STABILITY,
    /**
     * The identity that the iOS device is provisioned by, the value should be "GOOGLE" or "FTL".
     */
    PROVISIONED_BY,
    /** The version of system on iOS devices. */
    SOFTWARE_VERSION,
    /** Whether the device is supervised. */
    SUPERVISED,
    /** Whether the lab whitelisted the control of system proxy on Mac. */
    WHITELISTED_PROXY_CTL,
    /** ICCID (Integrated Circuit Card Identifier) of the device. */
    ICCID,
    /** A comma-separated string containing the ICCID of each SIM on the device. */
    ICCIDS,

    // lab server level
    /** The disk usable size of the host. */
    ALERT_LAB_DISK_USABLE_SIZE,
    /** Whether the lab the device is connected to supports container-mode test. */
    LAB_SUPPORTS_CONTAINER,
    /** Whether the lab the device is connected to supports sandbox-mode test. */
    LAB_SUPPORTS_SANDBOX,
    /** Shared performance pool */
    PERFORMANCE_POOL,
    /** Sim card info */
    SIM_CARD_INFO,
    /** Pool name used by shared lab. */
    POOL_NAME,
    /** The ATE cluster name */
    CLUSTER;

    public String lowerCaseName() {
      return Ascii.toLowerCase(name());
    }
  }

  /** Values for COMMUNICATION_TYPE */
  public enum CommunicationTypeValue {
    ADB,
    USB,
    SSH,
    VIDEO,
  }

  /** Dimension values. */
  public static final class Value {
    /** Dimension value prefix which indicates it is a regular express. */
    public static final String PREFIX_REGEX = "regex:";

    /** Dimension value all for device dimension. */
    public static final String ALL_VALUE_FOR_DEVICE = "*";

    /** Dimension value for the job requests the device excluding the dimension. */
    public static final String EXCLUDE = "exclude";

    /** Dimension value suffix which represents a Chinese or Mac host name. */
    public static final String SUFFIX_CHINA_HOST_NAME = ".*\\.(bej|sha|roam)\\..*";

    /** Dimension value for public shared pool. */
    public static final String POOL_SHARED = "shared";

    /** Dimension value that excludes the shared pool. */
    public static final String POOL_NONSHARED = "^((?!shared).)*$";

    /** Dimension value for public shared default performance pool. */
    public static final String DEFAULT_PERFORMANCE_POOL_SHARED = "default";

    /** Customized dimension default value. (b/37541448) */
    public static final String CUSTOMIZED_DEFAULT = "init";

    /** Dimension value for unknown dimensions. */
    public static final String UNKNOWN_VALUE = "unknown";

    /** Dimension value for "low" status. */
    public static final String LOW_VALUE = "low";

    /** Dimension value for "ok" status. */
    public static final String OK_VALUE = "ok";

    /** Dimension value for "true" boolean. */
    public static final String TRUE = "true";

    /** Dimension value prefix which indicates it is a ChromeOS device. */
    public static final String PREFIX_CHROME_OS_BOARD = "cros-";

    /** Dimension value for iOS device that is provisioned by GOOGLE. */
    public static final String IOS_PROVISIONED_BY_GOOGLE = "GOOGLE";

    /** Dimension value for iOS device that is provisioned by FTL. */
    public static final String IOS_PROVISIONED_BY_FTL = "FTL";

    /** Dimension value for no sim for sim_card_info dimension. */
    public static final String NO_SIM = "NO_SIM";

    /** Dimension value for default pool_name. */
    public static final String DEFAULT_POOL_NAME = "DEFAULT";

    private Value() {}
  }

  private Dimension() {}
}
