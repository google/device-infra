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

import android.content.Intent;
import android.util.Log;
import android.view.WindowManager.LayoutParams;
import com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.shared.DaemonBaseActivity;
import com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.shared.ResId;

/**
 * Mobile Harness daemon main activity. Once this activity is launched, will start the {@link
 * DaemonService}.
 */
public class DaemonActivity extends DaemonBaseActivity {
  private static final String TAG = DaemonActivity.class.getCanonicalName();

  @Override
  public void startDaemonService() {
    Intent myIntent = DaemonService.newIntent(this);
    startService(myIntent);
  }

  @Override
  public ResId getResIdImpl() {
    return new ResIdImpl();
  }

  @Override
  public void dismissKeyguard() {
    getWindow().addFlags(LayoutParams.FLAG_DISMISS_KEYGUARD | LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    Log.d(TAG, "Dismiss keyguard when MobileHarness Daemon is running");
  }

  @Override
  public void onStop() {
    // Release any resource in this function which can be guaranteed to run.
    clearDismissKeyguardFlag();
    super.onStop();
  }

  private void clearDismissKeyguardFlag() {
    getWindow().clearFlags(LayoutParams.FLAG_DISMISS_KEYGUARD | LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    Log.d(TAG, "Clear keyguard flag for MobileHarness Daemon");
  }
}
