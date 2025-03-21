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

package com.google.devtools.common.metrics.stability.rpc.grpc;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.converter.DeserializedException;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.rpc.RpcErrorUtil;
import com.google.devtools.common.metrics.stability.rpc.proto.RpcErrorPayloadProto.RpcErrorPayload;
import com.google.devtools.common.metrics.stability.rpc.proto.RpcErrorProto.RpcError;
import com.google.devtools.mobileharness.shared.util.base.ProtoExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Utility for converting between {@link StatusRuntimeException} and {@linkplain
 * com.google.devtools.common.metrics.stability.model.ErrorIdProvider} exceptions.
 */
public class GrpcExceptionUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** GFE has HTTP header size limit, so we need this limit here. */
  @VisibleForTesting static final int EXCEPTION_DETAIL_SIZE_LIMIT_BYTES = 3_072;

  /**
   * Converts a {@link Throwable} (usually an exception implementing {@linkplain
   * com.google.devtools.common.metrics.stability.model.ErrorIdProvider}) to a {@link
   * StatusRuntimeException}, by serializing the throwable to an {@linkplain
   * com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail
   * ExceptionDetail} proto, compressing it, truncating fields if necessary and adding the proto to
   * the payload of the {@link StatusRuntimeException}.
   *
   * <p>The size of the compressed {@linkplain
   * com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail
   * ExceptionDetail} will not exceed 3KB. To reach the goal, stack traces, messages of the original
   * exception or even the whole original exception may be truncated.
   *
   * @see com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail
   */
  public static StatusRuntimeException toStatusRuntimeException(Throwable throwable) {
    Metadata trailers = new Metadata();
    addMessageToMetadata(
        trailers,
        RpcErrorPayload.newBuilder()
            .setRpcError(
                RpcErrorUtil.toRpcError(
                    ErrorModelConverter.toExceptionDetail(throwable),
                    EXCEPTION_DETAIL_SIZE_LIMIT_BYTES))
            .build());
    return Status.INTERNAL.withDescription(throwable.getMessage()).asRuntimeException(trailers);
  }

  /**
   * Converts a {@link StatusRuntimeException} to a {@link GrpcExceptionWithErrorId}, by
   * uncompressing the {@linkplain
   * com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail
   * ExceptionDetail} proto which represents the server side application error in the payload if
   * any, deserializing it to a {@link DeserializedException} and adding it to {@link
   * GrpcExceptionWithErrorId#getApplicationError()} of the result.
   *
   * <p>Note that you need to provide an additional client side {@link ErrorId} and a message which
   * describe the context of the rpc failure.
   *
   * @see GrpcExceptionWithErrorId
   */
  public static GrpcExceptionWithErrorId toGrpcExceptionWithErrorId(
      ErrorId errorId, String message, StatusRuntimeException grpcException) {
    DeserializedException applicationError = null;
    Throwable deserializingError = null;
    try {
      applicationError = getApplicationError(grpcException).orElse(null);
    } catch (IOException e) {
      deserializingError = e;
    }
    GrpcExceptionWithErrorId result =
        new GrpcExceptionWithErrorId(
            errorId,
            message,
            grpcException.getStatus().getCode().value(),
            applicationError,
            grpcException);
    if (deserializingError != null) {
      result.addSuppressed(deserializingError);
    }
    return result;
  }

  private static Optional<DeserializedException> getApplicationError(Throwable throwable)
      throws IOException {
    Optional<RpcError> error =
        getPayload(throwable)
            .filter(RpcErrorPayload::hasRpcError)
            .map(RpcErrorPayload::getRpcError);
    return error.isPresent()
        ? RpcErrorUtil.toExceptionDetail(error.get())
            .map(ErrorModelConverter::toDeserializedException)
        : Optional.empty();
  }

  public static Optional<RpcErrorPayload> getPayload(Throwable throwable) {
    Throwable cause = checkNotNull(throwable, "throwable cannot be null");
    while (cause != null) {
      if (cause instanceof StatusRuntimeException) {
        StatusRuntimeException e = (StatusRuntimeException) cause;
        return getPayload(e.getStatus(), e.getTrailers(), e);
      } else if (cause instanceof StatusException) {
        StatusException e = (StatusException) cause;
        return getPayload(e.getStatus(), e.getTrailers(), e);
      }
      cause = cause.getCause();
    }
    return Optional.empty();
  }

  private static Optional<RpcErrorPayload> getPayload(
      Status status, Metadata trailers, Throwable cause) {
    return Stream.of(getPayloadFromProto(trailers), getPayloadFromBinary(trailers, cause))
        .filter(Optional::isPresent)
        .flatMap(Optional::stream)
        .findFirst();
  }

  private static Optional<RpcErrorPayload> getPayloadFromProto(Metadata trailers) {
    return Optional.ofNullable(
        trailers.get(ProtoUtils.keyForProto(RpcErrorPayload.getDefaultInstance())));
  }

  private static Optional<RpcErrorPayload> getPayloadFromBinary(
      Metadata trailers, Throwable cause) {
    // For local generated exceptions.
    byte[] bytes =
        trailers.get(
            Metadata.Key.of(
                String.format(
                    "__crpc_mse_%d%s", // See com.google.cloud.rpc2.prodx.MessageSetErrors.
                    300713958, // RpcErrorPayload.MESSAGE_SET_EXTENSION_FIELD_NUMBER
                    Metadata.BINARY_HEADER_SUFFIX),
                Metadata.BINARY_BYTE_MARSHALLER));
    if (bytes == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          RpcErrorPayload.parseFrom(bytes, ProtoExtensionRegistry.getGeneratedRegistry()));

    } catch (InvalidProtocolBufferException e) {
      logger.atWarning().withCause(cause).log(
          "Failed to parse payload from %s trailers: %s",
          cause.getClass().getSimpleName(), trailers);
      return Optional.empty();
    }
  }

  /** Branched from com.google.cloud.rpc2.prodx.MessageSetErrors.addToMetadata(). */
  private static void addMessageToMetadata(Metadata metadata, Message message) {
    Metadata.Key<byte[]> key =
        Metadata.Key.of(
            String.format(
                "%s%d%s",
                "__crpc_mse_",
                300713958, // RpcErrorPayload.MESSAGE_SET_EXTENSION_FIELD_NUMBER
                Metadata.BINARY_HEADER_SUFFIX),
            Metadata.BINARY_BYTE_MARSHALLER);
    metadata.put(key, message.toByteArray());
  }

  private GrpcExceptionUtil() {}
}
