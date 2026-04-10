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

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

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
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
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
  private final UniverseFactory universeFactory;

  @Inject
  ConfigServiceLogicImpl(
      GetDeviceConfigHandler getDeviceConfigHandler,
      CheckDeviceWritePermissionHandler checkDeviceWritePermissionHandler,
      UpdateDeviceConfigHandler updateDeviceConfigHandler,
      GetRecommendedWifiHandler getRecommendedWifiHandler,
      GetHostDefaultDeviceConfigHandler getHostDefaultDeviceConfigHandler,
      GetHostConfigHandler getHostConfigHandler,
      CheckHostWritePermissionHandler checkHostWritePermissionHandler,
      UpdateHostConfigHandler updateHostConfigHandler,
      UniverseFactory universeFactory) {
    this.getDeviceConfigHandler = getDeviceConfigHandler;
    this.checkDeviceWritePermissionHandler = checkDeviceWritePermissionHandler;
    this.updateDeviceConfigHandler = updateDeviceConfigHandler;
    this.getRecommendedWifiHandler = getRecommendedWifiHandler;
    this.getHostDefaultDeviceConfigHandler = getHostDefaultDeviceConfigHandler;
    this.getHostConfigHandler = getHostConfigHandler;
    this.checkHostWritePermissionHandler = checkHostWritePermissionHandler;
    this.updateHostConfigHandler = updateHostConfigHandler;
    this.universeFactory = universeFactory;
  }

  @Override
  public ListenableFuture<GetDeviceConfigResponse> getDeviceConfig(GetDeviceConfigRequest request) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return getDeviceConfigHandler.getDeviceConfig(request, universe);
  }

  @Override
  public ListenableFuture<CheckDeviceWritePermissionResponse> checkDeviceWritePermission(
      CheckDeviceWritePermissionRequest request, Optional<String> username) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return checkDeviceWritePermissionHandler.checkDeviceWritePermission(
        request.getId(), universe, username);
  }

  @Override
  public ListenableFuture<UpdateDeviceConfigResponse> updateDeviceConfig(
      UpdateDeviceConfigRequest request, Optional<String> username) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return updateDeviceConfigHandler.updateDeviceConfig(request, universe, username);
  }

  @Override
  public ListenableFuture<GetRecommendedWifiResponse> getRecommendedWifi(
      GetRecommendedWifiRequest request) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return getRecommendedWifiHandler.getRecommendedWifi(request, universe);
  }

  @Override
  public ListenableFuture<GetHostDefaultDeviceConfigResponse> getHostDefaultDeviceConfig(
      GetHostDefaultDeviceConfigRequest request) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return getHostDefaultDeviceConfigHandler.getHostDefaultDeviceConfig(request, universe);
  }

  @Override
  public ListenableFuture<GetHostConfigResponse> getHostConfig(GetHostConfigRequest request) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return getHostConfigHandler.getHostConfig(request, universe);
  }

  @Override
  public ListenableFuture<CheckHostWritePermissionResponse> checkHostWritePermission(
      CheckHostWritePermissionRequest request, Optional<String> username) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return checkHostWritePermissionHandler.checkHostWritePermission(
        request.getHostName(), universe, username);
  }

  @Override
  public ListenableFuture<UpdateHostConfigResponse> updateHostConfig(
      UpdateHostConfigRequest request, Optional<String> username) {
    UniverseScope universe;
    try {
      universe = universeFactory.create(request.getUniverse());
    } catch (IllegalArgumentException e) {
      return immediateFailedFuture(e);
    }
    return updateHostConfigHandler.updateHostConfig(request, universe, username);
  }
}
