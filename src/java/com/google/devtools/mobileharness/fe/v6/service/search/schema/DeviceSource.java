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

import com.google.auto.value.AutoOneOf;
import com.google.common.collect.ImmutableList;

/**
 * Declarative description of where a device search key's data originates.
 *
 * <p>Exclusively models device-level data origins (fields on {@code DeviceInfo}, dimensions in
 * {@code DeviceFeature.composite_dimension}, device configs, or computed device attributes).
 * Mechanically derives the {@code DeviceInfoMask} for the core pull.
 */
@AutoOneOf(DeviceSource.Kind.class)
public abstract class DeviceSource {

  /** The kind of device data source. */
  public enum Kind {
    /** A typed field on {@code LabQueryProto.DeviceInfo} (e.g. {@code "device_status"}). */
    DEVICE_INFO_FIELD,
    /** A named dimension in {@code DeviceFeature.composite_dimension} (e.g. {@code "model"}). */
    DIMENSION,
    /** A configuration field from {@code ConfigService} (e.g. {@code "wifi_ssid"}). */
    CONFIG,
    /** A computed device value with documented inputs and note. */
    COMPUTED,
  }

  public abstract Kind getKind();

  public abstract String deviceInfoField();

  /** Creates a {@link DeviceSource} pointing to a typed field on {@code DeviceInfo}. */
  public static DeviceSource deviceInfoField(String protoPath) {
    return AutoOneOf_DeviceSource.deviceInfoField(protoPath);
  }

  public abstract String dimension();

  /** Creates a {@link DeviceSource} pointing to a named device dimension. */
  public static DeviceSource dimension(String dimensionName) {
    return AutoOneOf_DeviceSource.dimension(dimensionName);
  }

  public abstract String config();

  /** Creates a {@link DeviceSource} pointing to a config service field. */
  public static DeviceSource config(String fieldName) {
    return AutoOneOf_DeviceSource.config(fieldName);
  }

  public abstract ComputedSource computed();

  /** Creates a {@link DeviceSource} for a computed field with documented inputs and note. */
  public static DeviceSource computed(ImmutableList<DeviceSource> maskInputs, String docNote) {
    return AutoOneOf_DeviceSource.computed(ComputedSource.create(maskInputs, docNote));
  }

  /** A computed device source holding its underlying mask dependencies and note. */
  @com.google.auto.value.AutoValue
  public abstract static class ComputedSource {
    public abstract ImmutableList<DeviceSource> maskInputs();

    public abstract String docNote();

    public static ComputedSource create(ImmutableList<DeviceSource> maskInputs, String docNote) {
      return new AutoValue_DeviceSource_ComputedSource(maskInputs, docNote);
    }
  }
}
