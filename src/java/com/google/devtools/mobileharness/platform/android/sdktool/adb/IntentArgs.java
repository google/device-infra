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

package com.google.devtools.mobileharness.platform.android.sdktool.adb;

import static com.google.devtools.mobileharness.shared.util.shell.ShellUtils.shellEscape;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import java.util.Map;
import java.util.Optional;

/**
 * Intent arguments for activity manager commands.
 *
 * @see <a href="https://developer.android.com/studio/command-line/adb#IntentSpec">Specification for
 *     intent arguments</a>
 */
@AutoValue
public abstract class IntentArgs {

  private static final String ARG_EXTRA_STRING = "-e";

  private static final String ARG_EXTRA_BOOLEAN = "--ez";

  private static final String ARG_EXTRA_INT = "--ei";

  private static final String ARG_EXTRA_LONG = "--el";

  private static final String ARG_EXTRA_FLOAT = "--ef";

  private static final String ARG_EXTRA_STRING_A = "--esa";

  /** Intent action, such as android.intent.action.VIEW. */
  public abstract Optional<String> action();

  /**
   * Component name with package name prefix to create an explicit intent, such as
   * com.example.app/.ExampleActivity.
   */
  public abstract Optional<String> component();

  /** Map of key-value pairs for extras string data with argument flag {@code -e}. */
  public abstract Optional<ImmutableMap<String, String>> extras();

  /** Map of key-value pairs for extras boolean data. */
  public abstract Optional<ImmutableMap<String, Boolean>> extrasBoolean();

  /** Map of key-value pairs for extras integer data. */
  public abstract Optional<ImmutableMap<String, Integer>> extrasInt();

  /** Map of key-value pairs for extras long data. */
  public abstract Optional<ImmutableMap<String, Long>> extrasLong();

  /** Map of key-value pairs for extras float data. */
  public abstract Optional<ImmutableMap<String, Float>> extrasFloat();

  /** Map of key-value pairs for extras String list separated by ",". */
  public abstract Optional<ImmutableMap<String, String>> extrasStringArray();

  /**
   * Specify a URI, package name, component name or other arguments when not qualified by one of the
   * above options.
   *
   * @see <a href="https://developer.android.com/studio/command-line/adb#IntentSpec">Specification
   *     for intent arguments</a>
   */
  public abstract Optional<String> otherArgument();

  @Memoized
  public String getIntentArgsString() {
    StringBuilder params = new StringBuilder();
    if (action().isPresent()) {
      params.append(String.format("-a %s", shellEscape(action().get())));
    }
    if (component().isPresent()) {
      params.append(String.format(" -n %s", shellEscape(component().get())));
    }

    addExtraArgs(params, ARG_EXTRA_STRING, extras());
    addExtraArgs(params, ARG_EXTRA_BOOLEAN, extrasBoolean());
    addExtraArgs(params, ARG_EXTRA_INT, extrasInt());
    addExtraArgs(params, ARG_EXTRA_LONG, extrasLong());
    addExtraArgs(params, ARG_EXTRA_FLOAT, extrasFloat());
    addExtraArgs(params, ARG_EXTRA_STRING_A, extrasStringArray());

    if (otherArgument().isPresent() && !StrUtil.isEmptyOrWhitespace(otherArgument().get())) {
      params.append(" ").append(otherArgument().get());
    }
    return params.toString().trim();
  }

  private static <V> void addExtraArgs(
      StringBuilder params, String argExtra, Optional<ImmutableMap<String, V>> extrasMap) {
    if (extrasMap.isPresent()) {
      for (Map.Entry<String, V> entry : extrasMap.get().entrySet()) {
        params.append(
            String.format(
                " %s %s %s",
                argExtra,
                shellEscape(entry.getKey()),
                shellEscape(String.valueOf(entry.getValue()))));
      }
    }
  }

  public static Builder builder() {
    return new AutoValue_IntentArgs.Builder();
  }

  /** Auto value builder for {@link IntentArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** Optional intent action. */
    public abstract Builder setAction(String action);

    /** Optional component name. */
    public abstract Builder setComponent(String component);

    /** Optional. Map of key-value pairs for extras string data with argument flag {@code -e}. */
    public abstract Builder setExtras(ImmutableMap<String, String> extras);

    /** Optional. Map of key-value pairs for extras boolean data. */
    public abstract Builder setExtrasBoolean(ImmutableMap<String, Boolean> extras);

    /** Optional. Map of key-value pairs for extras integer data. */
    public abstract Builder setExtrasInt(ImmutableMap<String, Integer> extras);

    /** Optional. Map of key-value pairs for extras long data. */
    public abstract Builder setExtrasLong(ImmutableMap<String, Long> extras);

    /** Map of key-value pairs for extras float data. */
    public abstract Builder setExtrasFloat(ImmutableMap<String, Float> extras);

    /** Optional arguments if other arguments don't qualify. */
    public abstract Builder setOtherArgument(String otherArgument);

    /**
     * Optional. Map of key-value pairs for extras string list ("," separated) with argument flag
     * {@code --esa}.
     */
    public abstract Builder setExtrasStringArray(ImmutableMap<String, String> extras);

    public abstract IntentArgs build();
  }
}
