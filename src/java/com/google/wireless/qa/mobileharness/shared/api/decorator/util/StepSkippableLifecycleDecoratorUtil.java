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

import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.Optional;

/**
 * Utility class for {@link
 * com.google.wireless.qa.mobileharness.shared.api.decorator.base.StepSkippableLifecycleDecorator}.
 */
public final class StepSkippableLifecycleDecoratorUtil {

  private static final String STATE_PREFIX = "step_skippable_lifecycle_decorator_state";
  private static final String KEY_SEPARATOR = "::";

  private StepSkippableLifecycleDecoratorUtil() {}

  /**
   * Saves state into JobInfo properties to be relayed (e.g. by session plugin) to a subsequent job.
   */
  public static void setState(
      JobInfo jobInfo, String deviceId, String className, String key, String value) {
    String namespacedKey = createNamespacedKey(deviceId, className, key);
    jobInfo.properties().add(namespacedKey, value);
  }

  /** Retrieves state that was saved previously (e.g. from a prior job). */
  public static Optional<String> getState(
      JobInfo jobInfo, String deviceId, String className, String key) {
    String namespacedKey = createNamespacedKey(deviceId, className, key);
    return jobInfo.properties().getOptional(namespacedKey);
  }

  private static String createNamespacedKey(String deviceId, String className, String key) {
    return String.join(KEY_SEPARATOR, STATE_PREFIX, deviceId, className, key);
  }

  /** Relays relevant states from {@code job1} to {@code job2}. */
  public static void relayStates(JobInfo job1, JobInfo job2) {
    job1.properties().getAll().entrySet().stream()
        .filter(entry -> entry.getKey().startsWith(STATE_PREFIX + KEY_SEPARATOR))
        .forEach(entry -> job2.properties().add(entry.getKey(), entry.getValue()));
  }
}
