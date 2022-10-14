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

package com.google.devtools.mobileharness.shared.util.command;

/**
 * Checked exception thrown when fails to handle a command output line in {@link LineCallback}.
 *
 * @see LineCallback
 */
public class LineCallbackException
    extends com.google.devtools.deviceinfra.shared.util.command.LineCallbackException {

  /**
   * Constructor.
   *
   * @param killCommand whether to kill the command
   * @param stopReadingOutput whether to stop reading the following output from stdout/stderr
   */
  public LineCallbackException(
      String message, Throwable cause, boolean killCommand, boolean stopReadingOutput) {
    super(message, cause, killCommand, stopReadingOutput);
  }
}
