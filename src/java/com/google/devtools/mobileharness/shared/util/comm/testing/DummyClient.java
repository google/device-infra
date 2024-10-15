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

package com.google.devtools.mobileharness.shared.util.comm.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.comm.testing.proto.DummyServiceGrpc;
import com.google.devtools.mobileharness.shared.util.comm.testing.proto.DummyServiceProto.DummyRequest;
import com.google.devtools.mobileharness.shared.util.comm.testing.proto.DummyServiceProto.DummyResponse;
import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;

/** Testing client for {@link DummyService}. */
public final class DummyClient {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DummyServiceGrpc.DummyServiceBlockingStub blockingStub;
  private final DummyServiceGrpc.DummyServiceStub asyncStub;
  private final ExecutorService asyncRequestExecutor;
  private final int repeat;

  /**
   * Creates a new client for {@link DummyService}.
   *
   * @param channel the channel to use for sending grpc requests.
   * @param executor the executor to use for sending async requests.
   * @param repeat the number of times to repeat the response for server streaming methods.
   */
  public DummyClient(Channel channel, ExecutorService executor, int repeat) {
    blockingStub = DummyServiceGrpc.newBlockingStub(channel);
    asyncStub = DummyServiceGrpc.newStub(channel);
    this.asyncRequestExecutor = executor;
    this.repeat = repeat;
  }

  /**
   * Creates a new client for {@link DummyService} with default repeat for server streaming methods.
   */
  public DummyClient(Channel channel, ExecutorService executor) {
    this(channel, executor, DummyServiceImpl.DEFAULT_REPEAT);
  }

  /** Sends a blocking unary RPC. */
  public DummyResponse blockingUnaryMethod(DummyRequest request) {
    return blockingStub.unaryMethod(request);
  }

  /** Sends a blocking server streaming RPC. */
  public ImmutableList<DummyResponse> blockingServerStreamingMethod(DummyRequest request) {
    ImmutableList.Builder<DummyResponse> responses = ImmutableList.builder();
    blockingStub.serverStreamingMethod(request).forEachRemaining(responses::add);
    return responses.build();
  }

  /** Sends an async unary RPC. */
  public ResponseObserver asyncUnaryMethod(DummyRequest request) {
    ResponseObserver responseObserver = new ResponseObserver();
    asyncStub.unaryMethod(request, responseObserver);
    return responseObserver;
  }

  /** Sends an async server streaming RPC. */
  public ResponseObserver asyncServerStreamingMethod(DummyRequest request) {
    ResponseObserver responseObserver = new ResponseObserver(repeat);
    asyncStub.serverStreamingMethod(request, responseObserver);
    return responseObserver;
  }

  /** Sends an async client streaming RPC. */
  public ResponseObserver clientStreamingMethod(Collection<DummyRequest> requests) {
    ResponseObserver responseObserver = new ResponseObserver();
    StreamObserver<DummyRequest> requestObserver =
        asyncStub.clientStreamingMethod(responseObserver);
    sendAsyncRequest(requestObserver, requests, null);
    return responseObserver;
  }

  /** Sends an async bidi streaming RPC. */
  public ResponseObserver bidiStreamingMethod(Collection<DummyRequest> requests) {
    ResponseObserver responseObserver = new ResponseObserver(requests.size());
    StreamObserver<DummyRequest> requestObserver = asyncStub.bidiStreamingMethod(responseObserver);
    sendAsyncRequest(requestObserver, requests, responseObserver);
    return responseObserver;
  }

  /** Extracts the names from a collection of {@link DummyResponse}s. */
  public static ImmutableList<String> toNames(Collection<DummyResponse> responses) {
    return responses.stream().map(DummyResponse::getName).collect(toImmutableList());
  }

  /** Converts a collection of names to a list of {@link DummyRequest}s. */
  public static ImmutableList<DummyRequest> toRequests(Collection<String> names) {
    return names.stream()
        .map(name -> DummyRequest.newBuilder().setName(name).build())
        .collect(toImmutableList());
  }

  private void sendAsyncRequest(
      StreamObserver<DummyRequest> requestObserver,
      Collection<DummyRequest> requests,
      @Nullable ResponseObserver responseObserver) {
    asyncRequestExecutor.execute(
        () -> {
          try {
            int toSend = requests.size();
            for (DummyRequest request : requests) {
              requestObserver.onNext(request);
              if (responseObserver == null) {
                logger.atInfo().log("Waiting for some seconds beforing sending next request.");
                Thread.sleep(500);
              } else {
                toSend--;
                while (responseObserver.countResponseToWait() > toSend) {
                  logger.atInfo().log("Waiting for response to be received.");
                  Thread.sleep(100);
                }
                if (responseObserver.countResponseToWait() == 0) {
                  break;
                }
              }
            }
            requestObserver.onCompleted();
          } catch (RuntimeException e) {
            logger.atWarning().withCause(e).log("Failed to send async request.");
            requestObserver.onError(e);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.atWarning().withCause(e).log("Interrupted while sending async request!");
            requestObserver.onError(e);
          }
        });
  }
}
