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

package com.google.devtools.common.metrics.stability.rpc;

import static java.util.zip.Deflater.BEST_COMPRESSION;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.common.metrics.stability.rpc.proto.RpcErrorProto.CompressedExceptionDetail;
import com.google.devtools.common.metrics.stability.rpc.proto.RpcErrorProto.CompressedExceptionDetail.CompressionFormat;
import com.google.devtools.common.metrics.stability.rpc.proto.RpcErrorProto.RpcError;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import java.io.IOException;
import java.util.Optional;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nullable;

/** Utility for converting between {@link RpcError} and {@link ExceptionDetail}. */
public class RpcErrorUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final CompressionFormat COMPRESSION_FORMAT = CompressionFormat.DEFLATE;

  /**
   * Converts an {@link RpcError} to an {@link ExceptionDetail} if any, by uncompressing data in it.
   */
  public static Optional<ExceptionDetail> toExceptionDetail(RpcError rpcError) throws IOException {
    if (rpcError.hasCompressedExceptionDetail()) {
      CompressedExceptionDetail compressedExceptionDetail = rpcError.getCompressedExceptionDetail();
      if (compressedExceptionDetail.getCompressionFormat() == COMPRESSION_FORMAT) {
        ByteString compressedData = compressedExceptionDetail.getCompressedData();
        try (InflaterInputStream inputStream = new InflaterInputStream(compressedData.newInput())) {
          return Optional.of(
              ExceptionDetail.parseFrom(inputStream, ExtensionRegistry.newInstance()));
        }
      } else {
        throw new IOException(
            String.format(
                "Failed to uncompress CompressedExceptionDetail in RpcError: format [%s] is"
                    + " expected but format [%s] is found",
                COMPRESSION_FORMAT, compressedExceptionDetail.getCompressionFormat()));
      }
    } else {
      return Optional.empty();
    }
  }

  /**
   * Converts {@link ExceptionDetail} to {@link RpcError}.
   *
   * @param compressSizeLimitBytes byte size limit of the output {@link ExceptionDetail}
   */
  public static RpcError toRpcError(
      ExceptionDetail exceptionDetail, @Nullable Integer compressSizeLimitBytes) {
    RpcError.Builder result =
        RpcError.newBuilder()
            .setCompressedExceptionDetail(
                CompressedExceptionDetail.newBuilder().setCompressionFormat(COMPRESSION_FORMAT));

    // Tries to compress ExceptionDetail.
    Optional<ByteString.Output> compressedExceptionDetail =
        compressExceptionDetail(exceptionDetail);
    if (compressedExceptionDetail.isPresent()
        && (compressSizeLimitBytes == null
            || compressedExceptionDetail.get().size() <= compressSizeLimitBytes)) {
      result
          .getCompressedExceptionDetailBuilder()
          .setCompressedData(compressedExceptionDetail.get().toByteString());
      return result.build();
    }

    // Tries to compress ExceptionDetail without stack trace.
    ExceptionDetail exceptionDetailWithoutStackTrace =
        clearStackTrace(exceptionDetail.toBuilder()).build();
    compressedExceptionDetail = compressExceptionDetail(exceptionDetailWithoutStackTrace);
    if (compressedExceptionDetail.isPresent()
        && (compressSizeLimitBytes == null
            || compressedExceptionDetail.get().size() <= compressSizeLimitBytes)) {
      logger.atInfo().log(
          "Stack trace of an ExceptionDetail is omitted because of RpcError size limit,"
              + " exception_detail=[%s]",
          exceptionDetail);
      result
          .getCompressedExceptionDetailBuilder()
          .setCompressedData(compressedExceptionDetail.get().toByteString());
      return result.build();
    }

    // TODO: Truncates error messages here.
    logger.atWarning().log(
        "An ExceptionDetail is omitted because of RpcError size limit," + " exception_detail=[%s]",
        exceptionDetail);
    return RpcError.getDefaultInstance();
  }

  private static Optional<ByteString.Output> compressExceptionDetail(
      ExceptionDetail exceptionDetail) {
    ByteString.Output result = ByteString.newOutput();
    try (DeflaterOutputStream deflaterOutputStream =
        new DeflaterOutputStream(result, new Deflater(BEST_COMPRESSION))) {
      exceptionDetail.writeTo(deflaterOutputStream);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to compress ExceptionDetail: %s", exceptionDetail);
      return Optional.empty();
    }
    return Optional.of(result);
  }

  @CanIgnoreReturnValue
  private static ExceptionDetail.Builder clearStackTrace(ExceptionDetail.Builder exceptionDetail) {
    exceptionDetail.getSummaryBuilder().clearStackTrace();
    if (exceptionDetail.hasCause()) {
      clearStackTrace(exceptionDetail.getCauseBuilder());
    }
    for (ExceptionDetail.Builder suppressed : exceptionDetail.getSuppressedBuilderList()) {
      clearStackTrace(suppressed);
    }
    return exceptionDetail;
  }

  private RpcErrorUtil() {}
}
