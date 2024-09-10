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

package com.google.devtools.mobileharness.infra.controller.scheduler;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.lab.DeviceScheduleUnit;
import com.google.devtools.mobileharness.shared.util.algorithm.GraphMatching;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Utility for scheduling multiple devices in a single allocation. */
public class AdhocTestbedSchedulingUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Finds a subset of devices that supports given type/dimension specs.
   *
   * @param devicePool Collection of DeviceScheduleUnits which are currently unallocated
   * @param job JobScheduleUnit defining the requirements of the job
   * @return a List of DeviceScheduleUnits that support the requirements in subDeviceSpecs, or an
   *     empty list if there is no subset of devicePool that can support the requirements. The order
   *     of the returned list corresponds to the order of specifications in subDeviceSpec.
   */
  public ImmutableList<DeviceScheduleUnit> findSubDevicesSupportingJob(
      Collection<DeviceScheduleUnit> devicePool, JobScheduleUnit job) {
    // Checks if there are enough devices.
    List<SubDeviceSpec> subDeviceSpecs = job.subDeviceSpecs().getAllSubDevices();
    if (devicePool.size() < subDeviceSpecs.size()) {
      logger.atFine().log(
          "%s",
          String.format(
              "Not enough idle devices (%d) of matching type to support subdevice specs: \n%s",
              devicePool.size(), job.subDeviceSpecs()));
      return ImmutableList.of();
    }

    // Shuffles device list.
    List<DeviceScheduleUnit> devices;
    if (Flags.instance().enableSimpleSchedulerShuffle.getNonNull()) {
      devices = new ArrayList<>(devicePool);
      Collections.shuffle(devices);
    } else {
      devices = ImmutableList.copyOf(devicePool);
    }

    // Calculates supported devices graph.
    ImmutableMultimap.Builder<SubDeviceSpec, DeviceScheduleUnit> supportedDevices =
        ImmutableMultimap.builder();
    for (SubDeviceSpec subDeviceSpec : subDeviceSpecs) {
      for (DeviceScheduleUnit device : devices) {
        if (subDeviceSupportsSpec(device, subDeviceSpec)) {
          supportedDevices.put(subDeviceSpec, device);
        }
      }
    }

    // Calculates maximum cardinality bipartite graph matching.
    ImmutableBiMap<SubDeviceSpec, DeviceScheduleUnit> matchingResult =
        GraphMatching.maximumCardinalityBipartiteMatching(supportedDevices.build());

    // Generates allocation.
    if (matchingResult.size() == subDeviceSpecs.size()) {
      return subDeviceSpecs.stream().map(matchingResult::get).collect(toImmutableList());
    } else {
      return ImmutableList.of();
    }
  }

  /** Checks whether the {@code subDevice} supports the type and dimensions in the {@code spec}. */
  private static boolean subDeviceSupportsSpec(DeviceScheduleUnit subDevice, SubDeviceSpec spec) {
    return subDevice.types().support(spec.type())
        && subDevice.dimensions().supportAndSatisfied(spec.dimensions().getAll())
        && subDevice.decorators().support(spec.decorators().getAll());
  }
}
