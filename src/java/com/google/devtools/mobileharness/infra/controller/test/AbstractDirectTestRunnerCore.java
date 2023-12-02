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

package com.google.devtools.mobileharness.infra.controller.test;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.api.model.proto.Test;
import com.google.devtools.mobileharness.infra.controller.test.PluginLoadingResult.PluginItem;
import com.google.devtools.mobileharness.infra.controller.test.TestContext.WithTestContext;
import com.google.devtools.mobileharness.infra.controller.test.exception.TestRunnerLauncherConnectedException;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionResult;
import com.google.devtools.mobileharness.infra.controller.test.util.SubscriberExceptionLoggingHandler;
import com.google.devtools.mobileharness.shared.constant.closeable.MobileHarnessAutoCloseable;
import com.google.devtools.mobileharness.shared.util.comm.messaging.message.TestMessageInfo;
import com.google.devtools.mobileharness.shared.util.comm.messaging.poster.TestMessagePoster;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.devtools.mobileharness.shared.util.event.EventBus.SubscriberExceptionContext;
import com.google.devtools.mobileharness.shared.util.logging.MobileHarnessLogTag;
import com.google.devtools.mobileharness.shared.util.sharedpool.SharedPoolJobUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.comm.message.CacheableTestMessageHandler;
import com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.ScopedEventBus;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.SkipInformationHandler;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.SkipInformationHandler.SkipInformation;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.SkipInformationHandler.SkipResultWithCause;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.model.job.util.ResultUtil;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.util.DeviceInfoUtil;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

/**
 * For executing a single test with multiple devices.
 *
 * <ul>
 *   <li>trace.* that may cause the library to crash.
 * </ul>
 */
