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

package com.google.devtools.mobileharness.infra.client.api.util.serverlocator;

import static com.google.common.net.HostAndPort.fromHost;
import static com.google.common.net.HostAndPort.fromParts;
import static com.google.common.net.InetAddresses.forString;
import static com.google.common.net.InetAddresses.isInetAddress;
import static com.google.common.net.InetAddresses.toAddrString;
import static com.google.devtools.mobileharness.shared.util.comm.relay.DestinationUtils.createDestination;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.common.net.HostAndPort;
import com.google.devtools.mobileharness.infra.client.api.mode.remote.LabServerLocator;
import com.google.devtools.mobileharness.infra.client.api.proto.ServerLocatorProto.GrpcServerLocator;
import com.google.devtools.mobileharness.infra.client.api.proto.ServerLocatorProto.ServerLocator;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator;
import com.google.devtools.mobileharness.shared.util.comm.relay.proto.DestinationProto.Destination;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.DirectTarget;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.ServerSpec;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.StubConfiguration;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.Transport;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.Optional;

/** Utility class for ServerLocatorProto. */
public final class ServerLocatorUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String GRPC_PREFIX = "grpc:";
  // Default scheme for grpc name resolver.
  private static final String DNS_PREFIX = "dns:///";

  /**
   * Gets the master server locator from the job info.
   *
   * @throws IllegalStateException if the job info does not contain a valid master server locator.
   */
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
   * Parses a {@link ServerLocator} from a string.
   *
   * <p>If the locator case is specified by the prefix tag, the locator will be parsed as the
   * corresponding locator type. Otherwise, will try to parse the locator as a grpc server locator
   * if possible.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code grpc:127.0.0.1:9876} is a valid grpc server locator.
   * </ul>
   */
  public static ServerLocator parseServerLocator(String serverLocator) {
    ServerLocator.Builder serverLocatorBuilder = ServerLocator.newBuilder();
    if (serverLocator.startsWith(GRPC_PREFIX)) {
      serverLocatorBuilder.setGrpcServerLocator(
          parseGrpcServerLocator(serverLocator.substring(GRPC_PREFIX.length())));
    } else {
      // Try to add the server locator as a grpc server locator if possible.
      try {
        serverLocatorBuilder.setGrpcServerLocator(parseGrpcServerLocator(serverLocator));
      } catch (IllegalStateException e) {
        logger.atWarning().withCause(e).log(
            "Failed to parse %s as grpc server locator.", serverLocator);
      }
    }
    return serverLocatorBuilder.build();
  }

  /**
   * Parses a {@link GrpcServerLocator} from a string.
   *
   * @throws IllegalStateException if the target is not a valid grpc target.
   */
  @VisibleForTesting
  static GrpcServerLocator parseGrpcServerLocator(String grpcServerTarget) {
    HostAndPort hostAndPort = HostAndPort.fromString(grpcServerTarget);
    GrpcServerLocator.Builder builder = GrpcServerLocator.newBuilder();
    if (hostAndPort.hasPort()) {
      builder.setPort(hostAndPort.getPort());
    }
    if (isInetAddress(hostAndPort.getHost())) {
      builder.setIp(toAddrString(forString(hostAndPort.getHost())));
    } else {
      builder.setHostname(hostAndPort.getHost());
    }
    return builder.build();
  }

  /**
   * Converts a {@link GrpcServerLocator} to a grpc target.
   *
   * <p>The grpc target will be resolved by {@link io.grpc.NameResolver}.
   */
  public static String toGrpcTarget(GrpcServerLocator grpcServerLocator) {
    String scheme = !grpcServerLocator.getIp().isEmpty() ? "" : DNS_PREFIX;
    String host =
        !grpcServerLocator.getIp().isEmpty()
            ? grpcServerLocator.getIp()
            : grpcServerLocator.getHostname();
    return grpcServerLocator.getPort() != 0
        ? scheme + fromParts(host, grpcServerLocator.getPort())
        : scheme + fromHost(host);
  }

  /**
   * Creates a {@link StubConfiguration} for a grpc server locator.
   *
   * <p>The grpc target in server spec will be resolved by {@link io.grpc.NameResolver}.
   */
  public static StubConfiguration createGrpcDirectStubConfiguration(
      GrpcServerLocator grpcServerLocator) {
    return StubConfiguration.newBuilder()
        .setTransport(Transport.GRPC)
        .setDirectTarget(
            DirectTarget.newBuilder()
                .setServerSpec(ServerSpec.newBuilder().setTarget(toGrpcTarget(grpcServerLocator))))
        .build();
  }

  /**
   * Creates a {@link StubConfiguration} for a grpc server locator with the relay destination from a
   * lab server locator.
   *
   * <p>The grpc target in server spec will be resolved by {@link io.grpc.NameResolver}.
   */
  public static StubConfiguration createGrpcDirectStubConfiguration(
      GrpcServerLocator grpcServerLocator, LabServerLocator labServerLocator) {
    return createGrpcDirectStubConfiguration(
        grpcServerLocator, createDestination(labServerLocator.labLocatorProto()));
  }

  /**
   * Creates a {@link StubConfiguration} for a grpc server locator with the relay destination from a
   * lab server locator and a test engine locator.
   *
   * <p>The grpc target in server spec will be resolved by {@link io.grpc.NameResolver}.
   */
  public static StubConfiguration createGrpcDirectStubConfiguration(
      GrpcServerLocator grpcServerLocator,
      LabServerLocator labServerLocator,
      TestEngineLocator testEngineLocator) {
    return createGrpcDirectStubConfiguration(
        grpcServerLocator, createDestination(labServerLocator, testEngineLocator));
  }

  private static StubConfiguration createGrpcDirectStubConfiguration(
      GrpcServerLocator grpcServerLocator, Destination destination) {
    return StubConfiguration.newBuilder()
        .setTransport(Transport.GRPC)
        .setDirectTarget(
            DirectTarget.newBuilder()
                .setServerSpec(ServerSpec.newBuilder().setTarget(toGrpcTarget(grpcServerLocator)))
                .setRelayDestination(destination))
        .build();
  }

  private ServerLocatorUtil() {}
}
