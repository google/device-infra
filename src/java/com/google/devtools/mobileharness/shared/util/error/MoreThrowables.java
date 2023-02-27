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

package com.google.devtools.mobileharness.shared.util.error;

/** More utilities for {@link Throwable}. */
public class MoreThrowables {

  /**
   * Returns a string containing {@link Throwable#getMessage()} of the given exception and all of
   * its causes (if any). The delimiter string is "{@code , cause: }". For example, "{@code
   * message_of_e, cause: message_of_cause_of_e, cause: message_of_cause_of_cause_of_e}".
   *
   * <p>Note that the purpose of this method is "printing the content of an exception but making the
   * result string NOT be like an exception", for the cases in which you don't want the "\tat
   * xxx.xxx.Xxx(Xxx.java:xxx)" in log confuse users that here is the root cause. The result string
   * of this method may have bad readability. In most cases, you should use {@code
   * Throwables.getStackTraceAsString(e)} instead.
   *
   * @param maxLength the max length of the printed message chain. For example, 2 means printing the
   *     message of the exception and the message of the cause of the exception (if any). 0 or
   *     negative for "no max length" (print until the last cause)
   */
  public static String shortDebugString(Throwable e, int maxLength) {
    StringBuilder result = new StringBuilder();
    boolean checkLength = maxLength > 0;
    while (true) {
      result.append(e.getMessage());
      if (checkLength) {
        maxLength--;
        if (maxLength == 0) {
          break;
        }
      }
      e = e.getCause();
      if (e == null) {
        break;
      }
      result.append(", cause: ");
    }
    return result.toString();
  }

  private MoreThrowables() {}
}
