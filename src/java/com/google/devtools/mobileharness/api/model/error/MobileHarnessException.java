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

package com.google.devtools.mobileharness.api.model.error;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.common.metrics.stability.model.ErrorIdProvider;
import com.google.devtools.common.metrics.stability.model.MetadataProvider;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

/** Base class of all Mobile Harness exceptions. */
@SuppressWarnings("OverrideThrowableToString")
public class MobileHarnessException extends Exception
    implements ErrorIdProvider<ErrorId>, MetadataProvider {

  private static final StackTraceElement[] EMPTY_STACK_TRACE = new StackTraceElement[0];

  private final ErrorId errorId;
  private final ConcurrentMap<String, String> metadata = new ConcurrentHashMap<>();

  public MobileHarnessException(ErrorId errorId, String message) {
    this(errorId, message, /* cause= */ null);
  }

  public MobileHarnessException(ErrorId errorId, String message, @Nullable Throwable cause) {
    this(
        errorId,
        message,
        cause,
        !message.endsWith(getMessageSuffix(errorId)),
        /* clearStackTrace= */ false);
  }

  /** Do NOT make it public. */
  MobileHarnessException(
      ErrorId errorId,
      String message,
      @Nullable Throwable cause,
      boolean addErrorIdToMessage,
      boolean clearStackTrace) {
    super(addErrorIdToMessage ? message + getMessageSuffix(errorId) : message, cause);
    this.errorId = errorId;
    if (clearStackTrace) {
      setStackTrace(EMPTY_STACK_TRACE);
    }
  }

  @Override
  public ErrorId getErrorId() {
    return errorId;
  }

  @Override
  public void addMetadata(String key, String value) {
    checkNotNull(key, "key");
    checkNotNull(value, "value");
    metadata.put(key, value);
  }

  @Override
  public void addMetadata(Map<String, String> metadata) {
    this.metadata.putAll(metadata);
  }

  @Override
  public ImmutableMap<String, String> getMetadata() {
    return ImmutableMap.copyOf(metadata);
  }

  @Override
  public Optional<String> getMetadata(String key) {
    return Optional.ofNullable(metadata.get(key));
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder(getClass().getSimpleName());
    String message = getLocalizedMessage();
    if (message != null) {
      result.append(": ").append(message);
    }
    if (metadata != null && !metadata.isEmpty()) {
      result.append(", metadata=").append(getMetadata());
    }
    return result.toString();
  }

  private static String getMessageSuffix(ErrorId errorId) {
    return " " + errorId;
  }
}
