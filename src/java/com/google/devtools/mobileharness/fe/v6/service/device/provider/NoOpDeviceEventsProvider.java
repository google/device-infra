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

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceEventsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceEventsResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import javax.inject.Singleton;

/**
 * Default no-op implementation of {@link DeviceEventsProvider}.
 *
 * <p>Returns an empty event list in environments where historical event stores are not connected.
 */
@Singleton
public final class NoOpDeviceEventsProvider implements DeviceEventsProvider {

  @Override
  public ListenableFuture<GetDeviceEventsResponse> getDeviceEvents(
      GetDeviceEventsRequest request, UniverseScope universe) {
    return immediateFuture(GetDeviceEventsResponse.getDefaultInstance());
  }
}
