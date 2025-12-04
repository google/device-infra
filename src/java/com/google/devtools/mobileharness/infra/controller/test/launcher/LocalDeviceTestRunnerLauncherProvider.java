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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.api.model.proto.Job.JobFeature;
import com.google.devtools.mobileharness.infra.container.controller.ProxyTestRunner;
import com.google.devtools.mobileharness.infra.controller.device.TestExecutor;
import com.google.devtools.mobileharness.infra.controller.device.TestExecutorProvider;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 * Provider for {@link LocalDeviceTestRunnerLauncher}.
 *
 * <p>This provider retrieves {@link TestExecutor} instances for the given devices, waits for the
 * devices to become ready, and verifies that at least one device supports the required job
 * features. It then creates a {@link LocalDeviceTestRunnerLauncher} with the prepared test
 * executors.
 */
public class LocalDeviceTestRunnerLauncherProvider implements LauncherProvider {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Duration WAIT_DEVICE_READY_TIMEOUT = Duration.ofSeconds(20L);

  private final TestExecutorProvider testExecutorProvider;

  @Inject
  LocalDeviceTestRunnerLauncherProvider(TestExecutorProvider testExecutorProvider) {
    this.testExecutorProvider = testExecutorProvider;
  }

  @Override
  public TestRunnerLauncher<? super ProxyTestRunner> getLauncher(
      String testId, JobFeature jobFeature, List<Device> devices)
      throws MobileHarnessException, InterruptedException {
    ImmutableList<String> deviceIds =
        devices.stream().map(Device::getDeviceId).collect(toImmutableList());
    List<TestExecutor> testExecutors = getTestExecutorsUntilReady(deviceIds);
    checkJobFeature(jobFeature, testExecutors);
    return new LocalDeviceTestRunnerLauncher(
        testExecutors.get(0), testExecutors.stream().skip(1L).collect(toImmutableList()));
  }

  private List<TestExecutor> getTestExecutorsUntilReady(List<String> deviceIds)
      throws MobileHarnessException, InterruptedException {
    // Waits for the device to be ready, since the device maybe INIT in LabServer.
    Clock clock = Clock.systemUTC();
    Instant expireTime = clock.instant().plus(WAIT_DEVICE_READY_TIMEOUT);
    Sleeper sleeper = Sleeper.defaultSleeper();
    int attempts = 0;
    while (true) {
      try {
        return getTestExecutors(deviceIds);
      } catch (MobileHarnessException e) {
        if (clock.instant().isAfter(expireTime)
            || (e.getErrorId() != InfraErrorId.LAB_RPC_PREPARE_TEST_DEVICE_NOT_ALIVE
                && e.getErrorId() != InfraErrorId.LAB_RPC_PREPARE_TEST_DEVICE_NOT_FOUND)) {
          throw e;
        }
        logger.atWarning().log(
            "%d failed attempts to get device runners of %s, try again later",
            ++attempts, deviceIds);
        sleeper.sleep(Duration.ofSeconds(1));
      }
    }
  }

  private List<TestExecutor> getTestExecutors(List<String> deviceIds)
      throws MobileHarnessException {
    List<TestExecutor> testExecutors = new ArrayList<>();
    for (String deviceId : deviceIds) {
      TestExecutor testExecutor = testExecutorProvider.getTestExecutorForDeviceId(deviceId);
      MobileHarnessExceptions.check(
          testExecutor != null,
          InfraErrorId.LAB_RPC_PREPARE_TEST_DEVICE_NOT_FOUND,
          () -> String.format("Device [%s] does not exist", deviceId));
      MobileHarnessExceptions.check(
          testExecutor.isAlive(),
          InfraErrorId.LAB_RPC_PREPARE_TEST_DEVICE_NOT_ALIVE,
          () -> String.format("Device [%s] is not alive when preparing test", deviceId));
      testExecutors.add(testExecutor);
    }
    return testExecutors;
  }

  private void checkJobFeature(JobFeature jobFeature, List<TestExecutor> testExecutors)
      throws MobileHarnessException {
    // TODO: Checks device requirement directly rather than job type.
    JobType primaryDeviceJobType =
        JobType.newBuilder()
            .setDriver(jobFeature.getDriver())
            .setDevice(jobFeature.getDeviceRequirements().getDeviceRequirement(0).getDeviceType())
            .addAllDecorator(
                jobFeature.getDeviceRequirements().getDeviceRequirement(0).getDecoratorList())
            .build();

    for (TestExecutor testExecutor : testExecutors) {
      if (isJobSupported(testExecutor.getDevice(), primaryDeviceJobType)) {
        return;
      }
    }

    throw new MobileHarnessException(
        InfraErrorId.LAB_RPC_PREPARE_TEST_JOB_TYPE_NOT_SUPPORTED,
        String.format("Job type [%s] is not supported by MH lab", primaryDeviceJobType));
  }

  /** Checks whether the job type is supported by this device. */
  private static boolean isJobSupported(Device device, JobType jobType) {
    if (!device.getDeviceTypes().contains(jobType.getDevice())) {
      logger.atWarning().log(
          "The device type [%s] is not supported by the device with ID %s",
          Sets.difference(ImmutableSet.of(jobType.getDevice()), device.getDeviceTypes()),
          device.getDeviceControlId());
      return false;
    }

    if (!device.getDriverTypes().contains(jobType.getDriver())) {
      logger.atWarning().log(
          "The driver [%s] is not supported by the device with ID %s",
          Sets.difference(ImmutableSet.of(jobType.getDriver()), device.getDriverTypes()),
          device.getDeviceControlId());
      return false;
    }

    if (!device.getDecoratorTypes().containsAll(jobType.getDecoratorList())) {
      logger.atWarning().log(
          "The decorators [%s] are not supported by the device with ID %s",
          Sets.difference(
              ImmutableSet.copyOf(jobType.getDecoratorList()), device.getDecoratorTypes()),
          device.getDeviceControlId());
      return false;
    }
    return true;
  }
}
