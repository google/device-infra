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

package com.google.devtools.mobileharness.infra.controller.device;

import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatusWithTimestamp;
import java.util.concurrent.atomic.AtomicInteger;

/** Runner for test and device lifecycle on device. */
public abstract class LocalDeviceRunner implements Runnable, LocalDeviceTestRunner {

  /** Total instance number of this class. */
  protected static AtomicInteger runnerCount = new AtomicInteger(0);

  /** Returns the thread number of the instances of this class. */
  public static int getRunnerCount() {
    return runnerCount.get();
  }

  /** Returns whether this device is available for running new test. */
  public abstract boolean isAvailable();

  /** Returns whether this device is ready. */
  public abstract boolean isReady();

  /** Returns whether this device is preparing. */
  public abstract boolean isPrepping();

  /** Returns whether the current thread is stopped. */
  public abstract boolean isStopped();

  /** Returns whether the current runner thread is canceled. */
  public abstract boolean isCancelled();

  /** Returns whether the device is in tear down process. */
  public abstract boolean isTearingDown();

  /** Tries to stop the current device runner. It will be stopped as quickly as possible. */
  public abstract void cancel();

  /** Gets status of a device. Will also record the timestamp of the status. */
  public abstract DeviceStatusWithTimestamp getDeviceStatusWithTimestamp();

  /** Updates the device status and billing statistic. */
  public abstract void updateStatusStat();
}
