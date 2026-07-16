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
import java.util.Optional;

/**
 * Provides the running test/job information for a BUSY device.
 *
 * <p>The canonical device→running-test mapping lives in MOSS (test result store). The internal
 * implementation queries MOSS {@code getTestDetails} by device serial + RUNNING status. The OSS
 * implementation is a no-op (returns empty).
 *
 * <p>This interface lives in third_party (OSS-exportable) so {@link
 * com.google.devtools.mobileharness.fe.v6.service.device.handlers.HealthAndActivityBuilder} can
 * depend on it without pulling in MOSS (google3-only).
 */
public interface RunningTestInfoProvider {

  /** Minimal info about the test/job currently running on a device. */
  record RunningTestInfo(String testId, String jobId) {}

  /**
   * Looks up the currently running test on the given device.
   *
   * @param deviceSerial the device UUID/serial
   * @return a future resolving to the running test info, or empty if none found or unavailable
   */
  ListenableFuture<Optional<RunningTestInfo>> getRunningTest(String deviceSerial);
}
