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

import com.google.common.collect.Collections2;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.lab.DeviceScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Utility for scheduling multiple devices in a single allocation. */
public class AdhocTestbedSchedulingUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration FIND_SUBDEVICES_TIMEOUT = Duration.ofSeconds(10);

  private final ExecutorService executor = Executors.newCachedThreadPool();

  public AdhocTestbedSchedulingUtil() {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    executor.shutdownNow();
                  } catch (Throwable e) {
                    logger.atWarning().withCause(e).log(
                        "Failed to shutdown ad hoc testbed scheduling executors");
                  }
                }));
  }

  /**
   * Finds a subset of devices that supports given type/dimension specs.
   *
   * @param devicePool Collection of DeviceScheduleUnits which are currently unallocated..
   * @param job JobScheduleUnit defining the requirements of the job
   * @return a List of DeviceScheduleUnits that support the requirements in subDeviceSpecs, or an
   *     empty list if there is no subset of devicePool that can support the requirements. The order
   *     of the returned list corresponds to the order of specifications in subDeviceSpec.
   */
  public List<DeviceScheduleUnit> findSubDevicesSupportingJob(
      Collection<DeviceScheduleUnit> devicePool, JobScheduleUnit job) throws InterruptedException {
    // Check first if there are even enough devices.
    List<SubDeviceSpec> subDeviceSpecList = job.subDeviceSpecs().getAllSubDevices();

    if (devicePool.size() < subDeviceSpecList.size()) {
      logger.atFine().log(
          "%s",
          String.format(
              "Not enough idle devices (%d) of matching type to support subdevice specs: \n%s",
              devicePool.size(), job.subDeviceSpecs()));
      return new ArrayList<>();
    }

    // Create a device list to structure the spec matching process and an index set to permute in
    // order to match subdevice specs to subsets of the unallocated devices list.
    List<DeviceScheduleUnit> devicePoolList = new ArrayList<>(devicePool);
    // Because the number of subdevice specs (S) is less than the number of devices (D), we iterate
    // through the (fewer) permutations of specs (S! < D!). However, because the order of subdevices
    // in the testbed should be preserved (e.g., a multidevice driver may assume the first device
    // has specific dimensions) we permute a list of indices instead of the actual
    // subDeviceSpecList, and match that permutation against sublists of the device list.
    ContiguousSet<Integer> specIndices =
        ContiguousSet.create(
            Range.closedOpen(0, subDeviceSpecList.size()), DiscreteDomain.integers());
    // Because this requires iterating through permutations of possibly many specs, set a timeout.
    final Future<List<DeviceScheduleUnit>> future =
        executor.submit(
            () -> {
              Map<Map.Entry<DeviceScheduleUnit, SubDeviceSpec>, Boolean>
                  subDeviceSupportsSpecCache = new HashMap<>();
              for (List<Integer> permutedSpecIndices : Collections2.permutations(specIndices)) {
                // For each permutation of spec indices, slide "current" along the list of devices
                // and if the "current" device matches the first unmatched spec, add that device
                // to subDeviceIndices and continue with the next unmatched spec.
                int current = 0;
                List<Integer> subDeviceIndices = new ArrayList<>();
                for (int i = 0; i < permutedSpecIndices.size(); i++) {
                  // Keep going while number of unmatched specs is not more than the number of
                  // remaining devices.
                  while (permutedSpecIndices.size() - i <= devicePoolList.size() - current) {
                    // Because checking if a device matches a spec is costly and may happen again,
                    // cache the results.
                    Map.Entry<DeviceScheduleUnit, SubDeviceSpec> key =
                        Map.entry(
                            devicePoolList.get(current),
                            subDeviceSpecList.get(permutedSpecIndices.get(i).intValue()));
                    subDeviceSupportsSpecCache.computeIfAbsent(
                        key, k -> subDeviceSupportsSpec(k.getKey(), k.getValue()));
                    if (subDeviceSupportsSpecCache.get(key)) {
                      // Found a match! Move onto the next spec and next device.
                      subDeviceIndices.add(current);
                      current++;
                      break;
                    }
                    // Device doesn't match, keep going.
                    current++;
                  }
                }
                if (subDeviceIndices.size() != permutedSpecIndices.size()) {
                  continue;
                }
                List<DeviceScheduleUnit> subDeviceList = new ArrayList<>();
                for (int i = 0; i < subDeviceIndices.size(); i++) {
                  // Find the next permuted index
                  int j = permutedSpecIndices.indexOf(i);
                  // Add the subdevice
                  subDeviceList.add(devicePoolList.get(subDeviceIndices.get(j)));
                }
                return subDeviceList;
              }
              return new ArrayList<>();
            });

    try {
      return future.get(FIND_SUBDEVICES_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      logger.atWarning().withCause(e).log("Search for a set of devices timed out");
    } catch (ExecutionException | CancellationException e) {
      logger.atWarning().withCause(e).log("Error while searching for a set of devices");
    } finally {
      future.cancel(true);
    }
    return new ArrayList<>();
  }

  /** Checks whether the {@code subDevice} supports the type and dimensions in the {@code spec}. */
  private static final boolean subDeviceSupportsSpec(
      DeviceScheduleUnit subDevice, SubDeviceSpec spec) {
    return subDevice.types().support(spec.type())
        && subDevice.dimensions().supportAndSatisfied(spec.dimensions().getAll())
        && subDevice.decorators().support(spec.decorators().getAll());
  }
}
