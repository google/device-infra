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

package com.google.devtools.mobileharness.platform.android.user;

import com.google.auto.value.AutoValue;
import java.util.Optional;
import java.util.OptionalInt;

/** Wrapper for "pm create-user" command arguments. */
@AutoValue
public abstract class AndroidCreateUserArgs {

  /** User name to be created. */
  public abstract String userName();

  /** User ID for profile */
  public abstract OptionalInt profileOf();

  /** Indicates that this user is a profile of another user. */
  public abstract Optional<Boolean> managed();

  /** Indicates the user has restrictions in privileges, in addition to those for normal users. */
  public abstract Optional<Boolean> restricted();

  /** Indicates that this user is ephemeral. */
  public abstract Optional<Boolean> ephemeral();

  /** Indicates a guest user that may be transient. */
  public abstract Optional<Boolean> guest();

  public static Builder builder() {
    return new AutoValue_AndroidCreateUserArgs.Builder();
  }

  /** Builder for {@link AndroidCreateUserArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setUserName(String userName);

    public abstract Builder setProfileOf(int profileOf);

    public abstract Builder setManaged(boolean managed);

    public abstract Builder setRestricted(boolean restricted);

    public abstract Builder setEphemeral(boolean ephemeral);

    public abstract Builder setGuest(boolean guest);

    public abstract AndroidCreateUserArgs build();
  }
}
