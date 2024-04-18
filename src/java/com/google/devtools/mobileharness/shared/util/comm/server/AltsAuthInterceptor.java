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

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.lang.reflect.Method;
import java.util.Collection;

/** Intercepts the gRPC requests from client and validates the user identity. */
class AltsAuthInterceptor implements ServerInterceptor {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final ImmutableSet<String> agentServiceAccounts;

  /**
   * Static method io.grpc.alts.AuthorizationUtil.clientAuthorizationCheck.
   *
   * <p>Requires a runtime or an indirect dependency on io.grpc.alts.
   */
  private final Method checkMethod;

  /** */
  public AltsAuthInterceptor(Collection<String> agentServiceAccounts) {
    this.agentServiceAccounts = ImmutableSet.copyOf(agentServiceAccounts);
    try {
      Class<?> authorizationUtilClass = Class.forName("io.grpc.alts.AuthorizationUtil");
      checkMethod =
          authorizationUtilClass.getMethod(
              "clientAuthorizationCheck", ServerCall.class, Collection.class);
    } catch (ReflectiveOperationException e) {
      throw ReflectionOperationExceptionWrapper.rethrowAsIllegalStateException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Only permit calls from the list of agent service accounts.
   */
  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    Status status = checkAuth(call);
    if (status.isOk()) {
      logger.atInfo().log("Agent RPC authorization check succeeded for %s", agentServiceAccounts);
      return next.startCall(call, headers);
    }
    logger.atSevere().log(
        "Agent RPC failed authorization check for %s: %s, %s.",
        agentServiceAccounts, headers, status);
    call.close(status, headers);
    return new Listener<ReqT>() {};
  }

  private <ReqT, RespT> Status checkAuth(ServerCall<ReqT, RespT> call) {
    try {
      return (Status) checkMethod.invoke(null, call, agentServiceAccounts);
    } catch (ReflectiveOperationException e) {
      throw ReflectionOperationExceptionWrapper.rethrowAsIllegalStateException(e);
    }
  }
}
