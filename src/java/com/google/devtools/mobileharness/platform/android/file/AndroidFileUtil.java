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

import static java.util.stream.Collectors.toCollection;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserUtil;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Utility class to manage file on Android device.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 *
 * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
 * separator on SDK>23. It's callers' responsibility to parse it correctly.
 */
public class AndroidFileUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String MULTI_USER_EXTERNAL_STORAGE_PATH_PREFIX = "/mnt/pass_through/";

  /** ADB arg for pulling file/dir from device. Should be followed with the src and des path. */
  @VisibleForTesting static final String ADB_ARG_PULL = "pull";

  /** ADB arg for pushing file/dir to device. Should be followed with the src and des path. */
  @VisibleForTesting static final String ADB_ARG_PUSH = "push";

  /** ADB arg for remounting the device. */
  @VisibleForTesting static final String ADB_ARG_REMOUNT = "remount";

  /** ADB shell command for getting the disk information. */
  @VisibleForTesting static final String ADB_SHELL_GET_DISK_INFO = "df $EXTERNAL_STORAGE";

  /** ADB shell command for getting the external storage path. */
  @VisibleForTesting static final String ADB_SHELL_GET_EXTERNAL_STORAGE = "echo $EXTERNAL_STORAGE";

  /** ADB shell command for getting the free internal storage information. */
  @VisibleForTesting static final String ADB_SHELL_GET_INTERNAL_STORAGE = "df /data";

  /** ADB shell command to list file/dir. Should be followed with the path in device. */
  @VisibleForTesting static final String ADB_SHELL_LIST_FILES = "ls";

  /** Indicator for the success of remounting the device. */
  private static final String ADB_REMOUNT_SUCCESS_INDICATOR = "remount succeeded";

  private static final String ADB_REMOUNT_REBOOT_INDICATOR =
      "Now reboot your device for settings to take effect";
  private static final String ADB_REMOUNT_EXIT_CODE_INDICATOR = "exit_code=11";

  /** Indicator for a file showed in "adb shell ls -l". */
  private static final char ADB_SHELL_LIST_FILE_INDICATOR = '-';

  /** Indicator for a directory showed in "adb shell ls -l". */
  private static final char ADB_SHELL_LIST_DIR_INDICATOR = 'd';

  /** Indicator for a symbolic linked file/dir showed in "adb shell ls -l". */
  private static final char ADB_SHELL_LIST_SYMLINK_INDICATOR = 'l';

  private static final Pattern ADB_SHELL_LIST_DETAILS_PATTERN =
      Pattern.compile(
          "^(?<fileOrDirAttr>[\\s\\S]{10})[\\s\\S]*\\d{2}:\\d{2}\\s+(?<fileOrDirName>[\\s\\S]*)");

  private static final Pattern ADB_SHELL_LIST_WITH_OPTION_F_PATTERN =
      Pattern.compile("^l[d\\-?]\\s+(?<fileOrDirName>[\\s\\S]*)");

  /** ADB shell command to make the file executable. */
  @VisibleForTesting static final String ADB_SHELL_MAKE_FILE_EXECUTABLE = "chmod 777 %s";

  /** ADB shell for calculating md5 in device with (sdk >= 16 && sdk <= 22). */
  @VisibleForTesting static final String ADB_SHELL_MD5 = "md5";

  /** ADB shell for calculating md5 in device with sdk >= 22. */
  @VisibleForTesting static final String ADB_SHELL_MD5SUM = "md5sum";

  /** ADB shell command to rename file/dir. Should be followed with the src & des paths. */
  @VisibleForTesting static final String ADB_SHELL_RENAME_FILES = "mv";

  /** ADB shell template for make directory. Should fill the dir path. */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_MAKE_DIRECTORY = "mkdir -p %s";

  /**
   * ADB shell template for removing file/dir. Format %1$s with path. "rm -f" is not support on
   * Android 3.x or older devices. While "rm" without "-f" on Android 4+ unrooted device will prompt
   * up to ask for user's confirm when deleting read-only files, which will block the process. Since
   * that only one rm will work, a path-not-found error will always be returned in the command
   * output but it's OK. .shellEscape
   *
   * <p>File or dir name need to be escaped by ShellUtils, because file name may contain '('.
   */
  @VisibleForTesting
  static final String ADB_SHELL_TEMPLATE_REMOVE_FILES_PATTERN = "rm -fr %1$s;rm -r %1$s";

  /** ADB shell command template to create symlink. */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_CREATE_SYMLINK = "ln -s %s %s";

  @VisibleForTesting static final Duration DEFAULT_LS_COMMAND_TIMEOUT = Duration.ofMinutes(5);

  /** Timeout of removing files. */
  @VisibleForTesting static final Duration DEFAULT_REMOVE_FILES_TIMEOUT = Duration.ofMinutes(1);

  /** Output signal when removing file/dir with invalid argument. */
  @VisibleForTesting static final String INVALID_ARGUMENT_REMOVE_FILES = ": Invalid argument";

  /** Output signal when file or dir does not exist. */
  @VisibleForTesting static final String OUTPUT_NO_FILE_OR_DIR = "No such file or directory";

  /** Output signal when device has no space left. */
  @VisibleForTesting static final String OUTPUT_NO_SPACE = "No space left on device";

  /** Short timeout for quick operations. */
  @VisibleForTesting static final Duration SHORT_COMMAND_TIMEOUT = Duration.ofSeconds(5);

  /** The string of "Filesystem". */
  private static final String STRING_FILESYSTEM = "Filesystem";

  /** system_user of the device. See go/adb-mu for more details. */
  private static final int SYSTEM_USER = 0;

  /** For operations with local files only. */
  private final LocalFileUtil localFileUtil;

  /** Utility class to manager user/profile on devices. */
  private final AndroidUserUtil androidUserUtil;

  /** Utility class to get device SDK version. */
  private final AndroidSystemSettingUtil androidSystemSettingUtil;

  /** Utility class to get device spec. */
  private final AndroidSystemSpecUtil androidSystemSpecUtil;

  /** {@code Adb} for running shell command on device. */
  private final Adb adb;

  public AndroidFileUtil() {
    this(
        new Adb(),
        new LocalFileUtil(),
        new AndroidUserUtil(),
        new AndroidSystemSettingUtil(),
        new AndroidSystemSpecUtil());
  }

  @VisibleForTesting
  AndroidFileUtil(
      Adb adb,
      LocalFileUtil localFileUtil,
      AndroidUserUtil androidUserUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidSystemSpecUtil androidSystemSpecUtil) {
    this.adb = adb;
    this.localFileUtil = localFileUtil;
    this.androidUserUtil = androidUserUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.androidSystemSpecUtil = androidSystemSpecUtil;
  }

  /**
   * Get the shared preferences key value pairs of a package. Example xml: <map> <long
   * name="android.app.binary.adbwatchdog.adbResetInterval" value="60000" /> <long
   * name="android.app.binary.adbwatchdog.adbTtl" value="43200000" /> <long
   * name="android.app.binary.adbwatchdog.adbCheckInterval" value="3600000" /> <string
   * name="android.app.binary.adbwatchdog.lastAdbResetReason"> 2020-08-03 23:03:50 - None
   * reset</string> </map>
   *
   * @param serial device id
   * @param packageName name of the package
   * @param fileName name of the shared preference file
   * @return shared preference key value pairs
   * @throws MobileHarnessException if command execution failed
   * @throws InterruptedException if interrupted
   */
  public Map<String, String> getSharedPrefs(String serial, String packageName, String fileName)
      throws MobileHarnessException, InterruptedException {
    String cmd =
        String.format(
            "run-as %s cat /data/data/%s/shared_prefs/%s", packageName, packageName, fileName);
    String output = "";
    try {
      output = adb.runShell(serial, cmd);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_CAT_FILE_EXE_ERROR, e.getMessage(), e);
    }
    return xmlToMap(output);
  }

  @VisibleForTesting
  Map<String, String> xmlToMap(String xml) throws MobileHarnessException {
    Map<String, String> map = new HashMap<>();
    Document xmlDoc;
    try {
      DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
      // Process XML securely, avoid attacks like XML External Entities (XXE)
      documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      xmlDoc =
          documentBuilderFactory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_CAT_FILE_PARSER_ERROR, e.getMessage(), e);
    }
    try {
      // Read long type shared prefs
      NodeList longPrefs = xmlDoc.getElementsByTagName("long");
      for (int i = 0; i < longPrefs.getLength(); i++) {
        String name = longPrefs.item(i).getAttributes().getNamedItem("name").getTextContent();
        String value = longPrefs.item(i).getAttributes().getNamedItem("value").getTextContent();
        map.put(name, value);
      }
      // Read string type shared prefs
      NodeList stringPrefs = xmlDoc.getElementsByTagName("string");
      for (int i = 0; i < stringPrefs.getLength(); i++) {
        String name = stringPrefs.item(i).getAttributes().getNamedItem("name").getTextContent();
        String value = stringPrefs.item(i).getTextContent();
        map.put(name, value);
      }
    } catch (NullPointerException e) {
      // NullPointerException may throw if the xml tree is not expected structure
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_CAT_FILE_PARSER_ERROR,
          String.format("Failed to parser shared_prefs with output[%s]", xml),
          e);
    }
    return map;
  }

  /**
   * Enables the underlying Adb object to have its command output logged to the class logger.
   *
   * <p>WARNING: This will log ALL command output for Adb commands from this instance of
   * AndroidUtil. Take caution to make sure this won't unintentionally spam your log.
   */
  public void enableCommandOutputLogging() {
    adb.enableCommandOutputLogging();
  }

  /**
   * Returns the external storage path of the device with the given serial ID. Support production
   * builds.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @throws MobileHarnessException if external storage is not found
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String getExternalStoragePath(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    String externalStoragePath;
    try {
      if (sdkVersion < AndroidVersion.PI.getStartSdkVersion()) {
        externalStoragePath =
            adb.runShellWithRetry(serial, ADB_SHELL_GET_EXTERNAL_STORAGE, SHORT_COMMAND_TIMEOUT);
      } else {
        int currentUser = androidUserUtil.getCurrentUser(serial, sdkVersion);
        logger.atInfo().log(
            "Device %s current user: %s, sdk version: %s", serial, currentUser, sdkVersion);
        if (currentUser == SYSTEM_USER
            || sdkVersion < AndroidVersion.ANDROID_11.getStartSdkVersion()) {
          externalStoragePath = "/storage/emulated/" + currentUser;
        } else {
          // special cases for multi user devices, especially, auto OS.
          externalStoragePath =
              MULTI_USER_EXTERNAL_STORAGE_PATH_PREFIX + currentUser + "/emulated/" + currentUser;
        }
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_GET_EXTERNAL_STORAGE_ERROR, e.getMessage(), e);
    }
    if (StrUtil.isEmptyOrWhitespace(externalStoragePath)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_EXTERNAL_STORAGE_NOT_FOUND,
          "External storage not found");
    }
    return externalStoragePath;
  }

  /**
   * Gets file/dir {@code targetPath} file info.
   *
   * <p>It's not able to get {@link FileInfo} of {@code targetPath} if it doesn't have permission to
   * access parent dir of {@code targetPath}, like "/storage/emulated" on unrooted devices. In this
   * case, returns {@link Optional#empty()}.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param targetPath path of file/dir
   * @param followSymlink whether to dereference file type of {@code targetPath} if it's a symbolic
   *     link
   * @throws MobileHarnessException if failed to run the command.
   * @throws InterruptedException if the thread executing the commands is interrupted.
   */
  public Optional<FileInfo> getFileInfo(
      String serial, int sdkVersion, String targetPath, boolean followSymlink)
      throws MobileHarnessException, InterruptedException {
    FileInfo.Builder fileInfoBuilder = FileInfo.builder().setPath(targetPath);
    if ("/".equals(targetPath)) {
      fileInfoBuilder.setType(FileType.DIR);
      return Optional.of(fileInfoBuilder.build());
    }
    // Append '/' to ensure it lists children of targetParentDirPath, required if
    // targetParentDirPath is a symlink
    String targetDirName = PathUtil.dirname(targetPath);
    String targetParentDirPath = "/".equals(targetDirName) ? "/" : targetDirName + "/";
    String targetBasename = PathUtil.basename(targetPath);
    String output;
    StringBuilder listFilesOutput = new StringBuilder();
    try {
      output =
          adb.runShell(
              serial,
              "ls -l " + targetParentDirPath,
              DEFAULT_LS_COMMAND_TIMEOUT,
              LineCallback.does(line -> listFilesOutput.append(line).append("\n")));
    } catch (MobileHarnessException e) {
      output = listFilesOutput.toString();
    }

    Optional<String> matchTargetLine =
        Splitters.LINE_SPLITTER.trimResults().omitEmptyStrings().splitToList(output).stream()
            .filter(
                line -> {
                  Matcher matcher = ADB_SHELL_LIST_DETAILS_PATTERN.matcher(line);
                  if (!matcher.find()) {
                    return false;
                  }
                  String fileOrDirName = matcher.group("fileOrDirName");
                  return fileOrDirName != null
                      && (targetBasename.equals(fileOrDirName)
                          || fileOrDirName.startsWith(targetBasename + " ->")); // symlink
                })
            .findFirst();
    if (matchTargetLine.isEmpty()) {
      logger.atInfo().log(
          "File/dir %s doesn't exist on device %s or doesn't have permission to access:%n%s",
          targetPath, serial, output);
      return Optional.empty();
    }

    Matcher matcher = ADB_SHELL_LIST_DETAILS_PATTERN.matcher(matchTargetLine.get());
    matcher.find();
    String fileOrDirAttr = matcher.group("fileOrDirAttr");
    fileInfoBuilder.setPermissions(FilePermissions.from(fileOrDirAttr.substring(1)));
    char fileOrDirIndicator = fileOrDirAttr.charAt(0);

    switch (fileOrDirIndicator) {
      case ADB_SHELL_LIST_FILE_INDICATOR:
        fileInfoBuilder.setType(FileType.FILE);
        break;
      case ADB_SHELL_LIST_DIR_INDICATOR:
        fileInfoBuilder.setType(FileType.DIR);
        break;
      case ADB_SHELL_LIST_SYMLINK_INDICATOR:
        fileInfoBuilder.setType(FileType.SYMLINK);
        break;
      default:
        fileInfoBuilder.setType(FileType.UNKNOWN);
    }

    if (fileOrDirIndicator != ADB_SHELL_LIST_SYMLINK_INDICATOR || !followSymlink) {
      return Optional.of(fileInfoBuilder.build());
    }

    // At this point, it's sure target file/dir a symbolic link file/dir
    Optional<FileType> symlinkFileOrDirType =
        getSymlinkFileOrDirType(serial, sdkVersion, targetPath);
    if (symlinkFileOrDirType.isEmpty() || FileType.UNKNOWN.equals(symlinkFileOrDirType.get())) {
      return Optional.of(fileInfoBuilder.setSymlinkType(FileType.UNKNOWN).build());
    } else {
      return Optional.of(fileInfoBuilder.setSymlinkType(symlinkFileOrDirType.get()).build());
    }
  }

  /**
   * Gets the final destination file/dir path on device to source file/dir on host.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param srcFileOrDirOnHost source file path on lab server host machine, could be a file or
   *     directory
   * @param desFileOrDirOnDevice destination file path on the device
   * @return the final destination file/dir path on device to source file/dir on host, or empty
   *     string if failed to get such a destination path on device.
   * @throws MobileHarnessException if failed to run the command.
   * @throws InterruptedException if the thread executing the commands is interrupted.
   */
  public String getPushedFileOrDirFinalDestinationPathOnDevice(
      String serial, int sdkVersion, String srcFileOrDirOnHost, String desFileOrDirOnDevice)
      throws MobileHarnessException, InterruptedException {
    /*
     * Below is an analysis for adb push (version 1.0.36)
     *
     * Source on Host  |                 Remote on Device
     *                 |            Exist        |       Not Exist
     *                 |      isFile  |  isDir   |   isFile   |   isDir
     *    isFile       |        #1    |    #2    |     #1     |     X
     *    isDir        |        X     |    #2    |     #1     |     #1
     *
     * #1: Destination file name as "desPathOnDevice"
     * #2: Destination file name as "desPathOnDevice + fileOrDirName(srcPathOnHost)"
     * X:  adb push will fail
     */
    if (!localFileUtil.isDirExist(srcFileOrDirOnHost)) {
      if (isDirExist(serial, sdkVersion, desFileOrDirOnDevice)) {
        return PathUtil.join(desFileOrDirOnDevice, PathUtil.basename(srcFileOrDirOnHost));
      } else if (desFileOrDirOnDevice.endsWith("/")) {
        logger.atWarning().log(
            "Cannot get final destination file path on device %s to source file %s: %s is a "
                + "non-existent dir on device",
            serial, srcFileOrDirOnHost, desFileOrDirOnDevice);
        return "";
      } else {
        return desFileOrDirOnDevice;
      }
    }

    if (!isFileOrDirExisted(serial, desFileOrDirOnDevice)) {
      return desFileOrDirOnDevice;
    } else if (isDirExist(serial, sdkVersion, desFileOrDirOnDevice)) {
      return PathUtil.join(desFileOrDirOnDevice, PathUtil.basename(srcFileOrDirOnHost));
    } else {
      logger.atWarning().log(
          "Cannot get final destination dir path on device %s to source dir %s: %s is an "
              + "existent file on device",
          serial, srcFileOrDirOnHost, desFileOrDirOnDevice);
      return "";
    }
  }

  /**
   * Get the device internal or external storage information. For external storage, the path to
   * check is "$EXTERNAL_STORAGE". For internal storage, the path to check is "/data". Note that the
   * returned storage has non-negative values and it might be zero.
   *
   * @param serial serial number of the device
   * @param isExternal whether getting external storage or intenral storage info
   * @throws MobileHarnessException if failed to run the command, or no valid disk information is
   *     found (API Level < 10), or the total internal storage space is negative.
   * @throws InterruptedException if the thread executing the commands is interrupted.
   */
  public StorageInfo getStorageInfo(String serial, boolean isExternal)
      throws MobileHarnessException, InterruptedException {
    AndroidErrorId getStorageInfoErrorId = null;
    AndroidErrorId invalidStorageInfoErrorId = null;
    String runCommand = null;
    String externalOrInternal = null;
    if (isExternal) {
      getStorageInfoErrorId = AndroidErrorId.ANDROID_FILE_UTIL_GET_DISK_INFO_ERROR;
      invalidStorageInfoErrorId = AndroidErrorId.ANDROID_FILE_UTIL_INVALID_DISK_INFO;
      runCommand = ADB_SHELL_GET_DISK_INFO;
      externalOrInternal = "EXTERNAL";
    } else {
      getStorageInfoErrorId = AndroidErrorId.ANDROID_FILE_UTIL_GET_INTERNAL_STORAGE_INFO_ERROR;
      invalidStorageInfoErrorId = AndroidErrorId.ANDROID_FILE_UTIL_INVALID_INTERNAL_STORAGE_INFO;
      runCommand = ADB_SHELL_GET_INTERNAL_STORAGE;
      externalOrInternal = "INTERNAL";
    }

    String output = null;
    try {
      output = adb.runShell(serial, runCommand);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          getStorageInfoErrorId,
          String.format("Failed to run the command [%s]%n%s", runCommand, e.getMessage()),
          e);
    }
    logger.atInfo().log("Disk information:%n%s", output);
    Long totalSizeBytes = null;
    Long freeSizeBytes = null;
    List<String> items =
        Splitter.onPattern("\\s+").trimResults().omitEmptyStrings().splitToList(output);
    if (items.size() > 1 && STRING_FILESYSTEM.equals(items.get(0))) {
      try {
        if (items.size() == 10) {
          // The output is like before N:
          //   Filesystem               Size     Used     Free   Blksize
          //   /storage/emulated/legacy 2.9G   848.2M     2.1G   4096
          totalSizeBytes = StrUtil.parseHumanReadableSize(items.get(6));
          freeSizeBytes = StrUtil.parseHumanReadableSize(items.get(8));
        } else if (items.size() == 13) {
          // The output on N devices is like:
          //    Filesystem     1K-blocks   Used Available Use% Mounted on
          //    /dev/fuse       26225216 122120  26103096   1% /storage/emulated
          totalSizeBytes = StrUtil.parseHumanReadableSize(items.get(8) + "K");
          freeSizeBytes = StrUtil.parseHumanReadableSize(items.get(10) + "K");
        }
      } catch (NumberFormatException e) {
        throw new MobileHarnessException(
            invalidStorageInfoErrorId,
            externalOrInternal + "storage information format error: " + output,
            e);
      }
    }
    if (totalSizeBytes == null || freeSizeBytes == null) {
      throw new MobileHarnessException(
          invalidStorageInfoErrorId,
          "No valid " + externalOrInternal + " storage information is found: \n" + output);
    } else if (totalSizeBytes < 0) {
      throw new MobileHarnessException(
          invalidStorageInfoErrorId,
          "Total" + externalOrInternal + "storage space " + totalSizeBytes + " is negative");
    }
    return StorageInfo.create(totalSizeBytes, freeSizeBytes);
  }

  /**
   * Returns true only if it's a directory (not file) and exists on device.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param dirPath dir path on device
   * @throws MobileHarnessException if failed to run the command.
   * @throws InterruptedException if the thread executing the commands is interrupted.
   */
  public boolean isDirExist(String serial, int sdkVersion, String dirPath)
      throws MobileHarnessException, InterruptedException {
    Optional<FileInfo> fileInfo =
        getFileInfo(serial, sdkVersion, dirPath, /* followSymlink= */ true);
    if (fileInfo.isEmpty()) {
      // Try the best to decide if target dir exists
      return isFileOrDirExisted(serial, dirPath);
    }
    return FileType.DIR.equals(fileInfo.get().type().orElse(null))
        || (FileType.SYMLINK.equals(fileInfo.get().type().orElse(null))
            && FileType.DIR.equals(fileInfo.get().symlinkType().orElse(null)));
  }

  /**
   * Returns true only if it's a file (not directory) and exists on device.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param filePath file path on device
   * @throws MobileHarnessException if failed to run the command.
   * @throws InterruptedException if the thread executing the commands is interrupted.
   */
  public boolean isFileExist(String serial, int sdkVersion, String filePath)
      throws MobileHarnessException, InterruptedException {
    Optional<FileInfo> fileInfo =
        getFileInfo(serial, sdkVersion, filePath, /* followSymlink= */ true);
    if (fileInfo.isEmpty()) {
      // Try the best to decide if target file exists
      return isFileOrDirExisted(serial, filePath);
    }
    return FileType.FILE.equals(fileInfo.get().type().orElse(null))
        || (FileType.SYMLINK.equals(fileInfo.get().type().orElse(null))
            && FileType.FILE.equals(fileInfo.get().symlinkType().orElse(null)));
  }

  /**
   * Checks whether the given file/dir exits on the device.
   *
   * @return true only if the file/dir exists
   */
  @CanIgnoreReturnValue
  public boolean isFileOrDirExisted(String serial, String fileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    try {
      String output = listFiles(serial, fileOrDirPath, /* showDetails= */ false);
      return !output.contains(OUTPUT_NO_FILE_OR_DIR);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_CHECK_FILE_OR_DIR_EXIST_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Lists file or directory in device, use a long listing format.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * <p>The returned string is like:
   *
   * <pre>
   * drwxrwxrwx root     root              2015-08-10 02:05 file1
   * -rw-rw-rw- root     root         8081 2015-07-23 03:22 InjectAgent.jar
   * -rw-r--r-- root     root        33889 2015-08-04 08:27 c.mp4
   * -rw-rw-rw- root     root     322303670 2015-07-21 10:50 d1
   * -rw-rw-rw- root     root      2204271 2015-08-06 07:14 network.pcap
   * -rwxrwxrwx root     root        17868 2015-07-22 11:52 screenrecord
   * -rwxrwxrwx root     root       652964 2015-08-06 07:13 tcpdump
   * -rw-r--r-- root     root     34544557 2015-07-23 04:41 v.mp4</pre>
   *
   * <p>If no file or directory is found, the returned string contains {@link
   * #OUTPUT_NO_FILE_OR_DIR}.
   *
   * @param serial serial number of the device
   * @param fileOrDirPath path of the file or directory to be listed
   */
  public String listFiles(String serial, String fileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    return listFiles(serial, fileOrDirPath, /* showDetails= */ true);
  }

  /**
   * Lists file or directory in device.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * <p>The result will have different kinds of results on the different systems/API level.
   *
   * @param serial serial number of the device
   * @param fileOrDirPath path of the file or directory to be listed
   * @param showDetails whether using a long listing format.
   * @param extraArgs extra arguments to "ls" command
   */
  public String listFiles(
      String serial, String fileOrDirPath, boolean showDetails, String... extraArgs)
      throws MobileHarnessException, InterruptedException {
    List<String> cmd = new ArrayList<>();
    cmd.add(ADB_SHELL_LIST_FILES);
    cmd.add(showDetails ? "-l" : null);
    Collections.addAll(cmd, extraArgs);
    cmd.add(fileOrDirPath);
    try {
      return adb.runShellWithRetry(serial, Joiner.on(' ').skipNulls().join(cmd));
    } catch (MobileHarnessException e) {
      if (e.getMessage().contains(OUTPUT_NO_FILE_OR_DIR)) {
        return fileOrDirPath + ": " + OUTPUT_NO_FILE_OR_DIR;
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_LIST_FILE_ERROR,
          String.format(
              "Failed to list file or dir %s from device %s%n%s",
              fileOrDirPath, serial, e.getMessage()),
          e);
    }
  }

  /**
   * List all files and dirs under the {@code fileOrDirPath} on the device in alphabetic order.
   *
   * <p>If the path is file, will return the path of the file if exists. If the path is dir, will
   * return contents under the directory if exists. Return empty set if the file or dir not exists.
   *
   * @param serial serial number of the device
   * @param fileOrDirPath path of the file or directory to be listed
   * @return ordered set of files and dirs.
   */
  public SortedSet<String> listFilesInOrder(String serial, String fileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    String output = listFiles(serial, fileOrDirPath, /* showDetails= */ false);
    if (output.contains(OUTPUT_NO_FILE_OR_DIR)) {
      return new TreeSet<>();
    }
    return Splitters.LINE_SPLITTER
        .trimResults()
        .omitEmptyStrings()
        .splitToStream(output)
        .collect(toCollection(TreeSet::new));
  }

  /**
   * Makes a directory in device. Creates parent directories as needed.
   *
   * @param serial serial number of the device
   * @param dirPathOnDevice path of the directory to be created
   */
  public void makeDirectory(String serial, String dirPathOnDevice)
      throws MobileHarnessException, InterruptedException {
    String info = "";
    Exception exception = null;
    try {
      info =
          adb.runShellWithRetry(
              serial, String.format(ADB_SHELL_TEMPLATE_MAKE_DIRECTORY, dirPathOnDevice));
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (!info.isEmpty() || exception != null) {
      if ((!info.isEmpty() && info.contains(OUTPUT_NO_SPACE))
          || (exception != null && exception.getMessage().contains(OUTPUT_NO_SPACE))) {
        throw new MobileHarnessException(
            DeviceUtil.inSharedLab()
                ? AndroidErrorId.ANDROID_FILE_UTIL_NO_SPACE_TO_MAKE_DIRECTORY_ERROR_IN_SHARED_LAB
                : AndroidErrorId
                    .ANDROID_FILE_UTIL_NO_SPACE_TO_MAKE_DIRECTORY_ERROR_IN_SATELLITE_LAB,
            String.format(
                "Please clean the device to make space for directory %s on device %s : %s",
                dirPathOnDevice, serial, (exception == null ? info : exception.getMessage())),
            exception);
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_MAKE_DIRECTORY_ERROR,
          String.format(
              "Failed to make directory %s on device %s: %s",
              dirPathOnDevice, serial, (exception == null ? info : exception.getMessage())),
          exception);
    }
  }

  /**
   * Makes a file executable in device.
   *
   * <p>Note: it cannot make files lived in external storage executable which is limited by Android
   * System.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial the serial number of the device
   * @param binFilePath the filepath of the binary file
   * @return std/err output
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  @CanIgnoreReturnValue
  public String makeFileExecutable(String serial, String binFilePath)
      throws MobileHarnessException, InterruptedException {
    try {
      return adb.runShellWithRetry(
          serial, String.format(ADB_SHELL_MAKE_FILE_EXECUTABLE, binFilePath));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_MAKE_FILE_EXECUTABLE_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Gets md5 of file {@code path} in device whose serial is {@code serial}. Requires API >= 16.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param path path in device
   * @return a 32 hexadecimal digit string. If file doesn't exist, return an empty string.
   */
  public String md5(String serial, int sdkVersion, String path)
      throws MobileHarnessException, InterruptedException {
    if (sdkVersion < AndroidVersion.JELLY_BEAN.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_SDK_VERSION_NOT_SUPPORT, "Doesn't support md5");
    }
    String md5Cmd =
        sdkVersion <= AndroidVersion.LOLLIPOP.getEndSdkVersion() ? ADB_SHELL_MD5 : ADB_SHELL_MD5SUM;
    String output = "";
    String shellCmd = String.format("%s %s", md5Cmd, path);
    try {
      output = adb.runShellWithRetry(serial, shellCmd).trim();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_GET_FILE_MD5_ERROR, e.getMessage(), e);
    }
    Matcher matcher = Pattern.compile("^([0-9a-fA-F]{32}) .*").matcher(output);
    if (!matcher.find()) {
      if (output.contains(OUTPUT_NO_FILE_OR_DIR)) {
        return "";
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_INVALID_MD5_OUTPUT,
          String.format("Command [%s] output is unexpected: %s", shellCmd, output));
    }
    return matcher.group(1);
  }

  /**
   * Pulls the file or the folder from the device to lab server host machine.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of the device
   * @param srcFileOrDirOnDevice source file path on the device, could be a file or directory
   * @param desFileOrDirOnHost destination file path on lab server host machine
   * @return log message
   * @throws MobileHarnessException
   * @throws InterruptedException
   */
  @CanIgnoreReturnValue
  public String pull(String serial, String srcFileOrDirOnDevice, String desFileOrDirOnHost)
      throws MobileHarnessException, InterruptedException {
    try {
      return adb.runWithRetry(
          serial, new String[] {ADB_ARG_PULL, srcFileOrDirOnDevice, desFileOrDirOnHost});
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_PULL_FILE_ERROR,
          String.format(
              "Failed to pull file %s from device %s%n%s",
              srcFileOrDirOnDevice, serial, e.getMessage()),
          e);
    }
  }

  /**
   * Pushes the file or the folder from lab server host machine to device.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param srcFileOrDirOnHost source file path on lab server host machine, could be a file or
   *     directory
   * @param desFileOrDirOnDevice destination file path on the device
   * @return log message
   */
  @CanIgnoreReturnValue
  public String push(
      String serial, int sdkVersion, String srcFileOrDirOnHost, String desFileOrDirOnDevice)
      throws MobileHarnessException, InterruptedException {
    return push(
        serial, sdkVersion, srcFileOrDirOnHost, desFileOrDirOnDevice, /* pushTimeout= */ null);
  }

  /**
   * Pushes the file or the folder from lab server host machine to device.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param srcFileOrDirOnHost source file path on lab server host machine, could be a file or
   *     directory
   * @param desFileOrDirOnDevice destination file path on the device
   * @param pushTimeout timeout for the execution of push
   * @return log message
   * @throws MobileHarnessException if failed to run the command, or failed to get destination dir
   *     path to source dir.
   * @throws InterruptedException if the thread executing the commands is interrupted.
   */
  @CanIgnoreReturnValue
  public String push(
      String serial,
      int sdkVersion,
      String srcFileOrDirOnHost,
      String desFileOrDirOnDevice,
      @Nullable Duration pushTimeout)
      throws MobileHarnessException, InterruptedException {
    if (!localFileUtil.isDirExist(srcFileOrDirOnHost)) {
      try {
        return adb.runWithRetry(
            serial,
            new String[] {ADB_ARG_PUSH, srcFileOrDirOnHost, desFileOrDirOnDevice},
            pushTimeout);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_FILE_UTIL_PUSH_FILE_ADB_ERROR,
            String.format(
                "Failed to push file %s to device %s:%s%n%s",
                srcFileOrDirOnHost, serial, desFileOrDirOnDevice, e.getMessage()),
            e);
      }
    }

    String desDirPathOnDevice =
        getPushedFileOrDirFinalDestinationPathOnDevice(
            serial, sdkVersion, srcFileOrDirOnHost, desFileOrDirOnDevice);
    if (desDirPathOnDevice.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_ILLEGAL_ARGUMENT,
          String.format(
              "Cannot get destination dir path to source dir on device when try to push dir "
                  + "%s to device %s: %s is not a valid dir path on device",
              srcFileOrDirOnHost, serial, desFileOrDirOnDevice));
    }

    /*
     * Device folder may be mounted for different filesystem, which may or may not support
     * symbolic link. Because of that, ADB push will also behave differently when
     * pushing symbolic links.
     *
     * So we always assume there are symbolic links in the source folder. Then we just walk through
     * the dir and push every files directly.
     *
     * This may also cover other cases that contains similar error message and can be solved by
     * the same walk-around.
     */
    StringBuilder log =
        new StringBuilder(
            "Source path is a directory and may contain symbolic links. "
                + "Need to walk through the dir and push every files");
    List<String> srcFilePathList;
    try {
      srcFilePathList = localFileUtil.listFilePaths(srcFileOrDirOnHost, true);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_PUSH_FILE_LOCAL_FILE_ERROR,
          String.format(
              "Failed to list file %s on local host machine.%n%s",
              srcFileOrDirOnHost, e.getMessage()),
          e);
    }

    for (String srcFilePath : srcFilePathList) {
      // Only get the file name into relative path.
      String relativePath = PathUtil.makeRelative(srcFileOrDirOnHost, srcFilePath);
      String desFilePath = PathUtil.join(desDirPathOnDevice, relativePath);
      try {
        log.append("\nPush: ")
            .append(relativePath)
            .append(" -> ")
            .append(desFilePath)
            .append(": ")
            .append(
                adb.runWithRetry(
                    serial, new String[] {ADB_ARG_PUSH, srcFilePath, desFilePath}, pushTimeout));
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_FILE_UTIL_PUSH_FILE_ADB_ERROR,
            String.format(
                "Failed to push file %s to device %s:%s%n%s",
                srcFilePath, serial, desFilePath, e.getMessage()),
            e);
      }
    }
    return log.toString();
  }

  /**
   * Remounts the /system and /vendor (if present) partitions on the device read-write. This only
   * works if the device has root access and has already become root, and it affects all users.
   *
   * @param serial serial number of the device
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void remount(String serial) throws MobileHarnessException, InterruptedException {
    // Due to legacy issues, the results are not checked by default.
    remount(serial, /* checkResults= */ false);
  }

  /**
   * Remounts the /system and /vendor (if present) partitions on the device read-write. This only
   * works if the device has root access and has already become root, and it affects all users.
   *
   * @param serial serial number of the device
   * @param checkResults if check the output for success
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void remount(String serial, boolean checkResults)
      throws MobileHarnessException, InterruptedException {
    String output = "";
    try {
      if (androidSystemSpecUtil.isEmulator(serial)) {
        int sdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(serial);
        String result = "";
        if (sdkVersion >= AndroidVersion.ANDROID_13.getStartSdkVersion()) {
          result = adb.run(serial, new String[] {"shell", "remount"});
        } else if (sdkVersion >= AndroidVersion.ANDROID_11.getStartSdkVersion()) {
          result = adb.run(serial, new String[] {"shell", "mount", "-o", "rw,remount", "/"});
        }
        if (result.contains(ADB_REMOUNT_SUCCESS_INDICATOR)) {
          return;
        }
      }
      output = adb.run(serial, new String[] {ADB_ARG_REMOUNT});
      if (checkResults && !output.contains(ADB_REMOUNT_SUCCESS_INDICATOR)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_FILE_UTIL_REMOUNT_ERROR,
            "The output of adb remount doesn't indicate the success: " + output);
      }
    } catch (MobileHarnessException e) {
      // b/296730927, sometimes the exit code is 11 instead of 0, which is also an indication
      // of success run.
      if (e.getErrorId().equals(AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE)
          && e.getMessage().contains(ADB_REMOUNT_REBOOT_INDICATOR)
          && e.getMessage().contains(ADB_REMOUNT_EXIT_CODE_INDICATOR)) {
        logger.atWarning().log(
            "Needs to reboot device %s to make remount effective because [%s].",
            serial, MoreThrowables.shortDebugString(e, 0));
      } else {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_FILE_UTIL_REMOUNT_ERROR, e.getMessage(), e);
      }
    }
  }

  /**
   * Deletes files or directories by name pattern in device.
   *
   * @param serial serial number of the device
   * @param fileOrDirPathPattern path pattern of the files or directories to be deleted
   */
  public void removeFiles(String serial, String fileOrDirPathPattern)
      throws MobileHarnessException, InterruptedException {
    removeFiles(serial, fileOrDirPathPattern, DEFAULT_REMOVE_FILES_TIMEOUT);
  }

  /**
   * Deletes files or directories by name pattern in device.
   *
   * @param serial serial number of the device
   * @param fileOrDirPathPattern path pattern of the files or directories to be deleted
   * @param timeout max execution time of each attempt
   */
  public void removeFiles(String serial, String fileOrDirPathPattern, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    String output = null;
    Exception exception = null;

    if (fileOrDirPathPattern.indexOf('*') >= 0 && fileOrDirPathPattern.indexOf('(') == -1) {
      logger.atWarning().log(
          "Skip to use shell escape %s , because disable globbing.", fileOrDirPathPattern);
    } else {
      fileOrDirPathPattern = ShellUtils.shellEscape(fileOrDirPathPattern);
    }

    String command = String.format(ADB_SHELL_TEMPLATE_REMOVE_FILES_PATTERN, fileOrDirPathPattern);
    try {
      output = adb.runShellWithRetry(serial, command, timeout).trim();
    } catch (MobileHarnessException e) {
      String errorMsg = e.getMessage();
      if (errorMsg.contains(OUTPUT_NO_FILE_OR_DIR)) {
        return;
      } else if (errorMsg.contains(INVALID_ARGUMENT_REMOVE_FILES)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_FILE_UTIL_REMOVE_FILE_INVALID_ARGUMENT, errorMsg, e);
      } else {
        exception = e;
      }
    }

    if (output != null && (output.isEmpty() || output.contains(OUTPUT_NO_FILE_OR_DIR))) {
      return;
    } else {
      logger.atWarning().log(
          "Remove files serial=%s, command=%s, timeout=%s", serial, command, timeout);
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_REMOVE_FILE_ERROR,
          exception == null ? output : exception.getMessage(),
          exception);
    }
  }

  /**
   * Renames file or directory in device.
   *
   * @param serial serial number of the device
   */
  public void renameFiles(String serial, String srcFileOrDirPath, String desFileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    String info = "";
    Exception exception = null;

    try {
      info =
          adb.runShellWithRetry(
                  serial, ADB_SHELL_RENAME_FILES + " " + srcFileOrDirPath + " " + desFileOrDirPath)
              .trim();
    } catch (MobileHarnessException e) {
      exception = e;
    }

    if (!info.isEmpty() || exception != null) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_RENAME_FILE_ERROR,
          exception == null ? info : exception.getMessage(),
          exception);
    }
  }

  /**
   * Creates symlink.
   *
   * @param serial serial number of the device
   * @param srcFileOrDirPath source path of symlink to be created.
   * @param desFileOrDirPath destination path of symlink to be created.
   */
  public void createSymlink(String serial, String srcFileOrDirPath, String desFileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    String info = "";
    String command =
        String.format(ADB_SHELL_TEMPLATE_CREATE_SYMLINK, srcFileOrDirPath, desFileOrDirPath);

    try {
      info = adb.runShellWithRetry(serial, command).trim();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_UTIL_CREATE_SYMLINK_ERROR, e.getMessage(), e);
    }

    if (!info.isEmpty()) {
      throw new MobileHarnessException(AndroidErrorId.ANDROID_FILE_UTIL_CREATE_SYMLINK_ERROR, info);
    }
  }

  /**
   * Gets the symlink pointed file/dir's type. Returns empty Optional if symlink points to
   * non-existent file/dir.
   */
  private Optional<FileType> getSymlinkFileOrDirType(
      String serial, int sdkVersion, String targetPath) throws InterruptedException {
    // Append '/' to ensure it lists children of targetParentDirPath, required if
    // targetParentDirPath is a symlink
    String targetDirName = PathUtil.dirname(targetPath);
    String targetParentDirPath = "/".equals(targetDirName) ? "/" : targetDirName + "/";
    String targetBasename = PathUtil.basename(targetPath);
    String output;
    String followSymlinkCmd;
    Pattern listResultPattern;
    StringBuilder lsFollowSymlinkOutput = new StringBuilder();
    if (sdkVersion >= AndroidVersion.NOUGAT.getStartSdkVersion()) {
      followSymlinkCmd = "ls -l -L " + targetParentDirPath;
      listResultPattern = ADB_SHELL_LIST_DETAILS_PATTERN;
    } else {
      // For API < 24, "ls" command doesn't support "-L" option
      followSymlinkCmd = "ls -F " + targetParentDirPath;
      listResultPattern = ADB_SHELL_LIST_WITH_OPTION_F_PATTERN;
    }
    // Dereference symbolic link to show information for the linked file/dir
    try {
      output =
          adb.runShell(
              serial,
              followSymlinkCmd,
              DEFAULT_LS_COMMAND_TIMEOUT,
              LineCallback.does(line -> lsFollowSymlinkOutput.append(line).append("\n")));
    } catch (MobileHarnessException e) {
      output = lsFollowSymlinkOutput.toString();
    }
    Optional<String> matchTargetLine =
        filterMatchTargetLineInListOutput(
            output, targetBasename, listResultPattern, "fileOrDirName");

    if (matchTargetLine.isEmpty()) {
      logger.atInfo().log(
          "Symbolic link %s points to a non-existent file/dir%n%s", targetPath, output);
      return Optional.empty();
    }

    if (sdkVersion >= AndroidVersion.NOUGAT.getStartSdkVersion()) {
      char fileOrDirIndicator = matchTargetLine.get().charAt(0);
      if (fileOrDirIndicator == ADB_SHELL_LIST_FILE_INDICATOR) {
        return Optional.of(FileType.FILE);
      } else if (fileOrDirIndicator == ADB_SHELL_LIST_DIR_INDICATOR) {
        return Optional.of(FileType.DIR);
      } else {
        logger.atInfo().log("Type for symbolic link %s is unknown:%n%s", targetPath, output);
        return Optional.of(FileType.UNKNOWN);
      }
    }

    String fileOrDirIndicator = matchTargetLine.get().substring(0, 2);
    if ((String.valueOf(ADB_SHELL_LIST_SYMLINK_INDICATOR) + ADB_SHELL_LIST_FILE_INDICATOR)
        .equals(fileOrDirIndicator)) {
      return Optional.of(FileType.FILE);
    } else if ((String.valueOf(ADB_SHELL_LIST_SYMLINK_INDICATOR) + ADB_SHELL_LIST_DIR_INDICATOR)
        .equals(fileOrDirIndicator)) {
      return Optional.of(FileType.DIR);
    } else if ((ADB_SHELL_LIST_SYMLINK_INDICATOR + "?").equals(fileOrDirIndicator)) {
      // The final target for symlink doesn't exist
      logger.atInfo().log(
          "Symbolic link %s points to a non-existent file/dir%n%s", targetPath, output);
      return Optional.empty();
    } else {
      logger.atInfo().log("Type for symbolic link %s is unknown:%n%s", targetPath, output);
      return Optional.of(FileType.UNKNOWN);
    }
  }

  /**
   * Filters out the file/dir info line in "adb shell ls" output for {@code fileOrDirName}. For
   * symbolic link target, the ls out must have been dereferenced with ls option "-L" or "-F".
   */
  private static Optional<String> filterMatchTargetLineInListOutput(
      String lsOutput, String fileOrDirName, Pattern pattern, String fileOrDirGroupNameInPattern) {
    Optional<String> matchTargetLine =
        Splitters.LINE_SPLITTER.trimResults().omitEmptyStrings().splitToList(lsOutput).stream()
            .filter(
                line -> {
                  Matcher matcher = pattern.matcher(line);
                  if (!matcher.find()) {
                    return false;
                  }
                  String fileOrDirNameGroup = matcher.group(fileOrDirGroupNameInPattern);
                  return fileOrDirName.equals(fileOrDirNameGroup);
                })
            .findFirst();
    return matchTargetLine;
  }
}
