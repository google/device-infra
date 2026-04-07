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

package com.google.devtools.mobileharness.fe.v6.service.util;

import com.google.common.base.Strings;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Factory for creating {@link UniverseScope} instances based on environment and request parameters.
 */
@Singleton
public class UniverseFactory {

  private final Environment environment;

  @Inject
  UniverseFactory(Environment environment) {
    this.environment = environment;
  }

  /**
   * Creates a {@link UniverseScope} instance from a universe string.
   *
   * <p>A null or empty string is treated as the default (self/google_1p) universe.
   *
   * @param universeStr the universe string from the request (may be null)
   * @return the {@link UniverseScope} instance
   * @throws IllegalArgumentException if the universe string is invalid for the current environment
   */
  public UniverseScope create(String universeStr) {
    String universe = Strings.nullToEmpty(universeStr);
    if (environment.isGoogleInternal()) {
      if (universe.isEmpty() || universe.equals("google_1p")) {
        return UniverseScope.SELF;
      } else {
        return new UniverseScope.RoutedUniverse(universe);
      }
    } else {
      if (universe.isEmpty()) {
        return UniverseScope.SELF;
      } else {
        throw new IllegalArgumentException(
            "Non-empty universe is not supported in OSS/ATS environment.");
      }
    }
  }
}
