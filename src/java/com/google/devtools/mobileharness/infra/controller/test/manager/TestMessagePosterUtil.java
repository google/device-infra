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

package com.google.devtools.mobileharness.infra.controller.test.manager;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.container.controller.ProxyTestRunner;
import com.google.devtools.mobileharness.infra.container.controller.ProxyToDirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.shared.util.comm.messaging.poster.TestMessagePoster;
import java.util.Optional;

/** Utilities for getting {@link TestMessagePoster} from {@link TestManager}. */
public class TestMessagePosterUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static Optional<TestMessagePoster> getPosterFromDirectTestManager(
      TestManager<DirectTestRunner> directTestManager, String testId) {
    return directTestManager.getTestRunner(testId).map(DirectTestRunner::getTestMessagePoster);
  }

  public static Optional<TestMessagePoster> getPosterFromProxyTestManager(
      TestManager<ProxyTestRunner> testManager, String testId) {
    return testManager
        .getTestRunner(testId)
        .filter(
            testRunner -> {
              if (!testRunner.isContainerMode()) {
                return true;
              } else {
                logger.atWarning().log(
                    "Failed to get test message poster of test [%s]"
                        + " because the test is container mode",
                    testId);
                return false;
              }
            })
        .map(testRunner -> (ProxyToDirectTestRunner) testRunner)
        .flatMap(
            testRunner -> {
              Optional<DirectTestRunner> proxiedDirectTestRunner =
                  testRunner.getProxiedDirectTestRunner();
              if (proxiedDirectTestRunner.isEmpty()) {
                logger.atWarning().log(
                    "Failed to get test message poster of test [%s]"
                        + " because the proxied direct test runner has not been connected",
                    testId);
              }
              return proxiedDirectTestRunner;
            })
        .map(DirectTestRunner::getTestMessagePoster);
  }

  private TestMessagePosterUtil() {}
}
