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
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceTestHistoryResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;

/**
 * Loads the historical tests that ran on a device, for the Device Detail "Test history" tab.
 *
 * <p>This is the environment-specific seam of {@code GetDeviceTestHistory}. The internal
 * implementation queries MOSS for the device's tests and maps them into the generic result table
 * (columns + rows); the OSS implementation returns an empty table, because MOSS is not reachable
 * from ATS (tests there live in the local controller).
 */
public interface DeviceTestHistoryProvider {

  /**
   * Returns one page of the device's tests, newest first.
   *
   * @param deviceId the device whose tests are listed
   * @param hostName optional host name; when non-empty, restricts the tests to this host,
   *     disambiguating a device id that appears on more than one host
   * @param pageToken opaque cursor from a previous response; empty for the first page
   * @param universe the universe the device belongs to
   */
  ListenableFuture<GetDeviceTestHistoryResponse> getDeviceTestHistory(
      String deviceId, String hostName, String pageToken, UniverseScope universe);
}