public abstract class AbstractDirectTestRunnerCore<T extends AbstractDirectTestRunnerCore<T>>
    extends AbstractTestRunner<T> implements DirectTestRunner {

  /** Logger for this instance. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DIMENSION_POOL =
      PropertyName.Test.PREFIX_DIMENSION + Ascii.toLowerCase(Dimension.Name.POOL.name());

  /**
   * Multiple event buses to handle events in different scopes with different handlers.
   *
   * <p>Do <b>*NOT*</b> add its visibility or add accessor for it.
   */
  private final ScopedEventBus<EventScope> scopedEventBus;

  /** Do <b>*NOT*</b> add its visibility or add accessor for it. */
  private final SubscriberExceptionLoggingHandler internalPluginExceptionHandler;

  /** Do <b>*NOT*</b> add its visibility or add accessor for it. */
  private final SubscriberExceptionLoggingHandler apiPluginExceptionHandler;

  /** Do <b>*NOT*</b> add its visibility or add accessor for it. */
  private final SubscriberExceptionLoggingHandler jarPluginExceptionHandler;

  /**
   * The device allocation for this test, which might be reset in {@link #updateDeviceStatus}
   * according to updated {@link #deviceInfos}.
   */
  private volatile Allocation allocation;

  /** The test to be executed. */
  private final TestInfo testInfo;

  /** The test message poster of the test. */
  private final CacheableTestMessagePoster testMessagePoster;

  /** Set by {@link #updateDeviceStatus}. */
  private volatile ImmutableList<DeviceFeature> deviceFeatures;

  /** Set by {@link #updateDeviceStatus}. */
  private volatile List<DeviceInfo> deviceInfos;

  /**
   * Plugins items loaded when the test runner is initialized (passed from outside) rather than
   * loaded in the pre_run_test phase.
   */
  protected final ImmutableList<PluginItem<?>> initialPluginItems;

  protected AbstractDirectTestRunnerCore(
      TestRunnerLauncher<? super T> launcher, DirectTestRunnerSetting setting)
      throws TestRunnerLauncherConnectedException {
    this(
        launcher,
        setting,
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread thread =
                  new Thread(
                      runnable,
                      "cacheable-test-message-poster-" + setting.testInfo().locator().getId());
              thread.setDaemon(true);
              return thread;
            }));
  }

  @VisibleForTesting
  AbstractDirectTestRunnerCore(
      TestRunnerLauncher<? super T> launcher,
      DirectTestRunnerSetting setting,
      ExecutorService testMessagePosterExecutorService)
      throws TestRunnerLauncherConnectedException {
    super(
        launcher, setting.testInfo().toTestExecutionUnit(), setting.allocation().toNewAllocation());
    testInfo = setting.testInfo();
    allocation = setting.allocation();

    // In multi-device tests we need to know which actual device was allocated for each
    // SubDeviceSpec in the job. This allows decorators to retrieve parameters associated with each
    // particular device/decorator stack.
    testInfo.jobInfo().subDeviceSpecs().setAllocation(allocation);

    ImmutableList.Builder<PluginItem<?>> initialPluginItemsBuilder = new ImmutableList.Builder<>();

    scopedEventBus = new ScopedEventBus<>(EventScope.class);
    scopedEventBus.add(EventScope.CLASS_INTERNAL);
    scopedEventBus.add(EventScope.GLOBAL_INTERNAL, setting.globalInternalBus().orElse(null));

    internalPluginExceptionHandler =
        new SubscriberExceptionLoggingHandler(/* saveException= */ true, /* isUserPlugin= */ false);
    scopedEventBus.add(EventScope.INTERNAL_PLUGIN, new EventBus(internalPluginExceptionHandler));
    setting
        .internalPluginSubscribers()
        .ifPresent(
            subscribers ->
                subscribers.forEach(
                    subscriber -> {
                      scopedEventBus.inScope(EventScope.INTERNAL_PLUGIN).register(subscriber);
                      initialPluginItemsBuilder.add(
                          PluginItem.create(subscriber, EventScope.INTERNAL_PLUGIN));
                    }));

    // Now user provided plugins via MH Java Client API are also using this handler. But considered
    // there are only a few users doing this, set the isUserPlugin=false here. Further breaking down
    // the plugin type would be helpful for further breaking down the error types.
    apiPluginExceptionHandler =
        new SubscriberExceptionLoggingHandler(true /* saveException */, false /*isUserPlugin */);
    scopedEventBus.add(EventScope.API_PLUGIN, new EventBus(apiPluginExceptionHandler));
    setting
        .apiPluginSubscribers()
        .ifPresent(
            subscribers ->
                subscribers.forEach(
                    subscriber -> {
                      scopedEventBus.inScope(EventScope.API_PLUGIN).register(subscriber);
                      initialPluginItemsBuilder.add(
                          PluginItem.create(subscriber, EventScope.API_PLUGIN));
                    }));

    jarPluginExceptionHandler =
        new SubscriberExceptionLoggingHandler(true /* saveException */, true /*isUserPlugin*/);
    scopedEventBus.add(EventScope.JAR_PLUGIN, new EventBus(jarPluginExceptionHandler));
    setting
        .jarPluginSubscribers()
        .ifPresent(
            subscribers ->
                subscribers.forEach(
                    subscriber -> {
                      scopedEventBus.inScope(EventScope.JAR_PLUGIN).register(subscriber);
                      initialPluginItemsBuilder.add(
                          PluginItem.create(subscriber, EventScope.JAR_PLUGIN));
                    }));

    initialPluginItems = initialPluginItemsBuilder.build();
    scopedEventBus.add(EventScope.TEST_MESSAGE);

    testMessagePoster = new CacheableTestMessagePoster(testMessagePosterExecutorService);
  }

  @Override
  public final boolean isClosed() {
    return !isRunning();
  }

  @Override
  public final Optional<List<DeviceFeature>> getDeviceFeatures() {
    return Optional.ofNullable(deviceFeatures);
  }

  @Override
  public final TestInfo getTestInfo() {
    return testInfo;
  }

  @Override
  public void postKill(boolean timeout, int killCount) {
    if (timeout) {
      testInfo
          .result()
          .toNewResult()
          .setNonPassing(
              Test.TestResult.TIMEOUT,
              new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                  InfraErrorId.TR_TEST_TIMEOUT_AND_KILLED, "Test is TIMEOUT and killed"));
      if (killCount == 1) {
        // Only prints this log the first time a test gets killed.
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Kill expired test: %s and mark test result TIMEOUT", testInfo.locator().getId());
      }
    }
  }

  @Override
  public void preExecute() {
    // Does nothing.
  }

  @Override
  public void finalizeTest(
      com.google.devtools.mobileharness.api.model.error.MobileHarnessException error) {
    TestInfo test = getTestInfo();
    if (test.status().get() != TestStatus.DONE) {
      // When a test is added to a device, but the device is disconnected during periodical check,
      // the test doesn't have a chance to get started. In this case, it is important to finalize
      // it here. Also see b/19134904.
      test.status().set(TestStatus.DONE);
      if (test.result().get() == TestResult.UNKNOWN) {
        test.result().toNewResult().setNonPassing(Test.TestResult.ERROR, error);
        test.log().atWarning().alsoTo(logger).log("%s", error.getMessage());
      }
    }
  }

  @Override
  public TestExecutionResult execute() throws InterruptedException {
    try (MobileHarnessAutoCloseable executeSpan = getExecuteSpan()) {
      MobileHarnessLogTag.addTag(MobileHarnessLogTag.TEST_ID, getTestInfo().locator().getId());
      String oldThreadName = Thread.currentThread().getName();
      Thread.currentThread()
          .setName(
              "test-runner-"
                  + getTestInfo().locator().getId()
                  + "@"
                  + getAllocation().getAllDevices().stream()
                      .map(com.google.devtools.mobileharness.api.model.lab.DeviceLocator::id)
                      .collect(joining(",")));
      try (WithTestContext ignored2 = TestContext.set(testInfo.locator())) {
        ImmutableList<DeviceLocator> deviceLocators = allocation.getAllDeviceLocators();
        TestLocator testLocator = testInfo.locator();
        JobInfo jobInfo = testInfo.jobInfo();
        Throwable testException = null;
        testInfo.status().set(TestStatus.RUNNING);
        PostTestDeviceOp postTestDeviceOp;
        try {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Start pre-running test %s on device(s) %s",
                  testLocator.getName(), deviceLocators);

          boolean skipRunTest = false;
          if (shouldRunDoPreRunTest(testInfo)) {
            try (MobileHarnessAutoCloseable preRunTestSpan = getPreRunTestSpan()) {
              skipRunTest = doPreRunTest();
            } finally {
              testInfo
                  .log()
                  .atInfo()
                  .alsoTo(logger)
                  .log(
                      "Pre-run test %s finished%s",
                      testLocator.getName(),
                      skipRunTest ? " and test running will be skipped" : "");
            }
          } else {
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log("Skip doPreRunTest because it is a resumed job");
          }
          if (!skipRunTest && !Thread.currentThread().isInterrupted()) {
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log(
                    "Start running test %s on device(s) %s",
                    testInfo.locator().getName(), allocation.getAllDeviceLocators());
            try (MobileHarnessAutoCloseable runTestSpan = getRunTestSpan()) {
              runTest(testInfo, allocation);
            } finally {
              testInfo
                  .log()
                  .atInfo()
                  .alsoTo(logger)
                  .log("Run test %s finished", testInfo.locator().getName());
            }
          }
        } catch (InterruptedException e) {
          if (jobInfo.timer().isExpired()) {
            // Job is timeout and test thread pool is shutdown.
            testInfo
                .result()
                .toNewResult()
                .setNonPassing(
                    Test.TestResult.TIMEOUT,
                    new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                        InfraErrorId.TR_JOB_TIMEOUT_AND_INTERRUPTED,
                        "Test interrupted due to job timeout",
                        e));
          } else if (testInfo.timer().isExpired()) {
            // Job is timeout and test thread pool is shutdown.
            testInfo
                .result()
                .toNewResult()
                .setNonPassing(
                    Test.TestResult.TIMEOUT,
                    new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                        InfraErrorId.TR_JOB_TIMEOUT_AND_INTERRUPTED,
                        "Test timeout and interrupted",
                        e));
          } else if (SystemUtil.isProcessShuttingDown()) {
            // The process is shutting down.
            testInfo
                .result()
                .toNewResult()
                .setNonPassing(
                    Test.TestResult.ERROR,
                    new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                        InfraErrorId.TR_TEST_INTERRUPTED_WHEN_PROCESS_SHUTDOWN,
                        "The process is shutting down.",
                        e));
          } else {
            // If job is not timeout but test timeout, TestManager has already marked the test as
            // TIMEOUT and can not be overwritten here.
            com.google.devtools.mobileharness.api.model.error.MobileHarnessException cause;

            if ("lab".equals(getComponentName()) || "local".equals(getComponentName())) {
              // If the test is not timeout, and it runs in lab or local mode, it should be
              // some error such as device disconnected.
              if (SharedPoolJobUtil.isUsingSharedPool(testInfo.jobInfo())) {
                cause =
                    new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                        InfraErrorId.TR_TEST_INTERRUPTED_IN_SHARED_LAB,
                        "Test is interrupted in the shared lab. It can be caused by device"
                            + " disconnection.",
                        e);
              } else {
                cause =
                    new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                        InfraErrorId.TR_TEST_INTERRUPTED_IN_SATELLITE_LAB,
                        "Test is interrupted in the satellite lab. It can be caused by device"
                            + " disconnection",
                        e);
              }
            } else {
              // If the test is not timeout, and it runs in client, it's usually killed by user.
              cause =
                  new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                      InfraErrorId.TR_TEST_INTERRUPTED_WHEN_USER_KILL_JOB,
                      "Test interrupted because it's manually killed by user.",
                      e);
            }
            testInfo.result().toNewResult().setNonPassing(Test.TestResult.ERROR, cause);
            testException = e;
          }
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log("Test interrupted, error:%n%s", Throwables.getStackTraceAsString(e));
          throw e;
        } catch (MobileHarnessException e) {
          // Marks this to {@link TestResult#ERROR} in case the driver has already changed the
          // {@link TestResult}.
          testInfo
              .result()
              .toNewResult()
              .setNonPassing(
                  Result.upgradeTestResult(ResultUtil.getResultByException(e)),
                  ErrorModelConverter.upgradeMobileHarnessException(e));
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log("ERROR: %s", Throwables.getStackTraceAsString(e));
          testException = e;
        } catch (Throwable e) {
          // Marks this to {@link TestResult#ERROR} in case the driver has already changed the
          // {@link TestResult}.
          testInfo
              .result()
              .toNewResult()
              .setNonPassing(
                  Test.TestResult.ERROR,
                  new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                      InfraErrorId.TR_TEST_RUNNER_FATAL_ERROR,
                      "TR FATAL ERROR: " + e.getMessage(),
                      e));
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log("FATAL ERROR: %s", Throwables.getStackTraceAsString(e));
          testException = e;
        } finally {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Start post-run test %s on device(s) %s",
                  testInfo.locator().getName(), allocation.getAllDeviceLocators());
          testInfo
              .properties()
              .add(
                  PropertyName.Test.DEVICE_DONE_EPOCH_MS,
                  String.valueOf(Clock.systemUTC().millis()));
          try (MobileHarnessAutoCloseable postRunTestSpan = getPostRunTestSpan()) {
            // Makes sure we finalize the test result.
            if (testInfo.result().get() == TestResult.UNKNOWN) {
              String errMsg = "Test result not found when test finished normally. Mark as ERROR.";
              testInfo
                  .result()
                  .toNewResult()
                  .setNonPassing(
                      Test.TestResult.ERROR,
                      new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                          InfraErrorId.TR_TEST_FINISHED_WITHOUT_RESULT, errMsg));
              testInfo.log().atWarning().alsoTo(logger).log("%s", errMsg);
            }

            postTestDeviceOp = doPostRunTest(testException);
            testMessagePoster.close();
          } finally {
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log("Post-run test %s finished", testInfo.locator().getName());
          }
          // Do not add test logs after calling the following method.
          testInfo.log().shrink();
          jobInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("End test %s on devices(s) %s", testLocator.getName(), deviceLocators);
        }
        return TestExecutionResult.create(
            Result.upgradeTestResult(testInfo.result().get()), postTestDeviceOp);
      } finally {
        Thread.currentThread().setName(oldThreadName);
      }
    }
  }

  /** Whether the runner need to run {@link #doPreRunTest()} method. */
  protected boolean shouldRunDoPreRunTest(TestInfo testInfo) {
    return true;
  }

  /** Returns the name of the component in which the test runner runs. */
  protected abstract String getComponentName();

  /** Initializes the test runner. */
  protected abstract void initialize(TestInfo testInfo, Allocation allocation)
      throws MobileHarnessException, InterruptedException;

  /**
   * Checks the device before running the test. If any exception thrown out, will skip the test and
   * mark it as ERROR.
   */
  protected abstract List<DeviceFeature> checkDevice(TestInfo testInfo, Allocation allocation)
      throws MobileHarnessException, InterruptedException;

  /**
   * Updates deviceFeatures, deviceInfos and allocation. This should be called in the following
   * scenarios:
   *
   * <ul>
   *   <li>After initialization
   *   <li>When test is finished running for local test runner.
   *   <li>When there are deviceFeatures in test status for remote test runner.
   * </ul>
   */
  protected void updateDeviceStatus(List<DeviceFeature> deviceFeatures)
      throws MobileHarnessException {
    MobileHarnessExceptions.check(
        allocation.getAllDeviceLocators().size() == deviceFeatures.size(),
        InfraErrorId.TR_CHECK_DEVICE_UNMATCHED_DEVICE_COUNT,
        () ->
            String.format(
                "Unmatched device count: allocated: %s, checked: %s",
                allocation.getAllDeviceLocators(), deviceFeatures));

    logger.atInfo().log(
        "Update the status of test: [%s], devices: [%s]",
        testInfo.locator().getName(), allocation.getAllDeviceLocators());

    this.deviceFeatures = ImmutableList.copyOf(deviceFeatures);
    this.deviceInfos =
        Streams.zip(
                allocation.getAllDeviceLocators().stream(),
                deviceFeatures.stream(),
                (deviceLocator, deviceFeature) ->
                    DeviceInfoUtil.getDeviceInfoForCurrentTest(
                        deviceLocator.getSerial(), deviceFeature, testInfo))
            .collect(toCollection(ArrayList::new));

    this.allocation =
        new Allocation(
            allocation.getTest(),
            allocation.getAllDeviceLocators(),
            deviceInfos.stream()
                .map(DeviceInfoUtil::getDimensions)
                .collect(toCollection(ArrayList::new)));
  }

  /**
   * Preparation before running the test. If any exception thrown out, will skip the test and mark
   * it as ERROR.
   *
   * @param isTestSkipped whether the test has already been skipped in TestStartingEvent phase
   */
  protected abstract void preRunTest(
      boolean isTestSkipped,
      TestInfo testInfo,
      Allocation allocation,
      List<DeviceFeature> deviceFeatures)
      throws MobileHarnessException, InterruptedException;

  /** Executes the test. Note if any exception thrown out, will mark the test as ERROR. */
  protected abstract void runTest(TestInfo testInfo, Allocation allocation)
      throws MobileHarnessException, InterruptedException;

  /**
   * Cleaning up after the test is finished. Note the exception occurs in this method will only be
   * logged. Won't affect the test result.
   */
  protected abstract PostTestDeviceOp postRunTest(TestInfo testInfo, Allocation allocation)
      throws MobileHarnessException, InterruptedException;

  /** Gets the span to be used in test execution logic, empty by default. */
  protected MobileHarnessAutoCloseable getExecuteSpan() {
    return new MobileHarnessAutoCloseable();
  }

  /** Gets the span to be used in pre run test logic, empty by default. */
  protected MobileHarnessAutoCloseable getPreRunTestSpan() {
    return new MobileHarnessAutoCloseable();
  }

  /** Gets the span to be used in run test logic, empty by default. */
  protected MobileHarnessAutoCloseable getRunTestSpan() {
    return new MobileHarnessAutoCloseable();
  }

  /** Gets the span to be used in post run test logic, empty by default. */
  protected MobileHarnessAutoCloseable getPostRunTestSpan() {
    return new MobileHarnessAutoCloseable();
  }

  /**
   * Sends the test starting events, runs {@link #preRunTest}, and send test started event.
   *
   * @return whether the test running needs to be skipped
   */
  @VisibleForTesting
  boolean doPreRunTest() throws MobileHarnessException, InterruptedException {
    boolean isTestSkipped;

    initialize(testInfo, allocation);

    // Use dimension specified in JobInfo as the default value of testInfo's dimension_pool
    // property.
    if (testInfo.jobInfo().dimensions().get(Name.POOL) != null
        && !testInfo.properties().has(DIMENSION_POOL)) {
      testInfo.properties().add(DIMENSION_POOL, testInfo.jobInfo().dimensions().get(Name.POOL));
    }

    List<DeviceFeature> deviceFeatures = checkDevice(testInfo, allocation);
    updateDeviceStatus(deviceFeatures);

    TestLocator testLocator = testInfo.locator();
    testInfo.log().atInfo().alsoTo(logger).log("Post TestStartingEvent to test %s", testLocator);
    // Event handlers in CLASS_INTERNAL:
    // 1) LabFileNotifier at lab server / test engine.
    // Event handlers in GLOBAL_INTERNAL:
    // 1) MossUploader.
    // Event handlers in API_PLUGIN:
    // 1) Inherit the event handlers of the API_PLUGIN bus from JobRunner.
    // 2) AndroidGmsCoreInstallerPlugin on Client side.
    // Event handlers in JAR_PLUGIN:
    // 1) Copy the event handlers of the JAR_PLUGIN bus from JobRunner on Client side.
    // 2) Lab plugins from "lab_plugin_jar" on Lab side.
    isTestSkipped =
        postTestEvent(
            "test starting event" /* eventType */,
            false /* afterDriverExecution */,
            createTestEvent(
                com.google.devtools.mobileharness.api.testrunner.event.test.TestStartingEvent.class,
                testInfo,
                allocation,
                deviceInfos,
                deviceFeatures,
                null),
            createTestEvent(
                TestStartingEvent.class, testInfo, allocation, deviceInfos, deviceFeatures, null));

    preRunTest(isTestSkipped, testInfo, allocation, deviceFeatures);
    testMessagePoster.asyncDisableAndHandleCache();

    testInfo.log().atInfo().alsoTo(logger).log("Post TestStartedEvent to test %s", testLocator);
    // Event handlers in CLASS_INTERNAL:
    // <-empty->
    // Event handlers in GLOBAL_INTERNAL:
    // 1) MossUploader.
    // Event handlers in INTERNAL_PLUGIN:
    // 1) TestAccountProviderClientPlugin.
    // Event handlers in API_PLUGIN:
    // 1) Inherit the event handlers of the API_PLUGIN bus from JobRunner.
    // Event handlers in JAR_PLUGIN:
    // 1) Inherit the event handlers of the JAR_PLUGIN bus from JobRunner on Client side.
    // 2) Lab plugins from "lab_plugin_jar" on Lab side.
    isTestSkipped |=
        postTestEvent(
            "test started event" /* eventType */,
            false /* afterDriverExecution */,
            createTestEvent(
                TestStartEvent.class, testInfo, allocation, deviceInfos, deviceFeatures, null),
            createTestEvent(
                TestStartedEvent.class, testInfo, allocation, deviceInfos, deviceFeatures, null),
            createTestEvent(
                com.google.devtools.mobileharness.api.testrunner.event.test.TestStartedEvent.class,
                testInfo,
                allocation,
                deviceInfos,
                deviceFeatures,
                null));
    return isTestSkipped;
  }

  /**
   * {@link AbstractTestRunner} holds new allocation:
   * com.google.devtools.mobileharness.api.model.allocation.Allocation {@link
   * AbstractDirectTestRunner} holds old allocation:
   * com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation This function returns
   * the old allocation and is designed only for test.
   */
  @VisibleForTesting
  Allocation getOldAllocation() {
    return allocation;
  }

  /**
   * Sends the test ending event, runs {@link #postRunTest(TestInfo, Allocation)}, and send test
   * ended event.
   */
  private PostTestDeviceOp doPostRunTest(@Nullable Throwable testException) {
    TestLocator testLocator = testInfo.locator();
    // If TestRunner.postRunTest(TestInfo...) throws an exception, the device will be rebooted.
    PostTestDeviceOp postTestDeviceOp = PostTestDeviceOp.REBOOT;
    testInfo.log().atInfo().alsoTo(logger).log("Post TestEndingEvent to test %s", testLocator);
    // Event handlers in JAR_PLUGIN:
    // 1) Inherit the event handlers of the JAR_PLUGIN bus from JobRunner on Client side.
    // 2) Lab plugins from "lab_plugin_jar" on Lab side.
    // Event handlers in API_PLUGIN:
    // 1) Inherit the event handlers of the API_PLUGIN bus from JobRunner.
    // Event handlers in GLOBAL_INTERNAL:
    // 1) MossUploader on client side.
    // 2) Test engine flushes its stdout/stderr files.
    // Event handlers in CLASS_INTERNAL:
    // 1) TestCommandHistorySaver on lab side.
    postTestEvent(
        "test ending event" /* eventType */,
        true /* afterDriverExecution */,
        createTestEvent(
            com.google.devtools.mobileharness.api.testrunner.event.test.TestEndingEvent.class,
            testInfo,
            allocation,
            deviceInfos,
            deviceFeatures,
            testException),
        createTestEvent(
            TestEndingEvent.class,
            testInfo,
            allocation,
            deviceInfos,
            deviceFeatures,
            testException));
    // Marks the test as finished.
    testInfo.status().set(TestStatus.DONE);
    logger.atInfo().log(
        "Finish [%s:%s] : %s",
        testInfo.jobInfo().locator(), testInfo.locator().getName(), testInfo.result().get());
    try {
      postTestDeviceOp = postRunTest(testInfo, allocation);
    } catch (InterruptedException e) {
      testInfo
          .errors()
          .addAndLog(
              new MobileHarnessException(
                  ErrorCode.INTERRUPTED, "Post-test operations interrupted", e),
              logger);
    } catch (MobileHarnessException e) {
      testInfo.errors().addAndLog(e, logger);
    } catch (Throwable e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("FATAL ERROR: %s", Throwables.getStackTraceAsString(e));
      testInfo.errors().add(ErrorCode.TEST_POST_RUN_ERROR, e);
    } finally {
      testInfo.log().atInfo().alsoTo(logger).log("Post TestEndedEvent to test %s", testLocator);
      // Event handlers in JAR_PLUGIN:
      // 1) Inherit the event handlers of the JAR_PLUGIN bus from JobRunner on Client side.
      // 2) Lab plugins from "lab_plugin_jar" on Lab side.
      // Event handlers in API_PLUGIN:
      // 1) Inherit the event handlers of the API_PLUGIN bus from JobRunner on Client side.
      // 2) TestRetryHandler on Client side.
      // 3) MonarchPlugin on Client side.
      // 4) PowerbugUploader on Client side.
      // Event handlers in INTERNAL_PLUGIN:
      // 1) TestAccountProviderClientPlugin.
      //
      // TestInfo end time will be set at this point.
      //
      // Event handlers in GLOBAL_INTERNAL:
      // 1) GenFileHandler
      // 2) GoogleAnalyticsUploader
      // 3) MossUploader: should run after API_PLUGIN to catch changes in client.
      // 4) StreamzStats: reports metric values to Streamz
      // Event handlers in CLASS_INTERNAL:
      // 1) LocalTestRunner closes PluginCreator: Must after all lab/client plugins are done.
      //    Otherwise, plugins won't be able to load classes.
      // 2) Client JobRunner releases allocation: Must do it at the every end of the
      //    TestRunner. Otherwise, it may cause:
      //    a. Device is assigned to another test before the old test runner thread stops,
      //       which means there are multiple test runner thread controlling the same
      //       device.
      //    b. Releasing device will sync to master to update the device as IDLE. And when
      //       lab server MasterSyncerForJob in GLOBAL_INTERNAL bus talks to master to close test,
      //       master will throw out RpcException. See http://b/13087889
      postTestEndedEvent(
          createTestEvent(
              TestEndedEvent.class,
              testInfo,
              allocation,
              deviceInfos,
              deviceFeatures,
              testException));
      logger.atInfo().log("Stopped");
    }
    return postTestDeviceOp;
  }

  @Override
  public final void registerTestEventSubscriber(Object subscriber, EventScope scope) {
    scopedEventBus.inScope(scope).register(subscriber);
  }

  @Override
  public final TestMessagePoster getTestMessagePoster() {
    return testMessagePoster;
  }

  /**
   * Creates test events.
   *
   * <p>TODO: Supports multiple devices in test events.
   */
  protected Object createTestEvent(
      Class<?> eventType,
      TestInfo testInfo,
      Allocation allocation,
      @Nullable List<DeviceInfo> deviceInfos,
      @Nullable List<DeviceFeature> deviceFeatures,
      @Nullable Throwable testError) {
    if (eventType == TestStartingEvent.class) {
      return new TestStartingEvent(testInfo, allocation, checkNotNull(deviceInfos).get(0));
    } else if (eventType
        == com.google.devtools.mobileharness.api.testrunner.event.test.TestStartingEvent.class) {
      DeviceFeature primaryDeviceFeature = checkNotNull(deviceFeatures).get(0);
      return new com.google.devtools.mobileharness.api.testrunner.event.test.TestStartingEvent() {
        @Override
        public DeviceFeature getDeviceFeature() {
          return primaryDeviceFeature;
        }

        @Override
        public TestInfo getTest() {
          return testInfo;
        }

        @Override
        public com.google.devtools.mobileharness.api.model.allocation.Allocation getAllocation() {
          return allocation.toNewAllocation();
        }
      };
    } else if (eventType == TestStartEvent.class) {
      return new TestStartEvent(testInfo, allocation, checkNotNull(deviceInfos).get(0));
    } else if (eventType == TestStartedEvent.class) {
      return new TestStartedEvent(testInfo, allocation, checkNotNull(deviceInfos).get(0));
    } else if (eventType
        == com.google.devtools.mobileharness.api.testrunner.event.test.TestStartedEvent.class) {
      DeviceFeature primaryDeviceFeature = checkNotNull(deviceFeatures).get(0);
      return new com.google.devtools.mobileharness.api.testrunner.event.test.TestStartedEvent() {
        @Override
        public DeviceFeature getDeviceFeature() {
          return primaryDeviceFeature;
        }

        @Override
        public TestInfo getTest() {
          return testInfo;
        }

        @Override
        public com.google.devtools.mobileharness.api.model.allocation.Allocation getAllocation() {
          return allocation.toNewAllocation();
        }
      };
    } else if (eventType == TestEndingEvent.class) {
      return new TestEndingEvent(
          testInfo, allocation, deviceInfos == null ? null : deviceInfos.get(0), testError);
    } else if (eventType == TestEndedEvent.class) {
      return new TestEndedEvent(
          testInfo,
          allocation,
          deviceInfos == null ? null : deviceInfos.get(0),
          /* shouldRebootDevice= */ false,
          testError);
    } else if (eventType
        == com.google.devtools.mobileharness.api.testrunner.event.test.TestEndingEvent.class) {
      return new com.google.devtools.mobileharness.api.testrunner.event.test.TestEndingEvent() {

        @Override
        public TestInfo getTest() {
          return testInfo;
        }

        @Override
        public com.google.devtools.mobileharness.api.model.allocation.Allocation getAllocation() {
          return allocation.toNewAllocation();
        }

        @Override
        public Optional<Throwable> getExecutionError() {
          return Optional.ofNullable(testError);
        }
      };
    } else {
      throw new IllegalArgumentException(
          "Failed to create test event. Type not supported: " + eventType.getName());
    }
  }

  /**
   * Posts test ended events in CLASS_INTERNAL, GLOBAL_INTERNAL, INTERNAL_PLUGIN, API_PLUGIN and
   * JAR_PLUGIN event buses in the given order.
   *
   * @return whether plugins want to skip running the test
   */
  private boolean postTestEndedEvent(Object testEvents) {
    List<Object> events = Arrays.asList(testEvents);
    try {
      scopedEventBus.post(events, EventScope.JAR_PLUGIN, EventScope.API_PLUGIN);
      getTestInfo().timing().end();
      scopedEventBus.post(
          events,
          EventScope.INTERNAL_PLUGIN,
          EventScope.GLOBAL_INTERNAL,
          EventScope.CLASS_INTERNAL);
      if (checkPluginExceptions(true)) {
        return true;
      }
    } catch (RuntimeException | Error e) {
      logTestEventError(e, "test ended event");
    }
    return false;
  }

  /**
   * Posts test events in CLASS_INTERNAL, GLOBAL_INTERNAL, INTERNAL_PLUGIN, API_PLUGIN and
   * JAR_PLUGIN event buses in the given order.
   *
   * @param afterDriverExecution whether the driver has run. If so, it means that the event posting
   *     scope should be reversed.
   * @return whether plugins want to skip running the test
   */
  protected final boolean postTestEvent(
      String eventType, boolean afterDriverExecution, Object... testEvents) {
    List<Object> events = Arrays.asList(testEvents);
    try {
      if (afterDriverExecution) {
        scopedEventBus.post(
            events,
            EventScope.JAR_PLUGIN,
            EventScope.API_PLUGIN,
            EventScope.INTERNAL_PLUGIN,
            EventScope.GLOBAL_INTERNAL,
            EventScope.CLASS_INTERNAL);
      } else {
        scopedEventBus.post(
            events,
            EventScope.CLASS_INTERNAL,
            EventScope.GLOBAL_INTERNAL,
            EventScope.INTERNAL_PLUGIN,
            EventScope.API_PLUGIN,
            EventScope.JAR_PLUGIN);
      }

      if (checkPluginExceptions(afterDriverExecution)) {
        return true;
      }
    } catch (RuntimeException | Error e) {
      logTestEventError(e, eventType);
    }
    return false;
  }

  /**
   * Returns {@code true} if the test should be skipped and also sets the test result.
   *
   * @param afterDriverExecution if it is after all drivers have been executed. If so, ignore test
   *     skipping and give warnings.
   * @return if the test should be skipped
   */
  private boolean checkPluginExceptions(boolean afterDriverExecution) {
    List<SubscriberExceptionContext> internalPluginExceptions =
        internalPluginExceptionHandler.pollExceptions();
    List<SubscriberExceptionContext> apiPluginExceptions =
        apiPluginExceptionHandler.pollExceptions();
    List<SubscriberExceptionContext> jarPluginExceptions =
        jarPluginExceptionHandler.pollExceptions();
    ImmutableList<SkipInformation> skipInfos =
        Streams.concat(
                internalPluginExceptions.stream(),
                apiPluginExceptions.stream(),
                jarPluginExceptions.stream())
            .map(SkipInformationHandler::convertIfSkipTestRunning)
            .flatMap(Streams::stream)
            .collect(toImmutableList());
    if (skipInfos.isEmpty()) {
      return false;
    }
    SkipResultWithCause skipResultWithCause = SkipInformationHandler.getTestResult(skipInfos);
    if (afterDriverExecution) {
      testInfo
          .errors()
          .addAndLog(
              ErrorCode.PLUGIN_ERROR,
              String.format(
                  "Plugins want to skip test and set test result but it is ignored because the"
                      + " driver has ended. The test result will NOT be changed as the desired"
                      + " test result in the exceptions. SkipTestException only works in"
                      + " TestStartingEvent/TestStartedEvent/LocalDriverStartingEvent/"
                      + "LocalDecoratorPreForwardEvent. If you just want to change test result"
                      + ", please call testInfo.resultWithCause().set() directly in your"
                      + " plugin. See go/mh-plugin-skip-test. Detail: %s",
                  skipResultWithCause.report()),
              logger);
      return false;
    }
    if (skipResultWithCause.resultWithCause().type().equals(Test.TestResult.PASS)) {
      testInfo.resultWithCause().setPass();
    } else {
      testInfo
          .resultWithCause()
          .setNonPassing(
              skipResultWithCause.resultWithCause().type(),
              skipResultWithCause.resultWithCause().causeExceptionNonEmpty());
    }
    return true;
  }

  /** Logs down the error of the test event. */
  protected void logTestEventError(Throwable e, String eventType) {
    testInfo
        .errors()
        .addAndLog(
            new MobileHarnessException(
                ErrorCode.EVENT_NOT_POSTED, "Failed to post " + eventType, e),
            logger);
  }

  private class CacheableTestMessagePoster extends CacheableTestMessageHandler
      implements TestMessagePoster {

    private CacheableTestMessagePoster(ExecutorService executorService) {
      super(executorService);
    }

    @Override
    public void postTestMessage(TestMessageInfo testMessageInfo) {
      submitTestMessage(testMessageInfo);
    }

    @Override
    public String getTestId() {
      return testInfo.locator().getId();
    }

    @Override
    public void handleTestMessage(TestMessageInfo testMessageInfo) {
      // TODO: Supports multiple devices in test messages.
      scopedEventBus.post(
          new TestMessageEvent(
              testMessageInfo, testInfo, allocation, checkNotNull(deviceInfos).get(0)),
          EventScope.TEST_MESSAGE);
    }
  }
}
