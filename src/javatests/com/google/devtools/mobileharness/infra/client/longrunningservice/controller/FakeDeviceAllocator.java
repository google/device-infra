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

package com.google.devtools.mobileharness.infra.client.longrunningservice.controller;

import com.google.common.collect.ImmutableList;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.AbstractDeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.AllocationWithStats;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;

public class FakeDeviceAllocator extends AbstractDeviceAllocator {

  private final LinkedBlockingQueue<Allocation> pendingAllocations = new LinkedBlockingQueue<>();

  public FakeDeviceAllocator(JobInfo jobInfo) {
    super(jobInfo);
  }

  public void addPendingAllocation(Allocation allocation) {
    pendingAllocations.add(allocation);
  }

  @Override
  public ImmutableList<AllocationWithStats> pollAllocations() {
    if (!pendingAllocations.isEmpty()) {
      Allocation allocation = pendingAllocations.poll();
      return ImmutableList.of(new AllocationWithStats(allocation));
    }
    return ImmutableList.of();
  }

  @Override
  public void extraAllocation(TestInfo testInfo) {}

  @Override
  public Optional<ExceptionDetail> setUp() {
    return Optional.empty();
  }

  @Override
  public void tearDown() {}

  @Override
  public void releaseAllocation(
      Allocation allocation, TestResult testResult, boolean deviceDirty) {}

  @Override
  public boolean isLocal() {
    return true;
  }
}
