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

package com.google.devtools.mobileharness.fe.v6.service.host.provider;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DiagnosticLink;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.List;
import java.util.Optional;

/** Provides auxiliary information about hosts, such as release info and pass-through flags. */
public interface HostAuxiliaryInfoProvider {

  /** Fetches release information and host attributes from internal services. */
  ListenableFuture<Optional<HostReleaseInfo>> getHostReleaseInfo(
      String hostName, UniverseScope universe);

  /**
   * @deprecated Use {@link #getHostReleaseInfo(String, UniverseScope)} instead. TODO: Remove after
   *     all callers are migrated to UniverseScope.
   */
  @Deprecated
  default ListenableFuture<Optional<HostReleaseInfo>> getHostReleaseInfo(
      String hostName, String universe) {
    return getHostReleaseInfo(hostName, UniverseScope.fromString(universe));
  }

  /**
   * @deprecated Use {@link #getHostReleaseInfo(String, UniverseScope)} instead. TODO: Remove after
   *     all callers are migrated to UniverseScope.
   */
  @Deprecated
  default ListenableFuture<Optional<HostReleaseInfo>> getHostReleaseInfo(String hostName) {
    return getHostReleaseInfo(hostName, new UniverseScope.SelfUniverse());
  }

  /** Fetches the legacy pass-through flags for the lab server. */
  ListenableFuture<Optional<String>> getPassThroughFlags(String hostName, UniverseScope universe);

  /**
   * @deprecated Use {@link #getPassThroughFlags(String, UniverseScope)} instead. TODO: Remove after
   *     all callers are migrated to UniverseScope.
   */
  @Deprecated
  default ListenableFuture<Optional<String>> getPassThroughFlags(String hostName, String universe) {
    return getPassThroughFlags(hostName, UniverseScope.fromString(universe));
  }

  /**
   * @deprecated Use {@link #getPassThroughFlags(String, UniverseScope)} instead. TODO: Remove after
   *     all callers are migrated to UniverseScope.
   */
  @Deprecated
  default ListenableFuture<Optional<String>> getPassThroughFlags(String hostName) {
    return getPassThroughFlags(hostName, new UniverseScope.SelfUniverse());
  }

  /** Fetches the diagnostic links for the host. */
  ListenableFuture<List<DiagnosticLink>> getDiagnosticLinks(
      String hostName, Optional<String> labType, UniverseScope universe);

  /**
   * @deprecated Use {@link #getDiagnosticLinks(String, Optional, UniverseScope)} instead. TODO:
   *     Remove after all callers are migrated to UniverseScope.
   */
  @Deprecated
  default ListenableFuture<List<DiagnosticLink>> getDiagnosticLinks(
      String hostName, Optional<String> labType, String universe) {
    return getDiagnosticLinks(hostName, labType, UniverseScope.fromString(universe));
  }

  /**
   * @deprecated Use {@link #getDiagnosticLinks(String, Optional, UniverseScope)} instead. TODO:
   *     Remove after all callers are migrated to UniverseScope.
   */
  @Deprecated
  default ListenableFuture<List<DiagnosticLink>> getDiagnosticLinks(
      String hostName, Optional<String> labType) {
    return getDiagnosticLinks(hostName, labType, new UniverseScope.SelfUniverse());
  }
}
