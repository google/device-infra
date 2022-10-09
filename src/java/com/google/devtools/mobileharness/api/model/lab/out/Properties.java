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

package com.google.devtools.mobileharness.api.model.lab.out;

import java.util.Map;
import java.util.Optional;
import javax.annotation.concurrent.ThreadSafe;

/** Mobile Harness device properties. */
@ThreadSafe
public interface Properties {

  /**
   * Associates the specified property value with the specified property key. If the property map
   * previously contained a mapping for the key, the old value is replaced by the specified value.
   *
   * @param key property key with which the specified property value is to be associated
   * @param value property value to be associated with the specified property key
   * @return the previous property value associated with <tt>key</tt>, or <tt>empty</tt> if there
   *     was no mapping for <tt>key</tt>
   */
  Optional<String> put(String key, String value);

  /**
   * Returns the property value to which the specified key is mapped, or <tt>empty</tt> if the
   * property map does not contain the key.
   *
   * @param key the key whose associated property value is to be returned
   * @return the property value to which <tt>key</tt> is mapped, or <tt>empty</tt> if the property
   *     map contains no mapping for <tt>key</tt>
   */
  Optional<String> get(String key);

  /**
   * Removes the mapping for a property key if it is present.
   *
   * <p>Returns the property value to which the property map previously associated the property key,
   * or <tt>empty</tt> if the property map contained no mapping for the property key.
   *
   * @param key property key whose mapping is to be removed
   * @return the previous property value associated with <tt>key</tt>, or <tt>empty</tt> if there
   *     was no mapping for <tt>key</tt>.
   */
  Optional<String> remove(String key);

  /**
   * @return an immutable copy of the device properties
   */
  Map<String, String> getAll();

  /** Clears all properties of the device. */
  void clear();

  /**
   * @return the integer property value, or empty if the value is not an integer or not present
   */
  default Optional<Integer> getInteger(String key) {
    try {
      return get(key).map(Integer::parseInt);
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  /**
   * @return the boolean property value, or false if the value is not a boolean or not present
   */
  default boolean getBoolean(String key) {
    return get(key).map(Boolean::parseBoolean).orElse(false);
  }

  /**
   * @return whether the given property is present
   */
  default boolean has(String key) {
    return get(key).isPresent();
  }
}
