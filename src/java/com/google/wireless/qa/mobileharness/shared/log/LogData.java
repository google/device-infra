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

package com.google.wireless.qa.mobileharness.shared.log;

import java.util.Optional;
import java.util.logging.Level;

/** A backend API for determining metadata associated with a log. */
public interface LogData {

  /** Gets the level of the log. */
  Level getLevel();

  /** Gets the cause of the log. */
  Optional<Throwable> getCause();

  /**
   * @return whether the stack trace of the cause should be logged if any.
   */
  boolean getWithCauseStack();

  /** Gets the message of the log. */
  Optional<String> getMessage();

  /** Gets the arguments of the message of the log. */
  Optional<Object[]> getArgs();

  /** Gets the formatted message of the log which should not be {@code null}. */
  String getFormattedMessage();
}
