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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.dimension.ValueComparator;
import com.google.devtools.mobileharness.shared.util.sharedpool.SharedPoolJobUtil;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DimensionFilter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * For pre-filtering the device candidates. For the devices that are excluded by this filter, it
 * won't be included in the allocation diagnostic report of the jobs.
 */
public final class DeviceFilter {
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

  public static DeviceQueryFilter getFilter(JobScheduleUnit job) {
    return getFilter(job, ImmutableList.of(), job.subDeviceSpecs().getAllSubDevices().get(0));
  }

  /**
   * Gets the {@code DeviceQueryFilter} to pre-filter the device candidates for diagnosing the
   * allocation failures of a job.
   */
  public static DeviceQueryFilter getFilter(
      JobScheduleUnit job, List<FilterType> filterTypes, SubDeviceSpec subDeviceSpec) {
    DeviceQueryFilter.Builder filter =
        DeviceQueryFilter.newBuilder().addTypeRegex(subDeviceSpec.type());
    if (isUsingSharedPool(job, subDeviceSpec)) {
      filter.addDimensionFilter(
          DimensionFilter.newBuilder()
              .setName(Ascii.toLowerCase(Name.POOL.name()))
              .setValueRegex(Value.POOL_SHARED));
    }
    filterTypes.forEach(diagnosticFilter -> {
      switch (diagnosticFilter) {
        case ACCESS -> filter.addOwnerRegex("public|" + job.jobUser().getRunAs());
        case DECORATOR -> filter.addAllDecoratorRegex(subDeviceSpec.decorators().getAll());
        case DIMENSION ->
            subDeviceSpec
                .dimensions()
                .getAll()
                .forEach((name, value) ->
                    convertForDeviceQueryApi(value)
                        .ifPresent(valueRegex ->
                            filter.addDimensionFilter(
                                DimensionFilter.newBuilder()
                                    .setName(name)
                                    .setValueRegex(valueRegex))));
        case DRIVER -> filter.addDriverRegex(job.type().getDriver());
        case STATUS -> filter.addStatus("idle");
      }
    });
    ImmutableList<DimensionFilter> dedupedDimensionFilters =
        filter.getDimensionFilterList().stream()
            .distinct()
            .sorted(
                Comparator.comparing(DimensionFilter::getName)
                    .thenComparing(DimensionFilter::getValueRegex))
            .collect(toImmutableList());
    return filter.clearDimensionFilter().addAllDimensionFilter(dedupedDimensionFilters).build();
  }

  /** Gets the filter from the device dimensions. */
  public static DeviceQueryFilter getFilter(Map<String, String> dimensions) {
    DeviceQueryFilter.Builder filter = DeviceQueryFilter.newBuilder();
    dimensions.forEach((key, value) ->
        filter.addDimensionFilter(DimensionFilter.newBuilder().setName(key).setValueRegex(value)));
    return filter.build();
  }

  private static boolean isUsingSharedPool(JobScheduleUnit job, SubDeviceSpec subDeviceSpec) {
    String pool = subDeviceSpec.dimensions().get(Name.POOL);
    if (pool != null) {
      return pool.equals(Value.POOL_SHARED);
    }
    SubDeviceSpecs subDeviceSpecs = job.subDeviceSpecs();
    return (subDeviceSpecs == null || subDeviceSpecs.getAllSubDevices().size() <= 1)
        && SharedPoolJobUtil.isUsingSharedPool(job);
  }

  /**
   * Converts a dimension value in {@link JobScheduleUnit} to a value regex in {@link
   * DimensionFilter}.
   *
   * <ol>
   *   <li>If the value is "{@code *}", returns "{@code .*}";
   *   <li>Omits "{@code exclude}", "{@code not_regex:}", "{@code optional:}", "{@code
   *       compare_int:}", and "{@code compare_str:}" values because they are not supported by the
   *       device query API;
   *   <li>Removes the "{@code regex:}" prefix.
   * </ol>
   */
  private static Optional<String> convertForDeviceQueryApi(String jobDimensionValue) {
    switch (jobDimensionValue) {
      case Value.EXCLUDE -> {
        return Optional.empty();
      }
      case Value.ALL_VALUE_FOR_DEVICE -> {
        return Optional.of(".*");
      }
      default -> {
        if (jobDimensionValue.startsWith(Value.PREFIX_REGEX)) {
          return Optional.of(jobDimensionValue.substring(Value.PREFIX_REGEX.length()));
        } else if (jobDimensionValue.startsWith(Value.PREFIX_NOT_REGEX)
            || jobDimensionValue.startsWith(Value.PREFIX_OPTIONAL)
            || ValueComparator.isComparison(jobDimensionValue)) {
          return Optional.empty();
        } else {
          return Optional.of(jobDimensionValue);
        }
      }
    }
  }

  private DeviceFilter() {}
}
