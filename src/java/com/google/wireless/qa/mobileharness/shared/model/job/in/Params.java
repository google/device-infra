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

package com.google.wireless.qa.mobileharness.shared.model.job.in;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/** Input parameters. */
public class Params {
  private final com.google.devtools.mobileharness.api.model.job.in.Params newParams;

  /** Creates the parameters segment. */
  public Params(@Nullable Timing timing) {
    newParams =
        new com.google.devtools.mobileharness.api.model.job.in.Params(
            timing == null ? null : timing.toNewTiming());
  }

  /**
   * Creates the params segment by the given api {@link
   * com.google.devtools.mobileharness.api.model.job.in.Params}. Note: please don't make this public
   * at any time.
   */
  Params(com.google.devtools.mobileharness.api.model.job.in.Params newParams) {
    this.newParams = newParams;
  }

  /**
   * @return the new data model which has the same backend of this object.
   */
  public com.google.devtools.mobileharness.api.model.job.in.Params toNewParams() {
    return newParams;
  }

  /** Adds the given parameter. */
  @CanIgnoreReturnValue
  public Params add(String name, String value) {
    newParams.add(name, value);
    return this;
  }

  /** Adds the given parameters. */
  @CanIgnoreReturnValue
  public Params addAll(Map<String, String> params) {
    newParams.addAll(params);
    return this;
  }

  /** Returns whether the parameter map is empty. */
  public boolean isEmpty() {
    return newParams.isEmpty();
  }

  /** Returns true if params has value of {@code name}. */
  public boolean has(String name) {
    return newParams.has(name);
  }

  /**
   * Returns the parameter {name, value} mapping. Never return null. At least an empty map is
   * returned.
   */
  public ImmutableMap<String, String> getAll() {
    return newParams.getAll();
  }

  /**
   * Returns the parameter value of the given parameter name, or null if the parameter not exists.
   */
  @Nullable
  public String get(String name) {
    return newParams.get(name).orElse(null);
  }

  /**
   * Returns the parameter value of the given parameter name, or return the given default value if
   * the parameter doesn't exist or its value is empty.
   */
  public String get(String name, String defaultValue) {
    return newParams.get(name, defaultValue);
  }

  /**
   * Returns the parameter value of the given parameter name, or Optional.empty() if the parameter
   * not exists.
   */
  public Optional<String> getOptional(String name) {
    return newParams.get(name);
  }

  /**
   * Gets a boolean from the parameter.
   *
   * @param name parameter name/key
   * @param defaultValue the default boolean value if the parameter does not exist or is empty
   */
  public boolean getBool(String name, boolean defaultValue) {
    return newParams.getBool(name, defaultValue);
  }

  /**
   * Returns true only if the given parameter has value and the value equals(ignore case) to "true".
   */
  public boolean isTrue(String name) {
    return newParams.isTrue(name);
  }

  /**
   * Gets an integer from the parameter.
   *
   * @param name parameter name/key.
   * @param defaultValue the default int value if the param not exists, or the param value is not
   *     valid
   */
  public int getInt(String name, int defaultValue) {
    return newParams.getInt(name, defaultValue);
  }

  /**
   * Gets a long from the parameter.
   *
   * @param name parameter name/key.
   * @param defaultValue the default long value if the param not exists, or the param value is not
   *     valid
   */
  public long getLong(String name, long defaultValue) {
    return newParams.getLong(name, defaultValue);
  }

  /**
   * Gets a long from the parameter.
   *
   * @param name parameter name/key.
   * @throws MobileHarnessException if the param that doesn't exist, has empty value or cannot be
   *     parsed.
   */
  public long getLong(String name) throws MobileHarnessException {
    return newParams.getLong(name);
  }

  /**
   * Gets a double from the parameter.
   *
   * @param name parameter name/key.
   * @param defaultValue the default int value if the param not exists, or the param value is not
   *     valid
   */
  public double getDouble(String name, double defaultValue) {
    return newParams.getDouble(name, defaultValue);
  }

  /**
   * Gets the comma separated value list from the parameter.
   *
   * @param name parameter name/key.
   * @param defaultValue the default if the param does not exist, or the param value is not valid
   */
  public List<String> getList(String name, List<String> defaultValue) {
    return newParams.getList(name, defaultValue);
  }

  /**
   * Gets the value list from the parameter with the customized string separator.
   *
   * @param name parameter name/key.
   * @param separator string separator for splitting a parameter string to a value list.
   * @param defaultValue the default if the param does not exist, or the param value is not valid
   */
  public List<String> getList(String name, String separator, List<String> defaultValue) {
    return newParams.getList(name, separator, defaultValue);
  }

  /**
   * Checks whether the parameters exist and is not empty.
   *
   * @param paramNames parameter names/keys
   * @throws MobileHarnessException if there is any param that doesn't exist or has empty value
   */
  public void checkExist(String... paramNames) throws MobileHarnessException {
    List<com.google.devtools.mobileharness.api.model.error.MobileHarnessException> errors =
        newParams.validateExist(paramNames);
    if (!errors.isEmpty()) {
      throw errors.get(0);
    }
  }

  /**
   * Checks whether the parameters is a valid integer of the given range.
   *
   * @param paramName parameter name/key.
   * @throws MobileHarnessException if the parameter's value is not a valid integer of the given
   *     range
   */
  public void checkInt(String paramName, int min, int max) throws MobileHarnessException {
    List<com.google.devtools.mobileharness.api.model.error.MobileHarnessException> errors =
        newParams.validateInt(paramName, min, max);
    if (!errors.isEmpty()) {
      throw errors.get(0);
    }
  }

  /**
   * Checks whether the parameters is a valid boolean.
   *
   * <p>Valid boolean values are empty or "true" or "false", case-insensitive.
   *
   * @param paramName parameter name/key
   * @param optional true if parameter is optional, in which case it can also be an empty string
   * @throws MobileHarnessException if the parameter value is not a valid boolean
   */
  public void checkBool(String paramName, boolean optional) throws MobileHarnessException {
    List<com.google.devtools.mobileharness.api.model.error.MobileHarnessException> errors =
        newParams.validateBool(paramName, optional);
    if (!errors.isEmpty()) {
      throw errors.get(0);
    }
  }

  @Override
  public String toString() {
    return newParams.toString();
  }
}
