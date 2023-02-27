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

package com.google.devtools.mobileharness.shared.util.testcomponents;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.File;
import java.io.FileFilter;

/** Utils for test components (See go/sponge-test-components for details). */
public class TestComponentsUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The dir name for test components in each test gen dir. */
  @VisibleForTesting static final String TEST_COMPONENTS_DIR_NAME = "test_components";

  @VisibleForTesting
  static final FileFilter testComponentsDirFilter =
      (File dir) -> {
        return PathUtil.basename(dir.getAbsolutePath()).equals(TEST_COMPONENTS_DIR_NAME);
      };

  private final LocalFileUtil fileUtil;

  public TestComponentsUtil() {
    this(new LocalFileUtil());
  }

  @VisibleForTesting
  TestComponentsUtil(LocalFileUtil fileUtil) {
    this.fileUtil = fileUtil;
  }
  /**
   * Gets the path of the test components directory, and prepares the dir. Test runners can write
   * test components files to this directory on the lab side. Note to make sure the test components
   * files have different file name to avoid overwriting when merging test components files in tests
   * belonging to the same job.
   *
   * <p>The purpose of this directory is so that test runners on the lab side can use write test
   * components in the same way as specified in go/sponge-test-components.
   *
   * @param testInfo the TestInfo for the test
   */
  public String prepareAndGetTestComponentsDir(TestInfo testInfo) throws MobileHarnessException {
    String dir = PathUtil.join(testInfo.getGenFileDir(), TEST_COMPONENTS_DIR_NAME);
    if (!fileUtil.isDirExist(dir)) {
      fileUtil.prepareDir(dir);
      fileUtil.grantFileOrDirFullAccess(dir);
    }
    return dir;
  }

  /**
   * Moves all the test components dir in {@code srcDir} to {@code destDir}.
   *
   * <p>The logic is called in MH client only.
   */
  public void moveTestComponentsDir(String srcDir, String destDir) throws InterruptedException {
    try {
      for (File dir : fileUtil.listDirs(srcDir, testComponentsDirFilter)) {
        fileUtil.mergeDir(dir.getAbsolutePath(), destDir);
        logger.atInfo().log("Merge dir %s to %s.", dir.getAbsolutePath(), destDir);
        for (String file : fileUtil.listFilePaths(destDir, true)) {
          logger.atInfo().log("In dir %s: %s", destDir, file);
        }
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Fail to move test components dir from %s to %s", srcDir, destDir);
    }
  }
}
