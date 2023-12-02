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

package com.google.devtools.mobileharness.infra.client.api.controller.job;

import static com.google.common.collect.Comparators.min;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Joiner.MapJoiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Streams;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Error.ExceptionDetail;
import com.google.devtools.mobileharness.api.model.proto.Job.AllocationExitStrategy;
import com.google.devtools.mobileharness.api.model.proto.Test;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.AllocationWithStats;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.DeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.AllocationDiagnostician;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.DeviceFilter;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.DeviceFilter.FilterType;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.Report;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.multidevice.MultiDeviceDiagnostician;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.singledevice.SingleDeviceDiagnostician;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.devtools.mobileharness.infra.client.api.mode.remote.JobCancelledException;
import com.google.devtools.mobileharness.infra.client.api.util.result.ClientAllocErrorUtil;
import com.google.devtools.mobileharness.infra.controller.plugin.CommonPluginCreatorFactory;
import com.google.devtools.mobileharness.infra.controller.plugin.PluginCreator;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunnerSetting;
import com.google.devtools.mobileharness.infra.controller.test.manager.TestManager;
import com.google.devtools.mobileharness.infra.controller.test.util.SubscriberExceptionLoggingHandler;
import com.google.devtools.mobileharness.shared.constant.closeable.MobileHarnessAutoCloseable;
import com.google.devtools.mobileharness.shared.util.comm.messaging.poster.TestMessagePoster;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadFactoryUtil;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.devtools.mobileharness.shared.util.event.EventBus.SubscriberExceptionContext;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.AbstractModule;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.client.api.event.JobStartEvent;
import com.google.wireless.qa.mobileharness.shared.api.validator.JobValidator;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Job;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.ScopedEventBus;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.SkipInformationHandler;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.SkipInformationHandler.SkipInformation;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.SkipInformationHandler.SkipResultWithCause;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.model.job.util.ResultUtil;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import javax.annotation.Nullable;

/**
 * Job runner for running a given job in an independent thread. It's feasible to use it in an
 * external environment. E.g. in a RBE instance. The logic removed includes:
 *
 * <ul>
 *   <li>trace.* that may cause the library to crash.
 *   <li>JobFileResolver and TestMessagePoster that need further refactor and are useless in
 *       external currently.
 * </ul>
 */
public class JobRunnerCore implements Runnable {

  /** Event scopes of job runner. */
  public enum EventScope {
    /** For this specific runner, for Mobile Harness framework logic. */
    CLASS_INTERNAL,
    /** For global Mobile Harness framework logic. */
    GLOBAL_INTERNAL,
    /** For some special internal plugins which needs to be executed before API/JAR plugins. */
    INTERNAL_PLUGIN,
    /** For this job and all belonging tests, for plugins added via Client API. */
    API_PLUGIN,
    /** For this job and all belonging tests, for "client_plugin_jar". */
    JAR_PLUGIN,
  }

  /** The base interval. */
  private static final Duration BASE_INTERVAL = Duration.ofMillis(500L);

  /** The normal first device query after job starts. */
  private static final Duration NORMAL_START_QUERY_DEVICE_LATENCY = Duration.ofMinutes(10L);

  /** The normal first device query after job starts. */
  private static final Duration LOCAL_FAIL_FAST_START_QUERY_DEVICE_LATENCY = Duration.ofMinutes(1L);

  /** The normal interval between two device queries. */
  private static final Duration NORMAL_QUERY_DEVICE_INTERVAL = Duration.ofMinutes(2L);

  /** The normal max times for device query. */
  private static final int NORMAL_MAX_QUERY_DEVICE_TIMES = 5;

  /** Interval for checking the job diagnostic report. */
  private static final Duration ALLOCATION_DIAGNOSE_INTERVAL_BEFORE_JOB_TIMEOUT =
      Duration.ofSeconds(60);

  /** The max times for job diagnostic. */
  private static final int MAX_ALLOCATION_DIAGNOSE_TIMES = 6;

  private static final int MAX_ALLOCATION_DIAGNOSE_TIMES_BEFORE_JOB_TIMEOUT = 3;

  /** The large base interval multiplier for polling the device manager to allocate devices. */
  private static final int LARGE_POLL_ALLOCATION_INTERVAL_MULTIPLIER = 20;

  /** The medium base interval multiplier for polling the device manager to allocate devices. */
  private static final int MEDIUM_POLL_ALLOCATION_INTERVAL_MULTIPLIER = 8;

  /** The small base interval multiplier for polling the device manager to allocate devices. */
  private static final int SMALL_POLL_ALLOCATION_INTERVAL_MULTIPLIER = 1;

  /** The real-time base interval multiplier for polling the device manager to allocate devices. */
  private static final int REAL_TIME_POLL_ALLOCATION_INTERVAL_MULTIPLIER = 2;

  /** The number of using the small polling allocation interval multiplier when the job starts. */
  private static final int NUM_USE_SMALL_POLL_ALLOCATION_INTERVAL_MULTIPLIER = 4;

  /** The number of using the real-time polling allocation interval multiplier. */
  private static final int NUM_USE_REAL_TIME_POLL_ALLOCATION_INTERVAL_MULTIPLIER = 15;

  /** Timeout in milliseconds for waiting for the test thread pool terminate. */
  private static final long TERMINATE_TEST_TIMEOUT_MS = Duration.ofMinutes(5).toMillis();

  /** Interval for checking the tests which are waiting for allocating devices. */
  private static final Duration CHECK_NEW_TESTS_INTERVAL = Duration.ofSeconds(30);

  /** Link to the helper page for the information about why a test failed to allocate any device. */
  private static final String TEST_NOT_ASSIGNED_HELPER_LINK =
      "http://go/omnilab-why-test-not-assigned";

  /** Error message to point users to the help page. */
  private static final String TEST_NOT_ASSIGNED_ERROR_MSG =
      "See " + TEST_NOT_ASSIGNED_HELPER_LINK + " for more details.";

  /** Logger for this job. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Detail information of the job. */
  private final JobInfo jobInfo;

  /** Execute mode for this job. */
  private final ExecMode execMode;

  /** Device allocator for allocating remote/local device resources. */
  private final DeviceAllocator deviceAllocator;

  /** To manage all the test runner threads of this job. */
  protected final TestManager<DirectTestRunner> testManager;

  /** Test manager and runner thread pool. */
  private final ListeningExecutorService testManagerThreadPool;

  /** Whether the current thread is running. */
  private volatile boolean running = false;

  /** Multiple event buses to handle events in different scopes with different handlers. */
  private final ScopedEventBus<EventScope> scopedEventBus;

  private final SubscriberExceptionLoggingHandler internalPluginExceptionHandler;
  private final SubscriberExceptionLoggingHandler apiPluginExceptionHandler;
  private final SubscriberExceptionLoggingHandler jarPluginExceptionHandler;

  /** Whether the job has allocated any devices. */
  private volatile boolean hasAllocation = false;

  /** File util for cleaning job files. */
  private final LocalFileUtil fileUtil;

  private final Clock clock;
  private final Sleeper sleeper;

  /** Util for diagnostic the reasons when the job fails to allocate devices. */
  @Nullable private volatile AllocationDiagnostician allocDiagnostician;

  private int diagnosticTimes = 0;

  /** The test message subscribers of the job. */
  private final List<Object> testMessageSubscribers = new ArrayList<>();

  /** Event subscribers of each event scope. */
  private final ListMultimap<EventScope, Object> scopeEventSubscribers =
      LinkedListMultimap.create();

  /** Validator for validating the job config. */
  private final JobValidator jobValidator;

  private final PluginCreator.Factory pluginLoaderFactory;

  private final DeviceQuerier deviceQuerier;

  private final DeviceQueryFilter deviceQueryFilter;

