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

package com.google.devtools.mobileharness.platform.android.xts.common.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import java.time.Duration;
import javax.inject.Inject;

/**
 * The util to restore the test cases of a chunked xTS package (e.g. {@code
 * android-chunked-cts.zip}).
 *
 * <p>A chunked xTS package ships its test cases as FastCDC chunks in {@code
 * android-<xts_type>/chunked-testcases} instead of {@code android-<xts_type>/testcases}, so the
 * chunks must be restored before running any test. This does the same as what the {@code
 * cts-tradefed} launcher script does for ATS console.
 */
public class XtsChunkedTestCasesRestorer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Timeout of restoring the test cases of a chunked xTS package. */
  private static final Duration RESTORE_TIMEOUT = Duration.ofHours(1);

  /** Directory which contains the chunks of the test cases in a chunked xTS package. */
  private static final String CHUNKED_TEST_CASES_DIR_NAME = "chunked-testcases";

  /** Binary in the xTS package which restores {@link #CHUNKED_TEST_CASES_DIR_NAME}. */
  private static final String RESTORER_BINARY_NAME = "cts_restorer";

  private final LocalFileUtil localFileUtil;
  private final CommandExecutor commandExecutor;

  @Inject
  @VisibleForTesting
  XtsChunkedTestCasesRestorer(LocalFileUtil localFileUtil, CommandExecutor commandExecutor) {
    this.localFileUtil = localFileUtil;
    this.commandExecutor = commandExecutor;
  }

  /**
   * Returns the dir which contains the chunks of the test cases in a chunked xTS package, which
   * only exists if the xTS package is chunked and its test cases haven't been restored yet.
   */
  public static String getChunkedTestCasesDir(String xtsRootDir, String xtsType) {
    return PathUtil.join(xtsRootDir, "android-" + xtsType, CHUNKED_TEST_CASES_DIR_NAME);
  }

  /**
   * Restores the test cases of a chunked xTS package in the given xTS root dir, if the xTS root dir
   * contains a chunked test cases dir.
   *
   * <p>The chunked test cases dir is removed by the restorer binary after a successful restoration,
   * so this method is a no-op for a non-chunked xTS package or an already restored one.
   *
   * @param xtsRootDir the dir which contains the {@code android-<xts_type>} dir
   * @param xtsType the xTS type, e.g. {@code cts}
   */
  public void restoreTestCases(String xtsRootDir, String xtsType)
      throws MobileHarnessException, InterruptedException {
    String chunkedTestCasesDir = getChunkedTestCasesDir(xtsRootDir, xtsType);
    if (!localFileUtil.isDirExist(chunkedTestCasesDir)) {
      return;
    }
    String xtsDir = PathUtil.join(xtsRootDir, "android-" + xtsType);
    String testCasesDir = PathUtil.join(xtsDir, "testcases");
    String restorerBinary = PathUtil.join(xtsDir, "tools", RESTORER_BINARY_NAME);
    if (!localFileUtil.isFileExist(restorerBinary)) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          AndroidErrorId.XTS_RESTORE_CHUNKED_TEST_CASES_ERROR,
          String.format(
              "Cannot restore test cases from %s because the restorer binary %s doesn't exist in"
                  + " the xTS package. Please download and use a non-chunked xTS package (e.g."
                  + " android-cts.zip) for the testing.",
              chunkedTestCasesDir, restorerBinary),
          /* cause= */ null);
    }
    logger.atInfo().log("Restoring test cases from %s to %s", chunkedTestCasesDir, testCasesDir);
    Command command =
        Command.of(
                restorerBinary,
                "--chunked-dir-path",
                chunkedTestCasesDir,
                "--output-dir-path",
                testCasesDir)
            .timeout(RESTORE_TIMEOUT);
    String output;
    try {
      output = commandExecutor.run(command);
    } catch (MobileHarnessException e) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          AndroidErrorId.XTS_RESTORE_CHUNKED_TEST_CASES_ERROR,
          String.format(
              "Failed to restore test cases from %s to %s. Aborting to prevent running with"
                  + " incomplete test cases. Please download and use a non-chunked xTS package"
                  + " (e.g. android-cts.zip) for the testing.",
              chunkedTestCasesDir, testCasesDir),
          e);
    }
    logger.atInfo().log("Restored test cases to %s, output: %s", testCasesDir, output);
  }
}
