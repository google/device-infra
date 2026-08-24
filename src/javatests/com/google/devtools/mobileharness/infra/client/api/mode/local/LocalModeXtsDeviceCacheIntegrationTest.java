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

package com.google.devtools.mobileharness.infra.client.api.mode.local;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.initializer.AdbInitializer;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Job.Retry;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCacheManager;
import com.google.devtools.mobileharness.api.testrunner.device.cache.XtsDeviceCache;
import com.google.devtools.mobileharness.infra.client.api.Annotations.GlobalInternalEventBus;
import com.google.devtools.mobileharness.infra.client.api.ClientApi;
import com.google.devtools.mobileharness.infra.client.api.ClientApiModule;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.shared.usmf.UsmfBinary;
import com.google.devtools.mobileharness.shared.usmf.UsmfEnvironment;
import com.google.devtools.mobileharness.shared.usmf.builtin.adb.MockAdbController;
import com.google.devtools.mobileharness.shared.usmf.builtin.adb.MockAndroidDevice;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.devtools.mobileharness.shared.util.junit.rule.CaptureLogs;
import com.google.devtools.mobileharness.shared.util.junit.rule.PrintTestName;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LocalModeXtsDeviceCacheIntegrationTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String SERIAL = "HT8420M00155";
  private static final String DEVICE_TYPE = "AndroidRealDevice";

  @Rule public final SetFlags flags = new SetFlags();
  @Rule public final CaptureLogs captureLogs = new CaptureLogs();
  @Rule public final PrintTestName printTestName = new PrintTestName();
  @Rule public final UsmfEnvironment usmfEnvironment = new UsmfEnvironment();
  @Rule public final LocalModeRule localModeRule = new LocalModeRule();

  @Bind @GlobalInternalEventBus private final EventBus globalInternalEventBus = new EventBus();

  @Inject private ClientApi clientApi;
  @Inject private LocalMode localMode;

  private MockAdbController mockAdbController;

  @Before
  public void setUp() throws Exception {
    // Resets static singletons to ensure a clean state between test runs.
    AdbInitializer.resetForTest();
    Aapt.resetForTest();

    // Invalidates any lingering device caches from prior tests.
    new XtsDeviceCache().invalidateCache(SERIAL);
    DeviceCacheManager.getInstance().invalidateGeneralAndContainerCaches(SERIAL);

    // Deploys mock ADB with a simulated Android device and mock AAPT binary.
    mockAdbController =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7(SERIAL))
            .buildAndDeploy();
    UsmfBinary mockAapt = usmfEnvironment.createBinary("aapt").buildAndDeploy();

    // Sets test flags to disable real device discovery and point to mock binaries.
    flags.setAll(
        ImmutableMap.<String, String>builder()
            .put("adb", mockAdbController.getAdbPath())
            .put("aapt", mockAapt.getPath())
            .put("enable_android_device_ready_check", "false")
            .put("enable_emulator_detection", "false")
            .put("enable_fastboot_detector", "false")
            .put("enable_fastboot_in_android_real_device", "false")
            .put("enable_root_device", "false")
            .put("android_device_daemon", "false")
            .put("clear_android_device_multi_users", "false")
            .put("disable_device_reboot", "true")
            .put("disable_wifi_util_func", "true")
            .put("ignore_check_device_failure", "true")
            .put("external_adb_initializer_template", "true")
            .put("adb_dont_kill_server", "true")
            .buildOrThrow());

    // Injects dependencies using Guice.
    Guice.createInjector(
            new ClientApiModule(), localModeRule.getModule(), BoundFieldModule.of(this))
        .injectMembers(this);

    // Initializes local mode with the global internal event bus.
    localMode.initialize(globalInternalEventBus);
  }

  @After
  public void tearDown() {
    // Resets static singletons.
    AdbInitializer.resetForTest();
    Aapt.resetForTest();

    // Cleans up device caches.
    new XtsDeviceCache().invalidateCache(SERIAL);
    DeviceCacheManager.getInstance().invalidateGeneralAndContainerCaches(SERIAL);
  }

  @Test
  public void runJob_keepXtsCache_deviceAllocatedAfterAdbOffline() throws Exception {
    // Runs the first job with a plugin that keeps the device cached in XtsDeviceCache while ADB
    // goes offline.
    JobInfo jobInfo1 = createJobInfo("job_with_xts_cache", Duration.ofSeconds(30L));
    TestPluginForKeepingXtsCache plugin = new TestPluginForKeepingXtsCache(mockAdbController);

    clientApi.startJob(jobInfo1, localMode, ImmutableList.of(plugin));
    clientApi.waitForJob(jobInfo1.locator().getId());

    // Verifies that the first job passes.
    assertThat(jobInfo1.resultWithCause().get().type()).isEqualTo(TestResult.PASS);

    // Verifies that XtsDeviceCache remains cached while general DeviceCache is invalidated.
    XtsDeviceCache xtsDeviceCache = new XtsDeviceCache();
    assertThat(xtsDeviceCache.isCached(SERIAL)).isTrue();
    assertThat(DeviceCache.getInstance().isCached(SERIAL)).isFalse();

    // Runs a second job requesting the same device.
    JobInfo jobInfo2 = createJobInfo("second_job_after_xts_cached", Duration.ofSeconds(30L));

    clientApi.startJob(jobInfo2, localMode);
    clientApi.waitForJob(jobInfo2.locator().getId());

    // Verifies that the second job succeeds because the device was retained by XtsDeviceCache.
    assertThat(jobInfo2.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
  }

  @Test
  public void runJob_withoutXtsCache_deviceUndetectedAfterAdbOffline() throws Exception {
    // Runs the first job with a plugin that does not retain XtsDeviceCache and takes ADB offline.
    JobInfo jobInfo1 = createJobInfo("job_without_xts_cache", Duration.ofSeconds(30L));
    TestPluginWithoutXtsCache plugin = new TestPluginWithoutXtsCache(mockAdbController);

    clientApi.startJob(jobInfo1, localMode, ImmutableList.of(plugin));
    clientApi.waitForJob(jobInfo1.locator().getId());

    // Verifies that the first job passes.
    assertThat(jobInfo1.resultWithCause().get().type()).isEqualTo(TestResult.PASS);

    // Verifies that neither XtsDeviceCache nor general DeviceCache retains the device cache.
    XtsDeviceCache xtsDeviceCache = new XtsDeviceCache();
    assertThat(xtsDeviceCache.isCached(SERIAL)).isFalse();
    assertThat(DeviceCache.getInstance().isCached(SERIAL)).isFalse();

    // Waits until the device manager detects that the device is undetected/offline.
    DeviceQuerier deviceQuerier = localMode.createDeviceQuerier();
    waitUntilDeviceUndetected(deviceQuerier, SERIAL, Duration.ofSeconds(10L));
  }

  private static void waitUntilDeviceUndetected(
      DeviceQuerier deviceQuerier, String serial, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    // Polls until the device is no longer present in the queried device list.
    while (Instant.now().isBefore(deadline)) {
      DeviceQueryResult result = deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
      boolean devicePresent =
          result.getDeviceInfoList().stream()
              .anyMatch(deviceInfo -> deviceInfo.getId().equals(serial));
      if (!devicePresent) {
        return;
      }
      Sleeper.defaultSleeper().sleep(Duration.ofMillis(200L));
    }
    // Throws an error if the device remains detected after the deadline.
    throw new AssertionError(
        String.format("Device %s is still detected after waiting %s", serial, timeout));
  }

  private static JobInfo createJobInfo(String jobName, Duration startTimeout) {
    // Configures job settings including device type, driver, retry policy, and start timeout.
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator(jobName))
            .setType(JobType.newBuilder().setDevice(DEVICE_TYPE).setDriver("NoOpDriver").build())
            .setSetting(
                JobSetting.newBuilder()
                    .setRetry(Retry.newBuilder().setTestAttempts(1).build())
                    .setTimeout(
                        Timeout.newBuilder().setStartTimeoutMs(startTimeout.toMillis()).build())
                    .build())
            .build();
    // Sets execution parameters for NoOpDriver.
    jobInfo.params().add("sleep_time_sec", "1");
    return jobInfo;
  }

  private static class TestPluginForKeepingXtsCache {
    private final MockAdbController mockAdbController;
    private final XtsDeviceCache xtsDeviceCache = new XtsDeviceCache();

    private TestPluginForKeepingXtsCache(MockAdbController mockAdbController) {
      this.mockAdbController = mockAdbController;
    }

    @Subscribe
    public void onTestStarting(LocalTestStartingEvent event) {
      logger.atInfo().log("TestPluginForKeepingXtsCache.onTestStarting");
      String deviceControlId = event.getLocalDevice().getDeviceControlId();
      // Caches the device in both XtsDeviceCache and general DeviceCache.
      xtsDeviceCache.cache(deviceControlId, DEVICE_TYPE, Duration.ofMinutes(5L));
      DeviceCache.getInstance().cache(deviceControlId, DEVICE_TYPE, Duration.ofMinutes(5L));

      // Simulates ADB disconnect by marking the device offline in mock ADB.
      try {
        mockAdbController.setDeviceOnline(deviceControlId, false);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to update mock adb state", e);
      }
    }

    @Subscribe
    public void onTestEnded(LocalTestEndedEvent event) {
      logger.atInfo().log("TestPluginForKeepingXtsCache.onTestEnded");
      // Invalidates only the general DeviceCache, leaving XtsDeviceCache active.
      DeviceCache.getInstance().invalidateCache(event.getLocalDevice().getDeviceControlId());
    }
  }

  private static class TestPluginWithoutXtsCache {
    private final MockAdbController mockAdbController;

    private TestPluginWithoutXtsCache(MockAdbController mockAdbController) {
      this.mockAdbController = mockAdbController;
    }

    @Subscribe
    public void onTestStarting(LocalTestStartingEvent event) {
      logger.atInfo().log("TestPluginWithoutXtsCache.onTestStarting");
      String deviceControlId = event.getLocalDevice().getDeviceControlId();
      // Caches the device only in general DeviceCache without XtsDeviceCache.
      DeviceCache.getInstance().cache(deviceControlId, DEVICE_TYPE, Duration.ofMinutes(5L));

      // Simulates ADB disconnect by marking the device offline in mock ADB.
      try {
        mockAdbController.setDeviceOnline(deviceControlId, false);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to update mock adb state", e);
      }
    }

    @Subscribe
    public void onTestEnded(LocalTestEndedEvent event) {
      logger.atInfo().log("TestPluginWithoutXtsCache.onTestEnded");
      // Invalidates the general DeviceCache on test end.
      DeviceCache.getInstance().invalidateCache(event.getLocalDevice().getDeviceControlId());
    }
  }
}
