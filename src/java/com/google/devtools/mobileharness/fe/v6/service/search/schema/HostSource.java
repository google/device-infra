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
 * Declarative description of where a host search key's data originates.
 *
 * <p>Exclusively models host-level data origins (fields on {@code LabInfo}, host properties in
 * {@code LabServerFeature.host_properties}, {@code HostInfoService} releases, fan-out provenance,
 * or computed host attributes). Mechanically derives the {@code LabInfoMask} for the core pull.
 */
@AutoOneOf(HostSource.Kind.class)
public abstract class HostSource {

  /** The kind of host data source. */
  public enum Kind {
    /** A typed field on {@code LabQueryProto.LabInfo} (e.g. {@code "lab_status"}). */
    LAB_INFO_FIELD,
    /** A property key in {@code LabServerFeature.host_properties} (e.g. {@code "host_os"}). */
    HOST_PROPERTY,
    /**
     * An enrichment field from Google 1P {@code HostInfoService} (e.g. {@code "release_status"}).
     */
    HOST_INFO,
    /**
     * Stamped request-side provenance during multi-controller fan-out (e.g. {@code
     * "ats_controller"}).
     */
    PROVENANCE,
    /** A computed host value with documented inputs and note (e.g. {@code "connectivity"}). */
    COMPUTED,
  }

  public abstract Kind getKind();

  public abstract String labInfoField();

  /** Creates a {@link HostSource} pointing to a typed field on {@code LabInfo}. */
  public static HostSource labInfoField(String protoPath) {
    return AutoOneOf_HostSource.labInfoField(protoPath);
  }

  public abstract String hostProperty();

  /** Creates a {@link HostSource} pointing to a key in {@code host_properties}. */
  public static HostSource hostProperty(String propertyKey) {
    return AutoOneOf_HostSource.hostProperty(propertyKey);
  }

  public abstract String hostInfo();

  /** Creates a {@link HostSource} pointing to a HostInfoService enrichment field. */
  public static HostSource hostInfo(String fieldName) {
    return AutoOneOf_HostSource.hostInfo(fieldName);
  }

  public abstract String provenance();

  /** Creates a {@link HostSource} for fan-out stamped provenance. */
  public static HostSource provenance(String fieldName) {
    return AutoOneOf_HostSource.provenance(fieldName);
  }

  public abstract ComputedSource computed();

  /** Creates a {@link HostSource} for a computed field with documented inputs and note. */
  public static HostSource computed(ImmutableList<HostSource> maskInputs, String docNote) {
    return AutoOneOf_HostSource.computed(ComputedSource.create(maskInputs, docNote));
  }

  /** A computed host source holding its underlying mask dependencies and note. */
  @com.google.auto.value.AutoValue
  public abstract static class ComputedSource {
    public abstract ImmutableList<HostSource> maskInputs();

    public abstract String docNote();

    public static ComputedSource create(ImmutableList<HostSource> maskInputs, String docNote) {
      return new AutoValue_HostSource_ComputedSource(maskInputs, docNote);
    }
  }
}
