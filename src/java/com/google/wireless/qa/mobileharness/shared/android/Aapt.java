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

package com.google.wireless.qa.mobileharness.shared.android;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.command.LineCallback.does;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.command.CommandTimeoutException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.proto.AndroidDeviceSpec.Abi;
import com.google.wireless.qa.mobileharness.shared.util.ArrayUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Executor for invoking AAPT command line tools from Android SDK. */
public class Aapt {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** AAPT args for getting the package info. Should be followed with the path of an APK. */
  @VisibleForTesting static final String[] AAPT_ARGS_DUMP_BADGING = {"dump", "badging"};

  /** AAPT args for getting the permissions. Should be followed with the path of an APK. */
  @VisibleForTesting static final String[] AAPT_ARGS_DUMP_PERMISSION = {"dump", "permissions"};

  /**
   * AAPT args for getting xml parse tree for an xml file contained within apk. Should be followed
   * with the path of an APK and xml file name.
   */
  @VisibleForTesting static final String[] AAPT_ARGS_DUMP_XMLTREE = {"dump", "xmltree"};

  private static final String FILE_FLAG = "--file";
  private static final String ANDROID_MANIFEST = "AndroidManifest.xml";
  private static final Splitter LINE_SPLITTER = Splitters.LINE_SPLITTER;

  private static final Pattern XMLTREE_CATEGORY_LAUNCHER_PATTERN =
      Pattern.compile(
          "^\\s*A:[\\s*]android:name\\(\\w+\\)\\=\\\"android.intent.category.LAUNCHER\\\"");
  private static final Pattern XMLTREE_ACTIVITY_ALIAS_PATTERN =
      Pattern.compile("\\s*E:\\s*activity-alias");
  private static final Pattern XMLTREE_TARGET_ACTIVITY_PATTERN =
      Pattern.compile("^\\s*A:\\s*android:targetActivity\\(\\w*\\)\\=\\\"([^\\\"]+)");

  private static final Pattern XMLTREE_INSTRUMENTATION_TARGET_PKG_PATTERN =
      Pattern.compile("^\\s*A:.*android:targetPackage\\(\\w+\\)\\=\\\"([^\\\"]+)");

  private static final Pattern BADGING_LAUNCHABLE_ACTIVITY_PATTERN =
      Pattern.compile("^\\s*launchable-activity:\\s*name='([^\\\"]+?)'");
  private static final Pattern BADGING_REPEATED_FIELD_PATTERN = Pattern.compile("'([^'\\s]+)'");

  /** Path of the AAPT command line tool. */
  private final String aaptPath;

  /**
   * Whether to cache aapt command output. The default value is {@link
   * #DEFAULT_ENABLE_AAPT_OUTPUT_CACHE}.
   */
  private final boolean enableAaptOutputCache;

  @VisibleForTesting static final boolean DEFAULT_ENABLE_AAPT_OUTPUT_CACHE = false;

  /** System command executor. */
  private final CommandExecutor cmdExecutor;

  /** Patterns for native code are not always present, so the list may stay empty. */
  private static final String NATIVE_CODE_PREFIX = "native-code: ";

  private static final String ALT_NATIVE_CODE_PREFIX = "alt-native-code: ";

  private final Map<String, String> aaptOutputCache = new HashMap<>();

  /**
   * Android SDK lazy initializer. It will do initialization only once before the first {@link Aapt}
   * instance is created.
   */
  @VisibleForTesting
  static class LazyInitializer {
    @Nullable private static final String AAPT_PATH;

    private static final String ERROR;

    public static String getAaptPath() {
      return AAPT_PATH;
    }

    static {
      String path = null;
      String error = null;
      try {
        path = getAaptPathExternal();
      } catch (IllegalStateException e) {
        error = e.getMessage();
      }
      AAPT_PATH = path;
      ERROR = error;
      logger.atInfo().log("Android SDK AAPT tool path: %s", AAPT_PATH);
      if (ERROR != null) {
        logger.atWarning().log("Failed to initialize AAPT: %s", ERROR);
      }
    }

