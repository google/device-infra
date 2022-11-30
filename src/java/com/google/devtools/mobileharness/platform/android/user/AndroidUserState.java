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

import java.util.HashMap;
import java.util.Map;

/**
 * User State defined in: //frameworks/base/services/core/java/com/android/server/am/UserState.java
 */
public enum AndroidUserState {
  /** User is first coming up. */
  STATE_BOOTING("BOOTING"),
  /** User is in the locked state. */
  STATE_RUNNING_LOCKED("RUNNING_LOCKED"),
  /** User is in the unlocking state. */
  STATE_RUNNING_UNLOCKING("RUNNING_UNLOCKING"),
  /** User is in the running state. */
  STATE_RUNNING_UNLOCKED("RUNNING_UNLOCKED"),
  /** User is in the initial process of being stopped. */
  STATE_STOPPING("STOPPING"),
  /** User is in the final phase of stopping, sending Intent.ACTION_SHUTDOWN. */
  STATE_SHUTDOWN("SHUTDOWN"),
  /** Unknown state. */
  STATE_UNKNOWN("UNKNOWN");

  private final String userState;

  private static final Map<String, AndroidUserState> strToEnum = new HashMap<>();

  static {
    for (AndroidUserState androidUserState : AndroidUserState.values()) {
      strToEnum.put(androidUserState.getUserState(), androidUserState);
    }
  }

  private AndroidUserState(String userState) {
    this.userState = userState;
  }

  public String getUserState() {
    return userState;
  }

  public static AndroidUserState enumOf(String userStateStr) {
    AndroidUserState result = strToEnum.get(userStateStr);
    return result == null ? AndroidUserState.STATE_UNKNOWN : result;
  }
}
