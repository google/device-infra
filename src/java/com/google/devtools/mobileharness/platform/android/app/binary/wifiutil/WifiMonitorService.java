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
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.google.devtools.mobileharness.platform.android.app.binary.wifiutil.WifiConnector.WifiException;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/**
 * A class to monitor wifi connectivity for a period of time.
 *
 * <p>Once it is started, this will send a HTTP request to the specified URL every interval and
 * record latencies. The latency history can be retrieved by {@link
 * WifiMonitorService#getData(Context)}. This class is used to support "startMonitor" and
 * "stopMonitor" commands.
 *
 * <p>Additionally, tests can reconnect wifi during test runs to ensure connectivity by sending
 * <code>com.google.devtools.mobileharness.platform.android.app.binary.wifiutil.RECONNECT</code>
 * action. Once the reconnection is done, it will send <code>
 * com.google.devtools.mobileharness.platform.android.app.binary.wifiutil.RECONNECT_COMPLETE</code>
 * broadcast with result.
 */
@SuppressLint("DefaultLocale")
public class WifiMonitorService extends IntentService {

  private static final String TAG = "WifiUtil." + WifiMonitorService.class.getSimpleName();

  public static final String PACKAGE_NAME =
      "com.google.devtools.mobileharness.platform.android.app.binary.wifiutil";
  public static final String ACTION_RECONNECT = PACKAGE_NAME + ".RECONNECT";
  public static final String ACTION_RECONNECT_COMPLETE = PACKAGE_NAME + ".RECONNECT_COMPLETE";
  public static final String EXTRA_URL_TO_CHECK = "urlToCheck";

  private static final String DEFAULT_URL_TO_CHECK = "http://www.google.com";
  private static final String DATA_FILE = "monitor.dat";
  private static final long MAX_DATA_FILE_SIZE = 1024 * 1024;

  /** Constructor. */
  public WifiMonitorService() {
    super(WifiMonitorService.class.getSimpleName());
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    final String action = intent.getAction();
    if (action == null) {
      monitor(intent);
    } else if (action.equals(ACTION_RECONNECT)) {
      reconnect(intent);
    }
  }

  private static String getStringExtra(Intent intent, String name, String defValue) {
    if (intent.hasExtra(name)) {
      return intent.getStringExtra(name);
    }
    return defValue;
  }

  private void monitor(Intent intent) {
    FileOutputStream out = null;
    try {
      final File file = getFileStreamPath(DATA_FILE);
      if (MAX_DATA_FILE_SIZE < file.length()) {
        Log.d(TAG, "data file is too big. clearing...");
        clearData(this);
      }

      final String urlToCheck = getStringExtra(intent, EXTRA_URL_TO_CHECK, DEFAULT_URL_TO_CHECK);
      out = openFileOutput(DATA_FILE, Context.MODE_APPEND);
      final PrintWriter writer = new PrintWriter(out);
      writer.write(String.format("%d,", checkLatency(urlToCheck)));
      writer.flush();
    } catch (final Exception e) {
      // Catching exceptions to prevent crash and minimize impacts on running tests.
      Log.e(TAG, "failed to monitor network latency", e);
    } finally {
      closeSilently(out);
    }
  }

  private void reconnect(Intent intent) {

    boolean result = false;
    try {
      final String urlToCheck = getStringExtra(intent, EXTRA_URL_TO_CHECK, DEFAULT_URL_TO_CHECK);
      Log.d(TAG, "checking network connection with " + urlToCheck);
      long latency = checkLatency(urlToCheck);
      if (latency < 0) {
        Log.d(TAG, "network connection is bad. reconnecting wifi...");
        WifiConnector connector = new WifiConnector(this);
        connector.reconnectToLastNetwork(urlToCheck);
      }
      result = true;
      Log.d(TAG, "network connection is good.");
    } catch (final WifiException e) {
      Log.e(TAG, "failed to reconnect", e);
    } catch (final Exception e) {
      Log.e(TAG, "failed to check network connection", e);
    }

    Intent broadcastIntent = new Intent(ACTION_RECONNECT_COMPLETE);
    broadcastIntent.putExtra("result", result);
    sendBroadcast(broadcastIntent);
  }

  /**
   * Checks network latency to the given URL.
   *
   * @param urlToCheck a URL to check
   * @return latency of a HTTP request to the URL.
   */
  private static long checkLatency(final String urlToCheck) {
    final long startTime = System.currentTimeMillis();
    if (!WifiConnector.checkConnectivity(urlToCheck)) {
      return -1;
    }
    return System.currentTimeMillis() - startTime;
  }

  /**
   * Clears the latency history data.
   *
   * @param context a {@link Context} object.
   */
  private static void clearData(final Context context) {
    context.deleteFile(DATA_FILE);

    FileOutputStream out = null;
    try {
      out = context.openFileOutput(DATA_FILE, 0);
    } catch (final Exception e) {
      Log.e(TAG, e.toString());
    } finally {
      closeSilently(out);
    }
  }

  /**
   * Enables network monitoring. This will also clear the latency history data.
   *
   * @param context a {@link Context} object.
   * @param interval an interval of connectivity checks in milliseconds.
   * @param urlToCheck a URL to check.
   */
  public static void enable(final Context context, final long interval, final String urlToCheck) {
    if (interval <= 0 || urlToCheck == null) {
      throw new IllegalArgumentException();
    }

    // Clear the data file.
    clearData(context);

    final Intent intent = new Intent(context, WifiMonitorService.class);
    intent.putExtra(EXTRA_URL_TO_CHECK, urlToCheck);
    final PendingIntent operation =
        PendingIntent.getService(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    final AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarm.setRepeating(AlarmManager.ELAPSED_REALTIME, interval, interval, operation);
  }

  /**
   * Disables network monitoring.
   *
   * @param context a {@link Context} object.
   */
  public static void disable(final Context context) {
    final Intent intent = new Intent(context, WifiMonitorService.class);
    final PendingIntent operation =
        PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    final AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarm.cancel(operation);
  }

  /**
   * Returns the latency history data.
   *
   * @param context a {@link Context} object.
   * @returns a comma-separated list of latency history for the given URL in milliseconds.
   */
  public static String getData(final Context context) {
    String output = "";
    FileInputStream in = null;
    try {
      in = context.openFileInput(DATA_FILE);
      final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
      output = reader.readLine();
    } catch (final IOException e) {
      // swallow
      Log.e(TAG, e.toString());
    } finally {
      closeSilently(in);
    }
    return output;
  }

  private static void closeSilently(Closeable closable) {
    if (closable != null) {
      try {
        closable.close();
      } catch (final IOException e) {
        // swallow
      }
    }
  }
}
