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

package com.google.devtools.deviceinfra.platform.android.sdk.fastboot.initializer;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Params for fastboot library. */
@AutoValue
public abstract class FastbootParam {

  /** Fastboot binary path. */
  public abstract Optional<String> fastbootPath();

  /**
   * "mke2fs" binary path.
   *
   * <p>Required when running on the Linux system. The "mke2fs" binary must live within the same
   * directory as the fastboot binary.
   */
  public abstract Optional<String> mke2fsPath();

  /** Error message in the fastboot initialization. */
  public abstract Optional<String> initializationError();

  /** Builder for value class {@link FastbootParam}. */
  public static Builder builder() {
    return new AutoValue_FastbootParam.Builder();
  }

  /** Builder for {@link FastbootParam}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setFastbootPath(String fastbootPath);

    public abstract Builder setMke2fsPath(String mke2fsPath);

    public abstract Builder setInitializationError(String initializationError);

    public abstract FastbootParam build();
  }
}
