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

package com.google.devtools.mobileharness.api.model.lab.in;

import com.google.common.annotations.Beta;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.shared.util.dimension.ValueComparator;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/** Device supported dimensions and required dimensions. */
@Beta
public class CompositeDimensions {

  private final Dimensions supported;
  private final Dimensions required;

  /** Do <b>not</b> make it public. Please use {@link LiteDimensionsFactory} instead. */
  CompositeDimensions(Dimensions supported, Dimensions required) {
    this.supported = supported;
    this.required = required;
  }

  /** Supported dimensions of the device. */
  public Dimensions supported() {
    return supported;
  }

  /** Required dimensions of the device. See go/mh-required-dimensions for more detail. */
  public Dimensions required() {
    return required;
  }

  /** Equivalent to <tt>(required ? {@link #required()} : {@link #supported()})</tt>. */
  public Dimensions value(boolean required) {
    return required ? required() : supported();
  }

  /** Coverts to proto. */
  public DeviceCompositeDimension toProto() {
    return DeviceCompositeDimension.newBuilder()
        .addAllSupportedDimension(supported.toProtos())
        .addAllRequiredDimension(required.toProtos())
        .build();
  }

  /** Adds all dimensions from the proto. */
  @CanIgnoreReturnValue
  public CompositeDimensions addAll(DeviceCompositeDimension proto) {
    supported.addAll(proto.getSupportedDimensionList());
    required.addAll(proto.getRequiredDimensionList());
    return this;
  }

  /**
   * Checks whether the given job dimensions is supported by this device, and the device required
   * dimensions are also satisfied by the job dimensions.
   */
  public boolean supportAndSatisfied(Map<String, String> jobDimensions) {
    return getUnsupportedJobDimensions(jobDimensions, true /*failFast*/).isEmpty()
        && getUnsatisfiedDeviceDimensions(jobDimensions, true /*failFast*/).isEmpty();
  }

  /**
   * Checks the given job dimensions and returns the ones that are not supported by this device.
   *
   * @param failFast true to return the result immediately when one pair of unsupported job
   *     dimensions are found, false to search and return all unsupported job dimensions
   */
  public Map<String, String> getUnsupportedJobDimensions(
      Map<String, String> jobDimensions, boolean failFast) {
    Map<String, String> unsupportedJobDimensions = new HashMap<>();
    if (jobDimensions.isEmpty()) {
      return unsupportedJobDimensions;
    }

    ListMultimap<String, String> deviceDimensions = supported.getAll();
    deviceDimensions.putAll(required.getAll());
    for (Entry<String, String> jobDimension : jobDimensions.entrySet()) {
      String jobDimensionName = jobDimension.getKey();
      String jobDimensionValue = jobDimensions.get(jobDimensionName);
      // The exclude value means request the device excluding the dimension.
      if (jobDimensionValue.equals(Dimension.Value.EXCLUDE)) {
        if (deviceDimensions.containsKey(jobDimensionName)) {
          unsupportedJobDimensions.put(jobDimensionName, jobDimensionValue);
          if (failFast) {
            return unsupportedJobDimensions;
          }
        }
        continue;
      }

      boolean match = false;
      for (String deviceDimensionValue : deviceDimensions.get(jobDimensionName)) {
        if (deviceDimensionValue.equals(Dimension.Value.ALL_VALUE_FOR_DEVICE)
            || jobDimensionValue.equals(deviceDimensionValue)
            || (jobDimensionValue.startsWith(Dimension.Value.PREFIX_REGEX)
                && deviceDimensionValue.matches(
                    jobDimensionValue.substring(Dimension.Value.PREFIX_REGEX.length())))
            || ((jobDimensionValue.startsWith(ValueComparator.PREFIX_INT_COMPARISON)
                    || jobDimensionValue.startsWith(ValueComparator.PREFIX_STR_COMPARISON))
                && ValueComparator.match(jobDimensionValue, deviceDimensionValue))) {
          match = true;
          break;
        }
      }
      if (!match) {
        unsupportedJobDimensions.put(jobDimensionName, jobDimensionValue);
        if (failFast) {
          return unsupportedJobDimensions;
        }
      }
    }
    return unsupportedJobDimensions;
  }

  /**
   * Returns the required device dimensions which are not satisfied by the given job dimensions.
   *
   * @param failFast true to return the result immediately when one pair of unsatisfied device
   *     required dimensions are found, false to search and return all unsatisfied device required
   *     dimensions
   */
  public Multimap<String, String> getUnsatisfiedDeviceDimensions(
      Map<String, String> jobDimensions, boolean failFast) {
    SetMultimap<String, String> unsatisfiedDeviceDimensions = HashMultimap.create();
    for (Entry<String, String> deviceRequiredDimension : required.getAll().entries()) {
      String deviceDimensionName = deviceRequiredDimension.getKey();
      String deviceDimensionValue = deviceRequiredDimension.getValue();
      String jobDimensionValue = jobDimensions.get(deviceDimensionName);

      boolean satisfied;
      if (jobDimensionValue == null || jobDimensionValue.equals(Dimension.Value.EXCLUDE)) {
        satisfied = false;
      } else {
        satisfied =
            deviceDimensionValue.equals(Dimension.Value.ALL_VALUE_FOR_DEVICE)
                || jobDimensionValue.equals(deviceDimensionValue)
                || (jobDimensionValue.startsWith(Dimension.Value.PREFIX_REGEX)
                    && deviceDimensionValue.matches(
                        jobDimensionValue.substring(Dimension.Value.PREFIX_REGEX.length())))
                || ((jobDimensionValue.startsWith(ValueComparator.PREFIX_INT_COMPARISON)
                        || jobDimensionValue.startsWith(ValueComparator.PREFIX_STR_COMPARISON))
                    && ValueComparator.match(jobDimensionValue, deviceDimensionValue));
      }
      if (!satisfied) {
        unsatisfiedDeviceDimensions.put(deviceDimensionName, deviceDimensionValue);
        if (failFast) {
          return unsatisfiedDeviceDimensions;
        }
      }
    }
    return unsatisfiedDeviceDimensions;
  }

  /** Returns "required" if true and "supported" otherwise. */
  public static String getDimensionsPrefix(boolean required) {
    return required ? "required" : "supported";
  }
}
