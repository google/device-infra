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

package com.google.wireless.qa.mobileharness.shared.controller.stat;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.container.annotation.ProcessIncompatible;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.proto.Job.ResultCounter;
import java.time.Clock;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.concurrent.GuardedBy;

/**
 * Statistic data of a device in the lab server.
 *
 * <p>All test related stats are only updated in {@linkplain
 * com.google.devtools.mobileharness.infra.controller.device.LocalDeviceRunner local device runner},
 * so they do not work for {@linkplain
 * com.google.wireless.qa.mobileharness.client.api.mode.local.lite.LocalLiteMode local lite mode}.
 */
@ProcessIncompatible
public class DeviceStat {

  /** Number of the consecutive setup failure num since last success. */
  private final AtomicLong consecutiveSetupFailureTimes = new AtomicLong(0);

  /** Total number of the historical tests ever run on this device. */
  @GuardedBy("this")
  private int totalHistoricalTestNum = 0;

  /** Number of the tests on this device since last reboot. */
  private volatile int testNumSinceLastReboot = 0;

  @GuardedBy("this")
  private final ResultCounter.Builder testResultCounterBuilder =
      ResultCounter.newBuilder()
          .setTotal(0)
          .setUnknown(0)
          .setPassed(0)
          .setFailed(0)
          .setError(0)
          .setTimeout(0)
          .setAbort(0)
          .setInfraError(0)
          .setSkipped(0);

  @GuardedBy("this")
  private int consecutiveFinishedFail;

  /** First time in milliseconds when this device is detected by lab server. */
  private final long firstShowUpTime;

  /** Last time in milliseconds when this device showed up in the lab server. */
  private final AtomicLong lastShowUpTime;

  /** Last time in milliseconds when this device became ready. */
  private final AtomicLong lastReadyTime = new AtomicLong(-1);

  /** Last time in milliseconds when this device rebooted. */
  private final AtomicLong lastRebootTime = new AtomicLong(-1);

  /** Last time in milliseconds when the device died. */
  private final AtomicLong lastDieTime = new AtomicLong(-1);

  /** Latest stable version of the device. */
  private volatile Optional<String> latestVersion;

  /** Mockable clock for getting the current system time. */
  private final Clock clock;

  /**
   * Status counts of the device since first showed up in the lab server. Won't be reset when device
   * is reconnected. Multiplies the counters with {@code
   * LocalDeviceManager#DETECT_DEVICES_INTERVAL_MS} to get the times in milliseconds.
   */
  private final ConcurrentMap<DeviceStatus, AtomicLong> statusCounts =
      new ConcurrentHashMap<>(DeviceStatus.values().length);

  /**
   * Status counts of the device since last time the device is started/connected. Will be reset when
   * device is reconnected. Multiplies the counters with {@code
   * LocalDeviceManager#DETECT_DEVICES_INTERVAL_MS} to get the times in milliseconds.
   */
  private final ConcurrentMap<DeviceStatus, AtomicLong> lastStatusCounts =
      new ConcurrentHashMap<>(DeviceStatus.values().length);

  /** Creates a DeviceStat when the device is first detected. Will update the detected time. */
  public DeviceStat() {
    this(Clock.systemUTC());
  }

  @VisibleForTesting
  DeviceStat(Clock clock) {
    this.clock = clock;
    firstShowUpTime = clock.millis();
    lastShowUpTime = new AtomicLong(firstShowUpTime);
    for (DeviceStatus status : DeviceStatus.values()) {
      if (!status.equals(DeviceStatus.UNRECOGNIZED)) {
        statusCounts.put(status, new AtomicLong(0));
        lastStatusCounts.put(status, new AtomicLong(0));
      }
    }
  }

  public synchronized void addNewTest() {
    ++testNumSinceLastReboot;
  }

  /** Saves a finished test to the test history. */
  public synchronized void addFinishedTest(TestResult testResult) {
    ++totalHistoricalTestNum;
    consecutiveFinishedFail =
        TestResult.PASS.equals(testResult) || TestResult.SKIP.equals(testResult)
            ? 0
            : consecutiveFinishedFail + 1;
    testResultCounterBuilder.setTotal(testResultCounterBuilder.getTotal() + 1);
    switch (testResult) {
      case UNKNOWN:
        testResultCounterBuilder.setUnknown(testResultCounterBuilder.getUnknown() + 1);
        break;
      case PASS:
        testResultCounterBuilder.setPassed(testResultCounterBuilder.getPassed() + 1);
        break;
      case FAIL:
        testResultCounterBuilder.setFailed(testResultCounterBuilder.getFailed() + 1);
        break;
      case ERROR:
        testResultCounterBuilder.setError(testResultCounterBuilder.getError() + 1);
        break;
      case TIMEOUT:
        testResultCounterBuilder.setTimeout(testResultCounterBuilder.getTimeout() + 1);
        break;
      case ABORT:
        testResultCounterBuilder.setAbort(testResultCounterBuilder.getAbort() + 1);
        break;
      case INFRA_ERROR:
        testResultCounterBuilder.setInfraError(testResultCounterBuilder.getInfraError() + 1);
        break;
      case SKIP:
        testResultCounterBuilder.setSkipped(testResultCounterBuilder.getSkipped() + 1);
        break;
      default:
        testResultCounterBuilder.setUnknown(testResultCounterBuilder.getUnknown() + 1);
    }
  }

