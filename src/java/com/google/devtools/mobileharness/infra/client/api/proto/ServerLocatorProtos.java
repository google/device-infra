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

package com.google.devtools.mobileharness.infra.client.api.proto;

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import com.google.devtools.mobileharness.infra.client.api.proto.ServerLocatorProto.GrpcServerLocator;
import com.google.devtools.mobileharness.infra.client.api.proto.ServerLocatorProto.ServerLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.Optional;

/** Utility class for ServerLocatorProto. */
public final class ServerLocatorProtos {

  /** Gets the master server locator from the job info. */
  public static Optional<ServerLocator> getMasterServerLocator(JobInfo jobInfo) {
    if (jobInfo.params().getBool(JobInfo.PARAM_USE_GRPC_ROUTER, false)) {
      return Optional.empty();
    } else {
      return jobInfo
          .params()
          .getOptional(JobInfo.PARAM_MASTER_LOCAL_GRPC_TARGET)
          .map(
              s ->
                  ServerLocator.newBuilder()
                      .setGrpcServerLocator(parseGrpcServerLocator(s))
                      .build());
    }
  }

  /**
   * Parses a grpc target into a structured {@link GrpcServerLocator}.
   *
   * @throws IllegalStateException if the target is not a valid grpc target.
   */
  public static GrpcServerLocator parseGrpcServerLocator(String grpcServerTarget) {
    HostAndPort hostAndPort = HostAndPort.fromString(grpcServerTarget);
    GrpcServerLocator.Builder builder =
        GrpcServerLocator.newBuilder().setPort(hostAndPort.getPort());
    if (InetAddresses.isInetAddress(hostAndPort.getHost())) {
      builder.setIp(hostAndPort.getHost());
    } else {
      builder.setHostname(hostAndPort.getHost());
    }
    return builder.build();
  }

  private ServerLocatorProtos() {}
}
