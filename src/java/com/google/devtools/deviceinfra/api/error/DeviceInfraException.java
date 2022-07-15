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

package com.google.devtools.deviceinfra.api.error;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.devtools.common.metrics.stability.model.ErrorIdProvider;
import com.google.devtools.common.metrics.stability.util.ErrorIdFormatter;
import com.google.devtools.deviceinfra.api.error.id.DeviceInfraErrorId;
import javax.annotation.Nullable;

/** Exception for Device Infra. */
public class DeviceInfraException extends Exception implements ErrorIdProvider<DeviceInfraErrorId> {

  private final DeviceInfraErrorId errorId;

  /**
   * See {@link Throwable#Throwable(String)}.
   *
   * <p>The {@code errorId} will be formatted and added to the end of the {@code message}.
   */
  public DeviceInfraException(DeviceInfraErrorId errorId, String message) {
    this(errorId, message, /* addErrorIdToMessage= */ true);
  }

  /** See {@link Throwable#Throwable(String, Throwable)}. */
  public DeviceInfraException(
      DeviceInfraErrorId errorId, String message, @Nullable Throwable cause) {
    super(formatMessageWithErrorId(errorId, message), cause);
    this.errorId = checkNotNull(errorId);
  }

  /**
   * See {@link Throwable#Throwable(String)}.
   *
   * @param addErrorIdToMessage if {@code true}, the {@code errorId} will be formatted and added to
   *     the end of the {@code message}
   */
  public DeviceInfraException(
      DeviceInfraErrorId errorId, String message, boolean addErrorIdToMessage) {
    super(addErrorIdToMessage ? formatMessageWithErrorId(errorId, message) : message);
    this.errorId = checkNotNull(errorId);
  }

  /** The {@link DeviceInfraErrorId} of the exception. */
  @Override
  public DeviceInfraErrorId getErrorId() {
    return errorId;
  }

  private static String formatMessageWithErrorId(DeviceInfraErrorId errorId, String message) {
    return String.format("%s %s", message, ErrorIdFormatter.formatErrorId(errorId));
  }
}
