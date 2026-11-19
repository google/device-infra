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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceEventsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceEventsResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;

/**
 * Loads the historical events for a device, backing the Device Detail "Device Events" tab.
 *
 * <p>This is the environment-specific seam of {@code GetDeviceEvents}. The internal implementation
 * combines system field transitions, manual recovery records from Arsenal Spanner, and inventory
 * notes; the OSS implementation returns an empty response.
 */
public interface DeviceEventsProvider {

  /**
   * Returns the device's historical events within the requested lookback window.
   *
   * @param request the request specifying device ID, optional host name, and window days
   * @param universe the universe the device belongs to
   */
  ListenableFuture<GetDeviceEventsResponse> getDeviceEvents(
      GetDeviceEventsRequest request, UniverseScope universe);
}
