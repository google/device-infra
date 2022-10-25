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

package com.google.wireless.qa.mobileharness.shared.model.job.in;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.InlineMe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Represents the requirements of the devices being requested for a single job. */
public class SubDeviceSpecs {

  /** Specifications for subDevice dimensions. */
  private final List<SubDeviceSpec> subDevices = new ArrayList<>();

  /** Dimensions that must have a shared value for each subDevice. */
  private final List<String> sharedDimensionNames = new ArrayList<>();

  /** The time records. */
  @Nullable private final Timing timing;

  /** Intended to hold a copy of global (not scoped) job params */
  private final Params params;

  /** A map of allocated device IDs to their corresponding {@link SubDeviceSpec} */
  private final Map<String, SubDeviceSpec> subDeviceSpecsById;

  /**
   * A map of allocated device IDs to their corresponding {@link SubDeviceSpec} index in {@link
   * SubDeviceSpecs#subDevices}.
   */
  private final Map<String, Integer> subDeviceSpecsIndexById;

  /**
   * The latest device IDs from the allocation of {@link #setAllocation(Allocation)} to help restore
   * {@link #subDeviceSpecsById} and {@link #subDeviceSpecsIndexById}.
   */
  private final List<String> latestDeviceIdsFromAllocation;

  /** Creates the sub-device specs segment of a job. */
  public SubDeviceSpecs(Params params, @Nullable Timing timing) {
    this.timing = timing;
    this.params = params;
    this.subDeviceSpecsById = new HashMap<>();
    this.subDeviceSpecsIndexById = new HashMap<>();
    this.latestDeviceIdsFromAllocation = new ArrayList<>();
  }

  /** Adds a dimensionless subDevice to the spec. */
  public SubDeviceSpec addSubDevice(String type) {
    return addSubDevice(type, ImmutableMap.of());
  }

  /** Adds a dimensioned subDevice to the spec. */
  public SubDeviceSpec addSubDevice(String type, Map<String, String> dimensions) {
    return addSubDevice(type, dimensions, ImmutableList.of());
  }

  /** Adds a dimensioned and decorated subDevice to the spec. */
  public SubDeviceSpec addSubDevice(
      String type, Map<String, String> dimensions, List<String> decorators) {
    return addSubDevice(type, dimensions, decorators, ImmutableMap.of());
  }

  /** Adds a dimensioned and decorated subDevice to the spec with scoped specs. */
  public SubDeviceSpec addSubDevice(
      String type,
      Map<String, String> dimensions,
      List<String> decorators,
      Map<String, JsonObject> scopedSpecsMap) {
    ScopedSpecs scopedSpecs = new ScopedSpecs(params, timing);
    for (String namespace : scopedSpecsMap.keySet()) {
      scopedSpecs.add(namespace, scopedSpecsMap.get(namespace));
    }
    SubDeviceSpec subDeviceSpec = SubDeviceSpec.create(type, scopedSpecs, timing);
    subDeviceSpec.dimensions().addAll(dimensions);
    subDeviceSpec.decorators().addAll(decorators);
    return addSubDevice(subDeviceSpec);
  }

  /** Adds a subDeviceSpec to the spec. */
  public SubDeviceSpec addSubDevice(SubDeviceSpec device) {
    subDevices.add(device);
    if (timing != null) {
      timing.touch();
    }
    return device;
  }

  /**
   * Adds a list of subDeviceSpecs to the spec.
   *
   * @deprecated use {@link SubDeviceSpecs#addAllSubDevices(List)} instead.
   */
  @InlineMe(replacement = "this.addAllSubDevices(deviceList)")
  @Deprecated
  public final void addAll(List<SubDeviceSpec> deviceList) {
    addAllSubDevices(deviceList);
  }

  /** Adds a list of subDeviceSpecs to the spec. */
  public void addAllSubDevices(List<SubDeviceSpec> deviceList) {
    for (SubDeviceSpec device : deviceList) {
      addSubDevice(device);
    }
  }

  /**
   * Gets a list of {@link SubDeviceSpec}s. \
   *
   * @deprecated use {@link SubDeviceSpecs#getAllSubDevices()} instead.
   */
  @InlineMe(replacement = "this.getAllSubDevices()")
  @Deprecated
  public final List<SubDeviceSpec> getAll() {
    return getAllSubDevices();
  }

  /** Gets a list of {@link SubDeviceSpec}s. */
  public List<SubDeviceSpec> getAllSubDevices() {
    return Collections.unmodifiableList(subDevices);
  }

  /**
   * Gets a {@link SubDeviceSpec} by index.
   *
   * @deprecated use {@link SubDeviceSpecs#getSubDevice(int)} instead.
   */
  @Deprecated
  public SubDeviceSpec get(int index) {
    return subDevices.get(index);
  }

  /** Gets a {@link SubDeviceSpec} by index. */
  public SubDeviceSpec getSubDevice(int index) {
    return subDevices.get(index);
  }

  /**
   * Gets the {@link SubDeviceSpec} corresponding to an allocated device indicated by the given
   * device ID.
   *
   * <p>Note that the SubDeviceSpecs only contains the first device or the first testbed specified
   * by the job type when creating JobInfo/JobScheduleUnit, so a deviceId from the other tests of
   * the job is not be able to be found.
   */
  public Optional<SubDeviceSpec> getSubDeviceById(@Nullable String deviceId) {
    if (deviceId == null || !subDeviceSpecsById.containsKey(deviceId)) {
      return Optional.empty();
    }
    return Optional.of(subDeviceSpecsById.get(deviceId));
  }

