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

package com.google.devtools.mobileharness.fe.v6.service.shared.providers;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabInfoStub;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import javax.inject.Inject;

/** Unified implementation of {@link LabInfoProvider} which routes requests based on universe. */
public class LabInfoProviderImpl implements LabInfoProvider {

  private final LabInfoStub defaultLabInfoStub;
  private final RoutedUniverseLabInfoStubFactory routedUniverseLabInfoStubFactory;

  @Inject
  LabInfoProviderImpl(
      LabInfoStub defaultLabInfoStub,
      RoutedUniverseLabInfoStubFactory routedUniverseLabInfoStubFactory) {
    this.defaultLabInfoStub = defaultLabInfoStub;
    this.routedUniverseLabInfoStubFactory = routedUniverseLabInfoStubFactory;
  }

  @Override
  public ListenableFuture<GetLabInfoResponse> getLabInfoAsync(
      GetLabInfoRequest request, UniverseScope universe) {
    return getLabInfoStub(universe).getLabInfoAsync(request);
  }

  private LabInfoStub getLabInfoStub(UniverseScope universe) {
    if (universe instanceof UniverseScope.RoutedUniverse routed) {
      return routedUniverseLabInfoStubFactory
          .getLabInfoStub(routed.atsControllerId())
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      String.format(
                          "No LabInfo stub available for routed universe '%s'",
                          routed.atsControllerId())));
    }
    return defaultLabInfoStub;
  }
}
