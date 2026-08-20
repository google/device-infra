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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask.DimensionsMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.protobuf.FieldMask;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.inject.Inject;

/**
 * The central authoritative schema registry for MobileHarness search keys.
 *
 * <p>Maintains strictly partitioned entity schemas ({@link DeviceKeyDescriptor}s for Device Search,
 * and {@link HostKeyDescriptor}s for Host Search). Mechanically derives the {@link DeviceInfoMask}
 * and {@link LabInfoMask} with complete type safety and entity isolation.
 *
 * <p>Construction enforces three invariants (fail-fast at startup): no duplicate ids within a
 * partition, each id's namespace prefix matches its entity partition, and the two partitions are
 * disjoint.
 */
public class KeyRegistry {

  private static final ImmutableSet<String> DEVICE_PREFIXES =
      ImmutableSet.of(
          DeviceKeys.PREFIX_DEVICE_FIELD,
          DeviceKeys.PREFIX_DIMENSION,
          DeviceKeys.PREFIX_DEVICE_CONFIG);

  private static final ImmutableSet<String> HOST_PREFIXES =
      ImmutableSet.of(HostKeys.PREFIX_HOST_FIELD, HostKeys.PREFIX_HOST_PROPERTY);

  private final ImmutableMap<String, DeviceKeyDescriptor> deviceKeys;
  private final ImmutableMap<String, HostKeyDescriptor> hostKeys;

  /** Standard OSS constructor holding only the universal common keys. */
  @Inject
  KeyRegistry() {
    this(ImmutableList.of(), ImmutableList.of());
  }

  /**
   * Protected constructor allowing derived registries (e.g. {@code AtsKeyRegistry}, {@code
   * InternalKeyRegistry}) to compose their specific extra keys on top of the universal common keys.
   */
  protected KeyRegistry(
      ImmutableList<DeviceKeyDescriptor> extraDeviceKeys,
      ImmutableList<HostKeyDescriptor> extraHostKeys) {
    this.deviceKeys =
        index(
            ImmutableList.<DeviceKeyDescriptor>builder()
                .addAll(DeviceKeys.COMMON_DEVICE_KEYS)
                .addAll(extraDeviceKeys)
                .build(),
            DeviceKeyDescriptor::id,
            DEVICE_PREFIXES,
            "device");
    this.hostKeys =
        index(
            ImmutableList.<HostKeyDescriptor>builder()
                .addAll(HostKeys.COMMON_HOST_KEYS)
                .addAll(extraHostKeys)
                .build(),
            HostKeyDescriptor::id,
            HOST_PREFIXES,
            "host");
    Set<String> overlap = Sets.intersection(deviceKeys.keySet(), hostKeys.keySet());
    checkArgument(
        overlap.isEmpty(), "Key ids registered in both device and host partitions: %s", overlap);
  }

  /**
   * Indexes descriptors by id, enforcing that each id starts with an allowed namespace prefix and
   * that no id is duplicated within the partition.
   */
  private static <T> ImmutableMap<String, T> index(
      ImmutableList<T> descriptors,
      Function<T, String> idFn,
      ImmutableSet<String> allowedPrefixes,
      String entity) {
    for (T descriptor : descriptors) {
      String id = idFn.apply(descriptor);
      checkArgument(
          allowedPrefixes.stream().anyMatch(id::startsWith),
          "%s key id '%s' must start with one of %s",
          entity,
          id,
          allowedPrefixes);
    }
    // The two-arg collector throws IllegalArgumentException on a duplicate id (fail-fast).
    return descriptors.stream().collect(toImmutableMap(idFn, descriptor -> descriptor));
  }

  /** Returns the device key descriptor for {@code keyId}, or empty if not registered. */
  public Optional<DeviceKeyDescriptor> getDeviceKey(String keyId) {
    return Optional.ofNullable(deviceKeys.get(keyId));
  }

