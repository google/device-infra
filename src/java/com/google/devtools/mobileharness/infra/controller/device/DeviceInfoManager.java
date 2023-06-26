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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.StackSize;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import com.google.devtools.mobileharness.api.model.lab.DeviceInfo;
import com.google.devtools.mobileharness.api.model.lab.DeviceInfoFactory;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

/**
 * Device info manager for managing Mobile Harness device info.
 *
 * <p>Use {@link #getOrCreate} to get {@link DeviceInfo}. The returned instance may be a remote stub
 * (if the current process is in Mobile Harness container), or fetch data from configuration like
 * {@linkplain com.google.wireless.qa.mobileharness.shared.api.ApiConfig ApiConfig} (if it is in
 * Mobile Harness lab server), etc.
 *
 * <p>All {@link DeviceInfo} instances of a device point to one backend among lab server and
 * containers on one machine.
 */
public class DeviceInfoManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final DeviceInfoManager INSTANCE = new DeviceInfoManager();

  /**
   * @return the singleton
   */
  public static DeviceInfoManager getInstance() {
    return INSTANCE;
  }

  /** All the {@link DeviceInfo}s group by device control id. */
  private final Map<String, DeviceInfo> deviceInfos = new ConcurrentHashMap<>();

  private final SystemUtil systemUtil = new SystemUtil();

  private final ListeningScheduledExecutorService threadPool;

  /** Control ID -> task future for deleting DeviceInfo of the device that the ID is linked with. */
  @VisibleForTesting
  final Map<String, ListenableScheduledFuture<?>> removalFutures = new ConcurrentHashMap<>();

  private DeviceInfoManager() {
    this(
        MoreExecutors.listeningDecorator(
            Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat("device-info-manager-%d").build())));
  }

  @VisibleForTesting
  public DeviceInfoManager(ListeningScheduledExecutorService threadPool) {
    this.threadPool = threadPool;
  }

  @CanIgnoreReturnValue
  public DeviceInfo getOrCreate(String deviceControlId, @Nullable ApiConfig apiConfig) {
    DeviceInfo deviceInfo = deviceInfos.get(deviceControlId);
    if (deviceInfo == null) {
      logger.atSevere().withStackTrace(StackSize.FULL).log(
          "A new DeviceInfo %s is created but can not link to container or device runner,"
              + " which can not sync information with other device infos among processes "
              + "correctly. Mobile Harness prepares Device/DeviceInfo for you, and if you "
              + "want to create new ones manually, please use LiteDeviceInfoFactory instead "
              + "and let your Device class do not inherit from BaseDevice.",
          deviceControlId);
      deviceInfo =
          DeviceInfoFactory.create(DeviceId.of(deviceControlId, "" /* never */), apiConfig);
    }
    return deviceInfo;
  }

  public Optional<DeviceInfo> get(String deviceControlId) {
    return Optional.ofNullable(deviceInfos.get(deviceControlId));
  }

  /** Do <b>not</b> make it public. */
  void add(DeviceId deviceId, @Nullable ApiConfig apiConfig, boolean retainDeviceInfo) {
    deviceInfos.compute(
        deviceId.controlId(),
        (id, oldDeviceInfo) -> {
          ListenableScheduledFuture<?> removalFuture = removalFutures.get(deviceId.controlId());
          if (removalFuture != null) {
            removalFuture.cancel(true /* mayInterruptIfRunning */);
            removalFutures.remove(deviceId.controlId());
          }
          if (oldDeviceInfo == null) {
            return DeviceInfoFactory.create(deviceId, apiConfig);
          } else {
            if (retainDeviceInfo) {
              return oldDeviceInfo;
            }
            logger.atWarning().log("DeviceInfo %s already exists", deviceId.controlId());
            return DeviceInfoFactory.create(deviceId, apiConfig);
          }
        });
  }

  /** Do <b>not</b> make it public. */
  void remove(String deviceControlId) {
    logger.atInfo().log("DeviceInfo removed for : %s", deviceControlId);
    deviceInfos.remove(deviceControlId);
  }

  /**
   * Removes DeviceInfo of specified device after the specified delay. If the DeviceInfo is reused
   * after removal is scheduled, the removal will be cancelled.
   */
  void removeDelayed(String deviceControlId, Duration delay) {
    ListenableScheduledFuture<?> future =
        threadPool.schedule(
            () -> {
              remove(deviceControlId);
              removalFutures.remove(deviceControlId);
            },
            delay.toMillis(),
            MILLISECONDS);
    ListenableScheduledFuture<?> pendingRemovalRequest = removalFutures.get(deviceControlId);
    if (pendingRemovalRequest != null) {
      pendingRemovalRequest.cancel(true);
    }
    removalFutures.put(deviceControlId, future);
  }
}
