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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.proto.Error.ExceptionDetail;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import java.util.Optional;

/** Skeletal implementation for a DeviceAllocator. */
public abstract class AbstractDeviceAllocator implements DeviceAllocator {
  protected final JobInfo jobInfo;

  protected AbstractDeviceAllocator(JobInfo jobInfo) {
    this.jobInfo = checkNotNull(jobInfo);
  }

  /** Sets up this device allocator. */
  @Override
  public Optional<ExceptionDetail> setUp() throws MobileHarnessException, InterruptedException {
    return Optional.empty();
  }

  /**
   * Releases the allocation.
   *
   * @param deviceDirty whether to leave the devices of this allocation as DIRTY
   */
  @Override
  public void releaseAllocation(Allocation allocation, TestResult testResult, boolean deviceDirty)
      throws MobileHarnessException, InterruptedException {}

  /**
   * Notifies the allocator to request extra allocation for retried or newly generated test after
   * the allocator is {@link #setUp()}.
   */
  @Override
  public void extraAllocation(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {}

  /** Tears down this device allocator and does the clean up. */
  @Override
  public void tearDown() throws MobileHarnessException, InterruptedException {}
}
