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
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.fileoperator.FileOperator;
import com.google.devtools.mobileharness.platform.android.lightning.fileoperator.FilePushArgs;
import com.google.devtools.mobileharness.platform.android.lightning.systemsetting.SystemSettingManager;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidFilePusherSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

/** Driver decorator for pushing files to device before running test. */
@DecoratorAnnotation(help = "For pushing files to device before running test")
public class AndroidFilePusherDecorator extends BaseDecorator implements AndroidFilePusherSpec {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AndroidFileUtil androidFileUtil;
  private final FileOperator fileOperator;
  private final SystemSettingManager systemSettingManager;
  private final SystemStateManager systemStateManager;

  /**
   * Constructor. Do NOT modify the parameter list. This constructor is required by the framework.
   */
  public AndroidFilePusherDecorator(
      Driver decoratedDriver,
      com.google.wireless.qa.mobileharness.shared.model.job.TestInfo testInfo) {
    this(
        decoratedDriver,
        testInfo,
        new AndroidFileUtil(),
        new FileOperator(),
        new SystemSettingManager(),
        new SystemStateManager());
  }

  @VisibleForTesting
  AndroidFilePusherDecorator(
      Driver decoratedDriver,
      com.google.wireless.qa.mobileharness.shared.model.job.TestInfo testInfo,
      AndroidFileUtil androidFileUtil,
      FileOperator fileOperator,
      SystemSettingManager systemSettingManager,
      SystemStateManager systemStateManager) {
    super(decoratedDriver, testInfo);
    this.androidFileUtil = androidFileUtil;
    this.fileOperator = fileOperator;
    this.systemSettingManager = systemSettingManager;
    this.systemStateManager = systemStateManager;
  }

  @Override
  public void run(TestInfo testInfo)
      throws com.google.wireless.qa.mobileharness.shared.MobileHarnessException,
          InterruptedException {
    String deviceId = getDevice().getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();
    Map<String, String> files =
        StrUtil.toMap(
            jobInfo.params().get(AndroidFilePusherSpec.PARAM_PUSH_FILES),
            AndroidFilePusherSpec.FILE_ENTRY_DELIMITER,
            AndroidFilePusherSpec.FILE_MAP_KEY_VALUE_DELIMITER, /* allowDelimiterInValue */
            false,
            /* isValueOptional= */ true);
    int pushTimeoutSec =
        jobInfo
            .params()
            .getInt(
                AndroidFilePusherSpec.PARAM_PUSH_TIMEOUT_SEC,
                AndroidFilePusherSpec.INVALID_PUSH_TIMEOUT);
    boolean removeFile =
        jobInfo.params().isTrue(AndroidFilePusherSpec.PARAM_REMOVE_FILES_BEFORE_PUSH);
    Optional<Duration> pushTimeout =
        pushTimeoutSec == AndroidFilePusherSpec.INVALID_PUSH_TIMEOUT
            ? Optional.empty()
            : Optional.of(Duration.ofSeconds(pushTimeoutSec));

    // Checks the device connection before actually pushing files.
    if (!isDeviceOnline(deviceId)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_PUSHER_DECORATOR_DEVICE_NOT_FOUND,
          "Failed to push files because device is disconnected");
    }

