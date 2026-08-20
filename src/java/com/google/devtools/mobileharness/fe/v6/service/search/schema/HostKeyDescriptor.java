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
 * The declarative metadata specification for a MobileHarness FE v6 host search key.
 *
 * <p>Strictly bound to {@link HostSource} to prevent compile-time source mismatch. A host key
 * carries two names because it cross-renders: it appears in host search (as {@link
 * #hostSearchName()}, e.g. {@code "Lab Server Connectivity"}) and, stamped onto devices, in device
 * search (as {@link #deviceSearchName()}, e.g. {@code "Host Lab Server Connectivity"}). Naming both
 * explicitly avoids mechanical "Host " prefixing errors.
 */
@AutoValue
public abstract class HostKeyDescriptor {

  /** The unique, type-safe identifier of the host key (e.g. {@code "host_field::host_name"}). */
  public abstract String id();

  /** The host-level data source declaration. */
  public abstract HostSource source();

  /** The user-facing label when the host key is shown (cross-entity) in device search. */
  public abstract String deviceSearchName();

  /** The user-facing label when the host key is shown in host search. */
  public abstract String hostSearchName();

  /** Whether the key is grammatically plural (e.g. "Owners are" vs "Model is"). */
  public abstract boolean isPlural();

  /** Creates a builder for {@link HostKeyDescriptor}. */
  public static Builder builder() {
    return new AutoValue_HostKeyDescriptor.Builder().setIsPlural(false);
  }

  /** Builder for {@link HostKeyDescriptor}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setId(String id);

    public abstract Builder setSource(HostSource source);

    public abstract Builder setDeviceSearchName(String deviceSearchName);

    public abstract Builder setHostSearchName(String hostSearchName);

    public abstract Builder setIsPlural(boolean isPlural);

    public abstract HostKeyDescriptor build();
  }
}
