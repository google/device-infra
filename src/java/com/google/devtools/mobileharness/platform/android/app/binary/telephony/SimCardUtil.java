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

package android.telephony.utility;

import static org.junit.Assert.assertNotNull;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.se.omapi.Reader;
import android.se.omapi.SEService;
import android.se.omapi.SEService.OnConnectedListener;
import android.telephony.TelephonyManager;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumentation test that allows to get some telephony states.
 *
 * <p>If TelephonyManager does not exist or is not supported, a test failure will be reported
 */
@RunWith(AndroidJUnit4.class)
public class SimCardUtil {

  private static final String SIM_STATE = "sim_state";
  private static final String CARRIER_PRIVILEGES = "has_carried_privileges";
  private static final String SECURED_ELEMENT = "has_secured_element";
  private static final String SE_SERVICE = "has_se_service";

  private static final long SERVICE_CONNECTION_TIME_OUT = 3000;

  private SEService mSeService;
  private Object mServiceMutex = new Object();
  private Timer mConnectionTimer;
  private ServiceConnectionTimerTask mTimerTask = new ServiceConnectionTimerTask();
  private boolean mConnected = false;
  private final OnConnectedListener mListener =
      new OnConnectedListener() {
        @Override
        public void onConnected() {
          synchronized (mServiceMutex) {
            mConnected = true;
            mServiceMutex.notify();
          }
        }
      };

  @Before
  public void setUp() throws Exception {
    mSeService =
        new SEService(InstrumentationRegistry.getContext(), new SynchronousExecutor(), mListener);
    mConnectionTimer = new Timer();
    mConnectionTimer.schedule(mTimerTask, SERVICE_CONNECTION_TIME_OUT);
  }

  @Test
  public void getSimCardInformation() throws Exception {
    // Context of the app under test.
    Context context = InstrumentationRegistry.getTargetContext();
    Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    assertNotNull(tm);

    Bundle returnBundle = new Bundle();
    // Sim card - SIM_STATE_READY 5
    int state = tm.getSimState();
    returnBundle.putInt(SIM_STATE, state);

    // UICC check
    boolean carrierPrivileges = tm.hasCarrierPrivileges();
    returnBundle.putBoolean(CARRIER_PRIVILEGES, carrierPrivileges);

    // Secured element check
    if (waitForConnection()) {
      Reader[] readers = mSeService.getReaders();
      for (Reader reader : readers) {
        returnBundle.putBoolean(SECURED_ELEMENT, reader.isSecureElementPresent());
        returnBundle.putBoolean(SE_SERVICE, reader.getSEService() != null ? true : false);
      }
    } else {
      returnBundle.putBoolean(SECURED_ELEMENT, false);
      returnBundle.putBoolean(SE_SERVICE, false);
    }
    SendToInstrumentation.sendBundle(instrumentation, returnBundle);
  }

  @After
  public void tearDown() throws Exception {
    if (mSeService != null && mSeService.isConnected()) {
      mSeService.shutdown();
      mConnected = false;
    }
  }

  private boolean waitForConnection() {
    synchronized (mServiceMutex) {
      if (!mConnected) {
        try {
          mServiceMutex.wait();
        } catch (InterruptedException e) {
          return false;
        }
      }
      if (!mConnected) {
        return false;
      }
      if (mConnectionTimer != null) {
        mConnectionTimer.cancel();
      }
      return true;
    }
  }

  private class SynchronousExecutor implements Executor {
    @Override
    public void execute(Runnable r) {
      r.run();
    }
  }

  private class ServiceConnectionTimerTask extends TimerTask {
    @Override
    public void run() {
      synchronized (mServiceMutex) {
        mServiceMutex.notifyAll();
      }
    }
  }
}
