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

package com.google.devtools.mobileharness.platform.testbed.adhoc.controller;

import static com.google.common.collect.Multimaps.toMultimap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.controller.test.local.annotation.DoNotSubscribeTestEvent;
import com.google.devtools.mobileharness.shared.util.concurrent.ConcurrencyUtil;
import com.google.devtools.mobileharness.shared.util.concurrent.ConcurrencyUtil.SubTask;
import com.google.devtools.mobileharness.shared.util.logging.MobileHarnessLogTag;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.decorator.BaseDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.Decorator;
import com.google.wireless.qa.mobileharness.shared.api.device.CompositeDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.BaseDriver;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.driver.DriverFactory;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * A "container" driver for running ad-hoc testbed tests.
 *
 * <p>This driver contains:
 *
 * <ol>
 *   <li>A "main" driver which is created from the driver type of the job. It receives a
 *       TestbedDevice, which contains all devices of the test.
 *   <li>One decorator stack for each device, which is created from the decorator type in the
 *       subDeviceSpec of the device. Each decorator receives its own device.
 * </ol>
 *
 * <p>When this driver runs, it runs all decorator stacks in parallel, and wait two barriers
 * before/after the main driver.
 *
 * <p>If one decorator stack or the main driver fails, all sub executions fail immediately. This
 * driver will throw the exception of the root cause.
 *
 * <p>If this driver is interrupted, it will interrupt all sub executions.
 */
@DoNotSubscribeTestEvent
public class AdhocTestbedDriver extends BaseDriver {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * A list of sub drivers which contains:
   *
   * <ul>
   *   <li>decorator1-of-device1 -> decorator2-of-device1 -> ... -> main-driver-with-testbed-device
   *   <li>decorator1-of-device2 -> decorator2-of-device2 -> ... -> secondary-driver (empty)
   *   <li>decorator1-of-device3 -> decorator2-of-device3 -> ... -> secondary-driver (empty)
   *   <li>decorator1-of-device4 -> decorator2-of-device4 -> ... -> secondary-driver (empty)
   *   <li>...
   * </ul>
   */
  private final ImmutableList<Driver> subDrivers;

  /** Lets all threads wait until all decorators' preRun() finish before running the main driver. */
  private final CyclicBarrier preDriverBarrier;

  /** Lets all threads wait until the main driver finishes before running decorators' postRun(). */
  private final CyclicBarrier postDriverBarrier;

  private final ListeningExecutorService threadPool;
  private final DriverFactory driverFactory;

  /** For posting driver events. */
  private final BiFunction<Driver, String, Decorator> driverWrapper;

  /** For posting decorator events. */
  @Nullable
  private final BiFunction<Driver, Class<? extends Decorator>, Decorator> decoratorExtender;

