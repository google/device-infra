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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * The declarative metadata for one key usable in host search.
 *
 * <p>A host key declares its {@link #labInfoSources()}, whose union drives the {@code LabInfoMask}
 * and whose extractors read values from {@code LabInfo}. Keys sourced from other data services
 * (such as release status from HostInfoService, or controller provenance from the partner
 * aggregator) do not extract from {@code GetLabInfo} and leave this list empty.
 *
 * <p>A host key carries a single {@link #display()} (its host-search label). When a host attribute
 * is also wanted in device search it is projected as a separate {@link DeviceKeyDescriptor} with
 * its own device-search name; the host descriptor never carries a device-search name.
 *
 * <p>{@link #isLongTail()} is {@code false} for a built-in host key and {@code true} for a host
 * property minted on demand by the registry. See {@link DeviceKeyDescriptor#isLongTail()}.
 */
@AutoValue
public abstract class HostKeyDescriptor implements KeyDescriptor {

  /** The unique, type-safe identifier of the host key (e.g. {@code "host_field::host_name"}). */
  @Override
  public abstract String id();

  /** {@code GetLabInfo} LabInfo extractions; union drives the {@code LabInfoMask}. */
  public abstract ImmutableList<LabInfoSource> labInfoSources();

  /** The host-search display (name + plural grammar). */
  @Override
  public abstract KeyDisplay display();

  /** The host-search aliases; see {@link KeyDescriptor#aliases()}. */
  @Override
  public abstract ImmutableSet<String> aliases();

  /** Whether this descriptor was minted for a discovered (non-built-in) host property. */
  @Override
  public abstract boolean isLongTail();

  /** Whether this descriptor represents a host property. */
  @Override
  public abstract boolean isHostProperty();

  /** Whether this descriptor represents a native host field. */
  public boolean isHostField() {
    return !isHostProperty();
  }

  /** Returns the bare name of this key without its namespace prefix. */
  @Override
  public String bareName() {
    int separator = id().lastIndexOf("::");
    return separator >= 0 ? id().substring(separator + 2) : id();
  }

  /** Creates a builder for {@link HostKeyDescriptor}. */
  public static Builder builder() {
    return new AutoValue_HostKeyDescriptor.Builder()
        .setLabInfoSources(ImmutableList.of())
        .setAliases(ImmutableSet.of())
        .setIsLongTail(false)
        .setIsHostProperty(false);
  }

  /** Builder for {@link HostKeyDescriptor}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setId(String id);

    public abstract Builder setLabInfoSources(ImmutableList<LabInfoSource> sources);

    /** Convenience for the common single lab-source case. */
    public final Builder setLabInfoSource(LabInfoSource source) {
      return setLabInfoSources(ImmutableList.of(source));
    }

    public abstract Builder setDisplay(KeyDisplay display);

    public abstract Builder setAliases(ImmutableSet<String> aliases);

    /** Convenience for declaring aliases inline in a catalog. */
    public final Builder setAliases(String... aliases) {
      return setAliases(ImmutableSet.copyOf(aliases));
    }

    public abstract Builder setIsLongTail(boolean isLongTail);

    public abstract Builder setIsHostProperty(boolean isHostProperty);

    public abstract HostKeyDescriptor build();
  }
}
