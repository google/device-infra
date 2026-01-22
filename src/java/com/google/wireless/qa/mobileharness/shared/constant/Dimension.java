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
    /** Whether a device is in checkin group. */
    MTAAS_DEVICE_CHECKIN_GROUP,
    /** The health of CloudRpc2. */
    CLOUDRPC_FAILURE,
    /**
     * The communication type of a device, such as USB, ADB or SSH. The communication details are
     * saved in device properties.
     */
    COMMUNICATION_TYPE,
    /** The control id of the device. */
    CONTROL_ID,
    /**
     * The device name.
     *
     * @see com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty#DEVICE
     */
    DEVICE,
    /** Class simple name of the {@code Device} class. */
    DEVICE_CLASS_NAME,
    /** The form of the device. The value can be one of [virtual/physical]. */
    DEVICE_FORM,
    /** The OS of the device. The value can be one of [android/ios/testbed]. */
    OS,
    // The priority of the device to get a test scheduled on it. The value is a number between 0 and
    // 65535. Higher number means higher priority.
    DEVICE_PRIORITY,
    /** Whether a device supports running test in container mode. */
    DEVICE_SUPPORTS_CONTAINER,
    /** Whether a device is supports running on Moreto. */
    DEVICE_SUPPORTS_MORETO,
    /**
     * Whether the device is allowed to boot with dev-key signed bootloader. Applicable to P25
     * devices only.
     */
    DEVKEY_ALLOW,
    /** The disk type. The value can be one of [SSD, HDD, UNKNOWN]. */
    DISK_TYPE,
    /** The type of the DeviceManager. The value can be one of [fusion, mh, mtaas]. */
    DM_TYPE,
    /** The id of the device. */
    ID,
    /** Hinge angle of the foldable device. */
    HINGE_ANGLE,
    /** Host IP of the lab server. */
    HOST_IP,
    /** Host name of the lab server. */
    HOST_NAME,
    /** OS of the lab server machine. */
    HOST_OS,
    /** OS version of the lab server machine. For Linux, it's the Ubuntu version. */
    HOST_OS_VERSION,
    /** Total memory size of the lab server in GB. */
    HOST_TOTAL_MEM,
    /** Version of the lab server machine. */
    HOST_VERSION,
    /** If the wifi is connected. */
    INTERNET,
    /** The health of GCS. */
    GCS_FAILURE,
    /** The operator of the GSM card. */
    GSM_OPERATOR_ALPHA,
    /** The health of GCS. /** The signature of GMS Core. */
    GMSCORE_SIGNATURE,
    /** The lab location of the device. */
    LAB_LOCATION,
    /** The type of the lab: Satellite/SLaaS/Core */
    LAB_TYPE,
    /** The location type of the lab. */
    LOCATION_TYPE,
    /** A customized dimension to make devices easier to identify on device list page of FE. */
    LABEL,
    /** The machine hardware name, as given by {@code uname -m}. */
    MACHINE_HARDWARE_NAME,
    /** Network connection stability. */
    MAC_ADDRESS,
    /** Network provider of the SIM card. */
    MCC_MNC,
    /** The model name of the device. */
    MODEL,
    /** Whether this device is monitored by the lab it connects to. */
    MONITORED,
    /** The monsoon id of the device. */
    MONSOON_ID,
    /** The monsoon status of the device. */
    MONSOON_STATUS,
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
    OMNI_MODE_USAGE,
    PING_GOOGLE_STABILITY,
    /** The average ping time of the device. */
    /** The max ping time of the device. */
    /**
     * Device pool name. Values include:
     * shared/shared_without_recovery/group_shared/partner_shared/dedicated.
     */
    POOL,
    /** Whether the device is reachable from the host via ping. */
    /** Whether the device is reachable from the host via DIAL. */
    /** The URL suffix for DIAL app. */
    DIAL_APP_URL_SUFFIX,
    /** The URL suffix for DIAL rest. */
    DIAL_REST_URL_SUFFIX,
    /** Whether the device has the YouTube app installed. */
    /** The release version of the device. */
    RELEASE_VERSION,
    /**
     * The major release version of the device: first two numbers from a 3-number version. For
     * example, if release_version is "6.0.1", then release_version_major is "6.0".
     */
    RELEASE_VERSION_MAJOR,
    /** The revision of the device. */
    REVISION,
    /** If the device is rooted or not. */
    ROOTED,
    /** The run target of the device. */
    RUN_TARGET,
    /** Whether the device has a device SoC chip ID bound DPM flashed. */
    SBDP_ALLOW,
    /** Whether the device has bootloader AntiRollback check disabled by DPM. */
    SBDP_AR_CHECK,
    /** Whether the device has bootloader AntiRollback counter update disabled by DPM. */
    SBDP_AR_UPDATE,
    /** Whether to enable taking screenshot from FE. */
    SCREENSHOT_ABLE,
    /** The device secure boot status (LifeCycleState, LCS). */
    SECURE_BOOT,
    /** Skip SUW by disabling the app. The value should be the package name of the SUW app. */
    SKIP_SUW_APP,
    /** The SoC ID of the device. */
    SOC_ID,
    /** Whether a lab/device supports running tests on ad hoc testbeds. */
    SUPPORTS_ADHOC,
    /** Whether a device is responsive on serial. */
    SERIAL_RESPONSIVE,
    /** The total memory size in MB. */
    TOTAL_MEMORY,
    /** The virtual device id in Tradefed. */
    TRADEFED_VIRTUAL_DEVICE_ID,
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
    /** Type of the power switch supplying power to the device. */
    POWER_SWITCH_TYPE,
    /** IP Address of the power switch supplying power to the device. */
    POWER_SWITCH_IP_ADDRESS,
    /** MAC Address of the power switch supplying power to the device. */
    POWER_SWITCH_MAC_ADDRESS,
    /** Port number/load number of the outlet of the power switch supplying power to the device. */
    POWER_SWITCH_PORT,

    // Android
    /** Customized dimension: Android Application Binary Interface. */
    ABI,
    /** Customized dimension: Android Google Search App version */
    AGSA_VERSION,
    /** The Pixel bootloader Non-Secure AP Anti-Rollback counter */
    AP_AR_NS,
    /** The Pixel bootloader Secure AP Anti-Rollback counter */
    AP_AR_S,
    /** The Pixel bootloader force update Anti-Rollback */
    AR_FORCE_UPDATE,
    /** The Pixel bootloader allow Anti-Rollback update via force_ar enabled */
    AR_UPDATE_ALLOW,
    /** The brand of the device. */
    BRAND,
    /** The build name of the device. */
    BUILD,
    /**
     * The alias of the build name.
     *
     * @see
     *     com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty#BUILD_ALIAS
     */
    BUILD_ALIAS,
    /** Customized dimension: Chrome version */
    CHROME_VERSION,
    /** The current development codename, or the string "REL" if this is a release build. */
    CODENAME,
    /** Max CPU frequency in Ghz. Precision is 1 decimal place. */
    CPU_FREQ_IN_GHZ,
    /** The display panel vendor of the device. */
    DISPLAY_PANEL_VENDOR,
    /**
     * The disk status of the device. The common value is OK. If free space is less than {@link
     * com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.realdevice.AndroidRealDeviceConstants#FREE_EXTERNAL_STORAGE_ALERT_MB},
     * this value will be LOW.
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
    /** GServices Android ID. */
    GSERVICES_ANDROID_ID,
    /** The hardware name of the device. */
    HARDWARE,
    /** The hardware UFS of the device. */
    HARDWARE_UFS,
    /**
     * The internal storage status of the device. The common value is OK. If free space is less than
     * {@link com.google.devtools.mobileharness.shared.util.flags.Flags#internalStorageAlertMb},
     * this value will be LOW.
     */
    INTERNAL_STORAGE_STATUS,
    /** Is the kernel GKI. The value can be one of [true, false]. */
    IS_GKI_KERNEL,
    /** The kernel release number of the device. */
    KERNEL_RELEASE_NUMBER,
    /** Launcher 1 package. */
    LAUNCHER_1,
    /** Launcher 3 package. */
    LAUNCHER_3,
    /** The Google Experience Launcher package. */
    LAUNCHER_GEL,
    /** The manufacturer of the device. */
    MANUFACTURER,
    /**
     * The approximate per-application memory class. Provides a memory limit for applications to let
     * the overall system work best.
     */
    MEMORY_CLASS_IN_MB,
    /** Moreto lab version for Moreto remote control protocol backward compatibility. */
    MORETO_LAB_VERSION,
    /** The Pixel bootloader Non-Secure AP Anti-Rollback counter. */
    NONSEC_AR,
    /** Number of CPUs on the android device */
    NUM_CPUS,
    /** The oem unlock statue of the device. Only used in fastboot mode. */
    OEM_UNLOCK,
    /** Indicate if try to reboot device to recover it. Only used in fastboot mode. */
    REBOOT_TO_RECOVER,
    /** The recovery tag name of the device. */
    RECOVERY,
    /**
     * The recovery status of the device. When set to dirty, it is waiting for recovery. When this
     * dimension doesn't exist, it means the device has been recovered.
     */
    RECOVERY_STATUS,
    /** The screen density. */
    SCREEN_DENSITY,
    /** The screen size. */
    SCREEN_SIZE,
    /** The Pixel bootloader Secure AP Anti-Rollback counter. */
    SEC_AR,
    /**
     * Serial no of the device. For normal Android device, SERIAL = ID. For Arc++, SERIAL is the
     * value of Build.SERIAL while ID is the ip of the device
     */
    SERIAL,
    /** Android SDK version. */
    SDK_VERSION,
    /** Whether the system build is signed. Values includes: dev-keys/release-keys/test-keys. */
    SIGN,
    /** The type of the SIM card. */
    SIM_CARD_TYPE,
    /** The operator of the SIM card. */
    SIM_OPERATOR,
    /** The remaining storage lifetime in percentages. */
    STORAGE_LIFETIME_PERCENTAGE,
    /** Whether the device is a svelte device. */
    SVELTE_DEVICE,
    /** Supports GmsCore. */
    SUPPORTS_GMSCORE,
    /**
     * The product_board dimension.
     *
     * @see
     *     com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty#PRODUCT_BOARD
     */
    PRODUCT_BOARD,
    /**
     * The product_device dimension.
     *
     * @see
     *     com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty#PRODUCT_DEVICE
     */
    PRODUCT_DEVICE,
    /** The path of writable external storage on the device. */
    WRITABLE_EXTERNAL_STORAGE,

    /** Whether the device is locked with the device admin. */
    DEVICE_ADMIN_LOCKED,

    /** Whether the device WIFI configuration is restricted by the device admin. */
    DEVICE_ADMIN_WIFI_RESTRICTED,

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
    /** The iOS simulator id */
    IOS_SIMULATOR_ID,

    // lab server level
    /** The disk usable size of the host. */
    ALERT_LAB_DISK_USABLE_SIZE,
    /** Whether the lab the device is connected to supports container-mode test. */
    LAB_SUPPORTS_CONTAINER,
    /** Whether the lab the device is connected to has an IO issue. */
    LAB_FILE_SYSTEM_IO_ERROR,
    /** Shared performance pool */
    PERFORMANCE_POOL,
    /** Sim card info */
    SIM_CARD_INFO,
    /** Pool name used by shared lab. */
    POOL_NAME,
    /** The ATE cluster name */
    CLUSTER,

    // Riemann's custom dimensions.
    /** A comma-separated string containing the carrier ID of each SIM on the device. */
    CARRIER_IDS,
    /** The radio version (i.e., the baseband version) of the device. */
    RADIO_VERSION,
    /** A comma-separated string containing information of all available SIM cards on the device. */
    SIM_INFOS,
    /**
     * A json format string containing signal strength information of all active SIM cards on the
     * device.
     */
    SIGNAL_STRENGTHS_IN_JSON,
    /** The modem security boot status. */
    MODEM_SECURE_BOOT_STATUS,
    /** The active modem count. */
    ACTIVE_MODEM_COUNT,

    // Used for Wrangler device properties.
    /** The type of the device. */
    DEVICETYPE,
    /** The drivers that the device supports. */
    DRIVER,
    /** The decorators that the device supports. */
    DECORATOR;

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

  /**
   * Values for SIM_CARD_TYPE.
   *
   * <p>These are the same as Tradefed's
   * http://google3/third_party/java_src/tradefederation_core/tools/tradefederation/core/invocation_interfaces/com/android/tradefed/invoker/shard/token/TokenProperty.java;l=23-25;rcl=364381045
   */
  public enum SimCardTypeValue {
    SIM_CARD,
    UICC_SIM_CARD,
    SECURE_ELEMENT_SIM_CARD,
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

    /** Dimension value for partner shared pool. */
    public static final String POOL_PARTNER_SHARED = "partner_shared";

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

    /** Dimension value for "false" boolean. */
    public static final String FALSE = "false";

    /** Dimension value prefix which indicates it is a ChromeOS device. */
    public static final String PREFIX_CHROME_OS_BOARD = "cros-";

    /** Dimension value for iOS device that is provisioned by GOOGLE. */
    public static final String IOS_PROVISIONED_BY_GOOGLE = "GOOGLE";

    /** Dimension value for iOS device that is provisioned by FTL. */
    public static final String IOS_PROVISIONED_BY_FTL = "FTL";

    /** Dimension value for the Google Development provisioning profile in the core lab. */
    public static final String IOS_PROVISIONED_BY_GOOGLE_IN_CORE_LAB =
        "Apple Development: Google Development (69FHSU289T)";

    /** Dimension value for the Firebase Test Lab provisioning profile in the core lab. */
    public static final String IOS_PROVISIONED_BY_FTL_IN_CORE_LAB =
        "iPhone Developer: Firebase Test Lab (2JX573L4Q7)";

    /** Dimension value for no sim for sim_card_info dimension. */
    public static final String NO_SIM = "NO_SIM";

    /** Dimension value for default pool_name. */
    public static final String DEFAULT_POOL_NAME = "DEFAULT";

    /** Dimension value for virtual device. */
    public static final String VIRTUAL = "virtual";

    /** Dimension value for physical device. */
    public static final String PHYSICAL = "physical";

    /** Dimension value for iOS device. */
    public static final String IOS = "ios";

    /** Dimension value for Android device. */
    public static final String ANDROID = "android";

    /** Dimension value for testbed device. */
    public static final String TESTBED = "testbed";

    private Value() {}
  }

  private Dimension() {}
}