  AdhocTestbedDriver(
      List<Device> devices,
      TestInfo testInfo,
      ListeningExecutorService threadPool,
      DriverFactory driverFactory,
      BiFunction<Driver, String, Decorator> driverWrapper,
      @SuppressWarnings("unused") @Nullable
          BiFunction<Driver, Class<? extends Decorator>, Decorator> decoratorExtender)
      throws MobileHarnessException, InterruptedException {
    super(createAdhocTestbedDevice(devices, testInfo), testInfo);
    this.threadPool = threadPool;
    this.driverFactory = driverFactory;
    this.driverWrapper = driverWrapper;
    this.decoratorExtender = null;
    this.subDrivers = ImmutableList.copyOf(createSubDrivers(devices));
    this.preDriverBarrier = new CyclicBarrier(devices.size());
    this.postDriverBarrier = new CyclicBarrier(devices.size());

    // Sets up TestbedDevice.
    testInfo.log().atInfo().alsoTo(logger).log("Setting up TestbedDevice");
    getDevice().setUp();
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    try {
      // Starts all sub drivers.
      testInfo.log().atInfo().alsoTo(logger).log("Starting all sub drivers");
      // TODO: Wraps the callable in a test context like with CommandExecutor.
      ConcurrencyUtil.runInParallel(
          subDrivers.stream()
              .map(
                  driver ->
                      SubTask.<Void>of(
                          () -> {
                            TestInfo subTestInfo =
                                testInfo
                                    .subTests()
                                    .add(
                                        driver.getDevice().getDeviceId(),
                                        createTestNameOnSubDriver(driver, testInfo));
                            subTestInfo
                                .properties()
                                .add(
                                    PropertyName.Test.AdhocTestbedDriver
                                        .IS_ADHOC_TESTBED_SUB_DEVICE,
                                    "true");
                            // If parent TestInfo fileTag contains device ID, attach them to
                            // subTestInfo so decorators in sub-device stack can retrieve those
                            // files (b/162563952).
                            ListMultimap<String, String> files =
                                testInfo.files().getAll().entries().stream()
                                    .filter(
                                        e -> e.getKey().contains(driver.getDevice().getDeviceId()))
                                    .collect(
                                        toMultimap(
                                            Map.Entry::getKey,
                                            Map.Entry::getValue,
                                            () ->
                                                MultimapBuilder.hashKeys()
                                                    .arrayListValues()
                                                    .build()));
                            subTestInfo.files().addAll(files);
                            // Several decorators dump test outputs to the directory provided
                            // through TestInfo.getGenFileDir(). To ensure each decorator stack
                            // records device specific outputs correctly, we need to provide a
                            // different output directory for each stack. Creating a sub-test for
                            // each decorator stack allows outputs to be tied to a specific device
                            // more clearly.
                            runSubDriver(subTestInfo, driver);
                            return null;
                          },
                          "test-runner-"
                              + testInfo.locator().getId()
                              + "-sub-device-"
                              + driver.getDevice().getDeviceId(),
                          MobileHarnessLogTag.SUB_DEVICE_ID,
                          driver.getDevice().getDeviceId()))
              .collect(Collectors.toList()),
          threadPool,
          /* resultMerger= */ results -> null);
    } finally {
      updateRootTestResultIfNeeded(testInfo);
    }
  }

  /*
   * Adds the device id as the suffix, to avoid the tests in different sub drivers share the same
   * test name.
   *
   * <p>If sub drivers use the same test name, a test runs in two devices will show in the sponge
   * like:
   * - parent_test_name
   *   - parent_test_name_attempts_1_of_2
   *   - parent_test_name_attempts_2_of_2
   *
   * <p>After adding device id as the suffix, the sponge is like:
   * - parent_test_name
   *   - parent_test_name_device1_id
   *   - parent_test_name_device2_id
   */
  private String createTestNameOnSubDriver(Driver driver, TestInfo parentTest) {
    return parentTest.locator().getName() + "_" + driver.getDevice().getDeviceId();
  }

