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

package com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;

/** Mobile Harness daemon JobIntentService for keeping device unlocked and awake. */
public class DaemonJobIntentService extends JobIntentService {
  private static final String TAG = "MobileHarnessDaemonV2";

  // No requirement for JOB_ID value, but just ensure it's the same value for all work enqueued for
  // the same class
  private static final int JOB_ID = 1;
  private static final int NOTIF_ID_CREATE = 101;
  private static final String NOTIFICATION_CHANNEL_ID = "mobileharness_daemon_service";
  private static final String NOTIFICATION_CHANNEL_NAME = "MobileHarnessDaemonService";
  private WakeLock mWakeLock;

  public static void start(Context context, Intent intent) {
    enqueueWork(context, intent);
  }

  public static Intent newIntent(Context context) {
    Intent intent = new Intent(context, DaemonJobIntentService.class);
    return intent;
  }

  private static void enqueueWork(Context context, Intent intent) {
    enqueueWork(context, DaemonJobIntentService.class, JOB_ID, intent);
  }

  @Override
  @SuppressWarnings("InvalidWakeLockTag")
  protected void onHandleWork(@NonNull Intent intent) {
    // Keeps screen awake.
    PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
    mWakeLock =
        powerManager.newWakeLock(
            PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
    mWakeLock.acquire();
    String msg = "Screen kept awake!";
    Log.d(TAG, msg);
    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
        .notify(NOTIF_ID_CREATE, createNotification(msg));
  }

  @Override
  public void onDestroy() {
    // Releases wake lock.
    if (null != mWakeLock) {
      mWakeLock.release();
    }
    super.onDestroy();
  }

  /** Create a notification given a piece of message. */
  @SuppressWarnings("UnspecifiedImmutableFlag")
  private Notification createNotification(String message) {
    String channelId = "";
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      channelId = createNotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME);
    }

    PendingIntent pendingIntent =
        PendingIntent.getActivity(this, 0, newIntent(this), PendingIntent.FLAG_ONE_SHOT);

    Notification notification =
        new NotificationCompat.Builder(this, channelId)
            .setContentIntent(pendingIntent)
            .setContentTitle(TAG)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher)
            .setWhen(System.currentTimeMillis())
            .build();
    return notification;
  }

  private String createNotificationChannel(String channelId, String channelName) {
    NotificationChannel channel =
        new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
    channel.setLightColor(Color.GREEN);
    channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
    NotificationManager notificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.createNotificationChannel(channel);
    return channelId;
  }
}
