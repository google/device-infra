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

package com.google.devtools.mobileharness.shared.util.comm.server;

import com.google.common.flogger.FluentLogger;
import io.grpc.ServerBuilder;
import io.grpc.netty.NettyServerBuilder;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Set;

/** Creates gRPC server builders. */
public class ServerBuilderFactory {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Creates a gRPC AltsServerBuilder.
   *
   * <p>This method requires a runtime or an indirect dependency on io.grpc.alts package.
   *
   * @param port of the server.
   * @param restrictToAuthUsers a set of authorized users. If the list is not empty, will restrict
   *     the Alts server to the authorized users in the set.
   */
  public static ServerBuilder<?> createAltsServerBuilder(
      int port, Set<String> restrictToAuthUsers) {
    logger.atInfo().log("Build an ALTS server.");
    ServerBuilder<?> serverBuilder;
    try {
      Class<?> altsServerBuilderClass = Class.forName("io.grpc.alts.AltsServerBuilder");
      Method forPortMethod = altsServerBuilderClass.getMethod("forPort", int.class);
      serverBuilder = (ServerBuilder<?>) forPortMethod.invoke(null, port);
    } catch (ReflectiveOperationException e) {
      throw ReflectionOperationExceptionWrapper.rethrowAsIllegalStateException(e);
    }
    return restrictToAuthUsers.isEmpty()
        ? serverBuilder
        : serverBuilder.intercept(new AltsAuthInterceptor(restrictToAuthUsers));
  }

  /**
   * Creates a {@link NettyServerBuilder}.
   *
   * @param port of the server.
   * @param localhost if the address is localhost.
   */
  public static NettyServerBuilder createNettyServerBuilder(int port, boolean localhost) {
    logger.atInfo().log("Build a Netty server.");
    return localhost
        ? NettyServerBuilder.forAddress(new InetSocketAddress("localhost", port))
        : NettyServerBuilder.forPort(port);
  }

  private ServerBuilderFactory() {}
}
