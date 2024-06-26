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

package com.google.devtools.mobileharness.platform.android.file;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserUtil;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import java.time.Duration;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidFileUtilTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Adb adb;
  @Mock private LocalFileUtil fileUtil;
  @Mock private AndroidUserUtil androidUserUtil;
  @Mock private AndroidSystemSettingUtil androidSystemSettingUtil;
  @Mock private AndroidSystemSpecUtil androidSystemSpecUtil;

  private static final String SERIAL = "363005dc750400ec";
  private static final String APK_PATH = "/usr/local/google/AndroidTranslateTest-debug.apk";
  private static final String PACKAGE_NAME = "com.google.package";
  private static final String FILE_NAME = "shared_prefs.xml";

  private static final Duration PUSH_TIME_OUT = Duration.ofSeconds(30L);

  private AndroidFileUtil androidFileUtil;

  @Before
  public void setUp() {
    androidFileUtil =
        new AndroidFileUtil(
            adb, fileUtil, androidUserUtil, androidSystemSettingUtil, androidSystemSpecUtil);
  }

  @Test
  public void getSharedPrefs() throws Exception {
    when(adb.runShell(
            SERIAL,
            "run-as "
                + PACKAGE_NAME
                + " cat /data/data/"
                + PACKAGE_NAME
                + "/shared_prefs/"
                + FILE_NAME))
        .thenReturn("<map/>");
    assertThat(androidFileUtil.getSharedPrefs(SERIAL, PACKAGE_NAME, FILE_NAME)).isEmpty();
  }

  @Test
  public void xmlToMap() throws Exception {
    String xml =
        "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
            + "<map>\n"
            + "<long name=\"android.app.binary.adbwatchdog.adbResetInterval\" value=\"60000\" />\n"
            + "<long name=\"android.app.binary.adbwatchdog.adbTtl\" value=\"43200000\" />\n"
            + "<long name=\"android.app.binary.adbwatchdog.adbCheckInterval\" value=\"3600000\""
            + " />\n"
            + "<string name=\"android.app.binary.adbwatchdog.lastAdbResetReason\">2020-08-03"
            + " 23:03:50 - None reset</string>\n"
            + "</map>";
    Map<String, String> rst = androidFileUtil.xmlToMap(xml);
    assertThat(rst.get("android.app.binary.adbwatchdog.adbResetInterval")).matches("60000");
    assertThat(rst.get("android.app.binary.adbwatchdog.adbTtl")).matches("43200000");
    assertThat(rst.get("android.app.binary.adbwatchdog.adbCheckInterval")).matches("3600000");
    assertThat(rst.get("android.app.binary.adbwatchdog.lastAdbResetReason"))
        .matches("2020-08-03 23:03:50 - None reset");
  }

  @Test
  public void getExternalStoragePath_api27() throws Exception {
    String externalStoragePath = "/mnt/sdcard";
    when(adb.runShellWithRetry(
            SERIAL,
            AndroidFileUtil.ADB_SHELL_GET_EXTERNAL_STORAGE,
            AndroidFileUtil.SHORT_COMMAND_TIMEOUT))
        .thenReturn(externalStoragePath)
        .thenReturn(null)
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    assertThat(androidFileUtil.getExternalStoragePath(SERIAL, /* sdkVersion= */ 27))
        .isEqualTo(externalStoragePath);
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidFileUtil.getExternalStoragePath(SERIAL, /* sdkVersion= */ 27))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_EXTERNAL_STORAGE_NOT_FOUND);
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidFileUtil.getExternalStoragePath(SERIAL, /* sdkVersion= */ 27))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_GET_EXTERNAL_STORAGE_ERROR);
  }

  @Test
  public void getExternalStoragePath_api28() throws Exception {
    when(androidUserUtil.getCurrentUser(SERIAL, /* sdkVersion= */ 28))
        .thenReturn(0)
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_USER_UTIL_GET_FOREGROUND_USER_ERROR, "Error"));

    assertThat(androidFileUtil.getExternalStoragePath(SERIAL, /* sdkVersion= */ 28))
        .isEqualTo("/storage/emulated/0");
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidFileUtil.getExternalStoragePath(SERIAL, /* sdkVersion= */ 28))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_GET_EXTERNAL_STORAGE_ERROR);
  }

  @Test
  public void getExternalStoragePath_api28_multiuser() throws Exception {
    when(androidUserUtil.getCurrentUser(SERIAL, /* sdkVersion= */ 28))
        .thenReturn(10)
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_USER_UTIL_GET_FOREGROUND_USER_ERROR, "Error"));

    assertThat(androidFileUtil.getExternalStoragePath(SERIAL, /* sdkVersion= */ 28))
        .isEqualTo("/storage/emulated/10");
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidFileUtil.getExternalStoragePath(SERIAL, /* sdkVersion= */ 28))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_GET_EXTERNAL_STORAGE_ERROR);
  }

  @Test
  public void getExternalStoragePath_api30_multiuser() throws Exception {
    when(androidUserUtil.getCurrentUser(SERIAL, /* sdkVersion= */ 30))
        .thenReturn(10)
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_USER_UTIL_GET_FOREGROUND_USER_ERROR, "Error"));

    assertThat(androidFileUtil.getExternalStoragePath(SERIAL, /* sdkVersion= */ 30))
        .isEqualTo("/mnt/pass_through/10/emulated/10");
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidFileUtil.getExternalStoragePath(SERIAL, /* sdkVersion= */ 30))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_GET_EXTERNAL_STORAGE_ERROR);
  }

  @Test
  public void getFileInfo_file() throws Exception {
    int sdkVersion = 28;
    String fileParentDir = "/data/test_dir/";
    String fileName = "test_file";
    String filePath = PathUtil.join(fileParentDir, fileName);

    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l " + fileParentDir),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn("-rw-rw-rw- root     root            0 2019-07-17 23:47 test_file")
        .thenAnswer(
            invocation -> {
              LineCallback callback = invocation.getArgument(3, LineCallback.class);
              callback.onLine(String.format("ls: //%s: No such file or directory", fileParentDir));
              throw new MobileHarnessException(
                  AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE,
                  "Failed to execute command");
            });

    assertThat(androidFileUtil.getFileInfo(SERIAL, sdkVersion, filePath, /* followSymlink= */ true))
        .hasValue(
            FileInfo.builder()
                .setPath(filePath)
                .setType(FileType.FILE)
                .setPermissions(FilePermissions.from("rw-rw-rw-"))
                .build());
    assertThat(androidFileUtil.getFileInfo(SERIAL, sdkVersion, filePath, /* followSymlink= */ true))
        .isEmpty();
  }

  @Test
  public void getFileInfo_dir() throws Exception {
    int sdkVersion = 28;
    String dirParentDir = "/data/test_dir/";
    String dirName = "test_dir";
    String dirPath = PathUtil.join(dirParentDir, dirName);

    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l " + dirParentDir),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn("drwxrwxrwx root     root              2019-07-17 05:28 test_dir");

    assertThat(androidFileUtil.getFileInfo(SERIAL, sdkVersion, dirPath, /* followSymlink= */ true))
        .hasValue(
            FileInfo.builder()
                .setPath(dirPath)
                .setType(FileType.DIR)
                .setPermissions(FilePermissions.from("rwxrwxrwx"))
                .build());
  }

  @Test
  public void getFileInfo_dirIsRootDir() throws Exception {
    int sdkVersion = 28;
    String dirPath = "/";

    assertThat(androidFileUtil.getFileInfo(SERIAL, sdkVersion, dirPath, /* followSymlink= */ true))
        .hasValue(FileInfo.builder().setPath(dirPath).setType(FileType.DIR).build());
  }

  @Test
  public void getFileInfo_symlinkDir_notFollow() throws Exception {
    int sdkVersion = 28;
    String dirParentDir = "/data/test_dir/";
    String dirName = "test_dir";
    String dirPath = PathUtil.join(dirParentDir, dirName);

    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l " + dirParentDir),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:28 test_dir -> /data/local/original_test_dir");

    assertThat(androidFileUtil.getFileInfo(SERIAL, sdkVersion, dirPath, /* followSymlink= */ false))
        .hasValue(
            FileInfo.builder()
                .setPath(dirPath)
                .setType(FileType.SYMLINK)
                .setPermissions(FilePermissions.from("rwxrwxrwx"))
                .build());
  }

  @Test
  public void getFileInfo_symlinkDir_followSymlink_api28() throws Exception {
    int sdkVersion = 28;
    String dirParentDir = "/data/test_dir/";
    String dirName = "test_dir";
    String dirPath = PathUtil.join(dirParentDir, dirName);

    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l " + dirParentDir),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:28 test_dir -> /data/local/original_test_dir");
    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l -L " + dirParentDir),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn("drwxrwx--x root   root    2019-07-18 15:16 test_dir");

    assertThat(androidFileUtil.getFileInfo(SERIAL, sdkVersion, dirPath, /* followSymlink= */ true))
        .hasValue(
            FileInfo.builder()
                .setPath(dirPath)
                .setType(FileType.SYMLINK)
                .setSymlinkType(FileType.DIR)
                .setPermissions(FilePermissions.from("rwxrwxrwx"))
                .build());
  }

  @Test
  public void getFileInfo_symlinkDir_followSymlink_api23() throws Exception {
    int sdkVersion = 23;
    String dirParentDir = "/data/test_dir/";
    String dirName = "test_dir";
    String dirPath = PathUtil.join(dirParentDir, dirName);

    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l " + dirParentDir),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:28 test_dir -> /data/local/original_test_dir");
    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -F " + dirParentDir),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn("ld test_dir");

    assertThat(androidFileUtil.getFileInfo(SERIAL, sdkVersion, dirPath, /* followSymlink= */ true))
        .hasValue(
            FileInfo.builder()
                .setPath(dirPath)
                .setType(FileType.SYMLINK)
                .setSymlinkType(FileType.DIR)
                .setPermissions(FilePermissions.from("rwxrwxrwx"))
                .build());
  }

  @Test
  public void getPushedFileOrDirFinalDestinationPathOnDevice_srcIsFile_desIsExistedFile()
      throws Exception {
    int sdkVersion = 28;
    androidFileUtil =
        spy(
            new AndroidFileUtil(
                adb, fileUtil, androidUserUtil, androidSystemSettingUtil, androidSystemSpecUtil));
    String srcFilePathOnHost = "/path/to/srcFile";
    String desFilePathOnDevice = "/data/local/desFile";
    doReturn(false).when(androidFileUtil).isDirExist(SERIAL, sdkVersion, desFilePathOnDevice);
    when(fileUtil.isDirExist(srcFilePathOnHost)).thenReturn(false);

    assertThat(
            androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
                SERIAL, sdkVersion, srcFilePathOnHost, desFilePathOnDevice))
        .isEqualTo(desFilePathOnDevice);
  }

  @Test
  public void getPushedFileOrDirFinalDestinationPathOnDevice_srcIsFile_desIsExistedDir()
      throws Exception {
    int sdkVersion = 28;
    androidFileUtil =
        spy(
            new AndroidFileUtil(
                adb, fileUtil, androidUserUtil, androidSystemSettingUtil, androidSystemSpecUtil));
    String srcFilePathOnHost = "/path/to/srcFile";
    String desDirPathOnDevice = "/data/local/desDir/";
    doReturn(true).when(androidFileUtil).isDirExist(SERIAL, sdkVersion, desDirPathOnDevice);
    when(fileUtil.isDirExist(srcFilePathOnHost)).thenReturn(false);

    assertThat(
            androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
                SERIAL, sdkVersion, srcFilePathOnHost, desDirPathOnDevice))
        .isEqualTo(PathUtil.join(desDirPathOnDevice, PathUtil.basename(srcFilePathOnHost)));
  }

  @Test
  public void getPushedFileOrDirFinalDestinationPathOnDevice_srcIsFile_desIsNotExistedFile()
      throws Exception {
    int sdkVersion = 28;
    androidFileUtil =
        spy(
            new AndroidFileUtil(
                adb, fileUtil, androidUserUtil, androidSystemSettingUtil, androidSystemSpecUtil));
    String srcFilePathOnHost = "/path/to/srcFile";
    String desFilePathOnDevice = "/data/local/desFile";
    doReturn(false).when(androidFileUtil).isDirExist(SERIAL, sdkVersion, desFilePathOnDevice);
    when(fileUtil.isDirExist(srcFilePathOnHost)).thenReturn(false);

    assertThat(
            androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
                SERIAL, sdkVersion, srcFilePathOnHost, desFilePathOnDevice))
        .isEqualTo(desFilePathOnDevice);
  }

  @Test
  public void getPushedFileOrDirFinalDestinationPathOnDevice_srcIsFile_desIsNotExistedDir()
      throws Exception {
    int sdkVersion = 28;
    androidFileUtil =
        spy(
            new AndroidFileUtil(
                adb, fileUtil, androidUserUtil, androidSystemSettingUtil, androidSystemSpecUtil));
    String srcFilePathOnHost = "/path/to/srcFile";
    String desDirPathOnDevice = "/data/local/desDir/";
    doReturn(false).when(androidFileUtil).isDirExist(SERIAL, sdkVersion, desDirPathOnDevice);
    when(fileUtil.isDirExist(srcFilePathOnHost)).thenReturn(false);

    assertThat(
            androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
                SERIAL, sdkVersion, srcFilePathOnHost, desDirPathOnDevice))
        .isEmpty();
  }

  @Test
  public void getPushedFileOrDirFinalDestinationPathOnDevice_srcIsDir_desIsExistedFile()
      throws Exception {
    int sdkVersion = 28;
    androidFileUtil =
        spy(
            new AndroidFileUtil(
                adb, fileUtil, androidUserUtil, androidSystemSettingUtil, androidSystemSpecUtil));
    String srcDirPathOnHost = "/path/to/srcDir";
    String desFilePathOnDevice = "/data/local/desFile";
    doReturn(true).when(androidFileUtil).isFileOrDirExisted(SERIAL, desFilePathOnDevice);
    doReturn(false).when(androidFileUtil).isDirExist(SERIAL, sdkVersion, desFilePathOnDevice);
    when(fileUtil.isDirExist(srcDirPathOnHost)).thenReturn(true);

    assertThat(
            androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
                SERIAL, sdkVersion, srcDirPathOnHost, desFilePathOnDevice))
        .isEmpty();
  }

  @Test
  public void getPushedFileOrDirFinalDestinationPathOnDevice_srcIsDir_desIsExistedDir()
      throws Exception {
    int sdkVersion = 28;
    androidFileUtil =
        spy(
            new AndroidFileUtil(
                adb, fileUtil, androidUserUtil, androidSystemSettingUtil, androidSystemSpecUtil));
    String srcDirPathOnHost = "/path/to/srcDir";
    String desDirPathOnDevice = "/data/local/desDir";
    doReturn(true).when(androidFileUtil).isFileOrDirExisted(SERIAL, desDirPathOnDevice);
    doReturn(true).when(androidFileUtil).isDirExist(SERIAL, sdkVersion, desDirPathOnDevice);
    when(fileUtil.isDirExist(srcDirPathOnHost)).thenReturn(true);

    assertThat(
            androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
                SERIAL, sdkVersion, srcDirPathOnHost, desDirPathOnDevice))
        .isEqualTo(PathUtil.join(desDirPathOnDevice, PathUtil.basename(srcDirPathOnHost)));
  }

  @Test
  public void getPushedFileOrDirFinalDestinationPathOnDevice_srcIsDir_desIsNotExistedFile()
      throws Exception {
    int sdkVersion = 28;
    androidFileUtil =
        spy(
            new AndroidFileUtil(
                adb, fileUtil, androidUserUtil, androidSystemSettingUtil, androidSystemSpecUtil));
    String srcDirPathOnHost = "/path/to/srcDir";
    String desFilePathOnDevice = "/data/local/desFile";
    doReturn(false).when(androidFileUtil).isFileOrDirExisted(SERIAL, desFilePathOnDevice);
    when(fileUtil.isDirExist(srcDirPathOnHost)).thenReturn(true);

    assertThat(
            androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
                SERIAL, sdkVersion, srcDirPathOnHost, desFilePathOnDevice))
        .isEqualTo(desFilePathOnDevice);
  }

  @Test
  public void getPushedFileOrDirFinalDestinationPathOnDevice_srcIsDir_desIsNotExistedDir()
      throws Exception {
    int sdkVersion = 28;
    androidFileUtil =
        spy(
            new AndroidFileUtil(
                adb, fileUtil, androidUserUtil, androidSystemSettingUtil, androidSystemSpecUtil));
    String srcDirPathOnHost = "/path/to/srcDir";
    String desDirPathOnDevice = "/data/local/desDir";
    doReturn(false).when(androidFileUtil).isFileOrDirExisted(SERIAL, desDirPathOnDevice);
    when(fileUtil.isDirExist(srcDirPathOnHost)).thenReturn(true);

    assertThat(
            androidFileUtil.getPushedFileOrDirFinalDestinationPathOnDevice(
                SERIAL, sdkVersion, srcDirPathOnHost, desDirPathOnDevice))
        .isEqualTo(desDirPathOnDevice);
  }

  @Test
  public void getStorageInfo_external_androidN() throws Exception {
    when(adb.runShell(SERIAL, AndroidFileUtil.ADB_SHELL_GET_DISK_INFO))
        .thenReturn(
            "Filesystem     1K-blocks   Used  Available Use% Mounted on\n"
                + "/dev/fuse           26225216   122120  26103096   1% /storage/emulated");

    StorageInfo storageInfo = androidFileUtil.getStorageInfo(SERIAL, true);

    assertThat(storageInfo.freeKB()).isEqualTo(26103096L);
    assertThat(storageInfo.totalKB()).isEqualTo(26225216L);
  }

  @Test
  public void getStorageInfo_external_illegalCases() throws Exception {
    when(adb.runShell(SERIAL, AndroidFileUtil.ADB_SHELL_GET_DISK_INFO))
        .thenReturn("Device not found")
        .thenReturn(
            "Filesystem               Size     Used     Free   Blksize\n"
                + "/storage/sdcard0         -1.0K     -1.0K     -1.0K   4096\n")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "adb error"));

    for (int i = 0; i < 2; i++) {
      assertThat(
              assertThrows(
                      MobileHarnessException.class,
                      () -> androidFileUtil.getStorageInfo(SERIAL, true))
                  .getErrorId())
          .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_INVALID_DISK_INFO);
    }
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidFileUtil.getStorageInfo(SERIAL, true))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_GET_DISK_INFO_ERROR);
  }

  @Test
  public void getStorageInfo_internal_preAndroidN() throws Exception {
    when(adb.runShell(SERIAL, AndroidFileUtil.ADB_SHELL_GET_INTERNAL_STORAGE))
        .thenReturn(
            "Filesystem               Size     Used     Free   Blksize\n"
                + "/data     2.9G   848.2M     2.1G   4096\n");

    StorageInfo storageInfo = androidFileUtil.getStorageInfo(SERIAL, false);

    assertThat(storageInfo.freeKB()).isEqualTo((long) (2.1 * (1 << 20)));
    assertThat(storageInfo.totalKB()).isEqualTo((long) (2.9 * (1 << 20)));
  }

  @Test
  public void isDirExist() throws Exception {
    int sdkVersion = 28;
    String fileOrDirParentDirPath = "/sdcard/dir/";
    String fileOrDirBasename = "test";
    String fileOrDirPath = PathUtil.join(fileOrDirParentDirPath, fileOrDirBasename);
    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l " + fileOrDirParentDirPath),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn("drwxrwxrwx root   root    2019-07-17 05:28 " + fileOrDirBasename)
        .thenReturn("-rwxrwxrwx root   root    2019-07-17 05:29 " + fileOrDirBasename)
        .thenReturn("");
    when(adb.runShellWithRetry(SERIAL, "ls " + fileOrDirPath))
        .thenReturn(AndroidFileUtil.OUTPUT_NO_FILE_OR_DIR);

    assertThat(androidFileUtil.isDirExist(SERIAL, sdkVersion, fileOrDirPath)).isTrue();
    assertThat(androidFileUtil.isDirExist(SERIAL, sdkVersion, fileOrDirPath)).isFalse();
    assertThat(androidFileUtil.isDirExist(SERIAL, sdkVersion, fileOrDirPath)).isFalse();
  }

  @Test
  public void isDirExist_symlink_api28() throws Exception {
    int sdkVersion = 28;
    String fileOrDirParentDirPath = "/sdcard/dir/";
    String fileOrDirBasename = "test";
    String fileOrDirPath = PathUtil.join(fileOrDirParentDirPath, fileOrDirBasename);

    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l " + fileOrDirParentDirPath),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:28 "
                + fileOrDirBasename
                + " -> /linked_target")
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:29 "
                + fileOrDirBasename
                + " -> /linked_target")
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:29 "
                + fileOrDirBasename
                + " -> /linked_target");
    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l -L " + fileOrDirParentDirPath),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn("drwxrwx--x root   root    2019-07-18 15:16 " + fileOrDirBasename)
        .thenReturn("-rwxrwx--x root   root    2019-07-18 15:16 " + fileOrDirBasename)
        .thenAnswer(
            invocation -> {
              LineCallback callback = (LineCallback) invocation.getArguments()[3];
              callback.onLine(
                  String.format("ls: //%s: No such file or directory", fileOrDirBasename));
              throw new MobileHarnessException(
                  AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE,
                  "Failed to execute command");
            });

    assertThat(androidFileUtil.isDirExist(SERIAL, sdkVersion, fileOrDirPath)).isTrue();
    assertThat(androidFileUtil.isDirExist(SERIAL, sdkVersion, fileOrDirPath)).isFalse();
    assertThat(androidFileUtil.isDirExist(SERIAL, sdkVersion, fileOrDirPath)).isFalse();
  }

  @Test
  public void isDirExist_symlink_api23() throws Exception {
    int sdkVersion = 23;
    String fileOrDirParentDirPath = "/sdcard/dir/";
    String fileOrDirBasename = "test";
    String fileOrDirPath = PathUtil.join(fileOrDirParentDirPath, fileOrDirBasename);

    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l " + fileOrDirParentDirPath),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:28 "
                + fileOrDirBasename
                + " -> /linked_target")
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:29 "
                + fileOrDirBasename
                + " -> /linked_target")
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:29 "
                + fileOrDirBasename
                + " -> /linked_target");
    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -F " + fileOrDirParentDirPath),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn("ld " + fileOrDirBasename)
        .thenReturn("l- " + fileOrDirBasename)
        .thenReturn("l? " + fileOrDirBasename);

    assertThat(androidFileUtil.isDirExist(SERIAL, sdkVersion, fileOrDirPath)).isTrue();
    assertThat(androidFileUtil.isDirExist(SERIAL, sdkVersion, fileOrDirPath)).isFalse();
    assertThat(androidFileUtil.isDirExist(SERIAL, sdkVersion, fileOrDirPath)).isFalse();
  }

  @Test
  public void isFileExist() throws Exception {
    int sdkVersion = 28;
    String fileOrDirParentDirPath = "/sdcard/dir/";
    String fileOrDirBasename = "test";
    String fileOrDirPath = PathUtil.join(fileOrDirParentDirPath, fileOrDirBasename);
    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l " + fileOrDirParentDirPath),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn("drwxrwxrwx root   root    2019-07-17 05:28 " + fileOrDirBasename)
        .thenReturn("-rwxrwxrwx root   root    2019-07-17 05:29 " + fileOrDirBasename)
        .thenReturn("");
    when(adb.runShellWithRetry(SERIAL, "ls " + fileOrDirPath))
        .thenReturn(AndroidFileUtil.OUTPUT_NO_FILE_OR_DIR);

    assertThat(androidFileUtil.isFileExist(SERIAL, sdkVersion, fileOrDirPath)).isFalse();
    assertThat(androidFileUtil.isFileExist(SERIAL, sdkVersion, fileOrDirPath)).isTrue();
    assertThat(androidFileUtil.isFileExist(SERIAL, sdkVersion, fileOrDirPath)).isFalse();
  }

  @Test
  public void isFileExist_symlink_api28() throws Exception {
    int sdkVersion = 28;
    String fileOrDirParentDirPath = "/sdcard/dir/";
    String fileOrDirBasename = "test";
    String fileOrDirPath = PathUtil.join(fileOrDirParentDirPath, fileOrDirBasename);

    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l " + fileOrDirParentDirPath),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:28 "
                + fileOrDirBasename
                + " -> /linked_target")
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:29 "
                + fileOrDirBasename
                + " -> /linked_target")
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:29 "
                + fileOrDirBasename
                + " -> /linked_target");
    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l -L " + fileOrDirParentDirPath),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn("drwxrwx--x root   root    2019-07-18 15:16 " + fileOrDirBasename)
        .thenReturn("-rwxrwx--x root   root    2019-07-18 15:16 " + fileOrDirBasename)
        .thenAnswer(
            invocation -> {
              LineCallback callback = (LineCallback) invocation.getArguments()[3];
              callback.onLine(
                  String.format("ls: //%s: No such file or directory", fileOrDirBasename));
              throw new MobileHarnessException(
                  AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE,
                  "Failed to execute command");
            });

    assertThat(androidFileUtil.isFileExist(SERIAL, sdkVersion, fileOrDirPath)).isFalse();
    assertThat(androidFileUtil.isFileExist(SERIAL, sdkVersion, fileOrDirPath)).isTrue();
    assertThat(androidFileUtil.isFileExist(SERIAL, sdkVersion, fileOrDirPath)).isFalse();
  }

  @Test
  public void isFileExist_symlink_api23() throws Exception {
    int sdkVersion = 23;
    String fileOrDirParentDirPath = "/sdcard/dir/";
    String fileOrDirBasename = "test";
    String fileOrDirPath = PathUtil.join(fileOrDirParentDirPath, fileOrDirBasename);

    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l " + fileOrDirParentDirPath),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:28 "
                + fileOrDirBasename
                + " -> /linked_target")
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:29 "
                + fileOrDirBasename
                + " -> /linked_target")
        .thenReturn(
            "lrwxrwxrwx root   root    2019-07-17 05:29 "
                + fileOrDirBasename
                + " -> /linked_target");
    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -F " + fileOrDirParentDirPath),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn("ld " + fileOrDirBasename)
        .thenReturn("l- " + fileOrDirBasename)
        .thenReturn("l? " + fileOrDirBasename);

    assertThat(androidFileUtil.isFileExist(SERIAL, sdkVersion, fileOrDirPath)).isFalse();
    assertThat(androidFileUtil.isFileExist(SERIAL, sdkVersion, fileOrDirPath)).isTrue();
    assertThat(androidFileUtil.isFileExist(SERIAL, sdkVersion, fileOrDirPath)).isFalse();
  }

  @Test
  public void isFileOrDirExisted() throws Exception {
    String fileOrDirPath = "/sdcard/test";
    when(adb.runShellWithRetry(SERIAL, AndroidFileUtil.ADB_SHELL_LIST_FILES + " " + fileOrDirPath))
        .thenReturn(fileOrDirPath)
        .thenReturn("error:" + AndroidFileUtil.OUTPUT_NO_FILE_OR_DIR)
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE,
                AndroidFileUtil.OUTPUT_NO_FILE_OR_DIR))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "error"));

    assertThat(androidFileUtil.isFileOrDirExisted(SERIAL, fileOrDirPath)).isTrue();
    assertThat(androidFileUtil.isFileOrDirExisted(SERIAL, fileOrDirPath)).isFalse();
    assertThat(androidFileUtil.isFileOrDirExisted(SERIAL, fileOrDirPath)).isFalse();
    assertThrows(
        MobileHarnessException.class,
        () -> androidFileUtil.isFileOrDirExisted(SERIAL, fileOrDirPath));
  }

  @Test
  public void listFilesInOrder() throws Exception {
    String fileOrDirPath = "/sdcard/test";
    when(adb.runShellWithRetry(SERIAL, AndroidFileUtil.ADB_SHELL_LIST_FILES + " " + fileOrDirPath))
        .thenReturn(fileOrDirPath)
        .thenReturn("file2\n" + "file1\n" + "a.txt\n" + "_132\n" + "ABC\n")
        .thenReturn("error:" + AndroidFileUtil.OUTPUT_NO_FILE_OR_DIR)
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "error"));

    assertThat(androidFileUtil.listFilesInOrder(SERIAL, fileOrDirPath))
        .containsExactly(fileOrDirPath);
    assertThat(androidFileUtil.listFilesInOrder(SERIAL, fileOrDirPath))
        .containsExactly("ABC", "_132", "a.txt", "file1", "file2")
        .inOrder();
    assertThat(androidFileUtil.listFilesInOrder(SERIAL, fileOrDirPath)).isEmpty();
    assertThrows(
        MobileHarnessException.class,
        () -> androidFileUtil.listFilesInOrder(SERIAL, fileOrDirPath));
  }

  @Test
  public void makeDirectory() throws Exception {
    String dirPathOnDevice = "/abc";
    when(adb.runShellWithRetry(
            SERIAL,
            String.format(AndroidFileUtil.ADB_SHELL_TEMPLATE_MAKE_DIRECTORY, dirPathOnDevice)))
        .thenReturn("Error")
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Command Failed"));

    assertThat(
            assertThrows(
                MobileHarnessException.class,
                () -> androidFileUtil.makeDirectory(SERIAL, dirPathOnDevice)))
        .hasMessageThat()
        .contains("Error");
    androidFileUtil.makeDirectory(SERIAL, dirPathOnDevice);
    assertThat(
            assertThrows(
                MobileHarnessException.class,
                () -> androidFileUtil.makeDirectory(SERIAL, dirPathOnDevice)))
        .hasMessageThat()
        .contains("Command Failed");
  }

  @Test
  public void makeDirectory_noSpaceLeft_satelliteLab() throws Exception {
    String dirPathOnDevice = "/abc";
    when(adb.runShellWithRetry(
            SERIAL,
            String.format(AndroidFileUtil.ADB_SHELL_TEMPLATE_MAKE_DIRECTORY, dirPathOnDevice)))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE,
                String.format("mkdir: '/abc': %s", AndroidFileUtil.OUTPUT_NO_SPACE)));

    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () -> androidFileUtil.makeDirectory(SERIAL, dirPathOnDevice));
    assertThat(e.getErrorId())
        .isEqualTo(
            AndroidErrorId.ANDROID_FILE_UTIL_NO_SPACE_TO_MAKE_DIRECTORY_ERROR_IN_SATELLITE_LAB);
  }

  @Test
  public void makeFileExecutable() throws Exception {
    String filePathOnDevice = "/path/to/file";
    when(adb.runShellWithRetry(
            SERIAL,
            String.format(AndroidFileUtil.ADB_SHELL_MAKE_FILE_EXECUTABLE, filePathOnDevice)))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    androidFileUtil.makeFileExecutable(SERIAL, filePathOnDevice);
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidFileUtil.makeFileExecutable(SERIAL, filePathOnDevice))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_MAKE_FILE_EXECUTABLE_ERROR);
  }

  @Test
  public void makeFileExecutableReadableNonWritable() throws Exception {
    String filePathOnDevice = "/path/to/file";
    when(adb.runShellWithRetry(
            SERIAL,
            String.format(
                AndroidFileUtil.ADB_SHELL_MAKE_FILE_EXECUTABLE_READABLE_NON_WRITEABLE,
                filePathOnDevice)))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    androidFileUtil.makeFileExecutableReadableNonWritable(SERIAL, filePathOnDevice);
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        androidFileUtil.makeFileExecutableReadableNonWritable(
                            SERIAL, filePathOnDevice))
                .getErrorId())
        .isEqualTo(
            AndroidErrorId.ANDROID_FILE_UTIL_MAKE_FILE_EXECUTABLE_READABLE_NOT_WRITEABLE_ERROR);
  }

  @Test
  public void md5() throws Exception {
    int sdkVersion = 18;
    when(adb.runShellWithRetry(SERIAL, AndroidFileUtil.ADB_SHELL_MD5 + " " + APK_PATH))
        .thenReturn("9130e3195c42b557e8cf4b532bb92671 " + APK_PATH)
        .thenReturn("No such file or directory")
        .thenReturn("some other error message")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    // Get md5 successfully.
    assertThat(androidFileUtil.md5(SERIAL, sdkVersion, APK_PATH))
        .isEqualTo("9130e3195c42b557e8cf4b532bb92671");
    // Return empty string if APK_PATH doesn't exist.
    assertThat(androidFileUtil.md5(SERIAL, sdkVersion, APK_PATH)).isEmpty();
    // Throw exception.
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidFileUtil.md5(SERIAL, sdkVersion, APK_PATH))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_INVALID_MD5_OUTPUT);
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidFileUtil.md5(SERIAL, sdkVersion, APK_PATH))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_GET_FILE_MD5_ERROR);
  }

  @Test
  public void pull() throws Exception {
    String srcFileOrDirOnDevice = "/sdcard/screenshot";
    String desFileOrDirOnHost = "/var/www/tmp";
    when(adb.runWithRetry(
            eq(SERIAL),
            aryEq(
                new String[] {
                  AndroidFileUtil.ADB_ARG_PULL, srcFileOrDirOnDevice, desFileOrDirOnHost
                })))
        .thenReturn("");
    androidFileUtil.pull(SERIAL, srcFileOrDirOnDevice, desFileOrDirOnHost);
  }

  @Test
  public void push() throws Exception {
    int sdkVersion = 28;
    String desFileOrDirOnDevice = "/sdcard/screenshot";
    String srcFileOrDirOnHost = "/var/www/tmp";

    when(fileUtil.isDirExist(srcFileOrDirOnHost)).thenReturn(false);
    when(adb.runWithRetry(
            eq(SERIAL),
            aryEq(
                new String[] {
                  AndroidFileUtil.ADB_ARG_PUSH, srcFileOrDirOnHost, desFileOrDirOnDevice
                }),
            eq(PUSH_TIME_OUT)))
        .thenReturn("");

    androidFileUtil.push(
        SERIAL, sdkVersion, srcFileOrDirOnHost, desFileOrDirOnDevice, PUSH_TIME_OUT);
  }

  @Test
  public void push_folder() throws Exception {
    int sdkVersion = 28;
    String desFileOrDirParentDirOnDevice = "/sdcard/";
    String desFileOrDirBasenameOnDevice = "www";
    String desFileOrDirOnDevice =
        PathUtil.join(desFileOrDirParentDirOnDevice, desFileOrDirBasenameOnDevice) + "/";
    String srcFileOrDirOnHost = "/var/www/";
    ImmutableList<String> filesInDirOnHost = ImmutableList.of("monkey.jar", "walkman.jar");

    when(fileUtil.isDirExist(srcFileOrDirOnHost)).thenReturn(true);
    when(adb.runShellWithRetry(
            SERIAL, AndroidFileUtil.ADB_SHELL_LIST_FILES + " " + desFileOrDirOnDevice))
        .thenReturn(PathUtil.removeExtraneousSlashes(desFileOrDirOnDevice));
    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l " + desFileOrDirParentDirOnDevice),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn("drwxrwxrwx root   root    2019-07-17 05:28 " + desFileOrDirBasenameOnDevice);
    when(fileUtil.listFilePaths(srcFileOrDirOnHost, true)).thenReturn(filesInDirOnHost);
    when(adb.runWithRetry(eq(SERIAL), any(String[].class))).thenReturn("");

    androidFileUtil.push(
        SERIAL, sdkVersion, srcFileOrDirOnHost, desFileOrDirOnDevice, PUSH_TIME_OUT);
  }

  @Test
  public void push_folder_commandFailed_throwException() throws Exception {
    int sdkVersion = 28;
    String desFileOrDirParentDirOnDevice = "/sdcard/";
    String desFileOrDirBasenameOnDevice = "www";
    String desFileOrDirOnDevice =
        PathUtil.join(desFileOrDirParentDirOnDevice, desFileOrDirBasenameOnDevice) + "/";
    String srcFileOrDirOnHost = "/var/www/";
    ImmutableList<String> filesInDirOnHost = ImmutableList.of("monkey.jar", "walkman.jar");

    when(fileUtil.isDirExist(srcFileOrDirOnHost)).thenReturn(true);
    when(adb.runShellWithRetry(
            SERIAL, AndroidFileUtil.ADB_SHELL_LIST_FILES + " " + desFileOrDirOnDevice))
        .thenReturn(PathUtil.removeExtraneousSlashes(desFileOrDirOnDevice));
    when(adb.runShell(
            eq(SERIAL),
            eq(AndroidFileUtil.ADB_SHELL_LIST_FILES + " -l " + desFileOrDirParentDirOnDevice),
            eq(AndroidFileUtil.DEFAULT_LS_COMMAND_TIMEOUT),
            any(LineCallback.class)))
        .thenReturn("drwxrwxrwx root   root    2019-07-17 05:28 " + desFileOrDirBasenameOnDevice);
    when(fileUtil.listFilePaths(srcFileOrDirOnHost, true))
        .thenReturn(filesInDirOnHost)
        .thenThrow(
            new MobileHarnessException(BasicErrorId.LOCAL_DIR_LIST_FILE_OR_DIRS_ERROR, "Error"));
    when(adb.runWithRetry(eq(SERIAL), any(String[].class), eq(PUSH_TIME_OUT)))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        androidFileUtil.push(
                            SERIAL,
                            sdkVersion,
                            srcFileOrDirOnHost,
                            desFileOrDirOnDevice,
                            PUSH_TIME_OUT))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_PUSH_FILE_ADB_ERROR);
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        androidFileUtil.push(
                            SERIAL,
                            sdkVersion,
                            srcFileOrDirOnHost,
                            desFileOrDirOnDevice,
                            PUSH_TIME_OUT))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_PUSH_FILE_LOCAL_FILE_ERROR);
  }

  @Test
  public void remount_checkResults() throws Exception {
    when(adb.run(eq(SERIAL), aryEq(new String[] {AndroidFileUtil.ADB_ARG_REMOUNT})))
        .thenReturn("remount succeeded")
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));
    when(androidSystemSettingUtil.getDeviceSdkVersion(eq(SERIAL))).thenReturn(29);
    when(androidSystemSpecUtil.isEmulator(eq(SERIAL))).thenReturn(false);

    androidFileUtil.remount(SERIAL, true);

    verify(adb).run(eq(SERIAL), aryEq(new String[] {AndroidFileUtil.ADB_ARG_REMOUNT}));
    assertThat(
            assertThrows(MobileHarnessException.class, () -> androidFileUtil.remount(SERIAL, true))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_REMOUNT_ERROR);
    assertThat(
            assertThrows(MobileHarnessException.class, () -> androidFileUtil.remount(SERIAL, true))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_REMOUNT_ERROR);
  }

  @Test
  public void remount_sdk34_checkResults() throws Exception {
    when(adb.run(eq(SERIAL), aryEq(new String[] {AndroidFileUtil.ADB_ARG_REMOUNT})))
        .thenReturn("Remount succeeded")
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));
    when(androidSystemSettingUtil.getDeviceSdkVersion(eq(SERIAL))).thenReturn(34);
    when(androidSystemSpecUtil.isEmulator(eq(SERIAL))).thenReturn(false);

    androidFileUtil.remount(SERIAL, true);

    verify(adb).run(eq(SERIAL), aryEq(new String[] {AndroidFileUtil.ADB_ARG_REMOUNT}));
    assertThat(
            assertThrows(MobileHarnessException.class, () -> androidFileUtil.remount(SERIAL, true))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_REMOUNT_ERROR);
    assertThat(
            assertThrows(MobileHarnessException.class, () -> androidFileUtil.remount(SERIAL, true))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_REMOUNT_ERROR);
  }

  @Test
  public void remount() throws Exception {
    when(adb.run(eq(SERIAL), aryEq(new String[] {AndroidFileUtil.ADB_ARG_REMOUNT})))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    when(androidSystemSettingUtil.getDeviceSdkVersion(eq(SERIAL))).thenReturn(29);
    when(androidSystemSpecUtil.isEmulator(eq(SERIAL))).thenReturn(false);

    androidFileUtil.remount(SERIAL);
    verify(adb).run(eq(SERIAL), aryEq(new String[] {AndroidFileUtil.ADB_ARG_REMOUNT}));

    assertThat(
            assertThrows(MobileHarnessException.class, () -> androidFileUtil.remount(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_REMOUNT_ERROR);
  }

  @Test
  public void remount_exitCode11() throws Exception {
    String adbRemountOutput =
        "Failed command with exit_code=11 and success_exit_codes=[0], result=[code=11, out=[Binder"
            + " ioctl to enable oneway spam detection failed: Invalid argument"
            + "Disabling verity for /system"
            + "Disabling verity for /system_ext"
            + "Waited one second for gsiservice (is service started? are binder threads started and"
            + " available?)"
            + "Using overlayfs for /system_ext"
            + "Disabling verity for /vendor"
            + "Using overlayfs for /vendor"
            + "Disabling verity for /product"
            + "Using overlayfs for /product"
            + "Now reboot your device for settings to take effect";
    when(adb.run(eq(SERIAL), aryEq(new String[] {AndroidFileUtil.ADB_ARG_REMOUNT})))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, adbRemountOutput));

    when(androidSystemSettingUtil.getDeviceSdkVersion(eq(SERIAL))).thenReturn(29);
    when(androidSystemSpecUtil.isEmulator(eq(SERIAL))).thenReturn(false);

    androidFileUtil.remount(SERIAL);
    verify(adb).run(eq(SERIAL), aryEq(new String[] {AndroidFileUtil.ADB_ARG_REMOUNT}));
  }

  @Test
  public void remountSDK30Emulator() throws Exception {
    when(adb.run(eq(SERIAL), aryEq(new String[] {"shell", "mount", "-o", "rw,remount", "/"})))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    when(androidSystemSettingUtil.getDeviceSdkVersion(eq(SERIAL))).thenReturn(30);
    when(androidSystemSpecUtil.isEmulator(eq(SERIAL))).thenReturn(true);

    androidFileUtil.remount(SERIAL);
    verify(adb).run(eq(SERIAL), aryEq(new String[] {"shell", "mount", "-o", "rw,remount", "/"}));

    assertThat(
            assertThrows(MobileHarnessException.class, () -> androidFileUtil.remount(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_REMOUNT_ERROR);
  }

  @Test
  public void remountSdk33Emulator() throws Exception {
    when(adb.run(eq(SERIAL), aryEq(new String[] {"shell", "remount"})))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    when(androidSystemSettingUtil.getDeviceSdkVersion(eq(SERIAL))).thenReturn(33);
    when(androidSystemSpecUtil.isEmulator(eq(SERIAL))).thenReturn(true);

    androidFileUtil.remount(SERIAL);
    verify(adb).run(eq(SERIAL), aryEq(new String[] {"shell", "remount"}));

    assertThat(
            assertThrows(MobileHarnessException.class, () -> androidFileUtil.remount(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_REMOUNT_ERROR);
  }

  @Test
  public void removeFiles() throws Exception {
    String fileOrDirPathPattern = "/sdcard\"'(xxxx";
    String args =
        String.format(
            AndroidFileUtil.ADB_SHELL_TEMPLATE_REMOVE_FILES_PATTERN, "'/sdcard\"'\\''(xxxx'");
    when(adb.runShellWithRetry(SERIAL, args, Duration.ofSeconds(60)))
        .thenReturn("error:" + AndroidFileUtil.OUTPUT_NO_FILE_OR_DIR)
        .thenReturn("error")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "adb error"));

    androidFileUtil.removeFiles(SERIAL, fileOrDirPathPattern);
    assertThrows(
        MobileHarnessException.class,
        () -> androidFileUtil.removeFiles(SERIAL, fileOrDirPathPattern));
    assertThrows(
        MobileHarnessException.class,
        () -> androidFileUtil.removeFiles(SERIAL, fileOrDirPathPattern));
  }

  @Test
  public void removeGlobFiles() throws Exception {
    String fileOrDirPathPattern = "/sdcard/s*";
    String args =
        String.format(
            AndroidFileUtil.ADB_SHELL_TEMPLATE_REMOVE_FILES_PATTERN, fileOrDirPathPattern);
    when(adb.runShellWithRetry(SERIAL, args, Duration.ofSeconds(60)))
        .thenReturn("error:" + AndroidFileUtil.OUTPUT_NO_FILE_OR_DIR)
        .thenReturn("error");

    androidFileUtil.removeFiles(SERIAL, fileOrDirPathPattern);
    assertThrows(
        MobileHarnessException.class,
        () -> androidFileUtil.removeFiles(SERIAL, fileOrDirPathPattern));
  }

  @Test
  public void removeFiles_invalidArgument_throwException() throws Exception {
    String fileOrDirPathPattern = "/sdcard/test_dir/.";
    String args =
        String.format(
            AndroidFileUtil.ADB_SHELL_TEMPLATE_REMOVE_FILES_PATTERN, fileOrDirPathPattern);
    when(adb.runShellWithRetry(SERIAL, args, Duration.ofSeconds(60)))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE,
                String.format(
                    "rm: %s%s",
                    fileOrDirPathPattern, AndroidFileUtil.INVALID_ARGUMENT_REMOVE_FILES)));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidFileUtil.removeFiles(SERIAL, fileOrDirPathPattern))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_REMOVE_FILE_INVALID_ARGUMENT);
  }

  @Test
  public void renameFiles() throws Exception {
    String srcFileName = "/sdcard/new_file";
    String desFileName = "/sdcard/old_file";
    when(adb.runShellWithRetry(
            SERIAL, AndroidFileUtil.ADB_SHELL_RENAME_FILES + " " + srcFileName + " " + desFileName))
        .thenReturn(" ")
        .thenReturn("error");

    androidFileUtil.renameFiles(SERIAL, srcFileName, desFileName);
    assertThrows(
        MobileHarnessException.class,
        () -> androidFileUtil.renameFiles(SERIAL, srcFileName, desFileName));
  }

  @Test
  public void createSymlink_retryFails() throws Exception {
    String srcFileOrDirPath = "/sdcard/srcdir";
    String desFileOrDirPath = "/sdcard/symlink/";
    when(adb.runShellWithRetry(
            SERIAL,
            String.format(
                AndroidFileUtil.ADB_SHELL_TEMPLATE_CREATE_SYMLINK,
                srcFileOrDirPath,
                desFileOrDirPath)))
        .thenReturn(" ")
        .thenReturn("error");

    androidFileUtil.createSymlink(SERIAL, srcFileOrDirPath, desFileOrDirPath);
    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () -> androidFileUtil.createSymlink(SERIAL, srcFileOrDirPath, desFileOrDirPath));
    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_CREATE_SYMLINK_ERROR);
    assertThat(e).hasMessageThat().contains("error");
  }

  @Test
  public void createSymlink_retryFails_throwException() throws Exception {
    String srcFileOrDirPath = "/sdcard/srcdir";
    String desFileOrDirPath = "/sdcard/symlink/";
    when(adb.runShellWithRetry(
            SERIAL,
            String.format(
                AndroidFileUtil.ADB_SHELL_TEMPLATE_CREATE_SYMLINK,
                srcFileOrDirPath,
                desFileOrDirPath)))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_FILE_UTIL_CREATE_SYMLINK_ERROR, "Error"));

    androidFileUtil.createSymlink(SERIAL, srcFileOrDirPath, desFileOrDirPath);
    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () -> androidFileUtil.createSymlink(SERIAL, srcFileOrDirPath, desFileOrDirPath));
    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_CREATE_SYMLINK_ERROR);
    assertThat(e).hasMessageThat().contains("Error");
  }
}
