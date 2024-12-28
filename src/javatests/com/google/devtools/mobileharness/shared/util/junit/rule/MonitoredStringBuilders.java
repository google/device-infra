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

package com.google.devtools.mobileharness.shared.util.junit.rule;

import static java.util.stream.Collectors.joining;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * Monitored {@link StringBuilder}s whose value will be automatically appended to the failure
 * details of a failed test, thereby simplifying the debugging process.
 */
public class MonitoredStringBuilders extends TestWatcher {

  private final Map<String, StringBuilder> stringBuilders = new ConcurrentHashMap<>();

  /**
   * Gets an existing or creates a new monitored {@link StringBuilder}, whose value will be appended
   * to the failure details of a test (if the test fails).
   */
  public StringBuilder getOrCreate(String label) {
    return stringBuilders.computeIfAbsent(label, newLabel -> new StringBuilder());
  }

  @Override
  protected void failed(Throwable error, Description description) {
    Exception suppressed =
        new IllegalStateException(
            stringBuilders.entrySet().stream()
                .map(
                    e ->
                        String.format(
                            "\n"
                                + "==============================\n"
                                + "begin of [%s]\n"
                                + "==============================\n"
                                + "%s\n"
                                + "==============================\n"
                                + "end of [%s]\n"
                                + "==============================\n",
                            e.getKey(), e.getValue(), e.getKey()))
                .collect(joining()));
    suppressed.setStackTrace(new StackTraceElement[0]);
    error.addSuppressed(suppressed);
  }
}
