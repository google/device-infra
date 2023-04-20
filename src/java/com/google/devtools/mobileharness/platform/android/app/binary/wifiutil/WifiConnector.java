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

package com.google.devtools.mobileharness.platform.android.app.binary.wifiutil;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.Callable;
import org.json.JSONException;
import org.json.JSONObject;

/** A helper class to connect to wifi networks. */
@SuppressLint("WifiManagerPotentialLeak,HardwareIds")
public class WifiConnector {

  private static final String TAG = WifiConnector.class.getSimpleName();
  private static final long DEFAULT_TIMEOUT = 120 * 1000;
  private static final long DEFAULT_WAIT_TIME = 5 * 1000;
  private static final long POLL_TIME = 1000;

  private Context mContext;
  private WifiManager mWifiManager;

  /** Thrown when an error occurs while manipulating Wi-Fi services. */
  public static class WifiException extends Exception {

    public WifiException(String msg) {
      super(msg);
    }

    public WifiException(String msg, Throwable cause) {
      super(msg, cause);
    }
  }

  public WifiConnector(final Context context) {
    mContext = context;
    mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
  }

  private static String quote(String str) {
    return String.format("\"%s\"", str);
  }

  /**
   * Waits until an expected condition is satisfied for {@code timeout}.
   *
   * @param checker a <code>Callable</code> to check the expected condition
   * @param description a description of what this callable is doing
   * @param timeout the duration to wait (millis) for the expected condition
   * @throws WifiException if DEFAULT_TIMEOUT expires
   * @return time in millis spent waiting
   */
  private long waitForCallable(
      final Callable<Boolean> checker, final String description, final long timeout)
      throws WifiException {
    if (timeout <= 0) {
      throw new WifiException(
          String.format("Failed %s due to invalid timeout (%d ms)", description, timeout));
    }
    long startTime = SystemClock.uptimeMillis();
    long endTime = startTime + timeout;
    try {
      while (SystemClock.uptimeMillis() < endTime) {
        if (checker.call()) {
          long elapsed = SystemClock.uptimeMillis() - startTime;
          Log.i(TAG, String.format("Time elapsed waiting for %s: %d ms", description, elapsed));
          return elapsed;
        }
        Thread.sleep(POLL_TIME);
      }
    } catch (final Exception e) {
      throw new WifiException("failed to wait for callable", e);
    }
    throw new WifiException(
        String.format("Failed %s due to exceeding timeout (%d ms)", description, timeout));
  }

  private void waitForCallable(final Callable<Boolean> checker, final String description)
      throws WifiException {
    long unused = waitForCallable(checker, description, DEFAULT_TIMEOUT);
  }

  /**
   * Adds a Wi-Fi network configuration.
   *
   * @param ssid SSID of a Wi-Fi network
   * @param psk PSK(Pre-Shared Key) of a Wi-Fi network. This can be null if the given SSID is for an
   *     open network
   * @param disableMacRandomization option to disable MAC address randomization, using the device's
   *     hardware MAC address
   * @return the network ID of a new network configuration
   * @throws WifiException if the operation fails
   */
  public int addNetwork(
      final String ssid,
      final String psk,
      final boolean scanSsid,
      final boolean disableMacRandomization)
      throws WifiException {
    // Skip adding network if it's already added in the device
    // TODO: Fix the permission issue for the APK to add/update already added network
    int networkId = getNetworkId(ssid);
    if (networkId >= 0) {
      return networkId;
    }
    final WifiConfiguration config = new WifiConfiguration();
    // A string SSID _must_ be enclosed in double-quotation marks
    config.SSID = quote(ssid);

    if (scanSsid) {
      config.hiddenSSID = true;
    }

    if (psk == null) {
      // KeyMgmt should be NONE only
      final BitSet keymgmt = new BitSet();
      keymgmt.set(WifiConfiguration.KeyMgmt.NONE);
      config.allowedKeyManagement = keymgmt;
    } else {
      config.preSharedKey = quote(psk);
    }

    if (disableMacRandomization && Build.VERSION.SDK_INT >= 33) { // Build.VERSION_CODES.TIRAMISU
      // config.setMacRandomizationSetting(WifiConfiguration.RANDOMIZATION_NONE);
      try {
        config.getClass().getMethod("setMacRandomizationSetting", int.class).invoke(config, 0);
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        throw new AssertionError(e);
      }
    }

    networkId = mWifiManager.addNetwork(config);
    if (-1 == networkId) {
      throw new WifiException("failed to add network");
    }

    return networkId;
  }

