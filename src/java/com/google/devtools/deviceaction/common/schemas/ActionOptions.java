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

package com.google.devtools.deviceaction.common.schemas;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A value class for action options.
 *
 * <p>It is an intermediate data class between plain flags and structured {@link ActionConfig}.
 */
@AutoValue
public abstract class ActionOptions {

  /** A value class for various options. */
  @AutoValue
  public abstract static class Options {
    private static final String ERROR_NAME = "INVALID_OPTION";

    /** True valued bool options. */
    public abstract ImmutableList<String> trueBoolOptions();

    /** False valued bool options. */
    public abstract ImmutableList<String> falseBoolOptions();

    /** Options as key-value pairs */
    public abstract ImmutableMultimap<String, String> keyValues();

    /** Options for files. The keys are tags. */
    public abstract ImmutableMultimap<String, String> fileOptions();

    public static Builder builder() {
      return new AutoValue_ActionOptions_Options.Builder()
          .setTrueBoolOptions(ImmutableList.of())
          .setFalseBoolOptions(ImmutableList.of())
          .setKeyValues(ImmutableMultimap.of())
          .setFileOptions(ImmutableMultimap.of());
    }

    /**
     * Returns the possible single value for the key.
     *
     * @throws DeviceActionException if there are multiple values.
     */
    public Optional<String> getOnlyValue(String key) throws DeviceActionException {
      try {
        return Optional.of(Iterables.getOnlyElement(keyValues().get(key)));
      } catch (NoSuchElementException e) {
        return Optional.empty();
      } catch (IllegalArgumentException e) {
        throw new DeviceActionException(
            ERROR_NAME,
            ErrorType.CUSTOMER_ISSUE,
            String.format(
                "The options %s should not have multiple values for key %s", keyValues(), key),
            e);
      }
    }

    /** Builder for {@link Options}. */
    @AutoValue.Builder
    public abstract static class Builder {
      abstract ImmutableList.Builder<String> trueBoolOptionsBuilder();

      abstract ImmutableList.Builder<String> falseBoolOptionsBuilder();

      abstract ImmutableMultimap.Builder<String, String> keyValuesBuilder();

      abstract ImmutableMultimap.Builder<String, String> fileOptionsBuilder();

      abstract Builder setTrueBoolOptions(List<String> values);

      abstract Builder setFalseBoolOptions(List<String> values);

      abstract Builder setKeyValues(Multimap<String, String> values);

      abstract Builder setFileOptions(Multimap<String, String> values);

      abstract Options autoBuild();

      /**
       * Builds the value.
       *
       * @throws DeviceActionException if some bool option is set to be true and false at the same
       *     time.
       */
      public final Options build() throws DeviceActionException {
        Options options = autoBuild();
        try {
          Preconditions.checkState(
              Collections.disjoint(options.trueBoolOptions(), options.falseBoolOptions()));
        } catch (IllegalStateException e) {
          throw new DeviceActionException(
              ERROR_NAME,
              ErrorType.CUSTOMER_ISSUE,
              String.format(
                  "A bool option can't be set to be true and false at the same time. Please double"
                      + " check true options %s and false options %s",
                  options.trueBoolOptions(), options.falseBoolOptions()),
              e);
        }
        return options;
      }

      @CanIgnoreReturnValue
      public final Builder addTrueBoolOptions(String... options) {
        trueBoolOptionsBuilder().add(options);
        return this;
      }

      @CanIgnoreReturnValue
      public final Builder addFalseBoolOptions(String... options) {
        falseBoolOptionsBuilder().add(options);
        return this;
      }

      @CanIgnoreReturnValue
      public final Builder addKeyValues(String key, String... values) {
        keyValuesBuilder().putAll(key, values);
        return this;
      }

      @CanIgnoreReturnValue
      public final Builder addFileOptions(String key, String... values) {
        fileOptionsBuilder().putAll(key, values);
        return this;
      }
    }
  }

  public abstract Command command();

  public abstract Options action();

  @Nullable
  public abstract Options firstDevice();

  @Nullable
  public abstract Options secondDevice();

  public static Builder builder() {
    return new AutoValue_ActionOptions.Builder();
  }

  /** Builder for {@link ActionOptions}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setCommand(Command value);

    public abstract Builder setAction(Options value);

    public abstract Builder setFirstDevice(Options value);

    public abstract Builder setSecondDevice(Options value);

    public abstract ActionOptions build();
  }
}
