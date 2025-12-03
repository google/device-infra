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
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import java.util.Optional;
import java.util.stream.Stream;

/** Utility class for Host types. */
public final class HostTypes {

  private static final String LAB_TYPE_CORE = "Core Lab";
  private static final String LAB_TYPE_FUSION = "Fusion Lab";
  private static final String LAB_TYPE_SATELLITE = "Satellite Lab";
  private static final String LAB_TYPE_SLAAS = "Satellite Lab (SLaaS)";
  private static final String LAB_TYPE_ATE = "ATE Lab";
  private static final String LAB_TYPE_FIELD = "Riemann Field Lab";
  private static final String LAB_TYPE_UNKNOWN = "Unknown";

  public static ImmutableList<String> determineLabTypeDisplayNames(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {

    Optional<String> baseCategoryOpt = determineBaseCategory(labInfoOpt, labTypeOpt);
    ImmutableList<String> additionalTags = getAdditionalTags(labTypeOpt);

    if (baseCategoryOpt.isEmpty() && additionalTags.isEmpty()) {
      return ImmutableList.of(LAB_TYPE_UNKNOWN);
    }

    return Stream.concat(baseCategoryOpt.stream(), additionalTags.stream())
        .distinct()
        .collect(toImmutableList());
  }

  private static Optional<String> determineBaseCategory(
      Optional<LabInfo> labInfoOpt, Optional<String> labTypeOpt) {

    Optional<String> labTypePropOpt =
        labInfoOpt.flatMap(
            labInfo ->
                labInfo.getLabServerFeature().getHostProperties().getHostPropertyList().stream()
                    .filter(hp -> hp.getKey().equals("lab_type"))
                    .map(HostProperty::getValue)
                    .findFirst());
    String labTypeProp = labTypePropOpt.map(Ascii::toLowerCase).orElse("");

    String typeEnumName = labTypeOpt.orElse("LAB_TYPE_UNSPECIFIED");

    // Determine Base Category with strict priority
    if (typeEnumName.equals("FUSION_LAB") || labTypeProp.equals("fusion")) {
      return Optional.of(LAB_TYPE_FUSION);
    }
    if (typeEnumName.equals("SHARED_LAB") || labTypeProp.equals("core")) {
      return Optional.of(LAB_TYPE_CORE);
    }
    if (labTypeProp.equals("slaas")) {
      return Optional.of(LAB_TYPE_SLAAS);
    }
    if (typeEnumName.equals("MH_SATELLITE_LAB")
        || typeEnumName.equals("MH_ATE_LAB")
        || typeEnumName.equals("RIEMANN_FIELD_LAB")
        || labTypeProp.equals("satellite")) {
      return Optional.of(LAB_TYPE_SATELLITE);
    }

    return Optional.empty();
  }

  private static ImmutableList<String> getAdditionalTags(Optional<String> labTypeOpt) {
    if (labTypeOpt.isEmpty()) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<String> additionalTagsBuilder = ImmutableList.builder();
    String typeEnumName = labTypeOpt.get();

    if (typeEnumName.equals("MH_ATE_LAB")) {
      additionalTagsBuilder.add(LAB_TYPE_ATE);
    }
    if (typeEnumName.equals("RIEMANN_FIELD_LAB")) {
      additionalTagsBuilder.add(LAB_TYPE_FIELD);
    }
    return additionalTagsBuilder.build();
  }

  private HostTypes() {}
}
