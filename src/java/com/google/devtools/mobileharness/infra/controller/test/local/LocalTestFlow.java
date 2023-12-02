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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.infra.controller.plugin.CommonPluginCreatorFactory;
import com.google.devtools.mobileharness.infra.controller.plugin.PluginCreator;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner.EventScope;
import com.google.devtools.mobileharness.infra.controller.test.PluginLoadingResult.PluginItem;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.controller.TestFlowConverter;
import com.google.devtools.mobileharness.infra.controller.test.util.TestCommandHistorySaver;
import com.google.devtools.mobileharness.platform.testbed.adhoc.controller.AdhocTestbedDriverFactory;
import com.google.devtools.mobileharness.shared.util.concurrent.ConcurrencyUtil;
import com.google.devtools.mobileharness.shared.util.concurrent.ConcurrencyUtil.SubTask;
import com.google.devtools.mobileharness.shared.util.logging.MobileHarnessLogTag;
import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.decorator.Decorator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.driver.DriverFactory;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

/** The workflow of executing a single test locally. */
public class LocalTestFlow {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DriverFactory driverFactory;

  private final AdhocTestbedDriverFactory adhocTestbedDriverFactory;

  private final ImmutableList<Object> builtinPlugins;

  private final PluginCreator.Factory pluginLoaderFactory;

  private final ListeningExecutorService testThreadPool;

  private final TestFlowConverter testFlowConverter;

  private static final String UTP_DRIVER_NAME = "UtpDriver";

  private static final ImmutableList<String> COMMON_DIMENSIONS_IN_TESTBED =
      ImmutableList.of(
          Ascii.toLowerCase(Dimension.Name.HOST_NAME.name()),
          Ascii.toLowerCase(Dimension.Name.HOST_VERSION.name()));

  public LocalTestFlow(ListeningExecutorService threadPool, TestFlowConverter testFlowConverter) {
    this(threadPool, new DriverFactory(), new AdhocTestbedDriverFactory(), testFlowConverter);
  }

  @VisibleForTesting
  LocalTestFlow(
      ListeningExecutorService threadPool,
      DriverFactory driverFactory,
      AdhocTestbedDriverFactory adhocTestbedDriverFactory,
      TestFlowConverter testFlowConverter) {
    this.testThreadPool = threadPool;
    this.driverFactory = driverFactory;
    this.adhocTestbedDriverFactory = adhocTestbedDriverFactory;
    this.builtinPlugins = ImmutableList.of(new TestCommandHistorySaver());
    this.pluginLoaderFactory = new CommonPluginCreatorFactory();
    this.testFlowConverter = testFlowConverter;
  }

  /**
   * Loads built-in plugins. Should be invoked when test is initializing.
   *
   * @return the loaded plugin items
   */
  ImmutableList<PluginItem<?>> loadBuiltInPlugin(DirectTestRunner testRunner) {
    // Loads built-in plugins.
    for (Object plugin : builtinPlugins) {
      testRunner.registerTestEventSubscriber(plugin, EventScope.CLASS_INTERNAL);
      testRunner.registerTestEventSubscriber(plugin, EventScope.TEST_MESSAGE);
    }
    return builtinPlugins.stream()
        .map(plugin -> PluginItem.create(plugin, EventScope.CLASS_INTERNAL))
        .collect(toImmutableList());
  }

  /** Loads plugins. Should be invoked when test is initializing. */
  ImmutableList<PluginItem<?>> loadPlugin(TestInfo testInfo, DirectTestRunner testRunner)
      throws MobileHarnessException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Loading lab plugins for test %s", testInfo.locator().getId());
    JobInfo jobInfo = testInfo.jobInfo();
    ImmutableSet<String> labPluginPaths = jobInfo.files().get(JobInfo.TAG_LAB_PLUGIN);
    List<String> labPluginClasses = jobInfo.params().getList(JobInfo.PARAM_LAB_PLUGIN, null);
    List<String> labPluginModuleClasses =
        jobInfo.params().getList(JobInfo.PARAM_CLIENT_PLUGIN_MODULES, null);
    String labPluginForceLoadFromJarClassRegex =
        jobInfo.params().get(JobInfo.PARAM_LAB_PLUGIN_FORCE_LOAD_FROM_JAR_CLASS_REGEX);

