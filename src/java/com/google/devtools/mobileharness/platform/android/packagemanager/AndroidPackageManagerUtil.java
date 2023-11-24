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

package com.google.devtools.mobileharness.platform.android.packagemanager;

import static com.google.devtools.mobileharness.shared.util.command.LineCallback.does;
import static java.util.stream.Collectors.toCollection;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.shared.AdbOutputParsingUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidService;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidSettings;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.shared.constant.PackageConstants;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.util.ArrayUtil;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Utility class to manage APKs and installed packages on Android devices/emulators.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 *
 * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
 * separator on SDK>23. It's callers' responsibility to parse it correctly.
 */
public class AndroidPackageManagerUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** ADB arg of uninstalation. Should be followed with a package name. */
  @VisibleForTesting static final String ADB_ARG_UNINSTALL = "uninstall";

  /** ADB args for installing a APK. Should be followed by the path of the APK. */
  @VisibleForTesting static final String[] ADB_ARGS_INSTALL = new String[] {"install", "-r", "-t"};

  /**
   * ADB args for installing an app with multiple files (APK or Dex Metadata files). Should be
   * followed by the space-delimited paths of multiple files.
   */
  @VisibleForTesting
  static final String[] ADB_ARGS_INSTALL_MULTIPLE = new String[] {"install-multiple", "-r"};

  /**
   * split_1.apk:split_2.apk:...:split_n.apk. All apks in each item are the split apks belong to a
   * single package.
   */
  @VisibleForTesting static final String ADB_ARG_INSTALL_MULTI_PACKAGE = "install-multi-package";

  /** ADB shell command for cleaning a package. Should be followed by the package name. */
  @VisibleForTesting static final String ADB_SHELL_CLEAR_PACKAGE = "pm clear";

  /** ADB shell command for uninstalling a package. Should be followed by the package name. */
  @VisibleForTesting static final String ADB_SHELL_UNINSTALL_PACKAGE = "pm uninstall";

  @VisibleForTesting static final String ADB_SHELL_DISABLE_APP = "pm disable";

  /** ADB shell command to get package list generated by Android package manager. */
  @VisibleForTesting
  public static final String ADB_SHELL_GET_PACKAGE_LIST = "cat /data/system/packages.list";

  /** ADB shell command for listing packages installed. Could be followed with the filter option. */
  @VisibleForTesting static final String ADB_SHELL_LIST_PACKAGES = "pm list packages";

  /** ADB shell for get path of an installed apk. Should be followed by the apk package name. */
  @VisibleForTesting static final String ADB_SHELL_PM_PATH = "pm path";

  /** ADB shell command for getting package verifier option. */
  @VisibleForTesting
  static final String ADB_SHELL_SETTINGS_PACKAGE_VERIFIER_INCLUDE_ADB =
      "verifier_verify_adb_installs";

  /** ADB shell template to grant permission. Should be filled by the package name & permission. */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_GRANT_PERMISSION = "pm grant";

  /** Suffix of apex files. */
  private static final String APEX_SUFFIX = ".apex";

  /** Timeout of cleaning package. */
  @VisibleForTesting static final Duration CLEAR_PACKAGE_TIMEOUT = Duration.ofSeconds(30);

  /** Default timeout in milliseconds for installation/uninstallation. */
  @VisibleForTesting
  static final Duration DEFAULT_INSTALL_TIMEOUT = Constants.DEFAULT_INSTALL_TIMEOUT;

  /** By default, multi-user is supported from API 17. */
  @VisibleForTesting
  static final int DEFAULT_MULTI_USER_START_SDK_VERSION =
      AndroidVersion.JELLY_BEAN.getStartSdkVersion() + 1;

  /** By default, Dex Metadata installation is supported from API 28. */
  @VisibleForTesting
  static final int DEFAULT_INSTALL_DEX_METADATA_START_SDK_VERSION =
      AndroidVersion.PI.getEndSdkVersion();

  /** By default, install-multi-package is supported from API 29. */
  @VisibleForTesting
  static final int DEFAULT_INSTALL_MULTI_PACKAGE_START_SDK_VERSION =
      AndroidVersion.PI.getEndSdkVersion() + 1;

  /** Partial output of "adb shell pm disable [app]" when the command succeeds. */
  static final String OUTPUT_DISABLE_APP_SUCCESS = "new state: disabled";

  /** Output with exception. */
  @VisibleForTesting static final String OUTPUT_EXCEPTION = "Exception";

  /** Output when the install app failed by no matching ABIS. */
  public static final String OUTPUT_INSTALL_FAILED_NO_MATCHING_ABIS =
      "INSTALL_FAILED_NO_MATCHING_ABIS";

  /** Output when the install app failed by manifest's missing libraries. */
  public static final String OUTPUT_INSTALL_FAILED_MISSING_SHARED_LIBRARY =
      "INSTALL_FAILED_MISSING_SHARED_LIBRARY";

  /** Output when the install app failed by update incompatible. */
  public static final String OUTPUT_INSTALL_FAILED_UPDATE_INCOMPATIBLE =
      "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE";

  /** Output when the install app failed by UID changed. */
  public static final String OUTPUT_INSTALL_FAILED_UID_CHANGED = "INSTALL_FAILED_UID_CHANGED";

  /** Output template when the install app failed due to no valid UID assigned. */
  public static final String OUTPUT_TEMPLATE_INSTALL_FAILED_UID_INVALID =
      "Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE:"
          + " Scanning Failed.: Package %s could not be assigned a valid UID]";

  /** Output when the install app failed by version downgrade. */
  public static final String OUTPUT_INSTALL_FAILED_VERSION_DOWNGRADE =
      "INSTALL_FAILED_VERSION_DOWNGRADE";

  /** Output when the install app failed by manifest malformed. */
  public static final String OUTPUT_INSTALL_PARSE_FAILED_MANIFEST_MALFORMED =
      "INSTALL_PARSE_FAILED_MANIFEST_MALFORMED";

  /** Output when the install app failed by invalid apk. */
  public static final String OUTPUT_INSTALL_FAILED_INVALID_APK = "INSTALL_FAILED_INVALID_APK";

  /** Output when the install app failed by duplicate permission. */
  public static final String OUTPUT_INSTALL_FAILED_DUPLICATE_PERMISSION =
      "INSTALL_FAILED_DUPLICATE_PERMISSION";

  /** Output when the install app failed by duplicate permission. */
  public static final String OUTPUT_INSTALL_FAILED_INVALID_APK_SPLIT_NULL =
      "INSTALL_FAILED_INVALID_APK: Split null was defined multiple times";

  /** Output when the install app failed by older sdk. */
  public static final String OUTPUT_INSTALL_FAILED_OLDER_SDK = "INSTALL_FAILED_OLDER_SDK";

  /** Prefix out the output lines of the "adb shell pm list package -3" command. */
  @VisibleForTesting static final String OUTPUT_PACKAGE_PREFIX = "package:";

  /** Prefix out the output lines of the "adb shell pm list package -U" command. */
  @VisibleForTesting static final String OUTPUT_PACKAGE_UID_PREFIX = "uid:";

  /** Output message which shows the command is killed. */
  @VisibleForTesting static final String OUTPUT_KILLED = "Killed";

  /** Output of a successful installation/uninstallation. */
  @VisibleForTesting static final String OUTPUT_SUCCESS = "Success";

  /** Output of a failed installation/uninstallation. */
  @VisibleForTesting static final String OUTPUT_FAILURE = "Failure";

  /** Output of when failed to clear package. */
  @VisibleForTesting static final String OUTPUT_FAILED = "Failed";

  /** Short timeout for quick operations. */
  @VisibleForTesting static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);

  private static final String SESSION_CREATION_START = "Created";

  private static final String SHOW_VERSION_CODE_FLAG = "--show-versioncode";

  private static final Pattern LIST_PACKAGE_WITH_SOURCE_DIR_AND_VERSION_REGEX =
      Pattern.compile(
          "package:(?<sourceDir>.*)=(?<pkgName>[^=]*) versionCode:(?<versionCode>\\d+)");
  private static final Pattern LIST_PACKAGE_WITH_VERSION_REGEX =
      Pattern.compile("package:(?<pkgName>.*) versionCode:(?<versionCode>\\d+)");

  private static final String ADB_SHELL_GET_MODULEINFO = "pm get-moduleinfo";

  private static final Pattern MODULEINFO_REGEX =
      Pattern.compile("ModuleInfo\\{[0-9a-fA-F]+ (?<name>.*)\\} packageName: (?<pkgName>.*)");

  /** Android SDK ADB command line tools executor. */
  private final Adb adb;

  /** Android SDK AAPT command line tools executor. */
  private final Aapt aapt;

  private final AndroidAdbUtil adbUtil;

  private final AndroidFileUtil androidFileUtil;

  private final Sleeper sleeper;

  /** An auxiliary class to process lines in stdout. */
  abstract static class LineProcessor {
    private final ArrayList<Boolean> successes = new ArrayList<>();

    /** Returns true if there are results and all results are successes. */
    boolean success() {
      return successes.stream().reduce(Boolean::logicalAnd).orElse(false);
    }

    /** Processes a line. */
    void process(String line) {
      successes.add(processSuccess(line));
    }

    /** Processes a line and returns if it is successful. */
    abstract boolean processSuccess(String line);
  }

  /** Creates a util for Android device operations. */
  public AndroidPackageManagerUtil() {
    this(
        new Adb(),
        new Aapt(),
        new AndroidAdbUtil(),
        new AndroidFileUtil(),
        Sleeper.defaultSleeper());
  }

  public AndroidPackageManagerUtil(
      Adb adb,
      Aapt aapt,
      AndroidAdbUtil adbUtil,
      AndroidFileUtil androidFileUtil,
      Sleeper sleeper) {
    this.adb = adb;
    this.aapt = aapt;
    this.adbUtil = adbUtil;
    this.androidFileUtil = androidFileUtil;
    this.sleeper = sleeper;
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
   * Clears the data of a package installed on device for user USER_SYSTEM.
   *
   * @param serial serial number of the device
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void clearPackage(String serial, String packageName)
      throws MobileHarnessException, InterruptedException {
    clearPackage(UtilArgs.builder().setSerial(serial).build(), packageName);
  }

  /**
   * Clears the data of a package installed on device.
   *
   * @param utilArgs args with serial, sdkVersion and userId
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void clearPackage(UtilArgs utilArgs, String packageName)
      throws MobileHarnessException, InterruptedException {
    isMultiUserSupported(utilArgs, DEFAULT_MULTI_USER_START_SDK_VERSION);

    String output = "";
    Exception exception = null;
    String serial = utilArgs.serial();
    String user = utilArgs.userId().isPresent() ? "--user " + utilArgs.userId().get() : null;
    String command =
        Joiner.on(' ').skipNulls().join(new String[] {ADB_SHELL_CLEAR_PACKAGE, user, packageName});
    try {
      output =
          adb.runShellWithRetry(
              serial,
              command,
              CLEAR_PACKAGE_TIMEOUT,
              LineCallback.stopWhen(
                  line ->
                      // Stops the command when we got enough signal. See b/20650828.
                      line.contains(OUTPUT_SUCCESS) || line.contains(OUTPUT_FAILED)));
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (exception != null || !output.trim().endsWith(OUTPUT_SUCCESS)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_CLEAR_PACKAGE_ERROR,
          String.format(
              "Failed to clear package %s on device %s: %s",
              packageName, serial, (exception == null ? output : exception.getMessage())),
          exception);
    }
  }

  /**
   * Disables an installed app without uninstalling it for user USER_SYSTEM. Disabling an
   * already-disabled app will not cause an error.
   *
   * @param serial serial number of the device
   * @param packageName package name of the apk to disable
   * @throws MobileHarnessException if the command fails or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public void disablePackage(String serial, String packageName)
      throws MobileHarnessException, InterruptedException {
    disablePackage(UtilArgs.builder().setSerial(serial).build(), packageName);
  }

  /**
   * Disables an installed app without uninstalling it. Disabling an already-disabled app will not
   * cause an error.
   *
   * @param utilArgs args for serial, sdkVersion and userId
   * @param packageName package name of the apk to disable
   * @throws MobileHarnessException if the command fails or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public void disablePackage(UtilArgs utilArgs, String packageName)
      throws MobileHarnessException, InterruptedException {
    isMultiUserSupported(utilArgs, DEFAULT_MULTI_USER_START_SDK_VERSION);

    String output = "";
    Exception exception = null;
    String serial = utilArgs.serial();
    String user = utilArgs.userId().isPresent() ? "--user " + utilArgs.userId().get() : null;
    String command =
        Joiner.on(' ').skipNulls().join(new String[] {ADB_SHELL_DISABLE_APP, user, packageName});
    try {
      output = adb.runShellWithRetry(serial, command, SHORT_TIMEOUT);
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (exception != null || !output.contains(OUTPUT_DISABLE_APP_SUCCESS)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_DISABLE_PACKAGE_ERROR,
          String.format(
              "Failed to disable package %s on device %s: %s",
              packageName, serial, (exception == null ? output : exception.getMessage())),
          exception);
    }
  }

  /**
   * Disable package verifier option. Only works with API level >= 17, no effect when API level <
   * 17. Supports production build.
   */
  public void disablePackageVerifier(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    disablePackageVerifier(UtilArgs.builder().setSerial(serial).setSdkVersion(sdkVersion).build());
  }

  /**
   * Disable package verifier option. Only works with API level >= 17, no effect when API level <
   * 17. Supports production build.
   */
  public void disablePackageVerifier(UtilArgs utilArgs)
      throws MobileHarnessException, InterruptedException {
    isMultiUserSupported(utilArgs, DEFAULT_MULTI_USER_START_SDK_VERSION);

    int sdkVersion = utilArgs.sdkVersion().orElse(0);
    try {
      // We only try to take action when API is >=17.
      if (sdkVersion >= 17) {
        AndroidSettings.Spec querySpec =
            AndroidSettings.Spec.create(
                AndroidSettings.Command.GET,
                AndroidSettings.NameSpace.GLOBAL,
                ADB_SHELL_SETTINGS_PACKAGE_VERIFIER_INCLUDE_ADB);
        String value = adbUtil.settings(utilArgs, querySpec);
        if (Objects.equals(value, "1") || Ascii.equalsIgnoreCase("null", value)) {
          AndroidSettings.Spec putSpec =
              AndroidSettings.Spec.create(
                  AndroidSettings.Command.PUT,
                  AndroidSettings.NameSpace.GLOBAL,
                  ADB_SHELL_SETTINGS_PACKAGE_VERIFIER_INCLUDE_ADB + " 0");
          logger.atInfo().log("Disable package verifier option");
          adbUtil.settings(utilArgs, putSpec);
          logger.atInfo().log("Package verifier is disabled");
        } else if (Objects.equals(value, "0")) {
          logger.atInfo().log("Package verifier option disabled, skipped");
        } else {
          logger.atWarning().log("Failed to find package verifier option, aborted");
        }
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to disable package verifier option, aborted.");
    }
  }

  /**
   * Gets the version code of the APEX modules for a rooted device. APEX module is a new Android
   * module introduced by go/mainline-play on Android Q. The method throws MobileHarness Exception
   * if used for builds below "Q".
   *
   * <p>By default it searches all packages across user USER_SYSTEM and find the match package, if
   * not given --user.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param packageName package name of the application
   */
  public int getApexVersionCode(String serial, int sdkVersion, String packageName)
      throws MobileHarnessException, InterruptedException {
    return getApexVersionCode(
        UtilArgs.builder().setSerial(serial).setSdkVersion(sdkVersion).build(), packageName);
  }

  /**
   * Gets the version code of the APEX modules for a rooted device. APEX module is a new Android
   * module introduced by go/mainline-play on Android Q. The method throws MobileHarness Exception
   * if used for builds below "Q".
   *
   * <p>By default it searches all packages across user USER_SYSTEM and find the match package, if
   * not given --user.
   *
   * @param utilArgs args for serial, sdkVersion and userId
   * @param packageName package name of the application
   */
  public int getApexVersionCode(UtilArgs utilArgs, String packageName)
      throws MobileHarnessException, InterruptedException {
    isMultiUserSupported(utilArgs, DEFAULT_MULTI_USER_START_SDK_VERSION);

    String serial = utilArgs.serial();
    int sdkVersion = utilArgs.sdkVersion().orElse(0);

    if (!isQtOrAboveBuild(serial, sdkVersion)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_SDK_VERSION_NOT_SUPPORT,
          String.format(
              "Device %s is not compatible with the query for APEX modules. Required Q+ build.",
              serial));
    }

    String[] command = new String[] {"list", "packages", "--apex-only", "--show-versioncode"};
    if (utilArgs.userId().isPresent()) {
      command = ArrayUtil.join(command, "--user", utilArgs.userId().get());
    }
    command = ArrayUtil.join(command, packageName);
    String output = "";
    try {
      output = adbUtil.cmd(serial, AndroidService.PACKAGE, command);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_APEX_MODULE_VERSION_CODE_ERROR,
          e.getMessage(),
          e);
    }
    Matcher matcher = Pattern.compile(" versionCode:(\\d+)").matcher(output);
    if (matcher.find() && !output.contains("Exception")) {
      String code = matcher.group(1);
      try {
        return Integer.parseInt(code);
      } catch (NumberFormatException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INVALID_APEX_VERSION_CODE,
            "Failed to parse the version code ["
                + code
                + "] of package "
                + packageName
                + ": "
                + e.getMessage(),
            e);
      }
    } else {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_MISSING_APEX_VERSION_CODE,
          String.format(
              "Can not find the version info of package %s. show-versioncode output: %n%s",
              packageName, output));
    }
  }

  /**
   * Gets the ABI of the given APK file.
   *
   * @param apkPath path of the apk package
   * @return the ABI of the given APK file
   */
  public String getApkAbi(String apkPath) throws MobileHarnessException, InterruptedException {
    try {
      return aapt.getApkAbi(apkPath);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_APK_ABI_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Gets the min SDK version of the given apk.
   *
   * @return the min SDK version, or 0 if not set, or -1 if failed to parse
   */
  public int getApkMinSdkVersion(String apkPath)
      throws MobileHarnessException, InterruptedException {
    try {
      return aapt.getApkMinSdkVersion(apkPath);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_APK_MIN_SDK_VERSION_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Gets the package name of the given APK file.
   *
   * @param apkPath path of the apk package
   * @return the package name of the given APK file
   */
  public String getApkPackageName(String apkPath)
      throws MobileHarnessException, InterruptedException {
    try {
      return aapt.getApkPackageName(apkPath);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_APK_PACKAGE_NAME_ERROR, e.getMessage(), e);
    }
  }

  /** Gets the version code of the given apk. */
  public int getApkVersionCode(String apkPath) throws MobileHarnessException, InterruptedException {
    try {
      return aapt.getApkVersionCode(apkPath);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_APK_VERSION_CODE_ERROR, e.getMessage(), e);
    }
  }

  /** Gets the version name of the given apk. */
  public String getApkVersionName(String apkPath)
      throws MobileHarnessException, InterruptedException {
    try {
      return aapt.getApkVersionName(apkPath);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_APK_VERSION_NAME_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Gets the version code of the application. Notes the application should have been installed in
   * the device before using this method.
   *
   * @param packageName package name of the application
   */
  public int getAppVersionCode(String serial, String packageName)
      throws MobileHarnessException, InterruptedException {
    String output = "";
    try {
      output = adbUtil.dumpSys(serial, DumpSysType.PACKAGE, packageName);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_DUMPSYS_ERROR, e.getMessage(), e);
    }
    Matcher matcher = Pattern.compile(" versionCode=(\\d+)").matcher(output);
    if (matcher.find() && !output.contains(OUTPUT_EXCEPTION)) {
      String code = matcher.group(1);
      try {
        return Integer.parseInt(code);
      } catch (NumberFormatException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INVALID_VERSION,
            "Failed to parse the version code ["
                + code
                + "] of package "
                + packageName
                + ": "
                + e.getMessage());
      }
    } else {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_VERSION_INFO_ERROR,
          String.format(
              "Can not find the version info of package %s. dumpsys package output: %n%s",
              packageName, output));
    }
  }

  /**
   * Gets the version name of the application. Notes the application should have been installed in
   * the device before using this method.
   *
   * @param packageName package name of the application
   */
  public String getAppVersionName(String serial, String packageName)
      throws MobileHarnessException, InterruptedException {
    String output = "";
    try {
      output = adbUtil.dumpSys(serial, DumpSysType.PACKAGE, packageName);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_DUMPSYS_ERROR, e.getMessage(), e);
    }
    Matcher matcher = Pattern.compile(" versionName=(.+)").matcher(output);
    boolean foundVersionName = matcher.find();
    if (foundVersionName && !output.contains(OUTPUT_EXCEPTION)) {
      return matcher.group(1).trim();
    } else {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_VERSION_INFO_ERROR,
          foundVersionName
              ? "Got " + OUTPUT_EXCEPTION + " in the version info of package " + packageName
              : "Can not find the version info of package " + packageName);
    }
  }

  /**
   * Gets installed path of apk package {@code apkPackageName} for user USER_SYSTEM in device whose
   * serial is {@code serial}.
   *
   * @param serial serial number of the device
   * @param apkPackage Package of apk
   * @return Installed path
   * @throws MobileHarnessException if failed to find {@code apkPackage}
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String getInstalledPath(String serial, String apkPackage)
      throws MobileHarnessException, InterruptedException {
    return getInstalledPath(UtilArgs.builder().setSerial(serial).build(), apkPackage);
  }

  /**
   * Gets installed path of apk package {@code apkPackageName} in device whose serial is {@code
   * serial}.
   *
   * @param utilArgs args for serial, sdkVersion and userId
   * @param apkPackage Package of apk
   * @return Installed path
   * @throws MobileHarnessException if failed to find {@code apkPackage}
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String getInstalledPath(UtilArgs utilArgs, String apkPackage)
      throws MobileHarnessException, InterruptedException {
    return getAllInstalledPaths(utilArgs, apkPackage).get(0);
  }

  /**
   * Gets all installed paths of apk package {@code apkPackageName} in device whose serial is {@code
   * serial}.
   *
   * <pre>
   * Some packages may have multiple installed paths in device, like:
   *
   * package:/data/app/com.google.android.gms-ieHLmtRchNhuQuB6z1tGEA==/base.apk
   * package:/data/app/com.google.android.gms-ieHLmtRchNhuQuB6z1tGEA==/split_config.en.apk</pre>
   *
   * @param utilArgs args for serial, sdkVersion and userId
   * @param apkPackage Package of apk
   * @return list of installed paths
   * @throws MobileHarnessException if failed to find {@code apkPackage}
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public ImmutableList<String> getAllInstalledPaths(UtilArgs utilArgs, String apkPackage)
      throws MobileHarnessException, InterruptedException {
    // Although "pm path" support "--user" starting from API 18 but it does not work correctly.
    // Starting from API 24, it got fixed.
    isMultiUserSupported(utilArgs, AndroidVersion.NOUGAT.getStartSdkVersion());

    String serial = utilArgs.serial();
    String user = utilArgs.userId().isPresent() ? "--user " + utilArgs.userId().get() : null;
    String command =
        Joiner.on(' ').skipNulls().join(new String[] {ADB_SHELL_PM_PATH, user, apkPackage});
    StringBuilder outputLines = new StringBuilder();
    try {
      adb.runShell(
          serial,
          command,
          /* timeout= */ null,
          LineCallback.does(line -> outputLines.append(line).append("\n")));
    } catch (MobileHarnessException e) {
      // When get installed path for an unknown package on SDK>=24, command fails and output is
      // empty. But we should only consider it as command failure when command output is not empty.
      if (!outputLines.toString().trim().isEmpty()) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_INSTALLED_APK_PATH_ERROR, e.getMessage(), e);
      }
    }
    String output = outputLines.toString().trim();
    Matcher matcher = Pattern.compile("package:(.*)").matcher(output);
    List<String> installedPaths = new ArrayList<>();
    while (matcher.find()) {
      installedPaths.add(matcher.group(1));
    }
    if (installedPaths.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_PM_PATH_NO_PACKAGE_FOUND,
          output.isEmpty()
              ? String.format(
                  "Can't find package %s in the presumed install path on device %s",
                  apkPackage, serial)
              : output);
    }
    return ImmutableList.copyOf(installedPaths);
  }

  /**
   * Grants given permission to the given package for user USER_SYSTEM. Note the device should
   * restart the adb daemon with root permissions, and the package should be installed before
   * granting permission.
   *
   * @param serial serial number of the device
   * @param packageName package name of the app to be granted with permission
   * @param permission the permission to grant to the package
   */
  public void grantPermission(String serial, String packageName, String permission)
      throws MobileHarnessException, InterruptedException {
    grantPermission(UtilArgs.builder().setSerial(serial).build(), packageName, permission);
  }

  /**
   * Grants given permission to the given package. Note the device should restart the adb daemon
   * with root permissions, and the package should be installed before granting permission.
   *
   * @param utilArgs args for serial, sdkVersion and userId
   * @param packageName package name of the app to be granted with permission
   * @param permission the permission to grant to the package
   */
  public void grantPermission(UtilArgs utilArgs, String packageName, String permission)
      throws MobileHarnessException, InterruptedException {
    isMultiUserSupported(utilArgs, AndroidVersion.MARSHMALLOW.getEndSdkVersion());

    String output = "";
    Exception exception = null;
    String serial = utilArgs.serial();
    String user = utilArgs.userId().isPresent() ? "--user " + utilArgs.userId().get() : null;
    String command =
        Joiner.on(' ')
            .skipNulls()
            .join(
                new String[] {ADB_SHELL_TEMPLATE_GRANT_PERMISSION, user, packageName, permission});

    try {
      output = adb.runShellWithRetry(serial, command);
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (exception != null || !output.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GRANT_PERMISSION_ERROR,
          String.format(
              "Failed to grant permission %s to %s: %s",
              permission, packageName, (exception == null ? output : exception.getMessage())),
          exception);
    }
  }

  /**
   * Installs the given apk to a specific device for user USER_ALL using default timeout value. If
   * the installation fails, will try to uninstall and re-install again.
   *
   * <p>See go/adb-install for installation regarding to multi-user.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param apkPath path of the apk package
   * @throws MobileHarnessException if error occurs
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void installApk(String serial, int sdkVersion, String apkPath)
      throws MobileHarnessException, InterruptedException {
    installApk(
        serial, sdkVersion, apkPath, /* grantPermissions= */ true, /* installTimeout= */ null);
  }

  /**
   * Installs the given apk to a specific device for user USER_ALL. If the installation fails (both
   * installation on internal storage and external storage), will try to uninstall and re-install
   * again.
   *
   * <p>See go/adb-install for installation regarding to multi-user.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param apkPath path of the apk package
   * @param grantPermissions whether to grant runtime permissions
   * @param installTimeout timeout for APK installation
   * @throws MobileHarnessException if error occurs
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void installApk(
      String serial,
      int sdkVersion,
      String apkPath,
      boolean grantPermissions,
      @Nullable Duration installTimeout)
      throws MobileHarnessException, InterruptedException {
    installApk(
        UtilArgs.builder().setSerial(serial).setSdkVersion(sdkVersion).build(),
        apkPath,
        grantPermissions,
        installTimeout);
  }

  /** Overload of {@link AndroidPackageManagerUtil#installApk} */
  public void installApk(
      UtilArgs utilArgs,
      String apkPath,
      boolean grantPermissions,
      @Nullable Duration installTimeout)
      throws MobileHarnessException, InterruptedException {
    installApk(
        utilArgs,
        apkPath,
        /* dexMetadataPath= */ null,
        grantPermissions,
        /* forceNoStreaming= */ false,
        installTimeout);
  }

  /**
   * Installs the given apk to a specific device. If the installation fails (both installation on
   * internal storage and external storage), will try to uninstall and re-install again.
   *
   * <p>See go/adb-install for installation regarding to multi-user.
   *
   * @param utilArgs args with serial, sdkVersion and userId
   * @param apkPath path of the apk package
   * @param dexMetadataPath optional path of the dex metadata file for the apk package
   * @param grantPermissions whether to grant runtime permissions
   * @param forceNoStreaming whether to add --no-streaming
   * @param installTimeout timeout for APK installation
   * @param extraArgs extra arguments to the install command
   * @throws MobileHarnessException if error occurs
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void installApk(
      UtilArgs utilArgs,
      String apkPath,
      @Nullable String dexMetadataPath,
      boolean grantPermissions,
      boolean forceNoStreaming,
      @Nullable Duration installTimeout,
      String... extraArgs)
      throws MobileHarnessException, InterruptedException {
    isMultiUserSupported(utilArgs, AndroidVersion.LOLLIPOP.getEndSdkVersion());
    if (dexMetadataPath != null) {
      isDexMetadataSupported(utilArgs);
    }

    String packageName = "";
    try {
      packageName = aapt.getApkPackageName(apkPath);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_PACKAGE_NAME_ERROR, e.getMessage(), e);
    }

    // Installs the apk.
    // "-d" only works with 17+ devices to allow app downgrade.
    // "-g" only works with 23+ devices to grant all required permissions.
    String serial = utilArgs.serial();
    int sdkVersion = utilArgs.sdkVersion().orElse(0);
    String[] installFiles =
        dexMetadataPath != null ? new String[] {apkPath, dexMetadataPath} : new String[] {apkPath};
    String[] installCommand =
        installFiles.length > 1 ? ADB_ARGS_INSTALL_MULTIPLE : ADB_ARGS_INSTALL;
    if (sdkVersion >= 17) {
      installCommand = ArrayUtil.join(installCommand, "-d");
    }
    if (sdkVersion >= 23 && grantPermissions) {
      installCommand = ArrayUtil.join(installCommand, "-g");
    }

    if (sdkVersion >= 27 && forceNoStreaming) {
      installCommand = ArrayUtil.join(installCommand, "--no-streaming");
    }

    if (extraArgs.length > 0) {
      installCommand = ArrayUtil.join(installCommand, extraArgs);
    }

    if (utilArgs.userId().isPresent()) {
      installCommand = ArrayUtil.join(installCommand, "--user", utilArgs.userId().get());
    }

    // Installs on external storage. The command is applicable for API >=15. Unknown for API < 15.
    // https://developer.android.com/studio/command-line/adb install [options] path section
    String[] installOnExternalCommand = ArrayUtil.join(installCommand, "--install-location", "2");
    installCommand = ArrayUtil.join(installCommand, installFiles);
    installOnExternalCommand = ArrayUtil.join(installOnExternalCommand, installFiles);

    LineCallback lineCallback =
        LineCallback.stopWhen(
            // The command can be hung with Android SDK 21+ release key devices. Cancels it once
            // got the signal. See b/18564117.
            line -> line.startsWith(OUTPUT_SUCCESS) || line.startsWith(OUTPUT_FAILURE));

    disablePackageVerifier(serial, sdkVersion); // for b/27476500

    String output;
    String outputByExternal = null;
    boolean installThrowsException = false;
    try {
      output =
          adb.run(
              serial,
              installCommand,
              installTimeout == null ? DEFAULT_INSTALL_TIMEOUT : installTimeout,
              lineCallback);
    } catch (MobileHarnessException e) {
      installThrowsException = true;
      output = e.getMessage();
      logger.atWarning().log(
          "Failed to install apk %s to device %s on [Internal Storage]:%n%s%n",
          apkPath, serial, output);
    }

    if (installThrowsException) {
      if (output.contains(OUTPUT_KILLED)) {
        // Throws the exception when adb was killed, because:
        // For Google experience devices, the definition of output can be found in
        // android/frameworks/base/core/java/android/content/pm/PackageManager.java
        // For oem devices, the return output may be customized to any kind of result.
        // e.g. Failure(-99)
        throwInstallationError(output, /* cause= */ null);
      }

      try {
        // If install failed, tries using external storage space (i.e. sdcard) to install
        outputByExternal =
            adb.run(
                serial,
                installOnExternalCommand,
                installTimeout == null ? DEFAULT_INSTALL_TIMEOUT : installTimeout,
                lineCallback);
        if (outputByExternal.contains(OUTPUT_SUCCESS)) {
          logger.atInfo().log(
              "Successfully installed apk %s to device %s:%n%s on [External Storage]",
              apkPath, serial, cutInstallOutput(outputByExternal));
          return;
        }
      } catch (MobileHarnessException ee) {
        logger.atWarning().withCause(ee).log(
            "Failed to install apk %s to device %s on [External Storage]:%n%s%n",
            apkPath, serial, outputByExternal);
      }
    } else {
      // Install does not throw exception
      if (output.contains(OUTPUT_SUCCESS)) {
        logger.atInfo().log(
            "Successfully installed apk %s to device %s:%n%s on [Internal Storage]",
            apkPath, serial, cutInstallOutput(output));
        return;
      }
    }

    if (output.contains(OUTPUT_INSTALL_FAILED_NO_MATCHING_ABIS)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_ABI_INCOMPATIBLE,
          "Failed to install "
              + apkPath
              + " due to no matching abis: "
              + output
              + "\n"
              + " See go/omnilab-faqs#how-to-fix-android-pkg-mngr-util-installation-abi-incompatible-error");
    }

    // Will be marked as FAIL (instead of ERROR) for this ErrorCode
    if (output.contains(OUTPUT_INSTALL_FAILED_MISSING_SHARED_LIBRARY)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_MISSING_SHARED_LIBRARY,
          String.format(
              "Failed to install %s due to missing shared libraries: %s%n"
                  + "Please check <uses-library> element in your manifest.%n",
              apkPath, output));
    }

    if (output.contains(OUTPUT_INSTALL_PARSE_FAILED_MANIFEST_MALFORMED)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_PARSE_FAILED_MANIFEST_MALFORMED,
          String.format(
              "Failed to install %s due to the manifest marlformed: %s", apkPath, output));
    }

    if (output.contains(OUTPUT_INSTALL_FAILED_INVALID_APK)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_INVALID_APK,
          String.format("Failed to install %s due to the invalid apk: %s", apkPath, output));
    }

    if (output.contains(OUTPUT_INSTALL_FAILED_DUPLICATE_PERMISSION)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_DUPLICATE_PERMISSION,
          String.format(
              "Failed to install %s due to the duplicate permission: %s", apkPath, output));
    }

    if (output.contains(OUTPUT_INSTALL_FAILED_OLDER_SDK)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_OLDER_SDK,
          String.format("Failed to install %s due to the older sdk: %s", apkPath, output));
    }

    logger.atWarning().log("Failed to install apk %s to device %s:%n%s", apkPath, serial, output);
    // Uninstalls the existing package.
    try {
      uninstallApk(utilArgs, packageName);
    } catch (MobileHarnessException e) {
      String errorMessage =
          String.format(
              "Failed to install on internal storage %s:%n%s%n"
                  + "Also failed to install on external storage %s:%n%s%n"
                  + "Try to uninstall package %s but failed too:%n%s%n",
              apkPath, output, apkPath, outputByExternal, packageName, e.getMessage());

      if (output.contains(OUTPUT_INSTALL_FAILED_UPDATE_INCOMPATIBLE)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_UPDATE_INCOMPATIBLE, errorMessage);
      }
      if (output.contains(String.format(OUTPUT_TEMPLATE_INSTALL_FAILED_UID_INVALID, packageName))) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_NO_VALID_UID_ASSIGNED,
            String.format(
                "Failed to install apk %s on device [%s] because no valid UID assigned: %s\n"
                    + "Please consider one of below options:\n"
                    + "1) adding param reboot_after_uninstallation:true to your test if it uses"
                    + " AndroidCleanAppsDecorator.\n"
                    + "2) rebooting device [%s].\n"
                    + "3) running on a different device.",
                apkPath, serial, output, serial));
      }
      if (AdbOutputParsingUtil.isAdbOutputOfInstallationInsufficientStorage(output)) {
        if (DeviceUtil.inSharedLab()) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_INSUFFICIENT_STORAGE,
              String.format(
                  "%s. The remaining storage is not enough, consider running a different device"
                      + " model.%n",
                  errorMessage));
        } else {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_INSUFFICIENT_STORAGE,
              String.format(
                  "%s. The remaining storage is not enough for installation. An option would"
                      + " be to add AndroidCleanAppsDecorator to the test to release some space"
                      + " before installation happens. If it still doesn't work, please contact the"
                      + " lab owner to clean up the device.%n",
                  errorMessage));
        }
      }

      // Remove the package floder when UID changed error occurred. b/27364937
      if (output.contains(OUTPUT_INSTALL_FAILED_UID_CHANGED)) {
        logger.atWarning().log(
            "Failed to uninstall package %s :%n%s%nTry to remove the package folder directly.",
            packageName, output);
        try {
          androidFileUtil.removeFiles(serial, "/data/data/" + packageName, Duration.ofSeconds(30));
        } catch (MobileHarnessException removeFilesException) {
          throwInstallationError(
              errorMessage
                  + String.format(
                      "And failed to delete the package folder:%n%s%n",
                      removeFilesException.getMessage()),
              /* cause= */ null);
        }
      } else {
        throwInstallationError(errorMessage, /* cause= */ null);
      }
    }

    // Retries to install the apk again.
    try {
      output =
          adb.run(
              serial,
              installCommand,
              installTimeout == null ? DEFAULT_INSTALL_TIMEOUT : installTimeout,
              lineCallback);
    } catch (MobileHarnessException e) {
      // For the second time, will throw whatever exception even if we got the error
      // INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE
      throwInstallationError("Fail to install " + apkPath + " twice: " + e.getMessage(), e);
    }
    if (!output.contains(OUTPUT_SUCCESS)) {
      throwInstallationError("Fail to install " + apkPath + " twice: " + output, /* cause= */ null);
    }

    logger.atWarning().log(
        "Successfully install apk %s to device %s after uninstalling existing package %s",
        apkPath, serial, packageName);
  }

  /**
   * Installs a list of packages with a single "adb install-multi-package" command to user USER_ALL.
   * Only work on devices with SDK version >= 29.
   *
   * @param serial serial number of the device
   * @param apkList a list of apk/apex files to be installed
   * @param waitForStagedSessionReady wait timeout for the stage finished
   * @param installTimeout timeout for installation
   * @param extraArgs extra arguments to the install-multi-package command
   * @throws MobileHarnessException if install command fails
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public void installMultiPackage(
      String serial,
      List<String> apkList,
      Duration waitForStagedSessionReady,
      @Nullable Duration installTimeout,
      String... extraArgs)
      throws MobileHarnessException, InterruptedException {
    installMultiPackage(
        UtilArgs.builder().setSerial(serial).build(),
        apkList,
        waitForStagedSessionReady,
        installTimeout,
        extraArgs);
  }

  /**
   * Installs a list of packages with a single "adb install-multi-package" command to user USER_ALL.
   * Only work on devices with SDK version >= 29.
   *
   * @param utilArgs args with serial, sdkVersion and userId
   * @param apkList a list of apk/apex files to be installed
   * @param waitForStagedSessionReady wait timeout for the stage finished
   * @param installTimeout timeout for installation
   * @param extraArgs extra arguments to the install-multi-package command
   * @throws MobileHarnessException if install command fails
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public void installMultiPackage(
      UtilArgs utilArgs,
      List<String> apkList,
      Duration waitForStagedSessionReady,
      @Nullable Duration installTimeout,
      String... extraArgs)
      throws MobileHarnessException, InterruptedException {
    InstallCmdArgs.Builder installCmdArgs =
        InstallCmdArgs.builder()
            .setReplaceExistingApp(true)
            .setAllowVersionCodeDowngrade(true)
            .setGrantPermissions(true);
    ListMultimap<String, String> apkMap = ArrayListMultimap.create();
    for (String apk : apkList) {
      String packageName = aapt.getApkPackageName(apk);
      apkMap.put(packageName, apk);
    }
    if (extraArgs.length > 0) {
      installCmdArgs.setExtraArgs(ImmutableList.copyOf(extraArgs));
    }
    installMultiPackage(
        utilArgs, installCmdArgs.build(), apkMap, waitForStagedSessionReady, installTimeout);
  }

  /**
   * Installs multiple packages with a single "adb install-multi-package" command to user USER_ALL.
   * Only work on devices with SDK version >= 29.
   *
   * @param utilArgs args with serial, sdkVersion and userId
   * @param installCmdArgs adb install args
   * @param packageMap a multimap from the package name to the corresponding apk/apex files to be
   *     installed
   * @param waitForStagedSessionReady wait timeout for the stage finished
   * @param installTimeout timeout for installation
   * @throws MobileHarnessException if install command fails
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public void installMultiPackage(
      UtilArgs utilArgs,
      InstallCmdArgs installCmdArgs,
      Multimap<String, String> packageMap,
      @Nullable Duration waitForStagedSessionReady,
      @Nullable Duration installTimeout)
      throws MobileHarnessException, InterruptedException {
    isMultiUserSupported(utilArgs, DEFAULT_MULTI_USER_START_SDK_VERSION);
    if (utilArgs.sdkVersion().isPresent()
        && utilArgs.sdkVersion().getAsInt() < DEFAULT_INSTALL_MULTI_PACKAGE_START_SDK_VERSION) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_SDK_VERSION_NOT_SUPPORT,
          String.format(
              "Install-multi-package support requires the minimal API level to %s",
              DEFAULT_INSTALL_MULTI_PACKAGE_START_SDK_VERSION));
    }
    // Since sdk >= 29, all args are possible except -p.
    if (installCmdArgs.partialApplicationInstall()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_PARTIAL_INSTALL_NOT_ALLOWED_ERROR,
          "Partial application installation are not allowed for install-multi-package.");
    }

    if (packageMap.isEmpty()) {
      logger.atWarning().log("No package to install.");
      return;
    }

    String[] installMultiPackagesCommand =
        ArrayUtil.join(ADB_ARG_INSTALL_MULTI_PACKAGE, installCmdArgs.getInstallArgsArray());
    if (utilArgs.userId().isPresent()) {
      installMultiPackagesCommand =
          ArrayUtil.join(installMultiPackagesCommand, "--user", utilArgs.userId().get());
    }

    Joiner apkJoiner = Joiner.on(":");
    installMultiPackagesCommand =
        ArrayUtil.join(
            installMultiPackagesCommand,
            packageMap.keySet().stream()
                .map(key -> apkJoiner.join(packageMap.get(key)))
                .collect(toCollection(ArrayList::new))
                .toArray(new String[0]));

    String output;
    String serial = utilArgs.serial();
    Duration timeout =
        installTimeout == null
            ? DEFAULT_INSTALL_TIMEOUT.multipliedBy(packageMap.keySet().size())
            : installTimeout;
    try {
      output =
          adb.run(
              serial,
              installMultiPackagesCommand,
              timeout,
              LineCallback.stopWhen(
                  line -> line.startsWith(OUTPUT_SUCCESS) || line.startsWith(OUTPUT_FAILURE)));

      // TODO: Use --wait-for-staged-session-ready instead.
      if (packageMap.values().stream().anyMatch(f -> f.endsWith(APEX_SUFFIX))
          && waitForStagedSessionReady != null) {
        sleeper.sleep(waitForStagedSessionReady);
      }
    } catch (MobileHarnessException e) {
      output = e.getMessage();
      if (output.contains(OUTPUT_INSTALL_FAILED_NO_MATCHING_ABIS)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_ABI_INCOMPATIBLE,
            String.format(
                "failed to install %s on device %s due to no matching abis",
                packageMap.values(), serial));
      }
      throwInstallationError("install command killed", e);
    }

    if (!allSessionsSuccess(output)) {
      if (output.contains(OUTPUT_INSTALL_FAILED_VERSION_DOWNGRADE)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_VERSION_DOWNGRADE,
            String.format("install-multi-package error: %s", output));
      }
      if (output.contains(OUTPUT_INSTALL_FAILED_INVALID_APK_SPLIT_NULL)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_INVALID_APK_SPLIT_NULL,
            String.format(
                "install-multi-package error: %s. See"
                    + " go/omnilab-faqs#how-to-fix-android-pkg-mngr-util-installation-invalid-apk-split-null"
                    + " for more details.",
                output));
      }
      throwInstallationError(
          "Failed to install packages:\n" + packageMap + '\n' + output, /* cause= */ null);
    }

    logger.atWarning().log("Successfully install apks %s to device %s", packageMap, serial);
  }

  /**
   * Gets package names of packages on the device for user USER_SYSTEM.
   *
   * @param serial serial number of the device
   * @param type package type
   * @return a list of package names; will never be null but could be empty
   * @throws MobileHarnessException if fail getting package info from the device
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public Set<String> listPackages(String serial, PackageType type)
      throws MobileHarnessException, InterruptedException {
    return listPackages(UtilArgs.builder().setSerial(serial).build(), type);
  }

  /**
   * Gets package names of packages on the device.
   *
   * @param utilArgs args with serial, sdkVersion and userId
   * @param type package type
   * @return a list of package names; will never be null but could be empty
   * @throws MobileHarnessException if fail getting package info from the device
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public Set<String> listPackages(UtilArgs utilArgs, PackageType type)
      throws MobileHarnessException, InterruptedException {
    isMultiUserSupported(utilArgs, DEFAULT_MULTI_USER_START_SDK_VERSION);

    String serial = utilArgs.serial();
    String[] adbCommand = new String[] {ADB_SHELL_LIST_PACKAGES, type.getOption()};
    if (utilArgs.userId().isPresent()) {
      adbCommand = ArrayUtil.join(adbCommand, "--user", utilArgs.userId().get());
    }

    String output = "";
    try {
      output = adb.runShellWithRetry(serial, Joiner.on(' ').skipNulls().join(adbCommand));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_LIST_PACKAGES_ERROR, e.getMessage(), e);
    }
    Set<String> packages = new HashSet<>();
    for (String line : Splitters.LINE_SPLITTER.trimResults().split(output)) {
      if (line.startsWith(OUTPUT_PACKAGE_PREFIX)) {
        packages.add(line.substring(OUTPUT_PACKAGE_PREFIX.length()));
      }
    }
    return packages;
  }

  /**
   * Gets a map of package UID and package name on the device for user USER_SYSTEM, sorted by UID.
   * Root access required for SDK version < 26.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @return a SortedMap of package UID and package name
   * @throws MobileHarnessException if fail to get package info from device
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public SortedMap<Integer, String> listPackagesWithUid(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    return listPackagesWithUid(
        UtilArgs.builder().setSerial(serial).setSdkVersion(sdkVersion).build());
  }

  /**
   * Gets a map of package UID and package name on the device, sorted by UID. Only work for SDK
   * version >= 26, no root access required.
   *
   * @param utilArgs args with serial, sdkVersion and userId
   * @param type package type
   * @return a SortedMap of package UID and package name
   * @throws MobileHarnessException if fail to get package info from device
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public SortedMap<Integer, String> listPackagesWithUid(UtilArgs utilArgs, PackageType type)
      throws MobileHarnessException, InterruptedException {
    isMultiUserSupported(utilArgs, DEFAULT_MULTI_USER_START_SDK_VERSION);

    String serial = utilArgs.serial();
    String[] adbCommand = new String[] {ADB_SHELL_LIST_PACKAGES, type.getOption(), "-U"};
    if (utilArgs.userId().isPresent()) {
      adbCommand = ArrayUtil.join(adbCommand, "--user ", utilArgs.userId().get());
    } else if (utilArgs.sdkVersion().orElse(0) >= AndroidVersion.PI.getEndSdkVersion()) {
      // For Auto Embedded devices (from P), the default user is 10 instead of 0 (USER_SYSTEM). if
      // UserId is not set, user 10 will be used and the returned package UIDs will be between
      // 1010000 ~ 1019999. Then MH will think package UIDs are exhausted and lead to unnecessary
      // device reboot. So, user 0 is explicitly added here to avoid this issue.
      adbCommand = ArrayUtil.join(adbCommand, "--user 0");
    }

    String output = "";
    try {
      output = adb.runShellWithRetry(serial, Joiner.on(' ').skipNulls().join(adbCommand));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_LIST_PACKAGES_ERROR, e.getMessage(), e);
    }
    SortedMap<Integer, String> packages = new TreeMap<>();
    for (String line : Splitters.LINE_SPLITTER.trimResults().split(output)) {
      // Output of "pm list packages -U"
      // package:com.google.android.inputmethod.latin uid:10122
      // package:com.google.android.storagemanager uid:10071
      if (line.startsWith(OUTPUT_PACKAGE_PREFIX)) {
        List<String> words = Splitter.onPattern("\\s+").trimResults().splitToList(line);
        if (words.size() != 2) {
          continue;
        }
        String packageName = words.get(0).substring(OUTPUT_PACKAGE_PREFIX.length());
        String packageUid = words.get(1).substring(OUTPUT_PACKAGE_UID_PREFIX.length());
        int uid = 0;
        try {
          uid = Integer.parseInt(packageUid);
        } catch (NumberFormatException e) {
          continue;
        }
        packages.put(uid, packageName);
      }
    }

    return packages;
  }

  /**
   * Gets a map of package UID and package name on the device, sorted by UID. Root access required
   * for SDK version < 26.
   *
   * @param utilArgs args with serial, sdkVersion and userId
   * @return a SortedMap of package UID and package name
   * @throws MobileHarnessException if fail to get package info from device
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public SortedMap<Integer, String> listPackagesWithUid(UtilArgs utilArgs)
      throws MobileHarnessException, InterruptedException {
    // For Android O+, get result from "pm list packages -U".
    if (utilArgs.sdkVersion().orElse(0) >= 26) {
      return listPackagesWithUid(utilArgs, PackageType.ALL);
    }

    String serial = utilArgs.serial();
    String output = "";
    try {
      output = adb.runShell(serial, ADB_SHELL_GET_PACKAGE_LIST);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_PACKAGE_LIST_ERROR, e.getMessage(), e);
    }
    SortedMap<Integer, String> packages = new TreeMap<>();
    for (String line : Splitters.LINE_SPLITTER.trimResults().split(output)) {
      List<String> words = Splitter.onPattern("\\s+").trimResults().splitToList(line);
      // Example of a line in packages.list:
      // com.android.egg 10121 0 /data/user/0/com.android.egg platform:targetSdkVersion=10000 none
      if (words.size() < 2) {
        continue;
      }

      String packageName = words.get(0).trim();
      String packageUid = words.get(1).trim();
      int uid = 0;
      try {
        uid = Integer.parseInt(packageUid);
      } catch (NumberFormatException e) {
        continue;
      }
      packages.put(uid, packageName);
    }

    return packages;
  }

  /**
   * List all Apex package infos installed on the device.
   *
   * <p>This method only works for SDK version >= Q.
   *
   * @param serial id for the device.
   * @return a sorted set of package infos.
   */
  public SortedSet<PackageInfo> listApexPackageInfos(String serial)
      throws MobileHarnessException, InterruptedException {
    return listApexPackageInfos(UtilArgs.builder().setSerial(serial).build());
  }

  /**
   * List all Apex package infos installed on the device.
   *
   * <p>This method only works for SDK version >= Q.
   *
   * @param utilArgs args with serial, sdkVersion and userId.
   * @return a sorted set of package infos.
   */
  public SortedSet<PackageInfo> listApexPackageInfos(UtilArgs utilArgs)
      throws MobileHarnessException, InterruptedException {
    String serial = utilArgs.serial();

    String[] adbCommand =
        new String[] {ADB_SHELL_LIST_PACKAGES, SHOW_VERSION_CODE_FLAG, "--apex-only", "-f"};

    if (utilArgs.userId().isPresent()) {
      adbCommand = ArrayUtil.join(adbCommand, "--user", utilArgs.userId().get());
    }

    String output;
    try {
      output = adb.runShellWithRetry(serial, Joiner.on(' ').skipNulls().join(adbCommand));
      logger.atInfo().log("List apex packages\n%s", output);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_LIST_APEX_PACKAGES_ERROR, e.getMessage(), e);
    }

    SortedSet<PackageInfo> packages = parseApexesFromOutput(output, /* withPath= */ true);
    if (packages.isEmpty()) {
      packages = parseApexesFromOutput(output, /* withPath= */ false);
    }

    return packages;
  }

  /**
   * List all package infos installed on the device.
   *
   * <p>This method only works for SDK version >= Q.
   *
   * @param serial id for the device.
   * @return a sorted set of package infos.
   */
  public SortedSet<PackageInfo> listPackageInfos(String serial)
      throws MobileHarnessException, InterruptedException {
    return listPackageInfos(UtilArgs.builder().setSerial(serial).build());
  }

  /**
   * List all package infos installed on the device.
   *
   * <p>This method only works for SDK version >= Q.
   *
   * @param utilArgs args with serial, sdkVersion and userId.
   * @return a sorted set of package infos.
   */
  public SortedSet<PackageInfo> listPackageInfos(UtilArgs utilArgs)
      throws MobileHarnessException, InterruptedException {
    String serial = utilArgs.serial();
    SortedSet<PackageInfo> packages = new TreeSet<>();

    String[] adbCommand = new String[] {ADB_SHELL_LIST_PACKAGES, SHOW_VERSION_CODE_FLAG, "-f"};

    if (utilArgs.userId().isPresent()) {
      adbCommand = ArrayUtil.join(adbCommand, "--user", utilArgs.userId().get());
    }

    String output;
    try {
      output = adb.runShellWithRetry(serial, Joiner.on(' ').skipNulls().join(adbCommand));
      logger.atInfo().log("List apk packages\n%s", output);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_LIST_PACKAGES_ERROR, e.getMessage(), e);
    }

    for (String line : Splitters.LINE_SPLITTER.trimResults().split(output)) {
      Matcher matcher;
      if ((matcher = LIST_PACKAGE_WITH_SOURCE_DIR_AND_VERSION_REGEX.matcher(line)).matches()) {
        packages.add(
            PackageInfo.builder()
                .setPackageName(matcher.group("pkgName"))
                .setSourceDir(matcher.group("sourceDir"))
                .setVersionCode(Long.parseLong(matcher.group("versionCode")))
                .build());
      } else {
        logger.atWarning().log("The line %s doesn't match the package pattern", line);
      }
    }

    return packages;
  }

  /**
   * Lists all module infos.
   *
   * <p>This method only works for SDK version >= Q.
   *
   * @param serial id for the device.
   * @return a sorted set of the module infos.
   */
  public SortedSet<ModuleInfo> listModuleInfos(String serial)
      throws MobileHarnessException, InterruptedException {
    SortedSet<ModuleInfo> modules = new TreeSet<>();
    LineProcessor processor =
        new LineProcessor() {
          @Override
          boolean processSuccess(String line) {
            line = line.trim();
            Matcher matcher;
            if ((matcher = MODULEINFO_REGEX.matcher(line)).matches()) {
              modules.add(
                  ModuleInfo.builder()
                      .setName(matcher.group("name"))
                      .setPackageName(matcher.group("pkgName"))
                      .build());
              return true;
            } else {
              logger.atWarning().log("The line [%s] doesn't match the module info pattern", line);
              return false;
            }
          }
        };
    String[] adbCommand = new String[] {"-s", serial, "shell", ADB_SHELL_GET_MODULEINFO, "--all"};
    Optional<MobileHarnessException> exceptionOp = processAdbResult(adbCommand, processor);

    if (processor.success()) {
      return modules;
    }
    throw exceptionOp.orElse(
        new MobileHarnessException(
            AndroidErrorId.ANDROID_PKG_MNGR_UTIL_LIST_MODULES_ERROR,
            "List moduleinfo not success."));
  }

  /**
   * Uninstalls the given package on the specific device for user USER_ALL.
   *
   * @param serial serial number of the device
   * @param packageName package name of the apk for uninstallation
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void uninstallApk(String serial, String packageName)
      throws MobileHarnessException, InterruptedException {
    uninstallApk(UtilArgs.builder().setSerial(serial).build(), packageName);
  }

  /**
   * Uninstalls the given package on the specific device.
   *
   * @param utilArgs args with serial, sdkVersion and userId
   * @param packageName package name of the apk for uninstallation
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void uninstallApk(UtilArgs utilArgs, String packageName)
      throws MobileHarnessException, InterruptedException {
    if (Objects.equals(packageName, PackageConstants.PACKAGE_NAME_GMS)
        && getInstalledPath(utilArgs, packageName).contains("/product/priv-app")) {
      logger.atInfo().log(
          "Skip uninstalling system base GmsCore as it will cause uninstall failure.");
      return;
    }

    isMultiUserSupported(utilArgs, DEFAULT_MULTI_USER_START_SDK_VERSION);

    String output = "";
    Exception exception = null;
    String serial = utilArgs.serial();
    String[] uninstallCommand = new String[] {ADB_ARG_UNINSTALL};
    if (utilArgs.userId().isPresent()) {
      uninstallCommand = ArrayUtil.join(uninstallCommand, "--user", utilArgs.userId().get());
    }
    uninstallCommand = ArrayUtil.join(uninstallCommand, packageName);

    try {
      output = adb.run(serial, uninstallCommand, DEFAULT_INSTALL_TIMEOUT);
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (exception != null || !output.contains(OUTPUT_SUCCESS)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_UNINSTALLATION_ERROR,
          (exception == null ? output : exception.getMessage()),
          exception);
    }
  }

  /**
   * Uninstalls app's package from package manager on device. If it fails, only a log is added.
   *
   * @param serial number of the device
   * @param packageName packageName of the app.
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void uninstallApkWithoutCheckingOutput(String serial, String packageName)
      throws InterruptedException {
    try {
      uninstallApk(serial, packageName);
    } catch (MobileHarnessException e) {
      // No need to throw exception, because sometimes packages are already uninstalled.
      logger.atWarning().log(
          "Failed to uninstall package %s from package manager on device %s: %s",
          packageName, serial, e.getMessage());
    }
  }

  @VisibleForTesting
  static String cutInstallOutput(String output) {
    return output.contains("[100%]") ? output.substring(output.lastIndexOf("[100%]")) : output;
  }

  /** Check if user id has been specified and device sdk version. */
  private static void isMultiUserSupported(UtilArgs utilArgs, int multiUserStartSdkVersion)
      throws MobileHarnessException {
    if (utilArgs.userId().isPresent()) {
      isDeviceSdkVersionIsAtLeast(utilArgs, multiUserStartSdkVersion, "Multi-user");
    }
  }

  private static void isDexMetadataSupported(UtilArgs utilArgs) throws MobileHarnessException {
    isDeviceSdkVersionIsAtLeast(
        utilArgs, DEFAULT_INSTALL_DEX_METADATA_START_SDK_VERSION, "Dex metadata installation");
  }

  /** Check if the device sdk version is at least {@code minSdkVersion}. */
  private static void isDeviceSdkVersionIsAtLeast(
      UtilArgs utilArgs, int minSdkVersion, String featureName) throws MobileHarnessException {
    if (utilArgs.sdkVersion().orElse(0) < minSdkVersion) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_SDK_VERSION_NOT_SUPPORT,
          String.format("%s support request minimal API level to %s", featureName, minSdkVersion));
    }
  }

  private boolean isQtOrAboveBuild(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    String versionCodename = "";
    try {
      versionCodename = adbUtil.getProperty(serial, AndroidProperty.CODENAME);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GET_DEVICE_PROP_ERROR, e.getMessage(), e);
    }
    return Ascii.equalsIgnoreCase("Q", versionCodename)
        || sdkVersion > AndroidVersion.PI.getEndSdkVersion();
  }

  private static boolean allSessionsSuccess(String output) {
    // Session may fail in spite of showing Success in output.
    return output.contains(OUTPUT_SUCCESS)
        && Splitters.LINE_SPLITTER
            .trimResults()
            .splitToStream(output)
            .allMatch(
                line -> line.startsWith(OUTPUT_SUCCESS) || line.startsWith(SESSION_CREATION_START));
  }

  private void throwInstallationError(String message, @Nullable Throwable cause)
      throws MobileHarnessException {
    if (DeviceUtil.inSharedLab()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_ERROR_IN_SHARED_LAB, message, cause);
    } else {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_ERROR_IN_SATELLITE_LAB, message, cause);
    }
  }

  /**
   * Processes adb results line by line in spite of possible execution failure.
   *
   * <p>An execution failure is returned instead of throwing. It allows users to determine if the
   * processing successes.
   *
   * @return possible execution exception during adb run.
   */
  private Optional<MobileHarnessException> processAdbResult(
      String[] command, LineProcessor processor)
      throws InterruptedException, MobileHarnessException {
    try {
      Command cmd = adb.getAdbCommand().args(command).onStdout(does(processor::process));
      // The command result is processed by the processor.
      CommandResult unused = adb.run(cmd);
      return Optional.empty();
    } catch (MobileHarnessException e) {
      MobileHarnessException toThrow =
          new MobileHarnessException(
              AndroidErrorId.ANDROID_PKG_MNGR_UTIL_LIST_MODULES_ERROR, e.getMessage(), e);
      if (e.getErrorId() == AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE) {
        logger.atWarning().log(
            "Ignore the execution failure to process the output: %s",
            MoreThrowables.shortDebugString(e, /* maxLength= */ 0));
        return Optional.of(toThrow);
      } else {
        throw toThrow;
      }
    }
  }

  private static SortedSet<PackageInfo> parseApexesFromOutput(String output, boolean withPath) {
    SortedSet<PackageInfo> packages = new TreeSet<>();

    Matcher matcher =
        withPath
            ? LIST_PACKAGE_WITH_SOURCE_DIR_AND_VERSION_REGEX.matcher(output)
            : LIST_PACKAGE_WITH_VERSION_REGEX.matcher(output);

    while (matcher.find()) {
      if (withPath) {
        packages.add(
            PackageInfo.builder()
                .setSourceDir(matcher.group("sourceDir"))
                .setPackageName(matcher.group("pkgName"))
                .setVersionCode(Long.parseLong(matcher.group("versionCode")))
                .setIsApex(true)
                .build());
      } else {
        packages.add(
            PackageInfo.builder()
                .setSourceDir("")
                .setPackageName(matcher.group("pkgName"))
                .setVersionCode(Long.parseLong(matcher.group("versionCode")))
                .setIsApex(true)
                .build());
      }
    }

    return packages;
  }
}
