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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidFilePullerSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidFilePullerDecoratorSpec;
import java.util.Map;

/**
 * Driver decorator for pulling generated files on device to PUBLIC_TMP directory of the test after
 * test finished. MobileHarness client will automatically retrieve these files and send them to
 * Sponge. If there are files existing under the given device directory before running test, by
 * default they will be cleaned up first.
 */
@DecoratorAnnotation(
    help = "For pulling generated files on device and send them back to client or " + "Sponge.")
public class AndroidFilePullerDecorator extends LifecycleDecorator
    implements AndroidFilePullerSpec, SpecConfigable<AndroidFilePullerDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AndroidFileUtil androidFileUtil;
  private final LocalFileUtil localFileUtil;
  private final AndroidAdbUtil androidAdbUtil;

  private AndroidFilePullerDecoratorSpec spec;
  private ImmutableList<String> fileOrDirPathsOnDevice;
  private boolean ignoreFilesNotExist = true;
  private boolean needTeardown = false;

  /**
   * Constructor. Do NOT modify the parameter list. This constructor is required by the lab server
   * framework.
   */
  public AndroidFilePullerDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(
        decoratedDriver,
        testInfo,
        new AndroidFileUtil(),
        new LocalFileUtil(),
        new AndroidAdbUtil());
  }

  /** Constructor for testing only. */
  @VisibleForTesting
  public AndroidFilePullerDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      AndroidFileUtil androidFileUtil,
      LocalFileUtil localFileUtil,
      AndroidAdbUtil androidAdbUtil) {
    super(decoratedDriver, testInfo);
    this.androidFileUtil = androidFileUtil;
    this.localFileUtil = localFileUtil;
    this.androidAdbUtil = androidAdbUtil;
  }

  @Override
  protected void setUp(SetupContext context) throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();
    spec = jobInfo.combinedSpec(this, deviceId);

    if (!matchProperties(deviceId, spec.getPropertyMap(), testInfo)) {
      needTeardown = false;
      return;
    }
    needTeardown = true;

    fileOrDirPathsOnDevice =
        Splitter.on(PATH_DELIMITER)
            .splitToStream(spec.getFilePathOnDevice())
            .map(String::trim)
            .collect(toImmutableList());

    // If the parameter is not set, or not equals to false, cleans up existing files under given
    // device path.
    boolean removeFilesBeforeTest = true;
    if (spec.hasRemoveFilesBeforeTest()) {
      removeFilesBeforeTest = spec.getRemoveFilesBeforeTest();
    }
    if (removeFilesBeforeTest) {
      for (String path : fileOrDirPathsOnDevice) {
        try {
          androidFileUtil.removeFiles(deviceId, path);
          testInfo.log().atInfo().alsoTo(logger).log("Remove file/dir in device: %s", path);
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .withCause(e)
              .log("Failed to remove file/dir %s on device before test.", path);
          if (!spec.getIgnoreRemovingFilesError()) {
            throw e;
          }
        }
      }
    }
    if (spec.hasSkipPullingNonExistFiles()) {
      ignoreFilesNotExist = spec.getSkipPullingNonExistFiles();
    }
  }

  @Override
  protected void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    if (!needTeardown) {
      return;
    }
    String deviceId = getDevice().getDeviceId();
    // Pulls the device files to PUBLIC_TMP dir of the test. MobileHarness client will get and
    // send them to Sponge automatically.
    ImmutableList<String> pulledFilePaths = ImmutableList.of();
    if (spec.hasPulledFilePaths()) {
      pulledFilePaths =
          Splitter.on(PATH_DELIMITER)
              .trimResults()
              .splitToStream(spec.getPulledFilePaths())
              .collect(toImmutableList());
    }
    for (int i = 0; i < fileOrDirPathsOnDevice.size(); i++) {
      String path = fileOrDirPathsOnDevice.get(i);
      String targetRootDirOrPath;
      if (!pulledFilePaths.isEmpty()) {
        String pulledPath = i < pulledFilePaths.size() ? pulledFilePaths.get(i) : "";
        targetRootDirOrPath =
            pulledPath.isEmpty()
                ? testInfo.getGenFileDir()
                : PathUtil.join(testInfo.getGenFileDir(), pulledPath);
      } else if (!spec.getPulledFileDir().isEmpty()) {
        targetRootDirOrPath = PathUtil.join(testInfo.getGenFileDir(), spec.getPulledFileDir());
      } else {
        targetRootDirOrPath = testInfo.getGenFileDir();
      }
      String noPathLog =
          "Skip pulling file/dir because directory [" + path + "] does not exist in the device. ";
      boolean isPathExist = false;

      // First check if file existed on device.
      try {
        isPathExist = androidFileUtil.isFileOrDirExisted(deviceId, path);
      } catch (MobileHarnessException e) {
        testInfo.log().atInfo().alsoTo(logger).withCause(e).log("Failed to check if file existed");
      }
      if (!isPathExist) {
        if (ignoreFilesNotExist) {
          testInfo.log().atInfo().alsoTo(logger).log("%s", noPathLog);
        } else {
          MobileHarnessException exception =
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_FILE_PULLER_DECORATOR_OUTPUT_FILE_NOT_FOUND, noPathLog);
          testInfo.warnings().addAndLog(exception, logger);
          testInfo.resultWithCause().setNonPassing(Test.TestResult.FAIL, exception);
        }
        continue;
      }

      // Now pull existing files from device.
      try {
        String targetPath =
            spec.getPreserveAbsolutePath()
                ? PathUtil.join(targetRootDirOrPath, path)
                : targetRootDirOrPath;
        localFileUtil.prepareParentDir(targetPath);
        String info = androidFileUtil.pull(deviceId, path, targetPath);
        testInfo.log().atInfo().alsoTo(logger).log("%s", info);
      } catch (MobileHarnessException e) {
        MobileHarnessException exception =
            new MobileHarnessException(
                AndroidErrorId.ANDROID_FILE_PULLER_DECORATOR_PULL_FILE_ERROR,
                "Failed to pull file/dir from device directory [" + path + "]",
                e);
        // Log the error message when failed to pull files.
        testInfo.warnings().addAndLog(exception, logger);
        if (!spec.getIgnorePullingExistFilesError()) {
          testInfo.resultWithCause().setNonPassing(Test.TestResult.FAIL, exception);
          break;
        }
      }
    }
  }

  /**
   * Checks if the device properties match the expected values specified in the property map.
   *
   * <p>This method fetches properties from the device one by one for each entry in the map. If any
   * property does not match, it returns false. If an exception occurs while fetching properties, it
   * logs a warning, adds a warning to the test info, and returns false.
   *
   * @param deviceId the ID of the device to check
   * @param propertyMap a map of property keys to expected values
   * @param testInfo the test info for logging and reporting warnings
   * @return {@code true} if all properties match or if the map is empty; {@code false} otherwise
   * @throws InterruptedException if the thread is interrupted during ADB calls
   */
  private boolean matchProperties(
      String deviceId, Map<String, String> propertyMap, TestInfo testInfo)
      throws InterruptedException {
    if (propertyMap.isEmpty()) {
      return true;
    }
    for (Map.Entry<String, String> entry : propertyMap.entrySet()) {
      String key = entry.getKey();
      String expectedValue = entry.getValue();
      try {
        String actualValue = androidAdbUtil.getProperty(deviceId, ImmutableList.of(key));
        if (!expectedValue.equals(actualValue)) {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Property match failed for %s: expected %s, but got %s. Skipping file pull.",
                  key, expectedValue, actualValue);
          return false;
        }
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .withCause(e)
            .log("Failed to get property %s from device. Skipping file pull.", key);
        testInfo
            .warnings()
            .addAndLog(
                new MobileHarnessException(
                    AndroidErrorId.ANDROID_ADB_UTIL_GET_DEVICE_PROPERTY_ERROR,
                    "Failed to check property " + key,
                    e),
                logger);
        return false;
      }
    }
    return true;
  }
}
