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

package com.google.devtools.mobileharness.infra.lab.rpc.stub.helper;

import static com.google.common.base.StandardSystemProperty.OS_NAME;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatusWithTimestamp;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabPort;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.PortType;
import com.google.devtools.mobileharness.infra.controller.device.DeviceStatusInfo;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.HeartbeatLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.HeartbeatLabResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignOutDeviceRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.SignUpLabResponse;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabSyncStub;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.proto.Version.VersionCheckRequest;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/** RPC stub helper for talking to MobileHarness Master V5 LabSyncService. */
public class LabSyncHelper {
  public static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting
  static final VersionCheckRequest VERSION_CHECK_REQ =
      VersionCheckRequest.newBuilder()
          .setStubVersion(Version.LAB_VERSION.toString())
          .setMinServiceVersion(Version.MIN_MASTER_V5_VERSION.toString())
          .build();

  // TODO: Define the scope of the Drivers alongside the Driver code.
  /** Banned drivers of public labs. */
  private static final ImmutableList<String> PRIVATE_LAB_DRIVERS =
      ImmutableList.of("AndroidAutopho", "HostBin", "NestGenericRunner");

  // TODO: Define the scope of the Decorator alongside the Decorator code.
  /** Banned decorators of public labs. */
  private static final ImmutableList<String> PRIVATE_LAB_DECORATORS =
      ImmutableList.of("AndroidFlashDeviceDecorator", "AndroidInstallSystemAppsDecorator");

  /** Banned decorators of fast wipe devices. */
  @VisibleForTesting
  static final ImmutableList<String> FAST_WIPE_BANNED_DECORATORS =
      ImmutableList.of(
          "AndroidOtaUpdateDecorator",
          "AndroidThingsFlashDeviceDecorator",
          "AndroidFlashDeviceDecorator",
          "AndroidFlashstationDecorator");

  /** Banned device types of fast wipe devices. */
  @VisibleForTesting
  static final ImmutableList<String> FAST_WIPE_BANNED_DEVICE_TYPES =
      ImmutableList.of("AndroidFlashableDevice");

  /** Stub for syncing lab info with Master. */
  private final LabSyncStub labSyncStub;

  /** RPC port of the Stubby services of this lab server. */
  private final int labRpcPort;

  /**
   * Socket port for receiving file of this lab server.
   *
   * <p>Following j/c/g/wireless/qa/mobileharness/lab/rpc/stub/MasterServiceStub.java. We still need
   * this for FileTransferClient in trident test.
   */
  @Deprecated private final int labSocketPort;

  /** All optional configurations of the lab server and the devices. */
  private final ApiConfig apiConfig;

  /** For detecting real-time lab hostname. */
  private final NetUtil netUtil;

  /**
   * Creates a Stubby stub for talking to MobileHarness Master.
   *
   * @param labSyncStub the master stub for syncing lab
   * @param labRpcPort RPC port of the Stubby services of this lab server
   * @param labSocketPort socket port for receiving file of this lab server
   */
  public LabSyncHelper(LabSyncStub labSyncStub, int labRpcPort, int labSocketPort) {
    this(labSyncStub, labRpcPort, labSocketPort, ApiConfig.getInstance(), new NetUtil());
  }

  @VisibleForTesting
  LabSyncHelper(
      LabSyncStub labSyncStub,
      int labRpcPort,
      int labSocketPort,
      ApiConfig config,
      NetUtil netUtil) {
    this.labSyncStub = labSyncStub;
    this.labRpcPort = labRpcPort;
    this.labSocketPort = labSocketPort;
    this.apiConfig = config;
    this.netUtil = netUtil;
  }