  private int getNetworkId(String ssid) {
    List<WifiConfiguration> netlist = mWifiManager.getConfiguredNetworks();
    for (WifiConfiguration config : netlist) {
      if (quote(ssid).equals(config.SSID)) {
        return config.networkId;
      }
    }
    return -1;
  }

  /**
   * Removes all Wi-Fi network configurations.
   *
   * @param throwIfFail <code>true</code> if a caller wants an exception to be thrown when the
   *     operation fails. Otherwise <code>false</code>.
   * @throws WifiException if the operation fails
   */
  public void removeAllNetworks(boolean throwIfFail) throws WifiException {
    List<WifiConfiguration> netlist = mWifiManager.getConfiguredNetworks();
    if (netlist != null) {
      int failCount = 0;
      for (WifiConfiguration config : netlist) {
        if (!mWifiManager.removeNetwork(config.networkId)) {
          Log.w(
              TAG,
              String.format(
                  "failed to remove network id %d (SSID = %s)", config.networkId, config.SSID));
          failCount++;
        }
      }
      if (0 < failCount && throwIfFail) {
        throw new WifiException("failed to remove all networks.");
      }
    }
  }

  /**
   * Check network connectivity by sending a HTTP request to a given URL.
   *
   * @param urlToCheck URL to send a test request to
   * @return <code>true</code> if the test request succeeds. Otherwise <code>false</code>.
   */
  public static boolean checkConnectivity(final String urlToCheck) {
    URL url = null;
    try {
      url = new URL(urlToCheck);
    } catch (MalformedURLException e) {
      throw new RuntimeException("Malformed URL", e);
    }
    HttpURLConnection urlConnection = null;
    try {
      urlConnection = (HttpURLConnection) url.openConnection();
      urlConnection.connect();
    } catch (final Exception e) {
      Log.e(TAG, "Failed to open connection to check connectivity", e);
      return false;
    } finally {
      if (urlConnection != null) {
        urlConnection.disconnect();
      }
    }
    return true;
  }

  /**
   * Connects a device to a given Wi-Fi network and check connectivity.
   *
   * @param ssid SSID of a Wi-Fi network
   * @param psk PSK of a Wi-Fi network
   * @param urlToCheck URL to use when checking connectivity
   * @param connectTimeout duration in seconds to wait for connecting to the network or {@code
   *     DEFAULT_TIMEOUT} millis if -1 is passed.
   * @param scanSsid whether to scan for hidden SSID for this network
   * @param disableMacRandomization option to disable MAC address randomization, using the device's
   *     hardware MAC address
   * @throws WifiException if the operation fails
   */
  public void connectToNetwork(
      final String ssid,
      final String psk,
      final String urlToCheck,
      long connectTimeout,
      final boolean scanSsid,
      final boolean disableMacRandomization)
      throws WifiException {
    if (!mWifiManager.isWifiEnabled()) {
      throw new WifiException("wifi not enabled");
    }

    updateLastNetwork(ssid, psk, scanSsid);

    connectTimeout = connectTimeout == -1 ? DEFAULT_TIMEOUT : (connectTimeout * 1000);
    long timeSpent;
    timeSpent =
        waitForCallable(
            new Callable<Boolean>() {
              @Override
              public Boolean call() throws Exception {
                return mWifiManager.isWifiEnabled();
              }
            },
            "enabling wifi",
            connectTimeout);

    // Wait for some seconds to let wifi to be stable. This increases the chance of success for
    // subsequent operations.
    try {
      Thread.sleep(DEFAULT_WAIT_TIME);
    } catch (InterruptedException e) {
      throw new WifiException(String.format("failed to sleep for %d ms", DEFAULT_WAIT_TIME), e);
    }

    removeAllNetworks(false);

    final int networkId = addNetwork(ssid, psk, scanSsid, disableMacRandomization);
    if (!mWifiManager.enableNetwork(networkId, true)) {
      throw new WifiException(String.format("failed to enable network %s", ssid));
    }
    if (!mWifiManager.saveConfiguration()) {
      Log.w(TAG, String.format("failed to save configuration %s", ssid));
    }
    connectTimeout = calculateTimeLeft(connectTimeout, timeSpent);
    timeSpent =
        waitForCallable(
            new Callable<Boolean>() {
              @Override
              public Boolean call() throws Exception {
                final SupplicantState state = mWifiManager.getConnectionInfo().getSupplicantState();
                return SupplicantState.COMPLETED == state;
              }
            },
            String.format("associating to network (ssid: %s)", ssid),
            connectTimeout);

    connectTimeout = calculateTimeLeft(connectTimeout, timeSpent);
    timeSpent =
        waitForCallable(
            new Callable<Boolean>() {
              @Override
              public Boolean call() throws Exception {
                final WifiInfo info = mWifiManager.getConnectionInfo();
                return 0 != info.getIpAddress();
              }
            },
            String.format("dhcp assignment (ssid: %s)", ssid),
            connectTimeout);

    connectTimeout = calculateTimeLeft(connectTimeout, timeSpent);
    long unused =
        waitForCallable(
            new Callable<Boolean>() {
              @Override
              public Boolean call() throws Exception {
                return checkConnectivity(urlToCheck);
              }
            },
            String.format("request to %s (ssid: %s)", urlToCheck, ssid),
            connectTimeout);
  }

