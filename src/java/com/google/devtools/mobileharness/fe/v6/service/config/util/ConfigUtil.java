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

package com.google.devtools.mobileharness.fe.v6.service.config.util;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;

/** Utility for configuration checks. */
public final class ConfigUtil {

  public static final String DEVICE_DEFAULT_OWNER = "mobileharness-device-default-owner";

  private ConfigUtil() {}

  /** Checks whether the owner list is empty or only contains the default owner. */
  public static boolean isOwnerEmptyOrDefault(Collection<String> owners) {
    ImmutableSet<String> uniqueOwners = ImmutableSet.copyOf(owners);
    return uniqueOwners.isEmpty()
        || (uniqueOwners.size() == 1 && uniqueOwners.contains(DEVICE_DEFAULT_OWNER));
  }

  /**
   * Checks whether the exception or any of its causes or suppressed exceptions indicates an invalid
   * user error (e.g. Cloud IAM policy failure for an unknown user ID).
   */
  public static boolean isInvalidUserError(Throwable t) {
    Throwable current = t;
    while (current != null) {
      String msg = current.getMessage();
      if (msg != null) {
        if (msg.contains("IAM_SET_DEVICE_POLICY_ERROR")
            || msg.contains("IAM_SET_LAB_DEFAULT_DEVICE_POLICY_ERROR")) {
          return true;
        }
      }
      for (Throwable suppressed : current.getSuppressed()) {
        if (isInvalidUserError(suppressed)) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }
}
