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

package com.google.devtools.mobileharness.platform.android.lightning.accountmanager;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.platform.android.accountmanager.AndroidAccountSetting;
import java.util.Optional;

/** Wrapper for arguments used for adding account to device. */
@AutoValue
public abstract class AddAccountArgs {

  /** Account setting for adding Google account on device. */
  public abstract AndroidAccountSetting accountSetting();

  /** Whether to ignore login error when adding Google account on device. */
  public abstract Optional<Boolean> ignoreLoginError();

  /** Maximal number of attempts to add Google account on device until it succeeds. */
  public abstract Optional<Integer> maxAttempts();

  public static Builder builder() {
    return new AutoValue_AddAccountArgs.Builder();
  }

  /** Auto value builder for {@link AddAccountArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setAccountSetting(AndroidAccountSetting accountSetting);

    public abstract Builder setIgnoreLoginError(boolean ignoreLoginError);

    public abstract Builder setMaxAttempts(int maxAttempts);

    public abstract AddAccountArgs build();
  }
}
