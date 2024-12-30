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

package com.google.devtools.mobileharness.shared.util.junit.rule.util;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableMap;
import javax.annotation.Nullable;

public class StringsDebugger {

  public static void onTestFinished(
      @Nullable Throwable testFailure, ImmutableMap<String, String> strings) {
    if (testFailure != null) {
      Exception exception =
          new IllegalStateException(
              strings.entrySet().stream()
                  .map(
                      entry ->
                          String.format(
                              "\n"
                                  + "==============================\n"
                                  + "begin of %s\n"
                                  + "==============================\n"
                                  + "%s\n"
                                  + "==============================\n"
                                  + "end of %s\n"
                                  + "==============================\n",
                              entry.getKey(), entry.getValue(), entry.getKey()))
                  .collect(joining()));
      exception.setStackTrace(new StackTraceElement[0]);
      testFailure.addSuppressed(exception);
    }
  }

  private StringsDebugger() {}
}
