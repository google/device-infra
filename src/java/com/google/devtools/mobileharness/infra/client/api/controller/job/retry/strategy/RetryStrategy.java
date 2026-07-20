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

package com.google.devtools.mobileharness.infra.client.api.controller.job.retry.strategy;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Map;
import java.util.Optional;

/** Strategy deciding whether a finished test should be retried. */
public interface RetryStrategy {

  /**
   * Struct containing details of a retry decision.
   *
   * @param retryReason The reason for the retry. If empty, the test will not be retried. Otherwise,
   *     the framework will create a new test attempt with the given reason. not including the
   *     retry.
   * @param newTestProperties Properties to be added to the new test attempt if a retry is made.
   */
  public record RetryInfo(Optional<String> retryReason, Map<String, String> newTestProperties) {}

  public static final RetryInfo NO_RETRY = new RetryInfo(Optional.empty(), ImmutableMap.of());

  /** Decide whether the test should be retried. */
  RetryInfo decideRetryOnTestEnd(TestInfo currentTestInfo)
      throws MobileHarnessException, InterruptedException;
}
