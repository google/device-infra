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

package com.google.devtools.mobileharness.infra.controller.test.local;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDecoratorPostForwardEvent;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDecoratorPreForwardEvent;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDriverEndedEvent;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDriverStartingEvent;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunnerSetting;
import com.google.devtools.mobileharness.infra.controller.test.PluginLoadingResult;
import com.google.devtools.mobileharness.infra.controller.test.PluginLoadingResult.PluginItem;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.exception.TestRunnerLauncherConnectedException;
import com.google.devtools.mobileharness.infra.controller.test.local.annotation.DoNotSubscribeTestEvent;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.controller.TestFlowConverter;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionResult;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.decorator.BaseDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.Decorator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** For executing a single test locally. */
public class LocalDirectTestRunner
    extends com.google.devtools.mobileharness.infra.controller.test.AbstractDirectTestRunnerCore<
        LocalDirectTestRunner> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ImmutableList<Device> devices;
  private final LocalTestFlow testFlow;
  private Driver driver;
  private final SettableFuture<PluginLoadingResult> pluginLoadingResultFuture =
      SettableFuture.create();

  public LocalDirectTestRunner(
      TestRunnerLauncher<? super LocalDirectTestRunner> launcher,
      DirectTestRunnerSetting setting,
      List<Device> devices,
      ListeningExecutorService threadPool,
      TestFlowConverter testFlowConverter)
      throws TestRunnerLauncherConnectedException {
    super(launcher, setting);
    this.devices = ImmutableList.copyOf(devices);
    this.testFlow = new LocalTestFlow(threadPool, testFlowConverter);
  }

  @Override
  public void preExecute() {
    // It may take a while for the device runner to pick up this test. So sets it to RUNNING to give
    // the client a correct test status. See b/19090306.
    getTestInfo().status().set(TestStatus.RUNNING);

    super.preExecute();
  }

  @Override
  public TestExecutionResult execute() throws InterruptedException {
    Instant startInstant = getTestRunnerStartInstant().orElseThrow(AssertionError::new);
    Instant executeInstant = getTestRunnerExecuteInstant().orElseThrow(AssertionError::new);
    getTestInfo()
        .properties()
        .add(
            PropertyName.Test.START_DELAY_MS,
            Long.toString(Duration.between(startInstant, executeInstant).toMillis()));

    return super.execute();
  }

  @Override
  public ListenableFuture<PluginLoadingResult> getPluginLoadingResult() {
    return pluginLoadingResultFuture;
  }

  @Override
  protected LocalDirectTestRunner self() {
    return this;
  }

  @Override
  protected String getComponentName() {
    return "local";
  }

  /**
   * Loads plugins.
   *
   * <p>{@inheritDoc}
   */
  @Override
  protected void initialize(TestInfo testInfo, Allocation allocation)
      throws MobileHarnessException {
    try {
      List<PluginItem<?>> pluginItems = new ArrayList<>();
      pluginItems.addAll(this.initialPluginItems);
      pluginItems.addAll(testFlow.loadBuiltInPlugin(this));

      pluginItems.addAll(testFlow.loadPlugin(testInfo, this));
      PluginLoadingResult pluginLoadingResult = PluginLoadingResult.create(pluginItems);
      this.pluginLoadingResultFuture.set(pluginLoadingResult);
    } catch (MobileHarnessException | RuntimeException e) {
      pluginLoadingResultFuture.setException(e);
      throw e;
    }
  }

  @Override
  protected List<DeviceFeature> checkDevice(TestInfo testInfo, Allocation allocation)
      throws MobileHarnessException, InterruptedException {
    return devices.stream().map(testFlow::checkDevice).collect(Collectors.toList());
  }

  @Override
  protected void preRunTest(
      boolean isTestSkipped,
      TestInfo testInfo,
      Allocation allocation,
      List<DeviceFeature> deviceFeatures)
      throws MobileHarnessException, InterruptedException {
    if (isTestSkipped) {
      logger.atInfo().log("Skip pre-run test since test is skipped");
      return;
    }
    logger.atInfo().log("Pre-run test");
    driver =
        testFlow.preRunTest(
            devices,
            testInfo,
            allocation.toNewAllocation(),
            // TODO: Supports multiple devices for driver event.
            (driver, driverName) ->
                new DriverEventGenerator(
                    driver,
                    driverName,
                    testInfo,
                    allocation,
                    deviceFeatures.get(0),
                    devices.get(0)),
            (decorated, decoratorClass) ->
                new DecoratorEventGenerator(
                    decorated,
                    testInfo,
                    allocation,
                    deviceFeatures.get(0),
                    devices.get(0),
                    decoratorClass));

    // Lets the driver, decorators and their steps subscribe messages of the test.
    // TODO: Supports multiple devices for test message.
    Set<Object> subscribers = ClassUtil.getAllSubscribersOfDriver(driver);
    for (Object subscriber : subscribers) {
      logger.atInfo().log(
          "Register test message subscriber: %s", subscriber.getClass().getSimpleName());
      registerTestEventSubscriber(subscriber, EventScope.TEST_MESSAGE);
    }
  }

  @Override
  protected void runTest(TestInfo testInfo, Allocation allocation)
      throws MobileHarnessException, InterruptedException {
    testFlow.runTest(testInfo, driver);
    updateDeviceStatus(checkDevice(testInfo, allocation));
  }

  @Override
  protected PostTestDeviceOp postRunTest(TestInfo testInfo, Allocation allocation)
      throws InterruptedException {
    return testFlow.postRunTest(testInfo, devices);
  }

  @Override
  protected Object createTestEvent(
      Class<?> eventType,
      TestInfo testInfo,
      Allocation allocation,
      @Nullable List<DeviceInfo> deviceInfos,
      @Nullable List<DeviceFeature> deviceFeatures,
      @Nullable Throwable testError) {
    return testFlow.createLocalTestEvent(
        eventType, testInfo, devices, allocation, deviceInfos, deviceFeatures, testError);
  }

  /**
   * Driver wrapper for generating and posting {@linkplain LocalDriverStartingEvent driver starting
   * event}s and {@linkplain LocalDriverEndedEvent driver ended event}s.
   *
   * <p>Note that it should be a decorator so {@link ClassUtil#getAllSubscribersOfDriver(Driver)}
   * can get subscribers correctly. (b/69013386)
   */
  @DoNotSubscribeTestEvent
  private class DriverEventGenerator extends BaseDecorator {

    private final TestInfo testInfo;
    private final com.google.devtools.mobileharness.api.model.allocation.Allocation allocation;
    private final DeviceFeature deviceFeature;
    private final Device device;
    private final String decoratedDriverName;

    private DriverEventGenerator(
        Driver decorated,
        String decoratedDriverName,
        TestInfo testInfo,
        Allocation allocation,
        DeviceFeature deviceFeature,
        Device device) {
      super(decorated, testInfo);
      this.testInfo = testInfo;
      this.allocation = allocation.toNewAllocation();
      this.deviceFeature = deviceFeature;
      this.device = device;
      this.decoratedDriverName = decoratedDriverName;
    }

    @Override
    public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Starting driver [%s] of test %s", decoratedDriverName, testInfo.locator().getName());
      try {
        if (postLocalDriverStartingEvent()) {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Test is skipped by plugins when %s is starting", decoratedDriverName);
        } else {
          getDecorated().run(testInfo);
        }
        postLocalDriverEndedEvent(/* error= */ null);
      } catch (MobileHarnessException | InterruptedException e) {
        postLocalDriverEndedEvent(e);
        throw e;
      } finally {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Driver [%s] of test %s ended", decoratedDriverName, testInfo.locator().getName());
      }
    }

    /** Returns whether to skip running the test */
    private boolean postLocalDriverStartingEvent() {
      testInfo.log().atInfo().alsoTo(logger).log("%s is starting", decoratedDriverName);
      Object event =
          new LocalDriverStartingEvent() {

            @Override
            public String getDriverName() {
              return decoratedDriverName;
            }

            @Override
            public DeviceFeature getDeviceFeature() {
              return deviceFeature;
            }

            @Override
            public Device getDevice() {
              return device;
            }

            @Override
            public TestInfo getTest() {
              return testInfo;
            }

            @Override
            public com.google.devtools.mobileharness.api.model.allocation.Allocation
                getAllocation() {
              return allocation;
            }
          };
      return postTestEvent("driver event" /* eventType */, false /* afterDriverExecution */, event);
    }

    /** Returns whether to skip running the test */
    private boolean postLocalDriverEndedEvent(@Nullable Throwable error) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "%s has ended%s",
              decoratedDriverName,
              (error == null ? "" : " with error: " + error.getClass().getSimpleName()));
      Object event =
          new LocalDriverEndedEvent() {

            @Override
            public String getDriverName() {
              return decoratedDriverName;
            }

            @Override
            public DeviceFeature getDeviceFeature() {
              return deviceFeature;
            }

            @Override
            public Device getDevice() {
              return device;
            }

            @Override
            public Optional<Throwable> getExecutionError() {
              return Optional.ofNullable(error);
            }

            @Override
            public TestInfo getTest() {
              return testInfo;
            }

            @Override
            public com.google.devtools.mobileharness.api.model.allocation.Allocation
                getAllocation() {
              return allocation;
            }
          };
      return postTestEvent("driver event" /* eventType */, true /* afterDriverExecution */, event);
    }
  }

  /**
   * Decorator extender for generating and posting {@linkplain LocalDecoratorPreForwardEvent
   * decorator pre-forward event}s and {@linkplain LocalDecoratorPostForwardEvent decorator
   * post-forward event}s.
   */
  @DoNotSubscribeTestEvent
  private class DecoratorEventGenerator extends BaseDecorator {

    private final TestInfo testInfo;
    private final com.google.devtools.mobileharness.api.model.allocation.Allocation allocation;
    private final DeviceFeature deviceFeature;
    private final Device device;
    private final Class<? extends Decorator> decoratorClass;

    private DecoratorEventGenerator(
        Driver decorated,
        TestInfo testInfo,
        Allocation allocation,
        DeviceFeature deviceFeature,
        Device device,
        Class<? extends Decorator> decoratorClass) {
      super(decorated, testInfo);
      this.testInfo = testInfo;
      this.allocation = allocation.toNewAllocation();
      this.deviceFeature = deviceFeature;
      this.device = device;
      this.decoratorClass = decoratorClass;
    }

    @Override
    public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
      try {
        if (postLocalDecoratorPreForwardEvent()) {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Test is skipped by plugins when %s is forwarding a running request",
                  decoratorClass.getSimpleName());
        } else {
          getDecorated().run(testInfo);
        }
        postLocalDecoratorPostForwardEvent(/* error= */ null);
      } catch (MobileHarnessException | InterruptedException e) {
        postLocalDecoratorPostForwardEvent(e);
        throw e;
      }
    }

    /** Returns whether to skip running the test */
    private boolean postLocalDecoratorPreForwardEvent() {
      String decoratorName = decoratorClass.getSimpleName();
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("%s is forwarding a running request", decoratorName);
      Object event =
          new LocalDecoratorPreForwardEvent() {

            @Override
            public String getDriverName() {
              return decoratorName;
            }

            @Override
            public DeviceFeature getDeviceFeature() {
              return deviceFeature;
            }

            @Override
            public Device getDevice() {
              return device;
            }

            @Override
            public TestInfo getTest() {
              return testInfo;
            }

            @Override
            public com.google.devtools.mobileharness.api.model.allocation.Allocation
                getAllocation() {
              return allocation;
            }
          };
      return postTestEvent(
          "decorator event" /* eventType */, false /* afterDriverExecution */, event);
    }

    /** Returns whether to skip running the test */
    private boolean postLocalDecoratorPostForwardEvent(@Nullable Throwable error) {
      String decoratorName = decoratorClass.getSimpleName();
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "%s has forwarded a running request%s",
              decoratorName,
              (error == null ? "" : " with error: " + error.getClass().getSimpleName()));
      Object event =
          new LocalDecoratorPostForwardEvent() {

            @Override
            public String getDriverName() {
              return decoratorName;
            }

            @Override
            public DeviceFeature getDeviceFeature() {
              return deviceFeature;
            }

            @Override
            public Device getDevice() {
              return device;
            }

            @Override
            public Optional<Throwable> getExecutionError() {
              return Optional.ofNullable(error);
            }

            @Override
            public TestInfo getTest() {
              return testInfo;
            }

            @Override
            public com.google.devtools.mobileharness.api.model.allocation.Allocation
                getAllocation() {
              return allocation;
            }
          };
      return postTestEvent(
          "decorator event" /* eventType */, true /* afterDriverExecution */, event);
    }
  }
}
