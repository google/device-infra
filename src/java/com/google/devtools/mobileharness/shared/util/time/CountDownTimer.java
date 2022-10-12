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

package com.google.devtools.mobileharness.shared.util.time;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.time.Duration;
import java.time.Instant;

/** Count down timer for counting down for specified time. */
public interface CountDownTimer {

  /**
   * Returns the expire time of the timer.
   *
   * @throws MobileHarnessException if the timer hasn't started
   */
  Instant expireTime() throws MobileHarnessException;

  /** Whether the timer is expired. */
  boolean isExpired();

  /**
   * Returns the remaining time before the timer is expired. The return value is positive.
   *
   * @throws MobileHarnessException if the timer is not started, or has expired
   */
  Duration remainingTimeJava() throws MobileHarnessException;
}
