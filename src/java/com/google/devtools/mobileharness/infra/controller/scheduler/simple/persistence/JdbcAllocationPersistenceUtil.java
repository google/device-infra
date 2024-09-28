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

package com.google.devtools.mobileharness.infra.controller.scheduler.simple.persistence;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.scheduler.simple.persistence.Annotations.SchedulerDatabaseConnections;
import com.google.devtools.mobileharness.service.moss.proto.Slg.AllocationProto;
import com.google.devtools.mobileharness.service.moss.util.slg.AllocationConverter;
import com.google.devtools.mobileharness.shared.util.database.DatabaseConnections;
import com.google.devtools.mobileharness.shared.util.database.TableUtil.StringBinaryProtoOrError;
import com.google.devtools.mobileharness.shared.util.database.TableUtil.StringBinaryProtoTable;
import javax.inject.Inject;

/** JDBC implementation of {@link AllocationPersistenceUtil}. */
public class JdbcAllocationPersistenceUtil implements AllocationPersistenceUtil {

  private final StringBinaryProtoTable<AllocationProto> table;

  @Inject
  JdbcAllocationPersistenceUtil(
      @SchedulerDatabaseConnections DatabaseConnections schedulerDatabaseConnections) {
    this.table =
        new StringBinaryProtoTable<>(
            schedulerDatabaseConnections,
            AllocationProto.parser(),
            "test_allocations",
            "test_id",
            "test_allocation");
  }

  @Override
  public void persistAllocation(Allocation allocation) throws MobileHarnessException {
    AllocationProto allocationProto = AllocationConverter.toProto(allocation);
    table.update(allocation.getTest().id(), allocationProto);
  }

  @Override
  public void removePersistedAllocation(String testId) throws MobileHarnessException {
    table.delete(testId);
  }

  @Override
  public ImmutableList<AllocationOrError> getPersistedAllocations() throws MobileHarnessException {
    ImmutableList<StringBinaryProtoOrError> result = table.read();

    return result.stream()
        .map(
            element -> {
              if (element.error().isPresent()) {
                return AllocationOrError.of(/* allocation= */ null, element.error().get());
              } else {
                AllocationProto allocationProto = (AllocationProto) element.value().orElseThrow();
                Allocation allocation = AllocationConverter.toNewAllocation(allocationProto);
                return AllocationOrError.of(/* allocation= */ allocation, /* error= */ null);
              }
            })
        .collect(toImmutableList());
  }
}
