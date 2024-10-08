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

package com.google.devtools.mobileharness.service.moss.util.slg;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.service.moss.proto.Slg.AllocationProto;
import com.google.devtools.mobileharness.service.moss.proto.Slg.AllocationProto.DimensionEntry;
import com.google.devtools.mobileharness.service.moss.proto.Slg.AllocationProto.DimensionMultiMap;
import com.google.wireless.qa.mobileharness.shared.model.allocation.AllocationInternalFactory;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class to convert a {@link Allocation} to a {@link AllocationProto} in forward and
 * backward.
 */
public final class AllocationConverter {

  private AllocationConverter() {}

  /** Gets a {@link Allocation} by the given {@link AllocationProto}. */
  public static Allocation toNewAllocation(AllocationProto allocationProto) {
    return fromProto(allocationProto).toNewAllocation();
  }

  /**
   * Gets a {@linkplain com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation
   * Allocation} by the given {@link AllocationProto}.
   */
  public static com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation fromProto(
      AllocationProto allocationProto) {
    return AllocationInternalFactory.createAllocation(allocationProto);
  }

  /** Gets a {@link AllocationProto} by the given {@link Allocation}. */
  public static AllocationProto toProto(Allocation allocation) {
    return AllocationProto.newBuilder()
        .setTestLocator(allocation.getTest().toProto())
        .addAllDeviceLocator(
            allocation.getAllDevices().stream()
                .map(DeviceLocator::toProto)
                .collect(toImmutableList()))
        .build();
  }

  /**
   * Gets a {@link AllocationProto} by the given {@linkplain
   * com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation Allocation}.
   */
  public static AllocationProto toProto(
      com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation allocation) {
    AllocationProto.Builder allocationProto =
        AllocationProto.newBuilder()
            .setTestLocator(allocation.getTest().toNewTestLocator().toProto());
    ImmutableList<com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator>
        deviceLocators = allocation.getAllDeviceLocators();
    Map<
            com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator,
            Multimap<String, String>>
        allDevices = allocation.getAllDevices();
    deviceLocators.forEach(
        deviceLocator -> {
          Multimap<String, String> dimensions = allDevices.get(deviceLocator);
          allocationProto.addDeviceLocator(deviceLocator.toNewDeviceLocator().toProto());
          allocationProto.addDimensionMultiMap(
              DimensionMultiMap.newBuilder()
                  .addAllDimensionEntry(
                      dimensions.asMap().entrySet().stream()
                          .map(
                              entry ->
                                  DimensionEntry.newBuilder()
                                      .setKey(entry.getKey())
                                      .addAllValue(entry.getValue())
                                      .build())
                          .collect(Collectors.toList())));
        });
    return allocationProto.build();
  }
}
