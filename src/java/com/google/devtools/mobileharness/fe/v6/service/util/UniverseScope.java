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

/**
 * Represents the universe scope for routing and feature enablement.
 *
 * <p>This sealed interface replaces the {@code Universe} proto for internal service logic,
 * providing compile-time type safety with exactly two states:
 *
 * <ul>
 *   <li>{@link SelfUniverse} — the local (google_1p) universe
 *   <li>{@link RoutedUniverse} — a remote ATS controller universe
 * </ul>
 */
public sealed interface UniverseScope {

  /** A singleton {@link SelfUniverse} instance for convenience. */
  UniverseScope SELF = new SelfUniverse();

  /** Represents the local (google_1p) universe. */
  record SelfUniverse() implements UniverseScope {}

  /** Represents a remote ATS controller universe, identified by its controller ID. */
  record RoutedUniverse(String atsControllerId) implements UniverseScope {}
}
