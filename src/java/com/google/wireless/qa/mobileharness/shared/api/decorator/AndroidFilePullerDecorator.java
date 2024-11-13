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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidFilePullerSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidFilePullerDecoratorSpec;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Driver decorator for pulling generated files on device to PUBLIC_TMP directory of the test after
 * test finished. MobileHarness client will automatically retrieve these files and send them to
 * Sponge. If there are files existing under the given device directory before running test, by
 * default they will be cleaned up first.
 */
@DecoratorAnnotation(
    help = "For pulling generated files on device and send them back to client or " + "Sponge.")
public class AndroidFilePullerDecorator extends BaseDecorator
    implements AndroidFilePullerSpec, SpecConfigable<AndroidFilePullerDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AndroidFileUtil androidFileUtil;

  /**
   * Constructor. Do NOT modify the parameter list. This constructor is required by the lab server
   * framework.
   */
  public AndroidFilePullerDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(decoratedDriver, testInfo, new AndroidFileUtil());
  }

  /** Constructor for testing only. */
  @VisibleForTesting
  public AndroidFilePullerDecorator(
      Driver decoratedDriver, TestInfo testInfo, AndroidFileUtil androidFileUtil) {
    super(decoratedDriver, testInfo);
    this.androidFileUtil = androidFileUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();
    AndroidFilePullerDecoratorSpec spec = jobInfo.combinedSpec(this, deviceId);

    List<String> fileOrDirPathsOnDevice =
        Splitter.onPattern(PATH_DELIMITER)
            .splitToStream(spec.getFilePathOnDevice())
            .map(String::trim)
            .collect(Collectors.toList());

    // If the parameter is not set, or not equals to false, cleans up existing files under given
    // device path.
    boolean removeFilesBeforeTest = true;
    if (spec.hasRemoveFilesBeforeTest()) {
      removeFilesBeforeTest = spec.getRemoveFilesBeforeTest();
    }
    if (removeFilesBeforeTest) {
      for (String path : fileOrDirPathsOnDevice) {
        androidFileUtil.removeFiles(deviceId, path);
        testInfo.log().atInfo().alsoTo(logger).log("Remove file/dir in device: %s", path);
      }
    }
    boolean ignoreFilesNotExist = true;
    if (spec.hasSkipPullingNonExistFiles()) {
      ignoreFilesNotExist = spec.getSkipPullingNonExistFiles();
    }
    try {
      // Runs "real" tests.
      getDecorated().run(testInfo);
    } finally {
      // Pulls the device files to PUBLIC_TMP dir of the test. MobileHarness client will get and
      // send them to Sponge automatically.
      for (String path : fileOrDirPathsOnDevice) {
        String noPathLog =
            "Skip pulling file/dir because directory [" + path + "] does not exist in the device. ";
        boolean isPathExist = false;

        // First check if file existed on device.
        try {
          isPathExist = androidFileUtil.isFileOrDirExisted(deviceId, path);
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .withCause(e)
              .log("Failed to check if file existed");
        }
        if (!isPathExist) {
          if (ignoreFilesNotExist) {
            testInfo.log().atInfo().alsoTo(logger).log("%s", noPathLog);
          } else {
            MobileHarnessException exception =
                new MobileHarnessException(
                    AndroidErrorId.ANDROID_FILE_PULLER_DECORATOR_OUTPUT_FILE_NOT_FOUND, noPathLog);
            testInfo.errors().addAndLog(exception, logger);
            testInfo.resultWithCause().setNonPassing(Test.TestResult.FAIL, exception);
          }
          continue;
        }

        // Now pull existing files from device.
        try {
          String info = androidFileUtil.pull(deviceId, path, testInfo.getGenFileDir());
          testInfo.log().atInfo().alsoTo(logger).log("%s", info);
        } catch (MobileHarnessException e) {
          MobileHarnessException exception =
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_FILE_PULLER_DECORATOR_PULL_FILE_ERROR,
                  "Failed to pull file/dir from device directory [" + path + "]",
                  e);
          // Log the error message when failed to pull files.
          testInfo.errors().addAndLog(exception, logger);
          if (!spec.getIgnorePullingExistFilesError()) {
            testInfo.resultWithCause().setNonPassing(Test.TestResult.FAIL, exception);
            break;
          }
        }
      }
    }
  }
}
