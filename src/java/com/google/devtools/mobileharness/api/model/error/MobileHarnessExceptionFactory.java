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

import javax.annotation.Nullable;

/** Factory for creating {@link MobileHarnessException} with advanced settings. */
public class MobileHarnessExceptionFactory {

  public static MobileHarnessException create(
      ErrorId errorId,
      String message,
      @Nullable Throwable cause,
      boolean addErrorIdToMessage,
      boolean clearStackTrace) {
    return new MobileHarnessException(
        errorId, message, cause, addErrorIdToMessage, clearStackTrace);
  }

  /**
   * Creates a user facing {@link MobileHarnessException} without stack trace and error id in
   * message.
   *
   * <p>WARNING: This method is exclusively for generating exceptions that display messages to
   * users. To create exceptions for other purposes, utilize the {@link #create(ErrorId, String,
   * Throwable, boolean, boolean)} method instead. This ensures that crucial debugging information,
   * such as the error ID and stack trace, is retained.
   */
  public static MobileHarnessException createUserFacingException(
      ErrorId errorId, String message, @Nullable Throwable cause) {
    return new MobileHarnessException(
        errorId, message, cause, /* addErrorIdToMessage= */ false, /* clearStackTrace= */ true);
  }

  private MobileHarnessExceptionFactory() {}
}
