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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.base.Utf8;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Spills oversized inline Android instrumentation test target arguments ({@code class}, {@code
 * package}, {@code notClass}, {@code notPackage}) into device-side {@code testFile} and {@code
 * notTestFile} files so {@code am instrument} does not exceed the kernel {@code MAX_ARG_STRLEN}
 * limit.
 */
final class AndroidInstrumentationTestFileSpiller {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String ARGUMENT_TEST_CLASS = "class";
  static final String ARGUMENT_NOT_TEST_CLASS = "notClass";
  static final String ARGUMENT_TEST_PACKAGE = "package";
  static final String ARGUMENT_NOT_TEST_PACKAGE = "notPackage";
  static final String ARGUMENT_TEST_FILE = "testFile";
  static final String ARGUMENT_NOT_TEST_FILE = "notTestFile";

  /**
   * Maximum UTF-8 byte length for inline test target arguments ({@code class}, {@code notClass},
   * {@code package}, {@code notPackage}) before spilling them to a device {@code testFile} or
   * {@code notTestFile}. Even when {@code Adb.runShell} writes commands exceeding 4,090 bytes to a
   * temporary device script ({@code /data/local/tmp/adb_shell_command_*.sh}), {@code
   * /system/bin/sh} and {@code ShellMain} still pass the {@code -e class <targets>} value as a
   * single {@code argv[i]} argument to {@code execve} when invoking {@code am} / {@code
   * app_process}, which fails with {@code E2BIG} ({@code Argument list too long}) if any single
   * argument exceeds the Linux kernel {@code MAX_ARG_STRLEN = 131072} bytes (128 KiB) limit. Using
   * 65,536 UTF-8 bytes leaves ample headroom.
   */
  private static final int MAX_TEST_TARGET_ARG_LENGTH = 65536;

  private static final String TEST_FILE_PREFIX = "instrumentation_test_file";
  private static final String NOT_TEST_FILE_PREFIX = "instrumentation_not_test_file";

  private static final ImmutableList<String> POSITIVE_TEST_TARGET_KEYS =
      ImmutableList.of(ARGUMENT_TEST_CLASS, ARGUMENT_TEST_PACKAGE);
  private static final ImmutableList<String> NEGATIVE_TEST_TARGET_KEYS =
      ImmutableList.of(ARGUMENT_NOT_TEST_CLASS, ARGUMENT_NOT_TEST_PACKAGE);

  private final LocalFileUtil fileUtil;
  private final AndroidInstrumentationUtil androidInstrumentationUtil;

  @Inject
  AndroidInstrumentationTestFileSpiller(
      LocalFileUtil fileUtil, AndroidInstrumentationUtil androidInstrumentationUtil) {
    this.fileUtil = fileUtil;
    this.androidInstrumentationUtil = androidInstrumentationUtil;
  }

  /**
   * Spills oversized positive and negative test targets from {@code optionMaps} (where {@link
   * Optional#empty()} denotes a skipped sequential entry) and {@code testTarget} to device files,
   * returning the updated top-level {@code testTarget} ({@code null} if folded into spilled {@code
   * testFile} / per-map {@code class} entries).
   */
  @Nullable
  String prepareTestFiles(
      TestInfo testInfo,
      String deviceId,
      int deviceSdkVersion,
      @Nullable String externalStoragePath,
      List<Optional<Map<String, String>>> optionMaps,
      @Nullable String testTarget)
      throws MobileHarnessException, InterruptedException {
    PushContext pushContext =
        new PushContext(testInfo, deviceId, deviceSdkVersion, externalStoragePath);
    int optionMapCount = optionMaps.size();
    boolean anyPositiveSpilled = false;
    List<Map<String, String>> unspilledPositiveMaps = new ArrayList<>();
    for (int i = 0; i < optionMapCount; i++) {
      Optional<Map<String, String>> maybeOptionMap = optionMaps.get(i);
      if (maybeOptionMap.isEmpty()) {
        continue;
      }
      Map<String, String> optionMap = maybeOptionMap.get();
      boolean spilledPositive =
          spillTargetsIfNeeded(
              pushContext,
              optionMap,
              ARGUMENT_TEST_FILE,
              POSITIVE_TEST_TARGET_KEYS,
              testTarget,
              getScopedTestFileName(TEST_FILE_PREFIX, i, optionMapCount));
      if (spilledPositive) {
        anyPositiveSpilled = true;
      } else {
        unspilledPositiveMaps.add(optionMap);
      }
      spillTargetsIfNeeded(
          pushContext,
          optionMap,
          ARGUMENT_NOT_TEST_FILE,
          NEGATIVE_TEST_TARGET_KEYS,
          /* extraTarget= */ null,
          getScopedTestFileName(NOT_TEST_FILE_PREFIX, i, optionMapCount));
    }
    if (!anyPositiveSpilled) {
      return testTarget;
    }
    // testTarget is already in the testFile of each map that spilled, so return null to stop
    // it being passed again as className. Maps that did not spill still need testTarget, so
    // pass it through their `class` option instead.
    String trimmedTestTarget = nullToEmpty(testTarget).trim();
    if (!trimmedTestTarget.isEmpty()) {
      for (Map<String, String> optionMap : unspilledPositiveMaps) {
        optionMap.merge(
            ARGUMENT_TEST_CLASS,
            trimmedTestTarget,
            (existingClass, newValue) ->
                existingClass.trim().isEmpty() ? newValue : newValue + "," + existingClass);
      }
    }
    return null;
  }

