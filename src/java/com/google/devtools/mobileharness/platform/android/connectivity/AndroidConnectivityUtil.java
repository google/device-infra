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

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.common.net.InetAddresses;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.shared.SharedLogUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidSvc;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.WaitArgs;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil.LocationType;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Utility methods for controlling the connectivity on Android devices/emulators, including wifi,
 * cellular, Bluetooth, etc.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 */
public class AndroidConnectivityUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** ADB shell command for showing net status. */
  @VisibleForTesting static final String ADB_SHELL_GET_NET_STATUS = "netstat";

  /** ADB shell template for connecting to wifi without password. Should fill with: wifi ssid. */
  @VisibleForTesting
  static final String ADB_SHELL_TEMPLATE_CONNECT_WIFI_WITHOUT_PASSWORD =
      "am instrument -e method connectToNetwork -e ssid %s "
          + "-w com.google.devtools.mobileharness.platform.android.app.binary.wifiutil/.WifiUtil";

  /**
   * ADB shell template for connecting to wifi with password. Should fill with: wifi ssid, password.
   */
  @VisibleForTesting
  static final String ADB_SHELL_TEMPLATE_CONNECT_WIFI_WITH_PASSWORD =
      "am instrument -e method connectToNetwork -e ssid %s -e psk %s -w"
          + " com.google.devtools.mobileharness.platform.android.app.binary.wifiutil/.WifiUtil";

  /**
   * ADB shell template for connecting to wifi with password and scan_ssid. Should fill with: wifi
   * ssid, password, and scan_ssid.
   */
  @VisibleForTesting
  static final String ADB_SHELL_TEMPLATE_CONNECT_WIFI_WITH_PASSWORD_AND_SCAN_SSID =
      "am instrument -e method connectToNetwork -e ssid %s -e psk %s -e scan_ssid %s -w"
          + " com.google.devtools.mobileharness.platform.android.app.binary.wifiutil/.WifiUtil";

  /** ADB shell template for dumpsys. Should fill with: type. */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_DUMP_SYS = "dumpsys %s";

  @VisibleForTesting
  static final Duration WAIT_FOR_WIFI_SCAN_RESULTS_TIMEOUT = Duration.ofMinutes(2);

  private static final Duration WAIT_FOR_WIFI_SCAN_RESULTS_INTERVAL = Duration.ofSeconds(5);

  @VisibleForTesting static final Duration WAIT_FOR_WIFI_STATE_TIMEOUT = Duration.ofSeconds(30);

  private static final Duration WAIT_FOR_WIFI_STATE_INTERVAL = Duration.ofSeconds(3);

  @VisibleForTesting
  static final String WIFI_UTIL_ADB_SHELL_SET_WIFI_TEMPLATE =
      "am instrument -e method %s -w"
          + " com.google.devtools.mobileharness.platform.android.app.binary.wifiutil/.WifiUtil";

  @VisibleForTesting
  static final String WIFI_UTIL_PACKAGE_NAME =
      "com.google.devtools.mobileharness.platform.android.app.binary.wifiutil";

  private static final String WIFI_UTIL_SET_WIFI_SUCCESSFULLY = "result=true";

  private static final String WIFI_ENABLED_STATUS_IN_DUMPSYS_WIFI = "Wi-Fi is enabled";

  private static final String WIFI_DISABLED_STATUS_IN_DUMPSYS_WIFI = "Wi-Fi is disabled";

  private static final String WIFI_SCAN_IDLE_STATE = "IdleState";

  /** The pattern of network link address. */
  private static final Pattern PATTERN_NETWORK_LINK_ADDRESSES =
      Pattern.compile("LinkAddresses: \\[(.*?)\\]");

  /** The new pattern of network SSID (Android >= 3.2.1). */
  private static final Pattern PATTERN_NETWORK_SSID_NEW =
      Pattern.compile("extra: \"(?<ssid>[^\"]*)\"");

  /** The old pattern of network SSID (Android < 3.2.1, but not sure about 3.0 or 3.1 actually). */
  private static final Pattern PATTERN_NETWORK_SSID_OLD = Pattern.compile("SSID: (?<ssid>[^,]*),");

  /** The pattern of network SSID for S and above. */
  private static final Pattern PATTERN_NETWORK_SSID_S_AND_ABOVE =
      Pattern.compile("SSID: \"(?<ssid>[^,]*)\",");

  private static final String CODENAME_S = "S";

  /** The pattern of looking for PSK in wifi config xml, for API from 26 to 29. */
  private static final Pattern PATTERN_PSK_IN_WIFI_CONFIG_XML =
      Pattern.compile("^>&quot;(?<psk>[\\s\\S]*?)&quot;</string>[\\s\\S]*?");

  /** The pattern of saved SSIDs and PSKs, for API from 15 to 25. */
  private static final Pattern PATTERN_SAVED_SSIDS_AND_PSKS_WPA_CONFIG =
      Pattern.compile(
          "network=\\{(\\r\\n|\\n)\\tssid=\"(?<ssid>[\\s\\S]*?)\"(\\r\\n|\\n)"
              + "(?:\\tpsk=\"(?<psk>[\\s\\S]*?)\"(\\r\\n|\\n))?[\\s\\S]*?\\}");

  /** The pattern of saved SSIDs and PSKs, for API from 26 to 29. */
  private static final Pattern PATTERN_SAVED_SSIDS_AND_PSKS_WIFI_CONFIG_XML =
      Pattern.compile(
          "<WifiConfiguration>(?:[\\s\\S]*?)<string name=\"SSID\">&quot;(?<ssid>[\\s\\S]*?)&quot;"
              + "</string>(?<prePsk>[\\s\\S]*?)\\sname=\"PreSharedKey\"(?<afterPsk>[\\s\\S]*?)"
              + "</WifiConfiguration>");

  /** The pattern of WIFI RSSI. */
  private static final Pattern PATTERN_WIFI_RSSI =
      Pattern.compile(
          "curState=CompletedState[\\s\\S]*mWifiInfo[\\s\\S]*"
              + "RSSI: *(?<result>-?[1-9]\\d*|0)"
              + " *,");

  private static final Pattern LINE_PATTERN = Pattern.compile("curState=(?<scanresults>.*)");

  /** The pattern of ip address. */
  private static final Pattern PATTERN_NETWORK_LINK_ADDRESS = Pattern.compile("(.*)/(.*)");

  /** ADB shell template for non-blocking ping. Should be filled with the time limit and host. */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_PING = "ping -c 1 -w %d %s";

  /** Extra execution time for running the ping command. */
  @VisibleForTesting static final Duration PING_COMMAND_DELAY = Duration.ofSeconds(5);

  /**
   * Timeout of a single ping. If the ping will try several times, use SHORT_PING_TIMEOUT,
   * otherwise, use LONG_PING_TIMEOUT
   */
  @VisibleForTesting static final Duration SHORT_PING_TIMEOUT = Duration.ofSeconds(6);

  @VisibleForTesting static final Duration LONG_PING_TIMEOUT = Duration.ofMinutes(1);

  /** Intercal second(s) between ping command. */
  @VisibleForTesting static final Duration CHECK_PING_INTERVAL = Duration.ofSeconds(1);

  /** Output of "adb shell ping" command when it succeeds to ping a host and get a reply. */
  @VisibleForTesting static final String OUTPUT_PING_SUCCESS = " bytes from ";

  /** ADB shell template for using apk to ping. */
  @VisibleForTesting
  static final String ADB_SHELL_TEMPLATE_PING_URL =
      "am instrument -e method checkConnectivity -e urlToCheck %s -w"
          + " com.google.devtools.mobileharness.platform.android.app.binary.wifiutil/.WifiUtil";

  /** The target host if the device is not in China. */
  @VisibleForTesting static final String HOST_FOR_NOT_IN_CHINA = "http://www.google.com";

  /** The target host if the device is in China. */
  @VisibleForTesting static final String HOST_FOR_IN_CHINA = "http://www.gstatic.com";

  /** Output of successfully using apk to ping. */
  private static final String PING_SUCCESS = "INSTRUMENTATION_RESULT: result=true";

  /** Interval milliseconds of waiting for network. */
  @VisibleForTesting static final Duration WAIT_FOR_NETWORK_INTERVAL = Duration.ofMillis(1500);

  @VisibleForTesting static final Duration DEFAULT_CONNECT_SSID_TIMEOUT = Duration.ofMinutes(5);

  /**
   * Used to analyze one wifi scan result entry. In Q after ag/5272319, it updates WPA2 to RSN.
   * Tested from API 15 to API 28.
   *
   * <p>See [android]//frameworks/opt/net/wifi/service/java/com/android/server/wifi/util/\
   * InformationElementUtil#generateCapabilitiesString
   */
  private static final Pattern PATTERN_WIFI_SECURITY_MODE =
      Pattern.compile("\\[(WPA|WPA2|WEP|RSN)(.*?)]");

  /** Path of file on device for keeping saved SSIDs and PSKs, for API from 13 to 25. */
  @VisibleForTesting
  static final String SAVED_SSIDS_PSKS_WPA_CONFIG_FILE_PATH = "/data/misc/wifi/wpa_supplicant.conf";

  /** Path of file on device for keeping saved SSIDs and PSKs, for API from 26 to 29. */
  @VisibleForTesting
  static final String SAVED_SSIDS_PSKS_WIFI_CONFIG_FILE_PATH =
      "/data/misc/wifi/WifiConfigStore.xml";

  /** {@code TimeSource} for getting current system time. */
  private final Clock clock;

  private final Adb adb;
  private final Sleeper sleeper;
  private final AndroidAdbUtil adbUtil;
  private final AndroidPackageManagerUtil packageManagerUtil;
  private final AndroidFileUtil androidFileUtil;

  /** {@code NetUtil} for common network operations. */
  private final NetUtil netUtil;

  /** Creates a util for Android device operations. */
  public AndroidConnectivityUtil() {
    this(
        new Adb(),
        Sleeper.defaultSleeper(),
        Clock.systemUTC(),
        new AndroidAdbUtil(),
        new AndroidPackageManagerUtil(),
        new AndroidFileUtil(),
        new NetUtil());
  }

  /** Constructor for unit tests only. */
  @VisibleForTesting
  AndroidConnectivityUtil(
      Adb adb,
      Sleeper sleeper,
      Clock clock,
      AndroidAdbUtil adbUtil,
      AndroidPackageManagerUtil packageManagerUtil,
      AndroidFileUtil androidFileUtil,
      NetUtil netUtil) {
    this.adb = adb;
    this.sleeper = sleeper;
    this.clock = clock;
    this.adbUtil = adbUtil;
    this.packageManagerUtil = packageManagerUtil;
    this.androidFileUtil = androidFileUtil;
    this.netUtil = netUtil;
  }

  /**
   * Connects to wifi network. Note you must install the WifiUtil.apk before using this method.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param wifiSsid the ssid of the wifi
   * @param wifiPsk the password of the wifi
   * @return {@code true} if device connects to given wifi SSID successfully, otherwise {@code
   *     false}
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public boolean connectToWifi(
      String serial,
      int sdkVersion,
      String wifiSsid,
      @Nullable String wifiPsk,
      @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    ConnectToWifiArgs.Builder builder =
        ConnectToWifiArgs.builder()
            .setSerial(serial)
            .setSdkVersion(sdkVersion)
            .setWifiSsid(wifiSsid)
            .setScanSsid(false)
            .setWaitTimeout(DEFAULT_CONNECT_SSID_TIMEOUT);
    if (!Strings.isNullOrEmpty(wifiPsk)) {
      builder.setWifiPsk(wifiPsk);
    }
    return connectToWifi(builder.build(), log);
  }

  /**
   * Connects to wifi network. Note you must install the WifiUtil.apk before using this method.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param wifiSsid the ssid of the wifi
   * @param wifiPsk the password of the wifi
   * @param scanSsid whether to scan for hidden SSID
   * @return {@code true} if device connects to given wifi SSID successfully, otherwise {@code
   *     false}
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public boolean connectToWifi(
      String serial,
      int sdkVersion,
      String wifiSsid,
      @Nullable String wifiPsk,
      boolean scanSsid,
      @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    ConnectToWifiArgs.Builder builder =
        ConnectToWifiArgs.builder()
            .setSerial(serial)
            .setSdkVersion(sdkVersion)
            .setWifiSsid(wifiSsid)
            .setScanSsid(scanSsid)
            .setWaitTimeout(DEFAULT_CONNECT_SSID_TIMEOUT);
    if (!Strings.isNullOrEmpty(wifiPsk)) {
      builder.setWifiPsk(wifiPsk);
    }
    return connectToWifi(builder.build(), log);
  }

  /**
   * Connects to wifi network. Note you must install the WifiUtil.apk before using this method.
   *
   * @param args args indicating how to connect the device to wifi
   * @return {@code true} if device connects to given wifi SSID successfully, otherwise {@code
   *     false}
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public boolean connectToWifi(ConnectToWifiArgs args, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String serial = args.serial();
    int sdkVersion = args.sdkVersion();
    String wifiSsid = args.wifiSsid();
    String wifiPsk = args.wifiPsk().orElse(null);
    boolean scanSsid = args.scanSsid().orElse(false);
    Duration waitTimeout = args.waitTimeout().orElse(DEFAULT_CONNECT_SSID_TIMEOUT);
    boolean forceTryConnect = args.forceTryConnect().orElse(false);

    if (DeviceUtil.inSharedLab()) {
      // TODO:shouldManageDevices check should be moved to higher level API while
      // AndroidConnectivityUtil can focus on managing device wifi/network.
      SharedLogUtil.logMsg(
          logger,
          Level.SEVERE,
          log,
          /* cause= */ null,
          "Ignoring attempt to connect device %s to WiFi while not managing devices.",
          serial);
      return false;
    }

    wifiSsid = ShellUtils.shellEscape(wifiSsid);
    if (!Strings.isNullOrEmpty(wifiPsk)) {
      wifiPsk = ShellUtils.shellEscape(wifiPsk);
    }
    if (canConnectToWifi(serial, sdkVersion, wifiSsid, wifiPsk, scanSsid, forceTryConnect, log)) {
      String cmd = null;
      if (scanSsid) {
        cmd =
            String.format(
                ADB_SHELL_TEMPLATE_CONNECT_WIFI_WITH_PASSWORD_AND_SCAN_SSID,
                wifiSsid,
                Strings.nullToEmpty(wifiPsk),
                String.valueOf(scanSsid));
      } else if (Strings.isNullOrEmpty(wifiPsk)) {
        cmd = String.format(ADB_SHELL_TEMPLATE_CONNECT_WIFI_WITHOUT_PASSWORD, wifiSsid);
      } else {
        cmd = String.format(ADB_SHELL_TEMPLATE_CONNECT_WIFI_WITH_PASSWORD, wifiSsid, wifiPsk);
      }
      try {
        String unused = adb.runShellWithRetry(serial, cmd);
      } catch (MobileHarnessException e) {
        String errorMessage = "Failed to connect to Wifi(SSID = " + wifiSsid + ")";
        if (scanSsid) {
          errorMessage += " with scanning for hidden SSID and";
        }
        if (Strings.isNullOrEmpty(wifiPsk)) {
          errorMessage += " without password";
        } else {
          errorMessage += " with password";
        }
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_CONNECTIVITY_FAIL_CONNECT_TO_WIFI, errorMessage, e);
      }

      if (waitForNetwork(serial, sdkVersion, wifiSsid, waitTimeout)) {
        return true;
      }
      SharedLogUtil.logMsg(
          logger,
          Level.WARNING,
          log,
          /* cause= */ null,
          "Failed to connect device %s to ssid '%s'",
          serial,
          wifiSsid);
    } else {
      SharedLogUtil.logMsg(
          logger,
          Level.WARNING,
          log,
          /* cause= */ null,
          "Skip connecting device %s to ssid '%s', check prior logs for reasons.",
          serial,
          wifiSsid);
    }
    return false;
  }

  /**
   * Gets the network state (e.g. LISTEN) of the specified protocol, local IP address and port.
   *
   * @param serial the serial number of the device
   * @param expectedProtocol the protocol in this query
   * @param expectedLocalIp the local IP address in this query; a null value means that any local IP
   *     address will be matched
   * @param expectedLocalPort the local port in this query
   * @return the state of the local address; null if the state of the local address is not found
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   * @throws IllegalArgumentException if the address's state cannot be represented by SocketState
   */
  @Nullable
  public SocketState getLocalNetState(
      String serial,
      SocketProtocol expectedProtocol,
      @Nullable String expectedLocalIp,
      int expectedLocalPort)
      throws MobileHarnessException, InterruptedException {
    String netstatOutput = "";
    try {
      netstatOutput = adb.runShellWithRetry(serial, ADB_SHELL_GET_NET_STATUS);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CONNECTIVITY_GET_NET_STATUS_ERROR, e.getMessage(), e);
    }
    // Sample output:
    // Proto Recv-Q Send-Q Local Address          Foreign Address        State
    // tcp        0      0 127.0.0.1:5037         0.0.0.0:*              LISTEN
    for (String line : Splitters.LINE_SPLITTER.split(netstatOutput)) {
      // Trims the line first, in case there are spaces at the beginning of the line.
      List<String> infos = Splitter.onPattern("[ \t]+").splitToList(line.trim());
      if (infos.size() < 6) {
        continue;
      }
      String protocol = infos.get(0);
      String localAddr = infos.get(3);
      String state = infos.get(5);
      int separatorPosition = localAddr.lastIndexOf(":");
      if (separatorPosition < 0) {
        continue;
      }
      String localIp = localAddr.substring(0, separatorPosition);
      String localPort = localAddr.substring(separatorPosition + 1);
      if (protocol.startsWith(Ascii.toLowerCase(expectedProtocol.name()))) {
        if ((expectedLocalIp == null || expectedLocalIp.equals(localIp))
            && localPort.equals(String.valueOf(expectedLocalPort))) {
          // Return the address's state.
          if (state.isEmpty()) {
            return SocketState.UNKNOWN;
          } else {
            return SocketState.valueOf(state);
          }
        }
      }
    }
    return null;
  }

  /**
   * Gets network link addresses. Only works with API level >= 18. Supports production build.
   *
   * @param serial serial number of the device
   * @return a list of the network link addresses including ipv4 and ipv6 network addresses
   */
  @SuppressWarnings("JdkObsolete")
  public List<String> getNetworkLinkAddress(String serial)
      throws MobileHarnessException, InterruptedException {
    List<String> result = new ArrayList<>();
    String output = adbUtil.dumpSys(serial, DumpSysType.CONNECTIVITY);
    Matcher matcher = PATTERN_NETWORK_LINK_ADDRESSES.matcher(output);
    while (matcher.find()) {
      for (String addressCandidate : StrUtil.toList(matcher.group(1))) {
        if (verifyNetworkLinkAddress(addressCandidate)) {
          result.add(addressCandidate);
        }
      }
    }
    if (!result.isEmpty()) {
      return result;
    }
    output = adb.runShell(serial, "ip route");
    if (Strings.isNullOrEmpty(output)) {
      return result;
    }
    List<String> split = Splitter.on(' ').splitToList(output);
    // try get the 9th column from the output
    // Example output: 100.112.36.0/22 dev wlan0 proto kernel scope link src 100.112.37.26
    if (split.size() > 8) {
      result.add(split.get(8));
    }
    return result;
  }

  /**
   * Gets network SSID.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @return the SSID if the device is connected with WIFI or mobile network; or an empty string if
   *     the device is connected to an unknown network such as a watch using bluetooth connection;
   *     or null if there is no network connection
   */
  @Nullable
  public String getNetworkSsid(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    List<String> resultList = getNetworkSsidList(serial, sdkVersion);
    if (resultList.isEmpty()) {
      return null;
    } else {
      // return the last one in the list, same as previous version of getNetworkSsid
      return Iterables.getLast(resultList);
    }
  }

  /**
   * Gets network SSID List, on the device that has both wifi and celluar we might get two ssid.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @return the SSID list if the device is connected with WIFI or mobile network; or an empty
   *     string if the device is connected to an unknown network such as a watch using bluetooth
   *     connection or empty list if there is no network connection
   */
  public List<String> getNetworkSsidList(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    String connectivityOutput = adbUtil.dumpSys(serial, DumpSysType.CONNECTIVITY);
    Set<String> resultSet = new HashSet<>();
    resultSet.addAll(parseNetworkSsid(serial, connectivityOutput));

    // Starting from Android P (PPR1.180328.001+),
    // SSID is hidden in CONNECTIVITY output, dump WIFI for SSID info.
    if (sdkVersion >= 27) {
      resultSet.addAll(parseNetworkSsid(serial, adbUtil.dumpSys(serial, DumpSysType.WIFI)));
    }
    // Note: there may be one empty string in result set after parsing. So in the case when there
    // are multiple SSIDs in the result set, it should filter out the empty string.
    return resultSet.size() <= 1
        ? ImmutableList.copyOf(resultSet)
        : resultSet.stream().filter(not(Strings::isNullOrEmpty)).collect(toImmutableList());
  }

  /**
   * Gets all SSIDs and PSKs saved in the device. This method only works with SDK version >= 13 and
   * rooted devices.
   *
   * @param serial the serial number of the device
   * @param sdkVersion SDK version of device
   * @return the map of all SSIDs and PSKs saved in the device
   */
  public ImmutableMap<String, String> getSavedSsidsAndPsks(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    String output = "";
    String wifiConfigFilePath =
        sdkVersion >= AndroidVersion.OREO.getStartSdkVersion()
            ? SAVED_SSIDS_PSKS_WIFI_CONFIG_FILE_PATH
            : SAVED_SSIDS_PSKS_WPA_CONFIG_FILE_PATH;
    if (!androidFileUtil.isFileOrDirExisted(serial, wifiConfigFilePath)) {
      logger.atInfo().log(
          "Not found wifi config file for saved SSIDs and PSKs on device %s: %s",
          serial, wifiConfigFilePath);
      return ImmutableMap.of();
    }
    try {
      output = adb.runShell(serial, String.format("cat %s", wifiConfigFilePath));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CONNECTIVITY_GET_SAVED_SSIDS_PSKS_ERROR, e.getMessage(), e);
    }

    return parseSavedSsidsAndPsks(output, sdkVersion);
  }

  /**
   * Gets WIFI RSSI. It is only supported in devices with sdk version 19 or greater.
   *
   * @param serial serial number of the device
   * @return the RSSI if the device is connected with WIFI; or null if there is no WIFI connection
   */
  public String getWifiRssi(String serial) throws MobileHarnessException, InterruptedException {
    String output = adbUtil.dumpSys(serial, DumpSysType.WIFI);
    // Below is an example of output dumpSys(serial, DumpSysType.WIFI) if there is a wifi
    // connection:
    //
    // ...
    // curState=CompletedState
    // mAuthFailureInSupplicantBroadcast false
    // mNetworksDisabledDuringConnect false
    // ...
    // mWifiInfo SSID: GoogleGuestPSK, BSSID: 00:24:6c:c7:d1:71, MAC: 88:07:4b:ae:7a:59,
    // Supplicant state: COMPLETED, RSSI: -52, Link speed: 130Mbps,
    // Frequency: 5805MHz, Net ID: 0, Metered hint: false, score: 60
    // ...
    //
    // If there is not a wifi connection, the output of dumpSys(serial, DumpSysType.WIFI) will not
    // contain the keyword 'curState=CompletedState'

    String result = null;
    Matcher matcher = PATTERN_WIFI_RSSI.matcher(output);
    if (matcher.find()) {
      result = matcher.group("result");
    }
    // Should find only one.
    if (matcher.find()) {
      result = null;
    }
    if (result != null) {
      logger.atInfo().log("Device %s WIFI RSSI is:%s", serial, result);
    } else {
      logger.atInfo().log("Device %s has no WIFI connection:", serial);
    }
    return result;
  }

  /**
   * Pings a host from an android device. Note that pinging any host from an emulator will never
   * succeed because it is not permitted.
   *
   * <p>Command works with Android API version >= 16. Unknown for API version < 16. The device does
   * not need to be rooted. The command can work on Android emulator. Note this method doesn't work
   * on some non-GED devices. We got such error message on some Samsung devices: This version of
   * ping should NOT run with privileges. Aborting
   *
   * @param serial serial number of the device
   * @param host host name to ping
   * @param count times of ping
   * @return ping success rate with count times
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public double pingSuccessRate(String serial, String host, int count) throws InterruptedException {
    String shell = String.format(ADB_SHELL_TEMPLATE_PING, SHORT_PING_TIMEOUT.getSeconds(), host);
    int successCount = 0;
    for (int i = 0; i < count; i++) {
      String output = null;
      try {
        output = adb.runShell(serial, shell, SHORT_PING_TIMEOUT.plus(PING_COMMAND_DELAY));
        sleeper.sleep(CHECK_PING_INTERVAL);
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Device %s failed to ping %s (times=%d): %s",
            serial, host, i, MoreThrowables.shortDebugString(e, 0));
        continue;
      }
      logger.atInfo().log("Device %s pings %s (times=%d):%n%s", serial, host, i, output);
      if (output.contains(OUTPUT_PING_SUCCESS)) {
        successCount += 1;
      }
    }
    return successCount * 1.0 / count;
  }

  /**
   * Pings a host from an android device. Must install the WifiUtil.apk before using this method.
   *
   * <p>Different from {@code pingSuccessRate} using adb shell ping, {@code pingSuccessfully} uses
   * devtools/mobileharness/platform/android/app/binary/wifiutil/WifiUtil.apk, and also selects the
   * target host according to the location of the server the device is connected to.
   *
   * @param serial serial number of the device
   * @return whether the ping is successfully
   */
  public boolean pingSuccessfully(String serial) throws InterruptedException {
    String targetUrl = HOST_FOR_NOT_IN_CHINA;
    try {
      if (netUtil.getLocalHostLocationType().equals(LocationType.IN_CHINA)) {
        targetUrl = HOST_FOR_IN_CHINA;
      }
      String output =
          adb.runShell(
              serial, String.format(ADB_SHELL_TEMPLATE_PING_URL, targetUrl), LONG_PING_TIMEOUT);
      if (!output.contains(PING_SUCCESS)) {
        logger.atWarning().log(
            "Device %s is not able to ping %s, output=[%s] ", serial, targetUrl, output);
        return false;
      } else {
        return true;
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Device %s failed to ping %s: %s",
          serial, targetUrl, MoreThrowables.shortDebugString(e, 0));
    }
    return false;
  }

  /**
   * Disable and re-enables Wifi of a device. This only works if the device has root access and has
   * already become root.
   *
   * @param serial the serial number of the device
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void reEnableWifi(String serial, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Device %s: Re-enable Wi-Fi.", serial);
    if (!setWifiEnabled(serial, /* enabled= */ false, log)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CONNECTIVITY_DISABLE_WIFI_ERROR,
          String.format("Failed to disable wifi on device %s", serial));
    }
    if (!setWifiEnabled(serial, /* enabled= */ true, log)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CONNECTIVITY_ENABLE_WIFI_ERROR,
          String.format("Failed to enable wifi on device %s", serial));
    }
  }

  /**
   * Enable or disable the NFC of a device. This only works if the device has root access and has
   * already become root. Requires API >= 24.
   *
   * @param serial the serial number of the device
   * @param sdkVersion SDK version of device
   * @param enabled to enable NFC of the device if true; to disable NFC of the device otherwise
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void setNFC(String serial, int sdkVersion, boolean enabled)
      throws MobileHarnessException, InterruptedException {
    if (sdkVersion < AndroidVersion.NOUGAT.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CONNECTIVITY_SDK_VERSION_NOT_SUPPORT,
          String.format("setNFC not supported on device %s with API < 24", serial));
    }
    String operation = enabled ? "enable" : "disable";
    logger.atInfo().log("Device %s: %s NFC.", serial, operation);
    AndroidSvc svcArgs =
        AndroidSvc.builder().setCommand(AndroidSvc.Command.NFC).setOtherArgs(operation).build();
    try {
      adbUtil.svc(serial, svcArgs);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CONNECTIVITY_SET_NFC_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Waits until the Android device connected to any WIFI or mobile network, or timeout.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param ssid the ssid that we should connect to (optional). If null, it would wait for any
   *     network connection.
   * @param timeout max wait and retry time
   * @return {@code true} if succeeds to connect to the given network (or any network if ssid is
   *     null).
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public boolean waitForNetwork(
      String serial, int sdkVersion, @Nullable String ssid, Duration timeout)
      throws InterruptedException {
    logger.atInfo().log("Waiting until device %s connects to SSID '%s'...", serial, ssid);
    return AndroidAdbUtil.waitForDeviceReady(
        UtilArgs.builder().setSerial(serial).setSdkVersion(sdkVersion).build(),
        utilArgs -> isDeviceConnectedToNetwork(utilArgs, ssid),
        WaitArgs.builder()
            .setSleeper(sleeper)
            .setClock(clock)
            .setCheckReadyInterval(WAIT_FOR_NETWORK_INTERVAL)
            .setCheckReadyTimeout(timeout)
            .build());
  }

  /**
   * Decide whether it can connect to wifi based on SSID, SSID security mode, given pwd. Must
   * install WifiUtil.apk before using this method if have to use WifiUtil to enable wifi first.
   *
   * <p>There are cases we can say it shouldn't try to connect to wifi: (1)SSID is empty; (2)SSID
   * doesn't exist in wifiscanner dumpsys, which means probably a wrong SSID provided; (3)SSID
   * exists and it's secured, but no pwd provided; (4)SSID exists and it's open network(unsecured),
   * but pwd is provided; (5)Device wifi cannot be enabled;
   *
   * <p>It doesn't know ahead whether pwd is correct or not without trying to connect the wifi, in
   * this case it will time out (2 minutes by default) if wrong pwd is provided.
   *
   * <p>For same SSID broadcasted on different frequencies, assumes both of them are either secured
   * or not-secured.
   *
   * <p>For hidden SSID or forceTryConnect is true, it'll connect device to that anyway.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param ssid SSID of WIFI to be connected
   * @param pwd password of WIFI to be connected
   * @param scanSsid whether to scan for hidden SSID
   * @param forceTryConnect whether to connect the device to given SSID forcely
   * @return {@code true} if it should try to connect to wifi, {@code false} otherwise
   */
  @VisibleForTesting
  boolean canConnectToWifi(
      String serial,
      int sdkVersion,
      String ssid,
      @Nullable String pwd,
      boolean scanSsid,
      boolean forceTryConnect,
      @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    if (Strings.isNullOrEmpty(ssid)) {
      SharedLogUtil.logMsg(
          logger,
          Level.INFO,
          log,
          /* cause= */ null,
          "Can not connect device %s to wifi as SSID is empty",
          serial);
      return false;
    }

    if (!setWifiEnabled(serial, /* enabled= */ true, log)) {
      SharedLogUtil.logMsg(
          logger,
          Level.INFO,
          log,
          /* cause= */ null,
          "Can not connect device %s to wifi as failed to enable wifi",
          serial);
      return false;
    }

    if (scanSsid) {
      SharedLogUtil.logMsg(
          logger,
          Level.INFO,
          log,
          /* cause= */ null,
          "SSID is hidden, connecting device %s to it anyway",
          serial);
      return true;
    }
    if (forceTryConnect) {
      SharedLogUtil.logMsg(
          logger,
          Level.INFO,
          log,
          /* cause= */ null,
          "Connecting device %s to SSID %s without checking wifi scan results",
          serial,
          ssid);
      return true;
    }

    boolean wifiScanDone = waitForWifiScanResults(serial, sdkVersion, log);
    if (!wifiScanDone) {
      // For debugging purpose, dump wifi scan results to logs.
      SharedLogUtil.logMsg(
          logger,
          Level.INFO,
          log,
          /* cause= */ null,
          "Wifi scan didn't finish as expected, here is the current wifi scan results:%n%s",
          dumpWifiScanResults(serial, sdkVersion));
      return false;
    }

    String wifiLatestScanResult = getWifiLatestScanResults(serial, sdkVersion);
    boolean pwdEmptyOrNull = Strings.isNullOrEmpty(pwd);
    Pattern ssidPattern = Pattern.compile(String.format("(^|\\s)%s(\\s|$)", ssid));
    for (String line : Splitters.LINE_SPLITTER.split(wifiLatestScanResult)) {
      if (ssidPattern.matcher(line).find()) {
        if (PATTERN_WIFI_SECURITY_MODE.matcher(line).find()) {
          SharedLogUtil.logMsg(
              logger,
              Level.INFO,
              log,
              /* cause= */ null,
              "Found SSID %s (secured) in wifiscanner, password is required.",
              ssid);
          return !pwdEmptyOrNull;
        } else {
          SharedLogUtil.logMsg(
              logger,
              Level.INFO,
              log,
              /* cause= */ null,
              "Found SSID %s (unsecured) in wifiscanner, no password should be given.",
              ssid);
          return pwdEmptyOrNull;
        }
      }
    }
    // For debugging purpose, dump real time wifi scan results to logs.
    SharedLogUtil.logMsg(
        logger,
        Level.WARNING,
        log,
        /* cause= */ null,
        "Can't find any matched SSID for ssid [%s].%nIf the SSID is hidden, please specify"
            + " \"wifi_scan_ssid\": \"true\" in the params.%nAnalyzed wifi latest scan results"
            + " for device %s:%n%s%nCurrent wifi scan results for debugging purpose:%n%s",
        ssid,
        serial,
        wifiLatestScanResult,
        dumpWifiScanResults(serial, sdkVersion));
    return false;
  }

  /**
   * Get wifi latest scan results from dumpsys wifi (API 25 and below) / dumpsys wifiscanner (API 26
   * and above).
   */
  private String getWifiLatestScanResults(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    return parseLatestScanResults(dumpWifiScanResults(serial, sdkVersion));
  }

  /**
   * Gets wifi state on a device.
   *
   * @param serial serial number of the device
   * @return device {@link WifiState}
   */
  private WifiState getWifiState(String serial)
      throws MobileHarnessException, InterruptedException {
    String output = adbUtil.dumpSys(serial, DumpSysType.WIFI);
    if (output.contains(WIFI_ENABLED_STATUS_IN_DUMPSYS_WIFI)) {
      return WifiState.ENABLED;
    } else if (output.contains(WIFI_DISABLED_STATUS_IN_DUMPSYS_WIFI)) {
      return WifiState.DISABLED;
    }
    return WifiState.UNKNOWN;
  }

  /**
   * Helper method for {@link #waitForNetwork(String, int, String, Duration)}.
   *
   * @return {@code true} if succeeds to connect to the given network (or any network if ssid is
   *     null).
   */
  private boolean isDeviceConnectedToNetwork(UtilArgs utilArgs, @Nullable String ssid) {
    String serial = utilArgs.serial();
    int sdkVersion = utilArgs.sdkVersion().getAsInt();
    try {
      List<String> currentSsidList = getNetworkSsidList(serial, sdkVersion);
      if (!currentSsidList.isEmpty() && (ssid == null || currentSsidList.contains(ssid))) {
        return true;
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get network SSID list for device %s:%n%s", serial, e.getMessage());
      return false;
    } catch (InterruptedException ie) {
      logger.atWarning().log(
          "Caught interrupted exception when getting device %s network SSID, interrupt current "
              + "thread:%n%s",
          serial, ie.getMessage());
      Thread.currentThread().interrupt();
    }
    return false;
  }

  /** Parse network SSID from dumpsy connectivity/wifi output. */
  private List<String> parseNetworkSsid(String serial, String output) {
    StringBuilder log = new StringBuilder();
    List<String> resultList = new ArrayList<>();
    String result = null;
    for (String line : Splitters.LINE_SPLITTER.split(output)) {
      line = line.trim();
      // Do NOT return null if the device is connected to network.
      if (line.startsWith("NetworkInfo: type:") /* Android <= 4.4.4 */
          || line.startsWith("NetworkAgentInfo") /* Android L */) {
        log.append('\n').append(line);
        if (line.contains("state: CONNECTED/CONNECTED, reason:")) {
          Matcher matcher = PATTERN_NETWORK_SSID_NEW.matcher(line);
          // It does not return here because if Android < 3.2.1, the SSID is in the next line.
          result = matcher.find() ? Strings.nullToEmpty(matcher.group("ssid")) : "";
          resultList.add(result);
        }
      } else if (line.startsWith("SSID: ") /* Android < 3.2.1 */
          || (line.startsWith("mWifiInfo SSID:")
              && line.contains("state: COMPLETED")) /* Android P */) {
        log.append('\n').append(line);
        Matcher matcher =
            isBuildSOrAbove(serial)
                ? PATTERN_NETWORK_SSID_S_AND_ABOVE.matcher(line)
                : PATTERN_NETWORK_SSID_OLD.matcher(line);
        result = matcher.find() ? Strings.nullToEmpty(matcher.group("ssid")) : "";
        resultList.add(result);
      }
    }
    if (result != null) {
      logger.atInfo().log("Device %s connected to network:%s", serial, log);
    }

    return resultList;
  }

  private boolean isBuildSOrAbove(String serial) {
    try {
      int sdkVersion = adbUtil.getIntProperty(serial, AndroidProperty.SDK_VERSION);
      return sdkVersion > AndroidVersion.ANDROID_11.getEndSdkVersion()
          || (sdkVersion == AndroidVersion.ANDROID_11.getEndSdkVersion()
              && adbUtil.getProperty(serial, AndroidProperty.CODENAME).equals(CODENAME_S));
    } catch (MobileHarnessException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      logger.atWarning().log(
          "Failed to check if the build on device %s is S or above: %s",
          serial, MoreThrowables.shortDebugString(e, 0));
      return false;
    }
  }

  /** Parse saved network SSIDs and PSKs from wifi configure file on device. */
  private static ImmutableMap<String, String> parseSavedSsidsAndPsks(
      String wifiConfigContent, int sdkVersion) {
    Map<String, String> result = new HashMap<>();
    if (sdkVersion >= AndroidVersion.OREO.getStartSdkVersion()) {
      // Sample output:
      // <WifiConfiguration>
      // <string name="ConfigKey">&quot;GoogleGuestPSK&quot;WPA_PSK</string>
      // <string name="SSID">&quot;GoogleGuestPSK&quot;</string>
      // <null name="BSSID" />
      // <string name="PreSharedKey">&quot;PskForGoogleGuest&quot;</string>
      // <null name="WEPKeys" />
      // <int name="WEPTxKeyIndex" value="0" />
      // <boolean name="HiddenSSID" value="false" />
      // ...
      // </WifiConfiguration>
      // ...
      // <WifiConfiguration>
      // <string name="ConfigKey">&quot;Pixel_3820&quot;NONE</string>
      // <string name="SSID">&quot;Pixel_3820&quot;</string>
      // <null name="BSSID" />
      // <null name="PreSharedKey" />
      // <null name="WEPKeys" />
      // <int name="WEPTxKeyIndex" value="0" />
      // <boolean name="HiddenSSID" value="false" />
      // ...
      // </WifiConfiguration>

      Matcher matcher = PATTERN_SAVED_SSIDS_AND_PSKS_WIFI_CONFIG_XML.matcher(wifiConfigContent);
      while (matcher.find()) {
        String ssid = matcher.group("ssid");
        if (matcher.group("prePsk").endsWith("<null")) {
          // SSID for an open network is found
          result.put(ssid, "");
        } else {
          Matcher pskOnlyMatcher =
              PATTERN_PSK_IN_WIFI_CONFIG_XML.matcher(matcher.group("afterPsk"));
          if (pskOnlyMatcher.find()) {
            result.put(ssid, pskOnlyMatcher.group("psk"));
          }
        }
      }
    } else {
      // Sample output:
      // ...
      // serial_number=079f36e12133089f
      // device_type=10-0050F204-5
      // config_methods=physical_display virtual_push_button
      // p2p_disabled=1
      //
      // network={
      //   ssid="GoogleGuestPSK"
      //   psk="pUp3EkaP"
      //   priority=4
      // }
      //
      // network={
      //   ssid="GIN-2g"
      //   psk="rrrrr ttt gg"
      //   key_mgmt=WPA-PSK
      //   priority=1
      // }
      //
      // ...

      Matcher matcher = PATTERN_SAVED_SSIDS_AND_PSKS_WPA_CONFIG.matcher(wifiConfigContent);
      while (matcher.find()) {
        String ssid = matcher.group("ssid");
        String psk = matcher.group("psk");
        if (psk == null) {
          psk = "";
        }
        result.put(ssid, psk);
      }
    }
    return ImmutableMap.copyOf(result);
  }

  /**
   * Enable or disable device wifi. Must install WifiUtil.apk before using this method if have to
   * use WifiUtil to manage wifi.
   *
   * <p>It uses WifiUtil to enable/disable wifi on device, which would work on release builds and
   * dev builds. But it won't work on release builds on Q+ because {@code
   * WifiManager#setWifiEnabled(boolean)} is deprecated.
   *
   * <p>If WifiUtil failed enabling/disabling wifi, it'll re-try with another shell command to
   * enable/disable wifi, while it may fail on some OEM devices.
   *
   * @param serial serial number of the device
   * @param enabled {@code true} to enable, {@code false} to disable
   * @return {@code true} if device wifi got enabled or disabled as requested in {@code enabled},
   *     {@code false} if the request cannot be satisfied
   */
  @CanIgnoreReturnValue
  @VisibleForTesting
  boolean setWifiEnabled(String serial, boolean enabled, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    WifiState targetWifiState = enabled ? WifiState.ENABLED : WifiState.DISABLED;
    if (currentWifiStateMatchTargetState(serial, targetWifiState)) {
      return true;
    }

    SharedLogUtil.logMsg(
        logger,
        Level.INFO,
        log,
        /* cause= */ null,
        "%s wifi on device %s...",
        enabled ? "Enabling" : "Disabling",
        serial);

    boolean setWifiSucceeded = false;
    if (packageManagerUtil
        .listPackages(serial, PackageType.THIRD_PARTY)
        .contains(WIFI_UTIL_PACKAGE_NAME)) {
      try {
        setWifiSucceeded =
            adb.runShellWithRetry(
                    serial,
                    String.format(
                        WIFI_UTIL_ADB_SHELL_SET_WIFI_TEMPLATE,
                        enabled ? "enableWifi" : "disableWifi"))
                .contains(WIFI_UTIL_SET_WIFI_SUCCESSFULLY);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            enabled
                ? AndroidErrorId.ANDROID_CONNECTIVITY_ENABLE_WIFI_VIA_WIFIUTIL_ERROR
                : AndroidErrorId.ANDROID_CONNECTIVITY_DISABLE_WIFI_VIA_WIFIUTIL_ERROR,
            e.getMessage(),
            e);
      }
    }
    if (!setWifiSucceeded) {
      String wifiOp = enabled ? "enable" : "disable";
      SharedLogUtil.logMsg(
          logger,
          Level.INFO,
          log,
          /* cause= */ null,
          "%s wifi on device %s via svc...",
          wifiOp,
          serial);
      AndroidSvc svcArgs =
          AndroidSvc.builder().setCommand(AndroidSvc.Command.WIFI).setOtherArgs(wifiOp).build();
      try {
        adbUtil.svc(serial, svcArgs);
      } catch (MobileHarnessException e) {
        SharedLogUtil.logMsg(
            logger,
            Level.SEVERE,
            log,
            /* cause= */ null,
            "Failed to %s wifi on device %s, upcoming tests may not work as expected:%n%s",
            enabled ? "enable" : "disable",
            serial,
            e.getMessage());
        return false;
      }
    }

    boolean wifiStateReady = waitForWifiState(serial, targetWifiState);
    if (!wifiStateReady) {
      SharedLogUtil.logMsg(
          logger,
          Level.WARNING,
          log,
          /* cause= */ null,
          "Failed %s wifi on device %s",
          enabled ? "enabling" : "disabling",
          serial);
    }
    return wifiStateReady;
  }

  /** Waits until device wifi state becomes to {@code targetState}. */
  private boolean waitForWifiState(String serial, WifiState targetState)
      throws InterruptedException {
    return AndroidAdbUtil.waitForDeviceReady(
        UtilArgs.builder().setSerial(serial).build(),
        utilArgs -> currentWifiStateMatchTargetState(utilArgs.serial(), targetState),
        WaitArgs.builder()
            .setSleeper(sleeper)
            .setClock(clock)
            .setCheckReadyInterval(WAIT_FOR_WIFI_STATE_INTERVAL)
            .setCheckReadyTimeout(WAIT_FOR_WIFI_STATE_TIMEOUT)
            .build());
  }

  private boolean currentWifiStateMatchTargetState(String serial, WifiState targetState) {
    try {
      WifiState curWifiState = getWifiState(serial);
      return curWifiState.equals(targetState);
    } catch (MobileHarnessException e) {
      logger.atWarning().log("Failed to get wifi state for device %s:%n%s", serial, e.getMessage());
      return false;
    } catch (InterruptedException ie) {
      logger.atWarning().log(
          "Caught interrupted exception when getting device %s wifi state, interrupt current "
              + "thread:%n%s",
          serial, ie.getMessage());
      Thread.currentThread().interrupt();
    }
    return false;
  }

  /**
   * Return true if auto wifi scan is done within specific duration of time after wifi enabled, and
   * ready to retrieve scan results, false otherwise.
   *
   * <p>For SDK > 25, it checks wifi scan state from dumpsys wifiscanner to determine if wifi scan
   * is done. Here is wifi scan states transition from wifi disabled to wifi enabled: 1) when wifi
   * disabled: IdleState; 2) right after wifi enabled: DefaultState, and then ScanningState; 3) when
   * wifi scan is done: IdleState.
   *
   * <p>For SDK <= 25, it checks wifi scan results section from dumpsys wifi to determine if wifi
   * scan is done. If it finds any scan results, it knows wifi scan is done.
   *
   * <p>Tested from API 15 to API 28, and Q.
   */
  @VisibleForTesting
  boolean waitForWifiScanResults(String serial, int sdkVersion, @Nullable LogCollector<?> log)
      throws InterruptedException {
    return AndroidAdbUtil.waitForDeviceReady(
        UtilArgs.builder().setSerial(serial).build(),
        utilArgs -> checkWifiScanResultsReady(utilArgs.serial(), sdkVersion, log),
        WaitArgs.builder()
            .setSleeper(sleeper)
            .setClock(clock)
            .setCheckReadyInterval(WAIT_FOR_WIFI_SCAN_RESULTS_INTERVAL)
            .setCheckReadyTimeout(WAIT_FOR_WIFI_SCAN_RESULTS_TIMEOUT)
            .build());
  }

  /** Helper method for {@link #waitForWifiScanResults(String, int, LogCollector)}. */
  private boolean checkWifiScanResultsReady(
      String serial, int sdkVersion, @Nullable LogCollector<?> log) {
    try {
      SharedLogUtil.logMsg(
          logger, Level.INFO, log, /* cause= */ null, "Checking wifi scan status...");
      return checkWifiScanResults(
          dumpWifiScanResults(serial, sdkVersion), isSdkVersionAboveNougat(sdkVersion));
    } catch (MobileHarnessException e) {
      SharedLogUtil.logMsg(
          logger,
          Level.WARNING,
          log,
          /* cause= */ null,
          "Failed to get wifi scan results for device %s:%n%s",
          serial,
          e.getMessage());
      return false;
    } catch (InterruptedException ie) {
      SharedLogUtil.logMsg(
          logger,
          Level.WARNING,
          log,
          /* cause= */ null,
          "Caught interrupted exception when getting device %s wifi scan results, interrupt "
              + "current thread:%n%s",
          serial,
          ie.getMessage());
      Thread.currentThread().interrupt();
    }
    return false;
  }

  private String dumpWifiScanResults(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    return isSdkVersionAboveNougat(sdkVersion)
        ? adbUtil.dumpSys(serial, DumpSysType.WIFISCANNER)
        : adbUtil.dumpSys(serial, DumpSysType.WIFI);
  }

  private boolean isSdkVersionAboveNougat(int sdkVersion) {
    return sdkVersion > AndroidVersion.NOUGAT.getEndSdkVersion();
  }

  @VisibleForTesting
  static boolean checkWifiScanResults(String wifiScanResults, boolean isAboveNougat) {
    return isAboveNougat
        ? Objects.equals(parseSingleScanStateMachineCurState(wifiScanResults), WIFI_SCAN_IDLE_STATE)
        : !parseLatestScanResults(wifiScanResults).isEmpty();
  }

  private static String parseSingleScanStateMachineCurState(String wifiScanResults) {
    String start = "WifiSingleScanStateMachine:";
    boolean isOn = false;

    for (String line : Splitters.LINE_SPLITTER.split(wifiScanResults)) {
      line = line.trim();
      if (!isOn && line.equals(start)) {
        isOn = true;
        continue;
      }

      if (isOn) {
        Matcher matcher = LINE_PATTERN.matcher(line);
        if (matcher.find()) {
          return matcher.group("scanresults").trim();
        }
      }
    }
    return "";
  }

  @VisibleForTesting
  static String parseLatestScanResults(String wifiScanResults) {
    String start = "Latest scan results:";
    StringJoiner joiner = new StringJoiner("\n");
    boolean isOn = false;
    boolean hasSkippedHeader = false;

    for (String line : Splitters.LINE_SPLITTER.trimResults().split(wifiScanResults)) {
      if (!isOn && line.equals(start)) {
        isOn = true;
        continue;
      }

      if (isOn) {
        if (hasSkippedHeader && !line.isEmpty()) {
          joiner.add(line);
        } else if (!hasSkippedHeader) {
          hasSkippedHeader = true; // Skip the header line "BSSID Frequency RSSI Age SSID FLAGS"
        } else {
          return joiner.toString();
        }
      }
    }
    return "";
  }

  /*
   * Returns true if the given {@code address} is a valid address.
   *
   * <p>Example of valid addresses: fe80::507a:c5ff:fe0b:f1b3/64, 100.112.36.36/22,
   *  fe80::507a:c5ff:fe0b:f1b3 and 100.112.36.36.
   */
  static boolean verifyNetworkLinkAddress(String address) {
    Matcher matcher = PATTERN_NETWORK_LINK_ADDRESS.matcher(address);
    if (matcher.matches()) {
      address = matcher.group(1);
    }
    return InetAddresses.isInetAddress(address);
  }
}
