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
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/** A general container class for a document that contains the UserData record from a Mobly test. */
@AutoValue
public abstract class MoblyUserDataEntry implements MoblyYamlDocEntry {

  @Override
  public Type getType() {
    return Type.USERDATA;
  }

  public abstract String getTimestamp();

  /**
   * Mobly UserData is kept as a map. Anything can be contained inside this dict, but the base
   * structure is a map.
   */
  public abstract ImmutableMap<String, Object> getUserDataMap();

  public static Builder builder() {
    return new AutoValue_MoblyUserDataEntry.Builder();
  }

  /** MoblyUserDataEntry Builder class. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setTimestamp(String value);

    public abstract Builder setUserDataMap(Map<String, Object> value);

    public abstract MoblyUserDataEntry build();
  }
}