  /**
   * Gets the {@link SubDeviceSpec} index of {@link SubDeviceSpecs#subDevices} corresponding to an
   * allocated device indicated by the given device ID.
   *
   * <p>Note that the SubDeviceSpecs only contains the first device or the first testbed specified
   * by the job type when creating JobInfo/JobScheduleUnit, so a deviceId from the other tests of
   * the job is not be able to be found.
   */
  public Optional<Integer> getSubDeviceIndexById(String deviceId) {
    if (deviceId == null || !subDeviceSpecsIndexById.containsKey(deviceId)) {
      return Optional.empty();
    }
    return Optional.of(subDeviceSpecsIndexById.get(deviceId));
  }

  /** Returns a Set of Strings containing all device types requested as sub-devices. */
  public Set<String> getAllSubDeviceTypes() {
    Set<String> types = new HashSet<>();
    for (SubDeviceSpec device : subDevices) {
      types.add(device.type());
    }
    return types;
  }

  /**
   * Gets the latest device IDs got from the latest allocation when invoking {@link
   * #setAllocation(Allocation)}. Note: it's only meaningful to support storing and recovering a
   * {@link SubDeviceSpecs} instance.
   */
  public ImmutableList<String> getLatestDeviceIdsFromAllocation() {
    return ImmutableList.copyOf(latestDeviceIdsFromAllocation);
  }

  /** Removes all {@link SubDeviceSpec}s. */
  public void clearSubDevices() {
    subDevices.clear();
  }

  /**
   * Removes all {@link SubDeviceSpec}s satisfying the given predicate and returns whether any were
   * removed.
   */
  public boolean removeSubDeviceIf(Predicate<SubDeviceSpec> predicate) {
    return subDevices.removeIf(predicate);
  }

  /** Sets the allocation associated with this set of SubDeviceSpecs. */
  public void setAllocation(Allocation allocation) {
    if (!subDeviceSpecsById.isEmpty()) {
      subDeviceSpecsById.clear();
    }
    if (!subDeviceSpecsIndexById.isEmpty()) {
      subDeviceSpecsIndexById.clear();
    }
    if (!latestDeviceIdsFromAllocation.isEmpty()) {
      latestDeviceIdsFromAllocation.clear();
    }
    ImmutableList<DeviceLocator> allocatedDeviceLocators = allocation.getAllDeviceLocators();
    for (int i = 0; i < allocatedDeviceLocators.size(); i++) {
      String deviceId = allocatedDeviceLocators.get(i).getSerial();
      subDeviceSpecsById.put(deviceId, subDevices.get(i));
      subDeviceSpecsIndexById.put(deviceId, i);
      latestDeviceIdsFromAllocation.add(deviceId);
    }
    if (timing != null) {
      timing.touch();
    }
  }

  /**
   * Returns true if no device have been added. This should not happen after finalizing the
   * sub-devices in the Client code, but is needed as part of the migration from a single device
   * type and dimensions fields to a sub-devices list in job info.
   *
   * @deprecated use {@link SubDeviceSpecs#getSubDeviceCount()} instead.
   */
  @Deprecated
  public boolean isEmpty() {
    return subDevices.isEmpty();
  }

  /** Returns the number of subdevices that have been added. */
  public int getSubDeviceCount() {
    return subDevices.size();
  }

  /** Returns true if multiple devices have been added. */
  public boolean hasMultipleDevices() {
    return subDevices.size() > 1;
  }

  /** Adds a shared dimension to the spec. */
  public void addSharedDimensionName(String dimension) {
    sharedDimensionNames.add(dimension);
  }

  /** Adds a collection of shared dimensions to the spec. */
  public void addSharedDimensionNames(Collection<String> dimensions) {
    sharedDimensionNames.addAll(dimensions);
  }

  /** Gets an immutable view of the shared dimensions in the spec. */
  public List<String> getSharedDimensionNames() {
    return Collections.unmodifiableList(sharedDimensionNames);
  }

  public JsonArray toJson() {
    Gson gson = new GsonBuilder().create();
    JsonArray deviceSpecList = new JsonArray();
    for (SubDeviceSpec device : subDevices) {
      JsonArray deviceSpecJson = new JsonArray();
      deviceSpecJson.add(device.type());
      deviceSpecJson.add(
          JsonParser.parseString(gson.toJson(device.dimensions().getAll())).getAsJsonObject());
      deviceSpecJson.add(
          JsonParser.parseString(gson.toJson(device.decorators().getAll())).getAsJsonArray());
      deviceSpecJson.add(
          JsonParser.parseString(gson.toJson(device.scopedSpecs().getAll())).getAsJsonObject());
      deviceSpecList.add(deviceSpecJson);
    }
    JsonArray subDeviceSpecsList = new JsonArray();
    subDeviceSpecsList.add(deviceSpecList);
    subDeviceSpecsList.add(
        JsonParser.parseString(gson.toJson(sharedDimensionNames)).getAsJsonArray());
    return subDeviceSpecsList;
  }

  @Override
  public String toString() {
    return toJson().toString();
  }
}
