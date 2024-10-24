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

package com.google.devtools.mobileharness.platform.android.lightning.fileoperator;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.out.Properties;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.file.checksum.ChecksumUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.log.testing.FakeLogCollector;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link FileOperator}. */
@RunWith(JUnit4.class)
public final class FileOperatorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final String DEVICE_ID = "363005dc750400ec";
  private static final Duration PUSH_TIMEOUT = Duration.ofSeconds(30);

  private FileOperator fileOperator;
  private FakeLogCollector testLog;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Device device;

  @Mock private Properties properties;
  @Mock private ChecksumUtil checksumUtil;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private AndroidFileUtil androidFileUtil;
  @Mock private AndroidSystemSettingUtil systemSettingUtil;

  @Before
  public void setUp() {
    testLog = new FakeLogCollector();
    fileOperator =
        new FileOperator(checksumUtil, localFileUtil, androidFileUtil, systemSettingUtil);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
  }

  @Test
  public void getAllAncestorDirs() {
    assertThat(fileOperator.getAllAncestorDirs("/dir1/dir2/dir3/file"))
        .containsExactly("/", "/dir1", "/dir1/dir2", "/dir1/dir2/dir3");
    assertThat(fileOperator.getAllAncestorDirs("./dir1/dir2")).isEmpty();
  }

  @Test
  public void pull() throws Exception {
    String srcPathOnDevice = "/sdcard/tmp/file";
    String desPathOnHost = "/path/on/host";
    when(androidFileUtil.isFileOrDirExisted(DEVICE_ID, srcPathOnDevice)).thenReturn(true);
    when(androidFileUtil.pull(DEVICE_ID, srcPathOnDevice, desPathOnHost))
        .thenReturn("Push file successfully");

    assertThat(
            fileOperator.pull(
                DEVICE_ID,
                FilePullArgs.builder()
                    .setSrcPathOnDevice(srcPathOnDevice)
                    .setDesPathOnHost(desPathOnHost)
                    .build(),
                testLog))
        .isTrue();

    verify(androidFileUtil).pull(DEVICE_ID, srcPathOnDevice, desPathOnHost);
    verify(localFileUtil, never()).prepareDir(desPathOnHost);
  }

  @Test
  public void pull_prepareDesPathOnHostAsDir() throws Exception {
    String srcPathOnDevice = "/sdcard/tmp/file";
    String desPathOnHost = "/path/on/host";
    when(androidFileUtil.isFileOrDirExisted(DEVICE_ID, srcPathOnDevice)).thenReturn(true);
    when(androidFileUtil.pull(DEVICE_ID, srcPathOnDevice, desPathOnHost))
        .thenReturn("Push file successfully");

    assertThat(
            fileOperator.pull(
                DEVICE_ID,
                FilePullArgs.builder()
                    .setSrcPathOnDevice(srcPathOnDevice)
                    .setDesPathOnHost(desPathOnHost)
                    .setPrepareDesPathOnHostAsDir(true)
                    .build(),
                testLog))
        .isTrue();

    verify(localFileUtil).prepareDir(desPathOnHost);
    verify(androidFileUtil).pull(DEVICE_ID, srcPathOnDevice, desPathOnHost);
  }

  @Test
  public void pull_srcFileOrDirNotExist() throws Exception {
    String srcPathOnDevice = "/sdcard/tmp/file";
    String desPathOnHost = "/path/on/host";
    when(androidFileUtil.isFileOrDirExisted(DEVICE_ID, srcPathOnDevice)).thenReturn(false);

    assertThat(
            fileOperator.pull(
                DEVICE_ID,
                FilePullArgs.builder()
                    .setSrcPathOnDevice(srcPathOnDevice)
                    .setDesPathOnHost(desPathOnHost)
                    .build(),
                testLog))
        .isFalse();

    verify(androidFileUtil, never()).pull(DEVICE_ID, srcPathOnDevice, desPathOnHost);
  }

  @Test
  public void pull_grantDesPathOnHostFullAccess() throws Exception {
    String srcPathOnDevice = "/sdcard/tmp/file";
    String desPathOnHost = "/path/on/host";
    when(androidFileUtil.isFileOrDirExisted(DEVICE_ID, srcPathOnDevice)).thenReturn(true);
    when(androidFileUtil.pull(DEVICE_ID, srcPathOnDevice, desPathOnHost))
        .thenReturn("Push file successfully");

    assertThat(
            fileOperator.pull(
                DEVICE_ID,
                FilePullArgs.builder()
                    .setSrcPathOnDevice(srcPathOnDevice)
                    .setDesPathOnHost(desPathOnHost)
                    .setGrantDesPathOnHostFullAccess(true)
                    .build(),
                testLog))
        .isTrue();

    verify(androidFileUtil).pull(DEVICE_ID, srcPathOnDevice, desPathOnHost);
    verify(localFileUtil).grantFileOrDirFullAccess(desPathOnHost);
  }

  @Test
  public void pull_logFailuresOnlyByDefault() throws Exception {
    String srcPathOnDevice = "/sdcard/tmp/file";
    String desPathOnHost = "/path/on/host";
    when(androidFileUtil.isFileOrDirExisted(DEVICE_ID, srcPathOnDevice)).thenReturn(true);
    when(androidFileUtil.pull(DEVICE_ID, srcPathOnDevice, desPathOnHost))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_FILE_UTIL_PULL_FILE_ERROR, "Pull Error"));

    assertThat(
            fileOperator.pull(
                DEVICE_ID,
                FilePullArgs.builder()
                    .setSrcPathOnDevice(srcPathOnDevice)
                    .setDesPathOnHost(desPathOnHost)
                    .build(),
                testLog))
        .isFalse();

    verify(androidFileUtil).pull(DEVICE_ID, srcPathOnDevice, desPathOnHost);
  }

  @Test
  public void pull_notLogFailuresOnly_throwException() throws Exception {
    String srcPathOnDevice = "/sdcard/tmp/file";
    String desPathOnHost = "/path/on/host";
    when(androidFileUtil.isFileOrDirExisted(DEVICE_ID, srcPathOnDevice)).thenReturn(true);
    when(androidFileUtil.pull(DEVICE_ID, srcPathOnDevice, desPathOnHost))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_FILE_UTIL_PULL_FILE_ERROR, "Pull Error"));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        fileOperator.pull(
                            DEVICE_ID,
                            FilePullArgs.builder()
                                .setSrcPathOnDevice(srcPathOnDevice)
                                .setDesPathOnHost(desPathOnHost)
                                .setLogFailuresOnly(false)
                                .build(),
                            testLog))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_OPERATOR_PULL_FILE_ERROR);
  }

  @Test
  public void pushFileOrDirTimeout_md5NotEqual_success() throws Exception {
    int sdkVersion = 28;
    String md5 = "1234567890";
    String srcPathOnHost = "file_path_and_name_on_host";
    String desPathOnDevice = "/sdcard/tmp/";
    String desFinalPathOnDevice = "/sdcard/tmp/name";
    String cachedPropertyKeyForDesPath =
        FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + desFinalPathOnDevice;

    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(sdkVersion);
    when(localFileUtil.isFileOrDirExist(srcPathOnHost)).thenReturn(true);
    when(checksumUtil.fingerprint(srcPathOnHost)).thenReturn(md5);
    when(androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
            DEVICE_ID, sdkVersion, srcPathOnHost, desPathOnDevice))
        .thenReturn(desFinalPathOnDevice);
    when(androidFileUtil.push(DEVICE_ID, sdkVersion, srcPathOnHost, desPathOnDevice, PUSH_TIMEOUT))
        .thenReturn("log");

    fileOperator.pushFileOrDir(
        device,
        FilePushArgs.builder()
            .setSrcPathOnHost(srcPathOnHost)
            .setDesPathOnDevice(desPathOnDevice)
            .setPushTimeout(PUSH_TIMEOUT)
            .build(),
        testLog);

    verify(device).getProperty(cachedPropertyKeyForDesPath);
    verify(device).setProperty(cachedPropertyKeyForDesPath, md5);
    verify(androidFileUtil)
        .push(DEVICE_ID, sdkVersion, srcPathOnHost, desPathOnDevice, PUSH_TIMEOUT);
  }

  @Test
  public void pushFileOrDir_md5NotEqual_success() throws Exception {
    int sdkVersion = 28;
    String md5 = "1234567890";
    String srcPathOnHost = "file_path_and_name_on_host";
    String desPathOnDevice = "/sdcard/tmp/";
    String desFinalPathOnDevice = "/sdcard/tmp/name";
    String cachedPropertyKeyForDesPath =
        FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + desFinalPathOnDevice;

    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(sdkVersion);
    when(localFileUtil.isFileOrDirExist(srcPathOnHost)).thenReturn(true);
    when(checksumUtil.fingerprint(srcPathOnHost)).thenReturn(md5);
    when(androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
            DEVICE_ID, sdkVersion, srcPathOnHost, desPathOnDevice))
        .thenReturn(desFinalPathOnDevice);
    when(androidFileUtil.push(
            DEVICE_ID, sdkVersion, srcPathOnHost, desPathOnDevice, /* pushTimeout= */ null))
        .thenReturn("log");

    fileOperator.pushFileOrDir(
        device,
        FilePushArgs.builder()
            .setSrcPathOnHost(srcPathOnHost)
            .setDesPathOnDevice(desPathOnDevice)
            .build(),
        testLog);

    verify(device).getProperty(cachedPropertyKeyForDesPath);
    verify(device).setProperty(cachedPropertyKeyForDesPath, md5);
    verify(androidFileUtil)
        .push(DEVICE_ID, sdkVersion, srcPathOnHost, desPathOnDevice, /* pushTimeout= */ null);
  }

  @Test
  public void pushFileOrDir_removePropertyIfFailedToPush_rethrowException() throws Exception {
    int sdkVersion = 28;
    String md5 = "1234567890";
    String srcPathOnHost = "file_path_and_name_on_host";
    String desPathOnDevice = "/sdcard/tmp/";
    String desFinalPathOnDevice = "/sdcard/tmp/name";
    String cachedPropertyKeyForDesPath =
        FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + desFinalPathOnDevice;

    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(sdkVersion);
    when(localFileUtil.isFileOrDirExist(srcPathOnHost)).thenReturn(true);
    when(checksumUtil.fingerprint(srcPathOnHost)).thenReturn(md5);
    when(androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
            DEVICE_ID, sdkVersion, srcPathOnHost, desPathOnDevice))
        .thenReturn(desFinalPathOnDevice);
    when(androidFileUtil.push(
            DEVICE_ID, sdkVersion, srcPathOnHost, desPathOnDevice, /* pushTimeout= */ null))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_FILE_UTIL_PUSH_FILE_ADB_ERROR, "Push Error"));
    when(device.info().properties()).thenReturn(properties);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        fileOperator.pushFileOrDir(
                            device,
                            FilePushArgs.builder()
                                .setSrcPathOnHost(srcPathOnHost)
                                .setDesPathOnDevice(desPathOnDevice)
                                .build(),
                            testLog))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_OPERATOR_PUSH_FILE_OR_DIR_ERROR);

    verify(device).getProperty(cachedPropertyKeyForDesPath);
    verify(properties).remove(cachedPropertyKeyForDesPath);
    verify(device, never()).setProperty(cachedPropertyKeyForDesPath, md5);
  }

  @Test
  public void pushFileOrDir_md5Equal_skipped() throws Exception {
    int sdkVersion = 28;
    String md5 = "1234567890";
    String srcPathOnHost = "file_path_and_name_on_host";
    String desPathOnDevice = "/sdcard/tmp/";
    String desFinalPathOnDevice = "/sdcard/tmp/name";
    String cachedPropertyKeyForDesPath =
        FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + desFinalPathOnDevice;

    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(sdkVersion);
    when(localFileUtil.isFileOrDirExist(srcPathOnHost)).thenReturn(true);
    when(checksumUtil.fingerprint(srcPathOnHost)).thenReturn(md5);
    when(device.getProperty(cachedPropertyKeyForDesPath)).thenReturn(md5);
    when(androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
            DEVICE_ID, sdkVersion, srcPathOnHost, desPathOnDevice))
        .thenReturn(desFinalPathOnDevice);

    fileOperator.pushFileOrDir(
        device,
        FilePushArgs.builder()
            .setSrcPathOnHost(srcPathOnHost)
            .setDesPathOnDevice(desPathOnDevice)
            .build(),
        testLog);

    verify(androidFileUtil, never()).push(anyString(), anyInt(), anyString(), anyString(), any());
  }

  @Test
  public void pushFileOrDir_failedToGetDesFinalPathOnDevice_throwException() throws Exception {
    int sdkVersion = 28;
    String srcPathOnHost = "file_path_and_name_on_host";
    String desPathOnDevice = "/sdcard/tmp/";

    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(sdkVersion);
    when(localFileUtil.isFileOrDirExist(srcPathOnHost)).thenReturn(true);
    when(androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
            DEVICE_ID, sdkVersion, srcPathOnHost, desPathOnDevice))
        .thenReturn("");

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        fileOperator.pushFileOrDir(
                            device,
                            FilePushArgs.builder()
                                .setSrcPathOnHost(srcPathOnHost)
                                .setDesPathOnDevice(desPathOnDevice)
                                .build(),
                            testLog))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_OPERATOR_ILLEGAL_ARGUMENT);
  }

  @Test
  public void pushFileOrDir_srcPathOnHostNotExist_throwException() throws Exception {
    String srcPathOnHost = "file_path_and_name_on_host";
    String desPathOnDevice = "/sdcard/tmp/";

    when(localFileUtil.isFileOrDirExist(srcPathOnHost)).thenReturn(false);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        fileOperator.pushFileOrDir(
                            device,
                            FilePushArgs.builder()
                                .setSrcPathOnHost(srcPathOnHost)
                                .setDesPathOnDevice(desPathOnDevice)
                                .build(),
                            testLog))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_OPERATOR_FILE_NOT_FOUND);
  }

  @Test
  public void pushFileOrDir_desPathOnDeviceIsEmpty_throwException() throws Exception {
    String srcPathOnHost = "file_path_and_name_on_host";
    String desPathOnDevice = "";

    when(localFileUtil.isFileOrDirExist(srcPathOnHost)).thenReturn(true);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        fileOperator.pushFileOrDir(
                            device,
                            FilePushArgs.builder()
                                .setSrcPathOnHost(srcPathOnHost)
                                .setDesPathOnDevice(desPathOnDevice)
                                .build(),
                            testLog))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_OPERATOR_FILE_NOT_FOUND);
  }

  @Test
  public void pushFileOrDir_desPathUnderTmpDir_skipCache() throws Exception {
    int sdkVersion = 28;
    String srcPathOnHost = "file_path_and_name_on_host";
    String desPathOnDevice = "/data/local/tmp/";

    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(sdkVersion);
    when(localFileUtil.isFileOrDirExist(srcPathOnHost)).thenReturn(true);
    when(androidFileUtil.push(
            DEVICE_ID, sdkVersion, srcPathOnHost, desPathOnDevice, /* pushTimeout= */ null))
        .thenReturn("log");

    fileOperator.pushFileOrDir(
        device,
        FilePushArgs.builder()
            .setSrcPathOnHost(srcPathOnHost)
            .setDesPathOnDevice(desPathOnDevice)
            .build(),
        testLog);

    verify(androidFileUtil, never())
        .getPushedFileOrDirFinalDestinationPathOnDevice(
            DEVICE_ID, sdkVersion, srcPathOnHost, desPathOnDevice);
    verify(checksumUtil, never()).fingerprint(srcPathOnHost);
    verify(device, never()).setProperty(anyString(), anyString());
    verify(androidFileUtil)
        .push(DEVICE_ID, sdkVersion, srcPathOnHost, desPathOnDevice, /* pushTimeout= */ null);
  }

  @Test
  public void removeFileOrDir_fileOrDirNotExist() throws Exception {
    String fileOrDirPath = "/sdcard/test_dir/test_file";
    when(androidFileUtil.isFileOrDirExisted(DEVICE_ID, fileOrDirPath)).thenReturn(false);

    fileOperator.removeFileOrDir(device, fileOrDirPath, testLog);

    verify(androidFileUtil, never()).removeFiles(DEVICE_ID, fileOrDirPath);
  }

  @Test
  public void removeFileOrDir() throws Exception {
    String fileOrDirPath = "/sdcard/test_dir1/test_dir2/test_file";
    when(androidFileUtil.isFileOrDirExisted(DEVICE_ID, fileOrDirPath)).thenReturn(true);
    when(device.info().properties()).thenReturn(properties);
    when(properties.getAll())
        .thenReturn(
            ImmutableMap.of(
                FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + "/sdcard/test_dir1",
                "test_dir1_md5",
                FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + "/sdcard/test_dir1/test_dir2",
                "test_dir2_md5",
                FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR
                    + "/sdcard/test_dir1/test_dir2/test_file",
                "test_file_md5"));

    fileOperator.removeFileOrDir(device, fileOrDirPath, testLog);

    verify(androidFileUtil).removeFiles(DEVICE_ID, fileOrDirPath);
    verify(properties)
        .remove(FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + "/sdcard/test_dir1");
    verify(properties)
        .remove(FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + "/sdcard/test_dir1/test_dir2");
    verify(properties)
        .remove(
            FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR
                + "/sdcard/test_dir1/test_dir2/test_file");
  }

  @Test
  public void removeFileOrDir_hasSubFilesOrDirs() throws Exception {
    String fileOrDirPath = "/sdcard/test_dir1/test_dir2";
    when(androidFileUtil.isFileOrDirExisted(DEVICE_ID, fileOrDirPath)).thenReturn(true);
    when(device.info().properties()).thenReturn(properties);
    when(properties.getAll())
        .thenReturn(
            ImmutableMap.of(
                FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + "/sdcard/test_dir1",
                "test_dir1_md5",
                FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + "/sdcard/test_dir1/test_dir2",
                "test_dir2_md5",
                FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR
                    + "/sdcard/test_dir1/test_dir2/test_dir3",
                "test_dir3_md5",
                FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR
                    + "/sdcard/test_dir1/test_dir2/test_file",
                "test_file_md5"));

    fileOperator.removeFileOrDir(device, fileOrDirPath, testLog);

    verify(androidFileUtil).removeFiles(DEVICE_ID, fileOrDirPath);
    verify(properties)
        .remove(FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + "/sdcard/test_dir1");
    verify(properties)
        .remove(FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR + "/sdcard/test_dir1/test_dir2");
    verify(properties)
        .remove(
            FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR
                + "/sdcard/test_dir1/test_dir2/test_dir3");
    verify(properties)
        .remove(
            FileOperator.DEVICE_PROP_PREFIX_PUSHED_FILE_OR_DIR
                + "/sdcard/test_dir1/test_dir2/test_file");
  }

  @Test
  public void removeFileOrDir_invalidArgument_throwException() throws Exception {
    String fileOrDirPath = "/sdcard/test_dir/test_file";
    when(androidFileUtil.isFileOrDirExisted(DEVICE_ID, fileOrDirPath)).thenReturn(true);
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_FILE_UTIL_REMOVE_FILE_INVALID_ARGUMENT, "Invalid argument"))
        .when(androidFileUtil)
        .removeFiles(DEVICE_ID, fileOrDirPath);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> fileOperator.removeFileOrDir(device, fileOrDirPath, testLog))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_OPERATOR_REMOVE_FILE_INVALID_ARGUMENT);
  }
}
