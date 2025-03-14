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

import static com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionUtil.toStatusRuntimeException;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.comm.testing.proto.DummyServiceGrpc;
import com.google.devtools.mobileharness.shared.util.comm.testing.proto.DummyServiceProto.DummyRequest;
import com.google.devtools.mobileharness.shared.util.comm.testing.proto.DummyServiceProto.DummyResponse;
import io.grpc.stub.StreamObserver;

/** Dummy service implementation for testing. */
public class DummyServiceImpl extends DummyServiceGrpc.DummyServiceImplBase {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String DEFAULT_WELCOME_MESSAGE = "Hello";
  private static final String DELIMITER = " and ";
  static final int DEFAULT_REPEAT = 3;

  private final String welcomeMessage;
  private final int repeat;

  /**
   * Creates a new dummy service implementation.
   *
   * @param welcomeMessage the message to prepend to the response.
   * @param repeat the number of times to repeat the response for server streaming methods.
   */
  public DummyServiceImpl(String welcomeMessage, int repeat) {
    this.welcomeMessage = welcomeMessage;
    this.repeat = repeat;
  }

  public DummyServiceImpl(String welcomeMessage) {
    this(welcomeMessage, DEFAULT_REPEAT);
  }

  public DummyServiceImpl() {
    this(DEFAULT_WELCOME_MESSAGE, DEFAULT_REPEAT);
  }

  @Override
  public void unaryMethod(DummyRequest request, StreamObserver<DummyResponse> responseObserver) {
    DummyResponse response;
    try {
      response = unaryReply(request);
    } catch (Throwable t) {
      responseObserver.onError(toStatusRuntimeException(t));
      return;
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void serverStreamingMethod(
      DummyRequest request, StreamObserver<DummyResponse> responseObserver) {
    for (int i = 0; i < repeat; i++) {
      logger.atInfo().log("ServerStreamingMethod: %s for %d", request.getName(), i);
      DummyResponse response;
      try {
        response = unaryReply(request);
      } catch (Throwable t) {
        responseObserver.onError(toStatusRuntimeException(t));
        return;
      }
      responseObserver.onNext(response);
    }
    responseObserver.onCompleted();
  }

  @Override
  public StreamObserver<DummyRequest> clientStreamingMethod(
      final StreamObserver<DummyResponse> responseObserver) {
    return new StreamObserver<DummyRequest>() {
      private final StringBuilder builder = new StringBuilder();

      @Override
      public void onNext(DummyRequest request) {
        logger.atInfo().log("ClientStreamingMethod get request: %s", request.getName());
        if (builder.length() > 0) {
          builder.append(DELIMITER);
        }
        builder.append(request.getName());
      }

      @Override
      public void onError(Throwable t) {
        logger.atWarning().withCause(t).log("ClientStreamingMethod: %s", t);
        responseObserver.onError(toStatusRuntimeException(t));
      }

      @Override
      public void onCompleted() {
        DummyResponse response;
        try {
          response = unaryReply(builder.toString());
        } catch (Throwable t) {
          responseObserver.onError(toStatusRuntimeException(t));
          return;
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
      }
    };
  }

  @Override
  public StreamObserver<DummyRequest> bidiStreamingMethod(
      final StreamObserver<DummyResponse> responseObserver) {
    return new StreamObserver<DummyRequest>() {
      private final StringBuilder builder = new StringBuilder();

      @Override
      public void onNext(DummyRequest request) {
        logger.atInfo().log("BidiStreamingMethod get request: %s", request.getName());
        if (builder.length() > 0) {
          builder.append(DELIMITER);
        }
        builder.append(request.getName());
        DummyResponse response;
        try {
          response = unaryReply(builder.toString());
        } catch (Throwable t) {
          responseObserver.onError(toStatusRuntimeException(t));
          return;
        }
        responseObserver.onNext(response);
      }

      @Override
      public void onError(Throwable t) {
        logger.atWarning().withCause(t).log("BidiStreamingMethod: %s", t);
        responseObserver.onError(toStatusRuntimeException(t));
      }

      @Override
      public void onCompleted() {
        responseObserver.onCompleted();
      }
    };
  }

  private DummyResponse unaryReply(DummyRequest request) {
    return unaryReply(request.getName());
  }

  private DummyResponse unaryReply(String name) {
    if (name.contains("error")) {
      throw new IllegalArgumentException(
          "This is message.", new IllegalStateException("This is cause"));
    }
    return DummyResponse.newBuilder()
        .setName(String.format("%s, %s.", welcomeMessage, name))
        .build();
  }
}
