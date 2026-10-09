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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Enums.FastbootProperty;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.api.testrunner.step.android.DeviceInitializationStep;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.RebootMode;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.CommandTimeoutException;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager.Lease;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidFlashGkiDecoratorSpec;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public final class AndroidFlashGkiDecoratorTest {
  private static final String TMP_DIR = "/tmp/dir";
  private static final String TEST_BOOT_IMAGE_PATH = "path/to/boot.img";
  private static final long TEST_BOOT_IMAGE_SIZE = 1000L;
  private static final String TEST_SYSTEM_DLKM_IMAGE_PATH = "path/to/system_dlkm.img";
  private static final long TEST_SYSTEM_DLKM_IMAGE_SIZE = 2000L;
  private static final String TEST_DEVICE_ID = "123456789ABC";
  private static final String TEST_REMOTE_PROXY_DEVICE_ID = "localhost:12345";
  private static final String TEST_REMOTE_PROXY_FASTBOOT_SERIAL = "tcp:127.0.0.1:54321";
  private static final int TEST_SDK_VERSION = 30;

  private AndroidFlashGkiDecorator decorator;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Log mockLog;
  @Mock private Api mockApi;
  @Mock private JobInfo mockJobInfo;
  @Mock private Driver mockDriver;
  @Mock private Device mockDevice;
  @Mock private Files mockFiles;
  @Mock private CommandTimeoutException mockTimeoutException;

  @Mock private CommandExecutor mockCommandExecutor;
  @Mock private LocalFileUtil mockLocalFileUtil;
  @Mock private AndroidSystemStateUtil mockAndroidSystemStateUtil;
  @Mock private AndroidSystemSettingUtil mockAndroidSystemSettingUtil;
  @Mock private Fastboot mockFastboot;
  @Mock private Sleeper mockSleeper;
  @Mock private AndroidAdbUtil mockAndroidAdbUtil;
  @Mock private Adb mockAdb;
  @Mock private ApkInstaller mockApkInstaller;
  @Mock private DeviceInitializationStep mockDeviceInitializationStep;
  @Mock private QuotaManager mockQuotaManager;
  @Mock private DeviceCache mockDeviceCache;
  @Mock private Lease mockQuotaLease;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private TestInfo mockTestInfo;

  private InOrder inOrder;

  @Before
  public void setUp() throws Exception {
    when(mockTestInfo.jobInfo()).thenReturn(mockJobInfo);
    when(mockJobInfo.files()).thenReturn(mockFiles);
    when(mockTestInfo.getTmpFileDir()).thenReturn(TMP_DIR);
    when(mockDriver.getDevice()).thenReturn(mockDevice);
    when(mockAdb.runShell(any(), eq("ps -ef | grep [s]napuserd")))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "snapuserd not found"));
    when(mockTestInfo.log()).thenReturn(mockLog);
    when(mockLog.atInfo()).thenReturn(mockApi);
    when(mockLog.atWarning()).thenReturn(mockApi);
    when(mockApi.alsoTo(any(FluentLogger.class))).thenReturn(mockApi);
    when(mockQuotaManager.acquire(any(), anyInt())).thenReturn(mockQuotaLease);
    when(mockAndroidSystemStateUtil.becomeRoot(any())).thenReturn(true);
    when(mockAndroidSystemStateUtil.isOnline(any())).thenReturn(true);
    when(mockAndroidSystemSettingUtil.getDeviceSdkVersion(any())).thenReturn(TEST_SDK_VERSION);

    decorator =
        new AndroidFlashGkiDecorator(
            mockDriver,
            mockTestInfo,
            mockCommandExecutor,
            mockLocalFileUtil,
            mockAndroidSystemStateUtil,
            mockAndroidSystemSettingUtil,
            mockFastboot,
            mockSleeper,
            mockAndroidAdbUtil,
            mockAdb,
            mockApkInstaller,
            mockQuotaManager,
            mockDeviceCache,
            mockDeviceInitializationStep) {
          @Override
          public String[] getKernelVersionFromImage(TestInfo testInfo, File image) {
            return new String[] {"android16-6.12", "63", "16k"};
          }
        };
  }

  private void mockSpec(AndroidFlashGkiDecoratorSpec spec, String deviceId) throws Exception {
    when(mockJobInfo.combinedSpec(decorator, deviceId)).thenReturn(spec);
  }

  private static AndroidFlashGkiDecoratorSpec.Builder getDefaultTestSpecBuilder() {
    return AndroidFlashGkiDecoratorSpec.newBuilder()
        .setGkiBootImage(TEST_BOOT_IMAGE_PATH)
        .setSystemDlkmImage(TEST_SYSTEM_DLKM_IMAGE_PATH);
  }

  @Test
  public void testBasicFlashFlow() throws Exception {
    when(mockDevice.getDeviceId()).thenReturn(TEST_DEVICE_ID);
    mockSpec(getDefaultTestSpecBuilder().build(), TEST_DEVICE_ID);
    when(mockAndroidAdbUtil.getProperty(TEST_DEVICE_ID, AndroidProperty.SERIAL))
        .thenReturn(TEST_DEVICE_ID);
    when(mockLocalFileUtil.isFileExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(true);
    when(mockLocalFileUtil.isDirExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(false);
    when(mockLocalFileUtil.getFileSize(Path.of(TEST_BOOT_IMAGE_PATH)))
        .thenReturn(TEST_BOOT_IMAGE_SIZE);
    when(mockLocalFileUtil.isFileExist(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH))).thenReturn(true);
    when(mockLocalFileUtil.isDirExist(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH))).thenReturn(false);
    when(mockLocalFileUtil.getFileSize(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH)))
        .thenReturn(TEST_SYSTEM_DLKM_IMAGE_SIZE);
    when(mockFastboot.runWithRetry(any(), any(), any(Duration.class), any(boolean.class)))
        .thenReturn("OKAY [  0.001s]");
    when(mockFastboot.getVar(TEST_DEVICE_ID, FastbootProperty.IS_USERSPACE)).thenReturn("no");
    inOrder =
        Mockito.inOrder(
            mockFiles, mockAndroidSystemStateUtil, mockFastboot, mockAndroidSystemSettingUtil);

    decorator.run(mockTestInfo);
    inOrder.verify(mockAndroidSystemStateUtil).reboot(TEST_DEVICE_ID, RebootMode.BOOTLOADER);
    inOrder
        .verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID),
            eq(new String[] {"reboot", "fastboot"}),
            any(Duration.class),
            eq(false));
    inOrder
        .verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID),
            eq(new String[] {"flash", "system_dlkm", TEST_SYSTEM_DLKM_IMAGE_PATH}),
            any(Duration.class),
            eq(false));
    inOrder
        .verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID),
            eq(new String[] {"reboot", "bootloader"}),
            any(Duration.class),
            eq(false));
    inOrder
        .verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID),
            eq(new String[] {"flash", "boot", TEST_BOOT_IMAGE_PATH}),
            any(Duration.class),
            eq(false));
    inOrder.verify(mockFastboot).reboot(TEST_DEVICE_ID);
    inOrder.verify(mockAndroidSystemStateUtil).becomeRoot(TEST_DEVICE_ID);
    inOrder
        .verify(mockAndroidSystemSettingUtil)
        .setSystemTimeToHost(TEST_DEVICE_ID, TEST_SDK_VERSION);
    inOrder.verify(mockAndroidSystemStateUtil).becomeRoot(TEST_DEVICE_ID);
  }

  @Test
  public void testRemoteProxyDeviceFindFastbootSerial() throws Exception {
    when(mockDevice.getDeviceId()).thenReturn(TEST_REMOTE_PROXY_DEVICE_ID);
    mockSpec(getDefaultTestSpecBuilder().build(), TEST_REMOTE_PROXY_DEVICE_ID);
    when(mockAndroidAdbUtil.getProperty(TEST_REMOTE_PROXY_DEVICE_ID, AndroidProperty.SERIAL))
        .thenReturn(TEST_DEVICE_ID);
    when(mockCommandExecutor.exec(
            Mockito.argThat(
                command ->
                    command != null
                        && command.getExecutable().equals("pontis")
                        && command.getArguments().size() == 1
                        && command.getArguments().get(0).equals("trackdevices"))))
        .thenAnswer(
            new Answer<CommandResult>() {
              @Override
              public CommandResult answer(InvocationOnMock invocation) throws Throwable {
                Command command = invocation.getArgument(0);
                LineCallback stdoutCallback = command.getStdoutLineCallback().get();

                // Simulate lines being outputted
                stdoutCallback.onLine("fastboot device<" + TEST_DEVICE_ID + ">");
                stdoutCallback.onLine(TEST_DEVICE_ID);
                stdoutCallback.onLine("fastboot://localhost:54321");

                // Simulate the command failing after some output
                throw mockTimeoutException;
              }
            });
    when(mockLocalFileUtil.isFileExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(true);
    when(mockLocalFileUtil.isDirExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(false);
    when(mockLocalFileUtil.getFileSize(Path.of(TEST_BOOT_IMAGE_PATH)))
        .thenReturn(TEST_BOOT_IMAGE_SIZE);
    when(mockLocalFileUtil.isFileExist(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH))).thenReturn(true);
    when(mockLocalFileUtil.isDirExist(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH))).thenReturn(false);
    when(mockLocalFileUtil.getFileSize(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH)))
        .thenReturn(TEST_SYSTEM_DLKM_IMAGE_SIZE);
    when(mockFastboot.runWithRetry(any(), any(), any(Duration.class), any(boolean.class)))
        .thenReturn("OKAY [  0.001s]");
    when(mockFastboot.getVar(TEST_REMOTE_PROXY_FASTBOOT_SERIAL, FastbootProperty.IS_USERSPACE))
        .thenReturn("no");

    decorator.run(mockTestInfo);
    verify(mockFastboot)
        .runWithRetry(
            eq(TEST_REMOTE_PROXY_FASTBOOT_SERIAL),
            eq(new String[] {"flash", "boot", TEST_BOOT_IMAGE_PATH}),
            any(Duration.class),
            eq(false));
  }

  @Test
  public void testFlashLogicalPartitionFirstFalse() throws Exception {
    when(mockDevice.getDeviceId()).thenReturn(TEST_DEVICE_ID);
    mockSpec(
        AndroidFlashGkiDecoratorSpec.newBuilder()
            .setGkiBootImage(TEST_BOOT_IMAGE_PATH)
            .setSystemDlkmImage(TEST_SYSTEM_DLKM_IMAGE_PATH)
            .setFlashLogicalPartitionFirst(false)
            .build(),
        TEST_DEVICE_ID);
    when(mockAndroidAdbUtil.getProperty(TEST_DEVICE_ID, AndroidProperty.SERIAL))
        .thenReturn(TEST_DEVICE_ID);
    when(mockLocalFileUtil.isFileExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(true);
    when(mockLocalFileUtil.isDirExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(false);
    when(mockLocalFileUtil.getFileSize(Path.of(TEST_BOOT_IMAGE_PATH)))
        .thenReturn(TEST_BOOT_IMAGE_SIZE);
    when(mockLocalFileUtil.isFileExist(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH))).thenReturn(true);
    when(mockLocalFileUtil.isDirExist(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH))).thenReturn(false);
    when(mockLocalFileUtil.getFileSize(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH)))
        .thenReturn(TEST_SYSTEM_DLKM_IMAGE_SIZE);
    when(mockFastboot.runWithRetry(any(), any(), any(Duration.class), any(boolean.class)))
        .thenReturn("OKAY [  0.001s]");
    when(mockFastboot.getVar(TEST_DEVICE_ID, FastbootProperty.IS_USERSPACE)).thenReturn("no");
    inOrder =
        Mockito.inOrder(
            mockFiles, mockAndroidSystemStateUtil, mockFastboot, mockAndroidSystemSettingUtil);

    decorator.run(mockTestInfo);
    inOrder.verify(mockAndroidSystemStateUtil).reboot(TEST_DEVICE_ID, RebootMode.BOOTLOADER);
    inOrder
        .verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID),
            eq(new String[] {"flash", "boot", TEST_BOOT_IMAGE_PATH}),
            any(Duration.class),
            eq(false));
    inOrder
        .verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID),
            eq(new String[] {"reboot", "fastboot"}),
            any(Duration.class),
            eq(false));
    inOrder
        .verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID),
            eq(new String[] {"flash", "system_dlkm", TEST_SYSTEM_DLKM_IMAGE_PATH}),
            any(Duration.class),
            eq(false));
    inOrder
        .verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID),
            eq(new String[] {"reboot", "bootloader"}),
            any(Duration.class),
            eq(false));
    inOrder.verify(mockFastboot).reboot(TEST_DEVICE_ID);
    inOrder.verify(mockAndroidSystemStateUtil).becomeRoot(TEST_DEVICE_ID);
    inOrder
        .verify(mockAndroidSystemSettingUtil)
        .setSystemTimeToHost(TEST_DEVICE_ID, TEST_SDK_VERSION);
    inOrder.verify(mockAndroidSystemStateUtil).becomeRoot(TEST_DEVICE_ID);
  }

  @Test
  public void testFlashGkiWithAdditionalFastbootCommand() throws Exception {
    when(mockDevice.getDeviceId()).thenReturn(TEST_DEVICE_ID);
    mockSpec(
        AndroidFlashGkiDecoratorSpec.newBuilder()
            .setGkiBootImage(TEST_BOOT_IMAGE_PATH)
            .setSystemDlkmImage(TEST_SYSTEM_DLKM_IMAGE_PATH)
            .addAdditionalFastbootCommand("erase misc")
            .addAdditionalFastbootCommand("")
            .build(),
        TEST_DEVICE_ID);
    when(mockAndroidAdbUtil.getProperty(TEST_DEVICE_ID, AndroidProperty.SERIAL))
        .thenReturn(TEST_DEVICE_ID);
    when(mockLocalFileUtil.isFileExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(true);
    when(mockLocalFileUtil.isDirExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(false);
    when(mockLocalFileUtil.getFileSize(Path.of(TEST_BOOT_IMAGE_PATH)))
        .thenReturn(TEST_BOOT_IMAGE_SIZE);
    when(mockLocalFileUtil.isFileExist(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH))).thenReturn(true);
    when(mockLocalFileUtil.isDirExist(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH))).thenReturn(false);
    when(mockLocalFileUtil.getFileSize(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH)))
        .thenReturn(TEST_SYSTEM_DLKM_IMAGE_SIZE);
    when(mockFastboot.runWithRetry(any(), any(), any(Duration.class), any(boolean.class)))
        .thenReturn("OKAY [  0.001s]");
    when(mockFastboot.getVar(TEST_DEVICE_ID, FastbootProperty.IS_USERSPACE)).thenReturn("no");

    decorator.run(mockTestInfo);
    verify(mockFastboot, never())
        .runWithRetry(eq(TEST_DEVICE_ID), eq(new String[] {""}), any(Duration.class), eq(false));
    verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID), eq(new String[] {"erase", "misc"}), any(Duration.class), eq(false));
    verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID),
            eq(new String[] {"flash", "boot", TEST_BOOT_IMAGE_PATH}),
            any(Duration.class),
            eq(false));
    verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID),
            eq(new String[] {"flash", "system_dlkm", TEST_SYSTEM_DLKM_IMAGE_PATH}),
            any(Duration.class),
            eq(false));
  }

  @Test
  public void testFlashGkiWithAdditionalFastbootdCommand() throws Exception {
    when(mockDevice.getDeviceId()).thenReturn(TEST_DEVICE_ID);
    mockSpec(
        AndroidFlashGkiDecoratorSpec.newBuilder()
            .setGkiBootImage(TEST_BOOT_IMAGE_PATH)
            .setSystemDlkmImage(TEST_SYSTEM_DLKM_IMAGE_PATH)
            .addAdditionalFastbootdCommand("")
            .addAdditionalFastbootdCommand("getvar all")
            .build(),
        TEST_DEVICE_ID);
    when(mockAndroidAdbUtil.getProperty(TEST_DEVICE_ID, AndroidProperty.SERIAL))
        .thenReturn(TEST_DEVICE_ID);
    when(mockLocalFileUtil.isFileExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(true);
    when(mockLocalFileUtil.isDirExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(false);
    when(mockLocalFileUtil.getFileSize(Path.of(TEST_BOOT_IMAGE_PATH)))
        .thenReturn(TEST_BOOT_IMAGE_SIZE);
    when(mockLocalFileUtil.isFileExist(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH))).thenReturn(true);
    when(mockLocalFileUtil.isDirExist(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH))).thenReturn(false);
    when(mockLocalFileUtil.getFileSize(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH)))
        .thenReturn(TEST_SYSTEM_DLKM_IMAGE_SIZE);
    when(mockFastboot.runWithRetry(any(), any(), any(Duration.class), any(boolean.class)))
        .thenReturn("OKAY [  0.001s]");
    when(mockFastboot.getVar(TEST_DEVICE_ID, FastbootProperty.IS_USERSPACE))
        .thenReturn("no", "yes");

    decorator.run(mockTestInfo);
    verify(mockFastboot, never())
        .runWithRetry(eq(TEST_DEVICE_ID), eq(new String[] {""}), any(Duration.class), eq(false));
    verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID), eq(new String[] {"getvar", "all"}), any(Duration.class), eq(false));
    verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID),
            eq(new String[] {"flash", "boot", TEST_BOOT_IMAGE_PATH}),
            any(Duration.class),
            eq(false));
    verify(mockFastboot)
        .runWithRetry(
            eq(TEST_DEVICE_ID),
            eq(new String[] {"flash", "system_dlkm", TEST_SYSTEM_DLKM_IMAGE_PATH}),
            any(Duration.class),
            eq(false));
    verify(mockFastboot, times(1))
        .runWithRetry(
            eq(TEST_DEVICE_ID),
            eq(new String[] {"reboot", "fastboot"}),
            any(Duration.class),
            eq(false));
  }

  /* Test parseKernelVersion with android image */
  @Test
  public void testParseKernelVersionAndroid() {
    String kernelString = "6.12.63-android16-6-gf2758f0da7bc-ab14892992-4k";
    String[] version = decorator.parseKernelVersion(mockTestInfo, "source", kernelString);
    assertThat(version).isEqualTo(new String[] {"android16-6.12", "63", "4k"});
  }

  /* Test parseKernelVersion with mainline image */
  @Test
  public void testParseKernelVersionMainline() {
    String kernelString = "6.19.0-mainline-g6cea65971bdc-ab14931105-16k";
    String[] version = decorator.parseKernelVersion(mockTestInfo, "source", kernelString);
    assertThat(version).isEqualTo(new String[] {"android-mainline", "0", "16k"});
  }

  /* Test parseKernelVersion with mainline image with rc */
  @Test
  public void testParseKernelVersionMainlineWithRc() {
    String kernelString = "7.1.0-rc6-mainline-g8a1ef95dff70-ab15558717-4k";
    String[] version = decorator.parseKernelVersion(mockTestInfo, "source", kernelString);
    assertThat(version).isEqualTo(new String[] {"android-mainline", "0", "4k"});
  }

  /* Test parseKernelVersion with an old android image */
  @Test
  public void testParseKernelVersionOldVersion() {
    String kernelString = "5.15.180-android14-11-gf55c0c36ffcd-ab13512086";
    String[] version = decorator.parseKernelVersion(mockTestInfo, "source", kernelString);
    assertThat(version).isEqualTo(new String[] {"android14-5.15", "180", "4k"});
  }

  @Test
  public void testParseKernelVersionAndroid13() {
    String kernelString = "5.10.150-android13-1-ab12345678";
    String[] version = decorator.parseKernelVersion(mockTestInfo, "source", kernelString);
    assertThat(version).isEqualTo(new String[] {"android13-5.10", "150", "4k"});
  }

  @Test
  public void testParseKernelVersionAndroid15_16k() {
    String kernelString = "6.6.10-android15-1-ab12345678";
    String[] version = decorator.parseKernelVersion(mockTestInfo, "source", kernelString);
    assertThat(version).isEqualTo(new String[] {"android15-6.6", "10", "16k"});
  }

  /* Test parseKernelVersion failure */
  @Test
  public void testParseKernelVersionFailure() {
    String kernelString = "invalid-kernel-string";
    String[] version = decorator.parseKernelVersion(mockTestInfo, "source", kernelString);
    assertThat(version).isNull();
  }

  @Test
  public void testGetKernelVersionFromImage_success() throws Exception {
    AndroidFlashGkiDecorator realDecorator =
        new AndroidFlashGkiDecorator(
            mockDriver,
            mockTestInfo,
            mockCommandExecutor,
            mockLocalFileUtil,
            mockAndroidSystemStateUtil,
            mockAndroidSystemSettingUtil,
            mockFastboot,
            mockSleeper,
            mockAndroidAdbUtil,
            mockAdb,
            mockApkInstaller,
            mockQuotaManager,
            mockDeviceCache,
            mockDeviceInitializationStep);

    File fakeImage = new File("fake_image.img");
    CommandResult mockCommandResult = Mockito.mock(CommandResult.class);
    when(mockCommandResult.stdout())
        .thenReturn("6.12.63-android16-6-gf2758f0da7bc-ab14892992-4k\n");
    when(mockCommandExecutor.exec(any(Command.class))).thenReturn(mockCommandResult);

    String[] version = realDecorator.getKernelVersionFromImage(mockTestInfo, fakeImage);

    assertThat(version).isEqualTo(new String[] {"android16-6.12", "63", "4k"});
    verify(mockCommandExecutor).exec(any(Command.class));
  }

  @Test
  public void testGetKernelVersionFromImage_commandFailure() throws Exception {
    AndroidFlashGkiDecorator realDecorator =
        new AndroidFlashGkiDecorator(
            mockDriver,
            mockTestInfo,
            mockCommandExecutor,
            mockLocalFileUtil,
            mockAndroidSystemStateUtil,
            mockAndroidSystemSettingUtil,
            mockFastboot,
            mockSleeper,
            mockAndroidAdbUtil,
            mockAdb,
            mockApkInstaller,
            mockQuotaManager,
            mockDeviceCache,
            mockDeviceInitializationStep);

    File fakeImage = new File("fake_image.img");
    when(mockCommandExecutor.exec(any(Command.class))).thenThrow(mockTimeoutException);
    String[] version = realDecorator.getKernelVersionFromImage(mockTestInfo, fakeImage);

    assertThat(version).isNull();
    verify(mockCommandExecutor).exec(any(Command.class));
  }

  /* Test validateKernelCompatibility success */
  @Test
  public void testValidateKernelCompatibilitySuccess() throws Exception {
    AndroidFlashGkiDecoratorSpec spec = getDefaultTestSpecBuilder().build();
    String[] bootVersion = {"android16-6.12", "63", "16k"};
    String[] imageVersion = {"android16-6.12", "60", "16k"};
    decorator.validateKernelCompatibility(mockTestInfo, spec, "Image", imageVersion, bootVersion);
  }

  /* Test validateKernelCompatibility mismatch */
  @Test
  public void testValidateKernelCompatibilityMismatch() throws Exception {
    AndroidFlashGkiDecoratorSpec spec = getDefaultTestSpecBuilder().build();
    String[] bootVersion = {"android16-6.12", "63", "16k"};
    String[] imageVersion = {"android15-6.6", "60", "16k"};
    MobileHarnessException expected =
        assertThrows(
            MobileHarnessException.class,
            () ->
                decorator.validateKernelCompatibility(
                    mockTestInfo, spec, "Image", imageVersion, bootVersion));
    assertThat(expected).hasMessageThat().contains("doesn't match");
  }

  /* Test validateKernelCompatibility lower patch level */
  @Test
  public void testValidateKernelCompatibilityLowerPatchLevel() throws Exception {
    AndroidFlashGkiDecoratorSpec spec = getDefaultTestSpecBuilder().build();
    String[] bootVersion = {"android16-6.12", "60", "16k"};
    String[] imageVersion = {"android16-6.12", "63", "16k"};
    MobileHarnessException expected =
        assertThrows(
            MobileHarnessException.class,
            () ->
                decorator.validateKernelCompatibility(
                    mockTestInfo, spec, "Image", imageVersion, bootVersion));
    assertThat(expected).hasMessageThat().contains("less than");
  }

  @Test
  public void testValidateKernelVersion_withGkiInfoTxt_success() throws Exception {
    when(mockDevice.getDeviceId()).thenReturn(TEST_DEVICE_ID);

    AndroidFlashGkiDecoratorSpec spec =
        getDefaultTestSpecBuilder().setGkiInfoTxt("path/to/gki-info.txt").build();
    mockSpec(spec, TEST_DEVICE_ID);

    when(mockAndroidAdbUtil.getProperty(TEST_DEVICE_ID, AndroidProperty.SERIAL))
        .thenReturn(TEST_DEVICE_ID);

    when(mockLocalFileUtil.isFileExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(true);
    when(mockLocalFileUtil.isDirExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(false);
    when(mockLocalFileUtil.getFileSize(Path.of(TEST_BOOT_IMAGE_PATH)))
        .thenReturn(TEST_BOOT_IMAGE_SIZE);
    when(mockLocalFileUtil.isFileExist(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH))).thenReturn(true);
    when(mockLocalFileUtil.isDirExist(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH))).thenReturn(false);
    when(mockLocalFileUtil.getFileSize(Path.of(TEST_SYSTEM_DLKM_IMAGE_PATH)))
        .thenReturn(TEST_SYSTEM_DLKM_IMAGE_SIZE);
    when(mockFastboot.runWithRetry(any(), any(), any(Duration.class), any(boolean.class)))
        .thenReturn("OKAY [  0.001s]");
    when(mockFastboot.getVar(TEST_DEVICE_ID, FastbootProperty.IS_USERSPACE)).thenReturn("no");
    when(mockLocalFileUtil.isFileExist("path/to/gki-info.txt")).thenReturn(true);

    decorator.run(mockTestInfo);

    verify(mockApi, never())
        .log("Will not validate kernel version since gki-info.txt is not provided.");
  }

  @Test
  public void testValidateKernelVersion_withGkiInfoTxt_parseFailure() throws Exception {
    AndroidFlashGkiDecorator invalidDecorator =
        new AndroidFlashGkiDecorator(
            mockDriver,
            mockTestInfo,
            mockCommandExecutor,
            mockLocalFileUtil,
            mockAndroidSystemStateUtil,
            mockAndroidSystemSettingUtil,
            mockFastboot,
            mockSleeper,
            mockAndroidAdbUtil,
            mockAdb,
            mockApkInstaller,
            mockQuotaManager,
            mockDeviceCache,
            mockDeviceInitializationStep) {
          @Override
          public String[] getKernelVersionFromImage(TestInfo testInfo, File image) {
            return null; // Simulate parsing failure
          }
        };

    when(mockDevice.getDeviceId()).thenReturn(TEST_DEVICE_ID);
    AndroidFlashGkiDecoratorSpec spec =
        getDefaultTestSpecBuilder().setGkiInfoTxt("path/to/gki-info.txt").build();

    when(mockJobInfo.combinedSpec(invalidDecorator, TEST_DEVICE_ID)).thenReturn(spec);
    when(mockLocalFileUtil.isFileExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(true);
    when(mockLocalFileUtil.isDirExist(Path.of(TEST_BOOT_IMAGE_PATH))).thenReturn(false);
    when(mockLocalFileUtil.getFileSize(Path.of(TEST_BOOT_IMAGE_PATH)))
        .thenReturn(TEST_BOOT_IMAGE_SIZE);
    when(mockLocalFileUtil.isFileExist("path/to/gki-info.txt")).thenReturn(true);

    MobileHarnessException expected =
        assertThrows(MobileHarnessException.class, () -> invalidDecorator.run(mockTestInfo));

    assertThat(expected)
        .hasMessageThat()
        .contains("Could not extract kernel version from gki-info.txt");
  }
}