  /** Returns the historical test statistic of this device. */
  public synchronized TestStat getHistoricalTestStat() {
    return TestStat.create(totalHistoricalTestNum, testResultCounterBuilder.build());
  }

  /**
   * Returns the number of the latest consecutive failed finished tests. 0 if the last test passed.
   */
  public synchronized int getConsecutiveFinishedFail() {
    return consecutiveFinishedFail;
  }

  /** Returns number of the tests on this device since last reboot. */
  public synchronized int getTestNumSinceLastReboot() {
    return testNumSinceLastReboot;
  }

  /** Updates the statistic data when the device shows up (not the first time) in the lab server. */
  public void onShowUp() {
    // Assume setup will fail which will be cleared when ready.
    consecutiveSetupFailureTimes.incrementAndGet();
    lastShowUpTime.set(clock.millis());
    for (AtomicLong count : lastStatusCounts.values()) {
      count.set(0);
    }
  }

  /** Updates the statistic data when the device becomes ready. */
  public void onReady() {
    lastReadyTime.set(clock.millis());
    consecutiveSetupFailureTimes.set(0);
  }

  /** Updates the statistic data when the device rebooted. */
  public void onReboot() {
    lastRebootTime.set(clock.millis());
    testNumSinceLastReboot = 0;
  }

  /** Sets the latest version of the device. */
  @CanIgnoreReturnValue
  public Optional<String> setLatestVersion(String version) {
    Optional<String> preVersion = latestVersion;
    latestVersion = Optional.of(version);
    return preVersion;
  }

  /** Updates the statistic data when the device dies. The device may be rebooted or removed. */
  public void onDie() {
    lastDieTime.set(clock.millis());
  }

  /** Returns number of the setup failures on this device since last success. */
  public long getConsecutiveSetupFailureTimes() {
    return consecutiveSetupFailureTimes.get();
  }

  /** Returns the time in milliseconds when this device is first time detected by the lab server. */
  public long getFirstShowUpTime() {
    return firstShowUpTime;
  }

  /** Returns the time in milliseconds when this device showed up last time. */
  public long getLastShowUpTime() {
    return lastShowUpTime.get();
  }

  /**
   * Returns the time in milliseconds when this device became ready last time. If the device has
   * never become ready before, will return a negative value.
   */
  public long getLastReadyTime() {
    return lastReadyTime.get();
  }

  /**
   * Returns the time in milliseconds when this device rebooted last time. If the device has never
   * rebooted before, will return a negative value.
   */
  public long getLastRebootTime() {
    return lastRebootTime.get();
  }

  /**
   * Returns the time in milliseconds when this device was removed or rebooted last time. If the
   * device has never died before, will return a negative value.
   */
  public long getLastDieTime() {
    return lastDieTime.get();
  }

  /**
   * Increases the status counter of the device. This method will be invoked very frequently, about
   * every {@code LocalDeviceManager#DETECT_DEVICES_INTERVAL_MS}. We don't synchronize these
   * operations of the {@code statusCounts} and {@code lastStatusCounts} to improve performance. The
   * data may not be so precise but enough to provide a overview of the device status.
   */
  public void countStatus(DeviceStatus status) {
    statusCounts.get(status).incrementAndGet();
    lastStatusCounts.get(status).incrementAndGet();
  }

  /**
   * Returns a copy of status counts since the device first showed up in the lab server. Returns
   * type is <DeviceStatus, Long> mapping. Multiplies the counters with {@code
   * LocalDeviceManager#DETECT_DEVICES_INTERVAL_MS} to get the times in milliseconds.
   */
  public Map<DeviceStatus, Long> getStatusCounts() {
    Map<DeviceStatus, Long> result = Maps.newHashMapWithExpectedSize(DeviceStatus.values().length);
    for (Entry<DeviceStatus, AtomicLong> entry : statusCounts.entrySet()) {
      result.put(entry.getKey(), entry.getValue().get());
    }
    return result;
  }

  /**
   * Returns a copy of status counts since last time the device is reconnected/rebooted. Return type
   * is <DeviceStatus, Long> mapping. Multiplies the counters with {@code
   * LocalDeviceManager#DETECT_DEVICES_INTERVAL_MS} to get the times in milliseconds.
   */
  public Map<DeviceStatus, Long> getLastStatusCounts() {
    Map<DeviceStatus, Long> result = Maps.newHashMapWithExpectedSize(DeviceStatus.values().length);
    for (Entry<DeviceStatus, AtomicLong> entry : lastStatusCounts.entrySet()) {
      result.put(entry.getKey(), entry.getValue().get());
    }
    return result;
  }

  /** Returns the latest version of the device. */
  public Optional<String> getLatestVersion() {
    return latestVersion;
  }
}
