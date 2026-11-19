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

package com.google.devtools.mobileharness.platform.android.connectivity;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.platform.android.lightning.shared.SharedLogUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil.LocationType;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Utility for enabling, restoring across reboots, and disabling USB reverse tethering (Reverse NCM
 * via Cuttlefish host bridge {@code cvd-ebr}) on physical USB-connected Android devices.
 */
public class UsbReverseTetheringUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting static final String CVD_EBR_INTERFACE = "cvd-ebr";

  @VisibleForTesting
  static final String CHECK_DEVICE_SUPPORT_CMD =
      "grep -qE 'functions/ncm\\.[^/]+/ifname usb' /vendor/etc/init/hw/init.*.usb.rc";

  @VisibleForTesting static final String GET_USB_FUNCTIONS_CMD = "svc usb getFunctions 2>&1";

  @VisibleForTesting static final String DUMPSYS_USB_CMD = "dumpsys usb";

  @VisibleForTesting static final String SET_USB_FUNCTIONS_RNDIS_CMD = "svc usb setFunctions rndis";

  @VisibleForTesting
  static final String SET_SCREEN_UNLOCKED_USB_FUNCTIONS_RNDIS_CMD =
      "svc usb setScreenUnlockedFunctions rndis";

  @VisibleForTesting static final String CLEAR_USB_FUNCTIONS_CMD = "svc usb setFunctions";

  @VisibleForTesting
  static final String CLEAR_SCREEN_UNLOCKED_USB_FUNCTIONS_CMD =
      "svc usb setScreenUnlockedFunctions";

  @VisibleForTesting public static final String PROPERTY_ENABLED = "usb_reverse_tethering_enabled";

  @VisibleForTesting
  public static final String PROPERTY_PERSIST_ACROSS_REBOOT =
      "usb_reverse_tethering_persist_across_reboot";

  @VisibleForTesting
  public static final String PROPERTY_CHECK_HOST = "usb_reverse_tethering_check_host";

  @VisibleForTesting
  public static final String PROPERTY_TIMEOUT_SEC = "usb_reverse_tethering_timeout_sec";

  @VisibleForTesting
  public static final Duration DEFAULT_CONNECTIVITY_TIMEOUT = Duration.ofSeconds(30);

  @VisibleForTesting static final Duration CONNECTIVITY_POLL_INTERVAL = Duration.ofSeconds(2);

  @VisibleForTesting static final Duration USB_DISCONNECT_WAIT_TIMEOUT = Duration.ofSeconds(5);

  @VisibleForTesting static final Duration USB_RECONNECT_WAIT_TIMEOUT = Duration.ofMinutes(1);

  private static final Duration USB_CACHE_DURATION = Duration.ofMinutes(2);
  private static final Duration SHORT_SHELL_CMD_TIMEOUT = Duration.ofSeconds(10);

  private static final Pattern VALID_CHECK_HOST_PATTERN =
      Pattern.compile("^[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?$");

  private static final Pattern VALID_USB_FUNCTIONS_OUTPUT_PATTERN =
      Pattern.compile("^[a-zA-Z0-9_,]+$");
  private static final Pattern TETHERING_USB_FUNCTIONS_PATTERN =
      Pattern.compile(
          "^\\s*(?:current_functions=|mCurrentFunctions:\\s*"
              + "|screen_unlocked_functions=|mScreenUnlockedFunctions:\\s*)"
              + ".*\\b(?:0x400|ncm|rndis)\\b",
          Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
  private static final Splitter WHITESPACE_SPLITTER = Splitter.onPattern("\\s+").omitEmptyStrings();
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

  private final Adb adb;
  private final AndroidConnectivityUtil connectivityUtil;
  private final AndroidSystemStateUtil systemStateUtil;
  private final NetUtil netUtil;
  private final DeviceCache deviceCache;
  private final Sleeper sleeper;
  private final Clock clock;

  public UsbReverseTetheringUtil() {
    this(new Adb(), new AndroidConnectivityUtil(), new AndroidSystemStateUtil());
  }

  public UsbReverseTetheringUtil(
      Adb adb, AndroidConnectivityUtil connectivityUtil, AndroidSystemStateUtil systemStateUtil) {
    this(
        adb,
        connectivityUtil,
        systemStateUtil,
        new NetUtil(),
        DeviceCache.getInstance(),
        Sleeper.defaultSleeper(),
        Clock.systemUTC());
  }

  @VisibleForTesting
  UsbReverseTetheringUtil(
      Adb adb,
      AndroidConnectivityUtil connectivityUtil,
      AndroidSystemStateUtil systemStateUtil,
      NetUtil netUtil,
      DeviceCache deviceCache,
      Sleeper sleeper,
      Clock clock) {
    this.adb = adb;
    this.connectivityUtil = connectivityUtil;
    this.systemStateUtil = systemStateUtil;
    this.netUtil = netUtil;
    this.deviceCache = deviceCache;
    this.sleeper = sleeper;
    this.clock = clock;
  }

  /** Returns whether the given connectivity check host is syntactically safe and non-empty. */
  public static boolean isValidCheckHost(@Nullable String host) {
    return !Strings.isNullOrEmpty(host) && VALID_CHECK_HOST_PATTERN.matcher(host).matches();
  }

  /** Returns whether the local host is configured to support USB reverse tethering. */
  public boolean isHostSupported() {
    Path netDir = Path.of("/sys/class/net", CVD_EBR_INTERFACE);
    return checkHostSupport(
        netDir.resolve("bridge"),
        netDir.resolve("flags"),
        Path.of("/proc/sys/net/ipv4/ip_forward"),
        Path.of("/proc/net/udp"),
        hasIpv4Address(CVD_EBR_INTERFACE));
  }

  @VisibleForTesting
  boolean checkHostSupport(
      Path bridgeDir,
      Path flagsPath,
      Path ipForwardPath,
      Path procNetUdpPath,
      boolean hasInterfaceIpv4) {
    try {
      if (!Files.isDirectory(bridgeDir)) {
        logger.atWarning().log(
            "Host USB reverse tethering unsupported: bridge directory %s not found.", bridgeDir);
        return false;
      }
      int flags = Integer.decode(Files.readString(flagsPath).trim());
      if ((flags & 0x1) == 0) {
        logger.atWarning().log(
            "Host USB reverse tethering unsupported: %s is not IFF_UP (flags=0x%x).",
            CVD_EBR_INTERFACE, flags);
        return false;
      }
      if (!hasInterfaceIpv4) {
        logger.atWarning().log(
            "Host USB reverse tethering unsupported: %s has no IPv4 address.", CVD_EBR_INTERFACE);
        return false;
      }
      if (!"1".equals(Files.readString(ipForwardPath).trim())) {
        logger.atWarning().log(
            "Host USB reverse tethering unsupported: net.ipv4.ip_forward is not 1.");
        return false;
      }
      boolean dhcpListening = false;
      for (String line : Files.readAllLines(procNetUdpPath)) {
        List<String> tokens = WHITESPACE_SPLITTER.splitToList(line.trim());
        if (tokens.size() > 1 && Ascii.toUpperCase(tokens.get(1)).endsWith(":0043")) {
          dhcpListening = true;
          break;
        }
      }
      if (!dhcpListening) {
        logger.atWarning().log(
            "Host USB reverse tethering unsupported: no DHCP server listening on UDP port 67.");
        return false;
      }
      return true;
    } catch (IOException | NumberFormatException e) {
      logger.atWarning().log(
          "Failed to verify host USB reverse tethering support: %s", e.getMessage());
      return false;
    }
  }

  /** Checks whether the device build supports USB reverse tethering (Reverse NCM). */
  public boolean isDeviceSupported(String serial) throws InterruptedException {
    try {
      var unused = adb.runShell(serial, CHECK_DEVICE_SUPPORT_CMD, SHORT_SHELL_CMD_TIMEOUT);
      return true;
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "Device %s does not support USB reverse tethering: %s",
          serial, MoreThrowables.shortDebugString(e));
      return false;
    }
  }

  /**
   * Enables USB reverse tethering on {@code device}, waits for USB re-enumeration, and verifies
   * network connectivity via ping.
   */
  public void enableUsbReverseTethering(
      Device device,
      boolean persistAcrossReboot,
      Duration connectivityTimeout,
      @Nullable String customCheckHost,
      @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    clearStateProperties(device);
    String deviceId = device.getDeviceId();
    if (DeviceUtil.isOverTcpDevice(deviceId)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_OVER_TCP_NOT_SUPPORTED,
          String.format(
              "Device %s is connected over TCP; USB reverse tethering requires a physical USB"
                  + " connection.",
              deviceId));
    }
    String checkHost = resolveCheckHost(customCheckHost);

    SharedLogUtil.logMsg(
        logger,
        log,
        "Enabling USB reverse tethering on device %s (persist_across_reboot=%s, timeout=%s,"
            + " check_host=%s).",
        deviceId,
        persistAcrossReboot,
        connectivityTimeout,
        checkHost);

    // Record enabled state before mutating USB functions so postRunTest recovery still runs if
    // USB re-enumeration or connectivity verification fails.
    device.setProperty(PROPERTY_ENABLED, "true");
    device.setProperty(PROPERTY_PERSIST_ACROSS_REBOOT, Boolean.toString(persistAcrossReboot));
    device.setProperty(PROPERTY_CHECK_HOST, checkHost);
    device.setProperty(PROPERTY_TIMEOUT_SEC, Long.toString(connectivityTimeout.toSeconds()));

    try {
      runWithDeviceCacheIfNeeded(
          device,
          () -> {
            if (persistAcrossReboot) {
              runUsbSvcCommand(deviceId, SET_SCREEN_UNLOCKED_USB_FUNCTIONS_RNDIS_CMD, log);
              waitForUsbReenumeration(deviceId);
              if (!hasTetheringUsbFunction(getUsbFunctions(deviceId, GET_USB_FUNCTIONS_CMD))) {
                // If screen was locked when setScreenUnlockedFunctions ran, activate immediately.
                runUsbSvcCommand(deviceId, SET_USB_FUNCTIONS_RNDIS_CMD, log);
                waitForUsbReenumeration(deviceId);
              }
            } else {
              runUsbSvcCommand(deviceId, SET_USB_FUNCTIONS_RNDIS_CMD, log);
              waitForUsbReenumeration(deviceId);
            }
          });
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_ENABLE_ERROR,
          String.format("Failed to enable USB reverse tethering on device %s", deviceId),
          e);
    }

    if (!verifyConnectivity(deviceId, checkHost, connectivityTimeout, log)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_CONNECTIVITY_VERIFICATION_FAILED,
          String.format(
              "Failed to verify network connectivity to %s within %s after enabling USB reverse"
                  + " tethering on device %s",
              checkHost, connectivityTimeout, deviceId));
    }
  }

  /**
   * Ensures USB reverse tethering is active and reachable after a MobileHarness-managed reboot when
   * previously enabled with {@code persist_across_reboot=true}.
   */
  public void restoreAfterRebootIfEnabled(Device device, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    if (!device.getBooleanProperty(PROPERTY_ENABLED)
        || !device.getBooleanProperty(PROPERTY_PERSIST_ACROSS_REBOOT)) {
      return;
    }
    String deviceId = device.getDeviceId();
    String checkHost = resolveCheckHost(device.getProperty(PROPERTY_CHECK_HOST));
    Duration timeout = resolveSavedTimeout(device.getProperty(PROPERTY_TIMEOUT_SEC));

    SharedLogUtil.logMsg(
        logger, log, "Restoring USB reverse tethering on device %s after reboot.", deviceId);

    try {
      if (!hasTetheringUsbFunction(getUsbFunctions(deviceId, GET_USB_FUNCTIONS_CMD))) {
        runWithDeviceCacheIfNeeded(
            device,
            () -> {
              runUsbSvcCommand(deviceId, SET_USB_FUNCTIONS_RNDIS_CMD, log);
              waitForUsbReenumeration(deviceId);
            });
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_POST_REBOOT_RESTORE_FAILED,
          String.format(
              "Failed to re-enable USB reverse tethering on device %s after reboot", deviceId),
          e);
    }

    if (!verifyConnectivity(deviceId, checkHost, timeout, log)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_POST_REBOOT_RESTORE_FAILED,
          String.format(
              "Failed to verify network connectivity to %s within %s after reboot on device %s",
              checkHost, timeout, deviceId));
    }
  }

  /** Disables USB reverse tethering, clears persisted USB functions, and waits for recovery. */
  public void disableUsbReverseTethering(Device device, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    SharedLogUtil.logMsg(logger, log, "Disabling USB reverse tethering on device %s.", deviceId);

    try {
      runWithDeviceCacheIfNeeded(
          device,
          () -> {
            runUsbSvcCommand(deviceId, CLEAR_SCREEN_UNLOCKED_USB_FUNCTIONS_CMD, log);
            if (!DeviceUtil.isOverTcpDevice(deviceId)) {
              systemStateUtil.waitForState(
                  deviceId, DeviceConnectionState.DEVICE, USB_RECONNECT_WAIT_TIMEOUT);
            }
            runUsbSvcCommand(deviceId, CLEAR_USB_FUNCTIONS_CMD, log);
            waitForUsbReenumeration(deviceId);
          });
      String usbDump =
          Strings.nullToEmpty(adb.runShell(deviceId, DUMPSYS_USB_CMD, SHORT_SHELL_CMD_TIMEOUT));
      if (hasTetheringUsbFunctionInDump(usbDump)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_DISABLE_ERROR,
            String.format(
                "USB reverse tethering functions remain active or persisted on device %s after"
                    + " reset",
                deviceId));
      }
      clearStateProperties(device);
      SharedLogUtil.logMsg(
          logger, log, "Successfully disabled USB reverse tethering on device %s.", deviceId);
    } catch (MobileHarnessException e) {
      if (e.getErrorId() == AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_DISABLE_ERROR) {
        throw e;
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_DISABLE_ERROR,
          String.format("Failed to disable USB reverse tethering on device %s", deviceId),
          e);
    }
  }

  /**
   * Clears stale active or persisted USB reverse tethering functions if marked enabled or left
   * behind in {@code dumpsys usb} by an interrupted test or Lab Server crash. No-op when no
   * tethering function is marked enabled, active, or persisted.
   */
  public void clearPersistedFunctionsIfSet(Device device)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    if (DeviceUtil.isOverTcpDevice(deviceId)) {
      clearStateProperties(device);
      return;
    }
    if (isMarkedEnabled(device)) {
      logger.atInfo().log(
          "Clearing marked-enabled USB reverse tethering functions on device %s.", deviceId);
      disableUsbReverseTethering(device, /* log= */ null);
      return;
    }
    String usbDump;
    try {
      usbDump =
          Strings.nullToEmpty(adb.runShell(deviceId, DUMPSYS_USB_CMD, SHORT_SHELL_CMD_TIMEOUT));
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "Failed to query USB state via '%s' on device %s: %s",
          DUMPSYS_USB_CMD, deviceId, MoreThrowables.shortDebugString(e));
      return;
    }
    if (hasTetheringUsbFunctionInDump(usbDump)) {
      logger.atInfo().log(
          "Clearing stale active or persisted USB reverse tethering functions on device %s.",
          deviceId);
      disableUsbReverseTethering(device, /* log= */ null);
    } else {
      clearStateProperties(device);
    }
  }

  /** Returns whether USB reverse tethering is currently marked active on {@code device}. */
  public boolean isMarkedEnabled(Device device) {
    return device.getBooleanProperty(PROPERTY_ENABLED);
  }

  /** Clears all in-memory USB reverse tethering state properties on {@code device}. */
  public void clearStateProperties(Device device) {
    device.setProperty(PROPERTY_ENABLED, null);
    device.setProperty(PROPERTY_PERSIST_ACROSS_REBOOT, null);
    device.setProperty(PROPERTY_CHECK_HOST, null);
    device.setProperty(PROPERTY_TIMEOUT_SEC, null);
  }

  private String resolveCheckHost(@Nullable String customCheckHost) throws MobileHarnessException {
    if (!Strings.isNullOrEmpty(customCheckHost)) {
      if (!isValidCheckHost(customCheckHost)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_ENABLE_ERROR,
            String.format("Invalid connectivity_check_host: '%s'", customCheckHost));
      }
      return customCheckHost;
    }
    try {
      if (netUtil.getLocalHostLocationType().equals(LocationType.IN_CHINA)) {
        return AndroidConnectivityUtil.HOST_PING_FOR_IN_CHINA;
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to determine host location type, defaulting ping host to %s: %s",
          AndroidConnectivityUtil.HOST_PING_FOR_NOT_IN_CHINA, MoreThrowables.shortDebugString(e));
    }
    return AndroidConnectivityUtil.HOST_PING_FOR_NOT_IN_CHINA;
  }

  private static Duration resolveSavedTimeout(@Nullable String timeoutSecStr) {
    if (!Strings.isNullOrEmpty(timeoutSecStr)) {
      try {
        long seconds = Long.parseLong(timeoutSecStr);
        if (seconds > 0) {
          return Duration.ofSeconds(seconds);
        }
      } catch (NumberFormatException ignored) {
        // Fall through to default timeout.
      }
    }
    return DEFAULT_CONNECTIVITY_TIMEOUT;
  }

  private boolean verifyConnectivity(
      String deviceId, String checkHost, Duration timeout, @Nullable LogCollector<?> log)
      throws InterruptedException {
    Instant deadline = clock.instant().plus(timeout);
    do {
      if (connectivityUtil.pingSuccessRate(deviceId, checkHost, /* count= */ 1) > 0) {
        SharedLogUtil.logMsg(
            logger,
            log,
            "Verified USB reverse tethering network connectivity to %s on device %s.",
            checkHost,
            deviceId);
        return true;
      }
      if (!clock.instant().isBefore(deadline)) {
        break;
      }
      sleeper.sleep(CONNECTIVITY_POLL_INTERVAL);
    } while (clock.instant().isBefore(deadline));

    SharedLogUtil.logMsg(
        logger,
        log,
        "Network connectivity check to %s failed on device %s after %s.",
        checkHost,
        deviceId,
        timeout);
    return false;
  }

  private void runUsbSvcCommand(String deviceId, String command, @Nullable LogCollector<?> log)
      throws InterruptedException {
    try {
      var unused = adb.runShell(deviceId, command, SHORT_SHELL_CMD_TIMEOUT);
    } catch (MobileHarnessException e) {
      // Switching USB functions disconnects the active USB ADB transport mid-command and commonly
      // causes adb shell to exit non-zero; log and wait for USB re-enumeration.
      SharedLogUtil.logMsg(
          logger,
          log,
          "Command '%s' on device %s exited during USB reconfiguration: %s",
          command,
          deviceId,
          MoreThrowables.shortDebugString(e));
    }
  }

  private void waitForUsbReenumeration(String deviceId)
      throws MobileHarnessException, InterruptedException {
    if (!DeviceUtil.isOverTcpDevice(deviceId)) {
      try {
        systemStateUtil.waitForState(
            deviceId, DeviceConnectionState.DISCONNECT, USB_DISCONNECT_WAIT_TIMEOUT);
      } catch (MobileHarnessException e) {
        // Device may have already completed USB re-enumeration before wait-for-disconnect started.
        logger.atInfo().log(
            "Timed out waiting for disconnect on %s during USB re-enumeration (continuing): %s",
            deviceId, MoreThrowables.shortDebugString(e));
      }
      systemStateUtil.waitForState(
          deviceId, DeviceConnectionState.DEVICE, USB_RECONNECT_WAIT_TIMEOUT);
    }
    systemStateUtil.waitUntilReady(deviceId, USB_RECONNECT_WAIT_TIMEOUT);
  }

  private ImmutableSet<String> getUsbFunctions(String deviceId, String command)
      throws InterruptedException {
    try {
      String output = Strings.nullToEmpty(adb.runShell(deviceId, command, SHORT_SHELL_CMD_TIMEOUT));
      return parseUsbFunctions(output);
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "Failed to query USB functions via '%s' on device %s: %s",
          command, deviceId, MoreThrowables.shortDebugString(e));
      return ImmutableSet.of();
    }
  }

  @VisibleForTesting
  static ImmutableSet<String> parseUsbFunctions(String rawOutput) {
    String trimmed = rawOutput.trim();
    if (trimmed.isEmpty() || !VALID_USB_FUNCTIONS_OUTPUT_PATTERN.matcher(trimmed).matches()) {
      return ImmutableSet.of();
    }
    return COMMA_SPLITTER.splitToStream(Ascii.toLowerCase(trimmed)).collect(toImmutableSet());
  }

  @VisibleForTesting
  static boolean hasTetheringUsbFunctionInDump(String dumpsysUsbOutput) {
    String deviceManagerDump = dumpsysUsbOutput;
    int hostManagerIdx = deviceManagerDump.indexOf("host_manager=");
    if (hostManagerIdx >= 0) {
      deviceManagerDump = deviceManagerDump.substring(0, hostManagerIdx);
    }
    int portManagerIdx = deviceManagerDump.indexOf("port_manager=");
    if (portManagerIdx >= 0) {
      deviceManagerDump = deviceManagerDump.substring(0, portManagerIdx);
    }
    return TETHERING_USB_FUNCTIONS_PATTERN.matcher(deviceManagerDump).find();
  }

  private static boolean hasTetheringUsbFunction(ImmutableSet<String> functions) {
    return functions.contains("rndis") || functions.contains("ncm");
  }

  private void runWithDeviceCacheIfNeeded(Device device, InterruptibleAction action)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    boolean newlyCached = !deviceCache.isCached(deviceId);
    if (newlyCached) {
      deviceCache.cache(deviceId, device.getClass().getSimpleName(), USB_CACHE_DURATION);
    }
    try {
      action.run();
    } finally {
      if (newlyCached) {
        deviceCache.invalidateCache(deviceId);
      }
    }
  }

  private static boolean hasIpv4Address(String interfaceName) {
    try {
      NetworkInterface netIf = NetworkInterface.getByName(interfaceName);
      if (netIf == null) {
        return false;
      }
      return Collections.list(netIf.getInetAddresses()).stream()
          .anyMatch(addr -> addr instanceof Inet4Address);
    } catch (IOException e) {
      logger.atWarning().log(
          "Failed to inspect network interface %s: %s", interfaceName, e.getMessage());
      return false;
    }
  }

  @FunctionalInterface
  private interface InterruptibleAction {
    void run() throws MobileHarnessException, InterruptedException;
  }
}
