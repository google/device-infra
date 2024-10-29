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

package com.google.devtools.mobileharness.shared.util.comm.relay.service;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import java.util.function.Function;
import javax.annotation.concurrent.GuardedBy;

/**
 * Switches a pair of server and client calls.
 *
 * <p>In details, {@link RequestProxy} relays the requests from the server call to the client call
 * and {@link ResponseProxy} relays the responses from the client call to the server call.
 *
 * <p>The implementation is based on the following diagram:
 *
 * <p>{@literal <pre> serverCall -----> serverCallListener (RequestProxy) ^ | | | | | | V
 * clientCallListener (ResponseProxy) <------ clientCall </pre> }
 */
final class CallSwitcher<ReqT, RespT, RelayReqT, RelayRespT> {

  final RequestProxy serverCallListener;
  final ResponseProxy clientCallListener;

  CallSwitcher(
      ServerCall<ReqT, RespT> serverCall,
      ClientCall<RelayReqT, RelayRespT> clientCall,
      Function<ReqT, RelayReqT> requestTransform,
      Function<RelayRespT, RespT> responseTransform) {
    serverCallListener = new RequestProxy(clientCall, requestTransform);
    clientCallListener = new ResponseProxy(serverCall, responseTransform);
  }

  class RequestProxy extends ServerCall.Listener<ReqT> {
    private final ClientCall<RelayReqT, ?> clientCall;
    private final Function<ReqT, RelayReqT> requestTransform;
    private final Object lock = new Object();

    @GuardedBy("lock")
    private boolean needToRequest;

    RequestProxy(ClientCall<RelayReqT, ?> clientCall, Function<ReqT, RelayReqT> requestTransform) {
      this.clientCall = clientCall;
      this.requestTransform = requestTransform;
    }

    @Override
    public void onCancel() {
      clientCall.cancel("Server cancelled", null);
    }

    @Override
    public void onHalfClose() {
      clientCall.halfClose();
    }

    @Override
    public void onMessage(ReqT message) {
      clientCall.sendMessage(requestTransform.apply(message));
      synchronized (lock) {
        if (clientCall.isReady()) {
          clientCallListener.serverCall.request(1);
        } else {
          // The outgoing call is not ready for more requests. Stop requesting additional data and
          // wait for it to catch up.
          needToRequest = true;
        }
      }
    }

    @Override
    public void onReady() {
      clientCallListener.onServerReady();
    }

    /**
     * Called from ResponseProxy, which is a different thread than the ServerCall.Listener
     * callbacks.
     */
    void onClientReady() {
      synchronized (lock) {
        if (needToRequest) {
          clientCallListener.serverCall.request(1);
          needToRequest = false;
        }
      }
    }
  }

  class ResponseProxy extends ClientCall.Listener<RelayRespT> {
    private final ServerCall<?, RespT> serverCall;
    private final Function<RelayRespT, RespT> responseTransform;
    private final Object lock = new Object();

    @GuardedBy("lock")
    private boolean needToRequest;

    ResponseProxy(ServerCall<?, RespT> serverCall, Function<RelayRespT, RespT> responseTransform) {
      this.serverCall = serverCall;
      this.responseTransform = responseTransform;
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      serverCall.close(status, trailers);
    }

    @Override
    public void onHeaders(Metadata headers) {
      serverCall.sendHeaders(headers);
    }

    @Override
    public void onMessage(RelayRespT message) {
      serverCall.sendMessage(responseTransform.apply(message));
      synchronized (lock) {
        if (serverCall.isReady()) {
          serverCallListener.clientCall.request(1);
        } else {
          // The incoming call is not ready for more responses. Stop requesting additional data
          // and wait for it to catch up.
          needToRequest = true;
        }
      }
    }

    @Override
    public void onReady() {
      serverCallListener.onClientReady();
    }

    /**
     * Called from RequestProxy, which is a different thread than the ClientCall.Listener callbacks
     */
    void onServerReady() {
      synchronized (lock) {
        if (needToRequest) {
          serverCallListener.clientCall.request(1);
          needToRequest = false;
        }
      }
    }
  }
}
