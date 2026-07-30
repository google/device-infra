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

package com.google.wireless.qa.mobileharness.shared.api.decorator.util;

import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;

/**
 * Utility class for {@link
 * com.google.wireless.qa.mobileharness.shared.api.decorator.base.StepSkippableLifecycleDecorator}.
 *
 * <p>Note on multi-device test executions: When running a multi-device test (e.g., in {@code
 * AdhocTestbedDriver}), MobileHarness creates a separate child sub-test ({@code subTestInfo}) for
 * each device's decorator stack so that device-specific logs and generated files remain isolated.
 * Consequently, decorators in multi-device runs execute against child sub-test objects rather than
 * the root test.
 *
 * <p>To ensure decorator lifecycle states are consistently visible across all sub-devices and can
 * be reliably relayed between jobs in the session, all state read/write operations must be routed
 * to the root {@link TestInfo}'s property map via {@link #getRootTest(TestInfo)}.
 */
public final class StepSkippableLifecycleDecoratorUtil {

  private static final String STATE_PREFIX = "step_skippable_lifecycle_decorator_state";
  private static final String KEY_SEPARATOR = "::";

  private StepSkippableLifecycleDecoratorUtil() {}

  /**
   * Saves state into the root TestInfo properties to be relayed (e.g. by session plugin) to a
   * subsequent job's tests.
   */
  public static void setState(
      TestInfo testInfo, String deviceId, String className, String key, String value) {
    String namespacedKey = createNamespacedKey(deviceId, className, key);
    getRootTest(testInfo).properties().add(namespacedKey, value);
  }

  /** Retrieves state that was saved previously (e.g. from a prior job's test). */
  public static Optional<String> getState(
      TestInfo testInfo, String deviceId, String className, String key) {
    String namespacedKey = createNamespacedKey(deviceId, className, key);
    return getRootTest(testInfo).properties().getOptional(namespacedKey);
  }

  private static String createNamespacedKey(String deviceId, String className, String key) {
    return String.join(KEY_SEPARATOR, STATE_PREFIX, deviceId, className, key);
  }

  /** Relays relevant states from {@code test1} to {@code test2}. */
  public static void relayStates(TestInfo test1, TestInfo test2) {
    getRootTest(test1).properties().getAll().entrySet().stream()
        .filter(entry -> entry.getKey().startsWith(STATE_PREFIX + KEY_SEPARATOR))
        .forEach(entry -> getRootTest(test2).properties().add(entry.getKey(), entry.getValue()));
  }

  /**
   * Returns the root TestInfo of the given test to ensure properties are stored and retrieved at
   * the root level even when decorators execute inside child sub-tests (such as in {@code
   * AdhocTestbedDriver} multi-device runs). When {@code testInfo} is already the root test, this
   * returns itself.
   */
  private static TestInfo getRootTest(TestInfo testInfo) {
    TestInfo root = testInfo.getRootTest();
    return root != null ? root : testInfo;
  }
}
