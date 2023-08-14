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

package com.google.wireless.qa.mobileharness.shared.comm.message;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.comm.messaging.message.TestMessageInfo;
import com.google.devtools.mobileharness.shared.util.comm.messaging.poster.TestMessagePoster;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.Optional;
import java.util.function.Function;

/**
 * The test message manager for test message mechanism.
 *
 * <p>It is the internal implementation of {@linkplain
 * com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil TestMessageUtil}.
 */
public class TestMessageManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @GuardedBy("CONSTRUCTING_LOCK")
  private static volatile TestMessageManager instance;

  /** The lock for creating singleton instance. */
  private static final Object CONSTRUCTING_LOCK = new Object();

  /**
   * Creates the singleton instance.
   *
   * @param testMessagePosterGetter the test message poster getter
   * @throws NullPointerException if the test message poster getter is {@code null}
   * @throws IllegalStateException if the test message manager has already been created
   */
  public static void createInstance(
      Function<String, Optional<TestMessagePoster>> testMessagePosterGetter) {
    Preconditions.checkNotNull(testMessagePosterGetter);
    synchronized (CONSTRUCTING_LOCK) {
      Preconditions.checkState(instance == null, "Test message manager has already been created");
      instance = new TestMessageManager(testMessagePosterGetter);
    }
  }

  /**
   * Gets the singleton instance.
   *
   * @return the singleton instance
   * @throws IllegalStateException if the test message manager has not been created
   */
  public static TestMessageManager getInstance() {
    synchronized (CONSTRUCTING_LOCK) {
      Preconditions.checkState(
          instance != null,
          "Test message manager has not been created. Perhaps Mobile Harness client or lab "
              + "server has not been created yet?");
      return instance;
    }
  }

  /** The test message poster getter. */
  private final Function<String, Optional<TestMessagePoster>> testMessagePosterGetter;

  /**
   * The constructor.
   *
   * @param testMessagePosterGetter the test message poster getter
   */
  @VisibleForTesting
  TestMessageManager(Function<String, Optional<TestMessagePoster>> testMessagePosterGetter) {
    this.testMessagePosterGetter = testMessagePosterGetter;
  }

  public void sendMessageToTest(TestMessageInfo testMessageInfo) throws MobileHarnessException {
    TestMessagePoster testMessagePoster =
        getTestMessagePosterByTestId(testMessageInfo.rootTestId())
            .orElseThrow(
                () ->
                    new MobileHarnessException(
                        InfraErrorId.TR_SEND_TEST_MESSAGE_TEST_NOT_FOUND,
                        String.format(
                            "[%s] is not a test ID of a running MH top-level test so message %s"
                                + " can't be posted to it. Is it a sub-test ID or had the test"
                                + " not started or already ended when sending the message?",
                            testMessageInfo.rootTestId(), testMessageInfo.message())));
    sendMessageToTest(testMessagePoster, testMessageInfo);
  }

  public void sendMessageToTest(
      TestMessagePoster testMessagePoster, TestMessageInfo testMessageInfo)
      throws MobileHarnessException {
    logger.atFine().log(
        "Send message to root test %s: %s", testMessagePoster.getTestId(), testMessageInfo);
    testMessagePoster.postTestMessage(testMessageInfo);
  }

  /**
   * Gets the test message poster by the test id.
   *
   * @param testId the test id
   * @return the test message poster
   */
  private Optional<TestMessagePoster> getTestMessagePosterByTestId(String testId) {
    return testMessagePosterGetter.apply(testId);
  }
}
