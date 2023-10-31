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

package com.google.devtools.deviceinfra.infra.client.api.mode.ats;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceScheduleUnit;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.lab.LabScheduleUnit;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceGrpc;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.HeartbeatLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.HeartbeatLabResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingDeviceRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingDeviceResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingDevicesRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingDevicesResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignOutDeviceRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignOutDeviceResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabResponse;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.checker.ServiceSideVersionChecker;
import com.google.devtools.mobileharness.shared.version.proto.Version.VersionCheckResponse;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.controller.event.AllocationEvent;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class RemoteDeviceManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration DEVICE_CLEANUP_INTERVAL = Duration.ofMinutes(1L);
  private static final Duration DEVICE_REMOVAL_TIME = Duration.ofMinutes(10L);

  private final ServiceSideVersionChecker versionChecker =
      new ServiceSideVersionChecker(Version.MASTER_V5_VERSION, Version.MIN_LAB_VERSION);
  private final AbstractScheduler scheduler;
  private final ListeningExecutorService threadPool;
  private final ListeningScheduledExecutorService scheduledThreadPool;

  private final Object lock = new Object();

  @GuardedBy("lock")
  private final Map<DeviceKey, DeviceData> devices = new HashMap<>();

  @GuardedBy("lock")
  private final Map<String, DeviceKey> deviceUuids = new HashMap<>();

  private final SettableFuture<Void> firstDeviceOrTimeoutFuture = SettableFuture.create();

  @Inject
  RemoteDeviceManager(
      AbstractScheduler scheduler,
      ListeningExecutorService threadPool,
      ListeningScheduledExecutorService scheduledThreadPool) {
    this.scheduler = scheduler;
    this.threadPool = threadPool;
    this.scheduledThreadPool = scheduledThreadPool;
  }

  void start(int grpcPort) throws MobileHarnessException {
    // Registers AllocationEventHandler.
    scheduler.registerEventHandler(new AllocationEventHandler());

    // Starts gRPC server.
    try {
      NettyServerBuilder.forPort(grpcPort)
          .addService(new LabSyncService())
          .executor(threadPool)
          .build()
          .start();
    } catch (IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.CLIENT_ATS_MODE_START_REMOTE_DEVICE_MANAGER_ERROR,
          String.format("Failed to start remote device manager with port [%s]", grpcPort),
          e);
    }

    // Starts device cleaner.
    logFailure(
        scheduledThreadPool.scheduleWithFixedDelay(
            threadRenaming(this::cleanUpDevices, () -> "remote-device-manager-device-cleaner"),
            DEVICE_CLEANUP_INTERVAL,
            DEVICE_CLEANUP_INTERVAL),
        Level.WARNING,
        "Error when cleaning up devices");

    // Starts first device awaiter.
    logFailure(
        scheduledThreadPool.schedule(
            threadRenaming(
                () -> firstDeviceOrTimeoutFuture.set(null),
                () -> "remote-device-manager-first-device-awaiter-timeout-marker"),
            Duration.ofSeconds(10L)),
        Level.WARNING,
        "Error when marking timeout for awaiting first device");

    logger.atInfo().log("Remote device manager started, grpc_port=%s", grpcPort);
  }

  ImmutableList<DeviceInfo> getDeviceInfos() {
    synchronized (lock) {
      return devices.values().stream().map(DeviceData::toDeviceInfo).collect(toImmutableList());
    }
  }

  /**
   * Returns a future which becomes done when the first device appears or the device manager has
   * been started for a while.
   *
   * <p>The returned future will not fail.
   */
  ListenableFuture<Void> getFirstDeviceOrTimeoutFuture() {
    return firstDeviceOrTimeoutFuture;
  }

  private class AllocationEventHandler {

    @Subscribe
    private void onAllocation(AllocationEvent event) {
      Allocation allocation = event.getAllocation();

      synchronized (lock) {
        for (DeviceLocator deviceLocator : allocation.getAllDevices()) {
          DeviceKey deviceKey =
              DeviceKey.of(deviceLocator.labLocator().hostName(), deviceLocator.id());
          if (devices.containsKey(deviceKey)) {
            DeviceData deviceData = devices.get(deviceKey);
            deviceData.updateByAllocationEvent(allocation);
          }
        }
      }
    }
  }

  private class LabSyncService extends LabSyncServiceGrpc.LabSyncServiceImplBase {

    private SignUpLabResponse doSignUpLab(SignUpLabRequest request)
        throws MobileHarnessException, InterruptedException {
      logger.atInfo().log("Sign up lab, req=[%s]", shortDebugString(request));

      // Checks version.
      VersionCheckResponse versionCheckResponse =
          versionChecker.checkStub(request.getVersionCheckRequest());

      // Creates lab locator.
      LabLocator labLocator = LabLocator.of(request.getLabIp(), request.getLabHostName());
      labLocator.ports().addAll(request.getLabServerSetting().getPortList());

      // TODO: Handles lab_server_feature.

      List<String> duplicatedUuids = new ArrayList<>();
      synchronized (lock) {
        for (SignUpLabRequest.Device device : request.getDeviceList()) {
          DeviceKey deviceKey = DeviceKey.of(labLocator.hostName(), device.getControlId());

          // Checks empty UUID.
          if (device.getUuid().isEmpty()) {
            logger.atWarning().log("Empty UUID, reject it, device=%s", shortDebugString(device));
            duplicatedUuids.add(device.getUuid());
            continue;
          }
          // Checks duplicated UUID.
          // TODO: Handles DisconnectedDevice and MissingDevice with duplicated UUID.
          if (deviceUuids.containsKey(device.getUuid())) {
            DeviceKey otherDeviceKey = deviceUuids.get(device.getUuid());
            if (!otherDeviceKey.equals(deviceKey)) {
              logger.atWarning().log(
                  "Duplicated UUID, reject it, uuid=[%s], new_device=%s, existing_device=%s",
                  device.getUuid(), deviceKey, otherDeviceKey);
              duplicatedUuids.add(device.getUuid());
              continue;
            }
          }

          DeviceData deviceData;
          if (devices.containsKey(deviceKey)) {
            // Updates device data.
            deviceData = devices.get(deviceKey);
            deviceData.updateBySignUp(device);
          } else {
            // Adds device data.
            deviceData = new DeviceData(deviceKey, labLocator, device);
            devices.put(deviceKey, deviceData);
            deviceUuids.put(deviceData.uuid, deviceKey);
            firstDeviceOrTimeoutFuture.set(null);
          }

          updateScheduler(deviceData);
        }
      }

      return SignUpLabResponse.newBuilder()
          .setVersionCheckResponse(versionCheckResponse)
          .addAllDuplicatedDeviceUuid(duplicatedUuids)
          .build();
    }

    private HeartbeatLabResponse doHeartbeatLab(HeartbeatLabRequest request)
        throws InterruptedException {
      logger.atInfo().log("Heartbeat lab, req=[%s]", shortDebugString(request));

      List<String> outdatedDeviceIds = new ArrayList<>();
      synchronized (lock) {
        for (HeartbeatLabRequest.Device device : request.getDeviceList()) {
          DeviceKey deviceKey = DeviceKey.of(request.getLabHostName(), device.getId());

          if (!devices.containsKey(deviceKey)) {
            logger.atInfo().log("Device hasn't been signed up yet, device=%s", deviceKey);
            outdatedDeviceIds.add(device.getId());
            continue;
          }

          DeviceData deviceData = devices.get(deviceKey);
          boolean needSignUp = deviceData.updateByHeartbeat(device);

          if (needSignUp) {
            outdatedDeviceIds.add(deviceKey.deviceControlId());
          }

          updateScheduler(deviceData);
        }
      }

      return HeartbeatLabResponse.newBuilder().addAllOutdatedDeviceId(outdatedDeviceIds).build();
    }

    private SignOutDeviceResponse doSignOutDevice(SignOutDeviceRequest request)
        throws InterruptedException {
      logger.atInfo().log("Sign out device, req=[%s]", shortDebugString(request));

      synchronized (lock) {
        DeviceKey deviceKey = DeviceKey.of(request.getLabHostName(), request.getDeviceId());
        if (devices.containsKey(deviceKey)) {
          DeviceData deviceData = devices.get(deviceKey);

          scheduler.unallocate(
              deviceData.dataFromLab.locator(), /* removeDevices= */ true, /* closeTest= */ true);

          devices.remove(deviceKey);
          deviceUuids.remove(deviceData.uuid);
        } else {
          logger.atWarning().log("Device to sign out not found, device=%s", deviceKey);
        }
      }

      return SignOutDeviceResponse.getDefaultInstance();
    }

    private RemoveMissingDeviceResponse doRemoveMissingDevice(RemoveMissingDeviceRequest request) {
      doRemoveMissingDevices(
          RemoveMissingDevicesRequest.newBuilder().addRemoveMissingDeviceRequest(request).build());
      return RemoveMissingDeviceResponse.getDefaultInstance();
    }

    @CanIgnoreReturnValue
    private RemoveMissingDevicesResponse doRemoveMissingDevices(
        RemoveMissingDevicesRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void signUpLab(
        SignUpLabRequest request, StreamObserver<SignUpLabResponse> responseObserver) {
      GrpcServiceUtil.invoke(
          request,
          responseObserver,
          this::doSignUpLab,
          LabSyncServiceGrpc.getServiceDescriptor(),
          LabSyncServiceGrpc.getSignUpLabMethod());
    }

    @Override
    public void heartbeatLab(
        HeartbeatLabRequest request, StreamObserver<HeartbeatLabResponse> responseObserver) {
      GrpcServiceUtil.invoke(
          request,
          responseObserver,
          this::doHeartbeatLab,
          LabSyncServiceGrpc.getServiceDescriptor(),
          LabSyncServiceGrpc.getHeartbeatLabMethod());
    }

    @Override
    public void signOutDevice(
        SignOutDeviceRequest request, StreamObserver<SignOutDeviceResponse> responseObserver) {
      GrpcServiceUtil.invoke(
          request,
          responseObserver,
          this::doSignOutDevice,
          LabSyncServiceGrpc.getServiceDescriptor(),
          LabSyncServiceGrpc.getSignOutDeviceMethod());
    }

    @Override
    public void removeMissingDevice(
        RemoveMissingDeviceRequest request,
        StreamObserver<RemoveMissingDeviceResponse> responseObserver) {
      GrpcServiceUtil.invoke(
          request,
          responseObserver,
          this::doRemoveMissingDevice,
          LabSyncServiceGrpc.getServiceDescriptor(),
          LabSyncServiceGrpc.getRemoveMissingDeviceMethod());
    }

    @Override
    public void removeMissingDevices(
        RemoveMissingDevicesRequest request,
        StreamObserver<RemoveMissingDevicesResponse> responseObserver) {
      GrpcServiceUtil.invoke(
          request,
          responseObserver,
          this::doRemoveMissingDevices,
          LabSyncServiceGrpc.getServiceDescriptor(),
          LabSyncServiceGrpc.getRemoveMissingDevicesMethod());
    }
  }

  @GuardedBy("lock")
  private void updateScheduler(DeviceData deviceData) throws InterruptedException {
    switch (deviceData.statusFromLab) {
      case IDLE:
        // Adds device to scheduler if it is IDLE.
        scheduler.upsertDevice(
            deviceData.dataFromLab,
            new LabScheduleUnit(deviceData.dataFromLab.locator().labLocator()));
        break;
      case BUSY:
        // Does nothing if it is BUSY.
        break;
      default:
        // Removes device from scheduler if it is not IDLE or BUSY.
        scheduler.unallocate(
            deviceData.dataFromLab.locator(), /* removeDevices= */ true, /* closeTest= */ true);
        break;
    }
  }

  /**
   * Clean up devices that have no heartbeat for a while.
   *
   * @throws IllegalStateException if interrupted
   */
  private void cleanUpDevices() {
    logger.atInfo().log("Cleaning up devices");
    Instant timestamp = Instant.now();
    // TODO: Uses fine-grained lock.
    synchronized (lock) {
      for (Iterator<Entry<DeviceKey, DeviceData>> iterator = devices.entrySet().iterator();
          iterator.hasNext(); ) {
        Entry<DeviceKey, DeviceData> entry = iterator.next();
        DeviceKey deviceKey = entry.getKey();
        DeviceData deviceData = entry.getValue();

        if (deviceData.updateFromLabLocalTimestamp.plus(DEVICE_REMOVAL_TIME).isBefore(timestamp)) {
          logger.atInfo().log(
              "Remove device, device=%s, last_update_from_lab=%s",
              deviceKey, deviceData.updateFromLabLocalTimestamp);

          try {
            scheduler.unallocate(
                deviceData.dataFromLab.locator(), /* removeDevices= */ true, /* closeTest= */ true);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }

          iterator.remove();
          deviceUuids.remove(deviceData.uuid);
        }
      }
    }
    Duration cleanupTime = Duration.between(timestamp, Instant.now());
    logger.atInfo().log("Devices cleanup finished, time_used=%s", cleanupTime);
  }

  /** Devices are indexed by (host_name + control_id). */
  @AutoValue
  abstract static class DeviceKey {

    abstract String labHostName();

    abstract String deviceControlId();

    private static DeviceKey of(String labHostName, String deviceControlId) {
      return new AutoValue_RemoteDeviceManager_DeviceKey(labHostName, deviceControlId);
    }
  }

  /** All access to this class must be guarded by {@link #lock}. */
  private static class DeviceData {

    private final DeviceKey deviceKey;

    private final String uuid;

    private DeviceScheduleUnit dataFromLab;

    /** Lab-side timestamp that is corresponding to {@link #dataFromLab}. */
    private Instant dataFromLabTimestamp;

    private DeviceStatus statusFromLab;

    /** Lab-side timestamp that is corresponding to {@link #statusFromLab}. */
    private Instant statusFromLabTimestamp;

    /**
     * Latest local timestamp that the lab sent any rpc to update this device (even if the update
     * was rejected).
     */
    private Instant updateFromLabLocalTimestamp;

    /**
     * Latest allocation on this device from scheduler. On the lab side, it is possible that the
     * test has not started, or is running, or has ended.
     */
    @Nullable private Allocation latestAllocationFromScheduler;

    private DeviceData(DeviceKey deviceKey, LabLocator labLocator, SignUpLabRequest.Device device) {
      Instant timestamp = Instant.ofEpochMilli(device.getTimestampMs());
      this.deviceKey = deviceKey;
      this.uuid = device.getUuid();
      this.dataFromLab =
          new DeviceScheduleUnit(DeviceLocator.of(device.getControlId(), labLocator));
      dataFromLab.addFeature(device.getFeature());
      this.dataFromLabTimestamp = timestamp;
      setStatusFromLab(device.getStatus(), timestamp);
      this.updateFromLabLocalTimestamp = Instant.now();
    }

    /**
     * Updates device data if there is a new one.
     *
     * <p>Lab locator port settings will not be changed.
     */
    private void updateBySignUp(SignUpLabRequest.Device device) {
      updateFromLabLocalTimestamp = Instant.now();

      Instant timestamp = Instant.ofEpochMilli(device.getTimestampMs());

      if (statusFromLabTimestamp.isBefore(timestamp)) {
        setStatusFromLab(device.getStatus(), timestamp);
      } else {
        logger.atWarning().log(
            "SignUpLabRequest.Device timestamp is older than current status timestamp [%s], ignore"
                + " it, device=%s, req=%s",
            statusFromLabTimestamp, deviceKey, shortDebugString(device));
      }

      if (dataFromLabTimestamp.isBefore(timestamp)) {
        // TODO: Merges and updates lab server ports.
        dataFromLab = new DeviceScheduleUnit(dataFromLab.locator());
        dataFromLab.addFeature(device.getFeature());
        dataFromLabTimestamp = timestamp;
      } else {
        logger.atWarning().log(
            "SignUpLabRequest.Device timestamp is older than current data timestamp [%s], ignore"
                + " it, device=%s, req=%s",
            dataFromLabTimestamp, deviceKey, shortDebugString(device));
      }
    }

    /**
     * Updates device status if there is a new one.
     *
     * <p>Will not update status from non-IDLE to IDLE. If happens, will require a re-sign-up.
     *
     * @return true if lab needs to re-sign-up the device
     */
    private boolean updateByHeartbeat(HeartbeatLabRequest.Device device) {
      updateFromLabLocalTimestamp = Instant.now();

      Instant timestamp = Instant.ofEpochMilli(device.getTimestampMs());

      if (statusFromLabTimestamp.isBefore(timestamp)) {
        // Does not set status from non-IDLE to IDLE in heartbeat. Requires a signup instead.
        if (!statusFromLab.equals(DeviceStatus.IDLE)
            && device.getStatus().equals(DeviceStatus.IDLE)) {
          logger.atInfo().log(
              "Prevent setting device status from %s to %s in heartbeat, require a re-signup,"
                  + " device=%s, req=%s",
              statusFromLab, device.getStatus(), deviceKey, shortDebugString(device));
          return true;
        }

        // Sets status.
        setStatusFromLab(device.getStatus(), timestamp);
      } else {
        logger.atWarning().log(
            "HeartbeatLabRequest.Device timestamp is older than current status timestamp [%s],"
                + " ignore it, device=%s, req=%s",
            dataFromLabTimestamp, deviceKey, shortDebugString(device));
      }

      return false;
    }

    private void setStatusFromLab(DeviceStatus statusFromLab, Instant statusFromLabTimestamp) {
      if (!statusFromLab.equals(this.statusFromLab)) {
        logger.atInfo().log(
            "Change device status from %s to %s, device=%s",
            this.statusFromLab, statusFromLab, deviceKey);
      }
      this.statusFromLab = statusFromLab;
      this.statusFromLabTimestamp = statusFromLabTimestamp;
    }

    /** Updates latest allocation info. */
    private void updateByAllocationEvent(Allocation allocation) {
      latestAllocationFromScheduler = allocation;
    }

    private DeviceInfo toDeviceInfo() {
      DeviceInfo.Builder deviceInfo =
          DeviceInfo.newBuilder()
              .setId(dataFromLab.locator().id())
              .setStatus(statusFromLab.name())
              .addAllOwner(dataFromLab.owners().getAll())
              .addAllType(dataFromLab.types().getAll())
              .addAllDriver(dataFromLab.drivers().getAll())
              .addAllDecorator(dataFromLab.decorators().getAll())
              .addAllDimension(
                  Stream.concat(
                          dataFromLab.dimensions().supported().getAll().entries().stream()
                              .map(
                                  entry ->
                                      Dimension.newBuilder()
                                          .setName(entry.getKey())
                                          .setValue(entry.getValue())
                                          .build()),
                          dataFromLab.dimensions().required().getAll().entries().stream()
                              .map(
                                  entry ->
                                      Dimension.newBuilder()
                                          .setRequired(true)
                                          .setName(entry.getKey())
                                          .setValue(entry.getValue())
                                          .build()))
                      .collect(toImmutableList()));

      if (statusFromLab.equals(DeviceStatus.BUSY) && latestAllocationFromScheduler != null) {
        deviceInfo
            .setJobId(latestAllocationFromScheduler.getTest().jobLocator().id())
            .setJobName(latestAllocationFromScheduler.getTest().jobLocator().name())
            .setTestId(latestAllocationFromScheduler.getTest().id())
            .setTestName(latestAllocationFromScheduler.getTest().name());
      }

      return deviceInfo.build();
    }
  }
}
