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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.sharedpool.SharedPoolJobUtil;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DimensionFilter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * For pre-filtering the device candidates. For the devices that are excluded by this filter, it
 * won't be included in the allocation diagnostic report of the jobs.
 */
public class DeviceFilter {
  /**
   * Diagnostic candidate filter to remove device candidates that do not match the field with the
   * given job from the diagnostic report.
   */
  public enum FilterType {
    ACCESS,
    DECORATOR,
    DIMENSION,
    DRIVER,
    STATUS,
  }

  public DeviceQueryFilter getFilter(JobScheduleUnit job) {
    return getFilter(job, ImmutableList.of());
  }

  /**
   * Gets the filters to pre-filtering the device candidates for diagnostic the allocation failures
   * of a job.
   */
  public DeviceQueryFilter getFilter(JobScheduleUnit job, List<FilterType> filterTypes) {
    DeviceQueryFilter.Builder filter = DeviceQueryFilter.newBuilder();
    filter.addTypeRegex(job.type().getDevice());
    if (SharedPoolJobUtil.isUsingSharedPool(job)) {
      filter.addDimensionFilter(
          DimensionFilter.newBuilder()
              .setName(Ascii.toLowerCase(Name.POOL.name()))
              .setValueRegex(Value.POOL_SHARED));
    }
    filterTypes.forEach(
        diagnosticFilter -> {
          switch (diagnosticFilter) {
            case ACCESS:
              filter.addOwnerRegex("public|" + job.jobUser().getRunAs());
              break;
            case DECORATOR:
              filter.addAllDecoratorRegex(job.type().getDecoratorList());
              break;
            case DIMENSION:
              job.dimensions()
                  .getAll()
                  .forEach(
                      (name, value) ->
                          convertForDeviceQueryApi(value)
                              .ifPresent(
                                  valueRegex ->
                                      filter.addDimensionFilter(
                                          DimensionFilter.newBuilder()
                                              .setName(name)
                                              .setValueRegex(valueRegex))));
              break;
            case DRIVER:
              filter.addDriverRegex(job.type().getDriver());
              break;
            case STATUS:
              filter.addStatus("idle");
              break;
            default:
              break;
          }
        });
    List<DimensionFilter> dedupedDimensionFilters =
        filter.getDimensionFilterList().stream().distinct().collect(Collectors.toList());
    filter.clearDimensionFilter().addAllDimensionFilter(dedupedDimensionFilters);
    return filter.build();
  }

  /** Gets the filter from the device dimensions. */
  public DeviceQueryFilter getFilter(Map<String, String> dimensions) {
    DeviceQueryFilter.Builder filter = DeviceQueryFilter.newBuilder();
    dimensions
        .entrySet()
        .forEach(
            entry ->
                filter.addDimensionFilter(
                    DimensionFilter.newBuilder()
                        .setName(entry.getKey())
                        .setValueRegex(entry.getValue())));
    return filter.build();
  }

  /**
   * Converts a dimension value in {@link JobScheduleUnit} to a value regex in {@link
   * DimensionFilter}.
   *
   * <ol>
   *   <li>If the value is "<code>*</code>", return "<code>.*</code>";
   *   <li>Omit the value "<code>exclude</code>" because it is not supported by device query API;
   *   <li>Remove the "<code>regex:</code>" prefix.
   * </ol>
   */
  private Optional<String> convertForDeviceQueryApi(String jobDimensionValue) {
    switch (jobDimensionValue) {
      case Value.EXCLUDE:
        return Optional.empty();
      case Value.ALL_VALUE_FOR_DEVICE:
        return Optional.of(".*");
      default:
        if (jobDimensionValue.startsWith(Value.PREFIX_REGEX)) {
          return Optional.of(jobDimensionValue.substring(Value.PREFIX_REGEX.length()));
        } else {
          return Optional.of(jobDimensionValue);
        }
    }
  }
}
