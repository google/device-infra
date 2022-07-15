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

package com.google.devtools.common.metrics.stability.converter;

import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.ErrorIdProvider;
import javax.annotation.Nullable;

/**
 * Exception deserialized from {@linkplain
 * com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail
 * ExceptionDetail} (usually from another process).
 *
 * <p>Its {@link #getCause()} has the same type if any.
 *
 * <p>Its message has the format "{@code $original_message
 * [$original_exception_class_simple_name]}".
 *
 * <p>Its stack trace is equal to the stack trace of the serialized original exception.
 */
public class DeserializedException extends Exception implements ErrorIdProvider<ErrorId> {

  private final DeserializedErrorId errorId;
  private final String originalExceptionClassName;

  /** Do not make it public. */
  DeserializedException(
      DeserializedErrorId errorId, String originalMessage, String originalExceptionClassName) {
    super(formatMessage(originalMessage, originalExceptionClassName));
    this.errorId = errorId;
    this.originalExceptionClassName = originalExceptionClassName;
  }

  /** Returns the class name of the serialized original exception. */
  public String getOriginalExceptionClassName() {
    return originalExceptionClassName;
  }

  /** {@inheritDoc} */
  @Override
  public ErrorId getErrorId() {
    return errorId;
  }

  /**
   * The message has the format "{@code $original_message [$original_exception_class_simple_name]}".
   *
   * <p>{@inheritDoc}
   */
  @Override
  public String getMessage() {
    return super.getMessage();
  }

  /** {@inheritDoc} */
  @Nullable
  @Override
  public DeserializedException getCause() {
    return (DeserializedException) super.getCause();
  }

  /**
   * The stack trace is equal to the stack trace of the serialized original exception.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public StackTraceElement[] getStackTrace() {
    return super.getStackTrace();
  }

  private static String formatMessage(String originalMessage, String originalExceptionClassName) {
    return String.format(
        "%s [%s]",
        originalMessage,
        originalExceptionClassName.substring(originalExceptionClassName.lastIndexOf('.') + 1));
  }
}
