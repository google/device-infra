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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.singledevice;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.base.Ascii;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.Assessment;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceInfo;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Assessment of a device or a group of device, about their support of a given job */
public class SingleDeviceAssessment implements Assessment<DeviceInfo> {
  private static final String DEVICE_DEFAULT_OWNER = "mobileharness-device-default-owner";

  // RULES for the weight values:
  // 1) To promote the devices when they support a certain requirement, increase the weight.
  //    But if it is not supported, the ranking will be extremely low.
  // 2) To promote the devices when they don't support a certain requirement, decrease the weight.
  //    But even when it is support, it won't significantly increase the ranking too.
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

  // Extra score added to those devices set to default owner.
  static final int SUPPLEMENT_HAS_POTENTIAL_ACCESS = WEIGHT_ACCESS - 1;

  // The mismatch of a single dimension will drop 2 score. So that one dimension un-matched devices'
  // scores are smaller than those busy or mobileharness-device-default-owner devices.
  static final int DEDUCTION_SINGLE_DIMENSION = 2;

  // When user requires the "label", decrease the ranks of those that don't have "label" to
  // promote the devices with "label".
  static final int DEDUCTION_LABEL_DIMENSION = 3;

  // When user requires the "id" or "host_name" or "host_ip", decrease the ranks of those that don't
  // support these fields.
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

  /** Assessment of a job. */
  SingleDeviceAssessment(JobScheduleUnit job) {
    this(
        job,
        null,
        new HashSet<>(job.type().getDecoratorList()),
        new HashMap<>(job.dimensions().getAll()));
  }

  SingleDeviceAssessment(JobScheduleUnit job, SubDeviceSpec spec) {
    this(
        job,
        spec,
        new HashSet<>(spec.decorators().getAll()),
        new HashMap<>(spec.dimensions().getAll()));
  }

  private SingleDeviceAssessment(
      JobScheduleUnit job,
      @Nullable SubDeviceSpec spec,
      Set<String> unsupportedDecorators,
      Map<String, String> unsupportedDimensions) {
    this.user = job.jobUser().getRunAs();
    this.driver = job.type().getDriver();
    this.requestedSharedDimensionNames = job.subDeviceSpecs().getSharedDimensionNames();
    this.deviceType = spec == null ? job.type().getDevice() : spec.type();
    this.requestedDimensions =
        ImmutableMap.copyOf(spec == null ? job.dimensions().getAll() : spec.dimensions().getAll());
    this.unsupportedDecorators = unsupportedDecorators;
    this.unsupportedDimensions = unsupportedDimensions;
    this.unsatisfiedDimensions = null;
    this.supportedSharedDimensions = LinkedListMultimap.create();
  }

  /** Adds a device into the assessment for a job. */
  @CanIgnoreReturnValue
  @Override
  public SingleDeviceAssessment addResource(DeviceInfo device) {
    // A device can only be potentially accessible when it's not accessible.
    if (device.owners().support(user)) {
      accessible = true;
      potentialAccessible = false;
    } else if (!accessible) {
      Set<String> owners = device.owners().getAll();
      if (owners.size() == 1 && owners.contains(DEVICE_DEFAULT_OWNER)) {
        potentialAccessible = true;
      }
    }
    driverSupported |= device.drivers().support(driver);
    deviceTypeSupported |= device.types().support(deviceType);

    if (!unsupportedDecorators.isEmpty()) {
      unsupportedDecorators = device.decorators().getUnsupported(unsupportedDecorators);
    }
    if (!unsupportedDimensions.isEmpty()) {
      unsupportedDimensions =
          device
              .dimensions()
              .getUnsupportedJobDimensions(unsupportedDimensions, /* failFast= */ false);
    }
    for (String sharedDimensionName : requestedSharedDimensionNames) {
      Stream.concat(
              device.dimensions().supported().get(sharedDimensionName).stream(),
              device.dimensions().required().get(sharedDimensionName).stream())
          .forEach(
              dimensionValue -> supportedSharedDimensions.put(sharedDimensionName, dimensionValue));
    }
    DeviceStatus deviceStatus = device.status().get();
    idle |= (deviceStatus == DeviceStatus.IDLE);
    missing &= (deviceStatus == DeviceStatus.MISSING);

    // Check device required dimensions.
    Multimap<String, String> newUnsatisfiedDimensions =
        device
            .dimensions()
            .getUnsatisfiedDeviceDimensions(requestedDimensions, /* failFast= */ false);
    if (unsatisfiedDimensions == null) {
      unsatisfiedDimensions = HashMultimap.create();
      unsatisfiedDimensions.putAll(newUnsatisfiedDimensions);
    } else if (!unsatisfiedDimensions.isEmpty()) {
      // Get intersection.
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

  /** Whether any devices are accessible for users of the job. */
  public boolean isAccessible() {
    return accessible;
  }

  /**
   * Whether any devices can become accessible if changing its owner from default value to the user
   */
  public boolean isPotentialAccessible() {
    return potentialAccessible;
  }

  /** Whether any devices support the required driver of the job. */
  public boolean isDriverSupported() {
    return driverSupported;
  }

  /** Whether any devices support the required device type of the job. */
  public boolean isDeviceTypeSupported() {
    return deviceTypeSupported;
  }

  /** Whether any devices support the required decorators of the job. */
  public boolean isDecoratorsSupported() {
    return unsupportedDecorators.isEmpty();
  }

  /** Gets the required decorators that are not supported by any devices. */
  public Set<String> getUnsupportedDecorators() {
    return Collections.unmodifiableSet(unsupportedDecorators);
  }

  /** Whether any devices support the required dimensions of the job. */
  public boolean isDimensionsSupported() {
    return unsupportedDimensions.isEmpty()
        && supportedSharedDimensions.keySet().size() == requestedSharedDimensionNames.size();
  }

  /** Gets the job required dimensions which are not supported by any devices. */
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

  /** Whether any device required dimensions are already satisfied by the job dimensions. */
  public boolean isDimensionsSatisfied() {
    return unsatisfiedDimensions.isEmpty();
  }

  /** Gets the device required dimensions which are not requested by the job. */
  public Multimap<String, String> getUnsatisfiedDimensions() {
    return Multimaps.unmodifiableMultimap(unsatisfiedDimensions);
  }

  /** Whether any devices are idle. */
  public boolean isIdle() {
    return idle;
  }

  /** Whether all devices are missing. */
  public boolean isMissing() {
    return missing;
  }

  /** Return true if all the requirements are matched but the device is busy. */
  public boolean isRequirementMatchedButBusy() {
    return getScore() == MAX_SCORE - (WEIGHT_STATUS - MIN_SCORE) && !isIdle();
  }

  /**
   * Calculates a score of the overall support of the devices for the job. The devices with bigger
   * score numbers will be promoted in the candidate device suggestion.
   *
   * <p>If the score is {@link #MAX_SCORE}, it means all job requirements are supported.
   *
   * <p>When no job requirement is supported, the score still could be larger than {@link
   * #MIN_SCORE}.
   */
  @Override
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
