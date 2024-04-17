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

package com.google.devtools.mobileharness.shared.util.comm.stub;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Executor;

/** Factory for creating {@link ManagedChannel} to the server. */
public class ChannelFactory {

  public static ManagedChannel createLocalChannel(int port, Executor executor) {
    return createChannel(NettyChannelBuilder.forAddress("localhost", port), executor);
  }

  /**
   * See {@link io.grpc.ManagedChannelBuilder#forTarget(String)} about all valid formats of {@code
   * target}.
   */
  public static ManagedChannel createChannel(String target, Executor executor) {
    return createChannel(NettyChannelBuilder.forTarget(target), executor);
  }

  /**
   * Create an interceptor that ensure the virtual host of the request, :authority header in HTTP2,
   * carries the specified name.
   */
  public static ClientInterceptor authorityInterceptor(final String authority) {
    Preconditions.checkNotNull(authority);
    // Override the :authority header in the HTTP2 request.
    return new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
          MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        callOptions = callOptions.withAuthority(authority);
        return next.newCall(method, callOptions);
      }
    };
  }

  /**
   * Create an interceptor that binds access tokens derived from the passed credential to every call
   * made on a channel. Credential file is expected to be of the form produced by clicking the
   * 'Generate new JSON Key' from a service account in the "APIs & Auth > Credentials" section on
   * the cloud developer console.
   *
   * @param credentialFile containing the JSON serialized credential
   * @param optionalScopes collection of scopes to apply to credential, optional.
   */
  public static ClientInterceptor credentialInterceptor(
      File credentialFile, Collection<String> optionalScopes) {
    try {
      GoogleCredentials credential =
          GoogleCredentials.fromStream(new FileInputStream(credentialFile));
      if (!optionalScopes.isEmpty()) {
        credential = credential.createScoped(optionalScopes);
      }
      return credentialInterceptor(credential);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  /**
   * Create an interceptor that binds access tokens derived from the passed credential to every call
   * made on a channel.
   *
   * @param credential to extract access tokens from.
   * @return client interceptor which adds the access token to every request on a channel.
   */
  public static ClientInterceptor credentialInterceptor(Credentials credential) {
    return new SetCallCredentialsInterceptor(MoreCallCredentials.from(credential));
  }

  private static final class SetCallCredentialsInterceptor implements ClientInterceptor {
    private final CallCredentials creds;

    public SetCallCredentialsInterceptor(CallCredentials creds) {
      this.creds = Preconditions.checkNotNull(creds, "creds");
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
      return next.newCall(method, callOptions.withCallCredentials(creds));
    }
  }

  private static ManagedChannel createChannel(
      NettyChannelBuilder channelBuilder, Executor executor) {
    return channelBuilder.negotiationType(NegotiationType.PLAINTEXT).executor(executor).build();
  }

  private ChannelFactory() {}
}
