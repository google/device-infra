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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.util.Optional;
import javax.annotation.Nullable;

/** Util to persist/resume allocations. */
public interface AllocationPersistenceUtil {

  /** Persists the allocation. */
  void persistAllocation(Allocation allocation) throws MobileHarnessException;

  /** Removes the allocation for the given test from the persistence data. */
  void removePersistedAllocation(String testId) throws MobileHarnessException;

  /** Gets all the persisted allocations. */
  ImmutableList<AllocationOrError> getPersistedAllocations() throws MobileHarnessException;

  /** Allocation or error. One and only one is present. */
  @AutoValue
  abstract class AllocationOrError {

    public abstract Optional<Allocation> allocation();

    public abstract Optional<Throwable> error();

    public static AllocationOrError of(@Nullable Allocation allocation, @Nullable Throwable error) {
      checkArgument((allocation != null && error == null) || (allocation == null && error != null));
      return new AutoValue_AllocationPersistenceUtil_AllocationOrError(
          Optional.ofNullable(allocation), Optional.ofNullable(error));
    }
  }

  /** No-op implementation of {@link AllocationPersistenceUtil}. */
  final class NoOpAllocationPersistenceUtil implements AllocationPersistenceUtil {
    @Override
    public void removePersistedAllocation(String testId) {}

    @Override
    public void persistAllocation(Allocation allocation) {}

    @Override
    public ImmutableList<AllocationOrError> getPersistedAllocations() {
      return ImmutableList.of();
    }
  }
}
