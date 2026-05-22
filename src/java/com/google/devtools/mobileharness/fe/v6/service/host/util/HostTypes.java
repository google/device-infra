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

package com.google.devtools.mobileharness.fe.v6.service.host.util;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UiLabType;
import java.util.List;
import java.util.Optional;

/** Utility class for Host types. */
public final class HostTypes {

  public static final String LAB_TYPE_CORE = "Core Lab";
  public static final String LAB_TYPE_FUSION = "Fusion Lab";
  public static final String LAB_TYPE_SATELLITE = "Satellite Lab";
  private static final String LAB_TYPE_SLAAS = "SLaaS";
  private static final String LAB_TYPE_ATE = "ATE Lab";
  private static final String LAB_TYPE_FIELD = "Riemann Field Lab";
  private static final String LAB_TYPE_UNKNOWN = "Unknown";

  private static final ImmutableMap<String, UiLabType> ENUM_NAME_TO_UI_LAB_TYPE =
      ImmutableMap.of(
          "FUSION_LAB", UiLabType.FUSION,
          "SHARED_LAB", UiLabType.CORE,
          "MH_SATELLITE_LAB", UiLabType.SATELLITE,
          "MH_ATE_LAB", UiLabType.ATE,
          "RIEMANN_FIELD_LAB", UiLabType.RIEMANN_FIELD);

  private static final ImmutableMap<String, UiLabType> PROP_TO_UI_LAB_TYPE =
      ImmutableMap.of(
          "core", UiLabType.CORE,
          "slaas", UiLabType.SLAAS,
          "satellite", UiLabType.SATELLITE);

  private static final ImmutableMap<UiLabType, String> UI_LAB_TYPE_TO_DISPLAY_NAME =
      new ImmutableMap.Builder<UiLabType, String>()
          .put(UiLabType.CORE, LAB_TYPE_CORE)
          .put(UiLabType.FUSION, LAB_TYPE_FUSION)
          .put(UiLabType.SATELLITE, LAB_TYPE_SATELLITE)
          .put(UiLabType.SLAAS, LAB_TYPE_SLAAS)
          .put(UiLabType.ATE, LAB_TYPE_ATE)
          .put(UiLabType.RIEMANN_FIELD, LAB_TYPE_FIELD)
          .put(UiLabType.UNKNOWN, LAB_TYPE_UNKNOWN)
          .buildOrThrow();

  public static boolean isCoreOrFusion(List<String> labTypes) {
    return labTypes.contains(LAB_TYPE_CORE) || labTypes.contains(LAB_TYPE_FUSION);
  }

  public static boolean isCoreOrFusion(Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return isCoreOrFusion(determineLabTypeDisplayNames(labInfoOpt, labTypeOpt));
  }

  public static boolean isCoreOrFusionUiLabTypes(List<UiLabType> labTypes) {
    return labTypes.contains(UiLabType.CORE) || labTypes.contains(UiLabType.FUSION);
  }

  // TODO: Better function signature to distinguish between the two sources of lab types.
  public static ImmutableList<UiLabType> determineUiLabTypes(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {

    ImmutableList.Builder<UiLabType> builder = ImmutableList.builder();

    String typeEnumName = labTypeOpt.orElse("LAB_TYPE_UNSPECIFIED");

    String labTypeProp = getHostProperty(labInfoOpt, "lab_type");
    String dmTypeProp = getHostProperty(labInfoOpt, "dm_type");

    // Source 1: lab_type in host properties (Checked first to preserve order)
    if (labTypeProp.equals("slaas")) {
      builder.add(UiLabType.SATELLITE);
      builder.add(UiLabType.SLAAS);
    } else {
      Optional.ofNullable(PROP_TO_UI_LAB_TYPE.get(labTypeProp)).ifPresent(builder::add);
    }

    // Source 2: Release Server Host Info
    Optional.ofNullable(ENUM_NAME_TO_UI_LAB_TYPE.get(typeEnumName)).ifPresent(builder::add);

    // Source 3: dm_type in host properties (only for fusion)
    if (dmTypeProp.equals("fusion")) {
      builder.add(UiLabType.FUSION);
    }

    return builder.build().stream().distinct().collect(toImmutableList());
  }

  /**
   * @deprecated Use {@link #determineUiLabTypes(Optional, Optional)} instead. This is retained for
   *     backward compatibility with older frontends that expect pre-formatted strings.
   */
  @Deprecated
  public static ImmutableList<String> determineLabTypeDisplayNames(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return determineUiLabTypes(labInfoOpt, labTypeOpt).stream()
        .map(type -> UI_LAB_TYPE_TO_DISPLAY_NAME.getOrDefault(type, LAB_TYPE_UNKNOWN))
        .collect(toImmutableList());
  }

  private static String getHostProperty(Optional<LabInfo> labInfoOpt, String key) {
    return labInfoOpt
        .flatMap(
            labInfo ->
                labInfo.getLabServerFeature().getHostProperties().getHostPropertyList().stream()
                    .filter(hp -> hp.getKey().equals(key))
                    .map(HostProperty::getValue)
                    .findFirst())
        .map(Ascii::toLowerCase)
        .orElse("");
  }

  private HostTypes() {}
}
