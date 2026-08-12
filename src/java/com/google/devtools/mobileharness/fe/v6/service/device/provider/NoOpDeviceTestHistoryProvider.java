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
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceTestHistoryResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import javax.inject.Singleton;

/**
 * OSS implementation of {@link DeviceTestHistoryProvider}.
 *
 * <p>Returns an empty table. In ATS the test history comes from the local controller rather than
 * MOSS, and the Device Detail test history tab is an internal feature.
 */
@Singleton
public final class NoOpDeviceTestHistoryProvider implements DeviceTestHistoryProvider {

  @Override
  public ListenableFuture<GetDeviceTestHistoryResponse> getDeviceTestHistory(
      String deviceId, String pageToken, UniverseScope universe) {
    return immediateFuture(GetDeviceTestHistoryResponse.getDefaultInstance());
  }
}
