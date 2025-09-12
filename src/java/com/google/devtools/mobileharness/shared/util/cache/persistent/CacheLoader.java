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

package com.google.devtools.mobileharness.shared.util.cache.persistent;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.nio.file.Path;
import java.util.Optional;

/** File loader for persistent cache. */
@FunctionalInterface
public interface CacheLoader<K> {

  /**
   * Loads the file for the given key.
   *
   * @param key the key to load the file for
   * @return the path to the file if it exists, otherwise an empty optional
   * @throws MobileHarnessException if there is an error loading the file
   * @throws InterruptedException if the thread is interrupted while loading the file
   */
  Optional<Path> load(K key) throws MobileHarnessException, InterruptedException;
}
