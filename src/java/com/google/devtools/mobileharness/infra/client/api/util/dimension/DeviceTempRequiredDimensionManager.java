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

package com.google.devtools.mobileharness.infra.client.api.util.dimension;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Manager for managing temp required dimensions of devices. */
@Singleton
public class DeviceTempRequiredDimensionManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration PERIODIC_CLEANUP_INTERVAL = Duration.ofHours(1L);

  /** Listener for state changes. Note that the listener is called asynchronously. */
  public interface Listener {
    void onDeviceTempRequiredDimensionChanged(DeviceKey deviceKey);
  }

  /** Key of a device. */
  public record DeviceKey(String labHostName, String deviceUuid) {

    @Override
    public String toString() {
      return String.format("host_name=%s, uuid=%s", labHostName, deviceUuid);
    }
  }

  /** Data record for device temp required dimensions. */
  public record DeviceTempRequiredDimensions(
      ImmutableListMultimap<String, String> dimensions, Instant expireTime) {

    @Override
    public String toString() {
      return String.format("dimensions=%s, expire_time=%s", dimensions, expireTime);
    }
  }

  private final ListeningExecutorService threadPool;
  private final ListeningScheduledExecutorService scheduledThreadPool;
  private final InstantSource instantSource;
  @Nullable private volatile Listener listener;

  private final ConcurrentHashMap<DeviceKey, DeviceTempRequiredDimensions> allDimensions =
      new ConcurrentHashMap<>();

  @Inject
  DeviceTempRequiredDimensionManager(
      ListeningExecutorService threadPool,
      ListeningScheduledExecutorService scheduledThreadPool,
      InstantSource instantSource) {
    this.threadPool = threadPool;
    this.scheduledThreadPool = scheduledThreadPool;
    this.instantSource = instantSource;
  }

  public void setListener(@Nullable Listener listener) {
    this.listener = listener;
  }

  public void start() {
    logger.atInfo().log("Starting DeviceTempRequiredDimensionManager");

    // Starts periodic cleanup task (as a safety net).
    logFailure(
        scheduledThreadPool.scheduleWithFixedDelay(
            threadRenaming(
                this::cleanUpDimensions,
                () -> "device-temp-required-dimension-manager-periodic-cleaner"),
            PERIODIC_CLEANUP_INTERVAL,
            PERIODIC_CLEANUP_INTERVAL),
        Level.WARNING,
        "Error when cleaning up device temp required dimensions");
  }

  /**
   * Adds temp required dimensions for a device.
   *
   * @param duration must be positive
   */
  public void addDimensions(
      DeviceKey deviceKey, ImmutableListMultimap<String, String> dimensions, Duration duration) {
    checkArgument(!duration.isZero() && !duration.isNegative(), "Duration must be positive");

    DeviceTempRequiredDimensions newDimensions =
        new DeviceTempRequiredDimensions(dimensions, instantSource.instant().plus(duration));

    DeviceTempRequiredDimensions oldDimensions = allDimensions.put(deviceKey, newDimensions);
    if (oldDimensions != null) {
      logger.atInfo().log(
          "Updated temp required dimensions for device %s: from [%s] to [%s]",
          deviceKey, oldDimensions, newDimensions);
    } else {
      logger.atInfo().log(
          "Added temp required dimensions for device %s: %s", deviceKey, newDimensions);
    }

    // Notifies listener asynchronously.
    notifyListener(deviceKey, oldDimensions != null ? "update" : "add");

    // Schedules specific cleanup task.
    logFailure(
        scheduledThreadPool.schedule(
            threadRenaming(
                new DeviceTempRequiredDimensionsRemover(deviceKey, newDimensions),
                () -> "device-temp-required-dimension-manager-dimensions-remover"),
            duration),
        Level.WARNING,
        "Error when removing temp required dimensions for device %s: %s",
        deviceKey,
        newDimensions);
  }

  /** Gets temp required dimensions for a device. */
  public Optional<DeviceTempRequiredDimensions> getDimensions(DeviceKey deviceKey) {
    return Optional.ofNullable(allDimensions.get(deviceKey))
        .filter(dimensions -> !instantSource.instant().isAfter(dimensions.expireTime()));
  }

  /** Removes temp required dimensions for a device. */
  public void removeDimensions(DeviceKey deviceKey) {
    DeviceTempRequiredDimensions removedDimensions = allDimensions.remove(deviceKey);
    if (removedDimensions != null) {
      logger.atInfo().log(
          "Removed temp required dimensions for device %s: %s", deviceKey, removedDimensions);
      notifyListener(deviceKey, "remove");
    }
  }

  private class DeviceTempRequiredDimensionsRemover implements Runnable {

    private final DeviceKey deviceKey;
    private final DeviceTempRequiredDimensions expectedDimensions;

    private DeviceTempRequiredDimensionsRemover(
        DeviceKey deviceKey, DeviceTempRequiredDimensions expectedDimensions) {
      this.deviceKey = deviceKey;
      this.expectedDimensions = expectedDimensions;
    }

    @Override
    public void run() {
      // Removes only when the dimensions are equal.
      boolean removed = allDimensions.remove(deviceKey, expectedDimensions);
      if (removed) {
        logger.atInfo().log(
            "Removed expired temp required dimensions for device %s: %s",
            deviceKey, expectedDimensions);
        notifyListener(deviceKey, "expire");
      }
    }
  }

  private void cleanUpDimensions() {
    logger.atInfo().log("Cleaning up device temp required dimensions");
    Instant now = instantSource.instant();
    Map<DeviceKey, DeviceTempRequiredDimensions> removed = new HashMap<>();

    allDimensions.forEach(
        (deviceKey, tempDimensions) -> {
          if (now.isAfter(tempDimensions.expireTime())) {
            if (allDimensions.remove(deviceKey, tempDimensions)) {
              removed.put(deviceKey, tempDimensions);
            }
          }
        });

    removed.forEach(
        (deviceKey, tempDimensions) -> {
          logger.atInfo().log(
              "Removed expired temp required dimensions (periodic) for device %s: %s",
              deviceKey, tempDimensions);
          notifyListener(deviceKey, "periodic-expire");
        });
  }

  private void notifyListener(DeviceKey deviceKey, String reason) {
    Listener listener = this.listener;
    if (listener != null) {
      logFailure(
          threadPool.submit(
              threadRenaming(
                  () -> listener.onDeviceTempRequiredDimensionChanged(deviceKey),
                  () -> "device-temp-required-dimension-notifier-" + reason)),
          Level.WARNING,
          "Error when notifying listener for device %s (%s)",
          deviceKey,
          reason);
    }
  }
}
