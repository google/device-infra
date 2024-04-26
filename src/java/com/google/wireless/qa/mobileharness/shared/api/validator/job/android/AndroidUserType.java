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

package com.google.wireless.qa.mobileharness.shared.api.validator.job.android;

import com.google.common.base.Ascii;
import com.google.errorprone.annotations.CheckReturnValue;

/** User type used by {@code AndroidSwitchUserDecorator}. */
public enum AndroidUserType {

  /** current foreground user of the device */
  CURRENT,
  /** user flagged as primary on the device; most often primary = system user = user 0 */
  PRIMARY,
  /** system user = user 0 */
  SYSTEM,
  /** secondary user, i.e. non-primary and non-system. */
  SECONDARY,
  /** guest user */
  GUEST;

  @CheckReturnValue
  public static AndroidUserType fromParam(String value) {
    return AndroidUserType.valueOf(Ascii.toUpperCase(value));
  }

  public boolean isGuest() {
    return this == GUEST;
  }
}
