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
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.Annotations.AtsResourceFederation;
import com.google.devtools.mobileharness.infra.client.api.mode.remote.LabServerLocator;
import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ResourceFederation;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.DeviceCacheStub;
import com.google.wireless.qa.mobileharness.client.api.util.stub.StubManager;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Resolver for resolving {@link DeviceCacheStub} by {@link LabLocator}. */
@Singleton
public class DeviceCacheStubResolver {

  private final StubManager stubManager;
  private final ResourceFederation resourceFederation;

  @Inject
  DeviceCacheStubResolver(
      StubManager stubManager, @AtsResourceFederation ResourceFederation resourceFederation) {
    this.stubManager = stubManager;
    this.resourceFederation = resourceFederation;
  }

  /** Resolves the {@link DeviceCacheStub} for the given lab. */
  public DeviceCacheStub resolveDeviceCacheStub(LabLocator labLocator)
      throws RpcExceptionWithErrorId {
    return stubManager.getPrepareTestStub(
        LabServerLocator.longRunningLabServer(labLocator), resourceFederation);
  }
}