  /** Returns the host key descriptor for {@code keyId}, or empty if not registered. */
  public Optional<HostKeyDescriptor> getHostKey(String keyId) {
    return Optional.ofNullable(hostKeys.get(keyId));
  }

  /** Returns all registered device key descriptors. */
  public ImmutableCollection<DeviceKeyDescriptor> deviceKeys() {
    return deviceKeys.values();
  }

  /** Returns all registered host key descriptors. */
  public ImmutableCollection<HostKeyDescriptor> hostKeys() {
    return hostKeys.values();
  }

  /** Returns all device key IDs. */
  public ImmutableSet<String> deviceKeyIds() {
    return deviceKeys.keySet();
  }

  /** Returns all host key IDs. */
  public ImmutableSet<String> hostKeyIds() {
    return hostKeys.keySet();
  }

  /**
   * Returns the display name for {@code keyId} in the given search context.
   *
   * <p>Host search shows only host keys (their {@code hostSearchName}). Device search shows device
   * keys (their {@code displayName}) and, cross-entity, host keys (their {@code deviceSearchName}).
   */
  public Optional<String> displayName(String keyId, boolean isHostSearch) {
    if (isHostSearch) {
      return getHostKey(keyId).map(HostKeyDescriptor::hostSearchName);
    }
    Optional<DeviceKeyDescriptor> deviceKey = getDeviceKey(keyId);
    if (deviceKey.isPresent()) {
      return deviceKey.map(DeviceKeyDescriptor::displayName);
    }
    return getHostKey(keyId).map(HostKeyDescriptor::deviceSearchName);
  }

  /** Mechanically derives the {@link DeviceInfoMask} exclusively from {@link #deviceKeys()}. */
  public DeviceInfoMask deriveDeviceInfoMask() {
    Set<String> fieldPaths = new LinkedHashSet<>();
    Set<String> dimensionNames = new LinkedHashSet<>();

    for (DeviceKeyDescriptor descriptor : deviceKeys.values()) {
      collectDeviceInfoSources(descriptor.source(), fieldPaths, dimensionNames);
    }

    if (!dimensionNames.isEmpty()) {
      fieldPaths.add("device_feature.composite_dimension");
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

  /** Mechanically derives the {@link LabInfoMask} exclusively from {@link #hostKeys()}. */
  public LabInfoMask deriveLabInfoMask() {
    Set<String> fieldPaths = new LinkedHashSet<>();

    for (HostKeyDescriptor descriptor : hostKeys.values()) {
      collectLabInfoSources(descriptor.source(), fieldPaths);
    }

    LabInfoMask.Builder mask = LabInfoMask.newBuilder();
    if (!fieldPaths.isEmpty()) {
      mask.setFieldMask(FieldMask.newBuilder().addAllPaths(fieldPaths));
    }
    return mask.build();
  }

  private static void collectDeviceInfoSources(
      DeviceSource source, Set<String> fieldPaths, Set<String> dimensionNames) {
    switch (source.getKind()) {
      case DEVICE_INFO_FIELD:
        fieldPaths.add(source.deviceInfoField());
        break;
      case DIMENSION:
        dimensionNames.add(source.dimension());
        break;
      case COMPUTED:
        for (DeviceSource input : source.computed().maskInputs()) {
          collectDeviceInfoSources(input, fieldPaths, dimensionNames);
        }
        break;
      case CONFIG:
        break;
    }
  }

  private static void collectLabInfoSources(HostSource source, Set<String> fieldPaths) {
    switch (source.getKind()) {
      case LAB_INFO_FIELD:
        fieldPaths.add(source.labInfoField());
        break;
      case HOST_PROPERTY:
        fieldPaths.add("lab_server_feature.host_properties");
        break;
      case COMPUTED:
        for (HostSource input : source.computed().maskInputs()) {
          collectLabInfoSources(input, fieldPaths);
        }
        break;
      case HOST_INFO:
      case PROVENANCE:
        break;
    }
  }
}
