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

package com.google.devtools.mobileharness.platform.android.overtcp;


import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A cache of over-TCP device Ids. Use method {#link #UpdateOverTcpIds} to update the cache. It will
 * connect/disconnect devices according to the difference between new Ips and current one.
 */
public class OverTcpDeviceCache {

  /** Cached over-TCP device IDs. */
  @VisibleForTesting Set<String> overTcpIds = new HashSet<>();

  /** Utilities for device connection and disconnection. */
  private final AndroidAdbInternalUtil androidAdbInternalUtil;

  private final FluentLogger logger;

  public OverTcpDeviceCache(AndroidAdbInternalUtil androidAdbInternalUtil, FluentLogger logger) {
    this.androidAdbInternalUtil = androidAdbInternalUtil;
    this.logger = logger;
  }

  /** Gets cached over-TCP device ids. */
  public Set<String> get() {
    return ImmutableSet.copyOf(overTcpIds);
  }

  /**
   * Finds the connected ID for a given ID.
   *
   * <p>After connecting to "192.168.10.20", the connectedIds may look like "192.168.10.20" or
   * "192.168.10.20:5555".
   */
  private Optional<String> findConnectedId(Set<String> connectedIds, String id) {
    for (String connectedId : connectedIds) {
      if (connectedId.contentEquals(id) || connectedId.startsWith(id.concat(":"))) {
        return Optional.of(connectedId);
      }
    }
    return Optional.empty();
  }

  /**
   * Updates cached over-TCP ids with {@code newOverTcpIds}. Connects/Disconnects the devices which
   * needs to be connected/Disconnected given the already {@code connectedIds}. Any connection
   * change will be synchronized to {@code connectedIds} automatically.
   *
   * @param newOverTcpIds IDs of new over-TCP devices
   * @param connectedIds IDs of all connected devices
   * @param cachedDeviceIds IDs of cached over-TCP devices
   * @return Updated {@code connectedIds}
   */
  @CanIgnoreReturnValue
  public Set<String> update(
      Set<String> newOverTcpIds, Set<String> connectedIds, Set<String> cachedDeviceIds)
      throws InterruptedException {
    if (newOverTcpIds != null) {
      // Disconnect all removed devices.
      for (String id : overTcpIds) {
        if (!newOverTcpIds.contains(id)) {
          Optional<String> connectedIdToRemove = findConnectedId(connectedIds, id);
          if (connectedIdToRemove.isPresent()) {
            if (tryDisconnect(id)) {
              connectedIds.remove(connectedIdToRemove.get());
            }
          }
        }
      }
      overTcpIds = newOverTcpIds;
    }

    Set<String> reconnectIds = disconnectOfflineDevices(overTcpIds, cachedDeviceIds);
    for (String id : overTcpIds) {
      if ((reconnectIds.contains(id) || findConnectedId(connectedIds, id).isEmpty())
          && tryConnect(id)) {
        connectedIds.add(id);
      }
    }
    return connectedIds;
  }

  /**
   * Disconnects and returns the set of devices from deviceIds which were offline.
   *
   * <p>Offline devices may still be reported as "connected" to this cache but will be. Over-TCP
   * connected devices can become offline due to different random reasons, and adb won't drop it
   * until explicitly asked. These devices will be in a state that prevents usage. Disconnecting and
   * then reconnecting such devices can potentially bring them back to a usable state.
   *
   * @param deviceIds The set of device IDs to check for offline status.
   * @param cachedDevices The set of device IDs that should not be disconnected, even if they are
   *     offline, because they are in the cache.
   */
  private Set<String> disconnectOfflineDevices(Set<String> deviceIds, Set<String> cachedDevices)
      throws InterruptedException {
    Set<String> reconnectDevices = new HashSet<>();
    Set<String> offlineDevices;
    try {
      offlineDevices = androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.OFFLINE);
    } catch (MobileHarnessException e) {
      // If we failed to get offline devices, don't disconnect anything.
      logger.atWarning().withCause(e).log("Exception happened getting offline devices.");
      return reconnectDevices;
    }
    for (String id : deviceIds) {
      if (offlineDevices.contains(id) && !cachedDevices.contains(id)) {
        logger.atInfo().log("Disconnecting from OFFLINE over-TCP device: %s", id);
        try {
          androidAdbInternalUtil.disconnect(id);
          reconnectDevices.add(id);
        } catch (MobileHarnessException e) {
          // Exception can happen if device is not available.
          // Ignore it so that other devices are not blocked.
          logger.atWarning().withCause(e).log(
              "%s",
              String.format("Exception happened while disconnecting from %s, ignoring it.", id));
        }
      }
    }
    return reconnectDevices;
  }

  /** Returns true if connects to {@code id} successfully. */
  private boolean tryConnect(String id) throws InterruptedException {
    try {
      androidAdbInternalUtil.connect(id);
      logger.atInfo().log("Connected to device : %s", id);
      return true;
    } catch (MobileHarnessException e) {
      logger.atWarning().log("Failed to connect to device %s: %s", id, e.getMessage());
    }
    return false;
  }

  /** Returns true if disconnects from {@code id} successfully. */
  private boolean tryDisconnect(String id) throws InterruptedException {
    try {
      androidAdbInternalUtil.disconnect(id);
      logger.atInfo().log("Disconnected from device : %s", id);
      return true;
    } catch (MobileHarnessException e) {
      logger.atWarning().log("Failed to disconnect from device %s: %s", id, e.getMessage());
    }
    return false;
  }
}
