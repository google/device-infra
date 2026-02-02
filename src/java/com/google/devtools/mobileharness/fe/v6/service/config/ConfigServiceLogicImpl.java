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

package com.google.devtools.mobileharness.fe.v6.service.config;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.CheckDeviceWritePermissionHandler;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.CheckHostWritePermissionHandler;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.GetDeviceConfigHandler;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.GetHostConfigHandler;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.GetHostDefaultDeviceConfigHandler;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.GetRecommendedWifiHandler;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.UpdateDeviceConfigHandler;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.UpdateHostConfigHandler;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceWritePermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceWritePermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostWritePermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostWritePermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostDefaultDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostDefaultDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetRecommendedWifiResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateHostConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateHostConfigResponse;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Common implementation of {@link ConfigServiceLogic}. */
@Singleton
public final class ConfigServiceLogicImpl implements ConfigServiceLogic {

  private final GetDeviceConfigHandler getDeviceConfigHandler;
  private final CheckDeviceWritePermissionHandler checkDeviceWritePermissionHandler;
  private final UpdateDeviceConfigHandler updateDeviceConfigHandler;
  private final GetRecommendedWifiHandler getRecommendedWifiHandler;
  private final GetHostDefaultDeviceConfigHandler getHostDefaultDeviceConfigHandler;
  private final GetHostConfigHandler getHostConfigHandler;
  private final CheckHostWritePermissionHandler checkHostWritePermissionHandler;
  private final UpdateHostConfigHandler updateHostConfigHandler;

  @Inject
  ConfigServiceLogicImpl(
      GetDeviceConfigHandler getDeviceConfigHandler,
      CheckDeviceWritePermissionHandler checkDeviceWritePermissionHandler,
      UpdateDeviceConfigHandler updateDeviceConfigHandler,
      GetRecommendedWifiHandler getRecommendedWifiHandler,
      GetHostDefaultDeviceConfigHandler getHostDefaultDeviceConfigHandler,
      GetHostConfigHandler getHostConfigHandler,
      CheckHostWritePermissionHandler checkHostWritePermissionHandler,
      UpdateHostConfigHandler updateHostConfigHandler) {
    this.getDeviceConfigHandler = getDeviceConfigHandler;
    this.checkDeviceWritePermissionHandler = checkDeviceWritePermissionHandler;
    this.updateDeviceConfigHandler = updateDeviceConfigHandler;
    this.getRecommendedWifiHandler = getRecommendedWifiHandler;
    this.getHostDefaultDeviceConfigHandler = getHostDefaultDeviceConfigHandler;
    this.getHostConfigHandler = getHostConfigHandler;
    this.checkHostWritePermissionHandler = checkHostWritePermissionHandler;
    this.updateHostConfigHandler = updateHostConfigHandler;
  }

  @Override
  public ListenableFuture<GetDeviceConfigResponse> getDeviceConfig(GetDeviceConfigRequest request) {
    return getDeviceConfigHandler.getDeviceConfig(request);
  }

  @Override
  public ListenableFuture<CheckDeviceWritePermissionResponse> checkDeviceWritePermission(
      CheckDeviceWritePermissionRequest request) {
    return checkDeviceWritePermissionHandler.checkDeviceWritePermission(request);
  }

  @Override
  public ListenableFuture<UpdateDeviceConfigResponse> updateDeviceConfig(
      UpdateDeviceConfigRequest request) {
    return updateDeviceConfigHandler.updateDeviceConfig(request);
  }

  @Override
  public ListenableFuture<GetRecommendedWifiResponse> getRecommendedWifi(
      GetRecommendedWifiRequest request) {
    return getRecommendedWifiHandler.getRecommendedWifi(request);
  }

  @Override
  public ListenableFuture<GetHostDefaultDeviceConfigResponse> getHostDefaultDeviceConfig(
      GetHostDefaultDeviceConfigRequest request) {
    return getHostDefaultDeviceConfigHandler.getHostDefaultDeviceConfig(request);
  }

  @Override
  public ListenableFuture<GetHostConfigResponse> getHostConfig(GetHostConfigRequest request) {
    return getHostConfigHandler.getHostConfig(request);
  }

  @Override
  public ListenableFuture<CheckHostWritePermissionResponse> checkHostWritePermission(
      CheckHostWritePermissionRequest request) {
    return checkHostWritePermissionHandler.checkHostWritePermission(request);
  }

  @Override
  public ListenableFuture<UpdateHostConfigResponse> updateHostConfig(
      UpdateHostConfigRequest request) {
    return updateHostConfigHandler.updateHostConfig(request);
  }
}
