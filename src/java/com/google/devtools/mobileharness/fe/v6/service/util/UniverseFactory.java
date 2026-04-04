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

import com.google.devtools.mobileharness.fe.v6.service.proto.common.RoutedUniverse;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.SelfUniverse;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.Universe;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Factory for creating {@link Universe} instances based on environment and request parameters. */
@Singleton
public class UniverseFactory {

  private final Environment environment;

  @Inject
  UniverseFactory(Environment environment) {
    this.environment = environment;
  }

  /**
   * Creates a {@link Universe} instance from a universe string.
   *
   * @param universeStr the universe string from the request
   * @return the {@link Universe} instance
   * @throws IllegalArgumentException if the universe string is invalid for the current environment
   */
  public Universe create(String universeStr) {
    if (environment.isGoogleInternal()) {
      if (universeStr.isEmpty() || universeStr.equals("google_1p")) {
        return Universe.newBuilder().setSelfUniverse(SelfUniverse.getDefaultInstance()).build();
      } else {
        return Universe.newBuilder()
            .setRoutedUniverse(RoutedUniverse.newBuilder().setAtsControllerId(universeStr))
            .build();
      }
    } else {
      if (universeStr.isEmpty()) {
        return Universe.newBuilder().setSelfUniverse(SelfUniverse.getDefaultInstance()).build();
      } else {
        throw new IllegalArgumentException(
            "Non-empty universe is not supported in OSS/ATS environment.");
      }
    }
  }
}
