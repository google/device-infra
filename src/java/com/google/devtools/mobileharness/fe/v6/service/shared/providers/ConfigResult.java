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

package com.google.devtools.mobileharness.fe.v6.service.shared.providers;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Represents the result of a config fetch, separating service status from value presence. */
@AutoValue
public abstract class ConfigResult<T> {

  /** Returns whether the config service is available and the request succeeded. */
  public abstract boolean isAvailable();

  /** The fetched config. Present if the service is available AND the config exists. */
  public abstract Optional<T> config();

  public static <T> ConfigResult<T> available(Optional<T> config) {
    return new AutoValue_ConfigResult<>(true, config);
  }

  public static <T> ConfigResult<T> unavailable() {
    return new AutoValue_ConfigResult<>(false, Optional.empty());
  }
}
