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

package com.google.devtools.mobileharness.infra.lab.controller;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatusWithTimestamp;
import com.google.devtools.mobileharness.infra.controller.device.DeviceStatusInfo;
import com.google.devtools.mobileharness.infra.controller.device.DeviceStatusProvider;
import com.google.devtools.mobileharness.infra.controller.device.DeviceStatusProvider.DeviceWithStatusInfo;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.helper.LabSyncHelper;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.HeartbeatLabResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabResponse;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalDeviceChangeEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalDeviceDownEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalDeviceErrorEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalDeviceUpEvent;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** For syncing the information of the Mobile Harness lab server with the Mobile Harness master. */
public class MasterSyncerForDevice implements Runnable, Observer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Interval of regular synchronization with master server. */
  private static final Duration SYNC_INTERVAL = Duration.ofSeconds(10);

  /** For talking to Master V5 LabSyncService. */
  private final LabSyncHelper labSyncHelper;

  /** Provider where to get the device status. */
  private final DeviceStatusProvider deviceStatusProvider;

  private final AtomicBoolean inDrainingMode = new AtomicBoolean(false);

  public MasterSyncerForDevice(
      DeviceStatusProvider deviceStatusProvider, LabSyncHelper labSyncHelper) {
    this.deviceStatusProvider = deviceStatusProvider;
    this.labSyncHelper = labSyncHelper;
  }

  @Override
  public void run() {
    logger.atInfo().log("Start running");
    firstSyncWithMaster();
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Thread.sleep(SYNC_INTERVAL.toMillis());
        regularSyncWithMaster();
      } catch (InterruptedException e) {
        logger.atWarning().log("Interrupted: %s", e.getMessage());
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        // Catches all exception to make sure the DeviceStatusProvider thread in lab won't be
        // stopped. Otherwise, no device can be detected.
        logger.atSevere().withCause(e).log("FATAL ERROR");
      }
    }
    logger.atSevere().log("Stopped!");
  }

  /** Signs up to master when a new device becomes ready. */
  @Subscribe
  public synchronized void onDeviceUp(LocalDeviceUpEvent event) {
    String deviceControlId = event.getDeviceControlId();
    String deviceType = event.getDeviceType();

    DeviceWithStatusInfo deviceAndStatusInfo =
        deviceStatusProvider.getDeviceAndStatusInfo(deviceControlId, deviceType);
    if (deviceAndStatusInfo == null) {
      logger.atSevere().log(
          "Received event for device %s(%s) which doesn't exist", deviceControlId, deviceType);
      return;
    }

    DeviceStatusWithTimestamp deviceStatusWithTimestamp =
        deviceAndStatusInfo.deviceStatusInfo().getDeviceStatusWithTimestamp();
    DeviceStatusWithTimestamp drainingDeviceStatusWithTimestamp = deviceStatusWithTimestamp;
    // Override newly detected device status if already in draining mode and DeviceStatus == IDLE.
    if (inDrainingMode.get() && deviceStatusWithTimestamp.getStatus() == DeviceStatus.IDLE) {
      drainingDeviceStatusWithTimestamp =
          DeviceStatusWithTimestamp.newBuilder(deviceStatusWithTimestamp)
              .setStatus(DeviceStatus.LAMEDUCK)
              .build();
    }

    try {
      signUpLab(
          ImmutableMap.of(
              deviceAndStatusInfo.device(),
              DeviceStatusInfo.newBuilder()
                  .setDeviceStatusWithTimestamp(drainingDeviceStatusWithTimestamp)
                  .build()));
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to sign up device %s(%s)", deviceControlId, deviceType);
    }
  }

  /** Updates the device to master when the device is changed. */
  @Subscribe
  public void onDeviceChanged(LocalDeviceChangeEvent event) {
    // We sign up the device again when device is changed.
    onDeviceUp(
        new LocalDeviceUpEvent(
            event.getDeviceControlId(), event.getDeviceUuid(), event.getDeviceType()));
  }

  /** Signs out the device when it is down. */
  @Subscribe
  public void onDeviceDown(LocalDeviceDownEvent event) throws InterruptedException {
    String deviceId = event.getDeviceUuid();

    try {
      labSyncHelper.signOutDevice(deviceId);
    } catch (ExecutionException | MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to sign out device %s", deviceId);
    }
  }

  /** Signs out the device when its error info changes. */
  @Subscribe
  public void onDeviceErrorChanged(LocalDeviceErrorEvent event) throws InterruptedException {
    String deviceId = event.getDeviceUuid();

    try {
      ExceptionDetail exceptionDetail = event.getDeviceError();
      DeviceWithStatusInfo deviceAndStatusInfo =
          deviceStatusProvider.getDeviceAndStatusInfo(deviceId);
      DeviceStatusInfo updatedDeviceStatusInfo =
          DeviceStatusInfo.newBuilder()
              .setDeviceStatusWithTimestamp(
                  deviceAndStatusInfo.deviceStatusInfo().getDeviceStatusWithTimestamp())
              .setExceptionDetail(exceptionDetail)
              .build();
      logger.atInfo().log("Device %s has error %s.", deviceId, event.getDeviceError());
      signUpLab(
          ImmutableMap.<Device, DeviceStatusInfo>of(
              deviceAndStatusInfo.device(), updatedDeviceStatusInfo));
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to update error on device %s", deviceId);
    }
  }

  /**
   * Notified when the lab config is changed. Needs to re-sign-up all the devices to update the
   * device config.
   */
  @Override
  public void update(Observable observable, Object arg) {
    logger.atInfo().log(
        "Re-sign-up all devices according to the config update or force update device status upon "
            + "entering lameduck mode.");
    try {
      Map<Device, DeviceStatusInfo> allDeviceStatus =
          deviceStatusProvider.getAllDeviceStatus(/* realtimeDetect= */ true);

      signUpLab(allDeviceStatus);

    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to re-sign-up all devices according to the config update");
    } catch (InterruptedException e) {
      logger.atWarning().log("Interrupted: %s", e.getMessage());
      Thread.currentThread().interrupt();
    }
  }

  /** Synchronizes the device status to master server. */
  @VisibleForTesting
  void regularSyncWithMaster() throws InterruptedException {
    // Change history:
    // 2016: Enables realtime detection to make sure the device is alive, to avoid b/28716975.
    // 2021: The previous solution will block the master sync(b/181512423), so we change realtime
    // detection to dispatch as a tradeoff.
    Map<Device, DeviceStatusInfo> devicesToSync =
        deviceStatusProvider.getAllDeviceStatusWithoutDuplicatedUuid(/* realtimeDispatch= */ true);
    Map<Class<? extends Device>, Multimap<String, String>> devicesToPrint = new HashMap<>();

    for (Entry<Device, DeviceStatusInfo> entry : devicesToSync.entrySet()) {
      Device device = entry.getKey();
      Class<? extends Device> type = device.getClass();
      Multimap<String, String> map =
          devicesToPrint.computeIfAbsent(type, k -> LinkedListMultimap.create());
      map.put(
          entry.getValue().getDeviceStatusWithTimestamp().getStatus().name(),
          entry.getKey().getDeviceId());
    }

    StringBuilder buf = new StringBuilder("Device count: ");
    buf.append(devicesToSync.size());
    if (!devicesToPrint.isEmpty()) {
      buf.append(", status:");
      for (Entry<Class<? extends Device>, Multimap<String, String>> entry :
          devicesToPrint.entrySet()) {
        // Prints the device type and the numbers of the devices of different status.
        buf.append("\n======== ");
        buf.append(entry.getKey().getSimpleName());
        buf.append(" ========");
        for (Entry<String, Collection<String>> map : entry.getValue().asMap().entrySet()) {
          // Prints the status.
          buf.append('\n');
          buf.append(map.getKey());
          buf.append('(');
          buf.append(map.getValue().size());
          buf.append("):");
          if (map.getValue().size() > 3) {
            buf.append("\n");
          }
          // Prints the device id. Up to 3 id in one row.
          int colIdx = 0;
          for (String id : map.getValue()) {
            if (colIdx == 3) {
              buf.append('\n');
              colIdx = 0;
            }
            buf.append('\t');
            buf.append(id);
            colIdx++;
          }
        }
      }
    }
    logger.atInfo().log("%s", buf);

    // Syncs with master.
    HeartbeatLabResponse heartbeatResp = null;
    try {
      heartbeatResp = labSyncHelper.heartbeatLab(devicesToSync);
    } catch (MobileHarnessException e) {
      logger.atWarning().log("%s", e.getMessage());
    }

    if (heartbeatResp != null) {
      Map<Device, DeviceStatusInfo> devicesToSignUp = new HashMap<>();
      if (heartbeatResp.getSignUpAll()) {
        devicesToSignUp.putAll(devicesToSync);
        logger.atInfo().log("Sign up the whole lab");
      } else {
        for (String deviceId : heartbeatResp.getOutdatedDeviceIdList()) {
          DeviceWithStatusInfo deviceAndStatusInfo =
              deviceStatusProvider.getDeviceAndStatusInfo(deviceId);
          if (deviceAndStatusInfo != null) {
            devicesToSignUp.put(
                deviceAndStatusInfo.device(), deviceAndStatusInfo.deviceStatusInfo());
          }
        }
      }
      if (heartbeatResp.getSignUpAll() || !devicesToSignUp.isEmpty()) {
        // If got the sign-up-all signal, always signs up even the lab has no device.
        if (!devicesToSignUp.isEmpty()) {
          StringBuilder log = new StringBuilder("Sign up devices:");
          for (Entry<Device, DeviceStatusInfo> device : devicesToSignUp.entrySet()) {
            log.append(" ")
                .append(device.getKey().getDeviceId())
                .append('(')
                .append(device.getValue().getDeviceStatusWithTimestamp())
                .append(')');
          }
          logger.atInfo().log("%s", log);
        }
        try {
          signUpLab(devicesToSignUp);
        } catch (MobileHarnessException e) {
          logger.atWarning().log("%s", e.getMessage());
        }
      }
    }
  }

  private void firstSyncWithMaster() {
    // The device info may be not ready at this point, so ignored. Just force a sign up of the Lab
    // Server to avoid b/173677899.
    logger.atInfo().log("First time sign up of the lab server");
    try {
      signUpLab(new HashMap<>());
    } catch (MobileHarnessException e) {
      logger.atWarning().log("%s", e.getMessage());
    }
  }

  private void signUpLab(Map<Device, DeviceStatusInfo> deviceDeviceStatusInfo)
      throws MobileHarnessException {
    SignUpLabResponse signUpLabResponse = labSyncHelper.signUpLab(deviceDeviceStatusInfo);
    if (signUpLabResponse.getDuplicatedDeviceUuidCount() > 0) {
      logger.atWarning().log(
          "Found duplicated device with uuids: [%s]",
          Joiner.on(',').join(signUpLabResponse.getDuplicatedDeviceUuidList()));
    }
    signUpLabResponse
        .getDuplicatedDeviceUuidList()
        .forEach(deviceStatusProvider::updateDuplicatedUuid);
  }

  /** Flag to notify MasterSyncer in draining mode. */
  public void enableDrainingMode() {
    inDrainingMode.set(true);
  }
}
