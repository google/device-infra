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
import com.google.devtools.deviceaction.framework.proto.ActionSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceSpec;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Optional;

/** Data structure for device actions. */
@AutoValue
public abstract class ActionConfig {

  public abstract Command cmd();

  public abstract ActionSpec actionSpec();

  public abstract Optional<DeviceSpec> firstSpec();

  public abstract Optional<DeviceSpec> secondSpec();

  public static Builder builder() {
    return new AutoValue_ActionConfig.Builder();
  }

  /** Builder for {@link ActionConfig}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setCmd(Command cmd);

    public abstract Builder setActionSpec(ActionSpec actionSpec);

    public abstract Builder setFirstSpec(DeviceSpec firstSpec);

    public abstract Builder setSecondSpec(DeviceSpec secondSpec);

    /** Sets the {@link DeviceSpec} at the position {@code devicePosition}. */
    @CanIgnoreReturnValue
    public Builder setDeviceSpec(DevicePosition devicePosition, DeviceSpec deviceSpec) {
      switch (devicePosition) {
        case FIRST:
          return this.setFirstSpec(deviceSpec);
        case SECOND:
          return this.setSecondSpec(deviceSpec);
      }
      return this;
    }

    public abstract ActionConfig build();
  }
}
