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

package com.google.devtools.deviceinfra.host.daemon.health.service;

import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toJavaDuration;

import com.google.devtools.deviceinfra.host.daemon.health.HealthStatusManager;
import com.google.devtools.deviceinfra.host.daemon.health.proto.HealthGrpc;
import com.google.devtools.deviceinfra.host.daemon.health.proto.HealthProto.CheckStatusRequest;
import com.google.devtools.deviceinfra.host.daemon.health.proto.HealthProto.CheckStatusResponse;
import com.google.devtools.deviceinfra.host.daemon.health.proto.HealthProto.DrainServerRequest;
import com.google.devtools.deviceinfra.host.daemon.health.proto.HealthProto.DrainServerResponse;
import com.google.devtools.deviceinfra.host.daemon.health.proto.HealthProto.ServingStatus;
import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.Optional;

/** Service for client to query and drain the server via both Grpc and Stubby. */
public class HealthServiceImpl extends HealthGrpc.HealthImplBase {

  private final HealthStatusManager statusManager;

  @Inject
  HealthServiceImpl(HealthStatusManager statusManager) {
    this.statusManager = statusManager;
  }

  @Override
  public void check(CheckStatusRequest request, StreamObserver<CheckStatusResponse> observer) {
    ServingStatus status = statusManager.check();
    observer.onNext(CheckStatusResponse.newBuilder().setStatus(status).build());
    observer.onCompleted();
  }

  @Override
  public void drain(DrainServerRequest request, StreamObserver<DrainServerResponse> observer) {
    drainRequest(request);
    observer.onNext(DrainServerResponse.newBuilder().setAcknowledged(true).build());
    observer.onCompleted();
  }

  private void drainRequest(DrainServerRequest request) {
    Optional<Duration> timeoutOpt =
        request.hasTimeout() ? Optional.of(toJavaDuration(request.getTimeout())) : Optional.empty();
    statusManager.drain(timeoutOpt);
  }
}
