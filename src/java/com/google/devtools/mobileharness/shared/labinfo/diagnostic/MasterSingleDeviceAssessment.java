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

package com.google.devtools.mobileharness.shared.labinfo.diagnostic;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.base.Ascii;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceRequirement;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.DiagnoseJobSpec;
import com.google.devtools.mobileharness.shared.util.dimension.ValueComparator;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/** Assessment of a device or a group of device, about their support of a given job */
public class MasterSingleDeviceAssessment {
  private static final String DEVICE_DEFAULT_OWNER = "mobileharness-device-default-owner";

  static final int WEIGHT_ACCESS = 3;
  static final int WEIGHT_DEVICE_TYPE = 2;
  static final int WEIGHT_DRIVER = 2;
  static final int WEIGHT_DECORATOR = 5;
  static final int WEIGHT_SUPPORTED_DIMENSION = 5;
  static final int WEIGHT_SATISFIED_DIMENSION = 4;
  static final int WEIGHT_STATUS = 1;

  public static final int MAX_SCORE =
      WEIGHT_ACCESS
          + WEIGHT_DEVICE_TYPE
          + WEIGHT_DRIVER
          + WEIGHT_DECORATOR
          + WEIGHT_SUPPORTED_DIMENSION
          + WEIGHT_SATISFIED_DIMENSION
          + WEIGHT_STATUS;
  static final int MIN_SCORE = 0;

  static final int SUPPLEMENT_HAS_POTENTIAL_ACCESS = WEIGHT_ACCESS - 1;
  static final int DEDUCTION_SINGLE_DIMENSION = 2;
  static final int DEDUCTION_LABEL_DIMENSION = 3;
  static final int DEDUCTION_STRONG_DIMENSION = 4;

  private final String user;
  private final String driver;
  private final String deviceType;
  private final ImmutableMap<String, String> requestedDimensions;
  private boolean accessible = false;
  private boolean potentialAccessible = false;
  private boolean driverSupported = false;
  private boolean deviceTypeSupported = false;
  private Set<String> unsupportedDecorators;
  private Map<String, String> unsupportedDimensions;
  private Multimap<String, String> unsatisfiedDimensions;

  private boolean idle = false;
  private boolean missing = true;

  private final List<String> requestedSharedDimensionNames;
  private final ListMultimap<String, String> supportedSharedDimensions;

  public MasterSingleDeviceAssessment(DiagnoseJobSpec spec, DeviceRequirement deviceRequirement) {
    this.user = spec.getUser().getRunAs();
    this.driver = spec.getDriver();
    this.requestedSharedDimensionNames = spec.getDeviceRequirements().getSharedDimensionList();
    this.deviceType = deviceRequirement.getDeviceType();
    this.requestedDimensions = ImmutableMap.copyOf(deviceRequirement.getDimensionsMap());
    this.unsupportedDecorators = new HashSet<>(deviceRequirement.getDecoratorList());
    this.unsupportedDimensions = new HashMap<>(deviceRequirement.getDimensionsMap());
    this.unsatisfiedDimensions = null;
    this.supportedSharedDimensions = LinkedListMultimap.create();
  }

