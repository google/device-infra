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

package com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v1;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import androidx.core.app.NotificationCompat;

/** Mobile Harness daemon service for keeping device unlocked and awaked. */
@SuppressWarnings("deprecation")
public class DaemonService extends Service {
  private static final String TAG = "MobileHarnessDaemonV1";

  private static final int NOTIF_ID_CREATE = 101;
  private static final int NOTIF_ID_DESTROY = 102;
  private WakeLock mWakeLock;

  public static Intent newIntent(Context context) {
    Intent intent = new Intent(context, DaemonService.class);
    return intent;
  }

  @Override
  public IBinder onBind(Intent arg0) {
    return null;
  }

  @Override
  @SuppressWarnings("InvalidWakeLockTag")
  public void onCreate() {
    super.onCreate();
    String msg;

    // Keeps screen awake.
    PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
    mWakeLock =
        powerManager.newWakeLock(
            PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
    mWakeLock.acquire();
    msg = "Screen kept awake!";

    Log.d(TAG, msg);
    startForeground(NOTIF_ID_CREATE, createNotification(msg));
  }

  @Override
  public void onDestroy() {
    // Releases wake lock.
    if (null != mWakeLock) {
      mWakeLock.release();
    }

    String msg = "Mobile Harness daemon service down!";
    Log.d(TAG, msg);
    stopForeground(true /* removeNotification */);
    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
        .notify(NOTIF_ID_DESTROY, createNotification(msg));
    super.onDestroy();
  }

  /** Create a notification given a piece of message. */
  @SuppressWarnings("UnspecifiedImmutableFlag")
  private Notification createNotification(String message) {
    PendingIntent pendingIntent =
        PendingIntent.getActivity(this, 0, newIntent(this), PendingIntent.FLAG_ONE_SHOT);

    Notification notification =
        new NotificationCompat.Builder(this)
            .setContentIntent(pendingIntent)
            .setContentTitle(TAG)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher)
            .setWhen(System.currentTimeMillis())
            .build();
    return notification;
  }
}
