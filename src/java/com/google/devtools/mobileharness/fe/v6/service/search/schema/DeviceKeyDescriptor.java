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

/**
 * The declarative metadata for one key usable in device search: either a device-native key or a
 * host key projected (cross-entity) into device search.
 *
 * <p>{@link #deviceInfoSources()} and {@link #labInfoSources()} declare the key's {@code
 * GetLabInfo} proto extractions and mask contributions: {@link #deviceInfoSources()} extract from
 * {@code DeviceInfo} for the {@code DeviceInfoMask}, while {@link #labInfoSources()} extract from
 * {@code LabInfo} (used by projected host keys to reuse the host key's lab extraction and feed the
 * {@code LabInfoMask}). Keys whose values come from other data services (such as ConfigService or
 * HostInfoService) do not extract from {@code GetLabInfo} and leave these lists empty.
 *
 * <p>A device key carries a single {@link #display()} (its device-search label). When a host
 * attribute is shown in device search it is a projected key here with its own device-search name;
 * the host descriptor keeps its host-search name.
 *
 * <p>{@link #isLongTail()} is {@code false} for a built-in key (declared in a catalog) and {@code
 * true} for a key minted on demand by the registry for a discovered dimension or host property. It
 * is stamped at creation and never hand-set at a call site: a built-in and a minted descriptor are
 * the same type, and only the registry knows which is which, so it records that fact here.
 */
@AutoValue
public abstract class DeviceKeyDescriptor {

  /** The unique, type-safe identifier of the key (e.g. {@code "device_field::uuid"}). */
  public abstract String id();

  /** {@code GetLabInfo} DeviceInfo extractions; union drives the {@code DeviceInfoMask}. */
  public abstract ImmutableList<DeviceInfoSource> deviceInfoSources();

  /**
   * {@code GetLabInfo} LabInfo extractions (for projected host keys); union drives the {@code
   * LabInfoMask}.
   */
  public abstract ImmutableList<LabInfoSource> labInfoSources();

  /** The device-search display (name + plural grammar). */
  public abstract KeyDisplay display();

  /** Whether this descriptor was minted for a discovered (non-built-in) key. */
  public abstract boolean isLongTail();

  /** Creates a builder for {@link DeviceKeyDescriptor}. */
  public static Builder builder() {
    return new AutoValue_DeviceKeyDescriptor.Builder()
        .setDeviceInfoSources(ImmutableList.of())
        .setLabInfoSources(ImmutableList.of())
        .setIsLongTail(false);
  }

  /** Builder for {@link DeviceKeyDescriptor}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setId(String id);

    public abstract Builder setDeviceInfoSources(ImmutableList<DeviceInfoSource> sources);

    /** Convenience for the common single device-source case. */
    public final Builder setDeviceInfoSource(DeviceInfoSource source) {
      return setDeviceInfoSources(ImmutableList.of(source));
    }

    public abstract Builder setLabInfoSources(ImmutableList<LabInfoSource> sources);

    /** Convenience for the common single lab-source case (a projected host key). */
    public final Builder setLabInfoSource(LabInfoSource source) {
      return setLabInfoSources(ImmutableList.of(source));
    }

    public abstract Builder setDisplay(KeyDisplay display);

    public abstract Builder setIsLongTail(boolean isLongTail);

    public abstract DeviceKeyDescriptor build();
  }
}
