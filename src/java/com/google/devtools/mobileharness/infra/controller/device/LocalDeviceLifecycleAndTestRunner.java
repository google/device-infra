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

package com.google.devtools.mobileharness.infra.controller.device;

import static com.google.devtools.mobileharness.shared.util.error.MoreThrowables.shortDebugStackTrace;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.constant.DeviceProperty;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatusWithTimestamp;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCacheManager;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.infra.controller.device.external.ExternalDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.external.ExternalDeviceManager.DeviceReservation;
import com.google.devtools.mobileharness.infra.controller.device.util.DeviceRebootUtil;
import com.google.devtools.mobileharness.infra.controller.test.event.TestExecutionEndedEvent;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionResult;
import com.google.devtools.mobileharness.shared.util.logging.MobileHarnessLogTag;
import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.annotation.RetainDeviceInfoAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.DeviceFactory;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalDeviceChangeEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalDeviceErrorEvent;
import com.google.wireless.qa.mobileharness.shared.controller.stat.DeviceStat;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/** A runnable class representing the life cycle of a device. */
public class LocalDeviceLifecycleAndTestRunner extends LocalDeviceRunner {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Time interval of two round of checking device change. */
  public static final Duration CHECK_DEVICE_INTERVAL = Duration.ofMinutes(5);

  /** Ask the DeviceInfoManager to keep the device info for this long. */
  public static final Duration DEVICE_INFO_REMOVE_DELAY = Duration.ofMinutes(5L);

  /** Expire time of the runner. Will kill it if any operation(except running test) exceeds this. */
  @VisibleForTesting static final Duration RUNNER_EXPIRE = Duration.ofMinutes(5);

  /** Will interrupt the runner repeatedly by the interval if it was canceled. */
  @VisibleForTesting static final Duration RUNNER_INTERRUPT_INTERVAL = Duration.ofMinutes(20);

  /**
   * Expire time of the tear down process. Will kill the tear down process if any operation(except
   * running test) exceeds this. Deadline of postDeviceChangeEvent before device tear down is 1 min,
   * and extend another 2 mins to make sure device tear down and reboot complete. b/32101092
   */
  private static final Duration TEAR_DOWN_EXPIRE = Duration.ofMinutes(3);

  private static final Duration WAIT_IDLE_IN_EXTERNAL_DEVICE_MANAGER_EXPIRE =
      Duration.ofSeconds(10);

  /** Wait interval in milliseconds when the runner is idle. */
  private static final long WAIT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(10);

  /** The device property to store reservation id. */
  private static final String DEVICE_PROPERTY_RESERVATION_ID = "reservation_id";

  /** The device controlled by this runner. */
  private final Device device;

  /** Device stat statistic data. */
  private final DeviceStat deviceStat;

  /** Config read from file. */
  private final ApiConfig apiConfig;

  private final DeviceRebootUtil deviceRebootUtil;

  // TODO: Reduces the boolean fields and use a DeviceStatus enum field instead.
  /** Whether the device is ready. */
  private volatile boolean initialized = false;

  /** Whether the current thread is running. */
  private volatile boolean running = false;

  /** Whether the current thread is canceled, which means it should stop as quickly as possible. */
  private volatile boolean cancelled = false;

  /** Whether the current thread is tearing down. */
  private volatile boolean tearingDown = false;

  /** The running test on this device. */
  @Nullable private volatile LocalDeviceTestExecutor test = null;

  /** Expire time of this running. */
  private volatile Instant expireTime;

  /** The last time when the device status is check. */
  private volatile Instant lastCheckDeviceTime;

  /** clock for getting the current system time. */
  private final Clock clock;

  /** Multiple event buses to handle events in different scopes with different handlers. */
  private final EventBus globalInternalBus;

  private final Object interruptLock = new Object();

  @GuardedBy("interruptLock")
  @Nullable
  private Thread runningThread;

  @GuardedBy("interruptLock")
  @Nullable
  private Instant lastInterruptTime;

  private final ExternalDeviceManager externalDeviceManager;

