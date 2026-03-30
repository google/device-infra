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

package com.google.devtools.mobileharness.fe.v6.service.host;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.CheckRemoteControlEligibilityRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.CheckRemoteControlEligibilityResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionHostRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionHostResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionMissingDevicesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionMissingDevicesResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDebugInfoRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDebugInfoResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDeviceSummariesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDeviceSummariesResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostHeaderInfoRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetPopularFlagsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetPopularFlagsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetReleaseConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetReleaseConfigsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostOverviewPageData;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostServiceGrpc;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.ReleaseLabServerRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.ReleaseLabServerResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RemoteControlDevicesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RemoteControlDevicesResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RestartLabServerRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RestartLabServerResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.StartLabServerRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.StartLabServerResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.StopLabServerRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.StopLabServerResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsResponse;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

/** Implementation of the gRPC HostService. */
public final class HostServiceGrpcImpl extends HostServiceGrpc.HostServiceImplBase {

  private final HostServiceLogic logic;
  private final ListeningExecutorService executor;

  @Inject
  HostServiceGrpcImpl(HostServiceLogic logic, ListeningExecutorService executor) {
    this.logic = logic;
    this.executor = executor;
  }

  @Override
  public void getHostHeaderInfo(
      GetHostHeaderInfoRequest request, StreamObserver<HostHeaderInfo> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getHostHeaderInfo,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getGetHostHeaderInfoMethod());
  }

  @Override
  public void getHostOverview(
      GetHostOverviewRequest request, StreamObserver<HostOverviewPageData> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getHostOverview,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getGetHostOverviewMethod());
  }

  @Override
  public void getHostDeviceSummaries(
      GetHostDeviceSummariesRequest request,
      StreamObserver<GetHostDeviceSummariesResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getHostDeviceSummaries,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getGetHostDeviceSummariesMethod());
  }

  @Override
  public void getHostDebugInfo(
      GetHostDebugInfoRequest request, StreamObserver<GetHostDebugInfoResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getHostDebugInfo,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getGetHostDebugInfoMethod());
  }

  @Override
  public void getPopularFlags(
      GetPopularFlagsRequest request, StreamObserver<GetPopularFlagsResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getPopularFlags,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getGetPopularFlagsMethod());
  }

  @Override
  public void updatePassThroughFlags(
      UpdatePassThroughFlagsRequest request,
      StreamObserver<UpdatePassThroughFlagsResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::updatePassThroughFlags,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getUpdatePassThroughFlagsMethod());
  }

  @Override
  public void getReleaseConfigs(
      GetReleaseConfigsRequest request,
      StreamObserver<GetReleaseConfigsResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getReleaseConfigs,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getGetReleaseConfigsMethod());
  }

  @Override
  public void decommissionMissingDevices(
      DecommissionMissingDevicesRequest request,
      StreamObserver<DecommissionMissingDevicesResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::decommissionMissingDevices,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getDecommissionMissingDevicesMethod());
  }

  @Override
  public void checkRemoteControlEligibility(
      CheckRemoteControlEligibilityRequest request,
      StreamObserver<CheckRemoteControlEligibilityResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::checkRemoteControlEligibility,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getCheckRemoteControlEligibilityMethod());
  }

  @Override
  public void remoteControlDevices(
      RemoteControlDevicesRequest request,
      StreamObserver<RemoteControlDevicesResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::remoteControlDevices,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getRemoteControlDevicesMethod());
  }

  @Override
  public void decommissionHost(
      DecommissionHostRequest request, StreamObserver<DecommissionHostResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::decommissionHost,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getDecommissionHostMethod());
  }

  @Override
  public void releaseLabServer(
      ReleaseLabServerRequest request, StreamObserver<ReleaseLabServerResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::releaseLabServer,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getReleaseLabServerMethod());
  }

  @Override
  public void startLabServer(
      StartLabServerRequest request, StreamObserver<StartLabServerResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::startLabServer,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getStartLabServerMethod());
  }

  @Override
  public void restartLabServer(
      RestartLabServerRequest request, StreamObserver<RestartLabServerResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::restartLabServer,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getRestartLabServerMethod());
  }

  @Override
  public void stopLabServer(
      StopLabServerRequest request, StreamObserver<StopLabServerResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::stopLabServer,
        executor,
        HostServiceGrpc.getServiceDescriptor(),
        HostServiceGrpc.getStopLabServerMethod());
  }
}
