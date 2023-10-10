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

package com.google.devtools.mobileharness.api.model.job.in;

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.TouchableTiming;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/** Input parameters. */
public class Params {

  /** Default splitter to split a long string into a string list. */
  private static final Splitter LIST_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

  /** Input parameters. */
  private final Map<String, String> params = new ConcurrentHashMap<>();

  /** The time records. */
  @Nullable private final TouchableTiming timing;

  /** Creates the parameters segment. */
  public Params(@Nullable TouchableTiming timing) {
    this.timing = timing;
  }

  /** Creates the parameters segment. */
  public Params() {
    this.timing = null;
  }

  /** Adds the given parameter. */
  @CanIgnoreReturnValue
  public Params add(String name, String value) {
    params.put(name, value);
    if (timing != null) {
      timing.touch();
    }
    return this;
  }

  /** Adds the given parameters. */
  @CanIgnoreReturnValue
  public Params addAll(Map<String, String> params) {
    this.params.putAll(params);
    if (timing != null) {
      timing.touch();
    }
    return this;
  }

  /** Returns whether the parameter map is empty. */
  public boolean isEmpty() {
    return params.isEmpty();
  }

  /** Returns the number of param pairs. */
  public int size() {
    return params.size();
  }

  /** Returns true if params has value of {@code name}. */
  public boolean has(String name) {
    return params.containsKey(name);
  }

  /**
   * Returns the parameter {name, value} mapping. Never return null. At least an empty map is
   * returned.
   */
  public ImmutableMap<String, String> getAll() {
    return ImmutableMap.copyOf(params);
  }

  /**
   * Returns the parameter value of the given parameter name, or empty if the parameter not exists.
   */
  public Optional<String> get(String name) {
    return Optional.ofNullable(params.get(name));
  }

  /**
   * Returns the parameter value of the given parameter name, or return the given default value if
   * the parameter doesn't exist or its value is empty.
   */
  public String get(String name, String defaultValue) {
    String value = params.get(name);
    if (StrUtil.isEmptyOrWhitespace(value)) {
      return defaultValue;
    } else {
      return value;
    }
  }

  /**
   * Gets a boolean from the parameter.
   *
   * @param name parameter name/key
   * @param defaultValue the default boolean value if the parameter does not exist or is empty
   */
  public boolean getBool(String name, boolean defaultValue) {
    Optional<String> value = get(name);
    if (value.isEmpty() || StrUtil.isEmptyOrWhitespace(value.get())) {
      return defaultValue;
    } else if (Ascii.equalsIgnoreCase(Boolean.TRUE.toString(), value.get())) {
      return true;
    } else if (Boolean.FALSE.toString().equalsIgnoreCase(value.get())) {
      return false;
    } else {
      return defaultValue;
    }
  }

  /**
   * Returns true only if the given parameter has value and the value equals(ignore case) to "true".
   */
  public boolean isTrue(String name) {
    Optional<String> value = get(name);
    return value.filter(Boolean::parseBoolean).isPresent();
  }

  /**
   * Gets an integer from the parameter.
   *
   * @param name parameter name/key.
   * @param defaultValue the default int value if the param not exists, or the param value is not
   *     valid
   */
  public int getInt(String name, int defaultValue) {
    Optional<String> value = get(name);
    if (value.isPresent() && !StrUtil.isEmptyOrWhitespace(value.get())) {
      try {
        return Integer.parseInt(value.get());
      } catch (NumberFormatException e) {
        // Ignored. Falls back to default value.
      }
    }
    return defaultValue;
  }

  /**
   * Gets a long from the parameter.
   *
   * @param name parameter name/key.
   * @param defaultValue the default long value if the param not exists, or the param value is not
   *     valid
   */
  public long getLong(String name, long defaultValue) {
    Optional<String> value = get(name);
    if (value.isPresent() && !StrUtil.isEmptyOrWhitespace(value.get())) {
      try {
        return Long.parseLong(value.get());
      } catch (NumberFormatException e) {
        // Ignored. Falls back to default value.
      }
    }
    return defaultValue;
  }

