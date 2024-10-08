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

package com.google.devtools.mobileharness.infra.controller.scheduler.simple;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.JobLocator;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.controller.scheduler.simple.persistence.AllocationPersistenceUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AllocationsTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private AllocationPersistenceUtil allocationPersistenceUtil;

  private static final TestLocator TEST_LOCATOR =
      TestLocator.of("test_id", "test_name", JobLocator.of("job_id", "job_name"));
  private static final DeviceLocator DEVICE_LOCATOR_1 =
      DeviceLocator.of("device_id_1", LabLocator.of("host_ip", "host_name"));
  private static final DeviceLocator DEVICE_LOCATOR_2 =
      DeviceLocator.of("device_id_2", LabLocator.of("host_ip", "host_name"));
  private static final Allocation ALLOCATION =
      new Allocation(TEST_LOCATOR, ImmutableList.of(DEVICE_LOCATOR_1, DEVICE_LOCATOR_2));
  private Allocations allocations;

  @Before
  public void setUp() {
    allocations = new Allocations(allocationPersistenceUtil);
  }

  @Test
  public void initialize() throws MobileHarnessException {
    when(allocationPersistenceUtil.getPersistedAllocations())
        .thenReturn(
            ImmutableList.of(AllocationPersistenceUtil.AllocationOrError.of(ALLOCATION, null)));
    allocations.initialize();
    verify(allocationPersistenceUtil).getPersistedAllocations();
    assertThat(allocations.getAllocationByTest("test_id")).isEqualTo(ALLOCATION);
    assertThat(allocations.getAllocationByDevice("device_id_1@host_ip")).isEqualTo(ALLOCATION);
    assertThat(allocations.getAllocationByDevice("device_id_2@host_ip")).isEqualTo(ALLOCATION);
  }

  @Test
  public void addRemoveGetAllocation() throws MobileHarnessException {
    assertThat(allocations.addAllocation(ALLOCATION)).isTrue();
    verify(allocationPersistenceUtil).persistAllocation(ALLOCATION);

    assertThat(allocations.getAllocationByTest("test_id")).isEqualTo(ALLOCATION);
    assertThat(allocations.getAllocationByTest("test_id_2")).isNull();
    assertThat(allocations.getAllocationByDevice("device_id_1@host_ip")).isEqualTo(ALLOCATION);
    assertThat(allocations.getAllocationByDevice("device_id_2@host_ip")).isEqualTo(ALLOCATION);
    assertThat(allocations.getAllocationByDevice("device_id_3@host_ip")).isNull();

    assertThat(allocations.containsTest("test_id")).isTrue();
    assertThat(allocations.containsTest("test_id_2")).isFalse();
    assertThat(allocations.containsDevice("device_id_1@host_ip")).isTrue();
    assertThat(allocations.containsDevice("device_id_2@host_ip")).isTrue();
    assertThat(allocations.containsDevice("device_id_3@host_ip")).isFalse();

    assertThat(allocations.addAllocation(ALLOCATION)).isFalse();
    assertThat(
            allocations.addAllocation(
                new Allocation(TEST_LOCATOR, ImmutableList.of(DEVICE_LOCATOR_1))))
        .isFalse();

    assertThat(
            allocations.removeAllocation(
                new Allocation(
                    TestLocator.of("test_id_2", "test_name", JobLocator.of("job_id", "job_name")),
                    ImmutableList.of(DEVICE_LOCATOR_1))))
        .isEqualTo(Allocations.RemoveAllocationResult.create(null, ImmutableList.of()));
    assertThat(
            allocations.removeAllocation(
                new Allocation(TEST_LOCATOR, ImmutableList.of(DEVICE_LOCATOR_1))))
        .isEqualTo(Allocations.RemoveAllocationResult.create(null, ImmutableList.of()));

    assertThat(allocations.removeAllocation(ALLOCATION))
        .isEqualTo(
            Allocations.RemoveAllocationResult.create(
                TEST_LOCATOR, ImmutableList.of(DEVICE_LOCATOR_1, DEVICE_LOCATOR_2)));

    verify(allocationPersistenceUtil).removePersistedAllocation("test_id");
  }
}
