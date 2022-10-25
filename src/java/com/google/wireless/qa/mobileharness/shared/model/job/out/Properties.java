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

package com.google.wireless.qa.mobileharness.shared.model.job.out;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

/** Output properties of a job/test. */
public class Properties {
  /** Properties generated during execution. */
  private final ConcurrentMap<String, String> properties = new ConcurrentHashMap<>();

  /** The time records of the job/test. */
  private final Timing timing;

  /** Creates the output properties segment of a job/test. */
  public Properties(Timing timing) {
    this.timing = timing;
  }

  /**
   * Maps the specified key to the specified value in the properties. Neither the key nor the value
   * can be null.
   *
   * @return the previous value associated with <tt>key</tt>, or <tt>null</tt> if there was no
   *     mapping for <tt>key</tt>
   * @throws NullPointerException if the specified key or value is null
   */
  @Nullable
  public String add(String key, String value) {
    String previousValue = properties.put(key, value);
    timing.touch();
    return previousValue;
  }

  /**
   * Maps the specified key to the specified value in the properties. Neither the key nor the value
   * can be null.
   *
   * @return the previous value associated with <tt>key</tt>, or <tt>null</tt> if there was no
   *     mapping for <tt>key</tt>
   * @throws NullPointerException if the specified key or value is null
   */
  public String add(PropertyName key, String value) {
    return add(key.toString().toLowerCase(), value);
  }

  /**
   * If the specified key is not already associated with a value, associate it with the given valu
   * in the properties. Neither the key nor the value can be null.
   *
   * @return the previous value associated with <tt>key</tt>, or <tt>null</tt> if there was no
   *     mapping for <tt>key</tt>
   * @throws NullPointerException if the specified key or value is null
   */
  @Nullable
  public String addIfAbsent(String key, String value) {
    String previousValue = properties.putIfAbsent(key, value);
    timing.touch();
    return previousValue;
  }

  /**
   * If the specified key is not already associated with a value, associate it with the given valu
   * in the properties. Neither the key nor the value can be null.
   *
   * @return the previous value associated with <tt>key</tt>, or <tt>null</tt> if there was no
   *     mapping for <tt>key</tt>
   * @throws NullPointerException if the specified key or value is null
   */
  public String addIfAbsent(PropertyName key, String value) {
    return addIfAbsent(key.toString().toLowerCase(), value);
  }

  /**
   * Adds all the given properties.
   *
   * @throws NullPointerException if the specified key or value is null
   */
  @CanIgnoreReturnValue
  public Properties addAll(Map<String, String> properties) {
    this.properties.putAll(properties);
    timing.touch();
    return this;
  }

  /** Returns whether the property map is empty. */
  public boolean isEmpty() {
    return properties.isEmpty();
  }

  /**
   * Returns the value to which the specified key is mapped in the properties, or {@code null} if
   * the properties contains no mapping for the key.
   *
   * @throws NullPointerException if the specified key is null
   */
  @Nullable
  public String get(String key) {
    return properties.get(key);
  }

  /**
   * Returns the value to which the specified key is mapped in the properties, or {@code null} if
   * the properties contains no mapping for the key.
   *
   * @throws NullPointerException if the specified key is null
   */
  @Nullable
  public String get(PropertyName key) {
    return get(key.toString().toLowerCase());
  }

  /**
   * Gets the optional value for the given key.
   *
   * @param key the required key
   * @return the value of the given if the key exists; else, return Optional.empty
   */
  public Optional<String> getOptional(String key) {
    return Optional.ofNullable(properties.get(key));
  }

  /**
   * Gets the optional value for the given key.
   *
   * @param key the required key
   * @return the value of the given if the key exists; else, return Optional.empty
   */
  public Optional<String> getOptional(PropertyName key) {
    return getOptional(Ascii.toLowerCase(key.toString()));
  }

  /** Checks whether there is a property with the given name. */
  public boolean has(String key) {
    return properties.containsKey(key);
  }

  /** Checks whether there is a property with the given property name. */
  public boolean has(PropertyName key) {
    return has(Ascii.toLowerCase(key.toString()));
  }

  /**
   * Returns the long value to which the specified key is mapped in the properties, or empty if the
   * properties contains no mapping for the key or the value is not a valid long.
   */
  public Optional<Long> getLong(String key) {
    try {
      return Optional.of(Long.parseLong(get(key)));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  /**
   * Returns the long value to which the specified key is mapped in the properties, or empty if the
   * properties contains no mapping for the key or the value is not a valid long.
   */
  public Optional<Long> getLong(PropertyName key) {
    return getLong(key.toString().toLowerCase());
  }

  /**
   * Adds the delta to the current value (or {@code 0L} if there is no current value or the current
   * value is not a parsable long) of the specified key.
   *
   * <p>For example, if the current value is "ab", plusLong(key, 10) will set the value to 10 and
   * return 10. If the current value is "5", plusLong(key, 10) will set the value to 15 and return
   * 15.
   *
   * @return the new value
   * @throws NullPointerException if the key is {@code null}
   */
  public long plusLong(String key, long delta) {
    return Long.parseLong(
        properties.compute(
            key,
            (k, v) -> {
              long oldValue;
              if (v != null) {
                try {
                  oldValue = Long.parseLong(v);
                } catch (NumberFormatException e) {
                  oldValue = 0L;
                }
              } else {
                oldValue = 0L;
              }
              return Long.toString(oldValue + delta);
            }));
  }

  /**
   * Adds the delta to the current value (or {@code 0L} if there is no current value or the current
   * value is not a parsable long) of the specified key.
   *
   * <p>For example, if the current value is "ab", plusLong(key, 10) will set the value to 10 and
   * return 10. If the current value is "5", plusLong(key, 10) will set the value to 15 and return
   * 15.
   *
   * @return the new value
   * @throws NullPointerException if the key is {@code null}
   */
  public long plusLong(PropertyName key, long delta) {
    return plusLong(key.toString().toLowerCase(), delta);
  }

  /**
   * Returns the boolean value stored at the key. If no value is stored then Optional.empty() is
   * returned.
   */
  public Optional<Boolean> getBoolean(String key) {
    String value = get(key);
    if (StrUtil.isEmptyOrWhitespace(value)) {
      return Optional.empty();
    } else if (Boolean.TRUE.toString().equalsIgnoreCase(value)) {
      return Optional.of(true);
    } else if (Boolean.FALSE.toString().equalsIgnoreCase(value)) {
      return Optional.of(false);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Returns the boolean value stored at the key. If no value is stored then Optional.empty() is
   * returned.
   */
  public Optional<Boolean> getBoolean(PropertyName key) {
    return getBoolean(key.toString().toLowerCase());
  }

  /** Returns all the properties. */
  public ImmutableMap<String, String> getAll() {
    return ImmutableMap.copyOf(properties);
  }

  /**
   * Removes the property.
   *
   * @return the previous value associated with <tt>key</tt>, or <tt>null</tt> if there was no
   *     mapping for <tt>key</tt>
   * @throws NullPointerException if the specified key is null
   */
  @Nullable
  public String remove(String key) {
    String previousValue = properties.remove(key);
    timing.touch();
    return previousValue;
  }

  /**
   * Removes the property.
   *
   * @return the previous value associated with <tt>key</tt>, or <tt>null</tt> if there was no
   *     mapping for <tt>key</tt>
   * @throws NullPointerException if the specified key is null
   */
  public String remove(PropertyName key) {
    return remove(key.toString().toLowerCase());
  }
}
