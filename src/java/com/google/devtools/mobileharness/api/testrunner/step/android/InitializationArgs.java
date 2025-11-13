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

package com.google.devtools.mobileharness.api.testrunner.step.android;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Arguments for device initialization. */
@AutoValue
public abstract class InitializationArgs {

  /**
   * Whether to skip setup wizard after flashing or factory reset. Skipping setup wizard works on
   * Google Experience Device with userdebug build only.
   */
  public abstract Optional<Boolean> skipSetupWizard();

  /**
   * Number of attempts to wait for device to initialize after flashing for each
   * device_init_attempts.
   */
  public abstract Optional<Integer> deviceOnlineWaitAttempts();

  public static Builder builder() {
    return new AutoValue_InitializationArgs.Builder();
  }

  /** Builder for {@link InitializationArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setSkipSetupWizard(Boolean skipSetupWizard);

    public abstract Builder setDeviceOnlineWaitAttempts(Integer deviceOnlineWaitAttempts);

    public abstract InitializationArgs build();
  }
}
