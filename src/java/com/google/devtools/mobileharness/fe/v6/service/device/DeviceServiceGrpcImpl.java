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
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceOverview;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceServiceGrpc;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceHealthinessStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceRecoveryTaskStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceTestResultStatsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthinessStats;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.RecoveryTaskStats;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TestResultStats;
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
      GetDeviceOverviewRequest request, StreamObserver<DeviceOverview> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getDeviceOverview,
        executor,
        DeviceServiceGrpc.getServiceDescriptor(),
        DeviceServiceGrpc.getGetDeviceOverviewMethod());
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
}
