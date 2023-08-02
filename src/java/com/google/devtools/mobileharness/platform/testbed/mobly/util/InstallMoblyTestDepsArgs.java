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

package com.google.devtools.mobileharness.platform.testbed.mobly.util;

import com.google.auto.value.AutoValue;
import java.time.Duration;
import java.util.Optional;

/** Args for installing Mobly test deps. */
@AutoValue
public abstract class InstallMoblyTestDepsArgs {

  /** Default timeout in seconds for "pip install". */
  public abstract Optional<Duration> defaultTimeout();

  /** Base URL of Python Package Index. */
  public abstract Optional<String> indexUrl();

  public static Builder builder() {
    return new AutoValue_InstallMoblyTestDepsArgs.Builder();
  }

  /** Builder for {@link InstallMoblyTestDepsArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setDefaultTimeout(Duration defaultTimeout);

    public abstract Builder setIndexUrl(String indexUrl);

    public abstract InstallMoblyTestDepsArgs build();
  }
}
