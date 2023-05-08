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

/** A utility class for errors. */
public class ErrorUtils {

  /** Recursively checks if any cause has the http status code. */
  public static boolean hasStatusCode(Throwable t, int code) {
    Optional<HttpResponseException> httpExceptionOptional =
        findCause(t, HttpResponseException.class);
    return httpExceptionOptional.isPresent() && httpExceptionOptional.get().getStatusCode() == code;
  }

  /** Finds a possible cause of a particular type {@code clazz} in the exception stack. */
  public static <T extends Throwable> Optional<T> findCause(Throwable throwable, Class<T> clazz) {
    Throwable cause = throwable;
    while (cause != null) {
      if (clazz.isInstance(cause)) {
        return Optional.of(clazz.cast(cause));
      }
      cause = cause.getCause();
    }
    return Optional.empty();
  }

  private ErrorUtils() {}
}
