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

/**
 * Wrapper class for "adb shell svc".
 *
 * <p>See http://cs/android/frameworks/base/cmds/svc/src/com/android/commands/svc/Svc.java for more
 * information.
 */
@AutoValue
public abstract class AndroidSvc {

  /** Run "adb shell svc help" to get the list/usage of each command. */
  public enum Command {
    POWER, // Available on API >= 15
    DATA, // Available on API >= 15
    WIFI, // Available on API >= 15
    USB, // Available on API >= 16
    NFC, // Available on API >= 24
    BLUETOOTH; // Available on API >= 27
  }

  /** Subcommand name as listed by Command. */
  public abstract Command command();

  /** Other arguments for subcommand. */
  public abstract Optional<String> otherArgs();

  public static Builder builder() {
    return new AutoValue_AndroidSvc.Builder();
  }

  /** Auto value builder for {@link AndroidSvc}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setCommand(Command command);

    public abstract Builder setOtherArgs(String otherArgs);

    public abstract AndroidSvc build();
  }
}
