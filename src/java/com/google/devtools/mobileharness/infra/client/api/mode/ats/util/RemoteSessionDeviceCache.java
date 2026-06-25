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

package com.google.devtools.mobileharness.infra.client.api.mode.ats.util;

import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.Annotations.AtsResourceFederation;
import com.google.devtools.mobileharness.infra.client.api.mode.remote.LabServerLocator;
import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ResourceFederation;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionDeviceCache;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.LeaseDeviceCacheRequest;
import com.google.devtools.mobileharness.infra.lab.proto.PrepareTestServiceProto.ReleaseDeviceCacheRequest;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.DeviceCacheStub;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.wireless.qa.mobileharness.client.api.util.stub.StubManager;
import javax.inject.Inject;

/** Remote implementation of {@link SessionDeviceCache} using {@link StubManager}. */
public class RemoteSessionDeviceCache implements SessionDeviceCache {

  private final StubManager stubManager;
  private final ResourceFederation resourceFederation;

  @Inject
  RemoteSessionDeviceCache(
      StubManager stubManager, @AtsResourceFederation ResourceFederation resourceFederation) {
    this.stubManager = stubManager;
    this.resourceFederation = resourceFederation;
  }

  @Override
  public void cache(CacheRequest request) throws MobileHarnessException, InterruptedException {
    try {
      DeviceCacheStub stub = resolveDeviceCacheStub(request.labLocator());
      LeaseDeviceCacheRequest protoRequest =
          LeaseDeviceCacheRequest.newBuilder()
              .addAllDeviceControlIds(request.deviceControlIds())
              .setCacheType(request.cacheType())
              .setLeaseDuration(TimeUtils.toProtoDuration(request.timeout()))
              .setLeaseId(request.sessionId())
              .build();
      stub.leaseDeviceCache(protoRequest);
    } catch (RpcExceptionWithErrorId e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_DEVICE_CACHE_LEASE_ERROR, "Failed to lease remote device cache", e);
    }
  }

  @Override
  public void invalidateCache(InvalidateCacheRequest request)
      throws MobileHarnessException, InterruptedException {
    try {
      DeviceCacheStub stub = resolveDeviceCacheStub(request.labLocator());
      ReleaseDeviceCacheRequest protoRequest =
          ReleaseDeviceCacheRequest.newBuilder()
              .addAllDeviceControlIds(request.deviceControlIds())
              .setCacheType(request.cacheType())
              .setLeaseId(request.sessionId())
              .build();
      stub.releaseDeviceCache(protoRequest);
    } catch (RpcExceptionWithErrorId e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_DEVICE_CACHE_RELEASE_ERROR, "Failed to release remote device cache", e);
    }
  }

  private DeviceCacheStub resolveDeviceCacheStub(LabLocator labLocator) {
    return stubManager.getPrepareTestStub(
        LabServerLocator.longRunningLabServer(labLocator), resourceFederation);
  }
}
