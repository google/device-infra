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

package com.google.devtools.mobileharness.infra.daemon.health.handler;

/** Handler interface for sub-component to manage in draining mode. */
public interface DrainHandler {

  /** Callbacks to drain sub-component. */
  default void drain() {}

  /** Returns true if the sub-component has drained. */
  default boolean hasDrained() {
    return true;
  }

  /** Returns true if the sub-component has timeout for drain. */
  default boolean hasExpired() {
    return false;
  }

  /** Cleanup for the forcibly drain. Normally it is triggered by the drain operation is expired. */
  default void forceCleanUp() {}
}