  /**
   * Signs up devices to master server.
   *
   * @param devices the device information and their status
   * @throws MobileHarnessException if fails to talk to master server or signs up error, with error
   *     code MasterError.LAB_VERSION_LOWER_THAN_REQUIRED if the lab version is lower than the
   *     master required; with error code MasterError.MASTER_VERSION_LOWER_THAN_REQUIRED if the
   *     master version is lower than the lab required
   */
  @CanIgnoreReturnValue
  public SignUpLabResponse signUpLab(Map<Device, DeviceStatusInfo> devices)
      throws MobileHarnessException {
    // Lab hostname and IP can change, needs real-time detection.
    SignUpLabRequest.Builder req =
        SignUpLabRequest.newBuilder()
            .setVersionCheckRequest(VERSION_CHECK_REQ)
            .setLabHostName(netUtil.getLocalHostName())
            .setLabServerSetting(
                LabServerSetting.newBuilder()
                    .addPort(
                        LabPort.newBuilder()
                            .setType(PortType.LAB_SERVER_RPC)
                            .setNum(labRpcPort)
                            .build())
                    .addPort(
                        LabPort.newBuilder()
                            .setType(PortType.LAB_SERVER_SOCKET)
                            .setNum(labSocketPort)
                            .build())
                    .build())
            .setLabServerFeature(getLabServerFeature());

    // If multiple IP detected in Lab, then let master decide which one to use.
    netUtil.getUniqueHostIpOrEmpty().ifPresent(req::setLabIp);

    List<String> deviceInfos = new ArrayList<>(devices.size());
    for (Map.Entry<Device, DeviceStatusInfo> entry : devices.entrySet()) {
      Device device = entry.getKey();
      String deviceControlId = device.getDeviceControlId();
      String deviceUuid = device.getDeviceUuid();
      DeviceStatusWithTimestamp statusWithTimestamp =
          entry.getValue().getDeviceStatusWithTimestamp();
      ExceptionDetail exceptionDetail = entry.getValue().getExceptionDetail();
      deviceInfos.add(
          String.format(
              "%s/%s(%s), error (%s):%s",
              deviceControlId,
              deviceUuid,
              statusWithTimestamp.getStatus(),
              exceptionDetail == null ? "" : exceptionDetail.getSummary().getErrorId().getName(),
              exceptionDetail == null ? "" : exceptionDetail.getSummary().getMessage()));

      // Removes the banned drivers from public lab.
      List<String> owners = apiConfig.getOwners(deviceControlId);
      List<String> executors = apiConfig.getExecutors(deviceControlId);
      Set<String> drivers = new HashSet<>(device.getDriverTypes());
      Set<String> decorators = new HashSet<>(device.getDecoratorTypes());
      Set<String> deviceTypes = new HashSet<>(device.getDeviceTypes());
      if (owners.isEmpty()) {
        drivers.removeAll(PRIVATE_LAB_DRIVERS);
        decorators.removeAll(PRIVATE_LAB_DECORATORS);

        // Ban these drivers of non-shared pool "public" (owners are not set) devices.
        // At this point we still don't want bother the new local lab whose owners haven't
        // been set yet.
        // TODO: Define the scope of the Drivers alongside the Driver code.
        if (!device.getDimension(Name.POOL).stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet())
            .contains(Value.POOL_SHARED)) {
          drivers.removeAll(ImmutableList.of("AndroidMonkey"));
        }
      }

      // Bans the flash/recovery related decorators on fastwipe devices.
      // We could consider re-flash the device in future if these decorators have strong reasons to
      // run on the fastwipe devices.
      List<String> recoveryTypes = device.getDimension(Name.RECOVERY);
      if (recoveryTypes.size() == 1
          && (recoveryTypes.get(0).equalsIgnoreCase("wipe")
              || recoveryTypes.get(0).equalsIgnoreCase("standard"))) {
        deviceTypes.removeAll(FAST_WIPE_BANNED_DEVICE_TYPES);
        decorators.removeAll(FAST_WIPE_BANNED_DECORATORS);
      }

      DeviceCompositeDimension.Builder compositeDimension = DeviceCompositeDimension.newBuilder();
      device
          .getDimensions()
          .forEach(
              dimension ->
                  compositeDimension.addSupportedDimension(
                      DeviceDimension.newBuilder()
                          .setName(dimension.getName())
                          .setValue(dimension.getValue())
                          .build()));
      device
          .getRequiredDimensions()
          .forEach(
              dimension ->
                  compositeDimension.addRequiredDimension(
                      DeviceDimension.newBuilder()
                          .setName(dimension.getName())
                          .setValue(dimension.getValue())
                          .build()));

      SignUpLabRequest.Device.Builder deviceSummary =
          SignUpLabRequest.Device.newBuilder()
              .setControlId(deviceControlId)
              .setUuid(deviceUuid)
              .setTimestampMs(statusWithTimestamp.getTimestampMs())
              .setStatus(statusWithTimestamp.getStatus())
              .setFeature(
                  DeviceFeature.newBuilder()
                      .addAllOwner(owners)
                      .addAllExecutor(executors)
                      .addAllType(deviceTypes)
                      .addAllDriver(drivers)
                      .addAllDecorator(decorators)
                      .setCompositeDimension(compositeDimension)
                      .build());
      if (exceptionDetail != null) {
        deviceSummary.setFlattenedExceptionDetail(
            ErrorModelConverter.toFlattenedExceptionDetail(exceptionDetail));
      }
      req.addDevice(deviceSummary.build());
    }

    if (!deviceInfos.isEmpty()) {
      logger.atInfo().log("Sign up lab with devices: %s", deviceInfos);
    } else {
      logger.atInfo().log("Sign up lab with no device");
    }

