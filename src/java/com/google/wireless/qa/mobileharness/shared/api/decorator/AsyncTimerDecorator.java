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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.job.TestInfo;
import java.util.Timer;
import java.util.TimerTask;

/** Decorator which runs timer task asynchronously during test running. */
public abstract class AsyncTimerDecorator extends AdapterDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public AsyncTimerDecorator(
      Driver decorated, com.google.wireless.qa.mobileharness.shared.model.job.TestInfo testInfo) {
    super(decorated, testInfo);
  }

  @Override
  public final void run(final TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Started");
    onStart(testInfo);
    final Timer timer =
        new Timer(String.format("timer-%s-%s", getClass().getSimpleName(), testInfo.getId()));
    long intervalMs = getIntervalMs(testInfo);
    Boolean fixedScheduleRate = getFixedScheduleRate(testInfo);

    TimerTask timerTask =
        new TimerTask() {
          @Override
          public void run() {
            try {
              runTimerTask(testInfo);
            } catch (MobileHarnessException e) {
              testInfo.addAndLogError(e, logger);
            } catch (InterruptedException e) {
              timer.cancel();
              Thread.currentThread().interrupt();
              logger.atWarning().log("%s", e.getMessage());
            }
          }
        };

    if (fixedScheduleRate) {
      timer.scheduleAtFixedRate(timerTask, 0, intervalMs);
    } else {
      timer.schedule(timerTask, 0, intervalMs);
    }

    try {
      // Starts the actual tests.
      getDecorated().run(testInfo);
    } finally {
      // Stops rotation after the test is finished.
      timer.cancel();
      timer.purge();
      onEnd(testInfo);
      logger.atInfo().log("Stopped");
    }
  }

  /** The interval time in milliseconds between two successive timer task executions. */
  @VisibleForTesting
  abstract long getIntervalMs(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException;

  /** Task periodically & asynchronously run during the test running. */
  @VisibleForTesting
  abstract void runTimerTask(TestInfo testInfo) throws MobileHarnessException, InterruptedException;

  /** Get the flag to indicate if schedule rate should be fixed */
  @VisibleForTesting
  Boolean getFixedScheduleRate(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    return false;
  }

  /** Executed when entering this decorator. */
  @VisibleForTesting
  @SuppressWarnings("unused")
  void onStart(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    // Does nothing.
  }

  /** Executed when finishing this decorator. */
  @VisibleForTesting
  @SuppressWarnings("unused")
  void onEnd(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    // Does nothing.
  }
}
