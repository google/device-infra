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

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Wrapper class for "adb shell content". */
@AutoValue
public abstract class AndroidContent {
  /** Run "adb shell content" to get the list/usage of each command. */
  public enum Command {
    INSERT,
    UPDATE,
    DELETE,
    QUERY,
    CALL,
    READ,
    WRITE,
    GETTYPE;
  }

  /** Subcommand name as listed by Command. */
  public abstract Command command();

  /** Uri address for a particular content provider. */
  public abstract String uri();

  /** Other arguments for subcommand. */
  public abstract Optional<String> otherArgument();

  public static Builder builder() {
    return new AutoValue_AndroidContent.Builder();
  }

  /** Auto value builder for {@link AndroidContent}. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** Subcommand for content. */
    public abstract Builder setCommand(Command cmd);

    /** URI for content. */
    public abstract Builder setUri(String uri);

    /** Other arguments for content command. */
    public abstract Builder setOtherArgument(String otherArgument);

    public abstract AndroidContent build();
  }
}
