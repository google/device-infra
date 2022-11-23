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

package com.google.devtools.mobileharness.platform.android.shared.autovalue;

import com.google.auto.value.AutoValue;
import java.util.Optional;
import java.util.OptionalInt;

/** Wrapper for commonly used arguments by Mobile Harness Lightning API. */
@AutoValue
public abstract class UtilArgs {

  /** Serial number for the device. */
  public abstract String serial();

  /** SDK version for the device. */
  public abstract OptionalInt sdkVersion();

  /** User ID under which the adb command executes. */
  public abstract Optional<String> userId();

  public static Builder builder() {
    return new AutoValue_UtilArgs.Builder();
  }

  /** Builder for {@link UtilArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSerial(String serial);

    public abstract Builder setSdkVersion(int sdkVersion);

    public abstract Builder setUserId(String userId);

    public abstract UtilArgs build();
  }
}
