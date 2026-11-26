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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceErrorsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceErrorsResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetDeviceErrors RPC. */
@Singleton
public final class GetDeviceErrorsHandler {

  private final LabInfoProvider labInfoProvider;
  private final ListeningExecutorService executor;

  @Inject
  GetDeviceErrorsHandler(LabInfoProvider labInfoProvider, ListeningExecutorService executor) {
    this.labInfoProvider = labInfoProvider;
    this.executor = executor;
  }

  /**
   * Gets the errors that the lab reported for the device on the given host.
   *
   * <p>Both {@code id} and {@code host_name} are required. Fails with {@code INVALID_ARGUMENT} if
   * either is empty.
   */
  public ListenableFuture<GetDeviceErrorsResponse> getDeviceErrors(
      GetDeviceErrorsRequest request, UniverseScope universe) {
    if (request.getId().isEmpty() || request.getHostName().isEmpty()) {
      return Futures.immediateFailedFuture(
          FeServiceException.invalidArgument(
              String.format(
                  "Device ID and host name are required, id=[%s], host_name=[%s]",
                  request.getId(), request.getHostName())));
    }
    LabInfoServiceProto.GetDeviceErrorsRequest labInfoRequest =
        LabInfoServiceProto.GetDeviceErrorsRequest.newBuilder()
            .setDeviceId(request.getId())
            .setHostName(request.getHostName())
            .build();
    return Futures.transform(
        labInfoProvider.getDeviceErrorsAsync(labInfoRequest, universe),
        response ->
            GetDeviceErrorsResponse.newBuilder()
                .addAllDeviceErrors(response.getDeviceErrorsList())
                .build(),
        executor);
  }
}
