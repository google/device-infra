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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidTestLoopTest.ANDROID_TEST_LOOP_FAILURE_MESSAGE;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidTestLoopTest.ANDROID_TEST_LOOP_TEST_END_EPOCH_MS;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidTestLoopTest.ANDROID_TEST_LOOP_TEST_START_EPOCH_MS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.testing.FakeTicker;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.protobuf.util.Durations;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidTestLoopTestSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidTestLoopTestTest {

  private static final String DEVICE_ID = "12345";

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Device device;
  @Mock private AndroidInstrumentationUtil instrumentationUtil;
  @Mock private ApkInstaller apkInstaller;
  @Mock private ResUtil resUtil;
  @Mock private Clock clock;

  private TestInfo testInfo;
  private JobInfo jobInfo;
  private AndroidTestLoopTest driver;
  private FakeTicker fakeTicker;

  @Before
  public void setUp() throws Exception {
    fakeTicker = new FakeTicker();
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                JobType.newBuilder()
                    .setDevice("device_type")
                    .setDriver("AndroidTestLoopTest")
                    .build())
            .setSetting(
                JobSetting.newBuilder()
                    .setTimeout(Timeout.newBuilder().setTestTimeoutMs(10000L).build())
                    .build())
            .build();
    testInfo = jobInfo.tests().add("test_name");

    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(resUtil.getResourceFile(any(), anyString())).thenReturn("/path/to/apk");
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1L)).thenReturn(Instant.ofEpochMilli(2L));
    driver =
        new AndroidTestLoopTest(
            device, testInfo, instrumentationUtil, apkInstaller, resUtil, fakeTicker, clock);
  }

  @Test
  public void run_multipleScenarios_success() throws Exception {
    AndroidTestLoopTestSpec spec =
        AndroidTestLoopTestSpec.newBuilder()
            .setAppPackageId("com.example.app")
            .setScenarios("1,3")
            .build();
    jobInfo.scopedSpecs().add("AndroidTestLoopTestSpec", spec);

    setupInstrumentation((lineCallback) -> lineCallback.onLine("INSTRUMENTATION_CODE: -1"));

    driver.run(testInfo);

    verify(apkInstaller).installApk(eq(device), any(ApkInstallArgs.class), any());
    verify(instrumentationUtil)
        .instrument(
            eq(DEVICE_ID),
            any(),
            eq(
                AndroidInstrumentationSetting.create(
                    "com.google.testing.platform.android.driver.testloop.companion",
                    ".TestLoopRunner",
                    null,
                    ImmutableMap.of(
                        "packageName", "com.example.app",
                        "scenario", "1"),
                    false,
                    true,
                    false,
                    false,
                    false,
                    false)),
            any(),
            any());
    verify(instrumentationUtil)
        .instrument(
            eq(DEVICE_ID),
            any(),
            eq(
                AndroidInstrumentationSetting.create(
                    "com.google.testing.platform.android.driver.testloop.companion",
                    ".TestLoopRunner",
                    null,
                    ImmutableMap.of(
                        "packageName", "com.example.app",
                        "scenario", "3"),
                    false,
                    true,
                    false,
                    false,
                    false,
                    false)),
            any(),
            any());

    assertThat(testInfo.properties().get(ANDROID_TEST_LOOP_TEST_START_EPOCH_MS)).isEqualTo("1");
    assertThat(testInfo.properties().get(ANDROID_TEST_LOOP_TEST_END_EPOCH_MS)).isEqualTo("2");
  }

  @Test
  public void run_instrumentationWithFailure_abortsEarlyWithResultFail() throws Exception {
    AndroidTestLoopTestSpec spec =
        AndroidTestLoopTestSpec.newBuilder()
            .setAppPackageId("com.example.app")
            .setScenarios("1,2")
            .build();
    jobInfo.scopedSpecs().add("AndroidTestLoopTestSpec", spec);

    setupInstrumentation((lineCallback) -> lineCallback.onLine("INSTRUMENTATION_CODE: 0"));

    driver.run(testInfo);

    verify(instrumentationUtil).instrument(eq(DEVICE_ID), any(), any(), any(), any());
    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.FAIL);
    assertThat(testInfo.resultWithCause().get().causeException().get().getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_TEST_LOOP_INSTRUMENTATION_FAILED);
    assertThat(testInfo.properties().get(ANDROID_TEST_LOOP_TEST_START_EPOCH_MS)).isEqualTo("1");
    assertThat(testInfo.properties().get(ANDROID_TEST_LOOP_TEST_END_EPOCH_MS)).isEqualTo("2");
  }

  @Test
  public void run_instrumentationWithError_setsResultToFailAndReturnsMessage() throws Exception {
    AndroidTestLoopTestSpec spec =
        AndroidTestLoopTestSpec.newBuilder()
            .setAppPackageId("com.example.app")
            .setScenarios("1,2")
            .build();
    jobInfo.scopedSpecs().add("AndroidTestLoopTestSpec", spec);

    setupInstrumentation(
        (lineCallback) -> {
          lineCallback.onLine(
              "onError: commandError=false message=INSTRUMENTATION_ABORTED: System has crashed.");
          lineCallback.onLine("INSTRUMENTATION_ABORTED: System has crashed.");
        });

    driver.run(testInfo);

    assertThat(testInfo.properties().get(ANDROID_TEST_LOOP_FAILURE_MESSAGE))
        .contains("INSTRUMENTATION_ABORTED: System has crashed.");
    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.FAIL);
    assertThat(testInfo.resultWithCause().get().causeException().get().getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_TEST_LOOP_INSTRUMENTATION_FAILED);
  }

  @Test
  public void run_timeoutAfterFirstScenario_abortsEarlyWithPass() throws Exception {
    AndroidTestLoopTestSpec spec =
        AndroidTestLoopTestSpec.newBuilder()
            .setAppPackageId("com.example.app")
            .setScenarios("1,2,3")
            .setScenariosTimeout(Durations.fromSeconds(10))
            .build();
    jobInfo.scopedSpecs().add("AndroidTestLoopTestSpec", spec);

    setupInstrumentation(
        (lineCallback) -> {
          lineCallback.onLine("INSTRUMENTATION_CODE: -1");
          // Advance the fake ticker by 10 seconds during the first scenario, leaving 0 seconds
          // remaining. The test should abort before running scenario 2.
          fakeTicker.advance(Duration.ofSeconds(10));
        });

    driver.run(testInfo);

    verify(instrumentationUtil, times(1)).instrument(eq(DEVICE_ID), any(), any(), any(), any());
    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
  }

  private void setupInstrumentation(Consumer<LineCallback> instrumentationConsumer)
      throws Exception {
    when(instrumentationUtil.instrument(any(), any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Supplier<LineCallback> supplier = invocation.getArgument(4);
              instrumentationConsumer.accept(supplier.get());
              return "";
            });
  }
}
