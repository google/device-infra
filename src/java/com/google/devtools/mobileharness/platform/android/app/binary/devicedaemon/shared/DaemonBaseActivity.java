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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Mobile Harness device daemon base activity. */
public abstract class DaemonBaseActivity extends Activity {
  private static final String TAG = DaemonBaseActivity.class.getCanonicalName();

  /** Broadcast receiver for WIFI connection change. */
  protected volatile BroadcastReceiver wifiChangeReceiver;

  protected volatile PhoneStateListener phoneStateListener;

  protected final DaemonUtil daemonUtil = new DaemonUtil();

  protected ResId resId;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    resId = getResIdImpl();
    setContentView(resId.getMainLayout());

    showAllDeviceInfo();

    setFinishButtonOnClick();

    daemonUtil.setScreenBrightness(getWindow());

    // Dismiss Keyguard for daemon activity.
    if (!Build.BOARD.equals("hawk")) {
      dismissKeyguard();
    }

    // Starts Daemon service.
    startDaemonService();
  }

  @Override
  protected void onDestroy() {
    if (wifiChangeReceiver != null) {
      unregisterReceiver(wifiChangeReceiver);
    }
    if (phoneStateListener != null) {
      TelephonyManager telMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
      if (telMgr != null) {
        telMgr.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
      }
    }
    super.onDestroy();
  }

  protected void showAllDeviceInfo() {
    daemonUtil.updateDeviceId(
        (TextView) findViewById(resId.getDeviceId()),
        (TextView) findViewById(resId.getMirroredDeviceId()),
        getIntent().getStringExtra("id"));
    daemonUtil.updateDeviceModelVersionBuildInfo((TextView) findViewById(resId.getBuildInfo()));
    daemonUtil.updateHostInfo(
        (TextView) findViewById(resId.getHostInfo()), getIntent().getStringExtra("hostname"));
    daemonUtil.updateLabelsInfo(
        (TextView) findViewById(resId.getCustomLabels()), getIntent().getStringExtra("labels"));
    daemonUtil.updateOwnerInfo(
        (TextView) findViewById(resId.getOwners()), getIntent().getStringExtra("owners"));
    showWifiInfo();
    showPhoneInfo();
    registerWifiChangeReceiver();
    addPhoneStateListener((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE));
  }

  protected void showWifiInfo() {
    daemonUtil.updateWifiInfo(
        (TextView) findViewById(resId.getWifiInfo()),
        (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE),
        getIntent().getStringExtra("ssid"));
  }

  protected void registerWifiChangeReceiver() {
    IntentFilter wifiFilter = new IntentFilter();
    wifiFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
    wifiFilter.addAction("android.net.wifi.STATE_CHANGE");
    wifiChangeReceiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            showWifiInfo();
          }
        };
    registerReceiver(wifiChangeReceiver, wifiFilter);
  }

  protected void showPhoneInfo() {
    daemonUtil.updatePhoneInfo(
        (TextView) findViewById(resId.getPhoneInfo()),
        (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE));
  }

  protected void addPhoneStateListener(TelephonyManager telMgr) {
    if (telMgr != null) {
      phoneStateListener =
          new PhoneStateListener() {
            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
              showPhoneInfo();
            }
          };
      telMgr.listen(phoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
    }
  }

  protected void setFinishButtonOnClick() {
    Button finishButton = (Button) findViewById(resId.getFinishButton());
    if (finishButton != null) {
      finishButton.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              DaemonBaseActivity.this.finish();
            }
          });
    }
  }

  /** Get {@link ResId} implementation. */
  protected abstract ResId getResIdImpl();

  /**
   * Dismiss Keyguard. Different versions of Daemon may have their own implementation due to
   * different target SDK.
   */
  protected abstract void dismissKeyguard();

  /** Start Daemon service. */
  protected abstract void startDaemonService();
}
