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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.services.storage.TestStorageConstants;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flags.testing.SetFlags;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil.AppInstallFinishHandler;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil.AppInstallStartHandler;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.systemsetting.SystemSettingManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidInstrumentationDriverSpec;
import com.google.wireless.qa.mobileharness.shared.api.step.android.InstallApkStep;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfoMocker;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AndroidInstrumentationTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Device device;
  @Bind @Mock private AndroidFileUtil androidFileUtil;
  @Mock private AndroidPackageManagerUtil androidPackageManagerUtil;
  @Mock private Adb adb;
  @Mock private Aapt aapt;
  @Mock private ApkInstaller apkInstaller;
  @Mock private SystemSettingManager systemSettingManager;
  @Mock private AndroidInstrumentationUtil androidInstrumentationUtil;
  @Mock private LocalFileUtil fileUtil;
  @Mock private TestMessageUtil testMessageUtil;
  @Bind @Mock private Sleeper sleeper;
  private final TestInfo testInfo = TestInfoMocker.mockTestInfo();
  private final JobInfo jobInfo = spy(testInfo.jobInfo());

  @Mock private InstallApkStep installApkStep;

  @Rule public final SetFlags flags = new SetFlags();

  private static final String RUNNER = "android.test.InstrumentationTestRunner";
  private static final String OPTIONS = "  key1 = value1\t,     key2 = value2  , ,   ";

  // NOTICE: the map entries here should match the `OPTIONS` value above.
  private static final ImmutableMap<String, String> OPTION_MAP =
      ImmutableMap.of("key1", "value1", "key2", "value2");

  private static final String[] BUILD_PACKAGES =
      new String[] {"com.google.spinner", "com.google.dependency"};

  private static final String TEST_APK_PATH = "/tmp/SpinnerTest/bin/SpinnerTest.apk";
  private static final String TEST_PACKAGE = "com.google.spinner.test";
  private static final String TEST_CLASS_NAME = "SpinnerTest";
  private static final String TEST_METHOD_NAME = "testText";
  private static final String TEST_NAME =
      TEST_PACKAGE + "." + TEST_CLASS_NAME + "#" + TEST_METHOD_NAME;
  private static final String JOB_GEN_FILE_DIR_PATH = "/job/gen/file/dir";
  private static final String GEN_FILE_DIR_PATH = JOB_GEN_FILE_DIR_PATH + "/test/gen/file/dir";
  private static final String TMP_FILE_DIR_PATH = "/test/tmp/file/dir";
  private static final String DEFAULT_INSTRUMENTATION_LOG_FILE_NAME = "instrument.log";

  private static final String DEVICE_ID = "device_id";
  private static final int DEFAULT_SDK_VERSION = 28;
  private static final String EXTERNAL_STORAGE = "/mnt/sdcard";
  private static final Duration TEST_TIMEOUT = Duration.ofSeconds(30);

  private AndroidInstrumentation driver;

  @Before
  public void setUp() throws Exception {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    driver =
        new AndroidInstrumentation(
            device,
            testInfo,
            androidPackageManagerUtil,
            adb,
            aapt,
            apkInstaller,
            systemSettingManager,
            fileUtil,
            testMessageUtil,
            androidInstrumentationUtil,
            installApkStep);
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(DEFAULT_SDK_VERSION);
    when(androidInstrumentationUtil.getTestArgs(any())).thenReturn(ImmutableMap.of());
  }

  @Test
  public void simpleRun_pass() throws Exception {
    mockRunTestBasicSteps(TEST_NAME, OPTIONS, false, false);
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_PREFIX_ANDROID_TEST, "true");
    String instrumentationLog =
        "com.google.codelab.mobileharness.android.hellomobileharness"
            + ".HelloMobileHarnessTest:.......\n\nTime: 31.281\n\nOK (7 tests)";
    AndroidInstrumentationSetting setting =
        mockInstrumentSetting(
            TEST_NAME,
            OPTION_MAP,
            /* async= */ false,
            /* showRawResults= */ false,
            /* prefixAndroidTest= */ true,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ true);
    when(androidInstrumentationUtil.instrument(
            DEVICE_ID, DEFAULT_SDK_VERSION, setting, TEST_TIMEOUT, null))
        .thenReturn(instrumentationLog);

    driver.run(testInfo);

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    verify(fileUtil)
        .writeToFile(
            PathUtil.join(GEN_FILE_DIR_PATH, DEFAULT_INSTRUMENTATION_LOG_FILE_NAME),
            instrumentationLog);
    verify(androidInstrumentationUtil)
        .prepareServicesApks(eq(testInfo), eq(device), eq(DEFAULT_SDK_VERSION), any(), any());
    verify(androidInstrumentationUtil).enableLegacyStorageForApk(DEVICE_ID, TEST_PACKAGE);
  }

  @Test
  public void run_installationFailSetResultFail() throws Exception {
    mockRunTestBasicSteps(TEST_NAME, OPTIONS, false, false);
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_PREFIX_ANDROID_TEST, "true");

    MobileHarnessException fakeException =
        new MobileHarnessException(AndroidErrorId.ANDROID_FASTBOOT_UNKNOWN_SLOT, "test123");
    doThrow(fakeException)
        .when(androidInstrumentationUtil)
        .prepareServicesApks(eq(testInfo), eq(device), eq(DEFAULT_SDK_VERSION), any(), any());

    assertThrows(MobileHarnessException.class, () -> driver.run(testInfo));
  }

  @Test
  public void run_fail() throws Exception {
    mockRunTestBasicSteps(TEST_NAME, OPTIONS, false, false);
    String instrumentationLog =
        " com.google.android.ads.GADSignalsTest:\nFAILURES!!!\nTests run: 1,  Failures: 1";
    AndroidInstrumentationSetting setting =
        mockInstrumentSetting(
            TEST_NAME,
            OPTION_MAP,
            /* async= */ false,
            /* showRawResults= */ false,
            /* prefixAndroidTest= */ false,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ true);
    when(androidInstrumentationUtil.instrument(
            DEVICE_ID, DEFAULT_SDK_VERSION, setting, TEST_TIMEOUT, null))
        .thenReturn(instrumentationLog);

    driver.run(testInfo);

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.FAIL);
    verify(fileUtil)
        .writeToFile(
            PathUtil.join(GEN_FILE_DIR_PATH, DEFAULT_INSTRUMENTATION_LOG_FILE_NAME),
            instrumentationLog);
  }

  @Test
  public void run_noIsolatedStorage() throws Exception {
    mockRunTestBasicSteps(TEST_NAME, OPTIONS, false, true);
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_PREFIX_ANDROID_TEST, "true");
    String instrumentationLog =
        "com.google.codelab.mobileharness.android.hellomobileharness"
            + ".HelloMobileHarnessTest:.......\n\nTime: 31.281\n\nOK (7 tests)";
    AndroidInstrumentationSetting setting =
        mockInstrumentSetting(
            TEST_NAME,
            OPTION_MAP,
            /* async= */ false,
            /* showRawResults= */ false,
            /* prefixAndroidTest= */ true,
            /* noIsolatedStorage= */ true,
            /* useTestStorageService= */ true);
    when(androidInstrumentationUtil.instrument(
            DEVICE_ID, DEFAULT_SDK_VERSION, setting, TEST_TIMEOUT, null))
        .thenReturn(instrumentationLog);

    driver.run(testInfo);

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    verify(fileUtil)
        .writeToFile(
            PathUtil.join(GEN_FILE_DIR_PATH, DEFAULT_INSTRUMENTATION_LOG_FILE_NAME),
            instrumentationLog);
  }

  @Test
  public void run_showRawData_pass() throws Exception {
    mockRunTestBasicSteps(TEST_PACKAGE + "." + TEST_CLASS_NAME, OPTIONS, true, false);
    when(androidInstrumentationUtil.showRawResultsIfNeeded(testInfo)).thenReturn(true);
    String instrumentationLog =
        "INSTRUMENTATION_STATUS: numtests=1\n"
            + "INSTRUMENTATION_STATUS: stream=.\n"
            + "INSTRUMENTATION_STATUS: id=AndroidJUnitRunner\n"
            + "INSTRUMENTATION_STATUS: test=testText\n"
            + "INSTRUMENTATION_STATUS: class=com.google.codelab.mobileharness."
            + "android.hellomobileharness.HelloMobileHarnessTest\n"
            + "INSTRUMENTATION_STATUS: current=1\n"
            + "INSTRUMENTATION_STATUS_CODE: 0\n"
            + "INSTRUMENTATION_RESULT: stream=\n\nTime: 35.204\n\nOK (7 tests)";
    AndroidInstrumentationSetting setting =
        mockInstrumentSetting(
            TEST_PACKAGE + "." + TEST_CLASS_NAME,
            OPTION_MAP,
            /* async= */ false,
            /* showRawResults= */ true,
            /* prefixAndroidTest= */ false,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ true);
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID),
            eq(DEFAULT_SDK_VERSION),
            eq(setting),
            eq(TEST_TIMEOUT),
            ArgumentMatchers.isNotNull()))
        .thenReturn(instrumentationLog);

    driver.run(testInfo);

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    verify(fileUtil)
        .writeToFile(
            PathUtil.join(GEN_FILE_DIR_PATH, DEFAULT_INSTRUMENTATION_LOG_FILE_NAME),
            instrumentationLog);
    verify(fileUtil)
        .writeToFile(
            eq(PathUtil.join(GEN_FILE_DIR_PATH, "instrument_test_result.textproto")),
            any(String.class));
    verify(fileUtil)
        .writeToFile(
            eq(PathUtil.join(GEN_FILE_DIR_PATH, "instrument_test_result.pb")), any(byte[].class));
  }

  @Test
  public void run_instrumentReturnCodeError_fail() throws Exception {
    mockRunTestBasicSteps(TEST_NAME, OPTIONS, false, false);

    String instrumentationLog =
        "INSTRUMENTATION_STATUS: class=com.google.Foo\n"
            + "INSTRUMENTATION_STATUS: current=1\n"
            + "INSTRUMENTATION_STATUS: id=AndroidJUnitRunner\n"
            + "INSTRUMENTATION_STATUS: numtests=1\n"
            + "INSTRUMENTATION_STATUS: stream=\n"
            + "INSTRUMENTATION_STATUS: test=barTest\n"
            + "INSTRUMENTATION_STATUS_CODE: 1\n"
            + "INSTRUMENTATION_RESULT: shortMsg=java.lang.RuntimeException\n"
            + "INSTRUMENTATION_RESULT: longMsg=java.lang.RuntimeException: No content..\n"
            + "INSTRUMENTATION_CODE: 0";
    AndroidInstrumentationSetting setting =
        mockInstrumentSetting(
            TEST_NAME,
            OPTION_MAP,
            /* async= */ false,
            /* showRawResults= */ false,
            /* prefixAndroidTest= */ false,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ true);
    when(androidInstrumentationUtil.instrument(
            DEVICE_ID, DEFAULT_SDK_VERSION, setting, TEST_TIMEOUT, null))
        .thenReturn(instrumentationLog);

    driver.run(testInfo);

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.FAIL);
    verify(fileUtil)
        .writeToFile(
            PathUtil.join(GEN_FILE_DIR_PATH, DEFAULT_INSTRUMENTATION_LOG_FILE_NAME),
            instrumentationLog);
  }

  @Test
  public void run_instrumentStatusError_fail() throws Exception {
    mockRunTestBasicSteps(TEST_NAME, OPTIONS, false, false);
    String instrumentationLog =
        "INSTRUMENTATION_STATUS: id=ActivityManagerService\n"
            + "INSTRUMENTATION_STATUS: Error=Permission Denial: starting instrumentation "
            + "ComponentInfo{com.google.android.apps.userpanel.integration/com.google.android.apps"
            + ".common.testing.testrunner.Google3InstrumentationTestRunner} from pid=14392, "
            + "uid=14392 not allowed because package com.google.android.apps.userpanel.integration"
            + " does not have a signature matching the target com.google.android.apps.userpanel\n"
            + "INSTRUMENTATION_STATUS_CODE: -1\n"
            + "java.lang.SecurityException: Permission Denial: starting instrumentation "
            + "ComponentInfo{com.google.android.apps.userpanel.integration/com.google.android.apps"
            + ".common.testing.testrunner.Google3InstrumentationTestRunner} from pid=14392, "
            + "uid=14392 not allowed because package com.google.android.apps.userpanel.integration"
            + " does not have a signature matching the target com.google.android.apps.userpanel\n"
            + "at android.os.Parcel.readException(Parcel.java:1465)\n"
            + "at android.os.Parcel.readException(Parcel.java:1419)\n"
            + "at android.app.ActivityManagerProxy.startInstrumentation"
            + "(ActivityManagerNative.java:3188)\n"
            + "at com.android.commands.am.Am.runInstrument(Am.java:864)\n"
            + "at com.android.commands.am.Am.onRun(Am.java:282)\n"
            + "at com.android.internal.os.BaseCommand.run(BaseCommand.java:47)\n"
            + "at com.android.commands.am.Am.main(Am.java:76)\n"
            + "at com.android.internal.os.RuntimeInit.nativeFinishInit(Native Method)\n"
            + "at com.android.internal.os.RuntimeInit.main(RuntimeInit.java:243)\n"
            + "at dalvik.system.NativeStart.main(Native Method)";
    AndroidInstrumentationSetting setting =
        mockInstrumentSetting(
            TEST_NAME,
            OPTION_MAP,
            /* async= */ false,
            /* showRawResults= */ false,
            /* prefixAndroidTest= */ false,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ true);
    when(androidInstrumentationUtil.instrument(
            DEVICE_ID, DEFAULT_SDK_VERSION, setting, TEST_TIMEOUT, null))
        .thenReturn(instrumentationLog);

    driver.run(testInfo);

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
    verify(fileUtil)
        .writeToFile(
            PathUtil.join(GEN_FILE_DIR_PATH, DEFAULT_INSTRUMENTATION_LOG_FILE_NAME),
            instrumentationLog);
  }

  @Test
  public void runBroadcastInstallMessage() throws Exception {
    mockRunTestBasicSteps(TEST_NAME, OPTIONS, false, false);
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_BROADCAST_INSTALL_MESSAGE, "true");
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_PREFIX_ANDROID_TEST, "true");
    String instrumentationLog =
        "com.google.codelab.mobileharness.android.hellomobileharness"
            + ".HelloMobileHarnessTest:.......\n\nTime: 31.281\n\nOK (7 tests)";
    AndroidInstrumentationSetting setting =
        mockInstrumentSetting(
            TEST_NAME,
            OPTION_MAP,
            /* async= */ false,
            /* showRawResults= */ false,
            /* prefixAndroidTest= */ true,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ true);
    when(androidInstrumentationUtil.instrument(
            DEVICE_ID, DEFAULT_SDK_VERSION, setting, TEST_TIMEOUT, null))
        .thenReturn(instrumentationLog);

    driver.run(testInfo);

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    // 2 times for each of test apk, basic services apk, test services apk
    verify(testMessageUtil, times(6))
        .sendMessageToTest(eq(TestInfoMocker.FAKE_TEST_ID), ArgumentMatchers.anyMap());
  }

  @Test
  public void runError() throws Exception {
    mockRunTestBasicSteps(TEST_NAME, OPTIONS, false, false);
    String instrumentationLog = "Other error message";
    AndroidInstrumentationSetting setting =
        mockInstrumentSetting(
            TEST_NAME,
            OPTION_MAP,
            /* async= */ false,
            /* showRawResults= */ false,
            /* prefixAndroidTest= */ false,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ true);
    when(androidInstrumentationUtil.instrument(
            DEVICE_ID, DEFAULT_SDK_VERSION, setting, TEST_TIMEOUT, null))
        .thenReturn(instrumentationLog);

    driver.run(testInfo);

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.ERROR);
    verify(fileUtil)
        .writeToFile(
            PathUtil.join(GEN_FILE_DIR_PATH, DEFAULT_INSTRUMENTATION_LOG_FILE_NAME),
            instrumentationLog);
  }

  @Test
  public void runTimeout() throws Exception {
    mockRunTestBasicSteps(TEST_NAME, OPTIONS, false, false);
    MobileHarnessException exception =
        new MobileHarnessException(
            AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_TIMEOUT, "timeout");
    AndroidInstrumentationSetting setting =
        mockInstrumentSetting(
            TEST_NAME,
            OPTION_MAP,
            /* async= */ false,
            /* showRawResults= */ false,
            /* prefixAndroidTest= */ false,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ true);
    when(androidInstrumentationUtil.instrument(
            DEVICE_ID, DEFAULT_SDK_VERSION, setting, TEST_TIMEOUT, null))
        .thenThrow(exception);

    assertThrows(MobileHarnessException.class, () -> driver.run(testInfo));
    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.TIMEOUT);
  }

  @Test
  public void runWithDefaultRunner() throws Exception {
    mockRunTestBasicSteps(TEST_NAME, OPTIONS, false, false);
    String instrumentationLog = "OK (1 test)";
    AndroidInstrumentationSetting setting =
        mockInstrumentSetting(
            TEST_NAME,
            OPTION_MAP,
            /* async= */ false,
            /* showRawResults= */ false,
            /* prefixAndroidTest= */ false,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ true);
    when(androidInstrumentationUtil.instrument(
            DEVICE_ID, DEFAULT_SDK_VERSION, setting, TEST_TIMEOUT, null))
        .thenReturn(instrumentationLog);

    driver.run(testInfo);

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    verify(fileUtil)
        .writeToFile(
            PathUtil.join(GEN_FILE_DIR_PATH, DEFAULT_INSTRUMENTATION_LOG_FILE_NAME),
            instrumentationLog);
  }

  @Test
  public void runAllMediumTests() throws Exception {
    mockRunTestBasicSteps(AndroidInstrumentationDriverSpec.TEST_NAME_MEDIUM, OPTIONS, false, false);
    HashMap<String, String> optionMap2 = new HashMap<>(OPTION_MAP);
    optionMap2.put("size", AndroidInstrumentationDriverSpec.TEST_NAME_MEDIUM);
    String instrumentationLog = "OK (1 test)";
    AndroidInstrumentationSetting setting =
        mockInstrumentSetting(
            /* className= */ null,
            optionMap2,
            /* async= */ false,
            /* showRawResults= */ false,
            /* prefixAndroidTest= */ false,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ true);
    when(androidInstrumentationUtil.instrument(
            DEVICE_ID, DEFAULT_SDK_VERSION, setting, TEST_TIMEOUT, null))
        .thenReturn(instrumentationLog);

    driver.run(testInfo);

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    verify(fileUtil)
        .writeToFile(
            PathUtil.join(GEN_FILE_DIR_PATH, DEFAULT_INSTRUMENTATION_LOG_FILE_NAME),
            instrumentationLog);
  }

  @Test
  public void optionMapWithCommaRunPass() throws Exception {
    mockRunTestBasicSteps(TEST_NAME, "key3=value\\,3", false, false);
    ImmutableMap<String, String> optionMap2 = ImmutableMap.of("key3", "value,3");
    String instrumentationLog = "OK (1 test)";
    AndroidInstrumentationSetting setting =
        mockInstrumentSetting(
            TEST_NAME,
            optionMap2,
            /* async= */ false,
            /* showRawResults= */ false,
            /* prefixAndroidTest= */ false,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ true);
    when(androidInstrumentationUtil.instrument(
            DEVICE_ID, DEFAULT_SDK_VERSION, setting, TEST_TIMEOUT, null))
        .thenReturn(instrumentationLog);

    driver.run(testInfo);

    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
    verify(fileUtil)
        .writeToFile(
            PathUtil.join(GEN_FILE_DIR_PATH, DEFAULT_INSTRUMENTATION_LOG_FILE_NAME),
            instrumentationLog);
  }

  private void mockRunTestBasicSteps(
      String testName, String options, boolean parseResultToMethod, boolean noIsolatedStorage)
      throws Exception {
    when(installApkStep.installBuildApks(device, testInfo))
        .thenReturn(Arrays.asList(BUILD_PACKAGES));

    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    TestLocator testLocator = spy(testInfo.locator());
    when(testInfo.locator()).thenReturn(testLocator);
    when(testLocator.getName()).thenReturn(testName);
    CountDownTimer testTimer = spy(testInfo.timer());
    when(testInfo.timer()).thenReturn(testTimer);
    when(testTimer.remainingTimeJava()).thenReturn(TEST_TIMEOUT);
    when(androidInstrumentationUtil.getBuildApks(any()))
        .thenReturn(ImmutableList.copyOf(BUILD_PACKAGES));
    jobInfo
        .params()
        .add(
            AndroidInstrumentationDriverSpec.PARAM_SPLIT_METHODS,
            Boolean.toString(parseResultToMethod));
    jobInfo
        .params()
        .add(
            AndroidInstrumentationDriverSpec.PARAM_DISABLE_ISOLATED_STORAGE_FOR_INSTRUMENTATION,
            Boolean.toString(noIsolatedStorage));

    jobInfo.files().add(AndroidInstrumentationDriverSpec.TAG_TEST_APK, TEST_APK_PATH);
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_OPTIONS, options);
    JobSetting jobSetting = spy(jobInfo.setting());
    doReturn(jobSetting).when(jobInfo).setting();
    when(jobSetting.getGenFileDir()).thenReturn(JOB_GEN_FILE_DIR_PATH);
    when(testInfo.getTmpFileDir()).thenReturn(TMP_FILE_DIR_PATH);
    String hostArgFilePath =
        PathUtil.join(TMP_FILE_DIR_PATH, TestStorageConstants.TEST_ARGS_FILE_NAME);
    when(fileUtil.writeToFile(eq(hostArgFilePath), (byte[]) any())).thenReturn(123L);

    when(androidInstrumentationUtil.getTestSpecificTestArgs(testInfo)).thenReturn(new HashMap<>());
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_ASYNC, "false");

    when(aapt.getApkPackageName(TEST_APK_PATH)).thenReturn(TEST_PACKAGE);
    when(androidInstrumentationUtil.cleanTestStorageOnDevice(
            testInfo, DEVICE_ID, DEFAULT_SDK_VERSION))
        .thenReturn(Optional.of(EXTERNAL_STORAGE));

    // Removes google test dir on device.
    when(testInfo.getGenFileDir()).thenReturn(GEN_FILE_DIR_PATH);

    // Installs apks.
    when(apkInstaller.installApkIfNotExist(
            eq(device),
            eq(
                ApkInstallArgs.builder()
                    .setApkPath(TEST_APK_PATH)
                    .setGrantPermissions(true)
                    .setSkipDowngrade(false)
                    .build()),
            any()))
        .thenReturn("test_package_name");

    doAnswer(
            invocation -> {
              TestInfo testInfo = invocation.getArgument(0, TestInfo.class);
              AppInstallStartHandler appInstallStartHandler =
                  invocation.getArgument(3, AppInstallStartHandler.class);
              AppInstallFinishHandler appInstallFinishHandler =
                  invocation.getArgument(4, AppInstallFinishHandler.class);

              appInstallStartHandler.onAppInstallStart(testInfo, "fake_basic_services_package");
              appInstallStartHandler.onAppInstallStart(testInfo, "fake_test_services_package");
              appInstallFinishHandler.onAppInstallFinish(testInfo, "fake_basic_services_package");
              appInstallFinishHandler.onAppInstallFinish(testInfo, "fake_test_services_package");
              return null;
            })
        .when(androidInstrumentationUtil)
        .prepareServicesApks(any(), any(), anyInt(), any(), any());

    when(androidInstrumentationUtil.getTestRunnerClassName(
            any(), any(), any(), any(), anyBoolean()))
        .thenReturn(RUNNER);
  }

  @Test
  public void extraOptions() throws Exception {
    final String hubPortKey = "hub_port";
    final String hubPort = "50008";
    mockRunTestBasicSteps(TEST_NAME, OPTIONS, false, false);
    jobInfo
        .params()
        .add(AndroidInstrumentationDriverSpec.PARAM_TEST_ARGS, "test_arg1=value1, test_arg2=a=b");
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_PREFIX_ANDROID_TEST, "true");
    testInfo
        .properties()
        .add(
            PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_EXTRA_OPTIONS,
            hubPortKey + "=" + hubPort);
    HashMap<String, String> optionMap2 = new HashMap<>(OPTION_MAP);
    optionMap2.put(hubPortKey, hubPort);
    AndroidInstrumentationSetting setting =
        mockInstrumentSetting(
            TEST_NAME,
            optionMap2,
            /* async= */ false,
            /* showRawResults= */ false,
            /* prefixAndroidTest= */ true,
            /* noIsolatedStorage= */ false,
            /* useTestStorageService= */ true);
    when(androidInstrumentationUtil.instrument(
            eq(DEVICE_ID),
            eq(DEFAULT_SDK_VERSION),
            eq(setting),
            eq(TEST_TIMEOUT),
            ArgumentMatchers.isNull()))
        .thenReturn("");

    driver.run(testInfo);

    verify(androidInstrumentationUtil)
        .instrument(
            eq(DEVICE_ID),
            eq(DEFAULT_SDK_VERSION),
            eq(setting),
            eq(TEST_TIMEOUT),
            ArgumentMatchers.isNull());
  }

  private AndroidInstrumentationSetting mockInstrumentSetting(
      @Nullable String className,
      @Nullable Map<String, String> otherOptions,
      boolean async,
      boolean showRawResults,
      boolean prefixAndroidTest,
      boolean noIsolatedStorage,
      boolean useTestStorageService) {
    return mockInstrumentSetting(
        className,
        otherOptions,
        async,
        showRawResults,
        prefixAndroidTest,
        noIsolatedStorage,
        useTestStorageService,
        /* enableCoverage= */ false);
  }

  private AndroidInstrumentationSetting mockInstrumentSetting(
      @Nullable String className,
      @Nullable Map<String, String> otherOptions,
      boolean async,
      boolean showRawResults,
      boolean prefixAndroidTest,
      boolean noIsolatedStorage,
      boolean useTestStorageService,
      boolean enableCoverage) {
    return AndroidInstrumentationSetting.create(
        TEST_PACKAGE,
        RUNNER,
        className,
        otherOptions,
        async,
        showRawResults,
        prefixAndroidTest,
        noIsolatedStorage,
        useTestStorageService,
        enableCoverage);
  }
}
