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

package com.google.devtools.mobileharness.platform.android.accountmanager;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/**
 * Android account setting for adding Android account on device by {@link
 * AndroidAccountManagerUtil}.
 */
@AutoValue
public abstract class AndroidAccountSetting {

  /** The email address of the Google account. */
  public abstract String email();

  /**
   * Credential type to add account. Defaults to {@link AccountCredentialType#PASSWORD} if not set.
   */
  public abstract AccountCredentialType credentialType();

  /** Password of the Google account. */
  public abstract String password();

  /** The type of the Google account. */
  public abstract AndroidGoogleAccountType accountType();

  /** Whether to enable account auto sync. */
  public abstract boolean autoSync();

  public static Builder builder() {
    return new AutoValue_AndroidAccountSetting.Builder();
  }

  /** Auto value builder for {@link AndroidAccountSetting}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setEmail(String email);

    public abstract Builder setCredentialType(AccountCredentialType credentialType);

    public abstract Builder setPassword(String password);

    public abstract Builder setAccountType(AndroidGoogleAccountType accountType);

    public abstract Builder setAutoSync(boolean autoSync);

    abstract Optional<AccountCredentialType> credentialType();

    abstract AndroidAccountSetting autoBuild();

    public AndroidAccountSetting build() {
      if (credentialType().isEmpty()) {
        setCredentialType(AccountCredentialType.PASSWORD);
      }
      return autoBuild();
    }
  }
}
