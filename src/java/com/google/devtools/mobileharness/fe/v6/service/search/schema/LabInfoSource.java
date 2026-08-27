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
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import java.util.function.Function;

/**
 * Defines how a host key extracts its value from a {@link LabInfo} proto and participates in the
 * {@code LabInfoMask}.
 *
 * <p>Each {@link LabInfoSource} pairs its {@code LabInfoMask} contribution ({@link
 * #maskFieldPaths()}) with its value extractor ({@link #extract}), guaranteeing that mask
 * derivation and proto extraction remain in sync without drift.
 */
public abstract class LabInfoSource {

  /** The {@code LabInfoMask.field_mask} paths this source requires. */
  public abstract ImmutableList<String> maskFieldPaths();

  /** Reads the raw value(s) of this source from a {@code LabInfo}. */
  public abstract ImmutableList<String> extract(LabInfo labInfo);

  /**
   * A typed field on {@code LabInfo}, named by its {@code LabInfoMask} path (for example {@code
   * "lab_locator.host_name"}). The path drives the mask and the getter reads the value; both are
   * given together at the call site, so a reader sees at a glance that they agree. Every derived
   * mask is checked against the proto descriptor in the registry tests, so a mistyped path fails
   * there rather than silently yielding empty values at serving time.
   */
  public static LabInfoSource field(
      String protoPath, Function<LabInfo, ImmutableList<String>> getter) {
    return new FieldSource(protoPath, getter);
  }

  /**
   * A property key in {@code lab_server_feature.host_properties}. All properties are pulled by one
   * blanket mask path, so the mask contribution is the same regardless of the specific key.
   */
  public static LabInfoSource hostProperty(String key) {
    return new HostPropertySource(key);
  }

  private static final class FieldSource extends LabInfoSource {
    private final String protoPath;
    private final Function<LabInfo, ImmutableList<String>> getter;

    FieldSource(String protoPath, Function<LabInfo, ImmutableList<String>> getter) {
      this.protoPath = protoPath;
      this.getter = getter;
    }

    @Override
    public ImmutableList<String> maskFieldPaths() {
      return ImmutableList.of(protoPath);
    }

    @Override
    public ImmutableList<String> extract(LabInfo labInfo) {
      return getter.apply(labInfo);
    }
  }

  private static final class HostPropertySource extends LabInfoSource {
    /** The one proto field that carries every host property, whichever property key is named. */
    private static final String HOST_PROPERTIES_PATH = "lab_server_feature.host_properties";

    private final String key;

    HostPropertySource(String key) {
      this.key = key;
    }

    @Override
    public ImmutableList<String> maskFieldPaths() {
      return ImmutableList.of(HOST_PROPERTIES_PATH);
    }

    @Override
    public ImmutableList<String> extract(LabInfo labInfo) {
      return labInfo.getLabServerFeature().getHostProperties().getHostPropertyList().stream()
          .filter(property -> property.getKey().equals(key))
          .map(HostProperty::getValue)
          .collect(toImmutableList());
    }
  }
}
