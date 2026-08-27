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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Defines how a device key extracts its value from a {@link DeviceInfo} proto and participates in
 * the {@code DeviceInfoMask}.
 *
 * <p>Each {@link DeviceInfoSource} pairs its {@code DeviceInfoMask} contribution ({@link
 * #maskFieldPaths()} / {@link #maskDimensionNames()}) with its value extractor ({@link #extract}),
 * guaranteeing that mask derivation and proto extraction remain in sync without drift.
 */
public abstract class DeviceInfoSource {

  /** The {@code DeviceInfoMask.field_mask} paths this source requires. */
  public abstract ImmutableList<String> maskFieldPaths();

  /** The {@code DeviceInfoMask} dimension names this source requires. */
  public abstract ImmutableList<String> maskDimensionNames();

  /** Reads the raw value(s) of this source from a {@code DeviceInfo}. */
  public abstract ImmutableList<String> extract(DeviceInfo deviceInfo);

  /**
   * A typed field on {@code DeviceInfo}, named by its {@code DeviceInfoMask} path (for example
   * {@code "device_feature.type"}). The path drives the mask and the getter reads the value; both
   * are given together at the call site, so a reader sees at a glance that they agree. Every
   * derived mask is checked against the proto descriptor in the registry tests, so a mistyped path
   * fails there rather than silently yielding empty values at serving time.
   */
  public static DeviceInfoSource field(
      String protoPath, Function<DeviceInfo, ImmutableList<String>> getter) {
    return new FieldSource(protoPath, getter);
  }

  /** A named dimension in {@code device_feature.composite_dimension}. */
  public static DeviceInfoSource dimension(String name) {
    return new DimensionSource(name);
  }

  private static final class FieldSource extends DeviceInfoSource {
    private final String protoPath;
    private final Function<DeviceInfo, ImmutableList<String>> getter;

    FieldSource(String protoPath, Function<DeviceInfo, ImmutableList<String>> getter) {
      this.protoPath = protoPath;
      this.getter = getter;
    }

    @Override
    public ImmutableList<String> maskFieldPaths() {
      return ImmutableList.of(protoPath);
    }

    @Override
    public ImmutableList<String> maskDimensionNames() {
      return ImmutableList.of();
    }

    @Override
    public ImmutableList<String> extract(DeviceInfo deviceInfo) {
      return getter.apply(deviceInfo);
    }
  }

  private static final class DimensionSource extends DeviceInfoSource {
    /** The one proto field that carries every device dimension, whichever dimension is named. */
    private static final String COMPOSITE_DIMENSION_PATH = "device_feature.composite_dimension";

    private final String name;

    DimensionSource(String name) {
      this.name = name;
    }

    @Override
    public ImmutableList<String> maskFieldPaths() {
      return ImmutableList.of(COMPOSITE_DIMENSION_PATH);
    }

    @Override
    public ImmutableList<String> maskDimensionNames() {
      return ImmutableList.of(name);
    }

    @Override
    public ImmutableList<String> extract(DeviceInfo deviceInfo) {
      DeviceCompositeDimension composite = deviceInfo.getDeviceFeature().getCompositeDimension();
      return Stream.concat(
              composite.getSupportedDimensionList().stream(),
              composite.getRequiredDimensionList().stream())
          .filter(dimension -> dimension.getName().equals(name))
          .map(DeviceDimension::getValue)
          .collect(toImmutableList());
    }
  }
}
