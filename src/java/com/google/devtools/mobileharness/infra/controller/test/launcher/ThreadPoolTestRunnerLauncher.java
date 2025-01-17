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

package com.google.devtools.mobileharness.infra.controller.test.launcher;

import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;

import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.controller.test.TestRunner;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.event.TestExecutionEndedEvent;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionResult;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/** Thread pool test runner launcher which uses an {@link ExecutorService} to launch the test. */
public class ThreadPoolTestRunnerLauncher<T extends TestRunner> extends TestRunnerLauncher<T> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ExecutorService threadPool;
  @Nullable private final EventBus globalInternalEventBus;
  private final TestTask testTask = new TestTask();

  @GuardedBy("testTask")
  private volatile Future<Void> testFuture;

  public ThreadPoolTestRunnerLauncher(
      ExecutorService threadPool, @Nullable EventBus globalInternalEventBus) {
    this.threadPool = threadPool;
    this.globalInternalEventBus = globalInternalEventBus;
  }

  @Override
  protected void asyncLaunchTest() {
    synchronized (testTask) {
      testFuture =
          threadPool.submit(
              threadRenaming(testTask, () -> getTestRunner().getTestRunnerThreadName()),
              /* result= */ null);
    }
  }

  @Override
  @SuppressWarnings("Interruption")
  protected void killTest() {
    synchronized (testTask) {
      if (testFuture != null) {
        testFuture.cancel(/* mayInterruptIfRunning= */ true);
      }
    }
  }

  @Override
  protected boolean isTestRunning() {
    boolean isRunning = isTestExecuting();
    synchronized (testTask) {
      isRunning |= testFuture != null && !testFuture.isDone();
      if (!isRunning) {
        testFuture = null;
      }
    }
    return isRunning;
  }

  private void postTestExecutionEndedEvent(
      Allocation allocation, TestResult testResult, boolean needReboot) {
    if (globalInternalEventBus != null) {
      logger.atInfo().log(
          "%s",
          String.format(
              "Post TestExecutionEndedEvent, allocation=%s, result=%s, need_reboot=%s",
              allocation, testResult, needReboot));
      globalInternalEventBus.post(new TestExecutionEndedEvent(allocation, testResult, needReboot));
    }
  }

  private class TestTask implements Runnable {

    @Override
    public void run() {
      TestExecutionResult result =
          TestExecutionResult.create(TestResult.UNKNOWN, PostTestDeviceOp.REBOOT);
      try {
        result = executeTest();
      } catch (InterruptedException e) {
        logger.atWarning().withCause(e).log(
            "Test [%s] interrupted", getTestRunner().getTestExecutionUnit().locator().id());
      } finally {
        boolean needReboot = result.postTestDeviceOp().equals(PostTestDeviceOp.REBOOT);
        postTestExecutionEndedEvent(
            getTestRunner().getAllocation(), result.testResult(), needReboot);
      }
    }
  }
}
