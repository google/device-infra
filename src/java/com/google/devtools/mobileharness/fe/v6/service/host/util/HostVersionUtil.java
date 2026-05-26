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

import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostReleaseInfo;
import java.util.Optional;

/** Stateless utility class for host version operations. */
public final class HostVersionUtil {

  private HostVersionUtil() {}

  /** Resolves the current running version from LabInfo or HostReleaseInfo. */
  public static Optional<String> resolveCurrentVersion(
      Optional<LabInfo> labInfoOpt, Optional<HostReleaseInfo> hostReleaseInfoOpt) {
    Optional<String> propertyVersionOpt =
        labInfoOpt.flatMap(
            labInfo ->
                labInfo.getLabServerFeature().getHostProperties().getHostPropertyList().stream()
                    .filter(p -> p.getKey().equals("host_version"))
                    .map(HostProperty::getValue)
                    .findFirst());

    if (propertyVersionOpt.isPresent()) {
      return propertyVersionOpt;
    } else if (hostReleaseInfoOpt.isPresent()) {
      return hostReleaseInfoOpt
          .get()
          .labServerReleaseInfo()
          .flatMap(HostReleaseInfo.ComponentInfo::version);
    }
    return Optional.empty();
  }

  /** Normalizes a version string by trimming and removing leading 'v'. */
  public static String normalizeVersion(String version) {
    if (version == null) {
      return "";
    }
    return version.trim().replaceAll("^v", "");
  }
}
