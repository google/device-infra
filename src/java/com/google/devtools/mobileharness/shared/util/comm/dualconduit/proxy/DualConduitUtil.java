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

package com.google.devtools.mobileharness.shared.util.comm.dualconduit.proxy;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;

/** Utility class for DualConduit. */
public class DualConduitUtil {

  @SuppressWarnings("NonFinalStaticField")
  @VisibleForTesting
  static boolean isXdsBootstrapped =
      Objects.equals(System.getenv("GRPC_XDS_BOOTSTRAP"), "/etc/grpc/bootstrap.json");

  /** Returns whether gRPC xDS is configured with the expected bootstrap file. */
  public static boolean isXdsBootstrapped() {
    return isXdsBootstrapped;
  }

  /**
   * Returns the xDS address of a server for DualConduit.
   *
   * <p>Note: This format is determined by mesh.go in the dualconduit Go package.
   *
   * @param serverType the type of the server
   * @param instanceId the DualConduit instance ID of the server
   */
  public static String getDualConduitXdsAddress(ServerType serverType, String instanceId) {
    return "xds:///" + serverType.toLowerCaseName() + "." + instanceId + ".dcon";
  }

  private DualConduitUtil() {}
}
