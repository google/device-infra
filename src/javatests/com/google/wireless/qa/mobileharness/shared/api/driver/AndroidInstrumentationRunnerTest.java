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
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_TEST_END_EPOCH_MS;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_TEST_START_EPOCH_MS;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.wireless.qa.mobileharness.shared.api.driver.AndroidInstrumentationOptionsPreparer.PreparedInstrumentationOptions;
import com.google.wireless.qa.mobileharness.shared.api.driver.AndroidInstrumentationRunner.ExecutionParams;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidInstrumentationDriverSpec;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfoMocker;
import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidInstrumentationRunnerTest {
  private static final String DEVICE_ID = "device_id";
  private static final int SDK_VERSION = 33;
  private static final String TEST_PACKAGE = "com.example.test";
  private static final String RUNNER_NAME = "androidx.test.runner.AndroidJUnitRunner";
  private static final String BUILD_PACKAGE = "com.example.app";
  private static final String EXTERNAL_STORAGE = "/sdcard";
  private static final ImmutableList<Optional<ImmutableMap<String, String>>> SINGLE_EMPTY_OPTION =
      ImmutableList.of(Optional.of(ImmutableMap.of()));

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private Adb adb;
  @Mock private SystemStateManager systemStateManager;
  @Mock private AndroidInstrumentationUtil androidInstrumentationUtil;
  @Mock private AndroidPackageManagerUtil androidPackageManagerUtil;
  @Mock private CountDownTimer timer;

  private final LocalFileUtil fileUtil = new LocalFileUtil();
  private final TestInfo testInfo = TestInfoMocker.mockTestInfo();
  private final JobInfo jobInfo = testInfo.jobInfo();
  private String genFileDir;
  private AndroidInstrumentationRunner runner;

  @Before
  public void setUp() throws Exception {
    genFileDir = tempFolder.newFolder("gen").getAbsolutePath();
    when(testInfo.getGenFileDir()).thenReturn(genFileDir);
    when(testInfo.timer()).thenReturn(timer);
    when(timer.remainingTimeJava()).thenReturn(Duration.ofSeconds(60));
    runner = createRunnerWithClock(Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC));
  }

  @Test
  public void run_asyncModeAtMinSdk_startsAsyncInstrumentationWithoutPullingFiles()
      throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_ASYNC, "true");
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_INSTRUMENT_TIMEOUT_SEC, "15");
    ImmutableMap<String, String> optionMap = ImmutableMap.of("key", "val");

    runner.run(
        createParams(
            /* sdkVersion= */ 21,
            ImmutableList.of(Optional.of(optionMap)),
            "com.example.AsyncTest",
            /* showRawResults= */ false));

    AndroidInstrumentationSetting expectedSetting =
        AndroidInstrumentationSetting.create(
            TEST_PACKAGE,
            RUNNER_NAME,
            "com.example.AsyncTest",
            optionMap,
            /* async= */ true,
            /* showRawResults= */ false,
            /* prefixAndroidTest= */ false,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ true,
            /* enableCoverage= */ false,
            /* useOrchestrator= */ false);
    verify(androidInstrumentationUtil)
        .instrument(DEVICE_ID, 21, expectedSetting, Duration.ofSeconds(15));
    verify(androidInstrumentationUtil, never())
        .pullInstrumentationFilesFromDevice(any(), any(), any());
  }

  @Test
  public void run_asyncModeOneBelowMinSdk_throwsIncompatibleDeviceError() {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_ASYNC, "true");

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () ->
                runner.run(
                    createParams(
                        /* sdkVersion= */ 20,
                        SINGLE_EMPTY_OPTION,
                        "com.example.Test",
                        /* showRawResults= */ false)));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_INSTRUMENTATION_INCOMPATIBLE_DEVICE);
    assertThat(thrown).hasMessageThat().contains("20 (<21)");
  }

  @Test
  public void run_asyncModeSkippedOptionMap_recordsSkipResult() throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_ASYNC, "true");

    runner.run(
        createParams(
            SDK_VERSION,
            ImmutableList.of(Optional.empty()),
            "com.example.Test",
            /* showRawResults= */ false));

    assertThat(testInfo.properties().get(AndroidInstrumentationDriverSpec.PROPERTY_RESULT + "_0"))
        .isEqualTo(AndroidInstrumentationDriverSpec.SKIPPED_OPTION_STR);
  }

  @Test
  public void run_asyncModeSkippedOptionMap_skipsInstrumentation() throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_ASYNC, "true");

    runner.run(
        createParams(
            SDK_VERSION,
            ImmutableList.of(Optional.empty()),
            "com.example.Test",
            /* showRawResults= */ false));

    verify(androidInstrumentationUtil, never()).instrument(any(), eq(SDK_VERSION), any(), any());
  }

  @Test
  public void run_asyncModeTimeoutException_setsTimeoutResultAndRethrows() throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_ASYNC, "true");
    when(androidInstrumentationUtil.instrument(eq(DEVICE_ID), eq(SDK_VERSION), any(), any()))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_TIMEOUT, "timed out"));

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> runner.run(createDefaultParams()));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_TIMEOUT);
    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.TIMEOUT);
  }

  @Test
  public void run_asyncModeNonTimeoutException_rethrowsWithoutSettingTimeout() throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_ASYNC, "true");
    when(androidInstrumentationUtil.instrument(eq(DEVICE_ID), eq(SDK_VERSION), any(), any()))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_FAILED, "exec failed"));

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> runner.run(createDefaultParams()));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_FAILED);
    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.UNKNOWN);
  }

  @Test
  public void run_sequentialMapsPassSkippedAndFail_aggregatesFailResultAndJoinsLogs()
      throws Exception {
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), isNull()))
        .thenReturn("OK (1 test)")
        .thenReturn("FAILURES!!!\nTests run: 1,  Failures: 1");

    runner.run(
        createParams(
            SDK_VERSION,
            ImmutableList.of(
                Optional.of(ImmutableMap.of("iter", "0")),
                Optional.empty(),
                Optional.of(ImmutableMap.of("iter", "2"))),
            "com.example.SeqTest",
            /* showRawResults= */ false));

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.FAIL);
    assertThat(testInfo.resultWithCause().get().causeExceptionNonEmpty().getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_FAILED);
    assertThat(testInfo.properties().get(AndroidInstrumentationDriverSpec.PROPERTY_RESULT + "_0"))
        .isEqualTo("PASS");
    assertThat(testInfo.properties().get(AndroidInstrumentationDriverSpec.PROPERTY_RESULT + "_1"))
        .isEqualTo(AndroidInstrumentationDriverSpec.SKIPPED_OPTION_STR);
    assertThat(testInfo.properties().get(AndroidInstrumentationDriverSpec.PROPERTY_RESULT + "_2"))
        .isEqualTo("FAIL");
    assertThat(
            testInfo
                .properties()
                .get(
                    PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_LOG_FILENAMES))
        .isEqualTo("instrument_0.log;instrument_2.log");
  }

  @Test
  public void run_sequentialMapsFailAndError_errorTakesPrecedenceOverFail() throws Exception {
    when(systemStateManager.isOnline(DEVICE_ID)).thenReturn(true);
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), isNull()))
        .thenReturn("FAILURES!!!\nTests run: 1,  Failures: 1")
        .thenReturn(
            "INSTRUMENTATION_STATUS: Error=Unable to find instrumentation info\n"
                + "INSTRUMENTATION_STATUS_CODE: -1");

    runner.run(
        createParams(
            SDK_VERSION,
            ImmutableList.of(
                Optional.of(ImmutableMap.of("iter", "0")),
                Optional.of(ImmutableMap.of("iter", "1"))),
            "com.example.SeqTest",
            /* showRawResults= */ false));

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
    assertThat(testInfo.resultWithCause().get().causeExceptionNonEmpty().getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_ERROR);
  }

  @Test
  public void run_sequentialOptionMapWithBeforeAndAfterAdbCommands_executesBothHooks()
      throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_0", "k=v");
    jobInfo
        .params()
        .add(
            AndroidInstrumentationDriverSpec.PARAM_ITER_ADB_SHELL_BEFORE_INSTRUMENTATION + "_0",
            "echo before0");
    jobInfo
        .params()
        .add(
            AndroidInstrumentationDriverSpec.PARAM_ITER_ADB_SHELL_AFTER_INSTRUMENTATION + "_0",
            "echo after0");
    when(adb.runShell(DEVICE_ID, "echo before0", null, null)).thenReturn("ok");
    when(adb.runShell(DEVICE_ID, "echo after0", null, null)).thenReturn("ok");
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), isNull()))
        .thenReturn("OK (1 test)");

    runner.run(createDefaultParams());

    verify(adb).runShell(DEVICE_ID, "echo before0", null, null);
    verify(adb).runShell(DEVICE_ID, "echo after0", null, null);
  }

  @Test
  public void run_multipleOptionMapsWithIterClearFlags_clearsBuildAndTestPackagesPerIteration()
      throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_ITER_CLEAR_BUILD, "true");
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_ITER_CLEAR_TEST, "true");
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), isNull()))
        .thenReturn("OK (1 test)");

    runner.run(
        createParams(
            SDK_VERSION,
            ImmutableList.of(Optional.of(ImmutableMap.of()), Optional.of(ImmutableMap.of())),
            null,
            /* showRawResults= */ false));

    verify(androidPackageManagerUtil, times(2)).clearPackage(DEVICE_ID, BUILD_PACKAGE);
    verify(androidPackageManagerUtil, times(2)).clearPackage(DEVICE_ID, TEST_PACKAGE);
  }

  @Test
  public void run_advancingClockAndRawResults_recordsStartEndEpochMsAndWritesJunitXml()
      throws Exception {
    AndroidInstrumentationRunner timedRunner =
        createRunnerWithClock(
            new AdvancingClock(Instant.ofEpochMilli(1_000L), Duration.ofMillis(250L)));
    stubRawInstrumentationLines(
        """
        INSTRUMENTATION_STATUS: class=com.example.SampleTest
        INSTRUMENTATION_STATUS: current=1
        INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
        INSTRUMENTATION_STATUS: numtests=1
        INSTRUMENTATION_STATUS: test=testPass
        INSTRUMENTATION_STATUS_CODE: 1
        INSTRUMENTATION_STATUS: class=com.example.SampleTest
        INSTRUMENTATION_STATUS: current=1
        INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
        INSTRUMENTATION_STATUS: numtests=1
        INSTRUMENTATION_STATUS: test=testPass
        INSTRUMENTATION_STATUS_CODE: 0
        INSTRUMENTATION_CODE: -1
        """,
        "OK (1 test)");

    timedRunner.run(createParams(SDK_VERSION, SINGLE_EMPTY_OPTION, "com.example.SampleTest", true));

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    assertThat(testInfo.properties().get(ANDROID_INSTRUMENTATION_TEST_START_EPOCH_MS))
        .isEqualTo("1000");
    assertThat(testInfo.properties().get(ANDROID_INSTRUMENTATION_TEST_END_EPOCH_MS))
        .isEqualTo("1750");
    assertThat(new File(genFileDir, "junit.xml").isFile()).isTrue();
  }

  @Test
  public void run_failOnAllSkippedWhenAllTestsIgnored_setsFailResult() throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_FAIL_ON_ALL_SKIPPED, "true");
    stubRawInstrumentationLines(
        """
        INSTRUMENTATION_STATUS: class=com.example.IgnoredTest
        INSTRUMENTATION_STATUS: current=1
        INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
        INSTRUMENTATION_STATUS: numtests=1
        INSTRUMENTATION_STATUS: test=testIgnored
        INSTRUMENTATION_STATUS_CODE: 1
        INSTRUMENTATION_STATUS: class=com.example.IgnoredTest
        INSTRUMENTATION_STATUS: current=1
        INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
        INSTRUMENTATION_STATUS: numtests=1
        INSTRUMENTATION_STATUS: test=testIgnored
        INSTRUMENTATION_STATUS_CODE: -3
        INSTRUMENTATION_CODE: -1
        """,
        "OK (1 test)");

    runner.run(createParams(SDK_VERSION, SINGLE_EMPTY_OPTION, "com.example.IgnoredTest", true));

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.FAIL);
    assertThat(testInfo.resultWithCause().get().causeExceptionNonEmpty())
        .hasMessageThat()
        .contains("All tests were ignored or assumption failures.");
    assertThat(testInfo.properties().has(PropertyName.Test.HALT_RETRY)).isFalse();
  }

  @Test
  public void run_failOnAllSkippedWhenNoTestsRan_setsFailResultAndHaltsRetry() throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_FAIL_ON_ALL_SKIPPED, "true");
    stubRawInstrumentationLines(
        "INSTRUMENTATION_STATUS: numtests=0\nINSTRUMENTATION_CODE: -1", "OK (0 tests)");

    runner.run(createParams(SDK_VERSION, SINGLE_EMPTY_OPTION, "com.example.EmptyShardTest", true));

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.FAIL);
    assertThat(testInfo.properties().get(PropertyName.Test.HALT_RETRY)).isEqualTo("true");
  }

  @Test
  public void run_beforeIterationAdbFails_throwsAdbCommandError() throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_0", "k=v");
    jobInfo
        .params()
        .add(
            AndroidInstrumentationDriverSpec.PARAM_ITER_ADB_SHELL_BEFORE_INSTRUMENTATION + "_0",
            "bad_cmd");
    when(adb.runShell(DEVICE_ID, "bad_cmd", null, null))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "adb err"));

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> runner.run(createDefaultParams()));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_INSTRUMENTATION_ADB_COMMAND_ERROR);
  }

  @Test
  public void run_beforeIterationAdbFails_pullsInstrumentationFilesFromDevice() throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_0", "k=v");
    jobInfo
        .params()
        .add(
            AndroidInstrumentationDriverSpec.PARAM_ITER_ADB_SHELL_BEFORE_INSTRUMENTATION + "_0",
            "bad_cmd");
    when(adb.runShell(DEVICE_ID, "bad_cmd", null, null))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "adb err"));

    assertThrows(MobileHarnessException.class, () -> runner.run(createDefaultParams()));

    verify(androidInstrumentationUtil)
        .pullInstrumentationFilesFromDevice(DEVICE_ID, testInfo, EXTERNAL_STORAGE);
  }

  @Test
  public void run_afterIterationAdbFails_logsWarningAndStillPasses() throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_0", "k=v");
    jobInfo
        .params()
        .add(
            AndroidInstrumentationDriverSpec.PARAM_ITER_ADB_SHELL_AFTER_INSTRUMENTATION + "_0",
            "after_fail");
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), isNull()))
        .thenReturn("OK (1 test)");
    when(adb.runShell(DEVICE_ID, "after_fail", null, null))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "adb err"));

    runner.run(createDefaultParams());

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    assertThat(testInfo.warnings().get(AndroidErrorId.ANDROID_INSTRUMENTATION_ADB_COMMAND_ERROR))
        .hasSize(1);
  }

  @Test
  public void run_clearPackageFails_throwsClearPackageError() throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_ITER_CLEAR_TEST, "true");
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), isNull()))
        .thenReturn("OK (1 test)");
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_PKG_MNGR_UTIL_CLEAR_PACKAGE_ERROR, "clear err"))
        .when(androidPackageManagerUtil)
        .clearPackage(DEVICE_ID, TEST_PACKAGE);

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () ->
                runner.run(
                    createParams(
                        SDK_VERSION,
                        ImmutableList.of(
                            Optional.of(ImmutableMap.of()), Optional.of(ImmutableMap.of())),
                        null,
                        false)));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_INSTRUMENTATION_CLEAR_PACKAGE_ERROR);
  }

  @Test
  public void run_writeLogFileFails_recordsWarningAndSetsPass() throws Exception {
    tempFolder.newFolder("gen", "instrument.log");
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), isNull()))
        .thenReturn("OK (1 test)");

    runner.run(createDefaultParams());

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    assertThat(testInfo.warnings().get(AndroidErrorId.ANDROID_INSTRUMENTATION_WRITE_FILE_ERROR))
        .hasSize(1);
  }

  @Test
  public void run_errorWhenDeviceOffline_setsDeviceOfflineErrorId() throws Exception {
    when(systemStateManager.isOnline(DEVICE_ID)).thenReturn(false);
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), isNull()))
        .thenReturn(
            "INSTRUMENTATION_STATUS: Error=Unable to find instrumentation info\n"
                + "INSTRUMENTATION_STATUS_CODE: -1");

    runner.run(createDefaultParams());

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
    assertThat(testInfo.resultWithCause().get().causeExceptionNonEmpty().getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_DEVICE_OFFLINE);
  }

  @Test
  public void run_syncInstrumentationTimeout_setsTimeoutResultAndThrows() throws Exception {
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), isNull()))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_TIMEOUT, "timed out"));

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> runner.run(createDefaultParams()));

    assertThat(thrown.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_TIMEOUT);
    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.TIMEOUT);
  }

  @Test
  public void run_gtestParserWithoutDeviceXmlParam_throwsGtestXmlNotSetError() throws Exception {
    jobInfo
        .params()
        .add(AndroidInstrumentationDriverSpec.PARAM_USE_ANDROIDINSTRUMENTATION_PARSER, "false");
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), isNull()))
        .thenReturn("done");

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> runner.run(createDefaultParams()));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_INSTRUMENTATION_GTEST_XML_FILE_ON_DEVICE_NOT_SET);
  }

  @Test
  public void run_gtestParserReturnsFailWithErrorMessage_unwrapsOptionalIntoFailureCause()
      throws Exception {
    jobInfo
        .params()
        .add(AndroidInstrumentationDriverSpec.PARAM_USE_ANDROIDINSTRUMENTATION_PARSER, "false");
    jobInfo
        .params()
        .add(AndroidInstrumentationDriverSpec.PARAM_GTEST_XML_FILE_ON_DEVICE, "/sdcard/gtest.xml");
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), isNull()))
        .thenReturn("done");
    when(androidInstrumentationUtil.processGtestResult(
            DEVICE_ID, testInfo, "/sdcard/gtest.xml", null))
        .thenReturn(
            AndroidInstrumentationUtil.GtestResult.of(
                TestResult.FAIL, Optional.of("gtest failure details")));

    runner.run(createDefaultParams());

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.FAIL);
    String failureMessage = testInfo.resultWithCause().get().causeExceptionNonEmpty().getMessage();
    assertThat(failureMessage).contains("gtest failure details");
    assertThat(failureMessage).doesNotContain("Optional[");
  }

  @Test
  public void run_gtestParserReturnsUnknownResult_setsErrorAndThrowsResultNotFound()
      throws Exception {
    jobInfo
        .params()
        .add(AndroidInstrumentationDriverSpec.PARAM_USE_ANDROIDINSTRUMENTATION_PARSER, "false");
    jobInfo
        .params()
        .add(AndroidInstrumentationDriverSpec.PARAM_GTEST_XML_FILE_ON_DEVICE, "/sdcard/gtest.xml");
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), isNull()))
        .thenReturn("done");
    when(androidInstrumentationUtil.processGtestResult(
            DEVICE_ID, testInfo, "/sdcard/gtest.xml", null))
        .thenReturn(
            AndroidInstrumentationUtil.GtestResult.of(TestResult.UNKNOWN, Optional.empty()));

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> runner.run(createDefaultParams()));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_RESULT_NOT_FOUND);
    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
  }

  @Test
  public void run_showRawResultsWhenJunitXmlPathIsDirectory_throwsWriteJunitXmlError()
      throws Exception {
    tempFolder.newFolder("gen", "junit.xml");
    stubRawInstrumentationLines("INSTRUMENTATION_CODE: -1", "OK (0 tests)");

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () -> runner.run(createParams(SDK_VERSION, SINGLE_EMPTY_OPTION, null, true)));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_INSTRUMENTATION_WRITE_JUNIT_XML_ERROR);
  }

  private AndroidInstrumentationRunner createRunnerWithClock(Clock clock) {
    return new AndroidInstrumentationRunner(
        adb,
        systemStateManager,
        fileUtil,
        androidInstrumentationUtil,
        androidPackageManagerUtil,
        clock);
  }

  private void stubRawInstrumentationLines(String rawLines, String summaryOutput)
      throws MobileHarnessException, InterruptedException {
    doAnswer(
            invocation -> {
              Supplier<LineCallback> callbackSupplier = invocation.getArgument(4);
              LineCallback callback = callbackSupplier.get();
              for (String line : Splitter.on('\n').split(rawLines)) {
                callback.onLine(line);
              }
              return summaryOutput;
            })
        .when(androidInstrumentationUtil)
        .instrument(eq(DEVICE_ID), eq(SDK_VERSION), any(), any(), any());
  }

  private ExecutionParams createDefaultParams() {
    return createParams(
        SDK_VERSION, SINGLE_EMPTY_OPTION, /* testTarget= */ null, /* showRawResults= */ false);
  }

  private ExecutionParams createParams(
      int sdkVersion,
      ImmutableList<Optional<ImmutableMap<String, String>>> optionMaps,
      @Nullable String testTarget,
      boolean showRawResults) {
    return ExecutionParams.builder()
        .setTestInfo(testInfo)
        .setDeviceId(DEVICE_ID)
        .setDeviceSdkVersion(sdkVersion)
        .setTestPackageName(TEST_PACKAGE)
        .setRunnerName(RUNNER_NAME)
        .setBuildPackageNames(ImmutableList.of(BUILD_PACKAGE))
        .setExternalStoragePath(EXTERNAL_STORAGE)
        .setPreparedOptions(PreparedInstrumentationOptions.create(optionMaps, testTarget))
        .setShowRawResults(showRawResults)
        .build();
  }

  private static final class AdvancingClock extends Clock {
    private Instant nextInstant;
    private final Duration step;

    AdvancingClock(Instant initialInstant, Duration step) {
      this.nextInstant = initialInstant;
      this.step = step;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      Instant current = nextInstant;
      nextInstant = nextInstant.plus(step);
      return current;
    }
  }
}
