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

package com.google.devtools.mobileharness.infra.ats.common;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;

/** Device details. */
@AutoValue
public abstract class DeviceDetails {

  public abstract String id();

  public abstract Optional<String> productType();

  public abstract Optional<String> productVariant();

  /** Device properties. Key is the device property name, value is its property value. */
  public abstract ImmutableMap<String, String> deviceProperties();

  public static Builder builder() {
    return new AutoValue_DeviceDetails.Builder().setDeviceProperties(ImmutableMap.of());
  }

  /** Builder for {@link DeviceDetails}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setId(String id);

    public abstract Builder setProductType(String productType);

    public abstract Builder setProductVariant(String productVariant);

    public abstract Builder setDeviceProperties(ImmutableMap<String, String> deviceProperties);

    public abstract DeviceDetails build();
  }
}
