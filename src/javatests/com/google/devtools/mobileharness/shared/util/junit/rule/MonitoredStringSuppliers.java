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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.devtools.mobileharness.shared.util.junit.rule.util.FinishWithFailureTestWatcher;
import com.google.devtools.mobileharness.shared.util.junit.rule.util.StringsDebugger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.junit.runner.Description;

/**
 * Monitored {@linkplain Supplier string suppliers} whose supplied strings will be automatically
 * appended to the failure details of a failed test, thereby simplifying the debugging process.
 */
public class MonitoredStringSuppliers extends FinishWithFailureTestWatcher {

  private final Map<String, Supplier<String>> stringSuppliers = new ConcurrentHashMap<>();

  /**
   * Adds a new {@linkplain Supplier string supplier} to monitor, whose supplied string will be
   * appended to the failure details of a test (if the test fails).
   *
   * @throws NullPointerException if the label or the string supplier is null
   * @throws IllegalStateException if the label has already been added
   */
  public void add(String label, Supplier<String> stringSupplier) {
    Supplier<String> oldValue = stringSuppliers.putIfAbsent(label, stringSupplier);
    checkState(oldValue == null);
  }

  @Override
  protected void onFinished(@Nullable Throwable testFailure, Description description) {
    StringsDebugger.onTestFinished(
        testFailure,
        () ->
            stringSuppliers.entrySet().stream()
                .collect(
                    toImmutableMap(
                        entry -> String.format("[%s]", entry.getKey()),
                        entry -> {
                          try {
                            return nullToEmpty(entry.getValue().get());
                          } catch (RuntimeException | Error e) {
                            return getStackTraceAsString(e);
                          }
                        })));
  }
}
