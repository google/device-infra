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

package com.google.devtools.mobileharness.fe.v6.service.device;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceOverviewPageData;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceServiceGrpc;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceHeaderInfoRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceHealthinessStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceRecoveryTaskStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceTestResultStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetLogcatRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetLogcatResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthinessStats;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineDeviceRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineDeviceResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.RecoveryTaskStats;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.RemoteControlRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.RemoteControlResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TakeScreenshotRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TakeScreenshotResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TestResultStats;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.UnquarantineDeviceRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.UnquarantineDeviceResponse;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

/** Implementation of the gRPC DeviceService. */
public final class DeviceServiceGrpcImpl extends DeviceServiceGrpc.DeviceServiceImplBase {

  private final DeviceServiceLogic logic;
  private final ListeningExecutorService executor;

  @Inject
  DeviceServiceGrpcImpl(DeviceServiceLogic logic, ListeningExecutorService executor) {
    this.logic = logic;
    this.executor = executor;
  }

  @Override
  public void getDeviceOverview(
      GetDeviceOverviewRequest request, StreamObserver<DeviceOverviewPageData> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getDeviceOverview,
        executor,
        DeviceServiceGrpc.getServiceDescriptor(),
        DeviceServiceGrpc.getGetDeviceOverviewMethod());
  }

  @Override
  public void getDeviceHeaderInfo(
      GetDeviceHeaderInfoRequest request, StreamObserver<DeviceHeaderInfo> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getDeviceHeaderInfo,
        executor,
        DeviceServiceGrpc.getServiceDescriptor(),
        DeviceServiceGrpc.getGetDeviceHeaderInfoMethod());
  }

  @Override
  public void getDeviceHealthinessStats(
      GetDeviceHealthinessStatsRequest request, StreamObserver<HealthinessStats> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getDeviceHealthinessStats,
        executor,
        DeviceServiceGrpc.getServiceDescriptor(),
        DeviceServiceGrpc.getGetDeviceHealthinessStatsMethod());
  }

  @Override
  public void getDeviceTestResultStats(
      GetDeviceTestResultStatsRequest request, StreamObserver<TestResultStats> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getDeviceTestResultStats,
        executor,
        DeviceServiceGrpc.getServiceDescriptor(),
        DeviceServiceGrpc.getGetDeviceTestResultStatsMethod());
  }

  @Override
  public void getDeviceRecoveryTaskStats(
      GetDeviceRecoveryTaskStatsRequest request,
      StreamObserver<RecoveryTaskStats> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getDeviceRecoveryTaskStats,
        executor,
        DeviceServiceGrpc.getServiceDescriptor(),
        DeviceServiceGrpc.getGetDeviceRecoveryTaskStatsMethod());
  }

  @Override
  public void takeScreenshot(
      TakeScreenshotRequest request, StreamObserver<TakeScreenshotResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::takeScreenshot,
        executor,
        DeviceServiceGrpc.getServiceDescriptor(),
        DeviceServiceGrpc.getTakeScreenshotMethod());
  }

  @Override
  public void getLogcat(
      GetLogcatRequest request, StreamObserver<GetLogcatResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getLogcat,
        executor,
        DeviceServiceGrpc.getServiceDescriptor(),
        DeviceServiceGrpc.getGetLogcatMethod());
  }

  @Override
  public void quarantineDevice(
      QuarantineDeviceRequest request, StreamObserver<QuarantineDeviceResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::quarantineDevice,
        executor,
        DeviceServiceGrpc.getServiceDescriptor(),
        DeviceServiceGrpc.getQuarantineDeviceMethod());
  }

  @Override
  public void unquarantineDevice(
      UnquarantineDeviceRequest request,
      StreamObserver<UnquarantineDeviceResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::unquarantineDevice,
        executor,
        DeviceServiceGrpc.getServiceDescriptor(),
        DeviceServiceGrpc.getUnquarantineDeviceMethod());
  }

  @Override
  public void remoteControl(
      RemoteControlRequest request, StreamObserver<RemoteControlResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::remoteControl,
        executor,
        DeviceServiceGrpc.getServiceDescriptor(),
        DeviceServiceGrpc.getRemoteControlMethod());
  }
}
