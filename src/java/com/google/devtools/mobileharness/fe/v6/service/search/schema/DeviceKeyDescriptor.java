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

/**
 * The declarative metadata specification for a MobileHarness FE v6 device search key.
 *
 * <p>Strictly bound to {@link DeviceSource} to prevent compile-time source mismatch. A device key
 * carries a single {@link #displayName()} because device keys never cross-render into host search
 * (host results have host columns, not device columns).
 */
@AutoValue
public abstract class DeviceKeyDescriptor {

  /** The unique, type-safe identifier of the device key (e.g. {@code "device_field::uuid"}). */
  public abstract String id();

  /** The device-level data source declaration. */
  public abstract DeviceSource source();

  /** The user-facing label in the device search table/filters (e.g. {@code "Model"}). */
  public abstract String displayName();

  /** Whether the key is grammatically plural (e.g. "Owners are" vs "Model is"). */
  public abstract boolean isPlural();

  /** Creates a builder for {@link DeviceKeyDescriptor}. */
  public static Builder builder() {
    return new AutoValue_DeviceKeyDescriptor.Builder().setIsPlural(false);
  }

  /** Builder for {@link DeviceKeyDescriptor}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setId(String id);

    public abstract Builder setSource(DeviceSource source);

    public abstract Builder setDisplayName(String displayName);

    public abstract Builder setIsPlural(boolean isPlural);

    public abstract DeviceKeyDescriptor build();
  }
}
