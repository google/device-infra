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

package com.google.devtools.mobileharness.fe.v6.service.errors;

import static com.google.common.base.Preconditions.checkNotNull;

import io.grpc.Status;

/**
 * Transport-neutral exception thrown by FE v6 service logic to signal a specific, user-facing error
 * with a canonical error code.
 *
 * <p>FE v6 runs on two servers with different RPC stacks that share the same business logic
 * (located in {@code third_party}): the OSS server speaks gRPC, while the internal (1P) server
 * speaks Stubby via Apps Framework. The shared logic must not depend on either stack's error types,
 * so it throws this neutral exception. Each server boundary translates it into its native error
 * representation:
 *
 * <ul>
 *   <li>OSS: {@code FeGrpcInvoker} converts it to an {@link io.grpc.StatusRuntimeException}.
 *   <li>Internal: an Apps Framework {@code SafeExceptionHandler} converts it to a {@code
 *       CanonicalCodeException}.
 * </ul>
 *
 * <p>The code is expressed as an {@link io.grpc.Status.Code}, whose numeric value ({@link
 * io.grpc.Status.Code#value()}) is the canonical error code shared across gRPC, Stubby, and {@code
 * google.rpc.Code}. This lets both boundaries map the error to the same HTTP status through their
 * respective transcoders (Envoy for OSS, ESF for internal).
 *
 * <p>Do not reuse generic JVM exceptions (e.g. {@link IllegalArgumentException}) to signal
 * caller-facing errors: those may be thrown by unrelated code and should surface as {@code
 * INTERNAL}. Throw {@code FeServiceException} to explicitly declare the intended canonical code.
 */
public final class FeServiceException extends RuntimeException {

  private final Status.Code code;

  public FeServiceException(Status.Code code, String message) {
    super(message);
    this.code = checkNotNull(code);
  }

  public FeServiceException(Status.Code code, String message, Throwable cause) {
    super(message, cause);
    this.code = checkNotNull(code);
  }

  /** Returns the canonical error code carried by this exception. */
  public Status.Code getCode() {
    return code;
  }

  /** Creates an exception with the {@code INVALID_ARGUMENT} code (invalid caller input). */
  public static FeServiceException invalidArgument(String message) {
    return new FeServiceException(Status.Code.INVALID_ARGUMENT, message);
  }

  /** Creates an exception with the {@code NOT_FOUND} code (requested entity does not exist). */
  public static FeServiceException notFound(String message) {
    return new FeServiceException(Status.Code.NOT_FOUND, message);
  }

  /** Creates an exception with the {@code PERMISSION_DENIED} code. */
  public static FeServiceException permissionDenied(String message) {
    return new FeServiceException(Status.Code.PERMISSION_DENIED, message);
  }

  /** Creates an exception with the {@code FAILED_PRECONDITION} code (wrong system state). */
  public static FeServiceException failedPrecondition(String message) {
    return new FeServiceException(Status.Code.FAILED_PRECONDITION, message);
  }

  /** Creates an exception with the {@code UNIMPLEMENTED} code (feature not supported here). */
  public static FeServiceException unimplemented(String message) {
    return new FeServiceException(Status.Code.UNIMPLEMENTED, message);
  }

  /** Creates an exception with the {@code INTERNAL} code (unexpected server error). */
  public static FeServiceException internal(String message) {
    return new FeServiceException(Status.Code.INTERNAL, message);
  }
}