  @CanIgnoreReturnValue
  public MasterSingleDeviceAssessment addResource(DeviceInfo device) {
    DeviceFeature feature = device.getDeviceFeature();

    // Access check
    if (supportOwner(feature.getOwnerList(), user) || feature.getExecutorList().contains(user)) {
      accessible = true;
      potentialAccessible = false;
    } else if (!accessible) {
      List<String> owners = feature.getOwnerList();
      if (owners.size() == 1 && owners.contains(DEVICE_DEFAULT_OWNER)) {
        potentialAccessible = true;
      }
    }

    driverSupported |= feature.getDriverList().contains(driver);
    deviceTypeSupported |= feature.getTypeList().contains(deviceType);

    if (!unsupportedDecorators.isEmpty()) {
      unsupportedDecorators.removeAll(feature.getDecoratorList());
    }

    // Dimensions check
    if (!unsupportedDimensions.isEmpty()) {
      unsupportedDimensions =
          getUnsupportedJobDimensions(feature.getCompositeDimension(), unsupportedDimensions);
    }

    // Shared dimensions
    for (String sharedDimensionName : requestedSharedDimensionNames) {
      getDimensionValues(feature.getCompositeDimension(), sharedDimensionName)
          .forEach(value -> supportedSharedDimensions.put(sharedDimensionName, value));
    }

    DeviceStatus deviceStatus = device.getDeviceStatus();
    idle |= (deviceStatus == DeviceStatus.IDLE);
    missing &= (deviceStatus == DeviceStatus.MISSING);

    // Check device required dimensions
    Multimap<String, String> newUnsatisfiedDimensions =
        getUnsatisfiedDeviceDimensions(feature.getCompositeDimension(), requestedDimensions);

    if (unsatisfiedDimensions == null) {
      unsatisfiedDimensions = HashMultimap.create();
      unsatisfiedDimensions.putAll(newUnsatisfiedDimensions);
    } else if (!unsatisfiedDimensions.isEmpty()) {
      // Get intersection
      if (newUnsatisfiedDimensions.isEmpty()) {
        unsatisfiedDimensions.clear();
      } else {
        SetMultimap<String, String> intersection = HashMultimap.create();
        unsatisfiedDimensions.forEach(
            (key, value) -> {
              if (newUnsatisfiedDimensions.containsEntry(key, value)) {
                intersection.put(key, value);
              }
            });
        unsatisfiedDimensions = intersection;
      }
    }
    return this;
  }

  private static boolean supportOwner(List<String> owners, String user) {
    return owners.isEmpty() || owners.contains(user);
  }

  private static Map<String, String> getUnsupportedJobDimensions(
      DeviceCompositeDimension deviceDimensions, Map<String, String> jobDimensions) {
    Map<String, String> unsupportedJobDimensions = new HashMap<>();
    if (jobDimensions.isEmpty()) {
      return unsupportedJobDimensions;
    }

    ListMultimap<String, String> deviceDims = ArrayListMultimap.create();
    for (DeviceDimension dim : deviceDimensions.getSupportedDimensionList()) {
      deviceDims.put(dim.getName(), dim.getValue());
    }
    for (DeviceDimension dim : deviceDimensions.getRequiredDimensionList()) {
      deviceDims.put(dim.getName(), dim.getValue());
    }

    for (Entry<String, String> jobDimension : jobDimensions.entrySet()) {
      String jobDimensionName = jobDimension.getKey();
      String jobDimensionValue = jobDimension.getValue();
      if (jobDimensionValue.equals(Dimension.Value.EXCLUDE)) {
        if (deviceDims.containsKey(jobDimensionName)) {
          unsupportedJobDimensions.put(jobDimensionName, jobDimensionValue);
        }
        continue;
      }

      boolean match = false;
      for (String deviceDimensionValue : deviceDims.get(jobDimensionName)) {
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
      }
    }
    return unsupportedJobDimensions;
  }