    private static String getAaptPathExternal() {
      String result = Flags.instance().aaptPath.getNonNull();
      if (result.isEmpty()) {
        logger.atInfo().log("AAPT path --aapt not specified, use \"aapt\" as AAPT path");
        result = "aapt";
      } else {
        logger.atInfo().log("AAPT path from user: %s", result);
      }

      if (new LocalFileUtil().isFileExistInPath(result)) {
        return result;
      } else {
        throw new IllegalStateException(
            String.format(
                "Invalid AAPT path [%s] (file doesn't exist or isn't in PATH dirs)", result));
      }
    }

    private LazyInitializer() {}
  }

  /** Creates a executor for running AAPT commands using Android SDK tools. */
  public Aapt() {
    this(LazyInitializer.getAaptPath(), DEFAULT_ENABLE_AAPT_OUTPUT_CACHE, new CommandExecutor());
  }

  /**
   * Creates an executor for running AAPT commands using Android SDK tools.
   *
   * @param enableAaptOutputCache whether to cache aapt command output
   */
  public Aapt(boolean enableAaptOutputCache) {
    this(LazyInitializer.getAaptPath(), enableAaptOutputCache, new CommandExecutor());
  }

  @VisibleForTesting
  Aapt(String aaptPath, boolean enableAaptOutputCache, CommandExecutor cmdExecutor) {
    this.aaptPath = aaptPath;
    this.enableAaptOutputCache = enableAaptOutputCache;
    this.cmdExecutor = Preconditions.checkNotNull(cmdExecutor);
  }

  /**
   * Gets the first ABI of the given APK file.
   *
   * @param apkPath path of the apk package
   * @return the ABI of the given APK file
   */
  public String getApkAbi(String apkPath) throws MobileHarnessException, InterruptedException {
    String output = run(ArrayUtil.join(AAPT_ARGS_DUMP_BADGING, apkPath));
    List<String> allAbis = parseNativeCode(output);
    if (!allAbis.isEmpty()) {
      return allAbis.get(0);
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_AAPT_GET_APK_ABI_ERROR,
        "Failed to find the ABI of " + apkPath + ": " + output);
  }

  /**
   * Lists all ABI of the given APK file.
   *
   * @param apkPath path of the apk package
   * @return the ABI list of the given APK file
   */
  public List<String> listApkAbi(String apkPath)
      throws MobileHarnessException, InterruptedException {
    return parseNativeCode(run(ArrayUtil.join(AAPT_ARGS_DUMP_BADGING, apkPath)));
  }

  /** Gets the instrumentation target package of the given test apk. */
  public Optional<String> getApkInstrumentationTargetPackage(String apkPath)
      throws MobileHarnessException, InterruptedException {
    String output = getAndroidManifest(apkPath);
    List<String> lines = LINE_SPLITTER.trimResults().omitEmptyStrings().splitToList(output);
    List<String> instrumentationEntryAttrs = new ArrayList<>();

    boolean instrumentationEntryStartFound = false;
    for (int i = 0; i < lines.size(); i++) {
      if (instrumentationEntryStartFound && !lines.get(i).startsWith("A: ")) {
        break;
      } else if (!instrumentationEntryStartFound
          && lines.get(i).startsWith("E: instrumentation (")) {
        instrumentationEntryStartFound = true;
      } else if (instrumentationEntryStartFound) {
        instrumentationEntryAttrs.add(lines.get(i));
      }
    }

    return instrumentationEntryAttrs.stream()
        .filter(attr -> XMLTREE_INSTRUMENTATION_TARGET_PKG_PATTERN.matcher(attr).find())
        .findFirst()
        .map(
            attr -> {
              Matcher matcher = XMLTREE_INSTRUMENTATION_TARGET_PKG_PATTERN.matcher(attr);
              return matcher.find() ? matcher.group(1) : "";
            });
  }

