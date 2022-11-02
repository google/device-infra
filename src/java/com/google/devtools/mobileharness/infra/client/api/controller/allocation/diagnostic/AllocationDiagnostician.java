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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic;

import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import java.util.Optional;

/** AllocationDiagnostician provides a report for identifying the cause of an allocation failure. */
public interface AllocationDiagnostician {

  /**
   * Generates a {@link Report} on why the job failed to allocate devices. This may be invoked
   * multiple times while the job is waiting for device allocation. The worst report generated
   * during this time will be surfaced.
   *
   * @param noPerfectCandidate whether there's perfect candidates.
   */
  Report diagnoseJob(boolean noPerfectCandidate)
      throws MobileHarnessException, InterruptedException;

  /** Returns the report from the previous call to {@link #diagnoseJob()} if there was one. */
  Optional<Report> getLastReport();

  void logExtraInfo();
}
