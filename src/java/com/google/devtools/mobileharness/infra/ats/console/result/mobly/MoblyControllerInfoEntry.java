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

package com.google.devtools.mobileharness.infra.ats.console.result.mobly;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/**
 * A general container class for a document that contains the Controller information from a Mobly
 * test.
 */
@AutoValue
public abstract class MoblyControllerInfoEntry implements MoblyYamlDocEntry {

  @Override
  public Type getType() {
    return Type.CONTROLLER_INFO;
  }

  /**
   * List of devices relevant to the Mobly test. If no devices were given, then an empty map will be
   * returned.
   *
   * <p>Object is stored because ControllerInfo contains no standard format. Mobly results dump
   * ControllerInfo from a YAML object, so anything could be contained in this section.
   */
  public abstract Optional<Object> getDevices();

  public static Builder builder() {
    return new AutoValue_MoblyControllerInfoEntry.Builder();
  }

  /** MoblyControllerInfoEntry Builder class. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setDevices(Object value);

    public abstract MoblyControllerInfoEntry build();
  }
}