    // Actually pushes files.
    String defaultPathOnDevice = null;
    Set<String> deletedDesPathOnDevice = new HashSet<>();
    for (Entry<String, String> file : files.entrySet()) {
      String tag = file.getKey();
      String desPathOnDevice = file.getValue();
      if (Strings.isNullOrEmpty(desPathOnDevice)) {
        if (defaultPathOnDevice == null) {
          // Reads device external storage only when needed.
          defaultPathOnDevice =
              PathUtil.join(
                  androidFileUtil.getExternalStoragePath(
                      deviceId, systemSettingManager.getDeviceSdkVersion(getDevice())),
                  AndroidFilePusherSpec.DEFAULT_PATH_ON_DEVICE);
        }
        desPathOnDevice = defaultPathOnDevice;
      }

      // If the parameter is set, clean up existing files under given device path.
      // Avoid delete the file/folder twice.
      if (removeFile && !deletedDesPathOnDevice.contains(desPathOnDevice)) {
        try {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Try to remove file/dir [%s] in device %s", desPathOnDevice, deviceId);
          fileOperator.removeFileOrDir(getDevice(), desPathOnDevice, testInfo.log());
          deletedDesPathOnDevice.add(desPathOnDevice);
        } catch (MobileHarnessException e) {
          String message = e.getMessage();
          if (AndroidErrorId.ANDROID_FILE_OPERATOR_REMOVE_FILE_INVALID_ARGUMENT.equals(
              e.getErrorId())) {
            throw new MobileHarnessException(
                AndroidErrorId.ANDROID_FILE_PUSHER_DECORATOR_DELETE_FILE_INVALID_ARGUMENT,
                message,
                e);
          } else if (message.contains("Read-only file system")) {
            testInfo.result().set(TestResult.FAIL);
            throw new MobileHarnessException(
                AndroidErrorId.ANDROID_FILE_PUSHER_DECORATOR_READ_ONLY_FILE_FROM_USER, message, e);
          }
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_FILE_PUSHER_DECORATOR_DELETE_FILE_ERROR, message, e);
        }
      }
      try {
        for (String srcPathOnHost : jobInfo.files().get(tag)) {
          FilePushArgs.Builder pushArgsBuilder =
              FilePushArgs.builder()
                  .setSrcPathOnHost(srcPathOnHost)
                  .setDesPathOnDevice(desPathOnDevice)
                  .setPrepareDesDirWhenSrcIsFile(
                      jobInfo
                          .params()
                          .isTrue(AndroidFilePusherSpec.PARAM_PREPARE_DES_DIR_WHEN_SRC_IS_FILE));
          pushTimeout.ifPresent(pushArgsBuilder::setPushTimeout);
          fileOperator.pushFileOrDir(getDevice(), pushArgsBuilder.build(), testInfo.log());
        }
      } catch (MobileHarnessException e) {
        String message = e.getMessage();
        ErrorId exErrorId = e.getErrorId();
        com.google.devtools.mobileharness.api.model.error.AndroidErrorId errorId =
            AndroidErrorId.ANDROID_FILE_PUSHER_DECORATOR_PUSH_FILE_ERROR;
        if (AndroidErrorId.ANDROID_FILE_OPERATOR_FILE_NOT_FOUND.equals(exErrorId)) {
          testInfo.result().set(TestResult.FAIL);
          errorId = AndroidErrorId.ANDROID_FILE_PUSHER_DECORATOR_USER_FILE_NOT_FOUND;
        } else if (AndroidErrorId.ANDROID_FILE_OPERATOR_ILLEGAL_ARGUMENT.equals(exErrorId)) {
          testInfo.result().set(TestResult.FAIL);
          errorId = AndroidErrorId.ANDROID_FILE_PUSHER_DECORATOR_ILLEGAL_ARGUMENT;
        } else {
          if (message.contains("Read-only file system")) {
            errorId = AndroidErrorId.ANDROID_FILE_PUSHER_DECORATOR_READ_ONLY_FILE_FROM_USER;
            testInfo.result().set(TestResult.FAIL);
          }
          if (message.contains("Not a directory") || message.contains("Is a directory")) {
            errorId =
                AndroidErrorId.ANDROID_FILE_PUSHER_DECORATOR_PUSH_USER_DIRECTORY_TO_FILE_ERROR;
            testInfo.result().set(TestResult.FAIL);
          }
        }
        throw new MobileHarnessException(errorId, message, e);
      }
    }

    // Runs the "real" tests.
    getDecorated().run(testInfo);
  }

  private boolean isDeviceOnline(String deviceId)
      throws MobileHarnessException, InterruptedException {
    try {
      return systemStateManager.isOnline(deviceId);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FILE_PUSHER_DECORATOR_GET_ONLINE_DEVICES_ERROR, e.getMessage(), e);
    }
  }
}
