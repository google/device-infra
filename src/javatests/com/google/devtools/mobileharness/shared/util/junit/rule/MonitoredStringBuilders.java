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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.devtools.mobileharness.shared.util.junit.rule.util.FinishWithFailureTestWatcher;
import com.google.devtools.mobileharness.shared.util.junit.rule.util.StringsDebugger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.junit.runner.Description;

/**
 * Monitored {@link StringBuilder}s whose value will be automatically appended to the failure
 * details of a failed test, thereby simplifying the debugging process.
 */
public class MonitoredStringBuilders extends FinishWithFailureTestWatcher {

  private final Map<String, StringBuilder> stringBuilders = new ConcurrentHashMap<>();

  /**
   * Gets an existing or creates a new monitored {@link StringBuilder}, whose value will be appended
   * to the failure details of a test (if the test fails).
   */
  public StringBuilder getOrCreate(String label) {
    return stringBuilders.computeIfAbsent(label, newLabel -> new StringBuilder());
  }

  @Override
  protected void onFinished(@Nullable Throwable testFailure, Description description) {
    StringsDebugger.onTestFinished(
        testFailure,
        () ->
            stringBuilders.entrySet().stream()
                .collect(
                    toImmutableMap(
                        entry -> String.format("[%s]", entry.getKey()),
                        entry -> entry.getValue().toString())));
  }
}
