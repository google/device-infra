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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator;

import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.proto.Error.ExceptionDetail;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import java.util.List;
import java.util.Optional;

/** For allocating devices for tests. */
public interface DeviceAllocator {
  /**
   * Sets up this device allocator.
   *
   * @return exception detail if the allocation request need to be fail-fast
   */
  Optional<ExceptionDetail> setUp() throws MobileHarnessException, InterruptedException;

  /**
   * Allocates devices for tests in the given job.
   *
   * @return the device allocations, or an empty list if there is no allocation
   */
  List<AllocationWithStats> pollAllocations() throws MobileHarnessException, InterruptedException;

  /**
   * Releases the allocation.
   *
   * @param deviceDirty whether to leave the devices of this allocation as DIRTY
   */
  void releaseAllocation(Allocation allocation, TestResult testResult, boolean deviceDirty)
      throws MobileHarnessException, InterruptedException;

  /**
   * Notifies the allocator to request extra allocation for retried or newly generated test after
   * the allocator is {@link #setUp()}.
   */
  void extraAllocation(TestInfo testInfo) throws MobileHarnessException, InterruptedException;

  /**
   * Tears down this device allocator and does the clean up.
   *
   * <p>NOTE: This method should NOT assume that {@link #releaseAllocation} of all allocations have
   * been invoked when this method is invoked. It should NOT assume that all {@link
   * #releaseAllocation} will be invoked eventually either. Therefore please remember to do all
   * clean up work which {@link #releaseAllocation} of all allocations should do in this method.
   */
  void tearDown() throws MobileHarnessException, InterruptedException;
}