  /**
   * Creates a new device runner.
   *
   * @param deviceId The ID of the device
   * @param type device type, which should be a sub-class of {@code BaseDevice}
   * @param stat statistic module for collecting device statistic, null to disable stat collection
   * @param globalInternalBus eventBus to receive device events
   * @throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException if fails to
   *     create the runner
   */
  public LocalDeviceLifecycleAndTestRunner(
      DeviceId deviceId,
      Class<? extends Device> type,
      EventBus globalInternalBus,
      DeviceStat stat,
      ExternalDeviceManager externalDeviceManager)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    apiConfig = ApiConfig.getInstance();

    DeviceInfoManager.getInstance()
        .add(deviceId, apiConfig, type.isAnnotationPresent(RetainDeviceInfoAnnotation.class));

    device = new DeviceFactory().createDevice(type, deviceId.controlId());
    addDeviceIdToDimension(deviceId);
    deviceStat = stat;
    deviceStat.onShowUp();
    clock = Clock.systemUTC();
    extendExpireTime();
    this.globalInternalBus = globalInternalBus;
    this.deviceRebootUtil = new DeviceRebootUtil();
    this.externalDeviceManager = externalDeviceManager;
  }

  /** Creates a new device runner. For test only. */
  @VisibleForTesting
  LocalDeviceLifecycleAndTestRunner(
      DeviceId deviceId,
      Device device,
      DeviceStat stat,
      ApiConfig apiConfig,
      Clock clock,
      Thread runningThread,
      ExternalDeviceManager externalDeviceManager) {
    this.device = device;
    addDeviceIdToDimension(deviceId);
    this.deviceStat = stat;
    this.apiConfig = apiConfig;
    this.clock = clock;
    this.runningThread = runningThread;

    running = true;
    initialized = true;
    extendExpireTime();

    globalInternalBus = new EventBus();
    this.deviceRebootUtil = new DeviceRebootUtil();
    this.externalDeviceManager = externalDeviceManager;
  }

  @Override
  public final void run() {
    running = true; // Must be the first line of run().
    synchronized (interruptLock) {
      runningThread = Thread.currentThread();
    }

    String oldThreadName = Thread.currentThread().getName();
    Thread.currentThread().setName("local-device-runner-" + device.getDeviceId());

    MobileHarnessLogTag.addTag(MobileHarnessLogTag.DEVICE_ID, device.getDeviceId());

    boolean needReboot = false;

    runnerCount.incrementAndGet();
    DeviceReservation deviceReservation = null;
    try {

      logger.atInfo().log("Started");

      // Initializes the device.
      initDevice();
      recordBecomeIdleTime();
      postDeviceChangeEvent("initialized");
      // Keeps running until interrupted.
      while (isReady()) {
        extendExpireTime();
        try {
          // Try to reserve device for 5 seconds. Even it failed, we can try in another iteration.
          deviceReservation =
              externalDeviceManager.reserveDevice(device.getDeviceId(), Duration.ofSeconds(5));
          if (!deviceReservation.getReservationId().isEmpty()) {
            device
                .info()
                .properties()
                .put(DEVICE_PROPERTY_RESERVATION_ID, deviceReservation.getReservationId());
          }
          if (checkDevice()) {
            postDeviceChangeEvent("changed detected");
          }
          if (test != null) {
            deviceReservation.markRunningTest();
          }
          if (checkNRunTest()) {
            needReboot = true;
          }
        } catch (com.google.devtools.mobileharness.api.model.error.MobileHarnessException e) {
          // Need to quit the loop when it's draining; Otherwise, just log the warning.
          if (e.getErrorId() == InfraErrorId.LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_FAIL_WHEN_DRAIN) {
            throw e;
          } else {
            logger.atWarning().withCause(e).log(
                "Failed to reserve device %s or run test.", device.getDeviceId());
          }
        } finally {
          device.info().properties().remove(DEVICE_PROPERTY_RESERVATION_ID);
        }
        if (!isReady()) {
          break;
        }
        // Release the reservation if there's another round of iteration.
        if (deviceReservation != null) {
          deviceReservation.close();
          deviceReservation = null;
        }
        extendExpireTime();
        synchronized (this) {
          wait(WAIT_INTERVAL_MS);
        }
      }
    } catch (InterruptedException e) {
      if (test != null) {
        logger.atInfo().withCause(e).log(
            "Need to reboot the device(if applicable) "
                + "because the device is interrupted when running test");
        needReboot = true;
      } else {
        // Does not print stack trace of InterruptedException since we don't want to print error
        // when normally exiting a server.
        logger.atInfo().log("Device interrupted. No test running at the moment.");
      }
    } catch (Throwable e) {
      logger.atSevere().withCause(e).log("FATAL ERROR");
      needReboot = true;
    } finally {
      // It is for avoiding the thread interrupted when the device is still tear down or reboot.
      // b/30105986.
      tearingDown = true;
      extendExpireTime(TEAR_DOWN_EXPIRE);
      // In case the execution of the latter logic takes too long, sets cancelled to let
      // LocalDeviceManager know the device is down.
      cancelled = true;

      postDeviceChangeEvent("dying");

      // It is safe to tear down the device now.
      // Create a device reservation if there is no existing deviceReservation.
      try (DeviceReservation ignored =
          deviceReservation != null
              ? deviceReservation
              : externalDeviceManager.reserveDevice(
                  device.getDeviceId(), Duration.between(clock.instant(), expireTime))) {
        device.tearDown();
        if (needReboot && !externalDeviceManager.isManagingDeviceLifeCycle()) {
          // TODO: Leaves the device in an unusable state instead of put it in the reboot
          // loop when rebooting doesn't help.
          try {
            if (device.canReboot()) {
              // Reboots the device, tries to recovery.
              logger.atInfo().log("Rebooting device");
              device.reboot();
              deviceStat.onReboot();
              logger.atInfo().log("Rebooted");
            }
          } catch (Exception e) {
            // Catches all exceptions to make sure we will decrease the runnerCount.
            logger.atWarning().withCause(e).log("Failed to reboot device");
            if (e instanceof InterruptedException) {
              Thread.currentThread().interrupt();
            }
          }
        }
      } catch (Exception e) {
        postDeviceErrorEvent(e);
        // Catches all exceptions to make sure we will decrease the runnerCount.
        logger.atWarning().withCause(e).log("Failed to stop device");
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
      }
      tearingDown = false;
      runnerCount.decrementAndGet();
      running = false;
      deviceStat.onDie();

      // Another runner for the same device might be created, delay the removal so that other
      // runners have the opportunity to get device properties.
      DeviceInfoManager.getInstance().removeDelayed(device.getDeviceId(), DEVICE_INFO_REMOVE_DELAY);

      logger.atInfo().log("Stopped");

      Thread.currentThread().setName(oldThreadName);
      synchronized (interruptLock) {
        runningThread = null;
      }
    }
  }

  @Override
  public boolean isAvailable() {
    return isReady() && test == null && !isPrepping();
  }

  /** If the device is IDLE in external device manager, it's of course available in external DM. */
  private boolean isAvailableInExternalDeviceManager() {
    ExternalDeviceManager.DeviceStatus deviceStatus =
        externalDeviceManager.getDeviceStatus(device.getDeviceId());
    return deviceStatus.equals(ExternalDeviceManager.DeviceStatus.IDLE);
  }

  /**
   * @return whether the device is IDLE or NEAR_IDLE in external device manager
   */
  private boolean waitIdleInExternalDeviceManager(Duration timeout) {
    Instant expireTime = clock.instant().plus(timeout);
    while (clock.instant().isBefore(expireTime)) {
      ExternalDeviceManager.DeviceStatus deviceStatus =
          externalDeviceManager.getDeviceStatus(device.getDeviceId());
      if (deviceStatus.equals(ExternalDeviceManager.DeviceStatus.IDLE)
          || deviceStatus.equals(ExternalDeviceManager.DeviceStatus.NEAR_IDLE)) {
        return true;
      }
      try {
        Sleeper.defaultSleeper().sleep(Duration.ofSeconds(1));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }

  @Override
  public boolean isAlive() {
    return running
        && !cancelled
        && expireTime.isAfter(clock.instant())
        && !Thread.currentThread().isInterrupted();
  }

  @Override
  public boolean isReady() {
    return initialized && isAlive();
  }

  @Override
  public boolean isPrepping() {
    return device.isPrepping()
        || !device.getDimension(Dimension.Name.ALERT_LAB_DISK_USABLE_SIZE).isEmpty()
        || !device.getDimension(Dimension.Name.CLOUDRPC_FAILURE).isEmpty()
        || !isAvailableInExternalDeviceManager();
  }

  @Override
  public boolean isStopped() {
    return !running;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public boolean isTearingDown() {
    return tearingDown && expireTime.isAfter(clock.instant());
  }

  @SuppressWarnings("Interruption")
  @Override
  public void cancel() {
    synchronized (interruptLock) {
      if (!cancelled) {
        logger.atInfo().log("Stopping device runner...");
        cancelled = true;
      }
      if (!isTearingDown()) {
        if (lastInterruptTime == null) {
          lastInterruptTime = clock.instant();
        } else if (lastInterruptTime.isBefore(clock.instant().minus(RUNNER_INTERRUPT_INTERVAL))
            && runningThread != null) {
          logger.atInfo().log(
              "The device runner %s was not killed after being canceled for %s minutes, try to"
                  + " interrupt it again. stack_trace=%s",
              runningThread.getName(),
              RUNNER_INTERRUPT_INTERVAL.toMinutes(),
              shortDebugStackTrace(runningThread, /* maxLength= */ 0));
          // b/191837695 to try to interrupt a timeout thread repeatedly to prevent some logic from
          // eating the InterruptException.
          runningThread.interrupt();
          lastInterruptTime = clock.instant();
        }
      }
    }
  }

  @Override
  public synchronized void cancel(LocalDeviceTestExecutor test) {
    if (this.test != test) {
      logger.atWarning().log(
          "Current device is not running test %s, skip canceling",
          test.getTestRunner().getTestExecutionUnit().locator());
    } else {
      logger.atInfo().log(
          "Stopping device runner with test %s...",
          test.getTestRunner().getTestExecutionUnit().locator());
      cancelled = true;
    }
  }

  @Override
  public synchronized boolean isJobSupported(JobType jobType) {
    synchronized (interruptLock) {
      if (!isAlive()) {
        logger.atWarning().log(
            "The device runner %s is not alive, skip checking job type", runningThread.getName());
        return false;
      }

      if (!device.getDeviceTypes().contains(jobType.getDevice())) {
        logger.atWarning().log(
            "The device type [%s] is not supported by the device runner %s",
            Sets.difference(ImmutableSet.of(jobType.getDevice()), device.getDeviceTypes()),
            runningThread.getName());
        return false;
      }

      if (!device.getDriverTypes().contains(jobType.getDriver())) {
        logger.atWarning().log(
            "The driver [%s] is not supported by the device runner %s",
            Sets.difference(ImmutableSet.of(jobType.getDriver()), device.getDriverTypes()),
            runningThread.getName());
        return false;
      }

      if (!device.getDecoratorTypes().containsAll(jobType.getDecoratorList())) {
        logger.atWarning().log(
            "The decorators [%s] are not supported by the device runner %s",
            Sets.difference(
                ImmutableSet.copyOf(jobType.getDecoratorList()), device.getDecoratorTypes()),
            runningThread.getName());
        return false;
      }
    }

    return true;
  }

  @Override
  public Device getDevice() {
    return device;
  }

  @Override
  public DeviceStatus getDeviceStatus() {
    DeviceStatus status = DeviceStatus.DYING;
    if (isAlive()) {
      if (!isReady()) {
        status = DeviceStatus.INIT;
      } else if (isAvailable()) {
        status = DeviceStatus.IDLE;
      } else if (isPrepping()) {
        status = DeviceStatus.PREPPING;
      } else {
        status = DeviceStatus.BUSY;
      }
    }
    return status;
  }

  @Override
  public DeviceStatusWithTimestamp getDeviceStatusWithTimestamp() {
    return DeviceStatusWithTimestamp.newBuilder()
        .setStatus(getDeviceStatus())
        .setTimestampMs(clock.millis())
        .build();
  }

  @Override
  public synchronized void reserve(LocalDeviceTestExecutor test)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    if (!isReady()) {
      throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
          InfraErrorId.DM_RESERVE_NON_READY_DEVICE, "Device is not ready");
    }
    if (!waitIdleInExternalDeviceManager(WAIT_IDLE_IN_EXTERNAL_DEVICE_MANAGER_EXPIRE)) {
      throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
          InfraErrorId.DM_RESERVE_NON_DUAL_STACK_READY_DEVICE,
          String.format("Device %s is not idle in the dual stack DM", device.getDeviceId()));
    }
    if (this.test == null) {
      this.test = test;
    } else {
      throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
          InfraErrorId.DM_RESERVE_BUSY_DEVICE,
          String.format(
              "Device is not available with test %s, can not add new test %s",
              this.test.getTestRunner().getTestExecutionUnit().locator(),
              test.getTestRunner().getTestExecutionUnit().locator()));
    }
    logger.atInfo().log(
        "Reserved to test %s", test.getTestRunner().getTestExecutionUnit().locator());
    notifyAll();
  }

  @Nullable
  @Override
  public LocalDeviceTestExecutor getTest() {
    return test;
  }

  /** Initializes the device. If fails, will set {@link #initialized} to false. */
  @VisibleForTesting
  void initDevice() throws InterruptedException, MobileHarnessException {
    extendExpireTime(Duration.ofDays(1));
    logger.atInfo().log("Initializing...");

    // Block the device setup process until it's reserved by MH DM.
    try (DeviceReservation ignored =
        externalDeviceManager.reserveDevice(
            device.getDeviceId(), Duration.ofDays(1).minusMinutes(1))) {
      extendExpireTime(device.getSetupTimeout());
      device.setUp();
    } catch (MobileHarnessException e) {
      initialized = false;
      postDeviceErrorEvent(e);
      throw e;
    }
    initialized = true;
    lastCheckDeviceTime = clock.instant();
    deviceStat.onReady();
    extendExpireTime();
    // Sort dimensions for easier manual scanning.
    List<StrPair> dimensions = new ArrayList<>(device.getDimensions());
    StrPairUtil.sort(dimensions);
    logger.atInfo().log(
        "Initialized:"
            + "\n---------- Owners ----------\n%s"
            + "\n---------- Executors ----------\n%s"
            + "\n---------- Dimensions ----------\n%s"
            + "\n---------- Types ----------\n%s"
            + "\n---------- Drivers ----------\n%s"
            + "\n---------- Decorators ----------\n%s",
        Joiner.on("\n").join(apiConfig.getOwners(device.getDeviceControlId())),
        Joiner.on("\n").join(apiConfig.getExecutors(device.getDeviceControlId())),
        StrPairUtil.convertCollectionToString(dimensions, ": ", "\n"),
        Joiner.on("\n").join(device.getDeviceTypes()),
        Joiner.on("\n").join(device.getDriverTypes()),
        Joiner.on("\n").join(device.getDecoratorTypes()));
    extendExpireTime();
  }

  @Override
  public void updateStatusStat() {
    DeviceStatus status;
    synchronized (this) {
      status = getDeviceStatus();
    }

    // Counts the status.
    deviceStat.countStatus(status);
  }

  /** Adds the device id to the device dimension. */
  private void addDeviceIdToDimension(DeviceId deviceId) {
    // Currently it's control id, need to reconsider whether to keep this field.
    device.addDimension(Dimension.Name.ID, deviceId.controlId());
    device.addDimension(Dimension.Name.CONTROL_ID, deviceId.controlId());
    if (!Strings.isNullOrEmpty(deviceId.uuid())) {
      device.addDimension(Dimension.Name.UUID, deviceId.uuid());
      device.addDimension(Dimension.Name.UUID_VOLATILE, String.valueOf(deviceId.isUuidVolatile()));
    }
  }

  /**
   * Checks whether there is test waiting for execution, runs the test if any. Note this method will
   * swallow any exception except {@code InterruptedException}, to make sure this thread can keep
   * running until interrupted or the device is stopped.
   *
   * @return whether the device needs to be rebooted due to test results
   * @throws InterruptedException the current thread is interrupted
   */
  @VisibleForTesting
  boolean checkNRunTest() throws InterruptedException {
    boolean needReboot = false;
    if (test == null || !isAlive()) {
      return needReboot;
    }
    logger.atInfo().log("Found test %s", test.getTestRunner().getTestExecutionUnit().locator());
    Duration testRemainingTime = null;
    try {
      testRemainingTime = test.getTestRunner().getTestExecutionUnit().timer().remainingTimeJava();
    } catch (MobileHarnessException e) {
      logger.atInfo().log("Test %s expired", test.getTestRunner().getTestExecutionUnit().locator());
    }
    if (testRemainingTime != null) {
      extendExpireTime(testRemainingTime);
    }
    try {
      deviceStat.addNewTest();
      TestExecutionResult testExecutionResult =
          TestExecutionResult.create(TestResult.UNKNOWN, PostTestDeviceOp.REBOOT);
      try {
        testExecutionResult = test.executeTest();
      } finally {
        device.info().properties().remove(DEVICE_PROPERTY_RESERVATION_ID);
        deviceStat.addFinishedTest(testExecutionResult.testResult());
        // Invalidate device cache after test is finished.
        DeviceCacheManager.getInstance().invalidateAllCaches(device.getDeviceControlId());
        needReboot =
            deviceRebootUtil.needRebootUponTestResults(
                getDevice(), deviceStat, apiConfig, testExecutionResult);
        postTestExecutionEndedEvent(
            test.getTestRunner().getAllocation(), testExecutionResult.testResult(), needReboot);
        if (needReboot) {
          // If the device needs to reboot based on the test results, the runner needs to be
          // cancelled.  This will only set a flag that marks this runner as cancelled.  Calling
          // code that checks isAlive() and isCancelled() would then interrupt our thread.
          cancel();
        }
      }
    } finally {
      test = null;
      if (!needReboot) {
        recordBecomeIdleTime();
      }
    }

    return needReboot;
  }

  private void extendExpireTime() {
    expireTime = clock.instant().plus(RUNNER_EXPIRE);
  }

  private void extendExpireTime(Duration duration) {
    expireTime = clock.instant().plus(duration);
  }

  /**
   * Checks the update of the device. Reboots the device if exception caught during checking.
   *
   * @return whether the device is changed, to notify the device management framework
   */
  private boolean checkDevice() throws InterruptedException, MobileHarnessException {
    if (clock.instant().minus(CHECK_DEVICE_INTERVAL).isBefore(lastCheckDeviceTime)) {
      return false;
    }
    logger.atInfo().log("Start periodical check");
    lastCheckDeviceTime = clock.instant();
    try {
      return device.checkDevice();
    } catch (MobileHarnessException e) {
      postDeviceErrorEvent(e);
      throw new MobileHarnessException(
          e.getErrorCode(), "Error occurs when checking device: " + e.getMessage());
    } finally {
      logger.atInfo().log("Finish periodical check");
    }
  }

  /** Posts {@link LocalDeviceChangeEvent} to signal the device status/dimension change. */
  private void postDeviceChangeEvent(String info) {
    logger.atInfo().log("Post LocalDeviceChangeEvent: %s", info);
    globalInternalBus.post(
        new LocalDeviceChangeEvent(
            device.getDeviceControlId(),
            device.getDeviceUuid(),
            device.getClass().getSimpleName()));
  }

  /**
   * Posts {@link LocalDeviceErrorEvent} to signal the device error occurs. When the exception is
   * null, post a default instance of {@link ExceptionDetail} to clear existing errors.
   */
  private void postDeviceErrorEvent(Exception e) {
    ExceptionDetail exceptionDetail = ErrorModelConverter.toExceptionDetail(e);
    logger.atInfo().log("Post LocalDeviceErrorEvent");
    globalInternalBus.post(
        new LocalDeviceErrorEvent(
            device.getDeviceControlId(),
            device.getDeviceUuid(),
            device.getClass().getSimpleName(),
            exceptionDetail));
  }

  private void postTestExecutionEndedEvent(
      Allocation allocation, TestResult testResult, boolean needReboot) {
    logger.atInfo().log(
        "Posting TestExecutionEndedEvent, allocation=%s, result=%s, need_reboot=%s",
        allocation, testResult, needReboot);
    globalInternalBus.post(
        new TestExecutionEndedEvent() {

          @Override
          public Allocation getAllocation() {
            return allocation;
          }

          @Override
          public TestResult getTestResult() {
            return testResult;
          }

          @Override
          public boolean needReboot() {
            return needReboot;
          }
        });
    logger.atInfo().log("TestExecutionEndedEvent posted");
  }

  /**
   * Records the time to device property about when this device become IDLE. So Client can compare
   * it with the test start time to calculate the allocation latency. See b/71722259.
   */
  private void recordBecomeIdleTime() {
    Instant now = clock.instant();
    logger.atInfo().atMostEvery(10, TimeUnit.MINUTES).log(
        "Update device %s last IDLE time: %s", device.getDeviceId(), now);
    getDevice()
        .setProperty(
            DeviceProperty.Name.BECOME_IDLE_EPOCH_MS.name().toLowerCase(Locale.ROOT),
            String.valueOf(now.toEpochMilli()));
  }
}