    // Always create a new ClassLoader for the lab plugin of a new test, and close it when test
    // ends.
    PluginCreator loader =
        pluginLoaderFactory.create(
            labPluginPaths,
            labPluginClasses,
            labPluginModuleClasses,
            /* forceLoadFromJarClassRegex= */ labPluginForceLoadFromJarClassRegex,
            PluginType.LAB,
            testInfo.log());
    if (loader.load()) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Created new lab plugin loader of test %s", testInfo.locator());
      testRunner.registerTestEventSubscriber(
          new Object() {

            @Subscribe
            private void onTestEnded(TestEndedEvent event) {
              loader.close();
              TestInfo testInfo = event.getTest();
              testInfo
                  .log()
                  .atInfo()
                  .alsoTo(logger)
                  .log("Closed lab plugin class loader of test %s", testInfo.locator());
            }
          },
          EventScope.CLASS_INTERNAL);
    }

    // Actually create and register the lab plugin instances.
    int pluginIndex = 0;
    List<Object> plugins = loader.getPlugins();
    for (Object plugin : plugins) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Loaded lab_plugin_jar class %s for test %s",
              plugin.getClass().getCanonicalName(), testInfo.locator());

      testRunner.registerTestEventSubscriber(plugin, EventScope.JAR_PLUGIN);
      testRunner.registerTestEventSubscriber(plugin, EventScope.TEST_MESSAGE);

      // Add lab plugin class name into JobInfo property for plugin usage analysis.
      jobInfo
          .properties()
          .add(
              JobInfo.PARAM_LAB_PLUGIN + "_" + pluginIndex++, plugin.getClass().getCanonicalName());
    }

    return plugins.stream()
        .map(plugin -> PluginItem.create(plugin, EventScope.JAR_PLUGIN))
        .collect(toImmutableList());
  }

  DeviceFeature checkDevice(Device device) {
    return device.toFeature();
  }

  /**
   * Device preparation before actually running a test.
   *
   * @return the generated driver for the given test
   */
  Driver preRunTest(
      List<Device> devices,
      TestInfo testInfo,
      com.google.devtools.mobileharness.api.model.allocation.Allocation allocation,
      BiFunction<Driver, String, Decorator> driverWrapper,
      BiFunction<Driver, Class<? extends Decorator>, Decorator> decoratorExtender)
      throws MobileHarnessException, InterruptedException {

    if (!testInfo
        .properties()
        .getBoolean(PropertyName.Test.LOCAL_TEST_DISABLE_DEVICE_PRE_RUN_POST_RUN)
        .orElse(false)) {
      runDevicePreRunTest(testInfo, devices);
    } else {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Device preRunTest is skipped because LOCAL_TEST_DISABLE_DEVICE_PRE_RUN_POST_RUN"
                  + "is true.");
    }

    Driver driver = createDriver(devices, testInfo, allocation, driverWrapper, decoratorExtender);

    logDimensionsOfDevices(testInfo, devices);

    updateTestPropertyWithDeviceDimensions(testInfo, devices);

    testInfo.properties().add(PropertyName.Test.LAB_TEST_GEN_FILE_DIR, testInfo.getGenFileDir());

    return driver;
  }

  /** Actual execution of a test. */
  void runTest(TestInfo testInfo, Driver driver)
      throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("--------- Device: RunTest (%s) ---------", testInfo.locator().getId());
    driver.run(testInfo);
  }

  /** Device clean up after actually running a test. */
  PostTestDeviceOp postRunTest(TestInfo testInfo, List<Device> devices)
      throws InterruptedException {

    if (testInfo
        .properties()
        .getBoolean(PropertyName.Test.LOCAL_TEST_DISABLE_DEVICE_PRE_RUN_POST_RUN)
        .orElse(false)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Device postRunTest is skipped because LOCAL_TEST_DISABLE_DEVICE_PRE_RUN_POST_RUN"
                  + "is true.");
      return PostTestDeviceOp.NONE;
    }

    PostTestDeviceOp postTestDeviceOp = PostTestDeviceOp.NONE;
    try {
      postTestDeviceOp = runDevicePostRunTest(testInfo, devices);
    } catch (InterruptedException e) {
      if (!testInfo.timer().isExpired()) {
        throw e;
      }
      // device.postRunTest may timeout and be killed with InterruptedException thrown,
      // we also need to reboot the device.
      postTestDeviceOp = PostTestDeviceOp.REBOOT;
    } catch (MobileHarnessException e) {
      testInfo.errors().addAndLog(e, logger);
    } catch (Throwable e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("FATAL ERROR: %s", Throwables.getStackTraceAsString(e));
      testInfo.errors().add(ErrorCode.TEST_POST_RUN_ERROR, e);
    }

    updateTestPropertyWithDeviceDimensions(testInfo, devices);

    return postTestDeviceOp;
  }

  /** Create local test events. */
  Object createLocalTestEvent(
      Class<?> eventType,
      TestInfo testInfo,
      List<Device> devices,
      Allocation allocation,
      @Nullable List<DeviceInfo> deviceInfos,
      @Nullable List<DeviceFeature> deviceFeatures,
      @Nullable Throwable testError) {
    ImmutableMap<String, Device> devicesById =
        devices.stream().collect(toImmutableMap(Device::getDeviceId, identity()));
    if (eventType == TestStartingEvent.class || eventType == LocalTestStartingEvent.class) {
      return new LocalTestStartingEvent(
          testInfo, devicesById, allocation, checkNotNull(deviceInfos).get(0));
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
    } else if (eventType == TestStartEvent.class || eventType == LocalTestStartEvent.class) {
      return new LocalTestStartEvent(
          testInfo, devicesById, allocation, checkNotNull(deviceInfos).get(0));
    } else if (eventType == TestStartedEvent.class || eventType == LocalTestStartedEvent.class) {
      return new LocalTestStartedEvent(
          testInfo, devicesById, allocation, checkNotNull(deviceInfos).get(0));
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
    } else if (eventType == TestEndingEvent.class || eventType == LocalTestEndingEvent.class) {
      return new LocalTestEndingEvent(
          testInfo,
          devicesById,
          allocation,
          deviceInfos == null ? null : deviceInfos.get(0),
          testError);
    } else if (eventType == TestEndedEvent.class || eventType == LocalTestEndedEvent.class) {
      return new LocalTestEndedEvent(
          testInfo,
          devicesById,
          allocation,
          deviceInfos == null ? null : deviceInfos.get(0),
          /* shouldRebootDevice= */ false,
          testError);
    } else if (eventType
            == com.google.devtools.mobileharness.api.testrunner.event.test.TestEndingEvent.class
        || eventType
            == com.google.devtools.mobileharness.api.testrunner.event.test.LocalTestEndingEvent
                .class) {
      DeviceFeature primaryDeviceFeature = deviceFeatures == null ? null : deviceFeatures.get(0);
      return new com.google.devtools.mobileharness.api.testrunner.event.test
          .LocalTestEndingEvent() {

        @Override
        public TestInfo getTest() {
          return testInfo;
        }

        @Override
        public com.google.devtools.mobileharness.api.model.allocation.Allocation getAllocation() {
          return allocation.toNewAllocation();
        }

        @Override
        public DeviceFeature getDeviceFeature() {
          return primaryDeviceFeature;
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

  private void runDevicePreRunTest(TestInfo testInfo, List<Device> devices)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException,
          InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("--------- Device: PreRunTest (%s) ---------", testInfo.locator().getId());
    ImmutableList<String> deviceIds =
        devices.stream().map(Device::getDeviceId).collect(toImmutableList());
    testInfo.properties().add(PropertyName.Test.DEVICE_ID_LIST, String.join(",", deviceIds));
    // Starts all devices' preRunTest().
    ConcurrencyUtil.runInParallel(
        devices.stream()
            .map(
                device ->
                    SubTask.<Void>of(
                        () -> {
                          runDevicePreRunTest(testInfo, device);
                          return null;
                        },
                        "test-runner-"
                            + testInfo.locator().getId()
                            + "-sub-device-"
                            + device.getDeviceId(),
                        MobileHarnessLogTag.SUB_DEVICE_ID,
                        device.getDeviceId()))
            .collect(toImmutableList()),
        testThreadPool,
        /* resultMerger= */ results -> null);
  }

  private void runDevicePreRunTest(TestInfo testInfo, Device device)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException,
          InterruptedException {
    try {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Pre-run test of device [%s]", device.getDeviceId());
      device.preRunTest(new com.google.wireless.qa.mobileharness.shared.api.job.TestInfo(testInfo));
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Pre-run test of device [%s] finished", device.getDeviceId());
    } catch (MobileHarnessException e) {
      throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
          InfraErrorId.TR_FAILED_TO_RUN_DEVICE_PRE_RUN_TEST_IN_LOCAL_TEST_FLOW,
          String.format("Failed to run pre-run test of device [%s]", device.getDeviceId()),
          e);
    }
  }

  private PostTestDeviceOp runDevicePostRunTest(TestInfo testInfo, List<Device> devices)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException,
          InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("--------- Device: PostRunTest (%s) ---------", testInfo.locator().getId());

    // Starts all devices' postRunTest().
    return ConcurrencyUtil.runInParallel(
        devices.stream()
            .map(
                device ->
                    SubTask.of(
                        () -> runDevicePostRunTest(testInfo, device),
                        "test-runner-"
                            + testInfo.locator().getId()
                            + "-sub-device-"
                            + device.getDeviceId(),
                        MobileHarnessLogTag.SUB_DEVICE_ID,
                        device.getDeviceId()))
            .collect(toImmutableList()),
        testThreadPool,
        results ->
            results.contains(PostTestDeviceOp.REBOOT)
                ? PostTestDeviceOp.REBOOT
                : PostTestDeviceOp.NONE);
  }

  private PostTestDeviceOp runDevicePostRunTest(TestInfo testInfo, Device device)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException,
          InterruptedException {
    try {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Post-run test of device [%s]", device.getDeviceId());
      PostTestDeviceOp result =
          device.postRunTest(
              new com.google.wireless.qa.mobileharness.shared.api.job.TestInfo(testInfo));
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Post-run test of device [%s] finished, result=%s", device.getDeviceId(), result);
      return result;
    } catch (MobileHarnessException e) {
      throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
          InfraErrorId.TR_FAILED_TO_RUN_DEVICE_POST_RUN_TEST_IN_LOCAL_TEST_FLOW,
          String.format("Failed to run post-run test of device [%s]", device.getDeviceId()),
          e);
    }
  }

  private Driver createDriver(
      List<Device> devices,
      TestInfo testInfo,
      com.google.devtools.mobileharness.api.model.allocation.Allocation allocation,
      BiFunction<Driver, String, Decorator> driverWrapper,
      BiFunction<Driver, Class<? extends Decorator>, Decorator> decoratorExtender)
      throws MobileHarnessException, InterruptedException {
    if (devices.size() == 1) {
      TestFlowConverter.Result testFlow = testFlowConverter.convert(testInfo, devices, allocation);

      Driver driver =
          testFlow.utpDriver().isPresent()
              // Fixed the name of UtpDriver to avoid the future class name change
              // (e.g. UtpDriver -> UtpDriverImpl) affecting users who subscribes driver events.
              ? driverWrapper.apply(testFlow.utpDriver().get(), UTP_DRIVER_NAME)
              : driverFactory.createDriver(
                  devices.get(0),
                  testInfo,
                  ClassUtil.getDriverClass(
                      testFlow.flow().getDriver().getMobileHarnessDriver().getDriverName()),
                  driverWrapper);
      return driverFactory.decorateDriver(
          driver,
          testInfo,
          ClassUtil.getDecoratorClasses(
              Lists.reverse(testFlow.flow().getDecoratorStack(0).getDecoratorNameList())),
          driverWrapper,
          decoratorExtender);
    } else {
      return adhocTestbedDriverFactory.create(
          devices, testInfo, testThreadPool, driverFactory, driverWrapper, decoratorExtender);
    }
  }

  private void logDimensionsOfDevices(TestInfo testInfo, List<Device> devices) {
    // Logs device dimensions.
    ImmutableList<String> deviceIds =
        devices.stream().map(Device::getDeviceId).collect(toImmutableList());
    String deviceInfoString =
        IntStream.range(0, devices.size())
            .mapToObj(
                i ->
                    "\n   + Device"
                        + (devices.size() == 1 ? "" : "-" + (i + 1))
                        + ": "
                        + devices.get(i).getDeviceId()
                        + devices.get(i).getDimensions().stream()
                            .map(
                                dimension ->
                                    "\n       - "
                                        + dimension.getName()
                                        + " = "
                                        + dimension.getValue())
                            .collect(joining()))
            .collect(joining());

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Start test [%s] on device(s) %s:\n"
                + " + Test Name: %s\n"
                + " + Test ID: %s\n"
                + " + Job Name: %s\n"
                + " + Job ID: %s\n"
                + " + Device Information:%s",
            testInfo.locator().getName(),
            deviceIds,
            testInfo.locator().getName(),
            testInfo.locator().getId(),
            testInfo.jobInfo().locator().getName(),
            testInfo.jobInfo().locator().getId(),
            deviceInfoString);
  }

  /**
   * Updates test properties with latest device dimensions.
   *
   * @param testInfo the root {@link TestInfo} for the test
   * @param devices all devices allocated for the test
   */
  private void updateTestPropertyWithDeviceDimensions(TestInfo testInfo, List<Device> devices) {
    for (Device device : devices) {
      Set<StrPair> dimensions = new HashSet<>(device.getDimensions());
      dimensions.addAll(device.getRequiredDimensions());
      for (Entry<String, Collection<String>> dimension :
          StrPairUtil.convertCollectionToMultimap(dimensions).asMap().entrySet()) {
        String keyName;
        if (devices.size() > 1) {
          // For multi-device tests insert the device ID in the middle of each property.
          // i.e. dimension_XXX becomes dimension_subdevice_deviceId_XXX
          keyName =
              PropertyName.Test.PREFIX_SUBDEVICE_DIMENSION
                  + device.getDeviceId()
                  + "_"
                  + dimension.getKey();
        } else {
          keyName = PropertyName.Test.PREFIX_DIMENSION + dimension.getKey();
        }
        // Merges dimension values for repeated dimension keys
        testInfo.properties().add(keyName, Joiner.on(",").join(dimension.getValue()));
      }
    }

    Map<String, Set<String>> commonDimensions = findCommonDimensionsInTestbed(devices);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Adding common dimensions %s shared among testbed to test %s",
            commonDimensions.keySet(), testInfo.locator().getId());
    for (Entry<String, Set<String>> dimension : commonDimensions.entrySet()) {
      // Merges dimension values for repeated dimension keys
      testInfo
          .properties()
          .add(
              PropertyName.Test.PREFIX_DIMENSION + dimension.getKey(),
              Joiner.on(",").join(dimension.getValue()));
    }
  }

  // Finds the dimensions of which all the devices shared the same value.
  private Map<String, Set<String>> findCommonDimensionsInTestbed(List<Device> devices) {
    Map<String, Set<String>> dimensionValuesByKey = new HashMap<>();
    for (String dimensionKey : COMMON_DIMENSIONS_IN_TESTBED) {
      for (Device device : devices) {
        Set<String> value = ImmutableSet.copyOf(device.getDimension(dimensionKey));
        value = Sets.intersection(value, dimensionValuesByKey.getOrDefault(dimensionKey, value));
        if (value.isEmpty()) {
          dimensionValuesByKey.remove(dimensionKey);
          break;
        }
        dimensionValuesByKey.put(dimensionKey, value);
      }
    }
    return dimensionValuesByKey;
  }
}
