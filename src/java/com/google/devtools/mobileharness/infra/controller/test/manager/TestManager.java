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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.flogger.LazyArgs.lazy;
import static java.util.Arrays.stream;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.infra.controller.test.TestRunner;
import com.google.devtools.mobileharness.infra.controller.test.model.JobExecutionUnit;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionUnit;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.concurrent.GuardedBy;

/**
 * Test manager which manages all the running tests. It can start and kill a test.
 *
 * @param <T> the type of test runner the manager manages
 */
public class TestManager<T extends TestRunner> implements Runnable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Interval of checking the current active tests. */
  private static final Duration CHECK_TEST_INTERVAL = Duration.ofSeconds(2);

  /** Interval of zombie test alert. */
  private static final Duration ZOMBIE_TEST_ALERT_INTERVAL = Duration.ofMinutes(1);

  /**
   * If the time of killing a test is equal or greater than this number, kills related processes.
   */
  private static final int MAX_KILL_COUNT = 30;

  /** The last time zombie test alert in milliseconds. */
  private volatile Instant lastZombieTestAlertTime =
      Instant.now().minus(ZOMBIE_TEST_ALERT_INTERVAL);

  /** {TestId, TestRunner} mapping of the running tests. */
  @GuardedBy("itself")
  private final Map<String, T> testRunners = new HashMap<>();

  private final SystemUtil systemUtil;
  private final Sleeper sleeper;

  public TestManager() {
    this(new SystemUtil(), Sleeper.defaultSleeper());
  }

  @VisibleForTesting
  TestManager(SystemUtil systemUtil, Sleeper sleeper) {
    this.systemUtil = systemUtil;
    this.sleeper = sleeper;
  }

  /**
   * Starts a test runner to execute a test.
   *
   * @throws TestStartedException if the test has already started
   * @throws MobileHarnessException if the test fails to start
   */
  public void startTest(T testRunner) throws MobileHarnessException {
    String testId = testRunner.getTestExecutionUnit().locator().id();
    synchronized (testRunners) {
      TestRunner runner = testRunners.get(testId);
      if (runner != null) {
        throw new TestStartedException(String.format("Test %s is already running", testId));
      }
      testRunner.start();
      addTestRunner(testId, testRunner);
    }
    logger.atInfo().log("Start test %s", testId);
  }

  /** Kills a test if the test exists and is running and removes it from test manager. */
  public void killAndRemoveTest(String testId) {
    synchronized (testRunners) {
      TestRunner runner = testRunners.get(testId);
      if (runner != null) {
        if (runner.isRunning()) {
          runner.kill(/* timeout= */ false);
          logger.atInfo().log("Kill test %s", testId);
        } else {
          testRunners.remove(testId);
          logger.atInfo().log("Test %s has already stopped", testId);
        }
      } else {
        logger.atInfo().log("Test %s not found", testId);
      }
    }
  }

  /** Checks whether there is any {@link TestRunner} running. */
  public boolean isAnyTestRunning() {
    synchronized (testRunners) {
      for (TestRunner runner : testRunners.values()) {
        if (runner.isRunning()) {
          return true;
        }
      }
      return false;
    }
  }

  // TODO: Remove this method.
  /** Gets running test ids of the given job. */
  public List<String> getRunningTestIds() {
    synchronized (testRunners) {
      return testRunners.values().stream()
          .filter(testRunner -> testRunner.isRunning())
          .map(testRunner -> testRunner.getTestExecutionUnit().locator().id())
          .collect(Collectors.toList());
    }
  }

  /** Gets all tests of the given job. */
  public ImmutableList<String> getAllTests(String jobId) {
    synchronized (testRunners) {
      ImmutableList<TestLocator> testLocators =
          testRunners.values().stream()
              .map(testRunner -> testRunner.getTestExecutionUnit().locator())
              .collect(toImmutableList());
      logger.atInfo().log("All tests: %s", testLocators);
      return testLocators.stream()
          .filter(testLocator -> jobId.equals(testLocator.jobLocator().id()))
          .map(TestLocator::id)
          .collect(toImmutableList());
    }
  }

  /** Checks whether the test of this allocation is already running. */
  public boolean isTestRunning(Allocation allocation) throws MobileHarnessException {
    String testId = allocation.getTest().id();
    TestRunner testRunner;
    synchronized (testRunners) {
      testRunner = testRunners.get(testId);
    }
    if (testRunner == null || !testRunner.isRunning()) {
      return false;
    } else {
      Function<Allocation, List<String>> deviceIdGetter =
          alloc ->
              alloc.getAllDevices().stream()
                  .map(com.google.devtools.mobileharness.api.model.lab.DeviceLocator::id)
                  .collect(Collectors.toList());
      if (!deviceIdGetter
          .apply(testRunner.getAllocation())
          .equals(deviceIdGetter.apply(allocation))) {
        throw new MobileHarnessException(
            ErrorCode.TEST_DUPLICATED_ALLOCATION,
            String.format(
                "Test %s already has allocation %s. The allocation %s is illegal.",
                testId, testRunner.getAllocation(), allocation));
      }
      return true;
    }
  }

  @Override
  public void run() {
    logger.atInfo().log("Started");
    int lastHashCode = 0;
    while (!Thread.currentThread().isInterrupted()) {
      try {
        sleeper.sleep(CHECK_TEST_INTERVAL);
        ListMultimap<JobExecutionUnit, ZombieTestInfo> zombieTests = LinkedListMultimap.create();
        synchronized (testRunners) {
          List<String> stoppedAndClosedTestIds = new ArrayList<>();

          // Checks expired tests and tries to kill them.
          for (TestRunner testRunner : testRunners.values()) {
            TestExecutionUnit testExecutionUnit = testRunner.getTestExecutionUnit();
            String testId = testExecutionUnit.locator().id();
            boolean isTestExpired = testExecutionUnit.timer().isExpired();
            int killCount = 0;
            if (isTestExpired) {
              killCount = killTimeoutTestRunner(testRunner);
            }
            boolean isTestRunning = testRunner.isRunning();
            if (!isTestRunning && testRunner.isClosed()) {
              stoppedAndClosedTestIds.add(testId);
            }
            if (isTestExpired && isTestRunning && killCount >= MAX_KILL_COUNT) {
              zombieTests.put(
                  testExecutionUnit.job(), ZombieTestInfo.create(testRunner, killCount));
            }
          }

          // Removes stopped and closed tests.
          // Can not used forEach here because @GuardedBy.
          for (String testId : stoppedAndClosedTestIds) {
            logger.atInfo().log("Remove stopped test: %s", testId);
            testRunners.remove(testId);
          }

          // Prints info of the running tests.
          if (!testRunners.isEmpty() && testRunners.hashCode() != lastHashCode) {
            logger.atInfo().log(
                "(%d) Test Ids: %s",
                testRunners.size(), Joiner.on(", ").join(testRunners.keySet()));
            lastHashCode = testRunners.hashCode();
          }
        }

        alertZombieTests(zombieTests);
      } catch (InterruptedException e) {
        logger.atWarning().log(
            "Interrupted %s", Strings.isNullOrEmpty(e.getMessage()) ? "" : e.getMessage());
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        // Catches all exception to keep this TestManager thread running.
        logger.atSevere().withCause(e).log("FATAL ERROR");
      }
    }
    logger.atInfo().log("Stopped!");
  }

  /**
   * Do NOT make it public. Test runner should be managed only by test manager and related util
   * classes in the same package.
   */
  Optional<T> getTestRunner(String testId) {
    synchronized (testRunners) {
      return Optional.ofNullable(testRunners.get(testId));
    }
  }

  /**
   * Do NOT make it public. Test runner should be managed only by test manager and related util
   * classes in the same package.
   */
  T getTestRunnerNonEmpty(String testId)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    return getTestRunner(testId)
        .orElseThrow(
            () ->
                new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                    InfraErrorId.TM_TEST_NOT_FOUND,
                    String.format("Test [%s] is not found", testId)));
  }

  @VisibleForTesting
  void addTestRunner(String testId, T testRunner) {
    logger.atInfo().log("Add test runner to test manager: %s", testId);
    synchronized (testRunners) {
      testRunners.put(testId, testRunner);
    }
  }

  @VisibleForTesting
  void logAllStackTraces() {
    logger.atWarning().atMostEvery(5, MINUTES).log("%s", lazy(TestManager::formatAllStackTraces));
  }

  private static String formatAllStackTraces() {
    StringBuilder content = new StringBuilder("Current stack traces:\n");
    for (Map.Entry<Thread, StackTraceElement[]> threadAndStack :
        Thread.getAllStackTraces().entrySet()) {
      content.append("Thread: ").append(threadAndStack.getKey()).append("\n");
      stream(threadAndStack.getValue())
          .forEach(
              stackTraceElement -> content.append("\tat ").append(stackTraceElement).append('\n'));
    }
    return content.toString();
  }

  private int killTimeoutTestRunner(TestRunner runner) throws InterruptedException {
    if (runner.isRunning()) {
      int killCount = runner.kill(/* timeout= */ true);
      TestLocator testLocator = runner.getTestExecutionUnit().locator();
      logger.atInfo().log("Kill test %s (kill_count=%d)", testLocator.id(), killCount);
      if (killCount >= MAX_KILL_COUNT) {
        logAllStackTraces();
        List<String> deviceIds =
            runner.getAllocation().getAllDevices().stream()
                .map(com.google.devtools.mobileharness.api.model.lab.DeviceLocator::id)
                .collect(Collectors.toList());
        killZombieProcesses(testLocator, deviceIds);
      }
      return killCount;
    } else {
      return 0;
    }
  }

  /** Kills the related processes of the given zombie runner. */
  @VisibleForTesting
  void killZombieProcesses(TestLocator testLocator, List<String> deviceIds)
      throws InterruptedException {
    try {
      // Checks the zombie processes.
      Set<Integer> processIds =
          new HashSet<>(systemUtil.getProcessIds(testLocator.jobLocator().id(), testLocator.id()));
      for (String deviceId : deviceIds) {
        processIds.addAll(systemUtil.getProcessIds(deviceId));
      }
      if (processIds.isEmpty()) {
        return;
      }
      logger.atWarning().log(
          "Found %d zombie process(es) related to test %s on device(s) %s: %s",
          processIds.size(), testLocator, deviceIds, processIds);

      // Kills the zombie processes.
      for (Integer processId : processIds) {
        try {
          logger.atWarning().log(
              "Kill zombie process %d:%n%s", processId, systemUtil.getProcessInfo(processId));
          systemUtil.killProcess(processId);
        } catch (MobileHarnessException e) {
          logger.atWarning().log(
              "Failed to kill process %s with exception: %s", processId, e.getMessage());
        }
      }
      // Check the killed processes.
      Set<Integer> killedProcessIds = new HashSet<>(processIds);
      Set<Integer> remainingProcessIds =
          new HashSet<>(systemUtil.getProcessIds(testLocator.jobLocator().id(), testLocator.id()));
      for (String deviceId : deviceIds) {
        remainingProcessIds.addAll(systemUtil.getProcessIds(deviceId));
      }
      killedProcessIds.removeAll(remainingProcessIds);

      logger.atWarning().log(
          "Killed %d zombie process(es) related to test %s: %s",
          killedProcessIds.size(), testLocator, killedProcessIds);
      for (Integer processId : remainingProcessIds) {
        logger.atWarning().log(
            "Remaining process %d on device(s) %s:\n%s",
            processId, deviceIds, systemUtil.getProcessInfo(processId));
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to check or kill the zombie processes");
    }
  }

  /** Information of a Zombie test. */
  @AutoValue
  abstract static class ZombieTestInfo {
    private static ZombieTestInfo create(TestRunner testRunner, int killCount) {
      return new AutoValue_TestManager_ZombieTestInfo(testRunner, killCount);
    }

    abstract TestRunner testRunner();

    abstract int killCount();
  }

  // Only logs zombie tests summary without sending emails.
  private void alertZombieTests(Multimap<JobExecutionUnit, ZombieTestInfo> allZombieTests) {
    if (!allZombieTests.isEmpty()
        && lastZombieTestAlertTime
            .plus(ZOMBIE_TEST_ALERT_INTERVAL)
            .isBefore(Clock.systemUTC().instant())) {

      StringBuilder content = new StringBuilder();
      content.append(allZombieTests.size()).append(" Zombie Tests in Total");

      for (JobExecutionUnit job : allZombieTests.keySet()) {
        Collection<ZombieTestInfo> zombieTests = allZombieTests.get(job);
        content
            .append("\n=========================================")
            .append("\nJob Id: ")
            .append(job.locator().id())
            .append("\nJob Name: ")
            .append(job.locator().name())
            .append("\nJob Driver: ")
            .append(job.driver())
            .append("\nJob Create Time: ")
            .append(TimeUtils.toDateString(job.timing().getCreateTime()))
            .append("\nJob Timeout: ")
            .append(job.timeout().jobTimeout())
            .append("ms")
            .append("\nTest Timeout: ")
            .append(job.timeout().testTimeout())
            .append("ms")
            .append("\nZombie Tests: ")
            .append(zombieTests.size());

        for (ZombieTestInfo test : zombieTests) {
          TestRunner testRunner = test.testRunner();
          TestExecutionUnit testExecutionUnit = testRunner.getTestExecutionUnit();
          content
              .append("\n-----------------------------------------")
              .append("\nTest Id: ")
              .append(testExecutionUnit.locator().id())
              .append("\nTest Name: ")
              .append(testExecutionUnit.locator().name())
              .append("\nTest Start Time: ")
              .append(
                  TimeUtils.toDateString(
                      testExecutionUnit.timing().getStartTime().orElse(Instant.EPOCH)))
              .append("\nDevice(s): ")
              .append(testRunner.getAllocation().getAllDevices())
              .append("\nKill Count: ")
              .append(test.killCount());
        }
      }
      content
          .append("\n=========================================\n")
          .append(TimeUtils.toDateString(Clock.systemUTC().instant()));

      logger.atInfo().log("Zombie tests summary:\n%s", content);
      lastZombieTestAlertTime = Instant.now();
    }
  }
}
