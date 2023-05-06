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

import com.google.api.client.http.HttpResponseException;
import java.util.Optional;

/** A utility class to check errors. */
public class ErrorChecker {

  /** Recursively checks if any cause has the status code. */
  public static boolean checkStatusCodeRecursively(Throwable t, int code) {
    Optional<HttpResponseException> exceptionOptional = getHttpResponseException(t);
    return exceptionOptional.isPresent() && exceptionOptional.get().getStatusCode() == code;
  }

  /** Gets the possible {@code HttpResponseException} in the cause. */
  public static Optional<HttpResponseException> getHttpResponseException(Throwable throwable) {
    Throwable cause = throwable;
    while (cause != null) {
      if (cause instanceof HttpResponseException) {
        return Optional.of((HttpResponseException) cause);
      }
      cause = cause.getCause();
    }
    return Optional.empty();
  }

  private ErrorChecker() {}
}