  /**
   * Connects a device to a given Wi-Fi network and check connectivity using
   *
   * @param ssid SSID of a Wi-Fi network
   * @param psk PSK of a Wi-Fi network
   * @param urlToCheck URL to use when checking connectivity
   * @param connectTimeout duration in seconds to wait for connecting to the network or {@code
   *     DEFAULT_TIMEOUT} millis if -1 is passed.
   * @throws WifiException if the operation fails
   */
  public void connectToNetwork(
      final String ssid, final String psk, final String urlToCheck, long connectTimeout)
      throws WifiException {
    connectToNetwork(ssid, psk, urlToCheck, -1, false, false);
  }

  /**
   * Connects a device to a given Wi-Fi network and check connectivity using {@code
   * DEFAULT_TIMEOUT}.
   *
   * @param ssid SSID of a Wi-Fi network
   * @param psk PSK of a Wi-Fi network
   * @param urlToCheck URL to use when checking connectivity
   * @throws WifiException if the operation fails
   */
  public void connectToNetwork(final String ssid, final String psk, final String urlToCheck)
      throws WifiException {
    connectToNetwork(ssid, psk, urlToCheck, -1);
  }

  /**
   * Disconnects a device from Wi-Fi network and disable Wi-Fi.
   *
   * @throws WifiException if the operation fails
   */
  public void disconnectFromNetwork() throws WifiException {
    if (mWifiManager.isWifiEnabled()) {
      removeAllNetworks(false);
    }
  }

  /**
   * Returns Wi-Fi information of a device.
   *
   * @return a {@link JSONObject} containing the current Wi-Fi status
   * @throws WifiException if the operation fails
   */
  public JSONObject getWifiInfo() throws WifiException {
    final JSONObject json = new JSONObject();

    try {
      final WifiInfo info = mWifiManager.getConnectionInfo();
      json.put("ssid", info.getSSID());
      json.put("bssid", info.getBSSID());
      json.put("hiddenSsid", info.getHiddenSSID());
      final int addr = info.getIpAddress();
      // IP address is stored with the first octet in the lowest byte
      final int a = (addr >> 0) & 0xff;
      final int b = (addr >> 8) & 0xff;
      final int c = (addr >> 16) & 0xff;
      final int d = (addr >> 24) & 0xff;
      json.put("ipAddress", String.format("%s.%s.%s.%s", a, b, c, d));
      json.put("linkSpeed", info.getLinkSpeed());
      json.put("rssi", info.getRssi());
      json.put("macAddress", info.getMacAddress());
    } catch (final JSONException e) {
      throw new WifiException(e.toString());
    }

    return json;
  }

  /**
   * Reconnects a device to a last connected Wi-Fi network and check connectivity.
   *
   * @param urlToCheck URL to use when checking connectivity
   * @throws WifiException if the operation fails
   */
  public void reconnectToLastNetwork(String urlToCheck) throws WifiException {
    final SharedPreferences prefs = mContext.getSharedPreferences(TAG, 0);
    final String ssid = prefs.getString("ssid", null);
    final String psk = prefs.getString("psk", null);
    final boolean scanSsid = prefs.getBoolean("scan_ssid", false);
    if (ssid == null) {
      throw new WifiException("No last connected network.");
    }
    connectToNetwork(ssid, psk, urlToCheck, -1, scanSsid, false);
  }

  private void updateLastNetwork(final String ssid, final String psk, final boolean scanSsid) {
    final SharedPreferences prefs = mContext.getSharedPreferences(TAG, 0);
    final SharedPreferences.Editor editor = prefs.edit();
    editor.putString("ssid", ssid);
    editor.putString("psk", psk);
    editor.putBoolean("scan_ssid", scanSsid);
    editor.commit();
  }

  private long calculateTimeLeft(long connectTimeout, long timeSpent) {
    if (timeSpent > connectTimeout) {
      return 0;
    } else {
      return connectTimeout - timeSpent;
    }
  }
}