  /**
   * Gets the launchable-activity of the given APK file.
   *
   * @param apkPath path of the apk package
   * @return the activity name of the given APK file
   */
  public String getApkLaunchableActivityName(String apkPath)
      throws MobileHarnessException, InterruptedException {
    Optional<String> launchableActivity = getApkLaunchableActivityNameWithBadging(apkPath);
    if (launchableActivity.isEmpty()) {
      launchableActivity = getApkLaunchableActivityNameWithXmlTree(apkPath);
    }
    return launchableActivity.orElseThrow(
        () ->
            new MobileHarnessException(
                AndroidErrorId.ANDROID_AAPT_GET_LAUNCHABLE_ACTIVITY_NAME_ERROR,
                "Fail to find the launchable-activity name of " + apkPath));
  }

  /** Gets the version code of the given apk. */
  public int getApkVersionCode(String apkPath) throws MobileHarnessException, InterruptedException {
    String output = run(ArrayUtil.join(AAPT_ARGS_DUMP_BADGING, apkPath));
    // Example output:
    // package: name='com.google.android.apps....' versionCode='1000' versionName='1.0.0' ...
    Matcher matcher = Pattern.compile(" versionCode='(\\d+)' versionName=").matcher(output);
    if (matcher.find()) {
      try {
        return Integer.parseInt(matcher.group(1));
      } catch (NumberFormatException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_AAPT_APK_INVALID_VERSION_CODE,
            "Fail to parse the version code of " + apkPath + ": " + output,
            e);
      }
    } else {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_AAPT_APK_UNMATCHED_VERSION_INFO_FORMAT,
          "Fail to find the version info of " + apkPath + ": " + output);
    }
  }

  /** Gets the version name of the given apk. */
  public String getApkVersionName(String apkPath)
      throws MobileHarnessException, InterruptedException {
    String output = run(ArrayUtil.join(AAPT_ARGS_DUMP_BADGING, apkPath));
    // Example output:
    // package: name='com.google.android.apps....' versionCode='1000' versionName='1.0.0' ...
    Matcher matcher = Pattern.compile(" versionName='([^']+)'").matcher(output);
    if (matcher.find()) {
      return matcher.group(1);
    } else {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_AAPT_APK_UNMATCHED_VERSION_NAME_FORMAT,
          "Fail to find the version name of " + apkPath + ": " + output);
    }
  }

  /**
   * Gets the min SDK version of the given apk.
   *
   * @return the min SDK version, or 0 if not set, or -1 if failed to parse
   */
  public int getApkMinSdkVersion(String apkPath)
      throws MobileHarnessException, InterruptedException {
    String output = run(ArrayUtil.join(AAPT_ARGS_DUMP_BADGING, apkPath));
    Matcher matcher = Pattern.compile("\\nsdkVersion:'([^']+)'").matcher(output);
    if (matcher.find()) {
      String version = matcher.group(1);
      try {
        return Integer.parseInt(version);
      } catch (NumberFormatException e) {
        // Failed to parse, e.g.: not a number but 'N' when the Android N is not released.
        return -1;
      }
    } else {
      // Not found means not set.
      return 0;
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
    String output = run(ArrayUtil.join(AAPT_ARGS_DUMP_BADGING, apkPath));
    String prefix = "package: name='";
    int startIdx = prefix.length();
    for (String line : LINE_SPLITTER.split(output)) {
      if (!line.startsWith(prefix)) {
        continue;
      }
      int endIdx = line.indexOf('\'', startIdx);
      if (endIdx > startIdx) {
        return line.substring(startIdx, endIdx);
      }
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_AAPT_GET_APK_PACKAGE_NAME_ERROR,
        "Fail to find the package name of " + apkPath + ": " + output);
  }

  /**
   * Gets the permissions of the given APK file.
   *
   * @param apkPath path of the apk package
   * @return the permissions of the given APK file
   */
  public Set<String> getApkPermissions(String apkPath)
      throws MobileHarnessException, InterruptedException {
    Set<String> permissions = new HashSet<>();
    String output = run(ArrayUtil.join(AAPT_ARGS_DUMP_PERMISSION, apkPath));
    // Example output:
    // package: com.google.assistant.core
    // uses-permission: name='android.permission.DUMP'
    // uses-permission: name='android.permission.READ_FRAME_BUFFER'
    // uses-permission: name='android.permission.BLUETOOTH'
    // ...
    String prefix = "uses-permission: name='";
    int startIdx = prefix.length();
    for (String line : LINE_SPLITTER.split(output)) {
      if (!line.startsWith(prefix)) {
        continue;
      }
      int endIdx = line.indexOf('\'', startIdx);
      if (endIdx > startIdx) {
        permissions.add(line.substring(startIdx, endIdx));
      }
    }
    return permissions;
  }

  /** Returns the path to the Aapt binary. */
  public String getAaptPath() {
    return aaptPath;
  }

  /** Gets launchable activity with command 'dump badging'. */
  private Optional<String> getApkLaunchableActivityNameWithBadging(String apkPath)
      throws MobileHarnessException, InterruptedException {
    String output = run(ArrayUtil.join(AAPT_ARGS_DUMP_BADGING, apkPath));
    // The output is like:
    // ...
    // launchable-activity: name='package.name.activity.name'  label='...' icon='...'
    // ...
    for (String line : LINE_SPLITTER.split(output)) {
      Matcher matcher = BADGING_LAUNCHABLE_ACTIVITY_PATTERN.matcher(line);
      if (matcher.find()) {
        return Optional.of(matcher.group(1));
      }
    }
    return Optional.empty();
  }

  /**
   * Gets launchable activity which is in activity-alias with command 'dump xmltree'.
   *
   * <pre>The output of 'dump xmltree' look like:
   * ...
   * E: activity-alias (line=312)
   *   A: android:targetActivity(0x01010003)="com.app.activity.MainActivity" ...
   *   ...
   *     E: intent-filter(line=xxx)
   *       ...
   *       E: category (line=xxx)
   *         A: android:name(0x01010003)="android.intent.category.LAUNCHER" ...
   * ...</pre>
   *
   * <p>There could have multiple lines with "activity-alias", but should only have one line with
   * "android.intent.category.LAUNCHER".
   *
   * <p>This method will search the line with "android.intent.category.LAUNCHER" in the whole
   * output, And reversely search from this line to find the line with "android:targetActivity",
   * until find it, or reach the "activity-alias" line.
   */
  private Optional<String> getApkLaunchableActivityNameWithXmlTree(String apkPath)
      throws MobileHarnessException, InterruptedException {
    String output = getAndroidManifest(apkPath);
    List<String> lines = LINE_SPLITTER.splitToList(output);

    int launcherLineIndex = -1;

    // Gets the line with "android.intent.category.LAUNCHER".
    for (int i = 0; i < lines.size(); i++) {
      if (XMLTREE_CATEGORY_LAUNCHER_PATTERN.matcher(lines.get(i)).find()) {
        if (launcherLineIndex != -1) {
          logger.atWarning().log(
              "Get multiple 'android.intent.category.LAUNCHER' from AndroidManifest.xml. "
                  + "Refer line %s and line %s in\n%s",
              launcherLineIndex, i, output);
          return Optional.empty();
        } else {
          launcherLineIndex = i;
        }
      }
    }

    // Reversely searches for "android:targetActivity".
    for (int i = launcherLineIndex - 1; i >= 0; i--) {
      String line = lines.get(i);
      if (XMLTREE_ACTIVITY_ALIAS_PATTERN.matcher(line).find()) {
        logger.atWarning().log(
            "Reached line activity-alias, but not get targetActivity. "
                + "Refer line %s to line %s in\n%s",
            i, launcherLineIndex, output);
        return Optional.empty();
      } else {
        Matcher activityMatcher = XMLTREE_TARGET_ACTIVITY_PATTERN.matcher(line);
        if (activityMatcher.find()) {
          return Optional.of(activityMatcher.group(1));
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Gets the output of aapt dump xmltree <apkPath> AndroidManifest.xml.
   *
   * @param apkPath local path of the apk file
   * @return the xmltree output.
   */
  public String getAndroidManifest(String apkPath)
      throws MobileHarnessException, InterruptedException {
    return run(ArrayUtil.join(AAPT_ARGS_DUMP_XMLTREE, apkPath, FILE_FLAG, ANDROID_MANIFEST));
  }

  /**
   * Runs AAPT command line tools.
   *
   * @param args AAPT command line arguments
   * @return command output
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  private String run(String[] args) throws MobileHarnessException, InterruptedException {
    checkAapt();
    StringBuilder output = new StringBuilder();
    Command command =
        Command.of(ArrayUtil.join(aaptPath, args))
            .onStdout(does(line -> output.append(line).append('\n')));
    String commandStr = command.toString();
    if (enableAaptOutputCache && aaptOutputCache.containsKey(commandStr)) {
      return aaptOutputCache.get(commandStr);
    }
    try {
      cmdExecutor.exec(command);
    } catch (CommandException e) {
      // Command may fail to get version code from apk (b/37925295) but we'd like to return captured
      // apk info (like package name) to caller so caller may still get needed info.
      // For command timeout case, throw an exception.
      if (e instanceof CommandTimeoutException) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_AAPT_COMMAND_EXEC_TIMEOUT, e.getMessage(), e);
      } else if (e instanceof CommandStartException) {
        // If AAPT fails to start, we throw the exception to facilitate debug. http://b/177877279
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_AAPT_COMMAND_START_ERROR, e.getMessage(), e);
      }
      // e could also be instanceof CommandFailureException, but the output still contains
      // information the caller needs, so we don't throw the exception.
    }
    if (output.length() == 0) {
      output.append('\n');
    }
    if (enableAaptOutputCache) {
      aaptOutputCache.put(commandStr, output.toString());
    }
    return output.toString();
  }

  /**
   * Checks whether the AAPT has been initialized normally.
   *
   * @throws MobileHarnessException if fails to initialize the AAPT
   */
  public void checkAapt() throws MobileHarnessException {
    if (aaptPath == null) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_AAPT_PATH_NOT_SET, LazyInitializer.ERROR);
    }
  }

  /**
   * Parsing all native codes from dump badging output.
   *
   * <p>Parses results from both native-code and alt-native-code badgings. The implementation
   * referenced <a
   * href="https://android.googlesource.com/platform/tools/tradefederation/+/master/src/com/android/tradefed/util/AaptParser.java">AaptParser.java</a>.
   *
   * @param output of aapt dump badging
   */
  private static ImmutableList<String> parseNativeCode(String output) {
    List<String> allNativeCodes = Lists.newArrayList();
    for (String line : LINE_SPLITTER.split(output)) {
      if (line.startsWith(NATIVE_CODE_PREFIX) || line.startsWith(ALT_NATIVE_CODE_PREFIX)) {
        Matcher matcher = BADGING_REPEATED_FIELD_PATTERN.matcher(line);
        while (matcher.find()) {
          allNativeCodes.add(matcher.group(1));
        }
      }
    }
    return allNativeCodes.stream().filter(Aapt::isAbi).collect(toImmutableList());
  }

  /** Checks if a string represents a valid abi value. */
  private static boolean isAbi(String str) {
    try {
      Abi.valueOf(Ascii.toUpperCase(str.replace('-', '_')));
      return true;
    } catch (IllegalArgumentException e) {
      logger.atWarning().log("%s is not a valid abi value", str);
      return false;
    }
  }
}
