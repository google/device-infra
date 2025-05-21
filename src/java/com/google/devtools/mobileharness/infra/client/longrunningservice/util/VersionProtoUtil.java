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

package com.google.devtools.mobileharness.infra.client.longrunningservice.util;

import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.VersionUtil;
import com.google.devtools.mobileharness.shared.version.proto.VersionProto;
import com.google.devtools.mobileharness.shared.version.proto.VersionProto.BuildVersion;
import com.google.devtools.mobileharness.shared.version.proto.VersionProto.Versions;
import java.util.Optional;

/** Util for generating {@link GetVersionResponse}. */
public class VersionProtoUtil {

  /** Creates a {@link GetVersionResponse} based on the current environment. */
  public static GetVersionResponse createGetVersionResponse() {
    Versions.Builder versions =
        Versions.newBuilder()
            .addVersions(
                VersionProto.Version.newBuilder()
                    .setVersion(Version.LAB_VERSION.toString())
                    .setType("LAB_VERSION"))
            .addVersions(
                VersionProto.Version.newBuilder()
                    .setVersion(Version.CLIENT_VERSION.toString())
                    .setType("CLIENT_VERSION"));
    GetVersionResponse.Builder result =
        GetVersionResponse.newBuilder()
            .setLabVersion(Version.LAB_VERSION.toString())
            .setClientVersion(Version.CLIENT_VERSION.toString())
            .setVersions(versions)
            .setProcessId(ProcessHandle.current().pid());

    Optional<BuildVersion> buildVersion = VersionUtil.getBuildVersion();
    if (buildVersion.isPresent()) {
      versions.setBuildVersion(buildVersion.get());
      result.setGithubVersion(buildVersion.get().getGithubVersion());
    }

    return result.build();
  }

  private VersionProtoUtil() {}
}
