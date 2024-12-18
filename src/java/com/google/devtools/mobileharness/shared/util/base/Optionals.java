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

package com.google.devtools.mobileharness.shared.util.base;

import com.google.common.flogger.FluentLogger;
import java.util.Optional;

/** Utility methods pertaining to {@link Optional}. */
public final class Optionals {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Returns the value of the first {@link Optional} if both {@link Optional} objects are equal,
   * otherwise returns an empty {@link Optional}.
   *
   * @param a the first {@link Optional} object
   * @param b the second {@link Optional} object
   * @return the value of the first {@link Optional} if both {@link Optional} objects are equal,
   *     otherwise returns an empty {@link Optional}
   */
  public static <T> Optional<T> getIfEqual(Optional<T> a, Optional<T> b) {
    if (a.equals(b)) {
      return a;
    }
    logger.atWarning().log("Two objects are not equal: %s, %s", a.orElse(null), b.orElse(null));
    return Optional.empty();
  }

  private Optionals() {}
}
