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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.CheckRemoteControlEligibilityRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.CheckRemoteControlEligibilityResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionMissingDevicesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionMissingDevicesResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDeviceSummariesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDeviceSummariesResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostOverview;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RemoteControlDevicesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RemoteControlDevicesResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsResponse;

/** Interface for the core logic of the Host Service. */
public interface HostServiceLogic {
  ListenableFuture<HostOverview> getHostOverview(GetHostOverviewRequest request);

  ListenableFuture<GetHostDeviceSummariesResponse> getHostDeviceSummaries(
      GetHostDeviceSummariesRequest request);

  ListenableFuture<UpdatePassThroughFlagsResponse> updatePassThroughFlags(
      UpdatePassThroughFlagsRequest request);

  ListenableFuture<DecommissionMissingDevicesResponse> decommissionMissingDevices(
      DecommissionMissingDevicesRequest request);

  ListenableFuture<CheckRemoteControlEligibilityResponse> checkRemoteControlEligibility(
      CheckRemoteControlEligibilityRequest request);

  ListenableFuture<RemoteControlDevicesResponse> remoteControlDevices(
      RemoteControlDevicesRequest request);
}
