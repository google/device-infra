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

  /** The canonical single-valued lab types in OmniLab Console. */
  public enum LabType {
    CORE("Core", LAB_TYPE_CORE),
    SLAAS("SLaaS", LAB_TYPE_SLAAS),
    SATELLITE("Satellite", LAB_TYPE_SATELLITE);

    private final String hostDetailDisplayName;
    private final String searchDisplayName;

    LabType(String hostDetailDisplayName, String searchDisplayName) {
      this.hostDetailDisplayName = hostDetailDisplayName;
      this.searchDisplayName = searchDisplayName;
    }

    /** Display name shown on the Host Detail Overview card ("Core", "SLaaS", "Satellite"). */
    public String hostDetailDisplayName() {
      return hostDetailDisplayName;
    }

    /** Display name indexed in Fleet Search ("Core Lab", "SLaaS", "Satellite Lab"). */
    public String searchDisplayName() {
      return searchDisplayName;
    }
  }

  private static final ImmutableMap<String, LabType> ENUM_NAME_TO_LAB_TYPE =
      ImmutableMap.of(
          "SHARED_LAB", LabType.CORE,
          "MH_SATELLITE_LAB", LabType.SATELLITE);

  private static final ImmutableMap<String, LabType> PROP_TO_LAB_TYPE =
      ImmutableMap.of(
          "core", LabType.CORE,
          "slaas", LabType.SLAAS,
          "satellite", LabType.SATELLITE);

  public static boolean isCoreOrFusion(List<String> labTypes) {
    return labTypes.contains(LAB_TYPE_CORE) || labTypes.contains(LAB_TYPE_FUSION);
  }

  public static boolean isCoreOrFusion(Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return isCoreLab(labInfoOpt, labTypeOpt)
        || determineDeviceManagerType(labInfoOpt, labTypeOpt).equals(DEVICE_MANAGER_TYPE_FUSION);
  }

  /** Returns true iff the host's single lab type is {@link LabType#CORE}. */
  public static boolean isCoreLab(Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return determineLabType(labInfoOpt, labTypeOpt)
        .filter(type -> type == LabType.CORE)
        .isPresent();
  }

  /**
   * Determines the single {@link LabType} of the host ({@code CORE}, {@code SLAAS}, or {@code
   * SATELLITE}).
   */
  public static Optional<LabType> determineLabType(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    String labTypeProp = getHostProperty(labInfoOpt, "lab_type");
    LabType fromProp = PROP_TO_LAB_TYPE.get(labTypeProp);
    if (fromProp != null) {
      return Optional.of(fromProp);
    }
    String typeEnumName = labTypeOpt.orElse("LAB_TYPE_UNSPECIFIED");
    return Optional.ofNullable(ENUM_NAME_TO_LAB_TYPE.get(typeEnumName));
  }

  /** Returns the single Host Detail display name ("Core", "SLaaS", or "Satellite"). */
  public static Optional<String> determineHostDetailLabType(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return determineLabType(labInfoOpt, labTypeOpt).map(LabType::hostDetailDisplayName);
  }

  /** Returns the single Fleet Search display name ("Core Lab", "SLaaS", or "Satellite Lab"). */
  public static Optional<String> determineSearchLabType(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return determineLabType(labInfoOpt, labTypeOpt).map(LabType::searchDisplayName);
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
   * @deprecated Use {@link #isCoreOrFusion(Optional, Optional)} instead. Retained for backward
   *     compatibility.
   */
  @Deprecated
  // Retained for backward compatibility with legacy UiLabType callers.
  @SuppressWarnings("deprecation")
  public static boolean isCoreOrFusionUiLabTypes(List<UiLabType> labTypes) {
    return labTypes.contains(UiLabType.CORE) || labTypes.contains(UiLabType.FUSION);
  }

  /**
   * @deprecated Use {@link #determineLabType(Optional, Optional)} instead. Retained for backward
   *     compatibility with older frontends that read {@code ui_lab_types}.
   */
  @Deprecated
  // Retained for backward compatibility with legacy UiLabType callers.
  @SuppressWarnings("deprecation")
  public static ImmutableList<UiLabType> determineUiLabTypes(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return determineLabType(labInfoOpt, labTypeOpt)
        .map(
            type ->
                switch (type) {
                  case CORE -> UiLabType.CORE;
                  case SLAAS -> UiLabType.SLAAS;
                  case SATELLITE -> UiLabType.SATELLITE;
                })
        .map(ImmutableList::of)
        .orElse(ImmutableList.of());
  }

  /**
   * @deprecated Use {@link #determineSearchLabType(Optional, Optional)} instead. Retained for
   *     backward compatibility.
   */
  @Deprecated
  // Retained for backward compatibility with legacy UiLabType callers.
  @SuppressWarnings("deprecation")
  public static String labTypeDisplayName(UiLabType labType) {
    return switch (labType) {
      case CORE -> LAB_TYPE_CORE;
      case SATELLITE -> LAB_TYPE_SATELLITE;
      case SLAAS -> LAB_TYPE_SLAAS;
      default -> LAB_TYPE_UNKNOWN;
    };
  }

  /**
   * @deprecated Use {@link #determineSearchLabType(Optional, Optional)} instead. Retained for
   *     backward compatibility with older frontends that read {@code lab_type_display_names}.
   */
  @Deprecated
  // Retained for backward compatibility with legacy lab_type_display_names callers.
  @SuppressWarnings("deprecation")
  public static ImmutableList<String> determineLabTypeDisplayNames(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {
    return determineUiLabTypes(labInfoOpt, labTypeOpt).stream()
        .map(HostTypes::labTypeDisplayName)
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
