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

package com.google.devtools.mobileharness.platform.android.systemspec;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.error.MoreThrowables.shortDebugString;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.UsbDeviceLocator;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AndroidEmulatorIds;
import com.google.wireless.qa.mobileharness.shared.proto.AndroidDeviceSpec.Abi;
import com.google.wireless.qa.mobileharness.shared.util.LuhnUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Utility class to query Android device hardware related specs. */
public class AndroidSystemSpecUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The vendor of display panel. */
  private enum PanelVendor {
    BOE(
        "boe",
        ImmutableSet.of(
            "boe-nt37290",
            "boe-ts110f5mlg0",
            "google-bigsurf",
            "google-tk4b",
            "google-tk4d",
            "google-ct3b",
            "google-ct3d",
            "google-tg4b",
            "google-tg4c",
            "google-tkicb",
            "google-fleb",
            "google-staea",
            "google-dcsdb")),
    CSOT("csot", ImmutableSet.of("csot-ppa957db2d", "google-tkicc")),
    SDC("sdc", ImmutableSet.of());

    private final String vendorName;
    private final ImmutableSet<String> panelNames;

    PanelVendor(String vendorName, ImmutableSet<String> panelNames) {
      this.vendorName = vendorName;
      this.panelNames = panelNames;
    }

    @Override
    public String toString() {
      return vendorName;
    }

    static PanelVendor fromPanelName(String panelName) {
      if (BOE.panelNames.contains(panelName)) {
        return BOE;
      }
      if (CSOT.panelNames.contains(panelName)) {
        return CSOT;
      }
      return SDC;
    }
  }

  /**
   * Pattern to match USB ID in "adb devices -l" like "usb:2-5" whose USB host bus is 2 and USB host
   * bus port is 5.
   */
  private static final Pattern OUTPUT_USB_ID_PATTERN =
      Pattern.compile("(?<bus>\\d+)-(?<port>(\\d+\\.)*\\d+)");

  @VisibleForTesting
  static final String ADB_SHELL_GET_HINGE_ANGLE = "sensor_test sample --num-samples 1 --sensor 36";

  private static final Pattern PATTERN_SENSOR_SAMPLE_DATA =
      Pattern.compile(
          "Sensor: (?<id>[\\d\\.]+) TS: (?<timestamp>\\d+) "
              + "Data: (?<dataX>[\\d\\.]+) (?<dataY>[\\d\\.]+) (?<dataZ>[\\d\\.]+)");

  /** ADB shell command for getting iphonesubinfo of the device. */
  @VisibleForTesting
  static final String ADB_SHELL_IPHONE_SUBINFO_TEMPLATE = "service call iphonesubinfo %d";

  /** Output of services not exist. */
  private static final String OUTPUT_SERVICE_NOT_EXIST = "Service %s does not exist";

  /** The pattern of device's IMEI. */
  private static final Pattern PATTERN_IMEI = Pattern.compile("'([\\d\\. ]+)'");

  /** The pattern of device's ICCID. */
  private static final Pattern PATTERN_ICCID = Pattern.compile("'([\\d\\. ]+)'");

  /** ADB shell command for getting the wifi mac address. */
  @VisibleForTesting
  static final String ADB_SHELL_GET_WIFI_MAC_ADDRESS = "cat /sys/class/net/wlan0/address";

  /** ADB shell command for getting the bluetooth mac address. */
  @VisibleForTesting
  static final String ADB_SHELL_GET_BLUETOOTH_MAC_ADDRESS = "settings get secure bluetooth_address";

  /** ADB shell command for getting the machine hardware name. */
  @VisibleForTesting static final String ADB_SHELL_GET_MACHINE_HARDWARE_NAME = "uname -m";

  /** ADB shell command for getting cpu info. */
  @VisibleForTesting static final String ADB_SHELL_GET_CPU_INFO = "cat /proc/cpuinfo";

  /** ADB shell command for retrieving max cpu frequency. */
  @VisibleForTesting
  static final String ADB_SHELL_GET_CPU_MAX_FREQ =
      "cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq";

  /** ADB shell command for listing all features of device. */
  @VisibleForTesting static final String ADB_SHELL_LIST_FEATURES = "pm list features";

  /** Output of starting characters of system features on device. */
  private static final String OUTPUT_FEATURE_STARTING_PATTERN = "feature:";

  /** ADB shell command for getting total memory. */
  @VisibleForTesting static final String ADB_SHELL_GET_TOTAL_MEM = "cat /proc/meminfo";

  /** ADB shell command to query SIM info. */
  @VisibleForTesting
  static final String ADB_SHELL_QUERY_SIM_INFO = "content query --uri content://telephony/siminfo";

  /** ADB shell command to query modem secure boot status. */
  @VisibleForTesting
  static final String ADB_SHELL_SECURE_BOOT_STATUS =
      "am instrument -w -e request 'at+googsecurebootstatus' -e response wait"
          + " com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation";

  /** ADB shell command to get subscription info. */
  @VisibleForTesting static final String ADB_SHELL_GET_SUBSCRIPTION_INFO = "dumpsys isub";

  /** Pattern to find carrier IDs from queried SIM info. */
  private static final Pattern PATTERN_SIM_INFO_CARRIER_ID =
      Pattern.compile("\\bcarrier_id=(-?\\d+)");

  /** Pattern to find ICCIDs from queried SIM info. */
  private static final Pattern PATTERN_SIM_INFO_ICCID = Pattern.compile("\\bicc_id=(\\d+)");

  /** Pattern to find physical SIM slot from queried SIM info. */
  private static final Pattern PATTERN_SIM_INFO_PHYSICAL_SLOT = Pattern.compile("\\bsim_id=-?\\d+");

  /** Pattern to find whether a SIM is an ESIM from queried SIM info. */
  private static final Pattern PATTERN_SIM_INFO_IS_ESIM = Pattern.compile("\\bis_embedded=\\d+");

  /** Pattern to find subscription type of a SIM from queried SIM info. */
  private static final Pattern PATTERN_SIM_INFO_SUB_TYPE =
      Pattern.compile("\\bsubscription_type=\\d+");

  /** Pattern to find the Unsolicited Result Code (URC) from an AT command. */
  private static final Pattern PATTERN_AT_COMMAND_URC = Pattern.compile("\\+(.+)");

  /** Pattern to find the Unsolicited Result Code (URC) number from an AT command. */
  private static final Pattern PATTERN_AT_COMMAND_NUMBER_URC = Pattern.compile("([0-9]+)");

  /** Pattern to find the active modem count from queried subscription info. */
  private static final Pattern PATTERN_ACTIVE_MODEM_COUNT =
      Pattern.compile("\\bActive modem count=(\\d+)");

  /** Output signal of getting total memory info. */
  private static final String OUTPUT_TOTAL_MEM_INFO = "MemTotal";

  /** Feature returned from 'adb shell pm list features' on Android TV devices. */
  @VisibleForTesting
  static final String FEATURE_TV = OUTPUT_FEATURE_STARTING_PATTERN + "android.software.leanback";

  /** Result of 'adb getprop ro.hardware.type' on automotive devices. */
  @VisibleForTesting static final String AUTOMOTIVE_TYPE = "automotive";

  /** Feature returned from 'adb shell pm list features' on Android Auto devices. */
  @VisibleForTesting
  static final String FEATURE_AUTOMOTIVE =
      OUTPUT_FEATURE_STARTING_PATTERN + "android.hardware.type.automotive";

  /** Feature returned from 'adb shell pm list features' on devices with low ram. */
  @VisibleForTesting
  static final String FEATURE_LOW_RAM =
      OUTPUT_FEATURE_STARTING_PATTERN + "android.hardware.ram.low";

  /** Feature returned from 'adb shell pm list features' on wearables. */
  @VisibleForTesting
  static final String FEATURE_WEARABLE =
      OUTPUT_FEATURE_STARTING_PATTERN + "android.hardware.type.watch";

  /**
   * Contains by the result of 'adb getprop ro.build.characteristics' on wearables.
   * http://b/232451871#comment12.
   */
  @VisibleForTesting static final String CHARACTERISTIC_WEARABLE = "watch";

  /** Pattern to match feature returned from 'adb shell pm list features' on pixel devices. */
  private static final Pattern PATTERN_FEATURE_PIXEL =
      Pattern.compile(
          OUTPUT_FEATURE_STARTING_PATTERN
              + "com\\.google\\.android\\.feature\\.(PIXEL|GOOGLE)_EXPERIENCE");

  /** ADB shell command to get storage lifetime. This does not work in old android builds. */
  @VisibleForTesting static final String ADB_SHELL_GET_STORAGE_LIFETIME = "tradeinmode getstatus";

  /** Linux shell command to get kernel release number. */
  @VisibleForTesting static final String LINUX_SHELL_GET_KERNEL_RELEASE = "uname -r";

  // GKI kernel release pattern: w.x.y-zzz-kmiGen-something-abBuildId-suffix. See
  // https://source.android.com/docs/core/architecture/kernel/gki-versioning#determine-release
  // But the ending must contain ab build id.
  private static final Pattern GKI_KERNEL_RELEASE_NUMBER_PATTERN =
      Pattern.compile(
          "^(?<w>\\d+)[.](?<x>\\d+)[.](?<y>\\d+)-(?<zzz>(android\\d+|mainline))-(?<kmigen>\\d+)-.*(?<abid>ab\\d+)(-.+)?$");

  /** The pattern of storage lifetime. */
  private static final Pattern PATTERN_STORAGE_LIFETIME =
      Pattern.compile("\"useful_lifetime_remaining\":\\s*(\\d+)");

  /** ADB shell command for getting panel name on legacy exynos devices. */
  @VisibleForTesting
  static final String ADB_SHELL_GET_PANEL_NAME_LEGACY =
      "cat /sys/devices/platform/exynos-drm/primary-panel/panel_name";

  /** ADB shell command for getting panel name on new devices. */
  @VisibleForTesting
  static final String ADB_SHELL_GET_PANEL_NAME_NEW =
      "mount -t debugfs debugfs /sys/kernel/debug; cat /d/dri/0/DSI-1/panel/name";

  private final Adb adb;

  private final AndroidAdbInternalUtil adbInternalUtil;

  private final AndroidAdbUtil adbUtil;

  public AndroidSystemSpecUtil() {
    this(new Adb(), new AndroidAdbInternalUtil(), new AndroidAdbUtil());
  }

  @VisibleForTesting
  AndroidSystemSpecUtil(Adb adb, AndroidAdbInternalUtil adbInternalUtil, AndroidAdbUtil adbUtil) {
    this.adb = adb;
    this.adbInternalUtil = adbInternalUtil;
    this.adbUtil = adbUtil;
  }

  /** Gets the ABI of device. */
  public Abi getDeviceAbi(String serial) throws MobileHarnessException, InterruptedException {
    String abiRawString = "";
    try {
      abiRawString = adbUtil.getProperty(serial, AndroidProperty.ABI);
      return Abi.valueOf(Ascii.toUpperCase(abiRawString.replace('-', '_')));
    } catch (IllegalArgumentException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SPEC_INVALID_ABI,
          String.format("Unrecognized ABI: %s.", abiRawString),
          e);
    }
  }

  /**
   * Gets ICCID of the device.
   *
   * @param serial the serial number of the device
   * @return the ICCID of the device
   * @throws MobileHarnessException if some unexpected error occurs during iphonesubinfo command
   */
  public Optional<String> getDeviceIccid(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    String command =
        String.format(ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, getIccidCommandNumber(sdkVersion));
    String adbOutput = null;
    try {
      adbOutput = adb.runShell(serial, command);
    } catch (MobileHarnessException e) {
      if (e.getMessage().contains(String.format(OUTPUT_SERVICE_NOT_EXIST, "iphonesubinfo"))) {
        logger.atWarning().log("Device %s doesn't support Telephony.", serial);
        return Optional.empty();
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_ICCID_ERROR, e.getMessage(), e);
    }

    Matcher iccidMatcher = PATTERN_ICCID.matcher(adbOutput);

    StringBuilder iccidBuilder = new StringBuilder();
    while (iccidMatcher.find()) {
      iccidBuilder.append(iccidMatcher.group(1).replaceAll("\\D", ""));
    }

    String iccid = iccidBuilder.toString();
    if (iccid.length() == 0) {
      return Optional.empty();
    }
    // Check that retrieved ICCID is correct using Luhn algorithm.
    // Also make sure that returned ICCID is not just a series of zeroes,
    // because some devices return zeroes instead of empty result to indicate an absence of ICCID.
    // Also make sure that the ICCID includes the correct prefix.
    if (!LuhnUtil.checkSequence(iccid) || iccid.matches("^0+$") || !iccid.startsWith("89")) {
      logger.atWarning().log("%s is not a valid ICCID for device %s.", iccid, serial);
      return Optional.empty();
    }
    return Optional.of(iccid);
  }

  /**
   * Gets the IMEI of the device.
   *
   * @param serial serial number of the device.
   * @return {@link Optional} containing IMEI of the device if device supports telephony, and {@link
   *     Optional#empty()} otherwise. Emulators do not support telephony, thus {@link
   *     Optional#empty()} is returned on emulator devices. API level 10 is tested.
   * @throws MobileHarnessException if some error occurs in executing system commands.
   * @throws InterruptedException if current thread is interrupted during this method.
   */
  public Optional<String> getDeviceImei(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    String command =
        String.format(ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, getImeiCommandNumber(sdkVersion));
    String adbOutput = null;
    try {
      adbOutput = adb.runShell(serial, command);
    } catch (MobileHarnessException e) {
      if (e.getMessage().contains(String.format(OUTPUT_SERVICE_NOT_EXIST, "iphonesubinfo"))) {
        logger.atWarning().log("Device %s doesn't support Telephony.", serial);
        return Optional.empty();
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_IMEI_ERROR, e.getMessage(), e);
    }

    Matcher imeiMatcher = PATTERN_IMEI.matcher(adbOutput);

    StringBuilder imeiBuilder = new StringBuilder();
    while (imeiMatcher.find()) {
      imeiBuilder.append(imeiMatcher.group(1).replaceAll("\\D", ""));
    }

    String imei = imeiBuilder.toString();
    if (imei.length() == 0) {
      return Optional.empty();
    }
    // Check that retrieved IMEI is correct using Luhn algorithm.
    // Also make sure that returned IMEI is not just a series of zeroes,
    // because some devices return zeroes instead of empty result to indicate an absence of IMEI.
    if (!LuhnUtil.checkSequence(imei) || imei.matches("^0+$")) {
      logger.atWarning().log("%s is not a valid IMEI for device %s.", imei, serial);
      return Optional.empty();
    }
    return Optional.of(imei);
  }

  /**
   * Gets wifi MAC address of the device.
   *
   * <p>Support production builds, require API >= 15 for real devices, API >= 25 for emulators.
   */
  public String getMacAddress(String serial) throws MobileHarnessException, InterruptedException {
    String output = "";
    try {
      output = adb.runShellWithRetry(serial, ADB_SHELL_GET_WIFI_MAC_ADDRESS).trim();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_MAC_ADDRESS_ERROR, e.getMessage(), e);
    }
    // Sample output:
    // ac:cf:85:2a:3f:8a
    return output;
  }

  /**
   * Gets bluetooth MAC address of the device.
   *
   * <p>Support production builds, tested on API >= 23 real devices.
   */
  public String getBluetoothMacAddress(String serial)
      throws MobileHarnessException, InterruptedException {
    String output = "";
    try {
      output =
          Ascii.toLowerCase(
              adb.runShellWithRetry(serial, ADB_SHELL_GET_BLUETOOTH_MAC_ADDRESS).trim());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_BLUETOOTH_MAC_ADDRESS_ERROR, e.getMessage(), e);
    }
    // Sample output:
    // 00:9a:cd:56:0e:88
    return output;
  }

  /**
   * Gets the machine hardware name, as given by {@code uname -m}.
   *
   * @param serial the serial number of the device
   * @throws MobileHarnessException if some error occurs in executing system commands, or memory
   *     info not found
   */
  public String getMachineHardwareName(String serial)
      throws MobileHarnessException, InterruptedException {
    try {
      return adb.runShellWithRetry(serial, ADB_SHELL_GET_MACHINE_HARDWARE_NAME).trim();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_MACHINE_HARDWARE_NAME_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Gets the number of CPUs on an android device.
   *
   * @param serial the serial number of the device
   * @return the number of CPUs on the device
   * @throws MobileHarnessException if some error occurs in executing system commands, or memory
   *     info not found
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public int getNumberOfCpus(String serial) throws MobileHarnessException, InterruptedException {
    String output = "";
    try {
      output = adb.runShellWithRetry(serial, ADB_SHELL_GET_CPU_INFO);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_CPU_INFO_ERROR, e.getMessage(), e);
    }
    // Sample output:
    // Processor       : ARMv7 Processor rev 0 (v7l)
    // processor       : 0
    // BogoMIPS        : 13.53
    //
    // processor       : 1
    // BogoMIPS        : 13.53
    //
    // processor       : 2
    // BogoMIPS        : 13.53
    //
    // processor       : 3
    // BogoMIPS        : 13.53
    int numCpus = 0;
    for (String line : Splitters.LINE_SPLITTER.split(output)) {
      line = line.trim();
      if (line.startsWith("processor")) {
        numCpus++;
      }
    }
    if (numCpus > 0) {
      return numCpus;
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_SYSTEM_SPEC_NO_CPU_FOUND, "No CPUs found:\n" + output);
  }

  /**
   * Gets the max CPU frequency in Hertz, or 0 if the information is not available.
   *
   * @param serial the serial number of the device
   */
  public int getMaxCpuFrequency(String serial) throws InterruptedException {
    try {
      String output = adb.runShell(serial, ADB_SHELL_GET_CPU_MAX_FREQ);
      return Integer.parseInt(output.trim());
    } catch (MobileHarnessException | NumberFormatException e) {
      logger.atWarning().withCause(e).log(
          "Device %s does not support retrieving max cpu frequency.", serial);
      return 0;
    }
  }

  /**
   * Gets the system features for device.
   *
   * @param serial device serial identifier
   * @return the set of system features, as output by "adb shell pm list features".
   */
  public Set<String> getSystemFeatures(String serial)
      throws MobileHarnessException, InterruptedException {
    String output = "";
    try {
      output = adb.runShellWithRetry(serial, ADB_SHELL_LIST_FEATURES);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SPEC_LIST_FEATURES_ERROR, e.getMessage(), e);
    }
    Splitter splitter = Splitter.onPattern("\r\n|\n|\r|,").omitEmptyStrings().trimResults();
    Set<String> features = new HashSet<>();
    for (String line : splitter.split(output)) {
      if (line.startsWith(OUTPUT_FEATURE_STARTING_PATTERN)) {
        features.add(line);
      }
    }
    return features;
  }

  /**
   * Gets the total memory usage in KB of an android device.
   *
   * @param serial the serial number of the device
   * @return the total memory of the device
   * @throws MobileHarnessException if some error occurs in executing system commands, or memory
   *     info not found
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public int getTotalMem(String serial) throws MobileHarnessException, InterruptedException {
    String output = "";
    try {
      output = adb.runShellWithRetry(serial, ADB_SHELL_GET_TOTAL_MEM);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_TOTAL_MEM_ERROR, e.getMessage(), e);
    }
    // Sample output:
    // MemTotal:         742868 kB
    // MemFree:          278656 kB
    // Buffers:           93824 kB
    // Cached:           139016 kB
    for (String line : Splitters.LINE_SPLITTER.omitEmptyStrings().trimResults().split(output)) {
      if (line.startsWith(OUTPUT_TOTAL_MEM_INFO)) {
        List<String> infos = Splitter.onPattern("[ \t]+").splitToList(line);
        if (infos.size() >= 3) {
          // The 2nd column is total memory value
          String str = infos.get(1);
          int value;
          try {
            value = Integer.parseInt(str);
          } catch (NumberFormatException e) {
            throw new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_SPEC_INVALID_TOTAL_MEM_VALUE,
                String.format("Value of the total memory is illegal; expect integer, got %s", str),
                e);
          }
          if (value < 0) {
            throw new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_SPEC_INVALID_TOTAL_MEM_VALUE,
                String.format(
                    "Value of the total memory is illegal; expect greater than 0, got %d", value));
          }
          return value;
        }
      }
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_SYSTEM_SPEC_TOTAL_MEM_VALUE_NOT_FOUND,
        "Memory info not found:\n" + output);
  }

  /**
   * Returns the per-application memory class.
   *
   * <p>This represents an approximate memory limit Android applications should impose on themselves
   * to let the overall system work best. Logic derived from {@code
   * ActivityManager::staticGetMemoryClass()}.
   */
  public int getMemoryClassInMb(String serial) throws MobileHarnessException, InterruptedException {
    String value = adbUtil.getProperty(serial, AndroidProperty.MEMORY_CLASS);
    // Currently, `m` is the only valid, used suffix
    if (Strings.isNullOrEmpty(value) || !value.endsWith("m")) {
      return 0;
    }
    try {
      return Integer.parseInt(value, 0, value.length() - 1, 10);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * Gets {@link UsbDeviceLocator} of an Android <b>real</b> device by its serial.
   *
   * <p>For example, if "adb devices -l" returns:
   *
   * <pre>
   * List of devices attached
   * 014994B00D014014  device  usb:1-2 ...
   * 363005DC750400EC  device  usb:3-11.4 ...
   * ...</pre>
   *
   * When env ADB_LIBUSB=1, the prefix "usb:" is omitted and "adb devices -l" returns:
   *
   * <pre>
   * List of devices attached
   * 014994B00D014014  device  1-2 ...
   * 363005DC750400EC  device  3-11.4 ...
   * ...</pre>
   *
   * This method for "014994B00D014014" will return UsbDeviceLocator.of(1, "2"). This method for
   * "363005DC750400EC" will return UsbDeviceLocator.of(3, "11.4").
   *
   * <p>In the case of device "363005DC750400EC", 3 indicates its bus number while "11.4" means port
   * 11, port 4. Between the two ports there must be a hub.
   *
   * @param serial the serial of the Android real device. Note that although a serial number
   *     returned by {@link AndroidAdbInternalUtil#getDeviceSerials()} may not be a real serial
   *     number but a USB ID like "usb:2-5" or "2-5", this "serial number" ("usb:2-5") can still be
   *     used by this method. In detail, if {@code serial} starts with "usb:", this method will not
   *     call "adb devices -l" but parse the serial to get the USB locator directly.
   */
  public UsbDeviceLocator getUsbLocator(String serial)
      throws MobileHarnessException, InterruptedException {
    String usbId;
    if (serial.startsWith(AndroidAdbInternalUtil.OUTPUT_USB_ID_TOKEN)) {
      usbId = serial;
    } else {
      ImmutableList<String> deviceLines = adbInternalUtil.listDevices(/* timeout= */ null);
      try {
        usbId =
            deviceLines.stream()
                .map(deviceLine -> deviceLine.split("\\s+"))
                .filter(deviceWords -> serial.equals(deviceWords[0]))
                .map(deviceWords -> deviceWords[2])
                .findFirst()
                .orElseThrow(
                    () ->
                        new MobileHarnessException(
                            AndroidErrorId.ANDROID_SYSTEM_SPEC_USB_LOCATOR_SERIAL_NOT_FOUND,
                            String.format(
                                "Failed to get USB locator because "
                                    + "[%s] is not found by adb devices",
                                serial)));
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_SYSTEM_SPEC_USB_LOCATOR_ADB_INVALID_LINE,
            "USB ID not found from adb devices, maybe it is an emulator?",
            e);
      }
    }
    String rawUsbId = usbId.replace(AndroidAdbInternalUtil.OUTPUT_USB_ID_TOKEN, "");
    Matcher matcher = OUTPUT_USB_ID_PATTERN.matcher(rawUsbId);
    if (matcher.matches()) {
      return UsbDeviceLocator.of(Integer.parseInt(matcher.group("bus")), matcher.group("port"));
    } else {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SPEC_USB_LOCATOR_INVALID_USB_ID,
          String.format("Invalid USB ID [%s] of device [%s]", usbId, serial));
    }
  }

  public Optional<String> getKernelReleaseNumber(String serial) {
    try {
      return Optional.of(adb.runShellWithRetry(serial, LINUX_SHELL_GET_KERNEL_RELEASE).trim());
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get kernel release number for device %s: %s", serial, shortDebugString(e));
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.atWarning().log(
          "Failed to get kernel release number for device %s: %s", serial, shortDebugString(e));
      return Optional.empty();
    }
  }

  public static boolean isGkiKernel(String kernelReleaseNumber) {
    return GKI_KERNEL_RELEASE_NUMBER_PATTERN.matcher(kernelReleaseNumber).matches();
  }

  /** Checks whether the given device ID belongs to an emulator. */
  public static boolean isAndroidEmulator(String deviceId) {
    return AndroidEmulatorIds.isAndroidEmulator(deviceId);
  }

  /** Check whether the given device ID is of type Cuttlefish. */
  public boolean isCuttlefishEmulator(String serial)
      throws MobileHarnessException, InterruptedException {
    return adbUtil.getProperty(serial, AndroidProperty.MODEL).startsWith("Cuttlefish")
        || adbUtil.getProperty(serial, AndroidProperty.MODEL).startsWith("cf_x86_64");
  }

  /**
   * Checks whether a device is an Android TV device.
   *
   * @param serial serial number of the device
   * @return true if device is automotive, false otherwise
   * @throws MobileHarnessException if error occurs when reading system features
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public boolean isAndroidTvDevice(String serial)
      throws MobileHarnessException, InterruptedException {
    return getSystemFeatures(serial).contains(FEATURE_TV);
  }

  /**
   * Checks whether a device is automotive.
   *
   * @param serial serial number of the device
   * @return true if device is automotive, false otherwise
   * @throws MobileHarnessException if error occurs when reading the {@link
   *     AndroidProperty#HARDWARE_TYPE} property
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public boolean isAutomotiveDevice(String serial)
      throws MobileHarnessException, InterruptedException {
    return adbUtil.getProperty(serial, AndroidProperty.HARDWARE_TYPE).equals(AUTOMOTIVE_TYPE)
        || getSystemFeatures(serial).contains(FEATURE_AUTOMOTIVE);
  }

  /** See {@link #isAndroidEmulator(String)}. */
  public boolean isEmulator(String deviceId) {
    return isAndroidEmulator(deviceId);
  }

  /**
   * Checks whether a device is running Android Go.
   *
   * @param serial serial number of the device
   * @return true if device is running Android Go, false otherwise
   * @throws MobileHarnessException if error occurs when reading system features
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public boolean isGoDevice(String serial) throws MobileHarnessException, InterruptedException {
    Set<String> features = getSystemFeatures(serial);
    return features.contains(FEATURE_LOW_RAM) && !features.contains(FEATURE_WEARABLE);
  }

  /**
   * Checks whether a device is on the pixel experience.
   *
   * @param serial serial number of the device
   * @return true if device is on the pixel experience, false otherwise
   * @throws MobileHarnessException if error occurs when reading system features
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public boolean isPixelExperience(String serial)
      throws MobileHarnessException, InterruptedException {
    return getSystemFeatures(serial).stream()
        .anyMatch(e -> PATTERN_FEATURE_PIXEL.matcher(e).matches());
  }

  /**
   * Checks whether a device is a wearable.
   *
   * @param serial serial number of the device
   * @return true if device is wearable, false otherwise
   * @throws MobileHarnessException if error occurs when reading system features
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public boolean isWearableDevice(String serial)
      throws MobileHarnessException, InterruptedException {
    return adbUtil
            .getProperty(serial, AndroidProperty.CHARACTERISTICS)
            .contains(CHARACTERISTIC_WEARABLE)
        || getSystemFeatures(serial).contains(FEATURE_WEARABLE);
  }

  /** Get the hinge angle of rooted foldable device by sampling sensor value. */
  public String getHingeAngle(String serial) throws MobileHarnessException, InterruptedException {
    String output = adb.runShell(serial, ADB_SHELL_GET_HINGE_ANGLE);

    Matcher matcher = PATTERN_SENSOR_SAMPLE_DATA.matcher(output);
    if (matcher.find()) {
      return matcher.group("dataX");
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_SYSTEM_SPEC_SENSOR_SAMPLE_ERROR,
        String.format("Failed to get hinge angle for device [%s]: %s", serial, output));
  }

  /**
   * Returns a list containing the ICCIDs of each valid SIM on the device.
   *
   * <p>Replicates Android's {@link SubscriptionController#getAvailableSubscriptionInfoList(String,
   * String)} which has specific logic for filtering available SIM info from a list of all SIMs that
   * include removed-but-cached SIMs.
   */
  public ImmutableList<String> getIccids(String serial)
      throws MobileHarnessException, InterruptedException {
    return getAvailableSimInfoStream(serial)
        .map(AndroidSystemSpecUtil::iccidFromSimInfo)
        .filter(iccid -> !isNullOrEmpty(iccid))
        .collect(toImmutableList());
  }

  /** Returns a list of carrier IDs of each available SIM on the device. */
  public ImmutableList<String> getCarrierIds(String serial)
      throws MobileHarnessException, InterruptedException {
    return getAvailableSimInfoStream(serial)
        .map(AndroidSystemSpecUtil::carrierIdFromSimInfo)
        .filter(AndroidSystemSpecUtil::isValidCarrierId)
        .collect(toImmutableList());
  }

  /** Returns the radio version (i.e., the baseband version) of the device. */
  public String getRadioVersion(String serial) throws MobileHarnessException, InterruptedException {
    return adbUtil.getProperty(serial, AndroidProperty.BASEBAND_VERSION);
  }

  private Stream<String> getAvailableSimInfoStream(String serial)
      throws MobileHarnessException, InterruptedException {
    String adbOutput = "";
    try {
      adbOutput = adb.runShellWithRetry(serial, ADB_SHELL_QUERY_SIM_INFO);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SPEC_QUERY_SIM_INFO_ERROR, e.getMessage(), e);
    }

    return LINE_SPLITTER.splitToStream(adbOutput).filter(AndroidSystemSpecUtil::isAvailableSim);
  }

  private static final Splitter LINE_SPLITTER = Splitter.on("\n").omitEmptyStrings().trimResults();

  /** Parses the {@code simInfo} string and returns {@code true} if the SIM is available. */
  private static final boolean isAvailableSim(String simInfo) {
    Matcher physicalSlotMatcher = PATTERN_SIM_INFO_PHYSICAL_SLOT.matcher(simInfo);
    Matcher esimMatcher = PATTERN_SIM_INFO_IS_ESIM.matcher(simInfo);
    Matcher subscriptionTypeMatcher = PATTERN_SIM_INFO_SUB_TYPE.matcher(simInfo);

    if (!physicalSlotMatcher.find() || !esimMatcher.find() || !subscriptionTypeMatcher.find()) {
      return false;
    }

    Splitter splitter = Splitter.onPattern("=").trimResults();

    // Invalid physical slot match: "sim_id=-1".
    int physicalSlot = Integer.parseInt(splitter.splitToList(physicalSlotMatcher.group()).get(1));
    // ESIM match: "is_embedded=1".
    boolean isEsim = splitter.splitToList(esimMatcher.group()).get(1).equals("1");
    // Remote SIM match: "subscription_type=1".
    boolean isRemote = splitter.splitToList(subscriptionTypeMatcher.group()).get(1).equals("1");

    return physicalSlot >= 0 || isEsim || isRemote;
  }

  /** Parses the {@code simInfo} string and returns the SIM's ICCID. */
  private static final String iccidFromSimInfo(String simInfo) {
    Matcher matcher = PATTERN_SIM_INFO_ICCID.matcher(simInfo);
    // Example match: "icc_id=89010005475451640413"
    if (!matcher.find()) {
      return "";
    }
    return matcher.group(1);
  }

  /** Parses the {@code simInfo} string and returns the SIM's carrier ID. */
  private static final String carrierIdFromSimInfo(String simInfo) {
    Matcher matcher = PATTERN_SIM_INFO_CARRIER_ID.matcher(simInfo);
    // Example match: "carrier_id=1435"
    if (!matcher.find()) {
      return "";
    }
    return matcher.group(1);
  }

  /** Returns {@code true} if {@code carrierId} is valid. */
  private static final boolean isValidCarrierId(String carrierId) {
    return !isNullOrEmpty(carrierId) && !carrierId.equals("-1");
  }

  /** Gets the appropriate command number in IPhoneSubInfo.aidl to use for looking up ICCID. */
  private static int getIccidCommandNumber(int sdkVersion) {
    if (sdkVersion < 21) {
      // getIccSerialNumber
      return 5;
    }
    if (sdkVersion < 28) {
      // getIccSerialNumberForSubscriber
      return 12;
    }
    if (sdkVersion < 30) {
      // getIccSerialNumberForSubscriber
      return 11;
    }
    // getIccSerialNumberForSubscriber
    return 14;
  }

  /** Gets the appropriate command number in IPhoneSubInfo.aidl to use for looking up IMEI. */
  private static int getImeiCommandNumber(int sdkVersion) {
    if (sdkVersion < 21) {
      // getDeviceId
      return 1;
    }
    if (sdkVersion < 30) {
      // getImeiForSubscriber
      return 4;
    }
    // getImeiForSubscriber
    return 5;
  }

  /**
   * Returns the remaining storage lifetime in percentages, or -1 if the information is unavailable.
   *
   * @param serial the serial number of the device
   */
  public int getStorageLifetime(String serial) throws InterruptedException {
    try {
      String out = adb.runShell(serial, ADB_SHELL_GET_STORAGE_LIFETIME);
      Matcher matcher = PATTERN_STORAGE_LIFETIME.matcher(out);
      if (matcher.find()) {
        return Integer.parseInt(matcher.group(1));
      }
      logger.atWarning().log(
          "Device %s does not support retrieving storage lifetime. Got output: %s", serial, out);
      return -1;
    } catch (MobileHarnessException | NumberFormatException e) {
      logger.atWarning().withCause(e).log(
          "Device %s does not support retrieving storage lifetime.", serial);
      return -1;
    }
  }

  /**
   * Gets the display panel vendor.
   *
   * <p>This method may return {@link Optional#empty()} if panel name could not be retrieved, which
   * may happen on un-rooted new devices.
   *
   * @param serial the serial number of the device
   */
  public Optional<String> getDisplayPanelVendor(String serial) throws InterruptedException {
    String panelName = getDisplayPanelName(serial);
    if (Strings.isNullOrEmpty(panelName)) {
      return Optional.empty();
    }
    return Optional.of(PanelVendor.fromPanelName(panelName).toString());
  }

  /**
   * Gets the display panel name.
   *
   * <p>It first attempts to read panel name using a command for legacy exynos devices. If that
   * fails, and if the device is rooted, it attempts to read panel name using a command for new
   * devices.
   *
   * @param serial the serial number of the device
   */
  private String getDisplayPanelName(String serial) throws InterruptedException {
    try {
      String output = adb.runShell(serial, ADB_SHELL_GET_PANEL_NAME_LEGACY).trim();
      if (!output.isEmpty()
          && !output.contains("No such file")
          && !output.contains("Permission denied")) {
        return output;
      }
    } catch (MobileHarnessException e) {
      // Ignore
    }

    try {
      String output = adb.runShell(serial, ADB_SHELL_GET_PANEL_NAME_NEW).trim();
      if (!output.isEmpty()
          && !output.contains("No such file")
          && !output.contains("Permission denied")) {
        return output;
      }
    } catch (MobileHarnessException e) {
      // Ignore
    }
    return "";
  }
}
