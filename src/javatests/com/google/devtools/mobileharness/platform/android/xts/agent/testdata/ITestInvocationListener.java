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

package com.android.tradefed.result;

public interface ITestInvocationListener {
  default void testModuleStarted(Object moduleContext) {}

  default void testModuleEnded() {}

  default void testRunStarted(String runName, int testCount) {}

  default void testRunEnded(long elapsedTime, Object runMetrics) {}

  default void testStarted(Object test) {}

  default void testEnded(Object test, Object testMetrics) {}

  default void testFailed(Object test, String trace) {}

  default void testAssumptionFailure(Object test, String trace) {}

  default void testIgnored(Object test) {}

  default void testSkipped(Object test, Object skipReason) {}
}
