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

package com.google.devtools.mobileharness.fe.v6.service.device.provider;

import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.DeviceOpsStub;

/** No-op implementation of {@link DeviceOpsStubProvider}. */
public final class NoOpDeviceOpsStubProvider implements DeviceOpsStubProvider {
  @Override
  public DeviceOpsStub createStub(String hostName, UniverseScope universe) {
    throw new UnsupportedOperationException("DeviceOps is not supported in OSS");
  }
}
