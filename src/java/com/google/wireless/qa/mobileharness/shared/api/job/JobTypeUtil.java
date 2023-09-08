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

package com.google.wireless.qa.mobileharness.shared.api.job;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Util methods for handling JobType proto. */
public final class JobTypeUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** File tag name of DeviceSpec textproto. */
  private static final String TAG_DEVICE_SPEC = "device_spec_textproto";

  public static String toString(JobType jobType) {
    StringBuilder builder = new StringBuilder(jobType.getDevice());
    builder.append('+');
    builder.append(jobType.getDriver());
    for (String decorator : jobType.getDecoratorList()) {
      builder.append('+');
      builder.append(decorator);
    }
    return builder.toString();
  }

  /**
   * Parses job type string. The format of the string should be:
   * Device+Driver[+Decorator1[+Decorator2]...] e.g.
   * AndroidRealDevice+AndroidInstrumentation+AndroidLogCatDecorator+AndroidFilePullerDecorator
   *
   * @throws MobileHarnessException if string format error
   */
  public static JobType parseString(String jobTypeStr) throws MobileHarnessException {
    String errMsg = "Job type format error. Format: Device+Driver[+Decorator1[+Decorator2]...]";

    String[] words = jobTypeStr.split("\\+");
    if (words.length < 2) {
      throw new MobileHarnessException(BasicErrorId.JOB_TYPE_NOT_SUPPORTED, errMsg);
    }
    for (int i = 0; i < words.length; i++) {
      words[i] = words[i].trim();
      if (words[i].isEmpty()) {
        throw new MobileHarnessException(BasicErrorId.JOB_TYPE_NOT_SUPPORTED, errMsg);
      }
    }
    JobType.Builder builder = JobType.newBuilder();
    builder.setDevice(words[0]);
    builder.setDriver(words[1]);
    for (int i = 2; i < words.length; i++) {
      builder.addDecorator(words[i]);
    }
    return builder.build();
  }

  /**
   * Gets a device type name from a {@link JobConfig.DeviceList} which should contain at least one
   * sub device.
   *
   * @return the device type of the first sub device
   */
  public static String getDeviceTypeName(JobConfig.DeviceList device) {
    return device.getSubDeviceSpec(0).getType();
  }

  /**
   * Parses JobType from a give {@link JobConfig}.
   *
   * @throws MobileHarnessException if type format is error, or device/driver is not given
   */
  public static JobType parseJobConfig(JobConfig jobConfig) throws MobileHarnessException {
    if (jobConfig.hasType()) {
      return parseString(jobConfig.getType());
    }

    JobType.Builder builder = JobType.newBuilder();
    if (jobConfig.getDevice().getSubDeviceSpecCount() > 0) {
      builder.setDevice(getDeviceTypeName(jobConfig.getDevice()));
    } else if (jobConfig.getFiles().getContentList().stream()
        .anyMatch(item -> item.getTag().equals(TAG_DEVICE_SPEC))) {
      // The device type is not really used in Gateway job config.
      // So to make it simpler, not parse the file here. It will be parsed later in JobInfoCreator.
    } else {
      throw new MobileHarnessException(
          BasicErrorId.JOB_TYPE_NOT_SUPPORTED,
          "Either device or device_spec_textproto file must be provided.");
    }

    if (!jobConfig.hasDriver()) {
      throw new MobileHarnessException(BasicErrorId.JOB_TYPE_NOT_SUPPORTED, "Driver is required.");
    }
    builder.setDriver(jobConfig.getDriver().getName());

    if (jobConfig.getDevice().getSubDeviceSpecCount() == 1) {
      if (jobConfig.getDevice().getSubDeviceSpec(0).hasDecorators()) {
        List<String> decorators = new ArrayList<>();
        jobConfig
            .getDevice()
            .getSubDeviceSpec(0)
            .getDecorators()
            .getContentList()
            .forEach(decorator -> decorators.add(decorator.getName()));

        // The JobConfig has reversed the order of decorators to make it more intuitive. We need to
        // reverse it again to make it compatible with original settings.
        Collections.reverse(decorators);
        builder.addAllDecorator(decorators);
      }
    } else {
      logger.atWarning().log("Job has multiple subdevices, skipped adding decorators to Job type.");
    }
    return builder.build();
  }

  private JobTypeUtil() {}
}