  /** The first device query after job starts. */
  private final Duration startQueryDeviceLatency;

  /** The interval between two device queries. */
  private final Duration queryDeviceInterval;

  private final int maxQueryDeviceTimes;

  /** Creates a job runner to run the given job in the given mode. */
  public JobRunnerCore(
      JobInfo jobInfo,
      DeviceAllocator deviceAllocator,
      ExecMode execMode,
      EventBus globalInternalBus)
      throws com.google.wireless.qa.mobileharness.shared.MobileHarnessException,
          InterruptedException {
    this(
        jobInfo,
        deviceAllocator,
        execMode,
        new TestManager<>(),
        MoreExecutors.listeningDecorator(
            Executors.newCachedThreadPool(
                ThreadFactoryUtil.createThreadFactory("test-manager-thread"))),
        new LocalFileUtil(),
        Clock.systemUTC(),
        Sleeper.defaultSleeper(),
        new JobValidator(),
        new CommonPluginCreatorFactory(),
        globalInternalBus);
  }

  protected JobRunnerCore(
      JobInfo jobInfo,
      DeviceAllocator deviceAllocator,
      ExecMode execMode,
      TestManager<DirectTestRunner> testManager,
      ListeningExecutorService testManagerThreadPool,
      LocalFileUtil fileUtil,
      Clock clock,
      Sleeper sleeper,
      JobValidator jobValidator,
      PluginCreator.Factory pluginLoaderFactory,
      @Nullable EventBus globalInternalBus)
      throws com.google.wireless.qa.mobileharness.shared.MobileHarnessException,
          InterruptedException {
    this.jobInfo = jobInfo;
    this.deviceAllocator = deviceAllocator;
    this.execMode = execMode;
    this.fileUtil = fileUtil;
    this.clock = clock;
    this.sleeper = sleeper;
    this.deviceQuerier = execMode.createDeviceQuerier();
    this.testManagerThreadPool = testManagerThreadPool;
    this.testManager = testManager;
    logFailure(
        this.testManagerThreadPool.submit(testManager),
        Level.SEVERE,
        "Fatal error in test manager");
    scopedEventBus = new ScopedEventBus<>(EventScope.class);
    scopedEventBus.add(EventScope.CLASS_INTERNAL);
    scopedEventBus.add(EventScope.GLOBAL_INTERNAL, globalInternalBus);

    internalPluginExceptionHandler =
        new SubscriberExceptionLoggingHandler(/* saveException= */ true, /* isUserPlugin= */ false);
    scopedEventBus.add(EventScope.INTERNAL_PLUGIN, new EventBus(internalPluginExceptionHandler));

    // Now user provided plugins via MH Java Client API are also using this handler. But considered
    // there are only a few users doing this, set the isUserPlugin=false here. Further breaking down
    // the plugin type would be great for further breaking down the error types.
    apiPluginExceptionHandler =
        new SubscriberExceptionLoggingHandler(/* saveException= */ true, /* isUserPlugin= */ false);
    scopedEventBus.add(EventScope.API_PLUGIN, new EventBus(apiPluginExceptionHandler));

    jarPluginExceptionHandler =
        new SubscriberExceptionLoggingHandler(/* saveException= */ true, /* isUserPlugin= */ true);
    scopedEventBus.add(EventScope.JAR_PLUGIN, new EventBus(jarPluginExceptionHandler));
    this.jobValidator = jobValidator;
    this.pluginLoaderFactory = pluginLoaderFactory;
    switch (jobInfo.setting().getAllocationExitStrategy()) {
      case FAIL_FAST_NO_IDLE:
        deviceQueryFilter =
            new DeviceFilter()
                .getFilter(
                    jobInfo,
                    ImmutableList.of(
                        FilterType.ACCESS,
                        FilterType.DRIVER,
                        FilterType.DECORATOR,
                        FilterType.DIMENSION,
                        FilterType.STATUS));
        startQueryDeviceLatency =
            execMode.getClass().getSimpleName().equals("LocalMode")
                ? LOCAL_FAIL_FAST_START_QUERY_DEVICE_LATENCY
                : Duration.ZERO;
        queryDeviceInterval = Duration.ZERO;
        maxQueryDeviceTimes = 1;
        break;
      case FAIL_FAST_NO_MATCH:
        deviceQueryFilter =
            new DeviceFilter()
                .getFilter(
                    jobInfo,
                    ImmutableList.of(
                        FilterType.ACCESS,
                        FilterType.DRIVER,
                        FilterType.DECORATOR,
                        FilterType.DIMENSION));
        startQueryDeviceLatency =
            execMode.getClass().getSimpleName().equals("LocalMode")
                ? LOCAL_FAIL_FAST_START_QUERY_DEVICE_LATENCY
                : Duration.ZERO;
        queryDeviceInterval = Duration.ZERO;
        maxQueryDeviceTimes = 1;
        break;
      default:
        deviceQueryFilter =
            new DeviceFilter()
                .getFilter(
                    jobInfo,
                    ImmutableList.of(
                        FilterType.ACCESS,
                        FilterType.DRIVER,
                        FilterType.DECORATOR,
                        FilterType.DIMENSION));
        startQueryDeviceLatency = NORMAL_START_QUERY_DEVICE_LATENCY;
        queryDeviceInterval = NORMAL_QUERY_DEVICE_INTERVAL;
        maxQueryDeviceTimes = NORMAL_MAX_QUERY_DEVICE_TIMES;
        break;
    }
  }

  /** Returns the job executed by this runner. */
  public JobInfo getJobInfo() {
    return jobInfo;
  }

  /** Returns whether the current thread is running. */
  public boolean isRunning() {
    return running;
  }

  /**
   * Registers a handler for the events in the given scope.
   *
   * <p>If the given scope is {@link EventScope#API_PLUGIN} or {@link EventScope#JAR_PLUGIN},
   * registers the handler for the events in {@linkplain com.google.wireless.qa.mobileharness
   * .shared.controller.test.TestRunner.EventScope#TEST_MESSAGE TestRunner.EventScope.TEST_MESSAGE}
   * of each tests started after the invocation of this method too.
   */
  public void registerEventHandler(Object handler, EventScope scope) {
    scopedEventBus.inScope(scope).register(handler);
    if (scope == EventScope.API_PLUGIN || scope == EventScope.JAR_PLUGIN) {
      synchronized (testMessageSubscribers) {
        testMessageSubscribers.add(handler);
      }
    }
    synchronized (scopeEventSubscribers) {
      scopeEventSubscribers.put(scope, handler);
    }
  }