  private static String getScopedTestFileName(String prefix, int optionMapIdx, int optionMapCount) {
    return optionMapCount <= 1 ? prefix + ".txt" : prefix + "_" + optionMapIdx + ".txt";
  }

  @CanIgnoreReturnValue
  private boolean spillTargetsIfNeeded(
      PushContext pushContext,
      Map<String, String> optionMap,
      String fileKey,
      ImmutableList<String> targetKeys,
      @Nullable String extraTarget,
      String fileName)
      throws MobileHarnessException, InterruptedException {
    int combinedUtf8Length = Utf8.encodedLength(nullToEmpty(extraTarget));
    for (String targetKey : targetKeys) {
      combinedUtf8Length += Utf8.encodedLength(nullToEmpty(optionMap.get(targetKey)));
    }
    if (combinedUtf8Length <= MAX_TEST_TARGET_ARG_LENGTH) {
      return false;
    }
    if (optionMap.containsKey(fileKey)) {
      pushContext
          .testInfo()
          .log()
          .atWarning()
          .alsoTo(logger)
          .log(
              "Oversized test targets (%d bytes) exceed limit of %d bytes, but cannot be"
                  + " spilled because '%s' is already specified: %s",
              combinedUtf8Length, MAX_TEST_TARGET_ARG_LENGTH, fileKey, optionMap.get(fileKey));
      return false;
    }
    List<String> targets = new ArrayList<>();
    addSplitTargets(targets, extraTarget);
    for (String targetKey : targetKeys) {
      addSplitTargets(targets, optionMap.get(targetKey));
    }
    String deviceFilePath = writeAndPushTestFile(pushContext, fileName, targets);
    optionMap.keySet().removeAll(targetKeys);
    optionMap.put(fileKey, deviceFilePath);
    return true;
  }

  private static void addSplitTargets(List<String> destination, @Nullable String commaSeparated) {
    if (isNullOrEmpty(commaSeparated)) {
      return;
    }
    int start = 0;
    int length = commaSeparated.length();
    for (int i = 0; i < length; i++) {
      char c = commaSeparated.charAt(i);
      if (c == '[') {
        int end = findMatchingClose(commaSeparated, i, '[', ']');
        if (end != -1) {
          i = end;
        }
      } else if (c == '(') {
        int end = findMatchingClose(commaSeparated, i, '(', ')');
        if (end != -1) {
          i = end;
        }
      } else if (c == ',') {
        addTrimmedTarget(destination, commaSeparated.substring(start, i));
        start = i + 1;
      }
    }
    addTrimmedTarget(destination, commaSeparated.substring(start));
  }

  private static int findMatchingClose(String input, int openIndex, char openChar, char closeChar) {
    int depth = 1;
    for (int i = openIndex + 1; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == openChar) {
        depth++;
      } else if (c == closeChar) {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static void addTrimmedTarget(List<String> destination, String rawTarget) {
    String trimmed = rawTarget.trim();
    if (!trimmed.isEmpty()) {
      destination.add(trimmed);
    }
  }

  private String writeAndPushTestFile(
      PushContext pushContext, String fileName, List<String> targets)
      throws MobileHarnessException, InterruptedException {
    String hostFilePath = PathUtil.join(pushContext.testInfo().getTmpFileDir(), fileName);
    try {
      fileUtil.writeToFile(hostFilePath, String.join("\n", targets));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_INSTRUMENTATION_WRITE_FILE_ERROR,
          String.format("Failed to write test file %s on host", hostFilePath),
          e);
    }
    return androidInstrumentationUtil.pushTestFile(
        pushContext.testInfo(),
        pushContext.deviceId(),
        pushContext.deviceSdkVersion(),
        hostFilePath,
        fileName,
        pushContext.externalStoragePath());
  }

  private record PushContext(
      TestInfo testInfo,
      String deviceId,
      int deviceSdkVersion,
      @Nullable String externalStoragePath) {}
}