  private static Multimap<String, String> getUnsatisfiedDeviceDimensions(
      DeviceCompositeDimension deviceDimensions, Map<String, String> jobDimensions) {
    SetMultimap<String, String> unsatisfiedDeviceDimensions = HashMultimap.create();
    for (DeviceDimension deviceRequiredDimension : deviceDimensions.getRequiredDimensionList()) {
      String deviceDimensionName = deviceRequiredDimension.getName();
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
      }
    }
    return unsatisfiedDeviceDimensions;
  }

  private static List<String> getDimensionValues(
      DeviceCompositeDimension deviceDimensions, String dimensionName) {
    List<String> values = new ArrayList<>();
    for (DeviceDimension dim : deviceDimensions.getSupportedDimensionList()) {
      if (dim.getName().equals(dimensionName)) {
        values.add(dim.getValue());
      }
    }
    for (DeviceDimension dim : deviceDimensions.getRequiredDimensionList()) {
      if (dim.getName().equals(dimensionName)) {
        values.add(dim.getValue());
      }
    }
    return values;
  }

  public boolean isAccessible() {
    return accessible;
  }

  public boolean isPotentialAccessible() {
    return potentialAccessible;
  }

  public boolean isDriverSupported() {
    return driverSupported;
  }

  public boolean isDeviceTypeSupported() {
    return deviceTypeSupported;
  }

  public boolean isDecoratorsSupported() {
    return unsupportedDecorators.isEmpty();
  }

  public Set<String> getUnsupportedDecorators() {
    return Collections.unmodifiableSet(unsupportedDecorators);
  }

  public boolean isDimensionsSupported() {
    return unsupportedDimensions.isEmpty()
        && supportedSharedDimensions.keySet().size() == requestedSharedDimensionNames.size();
  }

  public Map<String, String> getUnsupportedDimensions() {
    if (supportedSharedDimensions.keySet().size() != requestedSharedDimensionNames.size()) {
      ImmutableMap<String, String> unsupportedSharedDimensions =
          requestedSharedDimensionNames.stream()
              .filter(name -> !supportedSharedDimensions.containsKey(name))
              .collect(toImmutableMap(Function.identity(), entry -> ""));
      if (unsupportedDimensions.isEmpty()) {
        return Collections.unmodifiableMap(unsupportedSharedDimensions);
      } else {
        return Collections.unmodifiableMap(
            Stream.concat(
                    unsupportedDimensions.entrySet().stream(),
                    unsupportedSharedDimensions.entrySet().stream())
                .collect(toImmutableMap(Entry::getKey, Entry::getValue, (first, second) -> first)));
      }
    } else {
      return Collections.unmodifiableMap(unsupportedDimensions);
    }
  }

  public Multimap<String, String> getSupportedSharedDimensions() {
    return supportedSharedDimensions;
  }

  public boolean isDimensionsSatisfied() {
    return unsatisfiedDimensions.isEmpty();
  }

  public Multimap<String, String> getUnsatisfiedDimensions() {
    return Multimaps.unmodifiableMultimap(unsatisfiedDimensions);
  }

  public boolean isIdle() {
    return idle;
  }

  public boolean isMissing() {
    return missing;
  }

  public int getScore() {
    return getAccessibleScore()
        + (driverSupported ? WEIGHT_DRIVER : MIN_SCORE)
        + (deviceTypeSupported ? WEIGHT_DEVICE_TYPE : MIN_SCORE)
        + Math.max(MIN_SCORE, WEIGHT_DECORATOR - unsupportedDecorators.size())
        + getSupportedDimensionScore()
        + getSatisfiedDimensionScore()
        + (idle ? WEIGHT_STATUS : MIN_SCORE);
  }

  public boolean hasMaxScore() {
    return getScore() == MAX_SCORE;
  }

  private int getAccessibleScore() {
    if (accessible) {
      return WEIGHT_ACCESS;
    } else if (potentialAccessible) {
      return MIN_SCORE + SUPPLEMENT_HAS_POTENTIAL_ACCESS;
    } else {
      return MIN_SCORE;
    }
  }

  private int getSupportedDimensionScore() {
    Map<String, String> calculatedUnsupportedDimensions = getUnsupportedDimensions();
    int score =
        WEIGHT_SUPPORTED_DIMENSION
            - calculatedUnsupportedDimensions.size() * DEDUCTION_SINGLE_DIMENSION;
    if (calculatedUnsupportedDimensions.containsKey(
        Ascii.toLowerCase(Dimension.Name.LABEL.name()))) {
      score = score - DEDUCTION_LABEL_DIMENSION + DEDUCTION_SINGLE_DIMENSION;
    }
    if (calculatedUnsupportedDimensions.containsKey(Ascii.toLowerCase(Dimension.Name.ID.name()))
        || calculatedUnsupportedDimensions.containsKey(
            Ascii.toLowerCase(Dimension.Name.HOST_IP.name()))
        || calculatedUnsupportedDimensions.containsKey(
            Ascii.toLowerCase(Dimension.Name.HOST_NAME.name()))) {
      score = score - DEDUCTION_STRONG_DIMENSION + DEDUCTION_SINGLE_DIMENSION;
    }
    return Math.max(MIN_SCORE, score);
  }

  private int getSatisfiedDimensionScore() {
    int score = WEIGHT_SATISFIED_DIMENSION - unsatisfiedDimensions.size();
    return Math.max(MIN_SCORE, score);
  }
}