  /**
   * Updates root TestInfo result based on testbed driver test result or sub-test results.
   *
   * <p>Sub-test results are only saved in sub TestInfo, need to reflect it on root TestInfo so
   * sponge can reflect it as expected.
   */
  @VisibleForTesting
  static void updateRootTestResultIfNeeded(TestInfo rootTestInfo) {
    TestResult rootTestResult = rootTestInfo.resultWithCause().get().type();
    if (rootTestResult.equals(TestResult.UNKNOWN)) {
      rootTestInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.ERROR,
              new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                  ExtErrorId.MOBLY_TESTBED_ADHOC_DRIVER_END_WITH_UNKNOWN_RESULT,
                  "Set root test result to ERROR because adhoc testbed driver ends with UNKNOWN"
                      + " test result. Maybe the primary driver has not been triggered."));
    } else if (rootTestResult.equals(TestResult.PASS)) {
      // Subtests in mobly tests may have different results, b/175287972
      int errorTestsTotalCnt = 0;
      int infraErrorTestsCnt = 0;
      int customerErrorTestsCnt = 0;
      int dependencyErrorTestsCnt = 0;
      boolean hasFailTests = false;
      for (TestInfo subTestInfo : rootTestInfo.subTests().getAll().values()) {
        TestResult subTestResult = subTestInfo.resultWithCause().get().type();
        String errMsg =
            String.format(
                "Sub test result[%s], sub test name[%s], sub test ID[%s]",
                subTestResult, subTestInfo.locator().getName(), subTestInfo.locator().getId());
        switch (subTestResult) {
          case FAIL:
            hasFailTests = true;
            rootTestInfo.log().atInfo().alsoTo(logger).log("%s", errMsg);
            break;
          case ERROR:
            errorTestsTotalCnt++;
            if (subTestInfo.resultWithCause().get().cause().isPresent()) {
              ErrorType errorType =
                  ErrorModelConverter.getCriticalErrorId(
                          subTestInfo.resultWithCause().get().causeProto().get())
                      .getType();
              if (ErrorType.INFRA_ISSUE.equals(errorType)) {
                infraErrorTestsCnt++;
              } else if (ErrorType.CUSTOMER_ISSUE.equals(errorType)) {
                customerErrorTestsCnt++;
              } else if (ErrorType.DEPENDENCY_ISSUE.equals(errorType)) {
                dependencyErrorTestsCnt++;
              }
            }
            rootTestInfo.log().atInfo().alsoTo(logger).log("%s", errMsg);
            break;
          default:
            break;
        }
      }

      if (errorTestsTotalCnt > 0) {
        ExtErrorId errorResultErrorId =
            ExtErrorId.MOBLY_TESTBED_ADHOC_DRIVER_SUBTEST_WITH_UNDETERMINED_ERROR_RESULT;
        if (infraErrorTestsCnt > 0) {
          errorResultErrorId =
              ExtErrorId.MOBLY_TESTBED_ADHOC_DRIVER_SUBTEST_WITH_INFRA_ERROR_RESULT;
        } else if (errorTestsTotalCnt == customerErrorTestsCnt) {
          errorResultErrorId =
              ExtErrorId.MOBLY_TESTBED_ADHOC_DRIVER_SUBTEST_WITH_CUSTOMER_ERROR_RESULT;
        } else if (errorTestsTotalCnt == dependencyErrorTestsCnt) {
          errorResultErrorId =
              ExtErrorId.MOBLY_TESTBED_ADHOC_DRIVER_SUBTEST_WITH_DEPENDENCY_ERROR_RESULT;
        }
        rootTestInfo
            .resultWithCause()
            .setNonPassing(
                TestResult.ERROR,
                new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                    errorResultErrorId, "Test has >= 1 ERROR subtest(s)"));
      } else if (hasFailTests) {
        rootTestInfo
            .resultWithCause()
            .setNonPassing(
                TestResult.FAIL,
                new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                    ExtErrorId.MOBLY_TESTBED_ADHOC_DRIVER_SUBTEST_WITH_FAIL_RESULT,
                    "Test has >= 1 FAIL subtest(s)"));
      }
    }
  }

  private void runSubDriver(TestInfo testInfo, Driver driver)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException,
          InterruptedException {
    try {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Starting sub driver of device [%s]", driver.getDevice().getDeviceId());
      driver.run(testInfo);
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Sub driver of device [%s] finished", driver.getDevice().getDeviceId());
    } catch (MobileHarnessException e) {
      com.google.devtools.mobileharness.api.model.error.MobileHarnessException newException =
          new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
              InfraErrorId.TR_FAILED_TO_RUN_SUB_DRIVER_IN_ADHOC_TESTBED_TEST,
              String.format(
                  "Failed to run driver of device [%s]", driver.getDevice().getDeviceId()),
              e);
      testInfo.resultWithCause().setNonPassing(TestResult.ERROR, newException);
      throw newException;
    }
  }

  private List<Driver> createSubDrivers(List<Device> devices) throws MobileHarnessException {
    List<Driver> subDriver = new ArrayList<>();

    // Creates the main driver and secondary drivers.
    List<Driver> rawSubDrivers = createRawSubDrivers(devices);

    // Wraps them with two barriers.
    List<Driver> synchronizedSubDrivers =
        rawSubDrivers.stream().map(DriverBarrier::new).collect(Collectors.toList());

    // Adds decorator stack to each sub driver.
    for (int i = 0; i < devices.size(); i++) {
      Driver decoratedDriver =
          decorateDriver(
              synchronizedSubDrivers.get(i),
              getTest().jobInfo().subDeviceSpecs().getSubDevice(i).decorators().getAll());
      subDriver.add(decoratedDriver);
    }
    return subDriver;
  }

  private List<Driver> createRawSubDrivers(List<Device> devices) throws MobileHarnessException {
    return Stream.concat(
            Stream.of(createMainDriver(devices.get(0))),
            devices.stream().skip(1L).map(SecondaryDriver::new))
        .collect(Collectors.toList());
  }

  private Driver createMainDriver(Device primaryDevice) throws MobileHarnessException {
    Driver driver =
        driverFactory.createDriver(
            getDevice(),
            getTest(),
            ClassUtil.getDriverClass(getTest().jobInfo().type().getDriver()),
            driverWrapper);
    // Because the device of the main driver is a TestbedDevice, but the decorators of it should
    // have the first sub-device, we create a wrapper here to replace the device that the outer
    // decorators can see/get.
    return new DriverDeviceReplacer(driver, primaryDevice);
  }

  private Driver decorateDriver(Driver driver, List<String> decoratorNamesInExecutingOrder)
      throws MobileHarnessException {
    return driverFactory.decorateDriver(
        driver,
        getTest(),
        ClassUtil.getDecoratorClasses(Lists.reverse(decoratorNamesInExecutingOrder)),
        null /* Do not send driver event to each decorator. */,
        decoratorExtender);
  }

  private static Device createAdhocTestbedDevice(List<Device> devices, TestInfo testInfo)
      throws MobileHarnessException {
    String testbedDeviceId = testInfo.locator().getId();

    // Users running custom device tests will run tests using several TestbedDevices. We need to
    // make sure that the subdevices in each TestbedDevice are unpacked and referenced directly in
    // the combined TestbedDevice in order to perform setup and device management correctly.
    List<Device> allSubDevices = new ArrayList<>();
    for (Device device : devices) {
      if (device instanceof CompositeDevice) {
        CompositeDevice compositeDevice = (CompositeDevice) device;
        allSubDevices.addAll(compositeDevice.getManagedDevices().asList());
      } else {
        allSubDevices.add(device);
      }
    }

    return new com.google.wireless.qa.mobileharness.shared.api.device.SimpleCompositeDevice(
        testbedDeviceId, allSubDevices);
  }

  @DoNotSubscribeTestEvent
  private static class DriverDeviceReplacer extends BaseDriver implements Decorator {

    private final Driver decorated;

    private DriverDeviceReplacer(Driver decorated, Device device) {
      super(device, decorated.getTest());
      this.decorated = decorated;
    }

    @Override
    public Driver getDecorated() {
      return decorated;
    }

    @Override
    public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
      testInfo.log().atInfo().alsoTo(logger).log("Running main driver");
      getDecorated().run(testInfo);
    }
  }

  @DoNotSubscribeTestEvent
  private class SecondaryDriver extends BaseDriver {

    private SecondaryDriver(Device device) {
      super(device, AdhocTestbedDriver.this.getTest());
    }

    @Override
    public void run(TestInfo testInfo) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Do nothing in the secondary driver with device [%s]", getDevice().getDeviceId());
    }
  }

  @DoNotSubscribeTestEvent
  private class DriverBarrier extends BaseDecorator {

    private DriverBarrier(Driver decorated) {
      super(decorated, AdhocTestbedDriver.this.getTest());
    }

    @Override
    public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
      try {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Waiting on pre-driver barrier, device=[%s]", getDevice().getDeviceId());
        preDriverBarrier.await();

        // The main "run" of AdhocTestbedDriver runs each stack using a sub-TestInfo specific to
        // each sub-device. We need to make sure the main driver is run against the TestInfo passed
        // to the AdhocTestbedDriver constructor.
        getDecorated().run(AdhocTestbedDriver.this.getTest());

        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Waiting on post-driver barrier, device=[%s]", getDevice().getDeviceId());
        postDriverBarrier.await();

        // If non of the sub device decorators failed, set the device TestInfo result to PASS.
        if (testInfo.resultWithCause().get().type().equals(TestResult.UNKNOWN)) {
          testInfo.resultWithCause().setPass();
        }
      } catch (BrokenBarrierException e) {
        InterruptedException exception =
            new InterruptedException(
                String.format(
                    "Interrupted when waiting on driver barrier, device=[%s]",
                    getDevice().getDeviceId()));
        exception.addSuppressed(e);
        throw exception;
      }
    }
  }
}