  @Override
  public void run() {
    String oldThreadName = Thread.currentThread().getName();
    Thread.currentThread().setName("job-runner-" + jobInfo.locator().getId());
    running = true;
    jobInfo.status().set(TestStatus.RUNNING);
    Throwable jobError = null;
    boolean isDeviceAllocatorSetUp = false;
    Optional<ExceptionDetail> failFastError = Optional.empty();
    String jobTag = jobInfo.locator().getId();
    jobInfo.log().atInfo().alsoTo(logger).log("Job started");
    try {
      boolean skipJob;
      try (MobileHarnessAutoCloseable preRunJobSpan = getPreRunJobSpan()) {
        skipJob = preRunJob();
      }

      jobInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Pre run job finished%s", skipJob ? " and the job is skipped by plugins" : "");
      if (!skipJob) {
        // Checks whether there is test generated.
        if (jobInfo.tests().isEmpty()) {
          throw new MobileHarnessException(
              InfraErrorId.CLIENT_JR_JOB_START_WITHOUT_TEST,
              "No test specified/generated for the job. Please specify the tests you need to run.");
        }

        // Prints the job detail after the files/parameters/dimensions/tests are finalized.
        printJobDetail();

        // Actually starts the job.
        Instant startDeviceAllocationTime = clock.instant();
        Instant expireTime =
            startDeviceAllocationTime.plusMillis(
                jobInfo.setting().getTimeout().getStartTimeoutMs());

        // The time to run allocation diagnostic. It should be a short while before job allocation
        // timeout.
        Instant diagnosticTime =
            expireTime.minus(
                min(
                    ALLOCATION_DIAGNOSE_INTERVAL_BEFORE_JOB_TIMEOUT.multipliedBy(
                        MAX_ALLOCATION_DIAGNOSE_TIMES_BEFORE_JOB_TIMEOUT - 1),
                    Duration.ofMillis(jobInfo.setting().getTimeout().getStartTimeoutMs() / 2)));
        failFastError = deviceAllocator.setUp();
        if (failFastError.isPresent()) {
          return;
        }

        isDeviceAllocatorSetUp = true;
        Instant nextCheckNewTestTime = clock.instant();
        int countPollAllocation = 0;
        Instant nextPollAllocationTime = getNextPollAllocationTime(countPollAllocation);
        // Uses this to log allocation start point of retry tests incrementally.
        Set<String> loggedTests = new HashSet<>();

        SuitableDeviceChecker suitableDeviceChecker = new SuitableDeviceChecker();
        // Don't check whether there's potential suitable device for tests in M&M. b/124489785
        if (Value.POOL_SHARED.equals(jobInfo.dimensions().get(Name.POOL))) {
          suitableDeviceChecker.setHasFoundPotentialSuitableDevice();
        }
        try (MobileHarnessAutoCloseable runAllTestsSpan = getRunAllTestsSpan()) {
          while (true) {
            for (TestInfo testInfo : jobInfo.tests().getAll().values()) {
              String testId = testInfo.locator().getId();
              if (loggedTests.contains(testId)) {
                continue;
              }
              jobInfo.log().atInfo().alsoTo(logger).log("Device allocation started");
              loggedTests.add(testId);
            }
            if (jobInfo.tests().allDone() && !testManager.isAnyTestRunning()) {
              jobInfo.log().atInfo().alsoTo(logger).log("Job done");
              break;
            }
            if (jobInfo.timer().isExpired()) {
              jobInfo.log().atWarning().alsoTo(logger).log("Job expired");
              jobInfo
                  .result()
                  .toNewResult()
                  .setNonPassing(
                      Test.TestResult.TIMEOUT,
                      new MobileHarnessException(
                          InfraErrorId.CLIENT_JR_JOB_EXPIRED, "Job timeout"));
              break;
            }
            Instant beforeSleep = clock.instant();
            sleeper.sleep(BASE_INTERVAL);
            if (clock.instant().isAfter(beforeSleep.plus(BASE_INTERVAL.multipliedBy(4)))) {
              logger.atInfo().log(
                  "Sleep too long for %d ms in JobRunner.run. Before: %s, After %s.",
                  Duration.between(beforeSleep, clock.instant()).toMillis(),
                  beforeSleep,
                  clock.instant());
            }
            if (clock.instant().isBefore(nextPollAllocationTime)) {
              continue;
            }
            countPollAllocation++;
            nextPollAllocationTime = getNextPollAllocationTime(countPollAllocation);

            for (AllocationWithStats allocationWithStats : deviceAllocator.pollAllocations()) {
              Allocation allocation = allocationWithStats.allocation();
              logger.atInfo().log("Allocation: %s", allocation);
              // Double checks the allocation.
              ImmutableList<DeviceLocator> deviceLocators = allocation.getAllDevices();
              TestLocator testLocator = allocation.getTest();
              if (!testLocator.jobLocator().id().equals(jobInfo.locator().getId())) {
                String error = "Receive allocation which doesn't belong to this job: " + allocation;
                logger.atSevere().log("%s", error);
                jobInfo.errors().addAndLog(ErrorCode.JOB_EXEC_ERROR, error);
                continue;
              }

              String testId = testLocator.id();
              TestInfo testInfo = jobInfo.tests().getById(testId);
              if (testInfo == null) {
                String error = "Test of the allocation not found: " + allocation;
                logger.atSevere().log("%s", error);
                jobInfo.errors().addAndLog(ErrorCode.JOB_EXEC_ERROR, error);
                continue;
              }

              // Doesn't need to start the test if the test of the allocation is already running.
              if (testManager.isTestRunning(allocation)) {
                jobInfo
                    .errors()
                    .addAndLog(
                        ErrorCode.JOB_EXEC_ERROR,
                        "Ignore allocation "
                            + allocation
                            + " because the test is already running on the device",
                        logger);
              } else {
                // Copy the allocation stats to test properties.
                testInfo.properties().addAll(allocationWithStats.stats());

                // Records the allocation time of the test.
                Duration allocationTime =
                    Duration.between(
                        getCorrectAllocationStartTime(startDeviceAllocationTime, testInfo),
                        clock.instant());
                testInfo
                    .properties()
                    .add(
                        PropertyName.Test.ALLOCATION_TIME_MS,
                        Long.toString(allocationTime.toMillis()));
                String allocationTimeSec = String.valueOf(allocationTime.getSeconds());
                testInfo.properties().add(PropertyName.Test.ALLOCATION_TIME_SEC, allocationTimeSec);
                jobInfo.log().atInfo().alsoTo(logger).log("Device allocation finished");

                try (MobileHarnessAutoCloseable allocateDeviceSpan =
                    getAllocateDeviceSpan(startDeviceAllocationTime, testInfo)) {
                  // Does nothing.
                }

                if (!hasAllocation) {
                  jobInfo
                      .properties()
                      .add(PropertyName.Job.FIRST_TEST_ALLOCATION_TIME_SEC, allocationTimeSec);
                }

                // Creates TestRunner with the allocation.
                hasAllocation = true;
                suitableDeviceChecker.setHasFoundPotentialSuitableDevice();
                jobInfo
                    .log()
                    .atInfo()
                    .alsoTo(logger)
                    .log("Allocated devices %s for test %s", deviceLocators, testId);
                testInfo.status().set(TestStatus.ASSIGNED);
                try {
                  // Passing plugins(INTERNAL_PLUGIN + API_PLUGIN + JAR_PLUGIN) to test runner
                  // thread.
                  DirectTestRunnerSetting setting =
                      DirectTestRunnerSetting.create(
                          testInfo,
                          new com.google.wireless.qa.mobileharness.shared.model.allocation
                              .Allocation(allocation),
                          scopedEventBus.inScope(EventScope.GLOBAL_INTERNAL),
                          scopeEventSubscribers.get(EventScope.INTERNAL_PLUGIN),
                          scopeEventSubscribers.get(EventScope.API_PLUGIN),
                          scopeEventSubscribers.get(EventScope.JAR_PLUGIN));
                  DirectTestRunner testRunner =
                      execMode.createTestRunner(setting, testManagerThreadPool);

                  // Subscribes test messages of the test.
                  synchronized (testMessageSubscribers) {
                    for (Object testMessageSubscriber : testMessageSubscribers) {
                      testRunner.registerTestEventSubscriber(
                          testMessageSubscriber, DirectTestRunner.EventScope.TEST_MESSAGE);
                    }
                  }

                  // Starts the test.
                  testManager.startTest(testRunner);
                } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
                  TestResult result = ResultUtil.getResultByException(e);
                  if (e instanceof MobileHarnessException) {
                    testInfo
                        .result()
                        .toNewResult()
                        .setNonPassing(
                            Result.upgradeTestResult(result), (MobileHarnessException) e);
                  } else {
                    testInfo
                        .result()
                        .toNewResult()
                        .setNonPassing(
                            Result.upgradeTestResult(result),
                            new MobileHarnessException(
                                InfraErrorId.CLIENT_JR_TEST_START_ERROR,
                                "Revert allocation "
                                    + allocation
                                    + " because failed to start the test on the devices",
                                e));
                  }
                  testInfo
                      .log()
                      .atWarning()
                      .withCause(e)
                      .alsoTo(logger)
                      .log(
                          "Revert allocation %s because failed to start the test on the devices",
                          allocation);
                  deviceAllocator.releaseAllocation(allocation, result, true);
                }
              }
            }
            if (!hasAllocation) {
              Instant now = clock.instant();
              if (expireTime.isBefore(now)) {
                jobInfo
                    .log()
                    .atWarning()
                    .alsoTo(logger)
                    .log(
                        "Timeout because no device is allocated after start_timeout %s seconds.",
                        jobInfo.setting().getTimeout().getStartTimeoutMs() / 1000);
                onJobStartTimeout(isDeviceAllocatorSetUp, true);
              } else if (diagnosticTime.isBefore(now)) {
                diagnosticTime =
                    diagnosticTime.plus(ALLOCATION_DIAGNOSE_INTERVAL_BEFORE_JOB_TIMEOUT);
                diagnose(false);
              }
              if (jobInfo.setting().getAllocationExitStrategy() != AllocationExitStrategy.NORMAL) {
                suitableDeviceChecker.check();
              }
            }
            if (nextCheckNewTestTime.isBefore(clock.instant())) {
              int newTestCount = jobInfo.tests().getNewTestCount();
              if (newTestCount > 0) {
                jobInfo
                    .log()
                    .atInfo()
                    .alsoTo(logger)
                    .log("%s tests waiting for device allocation", newTestCount);
              }
              int suspendedTestCount = jobInfo.tests().getSuspendedTestCount();
              if (suspendedTestCount > 0) {
                jobInfo
                    .log()
                    .atInfo()
                    .alsoTo(logger)
                    .log("%s tests suspended due to quota issues.", suspendedTestCount);
              }
              nextCheckNewTestTime = nextCheckNewTestTime.plus(CHECK_NEW_TESTS_INTERVAL);
            }
          }
        }
      }
    } catch (JobCancelledException e) {
      // Job is killed by user from FE. It only happens in remote mode.
      jobInfo.resultWithCause().setNonPassing(Test.TestResult.FAIL, e);
      jobInfo.tests().getAll().values().stream()
          .filter(testInfo -> testInfo.status().get() != TestStatus.DONE)
          .forEach(
              testInfo -> {
                testInfo.status().set(TestStatus.DONE);
                testInfo.resultWithCause().setNonPassing(Test.TestResult.FAIL, e);
              });
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      if (jobInfo.params().getBool(JobInfo.PARAM_IGNORE_NOT_ASSIGNED_TESTS, false)
          && e.getErrorCode() == ErrorCode.DEVICE_NOT_FOUND.code()) {
        jobInfo.log().atInfo().alsoTo(logger).log("Ignoring: %s", e);
      } else {
        jobError = e;
        jobInfo.errors().addAndLog(e, logger);
      }
    } catch (InterruptedException e) {
      jobError = e;
      Thread.currentThread().interrupt();
      jobInfo
          .errors()
          .addAndLog(
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_JOB_EXEC_INTERRUPTED, "Job interrupted", e),
              logger);
    } catch (Throwable t) {
      // For safety, catches all unknown error.
      jobError = t;
      logger.atSevere().withCause(t).log("FATAL JOB ERROR");
      jobInfo
          .errors()
          .addAndLog(
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_JOB_EXEC_FATAL_ERROR, "Fatal job error", t));
    } finally {
      if (isDeviceAllocatorSetUp) {
        tearDownAllocator();
      }
      try (MobileHarnessAutoCloseable postRunJobSpan = getPostRunJobSpan()) {
        postRunJob(jobError, failFastError, isDeviceAllocatorSetUp);
      }
      Thread.currentThread().setName(oldThreadName);
    }
  }

  /* Gets the correct test allocation start time. When a test retries, the test allocation time is
   * different from the job allocation start time.
   **/
  protected static Instant getCorrectAllocationStartTime(
      Instant jobStartDeviceAllocationTime, TestInfo testInfo) {
    return jobStartDeviceAllocationTime.isBefore(testInfo.timing().getCreateTime())
        ? testInfo.timing().getCreateTime()
        : jobStartDeviceAllocationTime;
  }

  /**
   * Gets the test message poster by the test id.Returns {@link Optional#empty} by default in the
   * core logic.
   */
  public Optional<TestMessagePoster> getTestMessagePoster(String testId) {
    return Optional.empty();
  }

  DeviceAllocator getDeviceAllocator() {
    return deviceAllocator;
  }

  /** Gets the span to be used in pre run job logic, empty by default. */
  protected MobileHarnessAutoCloseable getPreRunJobSpan() {
    return new MobileHarnessAutoCloseable();
  }

  /** Gets the span to be used in run all tests logic, empty by default. */
  protected MobileHarnessAutoCloseable getRunAllTestsSpan() {
    return new MobileHarnessAutoCloseable();
  }

  /** Gets the span to be used in allocate device logic, empty by default. */
  protected MobileHarnessAutoCloseable getAllocateDeviceSpan(
      Instant startDeviceAllocationTime, TestInfo testInfo) {
    return new MobileHarnessAutoCloseable();
  }

  /** Gets the span to be used in post run job logic, empty by default. */
  protected MobileHarnessAutoCloseable getPostRunJobSpan() {
    return new MobileHarnessAutoCloseable();
  }

  /** Adds the trace related properties to the job, does nothing by default in the core logic. */
  protected void addTracePropertiesToJob(JobInfo jobInfo) {}

  /** Resolves running files of the job, does nothing by default in the core logic. */
  protected void resolveJobFiles(JobInfo jobInfo)
      throws MobileHarnessException, InterruptedException {}

  private void printJobDetail() {
    Joiner listJoiner = Joiner.on("\n- ");
    MapJoiner mapJoiner = listJoiner.withKeyValueSeparator("=");
    StringBuilder buf = new StringBuilder("Job detail: \n");
    buf.append(String.format("ID:\t%s\n", jobInfo.locator().getId()));
    buf.append(String.format("NAME:\t%s\n", jobInfo.locator().getName()));
    if (!jobInfo.dimensions().isEmpty()) {
      buf.append(
          String.format("\nDIMENSIONS:\n- %s\n", mapJoiner.join(jobInfo.dimensions().getAll())));
    }
    if (!jobInfo.params().isEmpty()) {
      buf.append(String.format("\nPARAMETERS:\n- %s\n", mapJoiner.join(jobInfo.params().getAll())));
    }
    if (!jobInfo.files().isEmpty()) {
      buf.append(
          String.format("\nFILES:\n- %s\n", mapJoiner.join(jobInfo.files().getAll().asMap())));
    }
    if (!jobInfo.tests().isEmpty()) {
      buf.append(
          String.format("\nTESTS:\n- %s\n", listJoiner.join(jobInfo.tests().getAll().keys())));
    }
    jobInfo.log().atInfo().alsoTo(logger).log("%s", buf.toString());
  }

  /**
   * Does the following things:
   *
   * <ol>
   *   <li>updates job properties
   *   <li>validates job
   *   <li>resolves non-local files
   *   <li>registers client plugin
   *   <li>posts {@link JobStartEvent}
   * </ol>
   *
   * @return if the job should be skipped. If it is {@code true}, the job and top-level test results
   *     should also be set.
   */
  @SuppressWarnings("LogAndThrow")
  private boolean preRunJob()
      throws com.google.wireless.qa.mobileharness.shared.MobileHarnessException,
          InterruptedException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    boolean skipJob = false;
    try {
      addTracePropertiesToJob(jobInfo);
      // TODO: Not do validation check for ait-triggered test to reduce binary size.
      // Explicitly disable job validation for ACID jobs as we don't want to introduce driver deps
      // in the acid frontend binary.
      if (!"ait".equals(jobInfo.properties().get("client")) && !jobInfo.params().has("acid_id")) {
        jobValidator.validateJob(jobInfo);
      }
      resolveJobFiles(jobInfo);

      jobInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Loading client jar plugins for job %s", jobInfo.locator().getId());
      final PluginCreator loader =
          pluginLoaderFactory.create(
              jobInfo.files().get(JobInfo.TAG_CLIENT_PLUGIN),
              jobInfo.params().getList(JobInfo.PARAM_CLIENT_PLUGIN, null),
              jobInfo.params().getList(JobInfo.PARAM_CLIENT_PLUGIN_MODULES, null),
              jobInfo.params().get(JobInfo.PARAM_CLIENT_PLUGIN_FORCE_LOAD_FROM_JAR_CLASS_REGEX),
              PluginType.CLIENT,
              jobInfo.log(),
              new AbstractModule() {

                @Override
                public void configure() {
                  bind(ExecMode.class).toInstance(execMode);
                }
              });
      if (loader.load()) {
        registerEventHandler(
            new Object() {
              @Subscribe
              public void onJobEnded(JobEndEvent event) {
                loader.close();
                jobInfo
                    .log()
                    .atInfo()
                    .alsoTo(logger)
                    .log("Closed jar plugin class loader of job %s", event.getJob().locator());
              }
            },
            EventScope.CLASS_INTERNAL);

        int pluginIndex = 0;
        for (Object plugin : loader.getPlugins()) {
          jobInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Loaded jar plugin: %s for job %s",
                  plugin.getClass().getCanonicalName(), jobInfo.locator().getId());
          registerEventHandler(plugin, EventScope.JAR_PLUGIN);

          // Add client plugin class name into JobInfo property for plugin usage analysis.
          jobInfo
              .properties()
              .add(
                  JobInfo.PARAM_CLIENT_PLUGIN + "_" + pluginIndex++,
                  plugin.getClass().getCanonicalName());
        }
      }

      long preRunJobTimeMs = stopwatch.stop().elapsed().toMillis();
      jobInfo
          .properties()
          .add(PropertyName.Job.PRE_RUN_JOB_TIME_MS, Long.toString(preRunJobTimeMs));
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to preRun job.");
      throw e;
    } finally {
      if (!jobInfo.properties().getBoolean(PropertyName.Job._IS_RESUMED_JOB).orElse(false)) {
        JobStartEvent jobStartEvent = new JobStartEvent(jobInfo);
        scopedEventBus.post(
            jobStartEvent,

            // Event handlers in CLASS_INTERNAL:
            EventScope.CLASS_INTERNAL,

            // Event handlers in GLOBAL_INTERNAL:
            // 1) TestLister: must run after AndroidBuildFetcher.
            // 2) MossUploader.
            // 3) MhTestDiagnosticsReporter
            EventScope.GLOBAL_INTERNAL,

            // Event handlers in INTERNAL_PLUGIN:
            // 1) JobSuspender.
            // 2) DeviceRequirementsMatcher.
            EventScope.INTERNAL_PLUGIN,

            // Event handlers in API_PLUGIN:
            // 1) Client plugins added via MobileHarnessClientApi.registerJobEventHandler(Object).
            // 2) Client plugins added via MobileHarnessClientApi.startJob(...).
            // 3) TestRetryHandler: must run after TestLister, before deviceAllocator.setUp().
            EventScope.API_PLUGIN,

            // Event handlers in JAR_PLUGIN:
            // 1) Client plugins specified via "client_plugin_jar" file.
            EventScope.JAR_PLUGIN);
        skipJob = checkPluginExceptions(false /* postRunJob */);
      } else {
        logger.atInfo().log("Skip sending JobStartEvent because it is a resumed job");
      }
    }
    return skipJob;
  }

  /**
   * Tears down the allocator. This method catches all exceptions of its operations to make sure the
   * postRunJob operations are executed. It is OK to swallow InterruptedException within this
   * method, the job thread is ending soon.
   */
  private void tearDownAllocator() {
    logger.atFine().log("Tear down allocator");
    try {
      deviceAllocator.tearDown();
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      jobInfo.errors().addAndLog(e, logger);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      jobInfo
          .errors()
          .addAndLog(
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_JOB_TEAR_DOWN_ALLOCATOR_INTERRUPTED,
                  "Job interrupted when tearing down allocator",
                  e),
              logger);
    } catch (Throwable t) {
      // For safety, prints out the unknown error.
      logger.atSevere().withCause(t).log("FATAL job error when tearing down allocator");
      jobInfo
          .errors()
          .addAndLog(
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_JOB_TEAR_DOWN_ALLOCATOR_FATAL_ERROR,
                  "FATAL job error when tearing down allocator",
                  t));
    }
  }

  private void postRunJob(
      @Nullable Throwable jobError,
      Optional<ExceptionDetail> failFastError,
      boolean isDeviceAllocatorSetUp) {
    try {
      logger.at(jobError == null ? Level.INFO : Level.WARNING).log(
          "Job runner post run job%s", jobError == null ? "" : " with exception " + jobError);
      // Catches all Exception of each operations below to make sure the all operations are
      // executed. It is OK to swallow InterruptedException here, the job thread is ending soon.
      logger.atInfo().log("Shutdown test thread pool");
      try {
        testManagerThreadPool.shutdownNow();
        testManagerThreadPool.awaitTermination(TERMINATE_TEST_TIMEOUT_MS, MILLISECONDS);
      } catch (InterruptedException e) {
        jobInfo
            .errors()
            .addAndLog(
                new MobileHarnessException(
                    InfraErrorId.CLIENT_JR_JOB_SHUT_DOWN_THRAD_POOL_INTERRUPTED,
                    String.format(
                        "Failed to terminate test thread pool of job %s within %d ms",
                        jobInfo.locator(), TERMINATE_TEST_TIMEOUT_MS),
                    e),
                logger);
      } catch (RuntimeException e) {
        String errorMessage = "Failed to shutdown test thread pool";
        logger.atWarning().withCause(e).log("%s", errorMessage);
        jobInfo
            .errors()
            .addAndLog(
                new MobileHarnessException(
                    InfraErrorId.CLIENT_JR_JOB_SHUT_DOWN_THREAD_POOL_FATAL_ERROR, errorMessage, e));
      }

      logger.atInfo().log("Finalize job");
      try {
        finalizeJobResult(failFastError, isDeviceAllocatorSetUp, false, jobError);
      } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
        jobInfo.errors().addAndLog(e, logger);
      } catch (Throwable e) {
        String errorMessage = "Failed to finalize job";
        logger.atWarning().withCause(e).log("%s", errorMessage);
        jobInfo
            .errors()
            .addAndLog(
                new MobileHarnessException(
                    InfraErrorId.CLIENT_JR_JOB_FINALIZE_RESULT_FATAL_ERROR, errorMessage, e));
      }
      jobInfo.status().set(TestStatus.DONE);
      jobInfo
          .properties()
          .add(
              Job.JOB_LINK_IN_MHFE,
              String.format("http://mobileharness-fe/jobdetailview/%s", jobInfo.locator().getId()));

      logger.atFine().log("Post JobEndEvent");
      try {
        JobEndEvent jobEndEvent = new JobEndEvent(jobInfo, jobError);
        scopedEventBus.post(
            jobEndEvent,
            // Event handlers in JAR_PLUGIN:
            // 1) Client plugins specified via "client_plugin_jar" file.
            EventScope.JAR_PLUGIN,

            // Event handlers in API_PLUGIN:
            // 1) Client plugins added via MobileHarnessClientApi.registerJobEventHandler(Object):
            //    a. MhClientSpongeUploader
            // 2) Client plugins added via MobileHarnessClientApi.startJob(...).
            EventScope.API_PLUGIN,

            // Event handlers in INTERNAL_PLUGIN:
            EventScope.INTERNAL_PLUGIN,

            // Event handlers in GLOBAL_INTERNAL:
            // 1) JobReporter
            // 2) GoogleAnalyticsUploader
            // 3) GenFileHandler
            // 4) MossUploader: should run after API_PLUGIN to catch changes in client.
            // 5) StreamzStats: reports metric values to Streamz
            // 6) MhTestDiagnosticsReporter
            EventScope.GLOBAL_INTERNAL,

            // Event handlers in CLASS_INTERNAL:
            // 1) Close PluginCreator: run after JAR_PLUGIN/GLOBAL_INTERNAL to make sure all client
            //    plugins are done. Otherwise, client plugins won't be able to load classes.
            EventScope.CLASS_INTERNAL);
        checkPluginExceptions(true /* postRunJob */);
      } catch (RuntimeException e) {
        String errorMessage = "Failed to post JobEndEvent";
        logger.atWarning().withCause(e).log("%s", errorMessage);
        jobInfo
            .errors()
            .addAndLog(
                new MobileHarnessException(
                    InfraErrorId.CLIENT_JR_JOB_END_EVENT_POST_FATAL_ERROR,
                    errorMessage + ": " + e.getMessage()));
      }

      // Removes the run file dirs of the job.
      JobSetting setting = jobInfo.setting();
      if (setting.hasRunFileDir()) {
        removePath(setting.getRunFileDir(), "run");
      }

      // Removes the gen file dirs of the job if flag is enabled.
      if (Flags.instance().removeJobGenFilesWhenFinished.getNonNull() && setting.hasGenFileDir()) {
        removePath(setting.getGenFileDir(), "gen");
      }

      // Removes the tmp file dirs of the job.
      if (setting.hasTmpFileDir()) {
        removePath(setting.getTmpFileDir(), "tmp");
      }
    } catch (Throwable e) {
      // For safety, prints out the unknown error.
      logger.atSevere().withCause(e).log("FATAL ERROR");
      jobInfo
          .errors()
          .addAndLog(
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_JOB_TEAR_DOWN_FATAL_ERROR,
                  "Fatal job error when tearing down job: ",
                  e));
    } finally {
      running = false;
      jobInfo.log().atInfo().alsoTo(logger).log("Job stopped");
    }
  }

  private void removePath(String path, String dirType) {
    logger.atInfo().log("Clear job %s-file dir", dirType);
    try {
      fileUtil.grantFileOrDirFullAccessRecursively(path);
      fileUtil.removeFileOrDir(path);
    } catch (Exception e) {
      logger.atWarning().withCause(e).log("Failed to remove %s-file dir %s", dirType, path);
      jobInfo
          .errors()
          .addAndLog(
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_JOB_CLEAN_UP_DIR_ERROR,
                  "Failed to remove " + dirType + "-file dir",
                  e),
              logger);
    }
  }

  /**
   * When job has no allocation when start_timeout is reached, records the error and throws out an
   * exception.
   */
  private void onJobStartTimeout(boolean isDeviceAllocatorSetUp, boolean isStartTimeoutExpired)
      throws MobileHarnessException, InterruptedException {
    com.google.wireless.qa.mobileharness.shared.MobileHarnessException suppressed = null;
    try {
      finalizeJobResult(Optional.empty(), isDeviceAllocatorSetUp, !isStartTimeoutExpired, null);
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      suppressed = e;
    }

    ErrorId errorId = InfraErrorId.CLIENT_JR_ALLOC_UNKNOWN_ERROR;
    String errorMsg = "Failed to allocate any device";

    if (ClientAllocErrorUtil.isJobAllocError(jobInfo)) {
      if (jobInfo.tests().getAll().values().stream()
          .allMatch(
              testInfo ->
                  testInfo.warnings().getAll().stream()
                      .anyMatch(
                          exceptionDetail ->
                              exceptionDetail.getSummary().getErrorId().getCode()
                                  == InfraErrorId.CLIENT_JR_ALLOC_INFRA_ERROR.code()))) {
        errorId = InfraErrorId.CLIENT_JR_ALLOC_INFRA_ERROR;
        errorMsg += " due to Mobile Harness infra issues";
      }
    } else if (ClientAllocErrorUtil.isJobAllocFail(jobInfo)) {
      errorId = InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR;
      errorMsg +=
          " due to your job config or device capacity issues. Please make sure there "
              + "are available devices that can meet all your job requirements";
    }
    errorMsg += ". Check test warnings for more detail.";
    jobInfo
        .properties()
        .add(
            PropertyName.Job.ALLOCATION_FAIL_AFTER_START_TIMEOUT,
            Boolean.toString(isStartTimeoutExpired));
    MobileHarnessException exception = new MobileHarnessException(errorId, errorMsg);
    if (suppressed != null) {
      exception.addSuppressed(suppressed);
    }
    throw exception;
  }

  /*
   * Finalize the job result. A job with no test will be marked as ERROR.
   */
  @VisibleForTesting
  void finalizeJobResult(
      Optional<ExceptionDetail> failFastError,
      boolean isDeviceAllocatorSetUp,
      boolean noPerfectCandidate,
      @Nullable Throwable jobError)
      throws MobileHarnessException, InterruptedException {
    if (jobInfo.result().get() != TestResult.UNKNOWN) {
      // Don't override the existing result, like TIMEOUT.
      return;
    }
    boolean hasErrorTests = false;
    boolean hasInfraErrorTests = false;
    boolean hasFailTests = false;
    boolean hasAllocFailTests = false;
    boolean hasAllocErrorTests = false;
    boolean hasSuspendedTests = false;
    boolean hasNotStartedTests = false;
    int skipTestCount = 0;
    int testCount = 0;
    String testIdForDisplayMhfeLink = "";
    for (TestInfo testInfo : jobInfo.tests().getFinalized().values()) {
      testCount++;
      switch (testInfo.status().get()) {
        case NEW:
          if (isDeviceAllocatorSetUp) {
            if (jobInfo.params().getBool(JobInfo.PARAM_IGNORE_NOT_ASSIGNED_TESTS, false)) {
              jobInfo
                  .log()
                  .atInfo()
                  .alsoTo(logger)
                  .log("%s: ignore device allocation error.", jobInfo.locator().getId());
            } else {
              // Adds test not assigned error for unfinished tests.
              String errMsg = "Test failed to allocate devices. ";
              String regexWarning = getRegexWarning(jobInfo);
              if (!regexWarning.isEmpty()) {
                jobInfo.log().atInfo().alsoTo(logger).log("%s", regexWarning);
              }

              // In case the start_timeout is extremely short, like 4s, and somehow we failed to run
              // the diagnostic report before job start timeout, here we give it another chance to
              // run the diagnostic report after job end.
              Optional<Report> diagnosticReport;
              do {
                diagnosticReport = diagnose(noPerfectCandidate);
              } while (diagnosticTimes < MAX_ALLOCATION_DIAGNOSE_TIMES);

              ErrorId errorId = InfraErrorId.CLIENT_JR_ALLOC_UNKNOWN_ERROR;
              MobileHarnessException cause = null;
              if (diagnosticReport.isPresent()) {
                errorId = diagnosticReport.get().getResult().errorId();
                if (errorId == InfraErrorId.CLIENT_JR_ALLOC_INFRA_ERROR) {
                  allocDiagnostician.logExtraInfo();
                }
                String diagnosticResult = diagnosticReport.get().getResult().readableReport();
                if (errorId == InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR) {
                  hasAllocFailTests = true;
                }
                errMsg += String.format("Diagnostic result: %s:\n%s\n", errorId, diagnosticResult);
                cause = diagnosticReport.get().getResult().cause();
              }

              errMsg += TEST_NOT_ASSIGNED_ERROR_MSG;
              testInfo
                  .errors()
                  .toWarnings()
                  .addAndLog(errorId, jobInfo.locator().getId() + ": " + errMsg, logger);
              hasAllocErrorTests = !hasAllocFailTests;
              testInfo
                  .result()
                  .toNewResult()
                  .setNonPassing(
                      Test.TestResult.ERROR, new MobileHarnessException(errorId, errMsg, cause));
              logAllocUserConfigErrorCauseToProperty(testInfo, errorId, cause);
            }
          } else if (failFastError.isPresent()) {
            hasAllocFailTests = true;
            testInfo
                .result()
                .toNewResult()
                .setNonPassing(
                    Test.TestResult.ERROR,
                    ErrorModelConverter.toMobileHarnessException(failFastError.get()));
          } else {
            if (!jobInfo.result().get().equals(TestResult.PASS)) {
              testInfo
                  .result()
                  .toNewResult()
                  .setNonPassing(
                      Test.TestResult.ERROR,
                      new MobileHarnessException(
                          InfraErrorId.CLIENT_JR_TEST_HAS_JOB_LEVEL_ERROR,
                          "Job has infra errors. Check job level error for more detail.",
                          jobError));
            }
            hasNotStartedTests = true;
          }
          break;
        case SUSPENDED:
          ErrorId errorId = InfraErrorId.CLIENT_JR_MNM_ALLOC_DEVICE_EXCEEDS_CEILING;
          String errMsg = "Test is suspended for quota issues. " + TEST_NOT_ASSIGNED_ERROR_MSG;
          testInfo
              .errors()
              .toWarnings()
              .addAndLog(errorId, jobInfo.locator().getId() + ": " + errMsg, logger);
          testInfo
              .result()
              .toNewResult()
              .setNonPassing(Test.TestResult.ERROR, new MobileHarnessException(errorId, errMsg));
          hasSuspendedTests = true;
          break;
        case ASSIGNED:
        case RUNNING:
        case DONE:
          switch (testInfo.result().get()) {
            case PASS:
              break;
            case SKIP:
              skipTestCount++;
              break;
            case FAIL:
              hasFailTests = true;
              testIdForDisplayMhfeLink = testInfo.locator().getId();
              break;
            case INFRA_ERROR:
              hasInfraErrorTests = true;
              testIdForDisplayMhfeLink = testInfo.locator().getId();
              break;
            case ALLOC_FAIL:
              // UMTS does allocation as part of test
              hasAllocFailTests = true;
              testIdForDisplayMhfeLink = testInfo.locator().getId();
              break;
            case ERROR:
            case TIMEOUT:
            case UNKNOWN:
              hasErrorTests = true;
              testIdForDisplayMhfeLink = testInfo.locator().getId();
              break;
            default:
              throw new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_TEST_HAS_UNKNOWN_RESULT,
                  "Unknown test result " + testInfo.result().get());
          }
          break;
      }
    }

    if (hasInfraErrorTests) {
      jobInfo
          .result()
          .toNewResult()
          .setNonPassing(
              Test.TestResult.INFRA_ERROR,
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_JOB_HAS_INFRA_ERROR_TEST,
                  "Job has >=1 INFRA_ERROR test(s). You can get the detailed error info in the test"
                      + " level."));
    } else if (hasErrorTests) {
      if (jobError instanceof MobileHarnessException
          && ((MobileHarnessException) jobError)
              .getErrorId()
              .type()
              .equals(ErrorType.INFRA_ISSUE)) {
        jobInfo
            .resultWithCause()
            .setNonPassing(
                Test.TestResult.ERROR,
                new MobileHarnessException(
                    InfraErrorId.CLIENT_JR_JOB_HAS_INFRA_ERROR_TEST,
                    "Job has >= 1 INFRA_ERROR test(s)",
                    jobError));
      } else {
        jobInfo
            .result()
            .toNewResult()
            .setNonPassing(
                Test.TestResult.ERROR,
                new MobileHarnessException(
                    InfraErrorId.CLIENT_JR_JOB_HAS_ERROR_TEST,
                    "Job has >=1 ERROR test(s). You can get the detailed ERROR info in the test"
                        + " level.",
                    jobError));
      }
    } else if (hasFailTests) {
      jobInfo
          .result()
          .toNewResult()
          .setNonPassing(
              Test.TestResult.FAIL,
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_JOB_HAS_FAIL_TEST,
                  "Job has >=1 FAIL test(s). You can get the detailed FAIL info in the test"
                      + " level."));
    } else if (hasAllocErrorTests) {
      jobInfo
          .result()
          .toNewResult()
          .setNonPassing(
              Test.TestResult.ERROR,
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_JOB_HAS_ALLOC_ERROR_TEST,
                  "Job has >=1 ALLOC ERROR test(s). You can get the detailed ALLOC ERROR info in"
                      + " the test level."));
    } else if (hasAllocFailTests) {
      jobInfo
          .result()
          .toNewResult()
          .setNonPassing(
              Test.TestResult.ERROR,
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_JOB_HAS_ALLOC_FAIL_TEST,
                  "Job has >=1 ALLOC FAIL test(s). You can get the detailed ALLOC FAIL info in the"
                      + " test level."));
    } else if (hasSuspendedTests) {
      jobInfo
          .result()
          .toNewResult()
          .setNonPassing(
              Test.TestResult.ERROR,
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_JOB_HAS_ALLOC_FAIL_TEST,
                  "Job has >= 1 SUSPENDED test(s)."));
    } else if (hasNotStartedTests) {
      if (jobError instanceof MobileHarnessException) {
        jobInfo
            .resultWithCause()
            .setNonPassing(Test.TestResult.ERROR, (MobileHarnessException) jobError);
      } else {
        jobInfo
            .resultWithCause()
            .setNonPassing(
                Test.TestResult.ERROR,
                new MobileHarnessException(
                    InfraErrorId.CLIENT_JR_JOB_EXEC_FATAL_ERROR,
                    "Job has error and the tests are not started."));
      }
    } else if (testCount > 0) {
      if (testCount == skipTestCount) {
        jobInfo
            .result()
            .toNewResult()
            .setNonPassing(
                Test.TestResult.SKIP,
                new MobileHarnessException(
                    InfraErrorId.CLIENT_JR_JOB_HAS_ALL_SKIPPED_TESTS,
                    "ALl tests of the job are skipped"));
      } else {
        jobInfo.result().toNewResult().setPass();
      }
    } else {
      jobInfo
          .result()
          .toNewResult()
          .setNonPassing(
              Test.TestResult.ERROR,
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_JOB_START_WITHOUT_TEST,
                  "No tests of the job are executed"));
    }
  }

  @VisibleForTesting
  static String getRegexWarning(JobInfo jobInfo) {
    return jobInfo.dimensions().getAll().entrySet().stream()
        .filter(
            dimension ->
                dimension.getValue().matches(".*[\\*\\[\\]\\|].*")
                    && !dimension.getValue().startsWith("regex:"))
        .map(
            dimension ->
                String.format(
                    "Dimension %s's value %s may be a regex. If it's a regex,"
                        + " please add regex: before the value.",
                    dimension.getKey(), dimension.getValue()))
        .collect(joining("\n"));
  }

  /**
   * Get the time of next allocation poll.
   *
   * @param countPollAllocation the poll allocation count up to now
   */
  private Instant getNextPollAllocationTime(int countPollAllocation) {
    int multiplier;
    if (jobInfo.tests().getNewTestCount() > 0) {
      if (Boolean.TRUE.equals(Flags.instance().realTimeJob.getNonNull())) {
        if (countPollAllocation < NUM_USE_REAL_TIME_POLL_ALLOCATION_INTERVAL_MULTIPLIER) {
          // Uses the real-time polling allocation interval multiplier when the job starts and
          // switches to the medium one after several attempts.
          multiplier = REAL_TIME_POLL_ALLOCATION_INTERVAL_MULTIPLIER;
        } else {
          multiplier = MEDIUM_POLL_ALLOCATION_INTERVAL_MULTIPLIER;
        }
      } else {
        if (countPollAllocation < NUM_USE_SMALL_POLL_ALLOCATION_INTERVAL_MULTIPLIER) {
          // Uses the small polling allocation interval multiplier when the job starts and switches
          // to the medium one after several attempts.
          multiplier = SMALL_POLL_ALLOCATION_INTERVAL_MULTIPLIER;
        } else {
          multiplier = MEDIUM_POLL_ALLOCATION_INTERVAL_MULTIPLIER;
        }
      }
    } else {
      // No tests are waiting for allocation, further reduce the interval multiplier. If there are
      // retries coming, it may wait a little longer time for its allocation. But consider it can
      // help to reduce many QPS, it is worthy.
      multiplier = LARGE_POLL_ALLOCATION_INTERVAL_MULTIPLIER;
    }
    return clock.instant().plus(BASE_INTERVAL.multipliedBy(multiplier));
  }

  /** Generates diagnostic report if no report is generated before. */
  @CanIgnoreReturnValue
  private Optional<Report> diagnose(boolean noPerfectCandidate) throws InterruptedException {
    diagnosticTimes++;
    try {
      if (allocDiagnostician == null) {
        allocDiagnostician = createAllocationDiagnostician(jobInfo, deviceQuerier);
      }
      if (allocDiagnostician != null) {
        // Double check to guarantee result accurate if previous check finds max score device.
        // b/64825449
        if (allocDiagnostician.getLastReport().isEmpty()
            || (allocDiagnostician.getLastReport().get().hasPerfectMatch()
                && diagnosticTimes < MAX_ALLOCATION_DIAGNOSE_TIMES)) {
          jobInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Diagnose allocation failure of job %s...", jobInfo.locator().getId());
          allocDiagnostician.diagnoseJob(noPerfectCandidate);
          jobInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Successfully generated allocation diagnostic report for job %s",
                  jobInfo.locator().getId());
        }
        return allocDiagnostician.getLastReport();
      }
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      jobInfo
          .errors()
          .addAndLog(
              new MobileHarnessException(
                  InfraErrorId.CLIENT_JR_ALLOC_DIAGNOSTIC_ERROR,
                  "Failed to diagnostic the allocation failure",
                  e),
              logger);
    }
    return Optional.empty();
  }

  @VisibleForTesting
  AllocationDiagnostician createAllocationDiagnostician(
      JobInfo jobInfo, DeviceQuerier deviceQuerier) {
    return jobInfo.subDeviceSpecs().hasMultipleDevices()
        ? new MultiDeviceDiagnostician(jobInfo, deviceQuerier)
        : new SingleDeviceDiagnostician(jobInfo, deviceQuerier);
  }

  /**
   * Check plugin exceptions to determine if the job should be skipped. If so, also sets the job and
   * top-level test results.
   *
   * <p>It should be invoked right after an event is posted.
   *
   * @param postRunJob if it is in post-run-job stage. If so, ignore job skipping and give warnings.
   * @return if the job should be skipped
   */
  @CanIgnoreReturnValue
  private boolean checkPluginExceptions(boolean postRunJob) {
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
            .map(SkipInformationHandler::convertIfSkipJobRunning)
            .flatMap(Streams::stream)
            .collect(toImmutableList());

    if (!skipInfos.isEmpty()) {
      SkipResultWithCause skipResultWithCause = SkipInformationHandler.getJobResult(skipInfos);
      if (postRunJob) {
        jobInfo
            .errors()
            .addAndLog(
                ErrorCode.PLUGIN_ERROR,
                String.format(
                    "Plugins want to skip job and set job result but it is ignored "
                        + "because the job has run. The job result will NOT be changed as "
                        + "the desired job result in the exceptions. SkipJobException only "
                        + "works in JobStartEvent. If you just want to change job result"
                        + ", please call jobInfo.result().set() directly in your plugin. "
                        + "See go/mh-plugin-skip-job. Detail: %s",
                    skipResultWithCause.report()),
                logger);
      } else {
        jobInfo.log().atInfo().alsoTo(logger).log("%s", skipResultWithCause.report());

        if (skipResultWithCause.resultWithCause().type().equals(Test.TestResult.PASS)) {
          jobInfo.resultWithCause().setPass();
          for (TestInfo testInfo : jobInfo.tests().getAll().values()) {
            testInfo.resultWithCause().setPass();
          }
        } else {
          jobInfo
              .resultWithCause()
              .setNonPassing(
                  skipResultWithCause.resultWithCause().type(),
                  ErrorModelConverter.toCommonExceptionDetail(
                      skipResultWithCause.resultWithCause().causeNonEmpty()));
          for (TestInfo testInfo : jobInfo.tests().getAll().values()) {
            testInfo
                .resultWithCause()
                .setNonPassing(
                    skipResultWithCause.resultWithCause().type(),
                    skipResultWithCause.resultWithCause().causeExceptionNonEmpty());
          }
        }
        return true;
      }
    }
    return false;
  }

  private void logAllocUserConfigErrorCauseToProperty(
      TestInfo testInfo, ErrorId errorId, @Nullable MobileHarnessException cause) {
    if (!errorId.equals(InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR)) {
      return;
    }
    if (cause == null) {
      return;
    }
    testInfo
        .properties()
        .add(PropertyName.Test.ALLOCATION_FAILURE_CLASSIFICATION, cause.getErrorId().name());
  }

  /** Checks whether there's potential suitable devices for the job. */
  private class SuitableDeviceChecker {

    private Instant nextQueryDeviceTime;
    private boolean hasFoundPotentialSuitableDevice;
    private int deviceQueryTimes;

    private SuitableDeviceChecker() {
      nextQueryDeviceTime = clock.instant().plus(startQueryDeviceLatency);
    }

    private void setHasFoundPotentialSuitableDevice() {
      hasFoundPotentialSuitableDevice = true;
    }

    private void check()
        throws com.google.wireless.qa.mobileharness.shared.MobileHarnessException,
            InterruptedException {
      if (!hasFoundPotentialSuitableDevice && nextQueryDeviceTime.isBefore(clock.instant())) {
        DeviceQueryResult deviceQueryResult = null;
        try {
          // Calculate next query time before query, and increase query times after query.
          // So when query throws exception, it will still query again until reach max query
          // times.
          nextQueryDeviceTime = nextQueryDeviceTime.plus(queryDeviceInterval);
          deviceQueryResult = deviceQuerier.queryDevice(deviceQueryFilter);
          deviceQueryTimes++;
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log(
              "Failed to query potential suitable device. Ignore the exception.");
        }
        if (deviceQueryResult != null && deviceQueryResult.getDeviceInfoCount() > 0) {
          hasFoundPotentialSuitableDevice = true;
        } else if (deviceQueryTimes >= maxQueryDeviceTimes) {
          jobInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log(
                  "Timeout after %s retries because there is no potential suitable devices",
                  deviceQueryTimes);
          onJobStartTimeout(true, false);
        }
      }
    }
  }
}
