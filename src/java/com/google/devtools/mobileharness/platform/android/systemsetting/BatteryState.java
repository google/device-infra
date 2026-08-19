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

package com.google.devtools.mobileharness.platform.android.systemsetting;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Battery state of an Android device. */
@AutoValue
public abstract class BatteryState {

  /** Battery level in percentage (0-100). */
  public abstract Optional<Integer> level();

  /** Battery temperature in Celsius. */
  public abstract Optional<Integer> temperature();

  /** Battery health code (e.g., 2 for BATTERY_HEALTH_GOOD). */
  public abstract Optional<Integer> health();

  public static Builder builder() {
    return new AutoValue_BatteryState.Builder();
  }

  /** Auto value builder for {@link BatteryState}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setLevel(int level);

    public abstract Builder setTemperature(int temperature);

    public abstract Builder setHealth(int health);

    public abstract BatteryState build();
  }
}
