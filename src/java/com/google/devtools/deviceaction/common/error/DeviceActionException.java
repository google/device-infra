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

package com.google.devtools.deviceaction.common.error;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.ErrorIdProvider;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.util.ErrorIdFormatter;
import com.google.devtools.deviceinfra.api.error.DeviceInfraException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import javax.annotation.Nullable;

/** A dedicated exception class for device action. */
public final class DeviceActionException extends Exception
    implements ErrorIdProvider<DeviceActionErrorId> {

  private final DeviceActionErrorId errorId;

  /** See {@link Throwable#Throwable(String)}. */
  public DeviceActionException(DeviceActionErrorId errorId, String message) {
    super(formatMessageWithErrorId(errorId, message));
    this.errorId = checkNotNull(errorId);
  }

  /** See {@link Throwable#Throwable(String, Throwable)}. */
  public DeviceActionException(
      DeviceActionErrorId errorId, String message, @Nullable Throwable cause) {
    super(formatMessageWithErrorId(errorId, message), cause);
    this.errorId = checkNotNull(errorId);
  }

  /** See {@link Throwable#Throwable(String)}. */
  public DeviceActionException(String name, ErrorType type, String message) {
    this(createErrorId(name, type), message);
  }

  /** See {@link Throwable#Throwable(String, Throwable)}. */
  public DeviceActionException(
      String name, ErrorType type, String message, @Nullable Throwable cause) {
    this(createErrorId(name, type), message, cause);
  }

  /** Converts a MobileHarness exception to a {@link DeviceActionException}. */
  public DeviceActionException(MobileHarnessException e, String message) {
    this(createErrorId(e.getErrorId()), message, e);
  }

  /** Converts a MobileHarness exception to a {@link DeviceActionException}. */
  @FormatMethod
  public DeviceActionException(
      MobileHarnessException e, @FormatString String message, Object... args) {
    this(e, String.format(message, args));
  }

  /** Converts a DeviceInfra exception to a {@link DeviceActionException}. */
  public DeviceActionException(DeviceInfraException e, String message) {
    this(createErrorId(e.getErrorId()), message, e);
  }

  /** Converts a DeviceInfra exception to a {@link DeviceActionException}. */
  @FormatMethod
  public DeviceActionException(
      DeviceInfraException e, @FormatString String message, Object... args) {
    this(e, String.format(message, args));
  }

  @Override
  public DeviceActionErrorId getErrorId() {
    return errorId;
  }

  private static String formatMessageWithErrorId(DeviceActionErrorId errorId, String message) {
    return String.format("%s %s", ErrorIdFormatter.formatErrorId(errorId), message);
  }

  private static DeviceActionErrorId createErrorId(String name, ErrorType type) {
    return new DeviceActionErrorId(name.hashCode(), name, type);
  }

  private static DeviceActionErrorId createErrorId(ErrorId errorId) {
    return new DeviceActionErrorId(errorId.code(), errorId.name(), errorId.type());
  }
}
