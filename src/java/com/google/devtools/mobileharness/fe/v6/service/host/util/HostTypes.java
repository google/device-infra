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
  public static final String LAB_TYPE_SLAAS = "SLaaS";
  public static final String DEVICE_MANAGER_TYPE_FUSION = "Fusion";
  public static final String DEVICE_MANAGER_TYPE_MH = "MH";
  private static final String LAB_TYPE_UNKNOWN = "Unknown";

  private static final ImmutableMap<String, UiLabType> ENUM_NAME_TO_UI_LAB_TYPE =
      ImmutableMap.of(
          "SHARED_LAB", UiLabType.CORE,
          "MH_SATELLITE_LAB", UiLabType.SATELLITE);

  private static final ImmutableMap<String, UiLabType> PROP_TO_UI_LAB_TYPE =
      ImmutableMap.of(
          "core", UiLabType.CORE,
          "slaas", UiLabType.SLAAS,
          "satellite", UiLabType.SATELLITE);

  private static final ImmutableMap<UiLabType, String> UI_LAB_TYPE_TO_DISPLAY_NAME =
      new ImmutableMap.Builder<UiLabType, String>()
          .put(UiLabType.CORE, LAB_TYPE_CORE)
          .put(UiLabType.SATELLITE, LAB_TYPE_SATELLITE)
          .put(UiLabType.SLAAS, LAB_TYPE_SLAAS)
          .put(UiLabType.UNKNOWN, LAB_TYPE_UNKNOWN)
          .buildOrThrow();

  public static boolean isCoreOrFusion(List<String> labTypes) {
    return labTypes.contains(LAB_TYPE_CORE) || labTypes.contains(LAB_TYPE_FUSION);
  }

  public static boolean isCoreOrFusion(Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return determineUiLabType(labInfoOpt, labTypeOpt)
            .filter(type -> type == UiLabType.CORE)
            .isPresent()
        || determineDeviceManagerType(labInfoOpt, labTypeOpt).equals(DEVICE_MANAGER_TYPE_FUSION);
  }

  public static boolean isCoreOrFusionUiLabTypes(List<UiLabType> labTypes) {
    return labTypes.contains(UiLabType.CORE) || labTypes.contains(UiLabType.FUSION);
  }

  /**
   * Determines the single semantic {@link UiLabType} of the host ({@code CORE}, {@code SLAAS}, or
   * {@code SATELLITE}).
   */
  public static Optional<UiLabType> determineUiLabType(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    String labTypeProp = getHostProperty(labInfoOpt, "lab_type");
    UiLabType fromProp = PROP_TO_UI_LAB_TYPE.get(labTypeProp);
    if (fromProp != null) {
      return Optional.of(fromProp);
    }
    String typeEnumName = labTypeOpt.orElse("LAB_TYPE_UNSPECIFIED");
    return Optional.ofNullable(ENUM_NAME_TO_UI_LAB_TYPE.get(typeEnumName));
  }

  public static ImmutableList<UiLabType> determineUiLabTypes(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return determineUiLabType(labInfoOpt, labTypeOpt)
        .map(ImmutableList::of)
        .orElse(ImmutableList.of());
  }

  /**
   * Determines the Device Manager Type of the host ({@code "Fusion"} when {@code dm_type ==
   * "fusion"} or {@code labType == "FUSION_LAB"}, otherwise {@code "MH"}).
   */
  public static String determineDeviceManagerType(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    if (getHostProperty(labInfoOpt, "dm_type").equals("fusion")
        || labTypeOpt.filter("FUSION_LAB"::equals).isPresent()) {
      return DEVICE_MANAGER_TYPE_FUSION;
    }
    return DEVICE_MANAGER_TYPE_MH;
  }

  /** Returns true iff the Release Server {@code labType} is {@code "MH_ATE_LAB"}. */
  public static boolean isAteLab(Optional<String> labTypeOpt) {
    return labTypeOpt.filter("MH_ATE_LAB"::equals).isPresent();
  }

  /** Returns the user-facing display name for a {@link UiLabType} (for example "Core Lab"). */
  public static String labTypeDisplayName(UiLabType labType) {
    return UI_LAB_TYPE_TO_DISPLAY_NAME.getOrDefault(labType, LAB_TYPE_UNKNOWN);
  }

  /**
   * @deprecated Use {@link #determineUiLabType(Optional, Optional)} instead. This is retained for
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
