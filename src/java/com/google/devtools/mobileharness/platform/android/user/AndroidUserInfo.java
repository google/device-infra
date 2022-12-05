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

/** Per-user information. */
@AutoValue
public abstract class AndroidUserInfo {

  /** User Name */
  public abstract String userName();

  /** User ID */
  public abstract int userId();

  /** Flags to indicate user type and profile. */
  public abstract int flag();

  /** User state, could be extended if other states are needed later. */
  public abstract boolean isRunning();

  /** Below flags for user info is directly copied from Android framework. */
  /**
   * Primary user. Only one user can have this flag set. It identifies the first human user on a
   * device.
   */
  public static final int FLAG_PRIMARY = 0x00000001;

  /** Indicates a guest user that may be transient. There can be only one guest user at a time. */
  public static final int FLAG_GUEST = 0x00000004;

  /**
   * Indicates the user has restrictions in privileges, in addition to those for normal users. Exact
   * meaning TBD. For instance, maybe they can't install apps or administer WiFi access pts.
   */
  public static final int FLAG_RESTRICTED = 0x00000008;

  /**
   * Indicates that this user is a profile of another user, for example holding a users corporate
   * data.
   */
  public static final int FLAG_MANAGED_PROFILE = 0x00000020;

  /**
   * Indicates that this user is ephemeral. I.e. the user will be removed after leaving the
   * foreground.
   */
  public static final int FLAG_EPHEMERAL = 0x00000100;

  /** Refer to UserHandle.USER_SYSTEM. */
  public static final int USER_SYSTEM = 0;

  public boolean isPrimary() {
    return (flag() & FLAG_PRIMARY) == FLAG_PRIMARY;
  }

  public boolean isGuest() {
    return (flag() & FLAG_GUEST) == FLAG_GUEST;
  }

  public boolean isSystem() {
    return userId() == USER_SYSTEM;
  }

  public static Builder builder() {
    return new AutoValue_AndroidUserInfo.Builder();
  }

  /** Builder for {@link AndroidUserInfo}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setUserName(String userName);

    public abstract Builder setUserId(int userId);

    public abstract Builder setFlag(int flag);

    public abstract Builder setIsRunning(boolean isRunning);

    public abstract AndroidUserInfo build();
  }
}