    try {
      return labSyncStub.signUpLab(req.build());
    } catch (RpcExceptionWithErrorId e) {
      // TODO: The following version check is useless because it is still checking the
      // old ErrorCode, while the Master is throwing the new ErrorIds. Need to upgrade this version
      // checker:
      // versionChecker.check(e);
      throw new MobileHarnessException(
          InfraErrorId.LAB_SYNC_SIGN_UP_ERROR, "Failed to sign up with Master", e);
    }
  }

  private LabServerFeature getLabServerFeature() throws MobileHarnessException {
    HostProperties.Builder hostProperties = apiConfig.getHostProperties().toBuilder();

    netUtil
        .getLocalHostLocation()
        .ifPresent(
            labLocation ->
                hostProperties.addHostProperty(
                    HostProperty.newBuilder()
                        .setKey(Ascii.toLowerCase(Name.LAB_LOCATION.name()))
                        .setValue(labLocation)
                        .build()));
    hostProperties.addHostProperty(
        HostProperty.newBuilder()
            .setKey(Ascii.toLowerCase(Name.HOST_OS.name()))
            .setValue(OS_NAME.value()));
    hostProperties.addHostProperty(
        HostProperty.newBuilder()
            .setKey(Ascii.toLowerCase(Name.HOST_VERSION.name()))
            .setValue(Version.LAB_VERSION.toString()));
    hostProperties.addHostProperty(
        HostProperty.newBuilder()
            .setKey(Ascii.toLowerCase(Name.LOCATION_TYPE.name()))
            .setValue(
                String.valueOf(Ascii.toLowerCase(netUtil.getLocalHostLocationType().name()))));
    hostProperties.addHostProperty(
        HostProperty.newBuilder()
            .setKey(Ascii.toLowerCase(Name.HOST_OS_VERSION.name()))
            .setValue(System.getProperty("os.version")));

    return LabServerFeature.newBuilder().setHostProperties(hostProperties).build();
  }

  /**
   * Sends heartbeat of the device to master server.
   *
   * @param devices the {@code <device, status>} mapping of all detectable devices
   * @return the device heartbeat response
   * @throws MobileHarnessException if fails to talk to master server or heartbeat error, with error
   *     code MasterError.LAB_VERSION_LOWER_THAN_REQUIRED if the lab version is lower than the
   *     master required
   */
  public HeartbeatLabResponse heartbeatLab(Map<Device, DeviceStatusInfo> devices)
      throws MobileHarnessException {
    List<String> deviceIds = new ArrayList<>(devices.size());
    HeartbeatLabRequest.Builder req =
        HeartbeatLabRequest.newBuilder().setLabHostName(netUtil.getLocalHostName());
    for (Map.Entry<Device, DeviceStatusInfo> entry : devices.entrySet()) {
      String deviceId =
          Strings.isNullOrEmpty(entry.getKey().getDeviceUuid())
              ? entry.getKey().getDeviceControlId()
              : entry.getKey().getDeviceUuid();
      deviceIds.add(deviceId);
      req.addDevice(
          HeartbeatLabRequest.Device.newBuilder()
              .setId(deviceId)
              .setStatus(entry.getValue().getDeviceStatusWithTimestamp().getStatus())
              .setTimestampMs(entry.getValue().getDeviceStatusWithTimestamp().getTimestampMs())
              .build());
    }
    // If multiple IP detected in Lab, then let master decide which one to use.
    netUtil.getUniqueHostIpOrEmpty().ifPresent(req::setLabIp);

    logger.atFine().log("Heartbeat devices: [%s]", Joiner.on(", ").join(deviceIds));
    try {
      return labSyncStub.heartbeatLab(req.build());
    } catch (RpcExceptionWithErrorId e) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_SYNC_HEARTBEAT_ERROR, "Failed to send heartbeat to Master", e);
    }
  }

  /** Signs out the device from the master. */
  public void signOutDevice(final String deviceId)
      throws ExecutionException, MobileHarnessException, InterruptedException {
    logger.atInfo().log("Sign out device: %s", deviceId);
    // Signs out device in an async rpc call because it can be invoked when the device runner thread
    // is interrupted.
    SignOutDeviceRequest.Builder req =
        SignOutDeviceRequest.newBuilder()
            .setDeviceId(deviceId)
            .setLabHostName(netUtil.getLocalHostName());
    // If multiple IP detected in Lab, then let master decide which one to use.
    netUtil.getUniqueHostIpOrEmpty().ifPresent(req::setLabIp);

    labSyncStub.signOutDevice(req.build()).get();
  }
}
