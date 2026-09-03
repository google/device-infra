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
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.protobuf.FieldMask;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The per-deployment catalog of built-in keys usable in host search, and the factory/parser for
 * every host-search key id (built-in or long-tail).
 *
 * <p>Its built-in seed is the universal common host keys; a subclass adds the extra keys its
 * deployment reaches (release/daemon/lab-type internally; the ATS controller in the partner
 * aggregator). The full set of keys a user can reference is this built-in set plus the long-tail
 * {@code host_property::<name>} keys discovered from data and minted on demand by {@link #getKey};
 * long-tail keys are never in the built-in set and never contribute to the derived mask.
 */
public abstract class HostKeyRegistry {

  private static final ImmutableSet<String> ALLOWED_PREFIXES =
      ImmutableSet.of(HostKeys.PREFIX_HOST_FIELD, HostKeys.PREFIX_HOST_PROPERTY);

  private final ImmutableMap<String, HostKeyDescriptor> builtInKeys;

  /**
   * Composes the universal common host keys with a subclass's extra keys. Fails fast on a duplicate
   * id or an id with an unknown namespace prefix.
   */
  protected HostKeyRegistry(ImmutableList<HostKeyDescriptor> extraKeys) {
    ImmutableList<HostKeyDescriptor> all =
        ImmutableList.<HostKeyDescriptor>builder()
            .addAll(HostKeys.COMMON_HOST_KEYS)
            .addAll(extraKeys)
            .build();
    for (HostKeyDescriptor key : all) {
      if (ALLOWED_PREFIXES.stream().noneMatch(key.id()::startsWith)) {
        throw new IllegalArgumentException(
            "Host key id '" + key.id() + "' must start with one of " + ALLOWED_PREFIXES);
      }
    }
    this.builtInKeys = all.stream().collect(toImmutableMap(HostKeyDescriptor::id, key -> key));
  }

  /**
   * Returns the descriptor for {@code keyId}: the built-in descriptor if registered, otherwise a
   * minted long-tail descriptor for a {@code host_property::} id, otherwise empty.
   */
  public Optional<HostKeyDescriptor> getKey(String keyId) {
    if (keyId == null) {
      return Optional.empty();
    }
    HostKeyDescriptor builtIn = builtInKeys.get(keyId);
    if (builtIn != null) {
      return Optional.of(builtIn);
    }
    if (keyId.startsWith(HostKeys.PREFIX_HOST_PROPERTY)) {
      return createLongTailHostPropertyKey(keyId.substring(HostKeys.PREFIX_HOST_PROPERTY.length()));
    }
    return Optional.empty();
  }

  /**
   * Mints a long-tail host-property key for a property discovered from data, or returns empty if
   * the property key is null or empty.
   */
  public Optional<HostKeyDescriptor> createLongTailHostPropertyKey(String propertyKey) {
    if (propertyKey == null || propertyKey.trim().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(HostKeys.hostPropertyKey(propertyKey));
  }

  /** All built-in host key descriptors. */
  public ImmutableCollection<HostKeyDescriptor> builtInKeys() {
    return builtInKeys.values();
  }

  /** All built-in host key ids. */
  public ImmutableSet<String> builtInKeyIds() {
    return builtInKeys.keySet();
  }

  /** Returns the display name for {@code keyId} (built-in or long-tail), or empty if unknown. */
  public Optional<String> displayName(String keyId) {
    return getKey(keyId).map(key -> key.display().name());
  }

  /**
   * Derives the {@link LabInfoMask} from the union of every built-in host key's {@code
   * labInfoSources}. Long-tail keys are excluded by construction (not in the built-in set).
   */
  public LabInfoMask deriveLabInfoMask() {
    Set<String> fieldPaths = new LinkedHashSet<>();
    for (HostKeyDescriptor key : builtInKeys.values()) {
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