  /**
   * Gets a long from the parameter.
   *
   * @param name parameter name/key.
   * @throws MobileHarnessException if the param that doesn't exist, has empty value or cannot be
   *     parsed.
   */
  public long getLong(String name) throws MobileHarnessException {
    Optional<String> value = get(name);
    if (value.isPresent() && !StrUtil.isEmptyOrWhitespace(value.get())) {
      try {
        return Long.parseLong(value.get());
      } catch (NumberFormatException e) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_PARAM_VALUE_FORMAT_ERROR, "Failed to parse long", e);
      }
    }
    throw new MobileHarnessException(
        BasicErrorId.JOB_PARAM_VALUE_NOT_FOUND,
        "Failed to get the value of parameter "
            + name
            + " since it does not exist or has empty value");
  }

  /**
   * Gets a double from the parameter.
   *
   * @param name parameter name/key.
   * @param defaultValue the default int value if the param not exists, or the param value is not
   *     valid
   */
  public double getDouble(String name, double defaultValue) {
    Optional<String> value = get(name);
    if (value.isPresent() && !StrUtil.isEmptyOrWhitespace(value.get())) {
      try {
        return Double.parseDouble(value.get());
      } catch (NumberFormatException e) {
        // Ignored. Falls back to default value.
      }
    }
    return defaultValue;
  }

  /**
   * Gets the comma separated value list from the parameter.
   *
   * <p>Changes to the returned list will not be reflected in the params.
   *
   * @param name parameter name/key.
   * @param defaultValue the default if the param does not exist, or the param value is not valid
   */
  public List<String> getList(String name, List<String> defaultValue) {
    Optional<String> value = get(name);
    if (value.isPresent() && !StrUtil.isEmptyOrWhitespace(value.get())) {
      return Lists.newArrayList(LIST_SPLITTER.split(value.get()));
    }
    return defaultValue;
  }

  /**
   * Gets the value list from the parameter with the customized string separator.
   *
   * @param name parameter name/key.
   * @param separator string separator for splitting a parameter string to a value list.
   * @param defaultValue the default if the param does not exist, or the param value is not valid
   */
  public List<String> getList(String name, String separator, List<String> defaultValue) {
    Optional<String> value = get(name);
    if (value.isPresent() && !StrUtil.isEmptyOrWhitespace(value.get())) {
      Splitter listSplitter = Splitter.on(separator).trimResults().omitEmptyStrings();
      return Lists.newArrayList(listSplitter.split(value.get()));
    }
    return defaultValue;
  }

  /**
   * Checks whether the parameters exist and is not empty.
   *
   * @param paramNames parameter names/keys
   * @return the error list if there is any param that doesn't exist or has empty value, or an empty
   *     list if all params are valid
   */
  public List<MobileHarnessException> validateExist(String... paramNames) {
    List<String> errorParamNames = new ArrayList<>();
    for (String paramName : paramNames) {
      String paramValue = params.get(paramName);
      if (StrUtil.isEmptyOrWhitespace(paramValue)) {
        errorParamNames.add(paramName);
      }
    }
    if (!errorParamNames.isEmpty()) {
      return ImmutableList.of(
          new MobileHarnessException(
              BasicErrorId.JOB_PARAM_VALUE_NOT_FOUND,
              "Parameter(s) " + errorParamNames + " not found or empty"));
    } else {
      return ImmutableList.of();
    }
  }

  /**
   * Checks whether the parameters is a valid integer of the given range.
   *
   * @param paramName parameter name/key.
   * @return the error list if the parameter's value is not a valid integer of the given range
   */
  public List<MobileHarnessException> validateInt(String paramName, int min, int max) {
    String paramValue = params.get(paramName);
    String message = null;
    Throwable cause = null;
    if (!StrUtil.isEmptyOrWhitespace(paramValue)) {
      try {
        int paramNum = Integer.parseInt(paramValue);
        if (paramNum < min || paramNum > max) {
          message =
              String.format(
                  "You must set the parameter [%s] with an integer between [%d, %d]",
                  paramName, min, max);
        }
      } catch (NumberFormatException e) {
        message = "Parameter [" + paramName + "] should be an integer";
        cause = e;
      }
    } else {
      message = "Parameter [" + paramName + "] not found or empty";
    }
    if (message != null) {
      return ImmutableList.of(
          new MobileHarnessException(BasicErrorId.JOB_PARAM_VALUE_FORMAT_ERROR, message, cause));
    } else {
      return ImmutableList.of();
    }
  }

  /**
   * Checks whether the parameters is a valid boolean.
   *
   * <p>Valid boolean values are empty or "true" or "false", case-insensitive.
   *
   * @param paramName parameter name/key
   * @param optional true if parameter is optional, in which case it can also be an empty string
   * @return the error list if the parameter value is not a valid boolean
   */
  public List<MobileHarnessException> validateBool(String paramName, boolean optional) {
    String message = null;
    String paramValue = params.get(paramName);
    if (!StrUtil.isEmptyOrWhitespace(paramValue)) {
      // Boolean.parseBoolean() checks for "true", case-insensitive. Everything else is considered
      // false, even "yes", which can be misleading and is thus worth checking for.
      if (!Ascii.equalsIgnoreCase(Boolean.TRUE.toString(), paramValue)
          && !Ascii.equalsIgnoreCase(Boolean.FALSE.toString(), paramValue)) {
        message = "You must set the '" + paramName + "' parameter to either 'true' or 'false'.";
      }
    } else if (!optional) {
      message = "Parameter '" + paramName + "' not found or empty.";
    }
    if (message != null) {
      return ImmutableList.of(
          new MobileHarnessException(BasicErrorId.JOB_PARAM_VALUE_FORMAT_ERROR, message));
    } else {
      return ImmutableList.of();
    }
  }

  @Override
  public String toString() {
    StringBuilder stringBuilder = new StringBuilder("Parameters:\n");

    for (Map.Entry<String, String> entry : params.entrySet()) {
      stringBuilder.append(
          String.format("\tName: %s\n\tValue: %s\n", entry.getKey(), entry.getValue()));
    }

    return stringBuilder.toString();
  }
}
