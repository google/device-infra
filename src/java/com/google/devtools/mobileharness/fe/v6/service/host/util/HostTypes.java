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
  private static final String LAB_TYPE_ATE = "ATE Lab";
  private static final String LAB_TYPE_FIELD = "Riemann Field Lab";
  private static final String LAB_TYPE_UNKNOWN = "Unknown";

  public static final String LAB_TYPE_DISPLAY_CORE = "Core";
  public static final String LAB_TYPE_DISPLAY_SLAAS = "SLaaS";
  public static final String LAB_TYPE_DISPLAY_SATELLITE = "Satellite";

  public static final String DEVICE_MANAGER_TYPE_FUSION = "Fusion";
  public static final String DEVICE_MANAGER_TYPE_MH = "MH";

  private static final ImmutableMap<String, String> PROP_TO_LAB_TYPE_DISPLAY =
      ImmutableMap.of(
          "core", LAB_TYPE_DISPLAY_CORE,
          "slaas", LAB_TYPE_DISPLAY_SLAAS,
          "satellite", LAB_TYPE_DISPLAY_SATELLITE);

  private static final ImmutableMap<String, String> ENUM_NAME_TO_LAB_TYPE_DISPLAY =
      ImmutableMap.of(
          "SHARED_LAB", LAB_TYPE_DISPLAY_CORE,
          "MH_SATELLITE_LAB", LAB_TYPE_DISPLAY_SATELLITE);

  private static final ImmutableMap<String, String> DISPLAY_TO_SEARCH_LAB_TYPE =
      ImmutableMap.of(
          LAB_TYPE_DISPLAY_CORE, LAB_TYPE_CORE,
          LAB_TYPE_DISPLAY_SLAAS, LAB_TYPE_SLAAS,
          LAB_TYPE_DISPLAY_SATELLITE, LAB_TYPE_SATELLITE);

  // Retained for backward compatibility with legacy HostOverview.ui_lab_types field.
  @SuppressWarnings("deprecation")
  private static final ImmutableMap<String, UiLabType> LEGACY_ENUM_NAME_TO_UI_LAB_TYPE =
      ImmutableMap.of(
          "FUSION_LAB", UiLabType.FUSION,
          "SHARED_LAB", UiLabType.CORE,
          "MH_SATELLITE_LAB", UiLabType.SATELLITE,
          "MH_ATE_LAB", UiLabType.ATE,
          "RIEMANN_FIELD_LAB", UiLabType.RIEMANN_FIELD);

  // Retained for backward compatibility with legacy HostOverview.ui_lab_types field.
  @SuppressWarnings("deprecation")
  private static final ImmutableMap<String, UiLabType> LEGACY_PROP_TO_UI_LAB_TYPE =
      ImmutableMap.of(
          "core", UiLabType.CORE,
          "slaas", UiLabType.SLAAS,
          "satellite", UiLabType.SATELLITE);

  // Retained for backward compatibility with legacy HostOverview.lab_type_display_names field.
  @SuppressWarnings("deprecation")
  private static final ImmutableMap<UiLabType, String> LEGACY_UI_LAB_TYPE_TO_DISPLAY_NAME =
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
    return isCoreLab(labInfoOpt, labTypeOpt)
        || determineDeviceManagerType(labInfoOpt, labTypeOpt).equals(DEVICE_MANAGER_TYPE_FUSION);
  }

  /** Returns true if the host's single lab type is Core. */
  public static boolean isCoreLab(Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return determineLabType(labInfoOpt, labTypeOpt)
        .filter(LAB_TYPE_DISPLAY_CORE::equals)
        .isPresent();
  }

  /** Returns true if the host's single lab type is Satellite. */
  public static boolean isSatelliteLab(Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return determineLabType(labInfoOpt, labTypeOpt)
        .filter(LAB_TYPE_DISPLAY_SATELLITE::equals)
        .isPresent();
  }

  /**
   * Determines the single Host Detail display lab type of the host ({@code "Core"}, {@code
   * "SLaaS"}, or {@code "Satellite"}).
   */
  public static Optional<String> determineLabType(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    String labTypeProp = getHostProperty(labInfoOpt, "lab_type");
    String fromProp = PROP_TO_LAB_TYPE_DISPLAY.get(labTypeProp);
    if (fromProp != null) {
      return Optional.of(fromProp);
    }
    String typeEnumName = labTypeOpt.orElse("LAB_TYPE_UNSPECIFIED");
    return Optional.ofNullable(ENUM_NAME_TO_LAB_TYPE_DISPLAY.get(typeEnumName));
  }

  /**
   * Determines the single Fleet Search lab type value of the host ({@code "Core Lab"}, {@code
   * "SLaaS"}, or {@code "Satellite Lab"}).
   */
  public static Optional<String> determineSearchLabType(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return determineLabType(labInfoOpt, labTypeOpt).map(DISPLAY_TO_SEARCH_LAB_TYPE::get);
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

  /**
   * @deprecated Use {@link #isCoreOrFusion(Optional, Optional)} instead.
   */
  @Deprecated
  // Retained for backward compatibility with callers still passing deprecated UiLabType.
  @SuppressWarnings("deprecation")
  public static boolean isCoreOrFusionUiLabTypes(List<UiLabType> labTypes) {
    return labTypes.contains(UiLabType.CORE) || labTypes.contains(UiLabType.FUSION);
  }

  /**
   * @deprecated Use {@link #determineLabType(Optional, Optional)} instead. Retained for backward
   *     compatibility with older frontends that read {@code HostOverview.ui_lab_types}.
   */
  @Deprecated
  // Retained for backward compatibility with legacy HostOverview.ui_lab_types field.
  @SuppressWarnings("deprecation")
  public static ImmutableList<UiLabType> determineUiLabTypes(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    ImmutableList.Builder<UiLabType> builder = ImmutableList.builder();
    String typeEnumName = labTypeOpt.orElse("LAB_TYPE_UNSPECIFIED");
    String labTypeProp = getHostProperty(labInfoOpt, "lab_type");
    String dmTypeProp = getHostProperty(labInfoOpt, "dm_type");

    if (labTypeProp.equals("slaas")) {
      builder.add(UiLabType.SATELLITE).add(UiLabType.SLAAS);
    } else {
      Optional.ofNullable(LEGACY_PROP_TO_UI_LAB_TYPE.get(labTypeProp)).ifPresent(builder::add);
    }

    Optional.ofNullable(LEGACY_ENUM_NAME_TO_UI_LAB_TYPE.get(typeEnumName)).ifPresent(builder::add);

    if (dmTypeProp.equals("fusion")) {
      builder.add(UiLabType.FUSION);
    }

    return builder.build().stream().distinct().collect(toImmutableList());
  }

  /**
   * @deprecated Use {@link #determineSearchLabType(Optional, Optional)} instead.
   */
  @Deprecated
  // Retained for backward compatibility with callers still passing deprecated UiLabType.
  @SuppressWarnings("deprecation")
  public static String labTypeDisplayName(UiLabType labType) {
    return LEGACY_UI_LAB_TYPE_TO_DISPLAY_NAME.getOrDefault(labType, LAB_TYPE_UNKNOWN);
  }

  /**
   * @deprecated Use {@link #determineLabType(Optional, Optional)} instead. This is retained for
   *     backward compatibility with older frontends that expect pre-formatted strings.
   */
  @Deprecated
  // Retained for backward compatibility with legacy HostOverview.lab_type_display_names field.
  @SuppressWarnings("deprecation")
  public static ImmutableList<String> determineLabTypeDisplayNames(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return determineUiLabTypes(labInfoOpt, labTypeOpt).stream()
        .map(type -> LEGACY_UI_LAB_TYPE_TO_DISPLAY_NAME.getOrDefault(type, LAB_TYPE_UNKNOWN))
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
