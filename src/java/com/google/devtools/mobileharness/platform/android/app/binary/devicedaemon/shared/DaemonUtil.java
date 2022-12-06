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

package com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.shared;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.TextView;
import androidx.annotation.Nullable;

/** Mobile Harness device daemon util class. */
public class DaemonUtil {
  private static final String TAG = DaemonUtil.class.getCanonicalName();

  private static final String WIFI_UNKNOWN = "WiFi unknown";

  private static final String WIFI_DISCONNECTED = "WiFi disconnected";

  private static final String PHONE_UNKNOWN = "Phone number unknown";

  /**
   * SSID returned by {@link WifiInfo#getSSID} if not enough permissions granted, or location
   * service not enabled on P+ devices.
   */
  private static final String UNKNOWN_SSID = "<unknown ssid>";

  /** The screen brightness when the activity comes to the foreground. */
  private static final float SCREEN_BRIGHTNESS = 0.08f;

  /** Update device id. */
  public void updateDeviceId(TextView idView, TextView mirroredIdView, @Nullable String id) {
    if (isStringNullOrEmpty(id)) {
      id = getDeviceSerial();
      Log.i(TAG, "No serial provided, get serial from device: " + id);
    }
    try {
      idView.setText(id);
    } catch (NoSuchFieldError e) {
      Log.e(TAG, "Failed to load device id: " + e.getMessage());
    }

    try {
      mirroredIdView.setText(id);
    } catch (NoSuchFieldError e) {
      Log.e(TAG, "Failed to load reversed device id: " + e.getMessage());
    }
  }

  @SuppressWarnings("HardwareIds")
  private String getDeviceSerial() {
    String serial = "";
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // From Q, Build.getSerial may return unknown or throw SecurityException,
      // https://developer.android.com/reference/android/os/Build#getSerial()
      serial = "unknown";
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // Requires the READ_PHONE_STATE permission
      serial = Build.getSerial();
    } else {
      // Deprecated in API level 26
      serial = Build.SERIAL;
    }
    return serial;
  }

  /** Update device build info. */
  public void updateDeviceModelVersionBuildInfo(TextView buildView) {
    try {
      buildView.setText(
          String.format(
              "%s(%s)\n%s(%s)(%s)\n%s",
              Build.MODEL,
              Build.TAGS,
              Build.VERSION.RELEASE,
              Build.VERSION.SDK_INT,
              Build.ID,
              Build.DISPLAY));
    } catch (NoSuchFieldError e) {
      Log.e(TAG, "Failed to load build info: " + e.getMessage());
    }
  }

  /** Update device host info. */
  public void updateHostInfo(TextView hostView, @Nullable String hostname) {
    if (isStringNullOrEmpty(hostname)) {
      hostView.setText("");
    } else {
      hostView.setText(hostname);
    }
  }

  /** Update device labels info. */
  public void updateLabelsInfo(TextView labelView, @Nullable String labels) {
    if (isStringNullOrEmpty(labels)) {
      labelView.setVisibility(View.GONE);
    } else {
      labelView.setText(labels);
    }
  }

  /** Update device owners info. */
  @SuppressWarnings("SetTextI18n")
  public void updateOwnerInfo(TextView ownersView, @Nullable String owners) {
    if (isStringNullOrEmpty(owners)) {
      ownersView.setVisibility(View.GONE);
    } else {
      ownersView.setText("owners:" + owners);
    }
  }

  /**
   * Update the WiFi info label according to the current WIFI status.
   *
   * <p>Need android.permission.ACCESS_NETWORK_STATE, android.permission.ACCESS_WIFI_STATE
   */
  public void updateWifiInfo(
      TextView wifiView, @Nullable WifiManager wifiMgr, @Nullable String explicitSsid) {
    Log.i(TAG, "WIFI info changed; updating");
    if (wifiMgr == null) {
      wifiView.setText(WIFI_UNKNOWN);
    } else if (wifiMgr.getWifiState() != WifiManager.WIFI_STATE_ENABLED) {
      wifiView.setText(WIFI_DISCONNECTED);
    } else {
      String ssidFromWifiMgr = getSsidFromWifiManager(wifiMgr);
      String ssid =
          UNKNOWN_SSID.equals(ssidFromWifiMgr) && !isStringNullOrEmpty(explicitSsid)
              ? explicitSsid
              : ssidFromWifiMgr;
      Log.i(TAG, String.format("Setting SSID with value [%s]", ssid));
      wifiView.setText(ssid);
    }
  }

  private String getSsidFromWifiManager(WifiManager wifiMgr) {
    WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
    if (wifiInfo == null) {
      return WIFI_UNKNOWN;
    } else {
      String ssid = wifiInfo.getSSID();
      if (ssid == null) {
        return WIFI_UNKNOWN;
      } else if (ssid.equals("0x")) {
        // WIFI is turned on but not connected.
        return WIFI_DISCONNECTED;
      } else {
        return ssid;
      }
    }
  }

  /**
   * Update phone number info.
   *
   * <p>Need android.permission.READ_PHONE_STATE
   */
  @SuppressWarnings("HardwareIds")
  public void updatePhoneInfo(TextView phoneView, @Nullable TelephonyManager telMgr) {
    Log.i(TAG, "Phone info changed; updating");
    if (telMgr == null) {
      phoneView.setText(PHONE_UNKNOWN);
    } else {
      String line1Number = telMgr.getLine1Number();
      if (isStringNullOrEmpty(line1Number)) {
        phoneView.setText(PHONE_UNKNOWN);
      } else {
        phoneView.setText(line1Number);
      }
    }
  }

  /** Set device screen brightness with daemon app default value. */
  public void setScreenBrightness(Window activityWindow) {
    LayoutParams layoutParams = activityWindow.getAttributes();
    layoutParams.screenBrightness = SCREEN_BRIGHTNESS;
    activityWindow.setAttributes(layoutParams);
  }

  private boolean isStringNullOrEmpty(@Nullable String str) {
    // String.isEmpty() is not supported on 2.2.1(API level 8) device.
    return str == null || str.length() == 0;
  }
}
