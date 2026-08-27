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

package com.google.devtools.mobileharness.fe.v6.service.search.schema;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask.DimensionsMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.protobuf.FieldMask;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The per-deployment catalog of built-in keys usable in device search, and the factory/parser for
 * every device-search key id (built-in or long-tail).
 *
 * <p>Its built-in seed is the universal common device-native keys plus the Group 1 host keys
 * projected into device search; a subclass adds the extra keys its deployment reaches (WiFi SSID
 * for standalone ATS; owners/executors and 1P host projections internally; the ATS controller in
 * the partner aggregator). The full set of keys a user can actually reference is this built-in set
 * plus the long-tail keys ({@code dimension::<name>}, cross-entity {@code host_property::<name>})
 * that are discovered from data and minted on demand by {@link #getKey}; long-tail keys are never
 * in the built-in set and therefore never contribute to the derived core-pull mask.
 *
 * <p>Because the same {@link DeviceInfoSource} / {@link LabInfoSource} objects contribute to the
 * mask and know how to extract their value, the {@code DeviceInfoMask} and {@code LabInfoMask}
 * derived here cannot drift from extraction.
 */
public abstract class DeviceKeyRegistry {

  private static final ImmutableSet<String> ALLOWED_PREFIXES =
      ImmutableSet.of(
          DeviceKeys.PREFIX_DEVICE_FIELD,
          DeviceKeys.PREFIX_DIMENSION,
          DeviceKeys.PREFIX_DEVICE_CONFIG,
          HostKeys.PREFIX_HOST_FIELD,
          HostKeys.PREFIX_HOST_PROPERTY);

  private final ImmutableMap<String, DeviceKeyDescriptor> builtInKeys;

  /**
   * Composes the universal common device keys and projected common host keys with a subclass's
   * extra keys. Fails fast on a duplicate id or an id with an unknown namespace prefix.
   */
  protected DeviceKeyRegistry(ImmutableList<DeviceKeyDescriptor> extraKeys) {
    ImmutableList<DeviceKeyDescriptor> all =
        ImmutableList.<DeviceKeyDescriptor>builder()
            .addAll(DeviceKeys.COMMON_DEVICE_KEYS)
            .addAll(DeviceKeys.COMMON_HOST_PROJECTIONS)
            .addAll(extraKeys)
            .build();
    for (DeviceKeyDescriptor key : all) {
      if (ALLOWED_PREFIXES.stream().noneMatch(key.id()::startsWith)) {
        throw new IllegalArgumentException(
            "Device key id '" + key.id() + "' must start with one of " + ALLOWED_PREFIXES);
      }
    }
    // The two-arg collector throws IllegalArgumentException on a duplicate id (fail-fast).
    this.builtInKeys = all.stream().collect(toImmutableMap(DeviceKeyDescriptor::id, key -> key));
  }

  /**
   * Returns the descriptor for {@code keyId}: the built-in descriptor if registered, otherwise a
   * minted long-tail descriptor for a {@code dimension::} or cross-entity {@code host_property::}
   * id, otherwise empty.
   */
  public Optional<DeviceKeyDescriptor> getKey(String keyId) {
    DeviceKeyDescriptor builtIn = builtInKeys.get(keyId);
    if (builtIn != null) {
      return Optional.of(builtIn);
    }
    if (keyId.startsWith(DeviceKeys.PREFIX_DIMENSION)) {
      return Optional.of(
          createLongTailDimensionKey(keyId.substring(DeviceKeys.PREFIX_DIMENSION.length())));
    }
    if (keyId.startsWith(HostKeys.PREFIX_HOST_PROPERTY)) {
      return Optional.of(
          createProjectLongTailHostPropertyKey(
              keyId.substring(HostKeys.PREFIX_HOST_PROPERTY.length())));
    }
    return Optional.empty();
  }

  /** Mints a long-tail device dimension key for a dimension discovered from data. */
  public DeviceKeyDescriptor createLongTailDimensionKey(String dimensionName) {
    return DeviceKeys.longTailDimensionKey(dimensionName);
  }

  /**
   * Mints a long-tail host-property key projected into device search (a cross-entity host attribute
   * discovered from data).
   */
  public DeviceKeyDescriptor createProjectLongTailHostPropertyKey(String propertyKey) {
    return DeviceKeys.projectHostKey(
        HostKeys.hostPropertyKey(propertyKey), KeyDisplay.of(propertyKey));
  }

  /** All built-in device key descriptors. */
  public ImmutableCollection<DeviceKeyDescriptor> builtInKeys() {
    return builtInKeys.values();
  }

  /** All built-in device key ids. */
  public ImmutableSet<String> builtInKeyIds() {
    return builtInKeys.keySet();
  }

  /** Returns the display name for {@code keyId} (built-in or long-tail), or empty if unknown. */
  public Optional<String> displayName(String keyId) {
    return getKey(keyId).map(key -> key.display().name());
  }

  /**
   * Derives the {@link DeviceInfoMask} from the union of every built-in device key's {@code
   * deviceInfoSources}. Long-tail keys are excluded by construction (not in the built-in set).
   */
  public DeviceInfoMask deriveDeviceInfoMask() {
    Set<String> fieldPaths = new LinkedHashSet<>();
    Set<String> dimensionNames = new LinkedHashSet<>();
    for (DeviceKeyDescriptor key : builtInKeys.values()) {
      for (DeviceInfoSource source : key.deviceInfoSources()) {
        fieldPaths.addAll(source.maskFieldPaths());
        dimensionNames.addAll(source.maskDimensionNames());
      }
    }
    DeviceInfoMask.Builder mask = DeviceInfoMask.newBuilder();
    if (!fieldPaths.isEmpty()) {
      mask.setFieldMask(FieldMask.newBuilder().addAllPaths(fieldPaths));
    }
    if (!dimensionNames.isEmpty()) {
      DimensionsMask dimsMask =
          DimensionsMask.newBuilder().addAllDimensionNames(dimensionNames).build();
      mask.setSupportedDimensionsMask(dimsMask).setRequiredDimensionsMask(dimsMask);
    }
    return mask.build();
  }

  /**
   * Derives the {@link LabInfoMask} from the union of every built-in device key's {@code
   * labInfoSources} (the projected host keys), so the shared {@code GetLabInfo} pull fetches the
   * host fields stamped onto each device.
   */
  public LabInfoMask deriveLabInfoMask() {
    Set<String> fieldPaths = new LinkedHashSet<>();
    for (DeviceKeyDescriptor key : builtInKeys.values()) {
      for (LabInfoSource source : key.labInfoSources()) {
        fieldPaths.addAll(source.maskFieldPaths());
      }
    }
    LabInfoMask.Builder mask = LabInfoMask.newBuilder();
    if (!fieldPaths.isEmpty()) {
      mask.setFieldMask(FieldMask.newBuilder().addAllPaths(fieldPaths));
    }
    return mask.build();
  }
}
