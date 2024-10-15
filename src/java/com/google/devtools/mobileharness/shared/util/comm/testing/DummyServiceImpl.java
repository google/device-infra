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
    responseObserver.onNext(unaryReply(request));
    responseObserver.onCompleted();
  }

  @Override
  public void serverStreamingMethod(
      DummyRequest request, StreamObserver<DummyResponse> responseObserver) {
    for (int i = 0; i < repeat; i++) {
      logger.atInfo().log("ServerStreamingMethod: %s for %d", request.getName(), i);
      responseObserver.onNext(unaryReply(request));
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
      }

      @Override
      public void onCompleted() {
        responseObserver.onNext(unaryReply(builder.toString()));
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
        responseObserver.onNext(unaryReply(builder.toString()));
      }

      @Override
      public void onError(Throwable t) {
        logger.atWarning().withCause(t).log("BidiStreamingMethod: %s", t);
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
    return DummyResponse.newBuilder()
        .setName(String.format("%s, %s.", welcomeMessage, name))
        .build();
  }
}
