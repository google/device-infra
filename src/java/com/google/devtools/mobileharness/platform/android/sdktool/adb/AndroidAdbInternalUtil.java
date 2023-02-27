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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.error.MoreThrowables.shortDebugString;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.proto.Adb.AdbInfo;
import com.google.devtools.mobileharness.platform.android.shared.constant.DeviceConstants;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AndroidEmulatorIds;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Utility methods for getting all device info from the same host, and managing adb server.
 *
 * <p>Driver/decorator/plugin should NOT use methods from this util directly as there is no reason a
 * test needs to know about other devices except for the assigned device.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 */
public class AndroidAdbInternalUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** ADB arg to connect a remote device. */
  @VisibleForTesting static final String ADB_ARG_CONNECT = "connect";

  /** ADB args to detach a USB device. */
  private static final String ADB_ARGS_DETACH_DEVICE = "detach";

  /** ADB args to attach a USB device. */
  private static final String ADB_ARGS_ATTACH_DEVICE = "attach";

  /** ADB args to get host features. */
  private static final String[] ADB_ARGS_HOST_FEATURES = new String[] {"host-features"};

  /** The minimum adb version that supports "adb detach". */
  private static final String MINIMUM_ADB_VERSION_SUPPORTING_ADB_DETACH = "31.0.3";

  /** ADB arg to disconnect from a remote device. */
  @VisibleForTesting static final String ADB_ARG_DISCONNECT = "disconnect";

  /** ADB arg for listing the connected devices/emulators. */
  private static final String ADB_ARG_GET_DEVICES = "devices";

  /** ADB args for kill the ADB server. */
  private static final String[] ADB_ARGS_KILL_SERVER = new String[] {"kill-server"};

  /** ADB args for get the ADB verion. */
  private static final String[] ADB_ARGS_GET_VERSION = new String[] {"version"};

  /**
   * @see devtools/mobileharness/platform/android/sdktool/binary/README
   */
  private static final String ADB_DEVICE_BLOCKING_FEATURE_TAG = "MH_ADB_DEVICE_BLOCKING";

  /** The output of "adb host-features" contains "libusb" under ADB_LIBUSB mode */
  private static final String ADB_LIBUSB_FEATURE_TAG = "libusb";

  /** Default timeout for listing devices with "adb devices". */
  private static final Duration LIST_DEVICES_DEFAULT_COMMAND_TIMEOUT = Duration.ofMinutes(2);

  /** Output of "adb devices" command may contain daemon lines before or after the starter line. */
  private static final String OUTPUT_DEVICE_LIST_DAEMON = "* daemon";

  /** Output of "adb devices" command after this line are connected devices information. */
  private static final String OUTPUT_DEVICE_LIST_STARTER = "List of devices attached";

  /** The token used in `adb devices -l` output to mark a USB ID. */
  public static final String OUTPUT_USB_ID_TOKEN = "usb:";

  /** Short timeout for quick operations. */
  private static final Duration SHORT_COMMAND_TIMEOUT = Duration.ofSeconds(5);

  /** Android SDK ADB command line tools executor. */
  private final Adb adb;

  public AndroidAdbInternalUtil() {
    this(new Adb());
  }

  AndroidAdbInternalUtil(Adb adb) {
    this.adb = adb;
  }

  /**
   * Connects to {@code deviceIp} with the default timeout.
   *
   * <p>Should only be called when device is managed by Mobile Harness.
   *
   * @param deviceIp ip address of the device to connect to
   * @throws MobileHarnessException if failed to connect to the IP or timeout
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void connect(String deviceIp) throws MobileHarnessException, InterruptedException {
    connect(deviceIp, /* timeout= */ null);
  }

  /**
   * Connects to {@code deviceIp} with a given timeout.
   *
   * <p>Should only be called when device is managed by Mobile Harness.
   *
   * @param deviceIp ip address of the device to connect to
   * @param timeout time to wait for connection attempt to complete, or null to use default timeout
   * @throws MobileHarnessException if failed to connect to the IP or timeout
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void connect(String deviceIp, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    String output = "";
    try {
      output = adb.run(new String[] {ADB_ARG_CONNECT, deviceIp}, timeout).trim();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_CONNECT_CMD_ERROR, e.getMessage(), e);
    }
    if (output.startsWith("connected to ") || output.startsWith("already connected to")) {
      return;
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_CONNECT_ERROR,
        String.format("Failed to connect to device IP %s: %s", deviceIp, output));
  }

  /**
   * Disconnects from {@code deviceIp}.
   *
   * <p>Should only be called when device is managed by Mobile Harness.
   *
   * @throws MobileHarnessException if failed to disconnect from the IP or timeout
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void disconnect(String deviceIp) throws MobileHarnessException, InterruptedException {
    String output = "";
    try {
      output = adb.run(new String[] {ADB_ARG_DISCONNECT, deviceIp}).trim();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_DISCONNECT_CMD_ERROR, e.getMessage(), e);
    }

    if (output.isEmpty() || output.startsWith("disconnected ")) {
      return;
    }

    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_DISCONNECT_ERROR,
        String.format("Failed to disconnect from device IP %s: %s", deviceIp, output));
  }

  /** Gets information of the current ADB binary. */
  public AdbInfo getAdbInfo() throws MobileHarnessException, InterruptedException {
    String adbVersion = getAdbVersion();
    boolean isDeviceBlockingSupported = adbVersion.contains(ADB_DEVICE_BLOCKING_FEATURE_TAG);
    boolean isLibUsbMode = getHostFeatures().contains(ADB_LIBUSB_FEATURE_TAG);
    String adbVersionNumber = AndroidAdbVersionUtil.getAdbVersionNumber(adbVersion);
    boolean isAdbVersionSupportsDetach = false;
    try {
      isAdbVersionSupportsDetach =
          AndroidAdbVersionUtil.compareAdbVersionNumber(
                  adbVersionNumber, MINIMUM_ADB_VERSION_SUPPORTING_ADB_DETACH)
              >= 0;
    } catch (IllegalArgumentException e) {
      logger.atWarning().withCause(e).log("Failed to compare adb versions.");
    }
    return AdbInfo.newBuilder()
        .setSupportDeviceBlock(isDeviceBlockingSupported)
        .setSupportAdbDetach(isLibUsbMode && isAdbVersionSupportsDetach)
        .build();
  }

  /** Gets the path of the current ADB binary. */
  public String getAdbPath() {
    return adb.getAdbPath();
  }

  /**
   * Gets serial numbers of the current connected Android devices, including real devices and
   * emulators. Returns device serial numbers in specified status base on {@code DeviceState}
   *
   * @param deviceState to specify the device state, set to null to get all kinds of devices
   * @return a set of serial numbers of Android devices, or an empty set if no devices detected
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public Set<String> getDeviceSerialsByState(@Nullable DeviceState deviceState)
      throws MobileHarnessException, InterruptedException {
    return getDeviceSerialsByState(deviceState, /* timeout= */ null);
  }

  /**
   * Gets serial numbers of the current connected Android devices, including real devices and
   * emulators. Returns device serial numbers in specified status base on {@code DeviceState}
   *
   * @param deviceState to specify the device state, set to null to get all kinds of devices
   * @param timeout adb list devices command timeout. Use {@link
   *     #LIST_DEVICES_DEFAULT_COMMAND_TIMEOUT} if {@code timeout} is null.
   * @return a set of serial numbers of Android devices, or an empty set if no devices detected
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public Set<String> getDeviceSerialsByState(
      @Nullable DeviceState deviceState, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return getDeviceSerialsAsMap(timeout).entrySet().stream()
        .filter(entry -> (deviceState == null || entry.getValue().equals(deviceState)))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  /**
   * Gets serial numbers of the current connected Android devices, including real devices and
   * emulators.
   *
   * <p>[b/36397768],[b/152732288] If a device has the known default serial "0123456789ABCDEF" or
   * "0123456789ABCDEF000", get the device's USB id instead of serial number. Many devices in NBU
   * market share this serial number, so if there are multiple such devices connected to the host,
   * we won't be able to operate them properly with serial numbers. So we should use their USB id
   * instead.
   *
   * @return a map of Android device serial to {@link DeviceState}, or an empty map if no devices
   *     detected
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public Map<String, DeviceState> getDeviceSerialsAsMap()
      throws MobileHarnessException, InterruptedException {
    return getDeviceSerialsAsMap(/* timeout= */ null);
  }

  /**
   * Gets serial numbers of the current connected Android devices, including real devices and
   * emulators.
   *
   * <p>[b/36397768],[b/152732288] If a device has the known default serial "0123456789ABCDEF" or
   * "0123456789ABCDEF000", get the device's USB id instead of serial number. Many devices in NBU
   * market share this serial number, so if there are multiple such devices connected to the host,
   * we won't be able to operate them properly with serial numbers. So we should use their USB id
   * instead.
   *
   * @param timeout adb list devices command timeout. Use {@link
   *     #LIST_DEVICES_DEFAULT_COMMAND_TIMEOUT} if {@code timeout} is null.
   * @return a map of Android device serial to {@link DeviceState}, or an empty map if no devices
   *     detected
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public Map<String, DeviceState> getDeviceSerialsAsMap(@Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    Map<String, DeviceState> ids = new HashMap<>();
    ImmutableList<String> deviceLines = listDevices(timeout);
    for (String line : deviceLines) {
      List<String> words = Splitter.onPattern("\\s+").splitToList(line.trim());
      // Should at least have device serial and device state.
      if (words.size() < 2) {
        logger.atWarning().log("Invalid ADB line format: %s", line);
        continue;
      }
      String serial = words.get(0);
      try {
        // If the device has the default serial, use the usb id as device id instead.
        if (DeviceConstants.OUTPUT_DEVICE_DEFAULT_SERIALS.contains(serial)) {
          // If the device shows up with the default serial, it is connected via USB, and must have
          // a usb ID.
          if (words.size() < 3 || !words.get(2).startsWith(OUTPUT_USB_ID_TOKEN)) {
            logger.atWarning().log("Invalid ADB line format: %s", line);
            continue;
          }
          String usbId = words.get(2);
          ids.put(usbId, DeviceState.valueOf(Ascii.toUpperCase(words.get(1))));
        } else {
          ids.put(serial, DeviceState.valueOf(Ascii.toUpperCase(words.get(1))));
        }
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log("Unknown type of device: %s", words);
      }
    }
    return ids;
  }

  /**
   * Gets serial numbers of the current connected Android real devices. Returns (online/offline)
   * device serial numbers if the {@code online} parameter is set to be (true/false).
   *
   * @param online whether the devices are online or offline
   * @return serial numbers of the Android real devices, or an empty set if no devices detected
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public Set<String> getRealDeviceSerials(boolean online)
      throws MobileHarnessException, InterruptedException {
    return getRealDeviceSerials(online, /* timeout= */ null);
  }

  /**
   * Gets serial numbers of the current connected Android real devices. Returns (online/offline)
   * device serial numbers if the {@code online} parameter is set to be (true/false).
   *
   * @param online whether the devices are online or offline
   * @param timeout adb list devices command timeout. Use {@link
   *     #LIST_DEVICES_DEFAULT_COMMAND_TIMEOUT} if {@code timeout} is null.
   * @return serial numbers of the Android real devices, or an empty set if no devices detected
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public Set<String> getRealDeviceSerials(boolean online, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    Set<String> allIds;
    if (online) {
      allIds = getDeviceSerialsByState(DeviceState.DEVICE, timeout);
    } else {
      allIds = getDeviceSerialsByState(DeviceState.OFFLINE, timeout);
    }
    return allIds.stream()
        .filter(((Predicate<String>) AndroidEmulatorIds::isAndroidEmulator).negate())
        .collect(Collectors.toSet());
  }

  /**
   * Checks whether the environment has ADB tool.
   *
   * @return empty if ADB is supported, detailed unsupported reason otherwise
   */
  public Optional<String> checkAdbSupport() throws InterruptedException {
    try {
      getAdbVersion();
      return Optional.empty();
    } catch (MobileHarnessException e) {
      return Optional.of(shortDebugString(e, /* maxLength= */ 2));
    }
  }

  /**
   * Returns device lines (line separator exclusive) of "adb devices -l".
   *
   * <p>For example, if "adb devices -l" returns:
   *
   * <pre>
   * * daemon not running. starting it now on port 5037 *
   * * daemon started successfully *
   * List of devices attached
   * 014994B00D014014  device  usb:3-11.2.2 ...
   * 363005DC750400EC  unauthorized  usb:3-12.1.2 ...
   * 0288504043411157  offline  usb:3-11.2.3 ...
   * EC88504043411145  recovery  usb:3-11.1.2 ...
   * ZHMU9UNFT  sideload  usb:3-12.2.2 ...
   * emulator-5554     device ... // emulators have no USB id.
   * HT9CYP806590      device  usb:3-11.2.4 ...</pre>
   *
   * <p>This method will return ["014994B00D014014 device usb:3-11.2.2 ...", "363005DC750400EC
   * unauthorized usb:3-12.1.2 ...", ...].
   *
   * <p>This method can be used by {@link #getDeviceSerialsAsMap} and {@code
   * AndroidSystemSpecUtil#getDeviceUsbLocator}.
   *
   * @param timeout adb list devices command timeout. Use {@link
   *     #LIST_DEVICES_DEFAULT_COMMAND_TIMEOUT} if {@code timeout} is null.
   */
  public ImmutableList<String> listDevices(@Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    String output;
    try {
      output =
          adb.runWithRetry(
              new String[] {ADB_ARG_GET_DEVICES, "-l"},
              timeout == null ? LIST_DEVICES_DEFAULT_COMMAND_TIMEOUT : timeout);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_GET_DEVICE_SERIALS_CMD_ERROR,
          String.format("Failed to list devices with command [%s devices -l]", adb.getAdbPath()),
          e);
    }
    return Splitters.LINE_SPLITTER
        .splitToStream(output)
        .filter(
            line ->
                !StrUtil.isEmptyOrWhitespace(line)
                    && !line.startsWith(OUTPUT_DEVICE_LIST_STARTER)
                    // Ignore "* daemon" lines.  The MTaaS docker container's sandboxed adb
                    // always prints these lines after the starter line, presumably because
                    // "adb_sandbox.cc" alters the stdout buffering.
                    && !line.startsWith(OUTPUT_DEVICE_LIST_DAEMON))
        .collect(toImmutableList());
  }

  /**
   * Kills the ADB server if it is running.
   *
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void killAdbServer() throws MobileHarnessException, InterruptedException {
    try {
      String unused = adb.run(ADB_ARGS_KILL_SERVER, SHORT_COMMAND_TIMEOUT);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_ADB_KILL_SERVER_ERROR, e.getMessage(), e);
    }
  }

  private String getAdbVersion() throws MobileHarnessException, InterruptedException {
    // "adb --version" not supported in low version of adb
    try {
      return adb.run(ADB_ARGS_GET_VERSION);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_GET_ADB_VERSION_ERROR,
          "Failed to get ADB version",
          e);
    }
  }

  /**
   * Runs "adb detach" to release a real device from the USB.
   *
   * <p>For example, if "adb devices" shows,
   *
   * <pre>
   * List of devices attached
   * ZT95QCYP7T8T89BE device
   * </pre>
   *
   * <p>When run "adb detach ZT95QCYP7T8T89BE" for the first time, the method returns a String
   * "ZT95QCYP7T8T89BE detached", and the device status becomes "unknown". When run "adb detach
   * ZT95QCYP7T8T89BE" again, this method returns a MobileHarnessException with error message "adb:
   * failed to detach: transport ZT95QCYP7T8T89BE is already detached".
   *
   * @param deviceId The serial of the device.
   * @return The result of the command execution as a String.
   * @throws MobileHarnessException if it fails to execute the command.
   */
  public String detachRealDevice(String deviceId)
      throws MobileHarnessException, InterruptedException {
    try {
      String result = adb.run(deviceId, new String[] {ADB_ARGS_DETACH_DEVICE});
      logger.atInfo().log("Device %s is detached, %s", deviceId, result);
      return result;
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_DEVICE_DETACH_ERROR,
          "Failed to dettach device:" + deviceId,
          e);
    }
  }

  /**
   * Runs "adb attach" to occupy a real device to the USB.
   *
   * <p>When run "adb detach ZT95QCYP7T8T89BE" and then run "adb attach ZT95QCYP7T8T89BE", this
   * method returns a String "ZT95QCYP7T8T89BE attached". When run "adb attach ZT95QCYP7T8T89BE"
   * again, this method throws an MobileHarnessException with message "adb: failed to attach:
   * transport ZT95QCYP7T8T89BE is not detached".
   *
   * @param deviceId The serial of the device.
   * @return The result of the command execution as a String.
   * @throws MobileHarnessException if it fails to execute the command.
   */
  public String attachRealDevice(String deviceId)
      throws MobileHarnessException, InterruptedException {
    try {
      String result = adb.run(deviceId, new String[] {ADB_ARGS_ATTACH_DEVICE});
      logger.atInfo().log("Device %s is attached, %s", deviceId, result);
      return result;
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_DEVICE_ATTACH_ERROR,
          "Failed to attach device:" + deviceId,
          e);
    }
  }

  /**
   * Runs "adb host-features" to get the features of the adb server.
   *
   * @return The result of the command execution as a String.
   * @throws MobileHarnessException if it fails to execute the command.
   */
  public String getHostFeatures() throws MobileHarnessException, InterruptedException {
    try {
      return adb.run(ADB_ARGS_HOST_FEATURES);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_INTERNAL_UTIL_GET_HOST_FEATURES_ERROR, e.getMessage(), e);
    }
  }
}
