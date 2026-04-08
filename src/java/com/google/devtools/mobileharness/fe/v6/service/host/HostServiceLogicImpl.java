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

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.host.handlers.GetHostDeviceSummariesHandler;
import com.google.devtools.mobileharness.fe.v6.service.host.handlers.GetHostHeaderInfoHandler;
import com.google.devtools.mobileharness.fe.v6.service.host.handlers.GetHostOverviewHandler;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.CanRolloutRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.CanRolloutResponse;
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
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Common implementation of {@link HostServiceLogic}. */
@Singleton
public final class HostServiceLogicImpl implements HostServiceLogic {

  private final GetHostOverviewHandler getHostOverviewHandler;
  private final GetHostDeviceSummariesHandler getHostDeviceSummariesHandler;
  private final GetHostHeaderInfoHandler getHostHeaderInfoHandler;
  private final UniverseFactory universeFactory;

  @Inject
  HostServiceLogicImpl(
      GetHostOverviewHandler getHostOverviewHandler,
      GetHostDeviceSummariesHandler getHostDeviceSummariesHandler,
      GetHostHeaderInfoHandler getHostHeaderInfoHandler,
      UniverseFactory universeFactory) {
    this.getHostOverviewHandler = getHostOverviewHandler;
    this.getHostDeviceSummariesHandler = getHostDeviceSummariesHandler;
    this.getHostHeaderInfoHandler = getHostHeaderInfoHandler;
    this.universeFactory = universeFactory;
  }

  @Override
  public ListenableFuture<HostHeaderInfo> getHostHeaderInfo(GetHostHeaderInfoRequest request) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return getHostHeaderInfoHandler.getHostHeaderInfo(request, universe);
  }

  @Override
  public ListenableFuture<HostOverviewPageData> getHostOverview(GetHostOverviewRequest request) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return getHostOverviewHandler.getHostOverview(request, universe);
  }

  @Override
  public ListenableFuture<GetHostDeviceSummariesResponse> getHostDeviceSummaries(
      GetHostDeviceSummariesRequest request) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return getHostDeviceSummariesHandler.getHostDeviceSummaries(request, universe);
  }

  @Override
  public ListenableFuture<GetHostDebugInfoResponse> getHostDebugInfo(
      GetHostDebugInfoRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(GetHostDebugInfoResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<GetPopularFlagsResponse> getPopularFlags(GetPopularFlagsRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(GetPopularFlagsResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<UpdatePassThroughFlagsResponse> updatePassThroughFlags(
      UpdatePassThroughFlagsRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(UpdatePassThroughFlagsResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<GetReleaseConfigsResponse> getReleaseConfigs(
      GetReleaseConfigsRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(GetReleaseConfigsResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<DecommissionMissingDevicesResponse> decommissionMissingDevices(
      DecommissionMissingDevicesRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(DecommissionMissingDevicesResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<CheckRemoteControlEligibilityResponse> checkRemoteControlEligibility(
      CheckRemoteControlEligibilityRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(CheckRemoteControlEligibilityResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<RemoteControlDevicesResponse> remoteControlDevices(
      RemoteControlDevicesRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(RemoteControlDevicesResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<DecommissionHostResponse> decommissionHost(
      DecommissionHostRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(DecommissionHostResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<ReleaseLabServerResponse> releaseLabServer(
      ReleaseLabServerRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(ReleaseLabServerResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<StartLabServerResponse> startLabServer(StartLabServerRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(StartLabServerResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<RestartLabServerResponse> restartLabServer(
      RestartLabServerRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(RestartLabServerResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<StopLabServerResponse> stopLabServer(StopLabServerRequest request) {
    // TODO: Use the universe parameter.
    @SuppressWarnings("unused")
    String universe = request.getUniverse();
    // TODO: Implement this method.
    return immediateFuture(StopLabServerResponse.getDefaultInstance());
  }

  @Override
  public ListenableFuture<CanRolloutResponse> canRollout(CanRolloutRequest request) {
    // TODO: Implement this method.
    return immediateFuture(CanRolloutResponse.getDefaultInstance());
  }
}
