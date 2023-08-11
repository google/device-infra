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

package com.google.devtools.mobileharness.shared.version.checker;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.proto.Version.VersionCheckRequest;
import com.google.devtools.mobileharness.shared.version.proto.Version.VersionCheckResponse;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Utility for checking the versions on RPC service side. */
public class ServiceSideVersionChecker {

  /** Version of the current RPC service. */
  private final Version serviceVersion;

  /** The lowest version of the stubs which are compatible with this RPC service. */
  private final Version minStubVersion;

  private final VersionCheckResponse response;

  /**
   * Creates a version checker for checking the versions on RPC service side.
   *
   * @param serviceVersion version of the current RPC service.
   * @param minStubVersion the lowest version of the stubs which are compatible with this RPC
   *     service
   */
  public ServiceSideVersionChecker(Version serviceVersion, Version minStubVersion) {
    this.serviceVersion = serviceVersion;
    this.minStubVersion = minStubVersion;
    this.response =
        VersionCheckResponse.newBuilder().setServiceVersion(serviceVersion.toString()).build();
  }

  /**
   * Checks the version compatibility.
   *
   * @param versionCheckRequest version info from stub
   * @throws MobileHarnessException if there is any version errors
   */
  @CanIgnoreReturnValue
  public VersionCheckResponse checkStub(VersionCheckRequest versionCheckRequest)
      throws MobileHarnessException {
    Version minServiceVersion;
    Version stubVersion;
    try {
      minServiceVersion = new Version(versionCheckRequest.getMinServiceVersion());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.VERSION_SERVICE_FORMAT_ERROR, "Service side version error", e);
    }
    try {
      stubVersion = new Version(versionCheckRequest.getStubVersion());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.VERSION_STUB_FORMAT_ERROR, "Stub side version error", e);
    }
    if (serviceVersion.compareTo(minServiceVersion) < 0) {
      throw new MobileHarnessException(
          BasicErrorId.VERSION_SERVICE_TOO_OLD,
          String.format(
              "Service version is lower than the version required by the stub, "
                  + "actual service version: %s, lowest service version required by the stub: %s",
              serviceVersion.toString(), minServiceVersion.toString()));
    }
    if (stubVersion.compareTo(minStubVersion) < 0) {
      throw new MobileHarnessException(
          BasicErrorId.VERSION_STUB_TOO_OLD,
          String.format(
              "Stub version is lower than the version required by the service, "
                  + "actual stub version: %s, lowest stub version required by the service: %s",
              stubVersion.toString(), minStubVersion.toString()));
    }
    return response;
  }
}
