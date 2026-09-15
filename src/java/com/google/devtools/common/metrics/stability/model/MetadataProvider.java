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

package com.google.devtools.common.metrics.stability.model;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;

/** Provider for adding and getting exception metadata. */
public interface MetadataProvider {

  /**
   * Adds a metadata key-value pair.
   *
   * @param key the metadata key, must not be null
   * @param value the metadata value, must not be null
   */
  void addMetadata(String key, String value);

  /**
   * Adds all metadata entries from the given map.
   *
   * @param metadata the map containing key-value pairs to add, must not be null
   */
  default void addMetadata(Map<String, String> metadata) {
    checkNotNull(metadata, "metadata");
    metadata.forEach(this::addMetadata);
  }

  /**
   * Returns all metadata as an immutable map.
   *
   * @return Map containing all metadata, or an empty map if no metadata exists.
   */
  ImmutableMap<String, String> getMetadata();

  /**
   * Returns the metadata value of the given key, or empty if the key does not exist.
   *
   * @param key the metadata key
   */
  default Optional<String> getMetadata(String key) {
    return Optional.ofNullable(getMetadata().get(key));
  }
}
