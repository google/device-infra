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

package com.google.devtools.mobileharness.platform.android.instrumentation;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.devtools.mobileharness.api.model.proto.Test;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkfile.ApkAnalyzer;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AppOperationMode;
import com.google.devtools.mobileharness.platform.android.testing.proto.TestArgsProto.TestArgumentPb;
import com.google.devtools.mobileharness.platform.android.testing.proto.TestArgsProto.TestArgumentsPb;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserUtil;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryException;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryStrategy;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryingCallable;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidInstrumentationDriverSpec;
import com.google.wireless.qa.mobileharness.shared.api.spec.EntryDelimiterSpec;
import com.google.wireless.qa.mobileharness.shared.api.spec.SplitMethodSpec;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidInstrumentation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidInstrumentationSpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Util class for Android Instrumentation Test.
 *
 * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
 * separator on SDK>23. It's callers' responsibility to parse it correctly.
 */
public class AndroidInstrumentationUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Handler for handling APP installation start event. */
  @FunctionalInterface
  public interface AppInstallStartHandler {

    void onAppInstallStart(TestInfo testInfo, String packageName);
  }

  /** Handler for handling APP installation finish event. */
  @FunctionalInterface
  public interface AppInstallFinishHandler {

    void onAppInstallFinish(TestInfo testInfo, String packageName);
  }

  /** ADB shell command to run instrumentation test. Should be followed with test specific args. */
  @VisibleForTesting static final String ADB_SHELL_INSTRUMENT = "am instrument";

  /** ADB shell command to set instrumentation classpath */
  @VisibleForTesting
  static final String ADB_SHELL_INSTRUMENT_CLASSPATH_TEMPLATE =
      "CLASSPATH=$(pm path %s androidx.test.services)";

  /** ADB Shell command to disable isolated-storage for instrumentation. */
  @VisibleForTesting
  static final String ADB_SHELL_INSTRUMENT_NO_ISOLATED_STORAGE = " --no-isolated-storage";

  /** ADB shell command to set instrumentation shell service */
  @VisibleForTesting
  static final String ADB_SHELL_INSTRUMENT_SHELL_SERVICE =
      "app_process / androidx.test.services.shellexecutor.ShellMain";

  /** ADB shell command for listing all the instrumentation info of device. */
  @VisibleForTesting static final String ADB_SHELL_LIST_INSTRUMENTATION = "pm list instrumentation";

  /** ADB shell command argument for nohup run. Should be followed with actual command. */
  static final String ADB_SHELL_NOHUP_START = "nohup >/dev/null 2>&1 sh -c \"";

  /** ADB shell command argument for nohup run. Should be followed with actual command. */
  static final String ADB_SHELL_NOHUP_END = "\"";

  /** Internal use device path for AndroidX test library. */
  @VisibleForTesting
  static final String ANDROID_TEST_DEVICE_PATH_INTERNAL_USE =
      TestStorageConstants.ON_DEVICE_PATH_INTERNAL_USE;

  /** Test running files device path for AndroidX test library. */
  private static final String ANDROID_TEST_DEVICE_PATH_TEST_RUNFILES =
      TestStorageConstants.ON_DEVICE_TEST_RUNFILES;

  /** Test output files device path for AndroidX test library. */
  @VisibleForTesting
  static final String ANDROID_TEST_DEVICE_PATH_TEST_OUTPUT =
      TestStorageConstants.ON_DEVICE_PATH_TEST_OUTPUT;

  /** Test property files device path for AndroidX test library. */
  @VisibleForTesting
  static final String ANDROID_TEST_DEVICE_PATH_TEST_PROPERTIES =
      TestStorageConstants.ON_DEVICE_PATH_TEST_PROPERTIES;

  /** Test argument file name for AndroidX test library. */
  private static final String ANDROID_TEST_TEST_ARGS_FILE_NAME =
      TestStorageConstants.TEST_ARGS_FILE_NAME;

  @VisibleForTesting static final String APP_OP_MANAGE_EXTERNAL_STORAGE = "MANAGE_EXTERNAL_STORAGE";

  /** Pattern to only parse first two items from content query. */
  private static final String CONTENT_QUERY_OUTPUT_PATTERN =
      "Row.*name=(?<NAME>.*),\\stype=(?<TYPE>.*),\\ssize=";

  /** ADB shell content command return output for no result. */
  private static final String CONTENT_QUERY_OUTPUT_NO_RESULT = "No result found";

  /** Default value of parameter of forcing to install basic service apk. */
  @VisibleForTesting static final boolean DEFAULT_FORCE_REINSTALL_BASIC_SERVICE_APK = true;

  /** TODO: remove this magic sdk version once b/128948778 got fixed. */
  @VisibleForTesting static final int MAGIC_SDK_VERSION = 100;

  /** Prefix out the output lines of the "adb shell pm list instrumentation" command. */
  private static final String OUTPUT_INSTRUMENTATION_PREFIX = "instrumentation:";

  /** Suffix out the output lines of the "adb shell pm list instrumentation" command. */
  private static final String OUTPUT_INSTRUMENTATION_SUFFIX = " (target=";

  /** The name of the test arg which contains the relative path of the test gen file dir. */
  private static final String TEST_ARG_GEN_FILE_DIR_RELATIVE_PATH =
      DirCommon.KEY_NAME_OF_TEST_GEN_FILE_DIR_RELATIVE_PATH;

  /* Device reconnect configuration parameters. */
  private static final int DEVICE_RECONNECT_MAX_RETRIES = 3;
  private static final Duration DEVICE_RECONNECT_INITIAL_DELAY = Duration.ofSeconds(5);
  private static final int DEVICE_RECONNECT_DELAY_MULTIPLIER = 3;

  /** Wait time for MediaProvider to reindex files after clearing its data. */
  private static final Duration MEDIA_PROVIDER_REINDEX_DELAY = Duration.ofSeconds(10);

  /** The result of the gtest process result. */
  @AutoValue
  public abstract static class GtestResult {
    /** The result of the gtest process. */
    public abstract TestResult testResult();

    /** The error message of the gtest process. */
    public abstract Optional<String> errorMessage();

    public static GtestResult of(TestResult testResult, Optional<String> errorMessage) {
      return new AutoValue_AndroidInstrumentationUtil_GtestResult(testResult, errorMessage);
    }
  }

  /** Android SDK ADB command line tools executor. */
  private final Adb adb;

  /** Android File utility class to manage file on device. */
  private final AndroidFileUtil androidFileUtil;

  /** Android User utility class to manage user information on device. */
  private final AndroidUserUtil androidUserUtil;

  /** Android utility class to query device setting. */
  private final AndroidSystemSettingUtil settingUtil;

  private final CommandExecutor commandExecutor;

  /** {@code Clock} for getting current system time. */
  private final Clock clock;

  /** File utility class to manage file on host machine. */
  private final LocalFileUtil localFileUtil;

  private final ApkAnalyzer apkAnalyzer;

  private final SystemStateManager systemStateManager;

  private final ResUtil resUtil;

  private final Aapt aapt;

  private final ApkInstaller apkInstaller;

  private final Sleeper sleeper;

  /** Creates a util for Android device operations. */
  public AndroidInstrumentationUtil() {
    this(
        new Adb(),
        new CommandExecutor(),
        Clock.systemUTC(),
        new LocalFileUtil(),
        new AndroidFileUtil(),
        new AndroidSystemSettingUtil(),
        new AndroidUserUtil(),
        new ApkAnalyzer(),
        new SystemStateManager(),
        new ResUtil(),
        new Aapt(),
        new ApkInstaller(),
        Sleeper.defaultSleeper());
  }

  /** Constructor for unit tests only. */
  @VisibleForTesting
  protected AndroidInstrumentationUtil(
      Adb adb,
      CommandExecutor commandExecutor,
      Clock clock,
      LocalFileUtil localFileUtil,
      AndroidFileUtil androidFileUtil,
      AndroidSystemSettingUtil settingUtil,
      AndroidUserUtil androidUserUtil,
      ApkAnalyzer apkAnalyzer,
      SystemStateManager systemStateManager,
      ResUtil resUtil,
      Aapt aapt,
      ApkInstaller apkInstaller,
      Sleeper sleeper) {
    this.adb = adb;
    this.commandExecutor = commandExecutor;
    this.clock = clock;
    this.localFileUtil = localFileUtil;
    this.androidFileUtil = androidFileUtil;
    this.settingUtil = settingUtil;
    this.androidUserUtil = androidUserUtil;
    this.apkAnalyzer = apkAnalyzer;
    this.systemStateManager = systemStateManager;
    this.resUtil = resUtil;
    this.aapt = aapt;
    this.apkInstaller = apkInstaller;
    this.sleeper = sleeper;
  }

  /**
   * Runs instrument command within the time limited.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of the device
   * @param deviceSdkVersion sdk version of the device, to determine whether nohup is supported when
   *     user want to async run instrument
   * @param instrumentationSetting arguments to run "am instrument" command
   * @param timeout max execution time
   * @return std/err output
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   * @see <a href="https://developer.android.com/studio/command-line/adb#am">Instrument options</a>
   */
  @CanIgnoreReturnValue
  public String instrument(
      String serial,
      @Nullable Integer deviceSdkVersion,
      AndroidInstrumentationSetting instrumentationSetting,
      Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return instrument(
        serial, deviceSdkVersion, instrumentationSetting, timeout, /* lineCallbackFactory= */ null);
  }

  /**
   * Runs instrument command within the time limited.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of the device
   * @param deviceSdkVersion sdk version of the device, to determine whether nohup is supported when
   *     user want to async run instrument
   * @param instrumentationSetting arguments to run "am instrument" command
   * @param timeout max execution time
   * @param lineCallbackFactory a factory to create a callback to be called when a line is received
   *     from the adb instrument command execution.
   * @return std/err output
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   * @see <a href="https://developer.android.com/studio/command-line/adb#am">Instrument options</a>
   */
  @CanIgnoreReturnValue
  public String instrument(
      String serial,
      @Nullable Integer deviceSdkVersion,
      AndroidInstrumentationSetting instrumentationSetting,
      Duration timeout,
      @Nullable Supplier<LineCallback> lineCallbackFactory)
      throws MobileHarnessException, InterruptedException {
    StringBuilder command = new StringBuilder();
    String packageName = instrumentationSetting.packageName();
    String runnerName = instrumentationSetting.runnerName();
    String className = instrumentationSetting.className();
    Map<String, String> otherOptions = instrumentationSetting.otherOptions();
    boolean async = instrumentationSetting.async();
    boolean showRawResults = instrumentationSetting.showRawResults();
    boolean prefixAndroidTest = instrumentationSetting.prefixAndroidTest();
    boolean noIsolatedStorage = instrumentationSetting.noIsolatedStorage();
    boolean useTestStorageService = instrumentationSetting.useTestStorageService();
    boolean enableCoverage = instrumentationSetting.enableCoverage();
    boolean useOrchestrator = instrumentationSetting.useOrchestrator();
    LineCallback instrumentationResultCallback =
        (lineCallbackFactory != null) ? lineCallbackFactory.get() : null;

    if (async) {
      command.append(ADB_SHELL_NOHUP_START);
    }

    if (prefixAndroidTest || useOrchestrator) {
      /*
       According to the Javadoc of ShellCommandClient in
       //depot/google3/third_party/android/androidx_test/services/shellexecutor/java/androidx/test/services/shellexecutor/ShellCommandClient.java
       Need to prefix below parameter before start am instrumentation.
      */
      String userId = getCurrentUser(serial, deviceSdkVersion).orElse(null);
      command
          .append(
              String.format(
                  ADB_SHELL_INSTRUMENT_CLASSPATH_TEMPLATE,
                  userId == null ? "" : String.format("--user %s", userId)))
          .append(" ");
      command.append(ADB_SHELL_INSTRUMENT_SHELL_SERVICE).append(" ");
    }

    command.append(ADB_SHELL_INSTRUMENT);
    // No isolated storage flag is only supported after Android Q.
    if (noIsolatedStorage
        && deviceSdkVersion != null
        && deviceSdkVersion > AndroidVersion.PI.getEndSdkVersion()) {
      command.append(ADB_SHELL_INSTRUMENT_NO_ISOLATED_STORAGE);
    }
    command.append(" -w");
    if (showRawResults) {
      command.append(" -r");
    }
    if (!StrUtil.isEmptyOrWhitespace(className)) {
      command.append(" -e class ");
      command.append(className);
    }

    if (otherOptions != null && !otherOptions.isEmpty()) {
      for (Entry<String, String> entry : otherOptions.entrySet()) {
        command.append(" -e ");
        command.append(entry.getKey());
        command.append(' ');
        command.append(entry.getValue());
      }
    }

    if (useTestStorageService) {
      command.append(" -e useTestStorageService true");
    }

    if (enableCoverage) {
      command.append(" -e coverage true");
      command.append(" -e coverageFile ").append(convertClassNameToCoverageFileName(className));
    }

    final String testRunner;
    if (useOrchestrator) {
      command
          .append(" -e targetInstrumentation ")
          .append(packageName)
          .append("/")
          .append(runnerName);
      testRunner = "androidx.test.orchestrator/androidx.test.orchestrator.AndroidTestOrchestrator";
    } else {
      testRunner = packageName + "/" + runnerName;
    }
    command.append(" ").append(testRunner);

    if (async) {
      command.append(ADB_SHELL_NOHUP_END);
    }

    logger.atInfo().log("Running adb instrumentation: %s", command);
    long begin = clock.instant().toEpochMilli();
    try {
      if (async) {
        var unused = adb.runShellAsync(serial, command.toString(), timeout);
        return "";
      } else {
        String output =
            adb.runShell(serial, command.toString(), timeout, instrumentationResultCallback).trim();
        if (output.endsWith("=") && clock.instant().minusMillis(begin).toEpochMilli() < 3 * 1000) {
          if (lineCallbackFactory != null) {
            instrumentationResultCallback = lineCallbackFactory.get();
          }
          logger.atWarning().log("Instrument acts unexpectedly: %s%nTry again.", output);
          output =
              adb.runShell(serial, command.toString(), timeout, instrumentationResultCallback)
                  .trim();
        }
        return output;
      }
    } catch (MobileHarnessException e) {
      AndroidErrorId newId;
      if (AndroidErrorId.ANDROID_ADB_SYNC_CMD_START_ERROR.equals(e.getErrorId())
          || AndroidErrorId.ANDROID_ADB_ASYNC_CMD_START_ERROR.equals(e.getErrorId())) {
        newId = AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_START_ERROR;
      } else if (AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_TIMEOUT.equals(e.getErrorId())) {
        newId = AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_TIMEOUT;
      } else {
        newId = AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_FAILED;
      }
      throw new MobileHarnessException(
          newId,
          String.format(
              "%s to execute instrumentation command [%s]",
              (newId == AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_TIMEOUT
                  ? "Timeout"
                  : "Failed"),
              command),
          e);
    }
  }

  /**
   * Adds test specific test_args. Note it only works with AndroidInstrumentation driver, when the
   * test specific test_args are added before the driver runs the instrumentation test.
   */
  public void addTestSpecificTestArg(TestInfo testInfo, String name, String value) {
    Map<String, String> testArgMap = new HashMap<>(getTestSpecificTestArgs(testInfo));
    testArgMap.put(name, value);
    testInfo
        .properties()
        .add(
            AndroidInstrumentation.TEST_SPECIFIC_TEST_ARGS,
            Joiner.on(getEntryDelimiter(testInfo.jobInfo()))
                .withKeyValueSeparator(StrUtil.DEFAULT_KEY_VALUE_DELIMITER)
                .join(testArgMap));
  }

  /** Clean the external storage on device before test. */
  public Optional<String> cleanTestStorageOnDevice(
      TestInfo testInfo, String deviceId, int deviceSdkVersion) throws InterruptedException {
    String externalStoragePath = null;
    try {
      externalStoragePath = androidFileUtil.getExternalStoragePath(deviceId, deviceSdkVersion);
      String deviceGooglePath =
          PathUtil.join(externalStoragePath, TestStorageConstants.ON_DEVICE_PATH_ROOT);
      if (androidFileUtil.isFileOrDirExisted(deviceId, deviceGooglePath)) {
        testInfo.log().atInfo().alsoTo(logger).log("Clean directory: %s", deviceGooglePath);
        String tmpDeviceGooglePath =
            PathUtil.join(
                PathUtil.dirname(deviceGooglePath),
                PathUtil.basename(deviceGooglePath) + "_" + testInfo.locator().getId());
        // Renames the dir before deleting it. See b/20096164 for detail.
        androidFileUtil.renameFiles(deviceId, deviceGooglePath, tmpDeviceGooglePath);
        androidFileUtil.removeFiles(deviceId, tmpDeviceGooglePath);
      }
    } catch (MobileHarnessException e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_INSTRUMENTATION_CLEAN_TEST_STORAGE_ERROR, e.getMessage()),
              logger);
    }
    return Optional.ofNullable(externalStoragePath);
  }

  /**
   * Disable isolated-storage feature for APK which will fall back to P stage. This API works for
   * both root and non-root device
   */
  public void enableLegacyStorageForApk(String deviceId, String packageName)
      throws MobileHarnessException, InterruptedException {
    try {
      boolean isIsolatedStorageEnabled = settingUtil.isIsolatedStorageEnabled(deviceId);
      if (isIsolatedStorageEnabled) {
        settingUtil.setPackageLegacyStorageMode(deviceId, packageName, /* enable= */ true);
      } else {
        logger.atInfo().log(
            "Skip setting package %s to legacy storage mode, since system isolated storage is not "
                + "enabled.",
            packageName);
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_ENABLE_LEGACY_STORAGE_ERROR,
          String.format("Failed to enable legacy storage feature for package %s", packageName),
          e);
    }
  }

  /** Gets the build apks used in an instrumentation. */
  public ImmutableList<String> getBuildApks(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    AndroidInstrumentationSpec androidInstrumentationSpec;
    try {
      androidInstrumentationSpec =
          testInfo.jobInfo().combinedSpecOfClass(AndroidInstrumentationSpec.class);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_SPEC_PARSE_ERROR,
          "Failed to parse Android Instrumentation Spec",
          e);
    }
    return androidInstrumentationSpec.getBuildApkList().stream()
        .flatMap(file -> file.getOutputList().stream())
        .distinct()
        .collect(toImmutableList());
  }

  /** Gets test args for Android instrumentation. */
  public ImmutableMap<String, String> getTestArgs(TestInfo testInfo) throws MobileHarnessException {
    return ImmutableMap.<String, String>builder()
        .putAll(
            StrUtil.toMap(
                testInfo.jobInfo().params().get(AndroidInstrumentationDriverSpec.PARAM_TEST_ARGS),
                /* allowDelimiterInValue= */ true))
        .putAll(getTestSpecificTestArgs(testInfo))
        .put(
            TEST_ARG_GEN_FILE_DIR_RELATIVE_PATH,
            PathUtil.makeRelative(
                testInfo.jobInfo().setting().getGenFileDir(), testInfo.getGenFileDir()))
        .buildOrThrow();
  }

  /**
   * Gets the test runner used in an instrumentation.
   *
   * @param analyzeApk if true, will use "{@code aapt dump xmltree}" to read AndroidManifest.xml in
   *     the test APK; if false, will use "{@code adb shell pm list instrumentation}" instead
   */
  public String getTestRunnerClassName(
      TestInfo testInfo,
      String serial,
      String testPackageName,
      String testApkPath,
      boolean analyzeApk)
      throws MobileHarnessException, InterruptedException {
    if (testInfo.jobInfo().params().has(AndroidInstrumentationDriverSpec.PARAM_RUNNER)) {
      return testInfo.jobInfo().params().get(AndroidInstrumentationDriverSpec.PARAM_RUNNER);
    }
    // If the runner is not specified, uses the first runner defined in the test apk.
    try {
      List<String> instrumentations;
      if (analyzeApk) {
        instrumentations = apkAnalyzer.getTestApkTestRunnerClassName(testApkPath);
        if (instrumentations.isEmpty()) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_INSTRUMENTATION_RUNNER_NOT_FOUND_FOR_PKG,
              "Can not find instrumentation test runners in test apk " + testApkPath);
        }
      } else {
        instrumentations = listInstrumentations(serial, testPackageName);
      }

      String runnerName = instrumentations.get(0);
      String output =
          instrumentations.size() > 1
              ? String.format(
                  "Get the instrumentation test runners of the test apk: \n - %s "
                      + "\nUse the first runner by default.",
                  Joiner.on("\n - ").join(instrumentations))
              : String.format(
                  "Use the instrumentation test runner of the test apk: %s", runnerName);
      testInfo.log().atInfo().alsoTo(logger).log("%s", output);
      return runnerName;
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_RUNNER_NOT_FOUND,
          "Instrumentation test runner not found",
          e);
    }
  }

  /**
   * Gets test specific test_args. Note it is only meaningful with AndroidInstrumentation driver.
   *
   * @return immutable map
   */
  public Map<String, String> getTestSpecificTestArgs(TestInfo testInfo) {
    return StrUtil.toMap(
        testInfo.properties().get(AndroidInstrumentation.TEST_SPECIFIC_TEST_ARGS),
        getEntryDelimiter(testInfo.jobInfo()),
        StrUtil.DEFAULT_KEY_VALUE_DELIMITER,
        true);
  }

  /**
   * Gets the instrumentation test runners of the given package in the device. The test apk should
   * be installed before using this method.
   *
   * <p>It lists all test packages across multi-user, as "pm list instrumentation" doesn't accept
   * --user.
   *
   * @param serial serial number of the device
   * @param packageName the Android package name of the test package, which is defined in the test
   *     package's manifest file.
   * @return a list of the instrumentation test runner, never be null or empty
   * @throws MobileHarnessException if fail to get the instrumentation test runners of the given
   *     package from the device
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public List<String> listInstrumentations(String serial, String packageName)
      throws MobileHarnessException, InterruptedException {
    ImmutableList.Builder<String> instrBuilder = ImmutableList.builder();
    String output;
    try {
      output = adb.runShellWithRetry(serial, ADB_SHELL_LIST_INSTRUMENTATION);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_LIST_INSTRUMENTATION_ERROR, e.getMessage(), e);
    }
    // Sample output:
    // instrumentation:com.google...utilities/.AddAccount (target=com.google....utilities)
    // instrumentation:com.google...test/android.test.InstrumentationTestRunner (target=...)
    String prefix = OUTPUT_INSTRUMENTATION_PREFIX + packageName + "/";
    int startIdx = prefix.length();
    int endIdx;
    for (String line : Splitters.LINE_SPLITTER.split(output)) {
      if (line.startsWith(prefix)
          && (endIdx = line.indexOf(OUTPUT_INSTRUMENTATION_SUFFIX)) > startIdx) {
        instrBuilder.add(line.substring(startIdx, endIdx));
      }
    }
    ImmutableList<String> instrumentations = instrBuilder.build();
    if (instrumentations.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_RUNNER_NOT_FOUND_FOR_PKG,
          String.format(
              "Can not find the instrumentation test runners for package %s, command_output=[%s]",
              packageName, output));
    }
    return instrumentations;
  }

  /** Prepares basic services apk, test services apk, and orchestrator apk on the device. */
  public void prepareServicesApks(
      TestInfo testInfo,
      Device device,
      int deviceSdkVersion,
      @Nullable AppInstallStartHandler appInstallStartHandler,
      @Nullable AppInstallFinishHandler appInstallFinishHandler)
      throws MobileHarnessException, InterruptedException {
    // Installs the basic_services.apk to support AndroidTestUtil methods.
    String basicServiceApkPath =
        testInfo
                .jobInfo()
                .files()
                .get(AndroidInstrumentationDriverSpec.TAG_BASIC_SERVICES_APK)
                .isEmpty()
            ? getResourceFile(AndroidInstrumentationDriverSpec.BASIC_SERVICE_APK_PATH)
            : testInfo
                .jobInfo()
                .files()
                .getSingle(AndroidInstrumentationDriverSpec.TAG_BASIC_SERVICES_APK);
    String basicServicePackageName = aapt.getApkPackageName(basicServiceApkPath);
    ApkInstallArgs basicServiceApkInstallArgs =
        createServiceApkInstallArgs(basicServiceApkPath, deviceSdkVersion, testInfo);
    installServiceApk(
        device,
        basicServiceApkInstallArgs,
        testInfo,
        basicServicePackageName,
        appInstallStartHandler,
        appInstallFinishHandler);

    // Installs the test_services.apk to support AndroidTestUtil methods.
    String testServicesApkPath =
        testInfo
                .jobInfo()
                .files()
                .get(AndroidInstrumentationDriverSpec.TAG_TEST_SERVICES_APK)
                .isEmpty()
            ? getResourceFile(AndroidInstrumentationDriverSpec.TEST_SERVICES_APK_PATH)
            : testInfo
                .jobInfo()
                .files()
                .getSingle(AndroidInstrumentationDriverSpec.TAG_TEST_SERVICES_APK);
    String testServicePackageName = aapt.getApkPackageName(testServicesApkPath);
    ApkInstallArgs testServicesApkInstallArgs =
        createServiceApkInstallArgs(testServicesApkPath, deviceSdkVersion, testInfo);
    installServiceApk(
        device,
        testServicesApkInstallArgs,
        testInfo,
        testServicePackageName,
        appInstallStartHandler,
        appInstallFinishHandler);

    // Install the orchestrator apk if it is set.
    if (!testInfo
        .jobInfo()
        .files()
        .get(AndroidInstrumentationDriverSpec.TAG_ORCHESTRATOR_APK)
        .isEmpty()) {
      var orchestratorApkPath =
          testInfo
              .jobInfo()
              .files()
              .getSingle(AndroidInstrumentationDriverSpec.TAG_ORCHESTRATOR_APK);
      var orchestratorPackageName = aapt.getApkPackageName(orchestratorApkPath);
      var orchestratorApkInstallArgs =
          createServiceApkInstallArgs(orchestratorApkPath, deviceSdkVersion, testInfo);
      installServiceApk(
          device,
          orchestratorApkInstallArgs,
          testInfo,
          orchestratorPackageName,
          appInstallStartHandler,
          appInstallFinishHandler);
    }

    if (testInfo
        .jobInfo()
        .params()
        .getBool(AndroidInstrumentationDriverSpec.PARAM_DISABLE_ISOLATED_STORAGE_FOR_APK, true)) {
      // Disable isolated-storage for basic service apk which provides content provider to test.
      enableLegacyStorageForApk(device.getDeviceId(), basicServicePackageName);
      if (deviceSdkVersion >= AndroidVersion.ANDROID_11.getStartSdkVersion()) {
        // Enable MANAGE_EXTERNAL_STORAGE for test service apk to grant AndroidTestUtil permission
        // to access files (b/170517865)
        enableManageExternalStorageForApk(device.getDeviceId(), testServicePackageName);
      }
    }
  }

  private void installServiceApk(
      Device device,
      ApkInstallArgs apkInstallArgs,
      TestInfo testInfo,
      String packageName,
      @Nullable AppInstallStartHandler appInstallStartHandler,
      @Nullable AppInstallFinishHandler appInstallFinishHandler)
      throws MobileHarnessException, InterruptedException {
    if (appInstallStartHandler != null) {
      appInstallStartHandler.onAppInstallStart(testInfo, packageName);
    }
    apkInstaller.installApk(device, apkInstallArgs, testInfo.log());
    if (appInstallFinishHandler != null) {
      appInstallFinishHandler.onAppInstallFinish(testInfo, packageName);
    }
  }

  private ApkInstallArgs createServiceApkInstallArgs(
      String apkPath, int deviceSdkVersion, TestInfo testInfo) {
    ApkInstallArgs.Builder builder =
        ApkInstallArgs.builder()
            .setApkPath(apkPath)
            .setGrantPermissions(true)
            .setSkipDowngrade(false);

    /*
     * Install (or force a re-install for) basic services and test services app depending on
     * AndroidInstrumentationDriverSpec.PARAM_FORCE_REINSTALL_BASIC_SERVICE_APK. Some other apps
     * will access test files via this service app by ContentProvider in last test run. Forcing
     * service app reinstall will break such access to make it easier for cleaning the googletest
     * folder later. Related story: b/20096164 (hard to clean), b/24283449 (caused by cleaning
     * failing in MNC).
     */
    if (testInfo
        .jobInfo()
        .params()
        .getBool(
            AndroidInstrumentationDriverSpec.PARAM_FORCE_REINSTALL_BASIC_SERVICE_APK,
            DEFAULT_FORCE_REINSTALL_BASIC_SERVICE_APK)) {
      builder.setSkipIfCached(false);
    }
    if (deviceSdkVersion >= AndroidVersion.ANDROID_11.getStartSdkVersion()) {
      builder.setForceQueryable(true);
    }
    return builder.build();
  }

  /**
   * Makes the given test args accessible to {@code
   * com.google.android.apps.common.testing.util.AndroidTestUtil#getTestArgs} from the device.
   * Relies on {@code androidx.test.services.storage.TestArgsContentProvider} being used in the test
   * runner.
   *
   * @param params the parameters for preparing test args
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void prepareTestArgs(PrepareTestArgsParams params)
      throws MobileHarnessException, InterruptedException {
    String serial = params.serial();
    ImmutableMap<String, String> testArgs = params.testArgs();
    @Nullable String deviceExternalStoragePath = params.deviceExternalStoragePath().orElse(null);
    String hostTmpFileDir = params.hostTmpFileDir();
    Log log = params.log();
    Warnings warnings = params.warnings();
    boolean forceAdbPush = params.forceAdbPush();
    boolean skipClearMediaProviderForMultiUserCase =
        params.skipClearMediaProviderForMultiUserCase();

    // An empty line to separate the log.
    log.append("\n");
    if (deviceExternalStoragePath == null) {
      if (!testArgs.isEmpty()) {
        warnings.addAndLog(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_INSTRUMENTATION_TEST_ARGS_NOT_SET,
                "Test arg not set because no writable external storage"),
            logger);
      }
      // No external storage, no action required.
      return;
    }

    // Creates new test arg files if specified.
    if (!testArgs.isEmpty()) {
      TestArgumentsPb.Builder builder = TestArgumentsPb.newBuilder();
      for (Entry<String, String> arg : testArgs.entrySet()) {
        builder.addArg(
            TestArgumentPb.newBuilder().setName(arg.getKey()).setValue(arg.getValue()).build());
      }
      TestArgumentsPb testArgsPb = builder.build();

      String hostFilePath = PathUtil.join(hostTmpFileDir, ANDROID_TEST_TEST_ARGS_FILE_NAME);
      log.atInfo().alsoTo(logger).log("Create test args file: \n%s", testArgsPb.toString().trim());
      try {
        localFileUtil.writeToFile(hostFilePath, testArgsPb.toByteArray());
        log.atInfo()
            .alsoTo(logger)
            .log("Push test args file to device (forceAdbPush: %s)", forceAdbPush);
        int sdkVersion = getDeviceSdkVersion(warnings, serial);

        // Multi user special case, try to use "adb push" to push "test_args.dat" file to device
        // instead of using "content write", see AndroidFileUtil#getExternalStoragePath.
        boolean isMultiUserSpecialCase =
            deviceExternalStoragePath.startsWith(
                AndroidFileUtil.MULTI_USER_EXTERNAL_STORAGE_PATH_PREFIX);

        // "adb shell content write" is only available from Android Q.
        if (sdkVersion > AndroidVersion.PI.getEndSdkVersion()
            && !isMultiUserSpecialCase
            && !forceAdbPush) {
          // ContentProvider for FileHost.TEST_ARGS does not support read/write.
          // Use TestStorageConstants.INTERNAL_USE_PROVIDER_AUTHORITY + TEST_ARGS_FILE_NAME
          // which point to the same folder.
          String testArgsContentUri =
              String.format(
                  "content://%s/%s",
                  TestStorageConstants.INTERNAL_USE_PROVIDER_AUTHORITY,
                  ANDROID_TEST_TEST_ARGS_FILE_NAME);
          contentWrite(
              serial,
              testArgsContentUri,
              getCurrentUser(serial, sdkVersion).orElse(null),
              hostFilePath);
        } else {
          // Fall back to "adb push" for Android Q- and multi-user special case.
          if (sdkVersion == 0) {
            // Try to get device SDK version again
            sdkVersion = settingUtil.getDeviceSdkVersion(serial);
          }
          String deviceFilePath =
              PathUtil.join(
                  deviceExternalStoragePath,
                  ANDROID_TEST_DEVICE_PATH_INTERNAL_USE + ANDROID_TEST_TEST_ARGS_FILE_NAME);
          androidFileUtil.push(serial, sdkVersion, hostFilePath, deviceFilePath);
          if (isMultiUserSpecialCase && !skipClearMediaProviderForMultiUserCase) {
            // Based on b/406931839#comment3, try to force sync. But for case in b/447394794, users
            // want to skip this step.
            String forceSyncCommand = "pm clear com.google.android.providers.media.module";
            try {
              String output = adb.runShell(serial, forceSyncCommand);
              logger.atInfo().log(
                  "Force sync by running '%s' output: %s", forceSyncCommand, output);
              // Give MediaProvider time to reindex the newly pushed test args file before the
              // instrumentation test starts.
              logger.atInfo().log(
                  "Sleeping for %d seconds for MediaProvider to reindex files...",
                  MEDIA_PROVIDER_REINDEX_DELAY.toSeconds());
              sleeper.sleep(MEDIA_PROVIDER_REINDEX_DELAY);
            } catch (MobileHarnessException e) {
              logger.atWarning().log(
                  "Failed to force sync by running '%s':%s",
                  forceSyncCommand, MoreThrowables.shortDebugString(e));
            }
          }
        }
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_INSTRUMENTATION_PREPARE_TEST_ARGS_ERROR,
            "Failed to create test args file: " + e.getMessage(),
            e);
      }
    }
  }

  /**
   * Processes the test instrument output files generated by {@code
   * AndroidTestUtil.getTestOutputStream()}. Pulls them out from device and will be sent to Sponge
   * at the end of the whole processing workflow.
   *
   * @param testInfo The target {@code testInfo} of this test
   * @param deviceId The related Android device
   * @param externalStoragePath External storage path of the generated files
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException InterruptedException when adb pull gets interrupted
   * @since Lab Server 4.68.0
   */
  public void processInstrumentOutputFiles(
      TestInfo testInfo, String deviceId, String externalStoragePath)
      throws InterruptedException, MobileHarnessException {
    processInstrumentOutputAndCoverageFiles(
        testInfo, deviceId, externalStoragePath, /* isCoverageFile= */ false);
  }

  /**
   * Processes the test instrument coverage files generated when coverage is enabled. Pulls them out
   * from device and will be sent to Sponge at the end of the whole processing workflow.
   *
   * @param testInfo The target {@code testInfo} of this test
   * @param deviceId The related Android device
   * @param externalStoragePath External storage path of the generated files
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException InterruptedException when adb pull gets interrupted
   */
  public void processInstrumentCoverageFiles(
      TestInfo testInfo, String deviceId, String externalStoragePath)
      throws InterruptedException, MobileHarnessException {
    processInstrumentOutputAndCoverageFiles(
        testInfo, deviceId, externalStoragePath, /* isCoverageFile= */ true);
  }

  /**
   * Processes the test instrument files, including test output files and test coverage files
   * generated by {@code AndroidTestUtil.getTestOutputStream()}, controlled by {@code
   * isCoverageFile} to indicate whether to process coverage files or output files. Pulls them out
   * from device and will be sent to Sponge at the end of the whole processing workflow.
   *
   * @param testInfo The target {@code testInfo} of this test
   * @param deviceId The related Android device
   * @param externalStoragePath External storage path of the generated files
   * @param isCoverageFile Whether to process coverage files or output files
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException InterruptedException when adb pull gets interrupted
   */
  private void processInstrumentOutputAndCoverageFiles(
      TestInfo testInfo, String deviceId, String externalStoragePath, boolean isCoverageFile)
      throws InterruptedException, MobileHarnessException {
    if (isNullExternalStoragePath(testInfo, externalStoragePath)) {
      return;
    }

    int deviceSdkVersion = getDeviceSdkVersion(testInfo.warnings(), deviceId);
    if (deviceSdkVersion == 0) {
      return;
    }

    String tmpHostDir = PathUtil.join(testInfo.getTmpFileDir(), "pull_" + UUID.randomUUID());
    boolean hasFile;
    try {
      if (deviceSdkVersion > AndroidVersion.PI.getEndSdkVersion()) {
        // Use content read for Android Q and above.
        hasFile =
            contentReadFolder(
                deviceId,
                String.format(
                    "content://%s",
                    isCoverageFile
                        ? TestStorageConstants.INTERNAL_USE_PROVIDER_AUTHORITY
                        : TestStorageConstants.TEST_OUTPUT_PROVIDER_AUTHORITY),
                tmpHostDir,
                /* subPath= */ null,
                getCurrentUser(deviceId, deviceSdkVersion).orElse(null));
      } else {
        // Use adb pull for Android Q-.
        // For Android P-, there is no isolated-storage feature, so no need to check sandbox folder.
        String devicePath =
            PathUtil.join(
                externalStoragePath,
                "/Android/data/androidx.test.services/files/",
                isCoverageFile
                    ? ANDROID_TEST_DEVICE_PATH_INTERNAL_USE
                    : ANDROID_TEST_DEVICE_PATH_TEST_OUTPUT);
        // Do not create the host folder before pull files.
        // Pulls the device output folder /sdcard/googletest/test_outputfiles/, to new TMP_FILE host
        // folder which doesn't exist, to make sure it doesn't create the sub-directory
        // test_outputfiles under it. See b/32065697.
        hasFile =
            pullInstrumentFiles(
                testInfo, deviceId, devicePath, tmpHostDir, /* shouldCreateFolder= */ false);
      }

      if (!hasFile) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Skip pulling instrumentation files from device %s as directory doesn't exist,"
                    + " isCoverageFile: %s",
                deviceId, isCoverageFile);
        return;
      }

      String desHostDir = testInfo.getGenFileDir();
      // Moves the content of the tmp dir to test GEN_FILE dir.
      for (File subFileOrDir : localFileUtil.listFilesOrDirs(tmpHostDir)) {
        String subFileOrDirPath = subFileOrDir.getPath();
        if (!isCoverageFile || subFileOrDirPath.endsWith(".ec")) {
          localFileUtil.moveFileOrDir(subFileOrDirPath, desHostDir);
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Moved test output file/dir %s to GEN_FILE dir %s, isCoverageFile: %s",
                  subFileOrDirPath, desHostDir, isCoverageFile);
        }
      }
      // Leaves the tmp dir alone, MH framework will clean it up after the test.
    } catch (MobileHarnessException e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  isCoverageFile
                      ? AndroidErrorId.ANDROID_INSTRUMENTATION_PROCESS_TEST_COVERAGE_FILE_ERROR
                      : AndroidErrorId.ANDROID_INSTRUMENTATION_PROCESS_TEST_OUTPUT_ERROR,
                  "Failed to pull test output file from device: " + e.getMessage(),
                  e),
              logger);
      // Fail the test if it's a coverage file pulling failure.
      if (isCoverageFile) {
        testInfo.resultWithCause().setNonPassing(TestResult.ERROR, e);
      }
    }
  }

  /**
   * Processes the test instrument property files generated by {@code
   * AndroidTestUtil.addStatsToSponge()}. This will be pulled out from the device and then parsed to
   * Sponge as properties at the end of the whole processing workflow.
   *
   * @param testInfo The target {@code testInfo} of this test
   * @param deviceId The related Android device
   * @param externalStoragePath External storage path of the generated files
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException InterruptedException when adb pull gets interrupted
   * @since Lab Server 4.68.0
   */
  public void processInstrumentPropertyFiles(
      TestInfo testInfo, String deviceId, String externalStoragePath)
      throws MobileHarnessException, InterruptedException {
    if (isNullExternalStoragePath(testInfo, externalStoragePath)) {
      return;
    }

    int deviceSdkVersion = getDeviceSdkVersion(testInfo.warnings(), deviceId);
    if (deviceSdkVersion == 0) {
      return;
    }

    String hostPropertyDir =
        PathUtil.join(testInfo.getTmpFileDir(), ANDROID_TEST_DEVICE_PATH_TEST_PROPERTIES);
    boolean hasOutputFile;
    try {
      if (deviceSdkVersion > AndroidVersion.PI.getEndSdkVersion()) {
        // Use content read for Android Q.
        hasOutputFile =
            contentReadFolder(
                deviceId,
                String.format(
                    "content://%s", TestStorageConstants.OUTPUT_PROPERTIES_PROVIDER_AUTHORITY),
                hostPropertyDir,
                /* subPath= */ null,
                getCurrentUser(deviceId, deviceSdkVersion).orElse(null));
      } else {
        // Use adb pull for Android Q-.
        // For Android P-, there is no isolated-storage feature, so no need to check sandbox folder.
        String devicePath =
            PathUtil.join(
                externalStoragePath,
                "/Android/data/androidx.test.services/files/",
                ANDROID_TEST_DEVICE_PATH_TEST_PROPERTIES);
        hasOutputFile =
            pullInstrumentFiles(
                testInfo, deviceId, devicePath, hostPropertyDir, /* shouldCreateFolder= */ true);
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_PULL_TEST_PROPERTY_ERROR,
          String.format("Failed to pull property files from device %s", deviceId),
          e);
    }

    if (!hasOutputFile) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Skip pulling instrumentation property files from device %s as output doesn't exist",
              deviceId);
      return;
    }

    for (File propertyFile : localFileUtil.listFiles(hostPropertyDir, true)) {
      try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(propertyFile))) {
        @SuppressWarnings("unchecked") // safe by specification
        Map<String, Serializable> properties = (Map<String, Serializable>) in.readObject();
        for (Entry<String, Serializable> property : properties.entrySet()) {
          testInfo.properties().add(property.getKey(), property.getValue().toString());
        }
      } catch (IOException | ClassNotFoundException e) {
        testInfo
            .warnings()
            .addAndLog(
                new MobileHarnessException(
                    AndroidErrorId.ANDROID_INSTRUMENTATION_PROCESS_TEST_PROPERTY_ERROR,
                    "Failed to parse test property file "
                        + propertyFile.getPath()
                        + ": "
                        + e.getMessage(),
                    e),
                logger);
      }
    }
  }

  public void pullInstrumentationFilesFromDevice(
      String deviceId, TestInfo testInfo, @Nullable String externalStoragePath)
      throws InterruptedException {
    // b/132998532. Device may be disconnected within instrumentation test, so ensure device is
    // online before pulling files from it.
    boolean isDeviceOnline = false;
    try {
      isDeviceOnline =
          RetryingCallable.newBuilder(
                  () -> {
                    boolean isOnline = false;
                    try {
                      isOnline = systemStateManager.isOnline(deviceId);
                    } catch (MobileHarnessException e) {
                      throw new MobileHarnessException(
                          AndroidErrorId.ANDROID_INSTRUMENTATION_GET_ONLINE_DEVICES_ERROR,
                          e.getMessage(),
                          e);
                    }
                    if (!isOnline) {
                      throw new MobileHarnessException(
                          AndroidErrorId.ANDROID_INSTRUMENTATION_GET_ONLINE_DEVICES_ERROR,
                          String.format("Device %s is not online", deviceId));
                    }
                    return true;
                  },
                  RetryStrategy.exponentialBackoff(
                      DEVICE_RECONNECT_INITIAL_DELAY,
                      DEVICE_RECONNECT_DELAY_MULTIPLIER,
                      DEVICE_RECONNECT_MAX_RETRIES))
              .setPredicate(e -> e instanceof MobileHarnessException)
              .build()
              .call();
      if (isDeviceOnline) {
        processInstrumentOutputFiles(testInfo, deviceId, externalStoragePath);
        processInstrumentPropertyFiles(testInfo, deviceId, externalStoragePath);

        // Only process coverage files when the enable_coverage is true.
        if (testInfo
            .jobInfo()
            .params()
            .getBool(AndroidInstrumentationDriverSpec.PARAM_ENABLE_COVERAGE, false)) {
          processInstrumentCoverageFiles(testInfo, deviceId, externalStoragePath);
        }
      } else {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Device %s is not online after %d retries, skip pulling instrumentation files from"
                    + " it",
                deviceId, DEVICE_RECONNECT_MAX_RETRIES);
      }
    } catch (RetryException | MobileHarnessException e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_INSTRUMENTATION_PULL_FILE_ERROR,
                  String.format(
                      "Failed to check if device %s is online after %d retries",
                      deviceId, DEVICE_RECONNECT_MAX_RETRIES),
                  e),
              logger);
    }
  }

  /**
   * Pushes the test data to device.
   *
   * <p>The test data is specified by the {@code AndroidInstrumentationDriverSpec.TAG_TEST_DATA}. It
   * should be rooted at google3.
   *
   * <p>The test data can be accessed by {@code AndroidTestUtil.getTestDataInputStream}.
   *
   * @param testInfo The target {@code testInfo} of this test
   * @param deviceId The related Android device
   * @param testDataFiles Data files to be pushed to device for this test
   * @param externalStoragePath External storage path of the generated files
   * @throws InterruptedException InterruptedException when adb pull gets interrupted
   */
  public void pushTestData(
      TestInfo testInfo,
      String deviceId,
      Set<String> testDataFiles,
      @Nullable String externalStoragePath)
      throws InterruptedException {
    if (isNullExternalStoragePath(testInfo, externalStoragePath)) {
      return;
    }

    int deviceSdkVersion = getDeviceSdkVersion(testInfo.warnings(), deviceId);
    if (deviceSdkVersion == 0) {
      return;
    }

    for (String testDataPathOnHost : testDataFiles) {
      try {
        // Gets Google3 relative path.
        int lastIndex = testDataPathOnHost.lastIndexOf("/google3/");
        if (lastIndex < 0) {
          testInfo
              .warnings()
              .addAndLog(
                  new MobileHarnessException(
                      AndroidErrorId.ANDROID_INSTRUMENTATION_INVALID_TEST_DATA,
                      testDataPathOnHost + " is not a google3 data file/dir, ignored"),
                  logger);
          continue;
        }

        String testDataRelativePath = testDataPathOnHost.substring(lastIndex);

        // Pushing test data thru "adb push" until bug (b/128948778) fixed.
        // Currently androidx.test.services.storage intend to disable insert/update operation.
        // See
        // third_party/android/androidx_test/services/storage/java/androidx/test/services/storage/provider/AbstractFileContentProvider.java.
        if (deviceSdkVersion > MAGIC_SDK_VERSION) {
          String contentUri =
              String.format(
                  "content://%s%s",
                  TestStorageConstants.TEST_RUNFILES_PROVIDER_AUTHORITY, testDataRelativePath);
          contentWrite(
              deviceId,
              contentUri,
              getCurrentUser(deviceId, deviceSdkVersion).orElse(null),
              testDataPathOnHost);
        } else {
          String testDataDirOnDevice =
              PathUtil.join(externalStoragePath, ANDROID_TEST_DEVICE_PATH_TEST_RUNFILES);
          // Pushes the file.
          String output =
              androidFileUtil.push(
                  deviceId,
                  deviceSdkVersion,
                  testDataPathOnHost,
                  PathUtil.join(testDataDirOnDevice, testDataRelativePath));
          logger.atInfo().log(
              "Push test data to %s:\n %s",
              PathUtil.join(testDataDirOnDevice, testDataRelativePath), output);
        }
      } catch (MobileHarnessException e) {
        testInfo
            .warnings()
            .addAndLog(
                new MobileHarnessException(
                    AndroidErrorId.ANDROID_INSTRUMENTATION_PUSH_TEST_DATA_ERROR, e.getMessage(), e),
                logger);
      }
    }
  }

  /**
   * Checks if need to show raw results for the "am instrument" command call.
   *
   * @param testInfo The target {@code testInfo} of this test
   */
  public boolean showRawResultsIfNeeded(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    boolean showRawResults = false;
    if (testInfo.jobInfo().params().isTrue(AndroidInstrumentationDriverSpec.PARAM_SPLIT_METHODS)) {
      testInfo
          .properties()
          .add(
              SplitMethodSpec.PROPERTY_METHOD_DELIMITER,
              AndroidInstrumentationDriverSpec.METHODS_DELIMITER);
      showRawResults = true;
    }
    return showRawResults;
  }

  /**
   * Gets the test result from the testInfo and check if the individual test cases passed.
   *
   * @param deviceId The related Android device
   * @param testInfo The target {@code testInfo} of this test
   * @param gtestXmlFile The gtest XML file path with the file name (e.g.
   *     /sdcard/Download/test_results.xml)
   * @param exception returns the exception after parsing the gtest XML files
   */
  public GtestResult processGtestResult(
      String deviceId,
      TestInfo testInfo,
      String gtestXmlFile,
      @Nullable MobileHarnessException exception)
      throws InterruptedException {
    // Handle the CommandException exception first
    if (exception != null) {
      if (exception.getErrorId() == AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_EXEC_TIMEOUT) {
        return GtestResult.of(TestResult.TIMEOUT, Optional.of(exception.getMessage()));
      } else {
        return GtestResult.of(TestResult.ERROR, Optional.of(exception.getMessage()));
      }
    }

    // Ensure device is online before pulling files from it.
    try {
      boolean isDeviceOnline = systemStateManager.isOnline(deviceId);
      if (!isDeviceOnline) {
        return GtestResult.of(
            TestResult.ERROR, Optional.of(String.format("Device %s is not online", deviceId)));
      }
    } catch (MobileHarnessException e) {
      return GtestResult.of(TestResult.ERROR, Optional.of(e.getMessage()));
    }

    boolean hasTestFailures =
        testInfo.subTests().getAll().values().stream()
            .anyMatch(AndroidInstrumentationUtil::isTestFailedOrError);
    if (hasTestFailures) {
      return GtestResult.of(TestResult.FAIL, Optional.of("Test cases failed."));
    }

    // The tests passed.
    return GtestResult.of(TestResult.PASS, Optional.empty());
  }

  private static boolean isTestFailedOrError(TestInfo subTest) {
    return subTest.resultWithCause().get().type() == Test.TestResult.FAIL
        || subTest.resultWithCause().get().type() == Test.TestResult.ERROR;
  }

  /** Check the external storage path is not null. */
  private static boolean isNullExternalStoragePath(TestInfo testInfo, String externalStoragePath) {
    if (externalStoragePath == null) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_INSTRUMENTATION_EXTERNAL_STORAGE_NOT_FOUND,
                  "No writable external storage"),
              logger);
      return true;
    }
    return false;
  }

  /**
   * Content read does not support folder, so recursively query and read a folder until a regular
   * file.
   */
  private boolean contentReadFolder(
      String deviceId,
      String contentUri,
      String tmpHostDir,
      @Nullable String subPath,
      @Nullable String userId)
      throws MobileHarnessException, InterruptedException {
    boolean hasOutput = false;
    String newUri = String.format("%s%s", contentUri, Strings.nullToEmpty(subPath));

    String output = contentQuery(deviceId, newUri, userId);
    if (output.contains(CONTENT_QUERY_OUTPUT_NO_RESULT)) {
      return false;
    }
    logger.atInfo().log("Query content %s output: %s", contentUri, output);

    for (Entry<String, String> entry : parseContentQueryResult(output).entrySet()) {
      if (entry.getValue().equals(AndroidTestContent.FileType.DIRECTORY.getType())) {
        // If it is a folder, recursively read that folder.
        hasOutput |= contentReadFolder(deviceId, contentUri, tmpHostDir, entry.getKey(), userId);
      } else {
        String tmpContentUri = contentUri + entry.getKey();
        String newHostPath = PathUtil.join(tmpHostDir, entry.getKey());
        String parentPath = PathUtil.dirname(newHostPath);
        try {
          // Create the folder if it is not there.
          localFileUtil.prepareDir(parentPath);
          output = contentRead(deviceId, tmpContentUri, userId, newHostPath);
          logger.atInfo().log("ContentRead output: %s", output);
          hasOutput = true;
        } catch (MobileHarnessException e) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_INSTRUMENTATION_CONTENT_READ_FOLDER_ERROR,
              String.format("Failed to read content %s from folder.", tmpContentUri),
              e);
        }
      }
    }
    return hasOutput;
  }

  /**
   * Simple version of "adb shell content read" with output redirect to host file.
   *
   * @param deviceId device serial number
   * @param uri uri address for content
   * @param userId which user to read the content
   * @param desFilePath file path on host machine as output
   * @return output of command execution
   */
  private String contentRead(
      String deviceId, String uri, @Nullable String userId, String desFilePath)
      throws MobileHarnessException, InterruptedException {
    String adbPath = adb.getAdbPath();
    String contentCommand =
        String.format("content read --uri %s ", uri)
            + (userId != null ? String.format("--user %s", userId) : "");

    // Redirect the output directly into a file in host machine which is recommended way.
    // See b/126805032 for detail.
    String shellCommand =
        String.format("%s -s %s shell %s > %s", adbPath, deviceId, contentCommand, desFilePath);
    try {
      return commandExecutor.run(Command.of("bash", "-c", shellCommand)).trim();
    } catch (CommandException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_CONTENT_READ_ERROR,
          String.format("Failed to read content with command: %s", shellCommand),
          e);
    }
  }

  /**
   * Simple version of "adb shell content write" with input redirect from host file.
   *
   * @param deviceId device serial number
   * @param uri uri address for content
   * @param userId which user to read the content
   * @param srcFilePath file path on host machine as input
   */
  private void contentWrite(
      String deviceId, String uri, @Nullable String userId, String srcFilePath)
      throws MobileHarnessException, InterruptedException {
    String adbPath = adb.getAdbPath();
    String contentCommand =
        String.format("content write --uri %s ", uri)
            + (userId != null ? String.format("--user %s", userId) : "");

    // Redirect the input directly from a file in host machine which is recommended way.
    // See b/126805032 for detail.
    String shellCommand =
        String.format("%s -s %s shell %s < %s", adbPath, deviceId, contentCommand, srcFilePath);
    try {
      String output = commandExecutor.run(Command.of("bash", "-c", shellCommand));
      logger.atInfo().log("Executed command [%s]: [%s]", shellCommand, output);
    } catch (CommandException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_CONTENT_WRITE_ERROR,
          String.format("Failed to write content with command: %s", shellCommand),
          e);
    }
  }

  /**
   * Simple version of "adb shell content query".
   *
   * @param deviceId device serial number
   * @param uri uri address for content
   * @param userId which user to read the content
   * @return output of command execution
   */
  private String contentQuery(String deviceId, String uri, @Nullable String userId)
      throws MobileHarnessException, InterruptedException {
    String contentCommand =
        String.format("content query --uri %s ", uri)
            + (userId != null ? String.format("--user %s", userId) : "");

    try {
      return adb.runShell(deviceId, contentCommand);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_CONTENT_QUERY_ERROR,
          String.format("Failed to query content with command: %s", contentCommand),
          e);
    }
  }

  /**
   * Enables MANAGE_EXTERNAL_STORAGE for package. This API only works for both root and non-root
   * device with SDK >= 30.
   */
  @VisibleForTesting
  void enableManageExternalStorageForApk(String deviceId, String packageName)
      throws MobileHarnessException, InterruptedException {
    try {
      Optional<AppOperationMode> appOperationMode =
          settingUtil.getPackageOperationMode(
              deviceId, packageName, APP_OP_MANAGE_EXTERNAL_STORAGE);
      logger.atInfo().log(
          "App operation mode for %s on device %s is: %s",
          APP_OP_MANAGE_EXTERNAL_STORAGE, deviceId, appOperationMode);
      if (!AppOperationMode.ALLOW.equals(appOperationMode.orElse(null))) {
        settingUtil.setPackageOperationMode(
            deviceId, packageName, APP_OP_MANAGE_EXTERNAL_STORAGE, AppOperationMode.ALLOW);
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_ENABLE_MANAGE_EXTERNAL_STORAGE_ERROR,
          String.format(
              "Failed to enable %s for package %s", APP_OP_MANAGE_EXTERNAL_STORAGE, packageName),
          e);
    }
  }

  private Optional<String> getCurrentUser(String deviceId, @Nullable Integer deviceSdkVersion)
      throws MobileHarnessException, InterruptedException {
    if (deviceSdkVersion != null
        && deviceSdkVersion >= AndroidVersion.NOUGAT.getStartSdkVersion()) {
      return Optional.of(
          Integer.toString(androidUserUtil.getCurrentUser(deviceId, deviceSdkVersion)));
    }
    return Optional.empty();
  }

  /** Return actual sdk version or 0 if failed to read. */
  private int getDeviceSdkVersion(Warnings warnings, String deviceId) throws InterruptedException {
    int deviceSdkVersion = 0;
    try {
      deviceSdkVersion = settingUtil.getDeviceSdkVersion(deviceId);
    } catch (MobileHarnessException e) {
      warnings.addAndLog(
          new MobileHarnessException(
              AndroidErrorId.ANDROID_INSTRUMENTATION_GET_SDK_VERSION_ERROR,
              String.format("Failed to read device sdk version from device %s", deviceId)),
          logger);
    }
    return deviceSdkVersion;
  }

  /** Gets the entry delimiter of JobInfo. */
  private static String getEntryDelimiter(JobInfo job) {
    return job.params()
        .get(EntryDelimiterSpec.PARAM_ENTRY_DELIMITER, StrUtil.DEFAULT_ENTRY_DELIMITER);
  }

  /**
   * Parse output from "content query" to perform as list file functionality.
   *
   * <p>Example output from content query:
   *
   * <pre>
   * $ adb shell content query --uri content://outputfiles
   * Row: 0 name=/test.txt, type=f, size=15, _data=/test.txt, _display_name=test.txt, _size=15
   * Row: 1 name=/test_folder, type=d, size=1, _data=/test_folder, _display_name=test_folder, \
   * _size=4096
   * $ adb shell content query --uri content://outputfiles/test_folder
   * Row: 0 name=/test_folder/sub_folder, type=d, size=1, _data=/test_folder/sub_folder, \
   * _display_name=sub_folder, _size=4096
   * $ adb shell content query --uri content://outputfiles/test_folder/sub_folder
   * Row: 0 name=/test_folder/sub_folder/sub_test.txt, type=f, size=0, \
   * _data=/outputfiles/test_folder/sub_folder/sub_test.txt, _display_name=sub_test.txt, _size=0
   * </pre>
   */
  private static Map<String, String> parseContentQueryResult(String output) {
    Map<String, String> contentMap = new HashMap<>();
    if (!Strings.isNullOrEmpty(output)) {
      Pattern contentPattern = Pattern.compile(CONTENT_QUERY_OUTPUT_PATTERN);
      Matcher contentMatcher = contentPattern.matcher("");
      for (String line : Splitters.LINE_SPLITTER.split(output)) {
        contentMatcher.reset(line);
        if (!contentMatcher.find()) {
          continue;
        }

        String contentName = contentMatcher.group("NAME").trim();
        String contentType = contentMatcher.group("TYPE").trim();
        if (Strings.isNullOrEmpty(contentName)) {
          continue;
        }
        contentMap.put(contentName, contentType);
      }
    }
    return contentMap;
  }

  /** Pulls the instrument output/property files from device to host machine. */
  private boolean pullInstrumentFiles(
      TestInfo testInfo,
      String deviceId,
      String filePathOnDevice,
      String filePathOnHost,
      boolean shouldCreateFolder)
      throws InterruptedException {
    try {
      if (!androidFileUtil.isFileOrDirExisted(deviceId, filePathOnDevice)) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Skip pulling files from directory '%s' on device %s as directory doesn't exist",
                filePathOnDevice, deviceId);
        return false;
      }

      if (shouldCreateFolder) {
        localFileUtil.prepareDir(filePathOnHost);
      }

      String output = androidFileUtil.pull(deviceId, filePathOnDevice, filePathOnHost);
      if (!output.isEmpty()) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("\nPulled test output files from device:\n%s", output);
      }
      return true;
    } catch (MobileHarnessException e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_INSTRUMENTATION_PULL_FILE_ERROR,
                  "Failed to pull instrument files from device: " + e.getMessage(),
                  e),
              logger);
    }
    return false;
  }

  private String getResourceFile(String resPath) throws MobileHarnessException {
    Optional<String> externalResFile = resUtil.getExternalResourceFile(resPath);
    if (externalResFile.isPresent()) {
      return externalResFile.get();
    }
    return resUtil.getResourceFile(this.getClass(), resPath);
  }

  /**
   * Converts a test class name to a valid coverage file name.
   *
   * <p>The dots and hashes are replaced with underscores. The extension is set to ".ec". For
   * example, "com.google.codelab.mobileharness.android.hellomobileharness#testMethod" will be
   * converted to "com_google_codelab_mobileharness_android_hellomobileharness_testMethod.ec".
   */
  @VisibleForTesting
  static String convertClassNameToCoverageFileName(String str) {
    return str.replace('.', '_').replace('#', '_') + ".ec";
  }
}
