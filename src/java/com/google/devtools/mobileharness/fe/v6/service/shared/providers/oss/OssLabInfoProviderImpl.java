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

package com.google.devtools.mobileharness.fe.v6.service.shared.providers.oss;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabInfoStub;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import javax.inject.Inject;

/** OSS implementation of {@link LabInfoProvider}. */
public class OssLabInfoProviderImpl implements LabInfoProvider {
  private final LabInfoStub labInfoStub;

  @Inject
  OssLabInfoProviderImpl(LabInfoStub labInfoStub) {
    this.labInfoStub = labInfoStub;
  }

  @Override
  public ListenableFuture<GetLabInfoResponse> getLabInfoAsync(
      GetLabInfoRequest request, String universe) {
    // OSS implementation ignores universe and calls the single injected stub.
    return labInfoStub.getLabInfoAsync(request);
  }
}
