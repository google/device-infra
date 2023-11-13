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

package com.google.wireless.qa.mobileharness.client.bin;

import static com.google.devtools.mobileharness.shared.file.resolver.job.JobFileResolverConstants.PARAM_PACKAGE_JOB_INFO_LOCATION;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.jobconfig.JobInfoCreator;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.jobconfig.JobConfigBuilder;
import com.google.wireless.qa.mobileharness.shared.jobconfig.JobConfigsBuilder;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfigs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Utility class to help initialize a list of job configs from the given input in Mobile Harness
 * client.
 */
public final class JobInitializer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Functional interface to get a {@link JobInfo} by the given {@link JobConfig}. */
  @FunctionalInterface
  public interface GetJobInfoMethod {
    JobInfo execute(JobConfig jobConfig, List<String> nonstandardFlags, @Nullable String genDirPath)
        throws MobileHarnessException, InterruptedException;
  }

  /** Env name for TEST_SRCDIR. */
  private static final String TEST_SRCDIR = System.getenv("TEST_SRCDIR");

  private static final SystemUtil systemUtil = new SystemUtil();

  /**
   * Initializes Job configs from Flags. Returns a non-empty {@link JobConfigs} object with no empty
   * JobConfig lists. Fails directly if there is any exception.
   */
  public static JobConfigs initializeJobConfigs()
      throws MobileHarnessException, InterruptedException {

    // Load Job configs from file flag jobConfigJsonPath.
    JobConfigsBuilder configsFromFile = loadJobConfigsJsonFile();

    JobConfigsBuilder mergedConfigs = configsFromFile;
    mergedConfigs.validate();
    return mergedConfigs.build();
  }

  /** Gets a list of {@link JobInfo}s by the given list of {@link JobConfig}s. */
  public static List<JobInfo> getJobInfos(
      List<JobConfig> jobConfigs,
      List<String> nonstandardFlags,
      GetJobInfoMethod getJobInfoMethod,
      Map<String, String> standardArgNames,
      Map<String, String> nonstandardArgNames,
      String clientType)
      throws InterruptedException {
    List<JobInfo> jobInfos = new ArrayList<>();
    for (JobConfig jobConfig : jobConfigs) {
      try {
        JobInfo jobInfo = getJobInfoMethod.execute(jobConfig, nonstandardFlags, TEST_SRCDIR);

        Map<String, String> paramStats = new HashMap<>(jobConfig.getParamStats().getContentMap());
        paramStats.putAll(standardArgNames);
        paramStats.putAll(nonstandardArgNames);
        if (!paramStats.isEmpty()) {
          jobInfo
              .properties()
              .add(
                  PropertyName.Job._PARAM_STATS,
                  StringMap.newBuilder().putAllContent(paramStats).build().toString());
        }
        jobInfo.properties().add(PropertyName.Job.CLIENT_TYPE, clientType);

        // Logs down jobConfig and the final value of params, subDeviceSpecs and files
        JobConfigBuilder jobConfigBuilder = JobConfigBuilder.fromProto(jobConfig);
        logger.atInfo().log(
            "Job config: \n"
                + "[Note this does not include 'dimension_', 'file_', or 'param_' prefixed flags"
                + " and device spec if using target_device.]\n"
                + "%s\n"
                + "where params, subDeviceSpecs and files are finalized in JobInfo as: \n"
                + "[Note this includes 'dimension_', 'file_', and 'param_' prefixed flags and"
                + " device spec]\n"
                + "\"params\": %s\n"
                + "\"subDeviceSpecs\": %s\n"
                + "\"files\": %s",
            jobConfigBuilder,
            jobInfo.params().getAll(),
            jobInfo.subDeviceSpecs(),
            jobInfo.files().getAll());
        jobInfos.add(jobInfo);
      } catch (MobileHarnessException e) {
        ExitCode exitCode;
        switch (e.getErrorCodeEnum()) {
          case FILE_READ_ERROR:
          case FILE_DELETE_ERROR:
            exitCode = ExitCode.Shared.FILE_OPERATION_ERROR;
            break;
          case JOB_SPEC_ERROR:
            exitCode = ExitCode.Client.JOB_SPEC_ERROR;
            break;
          default:
            exitCode = ExitCode.Client.JOB_INFO_ERROR;
            break;
        }
        systemUtil.exit(exitCode, e);
      }
    }
    return jobInfos;
  }

  /**
   * Loads JobConfigs from file {@link #jobConfigsJson}. Returns an empty JobConfigsBuilder if
   * {@link #jobConfigsJson} is not specified or is empty.
   */
  @VisibleForTesting
  static JobConfigsBuilder loadJobConfigsJsonFile() {
    String path = Flags.instance().jobConfigsJson.get();
    if (!Strings.isNullOrEmpty(path)) {
      try {
        String jsonFilePath =
            JobInfoCreator.getFileOrDirPath("", path, TEST_SRCDIR, ImmutableMap.of()).get(0);
        JobConfigsBuilder jobConfigsBuilder = JobConfigsBuilder.fromJsonFile(jsonFilePath);
        // Adds the package info to the job configs.
        for (JobConfigBuilder jobConfigBuilder : jobConfigsBuilder.getList()) {
          JobConfig.Builder proto = jobConfigBuilder.getProtoBuilder();
          StringMap updatedParams =
              proto.getParams().toBuilder()
                  .putContent(PARAM_PACKAGE_JOB_INFO_LOCATION, jsonFilePath)
                  .build();
          proto.setParams(updatedParams);
        }
        return jobConfigsBuilder;
      } catch (MobileHarnessException | InterruptedException e) {
        systemUtil.exit(ExitCode.Client.JOB_INFO_ERROR, e);
      }
    }
    return JobConfigsBuilder.fromDefaultProto();
  }

  private JobInitializer() {}
}
