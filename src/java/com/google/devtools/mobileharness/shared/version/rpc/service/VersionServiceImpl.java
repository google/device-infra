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

package com.google.devtools.mobileharness.shared.version.rpc.service;

import com.google.devtools.mobileharness.shared.version.VersionUtil;
import com.google.devtools.mobileharness.shared.version.proto.VersionProto.BuildVersion;
import com.google.devtools.mobileharness.shared.version.proto.VersionProto.Version;
import com.google.devtools.mobileharness.shared.version.proto.VersionProto.Versions;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto.GetVersionRequest;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto.GetVersionResponse;
import java.util.Optional;

/** Implementation of {@code VersionService}. */
public class VersionServiceImpl {

  private final GetVersionResponse response;

  public VersionServiceImpl(Version version) {
    Versions.Builder versions = Versions.newBuilder().addVersions(version);
    GetVersionResponse.Builder response =
        GetVersionResponse.newBuilder().setVersion(version.getVersion()).setVersions(versions);

    // Gets build version.
    Optional<BuildVersion> buildVersion = VersionUtil.getBuildVersion();
    if (buildVersion.isPresent()) {
      versions.setBuildVersion(buildVersion.get());
      response.setGithubVersion(buildVersion.get().getGithubVersion());
    }

    this.response = response.build();
  }

  public GetVersionResponse getVersion(@SuppressWarnings("unused") GetVersionRequest request) {
    return response;
  }
}
