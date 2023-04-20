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
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import com.google.devtools.mobileharness.platform.android.app.binary.wifiutil.WifiConnector.WifiException;

/**
 * An instrumentation class to manipulate Wi-Fi services on device.
 *
 * <p>adb shell am instrument -e method (method name) -e arg1 val1 -e arg2 val2 -e arg3 val3 -w
 * com.google.devtools.mobileharness.platform.android.app.binary.wifiutil/.WifiUtil
 */
@SuppressLint("WifiManagerLeak")
public class WifiUtil extends Instrumentation {
  // TODO: document exposed API methods and arguments
  private static final String TAG = "WifiUtil";

  private static final String DEFAULT_URL_TO_CHECK = "http://www.google.com";
  private static final int API_LEVEL_Q_TBD = 29;

  private Bundle mArguments;

  static class MissingArgException extends Exception {
    public MissingArgException(String msg) {
      super(msg);
    }

    public static MissingArgException fromArg(String arg) {
      return new MissingArgException(String.format("Error: missing mandatory argument '%s'", arg));
    }
  }

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    mArguments = arguments;
    // elevate permission if on Qt or later
    int apiLevel = Build.VERSION.SDK_INT;
    if (!"REL".equals(Build.VERSION.CODENAME)) {
      // add one extra if platform is under development
      // i.e. trying to predict the next API level number
      apiLevel++;
    }
    // on Qt or higher
    // TODO: change to Build.VERSION_CODES.Q after API level is finalized
    if (apiLevel >= API_LEVEL_Q_TBD) {
      getUiAutomation().adoptShellPermissionIdentity();
    }
    start();
  }

  /**
   * Fails an instrumentation request.
   *
   * @param errMsg an error message
   */
  private void fail(String errMsg) {
    Log.e(TAG, errMsg);
    Bundle result = new Bundle();
    result.putString("error", errMsg);
    finish(Activity.RESULT_CANCELED, result);
  }

  /**
   * Returns the string value of an argument for the specified name, or throws {@link
   * MissingArgException} if the argument is not found or empty.
   *
   * @param arg the name of an argument
   * @return the value of an argument
   * @throws MissingArgException if the argument is not found
   */
  private String expectString(String arg) throws MissingArgException {
    String val = mArguments.getString(arg);
    if (TextUtils.isEmpty(val)) {
      throw MissingArgException.fromArg(arg);
    }

    return val;
  }

  /**
   * Returns the value of a string argument for the specified name, or defaultValue if the argument
   * is not found or empty.
   *
   * @param arg the name of an argument
   * @param defaultValue a value to return if the argument is not found
   * @return the value of an argument
   */
  private String getString(String arg, String defaultValue) {
    String val = mArguments.getString(arg);
    if (TextUtils.isEmpty(val)) {
      return defaultValue;
    }

    return val;
  }

  /**
   * Returns the integer value of an argument for the specified name, or throws {@link
   * MissingArgException} if the argument is not found or cannot be parsed to an integer.
   *
   * @param arg the name of an argument
   * @return the value of an argument
   * @throws MissingArgException if the argument is not found
   */
  private int expectInteger(String arg) throws MissingArgException {
    String val = expectString(arg);
    int intVal;
    try {
      intVal = Integer.parseInt(val);
    } catch (NumberFormatException e) {
      final String msg = String.format("Couldn't parse arg '%s': %s", arg, e.getMessage());
      throw new MissingArgException(msg);
    }

    return intVal;
  }

  /**
   * Returns the integer value of an argument for the specified name, or defaultValue if the
   * argument is not found, empty, or cannot be parsed.
   *
   * @param arg the name of an argument
   * @param defaultValue a value to return if the argument is not found
   * @return the value of an argument
   */
  private int getInteger(String arg, int defaultValue) {
    try {
      return expectInteger(arg);
    } catch (MissingArgException e) {
      return defaultValue;
    }
  }

  private boolean getBoolean(String arg, boolean defaultValue) {
    try {
      return Boolean.parseBoolean(expectString(arg));
    } catch (MissingArgException e) {
      return defaultValue;
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    final Bundle result = new Bundle();

    try {
      final String method = expectString("method");

      WifiManager wifiManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
      if (wifiManager == null) {
        fail("Couldn't get WifiManager reference; goodbye!");
        return;
      }
      WifiConnector connector = new WifiConnector(getContext());

      // As a pattern, method implementations below should gather arguments _first_, and then
      // use those arguments so that the system is not left in an inconsistent state if an
      // argument is missing in the middle of an implementation.
      if ("enableWifi".equals(method)) {
        // Doesn't work on Q
        result.putBoolean("result", wifiManager.setWifiEnabled(true));
      } else if ("disableWifi".equals(method)) {
        // Doesn't work on Q
        result.putBoolean("result", wifiManager.setWifiEnabled(false));
      } else if ("addOpenNetwork".equals(method)) {
        final String ssid = expectString("ssid");
        final boolean scanSsid = getBoolean("scan_ssid", false);
        final boolean disableMacRandomization = getBoolean("disableMacRandomization", false);

        result.putInt(
            "result", connector.addNetwork(ssid, null, scanSsid, disableMacRandomization));

      } else if ("addWpaPskNetwork".equals(method)) {
        final String ssid = expectString("ssid");
        final boolean scanSsid = getBoolean("scan_ssid", false);
        final String psk = expectString("psk");
        final boolean disableMacRandomization = getBoolean("disableMacRandomization", false);

        result.putInt("result", connector.addNetwork(ssid, psk, scanSsid, disableMacRandomization));

      } else if ("associateNetwork".equals(method)) {
        final int id = expectInteger("id");

        result.putBoolean(
            "result", wifiManager.enableNetwork(id, true /* disable other networks */));

      } else if ("disconnect".equals(method)) {
        result.putBoolean("result", wifiManager.disconnect());

      } else if ("disableNetwork".equals(method)) {
        final int id = expectInteger("id");

        result.putBoolean("result", wifiManager.disableNetwork(id));

      } else if ("isWifiEnabled".equals(method)) {
        result.putBoolean("result", wifiManager.isWifiEnabled());

      } else if ("getIpAddress".equals(method)) {
        final WifiInfo info = wifiManager.getConnectionInfo();
        final int addr = info.getIpAddress();

        // IP address is stored with the first octet in the lowest byte
        final int a = (addr >> 0) & 0xff;
        final int b = (addr >> 8) & 0xff;
        final int c = (addr >> 16) & 0xff;
        final int d = (addr >> 24) & 0xff;

        result.putString("result", String.format("%s.%s.%s.%s", a, b, c, d));

      } else if ("getSSID".equals(method)) {
        final WifiInfo info = wifiManager.getConnectionInfo();

        result.putString("result", info.getSSID());

      } else if ("getBSSID".equals(method)) {
        final WifiInfo info = wifiManager.getConnectionInfo();

        result.putString("result", info.getBSSID());

      } else if ("removeAllNetworks".equals(method)) {
        connector.removeAllNetworks(true);

        result.putBoolean("result", true);

      } else if ("removeNetwork".equals(method)) {
        final int id = expectInteger("id");

        result.putBoolean("result", wifiManager.removeNetwork(id));

      } else if ("saveConfiguration".equals(method)) {
        result.putBoolean("result", wifiManager.saveConfiguration());

      } else if ("getSupplicantState".equals(method)) {
        String state = wifiManager.getConnectionInfo().getSupplicantState().name();
        result.putString("result", state);

      } else if ("checkConnectivity".equals(method)) {
        final String url = getString("urlToCheck", DEFAULT_URL_TO_CHECK);

        result.putBoolean("result", connector.checkConnectivity(url));

      } else if ("connectToNetwork".equals(method)) {
        final String ssid = expectString("ssid");
        final boolean scanSsid = getBoolean("scan_ssid", false);
        final String psk = getString("psk", null);
        final String pingUrl = getString("urlToCheck", DEFAULT_URL_TO_CHECK);
        final long connectTimeout = getInteger("connectTimeout", -1);
        final boolean disableMacRandomization = getBoolean("disableMacRandomization", false);
        connector.connectToNetwork(
            ssid, psk, pingUrl, connectTimeout, scanSsid, disableMacRandomization);

        result.putBoolean("result", true);

      } else if ("disconnectFromNetwork".equals(method)) {
        connector.disconnectFromNetwork();

        result.putBoolean("result", true);

      } else if ("getWifiInfo".equals(method)) {
        result.putString("result", connector.getWifiInfo().toString());

      } else if ("startMonitor".equals(method)) {
        final int interval = expectInteger("interval");
        final String urlToCheck = getString("urlToCheck", DEFAULT_URL_TO_CHECK);

        WifiMonitorService.enable(getContext(), interval, urlToCheck);

        result.putBoolean("result", true);

      } else if ("stopMonitor".equals(method)) {
        final Context context = getContext();
        WifiMonitorService.disable(context);

        result.putString("result", WifiMonitorService.getData(context));

      } else {
        fail(String.format("Didn't recognize method '%s'", method));
        return;
      }
    } catch (WifiException | MissingArgException e) {
      fail(e.getMessage());
      return;
    }

    finish(Activity.RESULT_OK, result);
  }
}
