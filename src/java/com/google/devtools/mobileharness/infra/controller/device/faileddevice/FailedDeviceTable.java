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

package com.google.devtools.mobileharness.infra.controller.device.faileddevice;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Table to record the control ID of all FailedDevices. This table will be the source of truth for
 * which devices should be FailedDevcies.
 */
public class FailedDeviceTable {
  // ENTRY_EXPIRATION_TIME defines the period beyond which previous init failures are forgotten.
  static final Duration ENTRY_EXPIRATION_TIME = Duration.ofMinutes(10);
  // TIME_TO_STAY_FAILED defines how long a device stays as a FailedDevice.
  static final Duration TIME_TO_STAY_FAILED = Duration.ofMinutes(20);
  // CLEANUP_INTERVAL defines how long expired entries are cleared.
  static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(15);

  private static final FailedDeviceTable INSTANCE = new FailedDeviceTable();

  private final int maxInitFailuresBeforeFail =
      Flags.instance().maxInitFailuresBeforeFail.getNonNull();

  /** Returns the singleton. */
  public static FailedDeviceTable getInstance() {
    return INSTANCE;
  }

  @AutoValue
  abstract static class FailedDeviceEntryInfo {
    abstract Instant lastFailedTime();

    abstract int failedCount();

    @VisibleForTesting
    static FailedDeviceEntryInfo of(Instant lastFailedTime, int failedCount) {
      return new AutoValue_FailedDeviceTable_FailedDeviceEntryInfo(lastFailedTime, failedCount);
    }
  }

  private final Object lock = new Object();

  /** FailedDevices control IDs -> Infos. */
  @GuardedBy("lock")
  @VisibleForTesting
  final Map<String, FailedDeviceEntryInfo> failedDevices = new HashMap<>();

  private final ListeningScheduledExecutorService threadPool;

  @GuardedBy("lock")
  private ListenableFuture<?> cleanUpTaskFuture;

  private FailedDeviceTable() {
    this(
        MoreExecutors.listeningDecorator(
            Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat("failed-device-table-%d").build())));
  }

  @VisibleForTesting
  FailedDeviceTable(ListeningScheduledExecutorService threadPool) {
    this.threadPool = threadPool;
  }

  /**
   * FailedDevices perish after some time to retry initialization, cleanUp() removes the expired
   * entries. cleanUp() only frees the memory. FailedDeviceEntryInfo's validation should not depend
   * on when entries are cleared.
   */
  @VisibleForTesting
  void cleanUp() {
    synchronized (lock) {
      for (Iterator<Map.Entry<String, FailedDeviceEntryInfo>> iterator =
              failedDevices.entrySet().iterator();
          iterator.hasNext(); ) {
        Map.Entry<String, FailedDeviceEntryInfo> entry = iterator.next();
        if (isExpired(entry.getValue())) {
          iterator.remove();
        }
      }
    }
  }

  public ImmutableSet<String> getFailedDeviceIds() {
    cleanUp();
    synchronized (lock) {
      if (failedDevices.keySet().isEmpty()) {
        return ImmutableSet.of();
      }
      return failedDevices.entrySet().stream()
          .filter(entry -> entry.getValue().failedCount() >= maxInitFailuresBeforeFail)
          .filter(
              entry ->
                  entry
                      .getValue()
                      .lastFailedTime()
                      .plus(TIME_TO_STAY_FAILED)
                      .isAfter(Clock.systemUTC().instant()))
          .map(Map.Entry::getKey)
          .collect(toImmutableSet());
    }
  }

  public boolean has(String deviceControlId) {
    return getFailedDeviceIds().contains(deviceControlId);
  }

  // Exclude the expired entries.
  private boolean isExpired(FailedDeviceEntryInfo entry) {
    if (entry.failedCount() >= maxInitFailuresBeforeFail) {
      // If it has been a FailedDevice, use TIME_TO_STAY_FAILED as time limit.
      if (entry.lastFailedTime().plus(TIME_TO_STAY_FAILED).isBefore(Clock.systemUTC().instant())) {
        return true;
      }
    } else {
      if (entry
          .lastFailedTime()
          .plus(ENTRY_EXPIRATION_TIME)
          .isBefore(Clock.systemUTC().instant())) {
        return true;
      }
    }
    return false;
  }

  public void add(String controlId) {
    checkNotNull(controlId);
    synchronized (lock) {
      if (failedDevices.containsKey(controlId) && !isExpired(failedDevices.get(controlId))) {
        FailedDeviceEntryInfo newEntry =
            FailedDeviceEntryInfo.of(
                Clock.systemUTC().instant(), failedDevices.get(controlId).failedCount() + 1);
        failedDevices.replace(controlId, newEntry);
      } else {
        failedDevices.put(controlId, FailedDeviceEntryInfo.of(Clock.systemUTC().instant(), 1));
      }
      if (cleanUpTaskFuture != null) {
        return;
      }
      cleanUpTaskFuture =
          threadPool.scheduleWithFixedDelay(
              this::cleanUp,
              CLEANUP_INTERVAL.toMillis(),
              CLEANUP_INTERVAL.toMillis(),
              MILLISECONDS);
      logFailure(cleanUpTaskFuture, Level.WARNING, "FailedDeviceTable cleanUp task has stopped!");
    }
  }

  public void remove(String controlId) {
    synchronized (lock) {
      failedDevices.remove(controlId);
    }
  }
}
