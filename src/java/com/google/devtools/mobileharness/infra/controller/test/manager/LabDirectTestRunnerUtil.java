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

import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.messaging.MessageDestinationNotFoundException;
import com.google.devtools.mobileharness.api.messaging.proto.MessagingProto.MessageSend;
import com.google.devtools.mobileharness.infra.container.controller.ProxyTestRunner;
import com.google.devtools.mobileharness.infra.container.controller.ProxyToDirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.messaging.MessageSender;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.shared.util.comm.messaging.poster.TestMessagePoster;
import java.util.Optional;

/**
 * Utilities for getting modules of {@link DirectTestRunner} from {@link TestManager} in lab server.
 */
public class LabDirectTestRunnerUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static Optional<TestMessagePoster> getTestMessagePoster(
      TestManagerImpl<ProxyTestRunner> testManager, String testId) {
    return getDirectTestRunner(testManager, testId, "test message poster")
        .map(DirectTestRunner::getTestMessagePoster);
  }

  public static MessageSender getMessageSender(
      TestManagerImpl<ProxyTestRunner> testManager, MessageSend messageSend)
      throws MessageDestinationNotFoundException {
    Optional<DirectTestRunner> directTestRunner =
        getDirectTestRunner(
            testManager, messageSend.getDestination().getTest().getRootTestId(), "message sender");
    if (directTestRunner.isPresent()) {
      return directTestRunner.get().getMessageSender();
    } else {
      throw new MessageDestinationNotFoundException(
          String.format(
              "Message destination is not found in lab, message_send=[%s]",
              shortDebugString(messageSend)));
    }
  }

  private static Optional<DirectTestRunner> getDirectTestRunner(
      TestManagerImpl<ProxyTestRunner> testManager, String testId, String moduleName) {
    return testManager
        .getTestRunner(testId)
        .filter(
            testRunner -> {
              if (!testRunner.isContainerMode()) {
                return true;
              } else {
                logger.atWarning().log(
                    "Failed to get %s of test [%s]" + " because the test is container mode",
                    moduleName, testId);
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
                    "Failed to get %s of test [%s]"
                        + " because the proxied direct test runner has not been connected",
                    moduleName, testId);
              }
              return proxiedDirectTestRunner;
            });
  }

  private LabDirectTestRunnerUtil() {}
}
