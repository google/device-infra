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

package com.google.devtools.deviceaction.common.utils;

import com.google.common.base.Preconditions;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;

/** A {@code Preconditions}-like class that throws Device Action exceptions. */
public final class Conditions {

  /** See {@link Preconditions#checkState(boolean)}. */
  public static void checkState(boolean expression, ErrorType errorType, String message)
      throws DeviceActionException {
    try {
      Preconditions.checkState(expression);
    } catch (IllegalStateException e) {
      throw new DeviceActionException("ILLEGAL_STATE", errorType, message, e);
    }
  }

  /** See {@link Preconditions#checkArgument(boolean)}. */
  public static void checkArgument(boolean expression, ErrorType errorType, String message)
      throws DeviceActionException {
    try {
      Preconditions.checkArgument(expression);
    } catch (IllegalArgumentException e) {
      throw new DeviceActionException("ILLEGAL_ARGUMENT", errorType, message, e);
    }
  }

  private Conditions() {}
}
