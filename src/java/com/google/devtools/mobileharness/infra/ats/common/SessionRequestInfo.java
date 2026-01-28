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

package com.google.devtools.mobileharness.infra.ats.common;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestEnvironment;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper.DeviceInfo;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteInfo;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Data holder used to create jobInfo. Data comes from session request handlers, like
 * RunCommandHandler.
 */
@SuppressWarnings("unused")
@AutoValue
public abstract class SessionRequestInfo {
  public abstract String testPlan();

  public abstract String commandLineArgs();

  public abstract String xtsRootDir();

  public abstract Optional<String> androidXtsZip();

  public abstract Optional<String> androidXtsZipDownloadUrl();

  public abstract Optional<String> androidXtsZipPassword();

  public abstract ImmutableList<String> deviceSerials();

  public abstract Optional<DeviceInfo> deviceInfo();

  public abstract ImmutableList<String> excludeDeviceSerials();

  public abstract ImmutableList<String> productTypes();

  public abstract ImmutableMap<String, String> deviceProperties();

  public abstract ImmutableList<String> moduleNames();

  public abstract Optional<String> testName();

  public abstract Optional<Integer> shardCount();

  public abstract ImmutableList<String> includeFilters();

  public abstract ImmutableList<String> excludeFilters();

  public abstract ImmutableMultimap<String, String> moduleMetadataIncludeFilters();

  public abstract ImmutableMultimap<String, String> moduleMetadataExcludeFilters();

  /**
   * When testPlan is "retry", at least one of retrySessionId, retrySessionResultDirName or
   * retrySessionIndex should be set before calling build().
   */
  public abstract Optional<Integer> retrySessionIndex();

  /**
   * When testPlan is "retry", at least one of retrySessionId, retrySessionResultDirName or
   * retrySessionIndex should be set before calling build().
   */
  public abstract Optional<String> retrySessionResultDirName();

  /**
   * When testPlan is "retry", at least one of retrySessionId, retrySessionResultDirName or
   * retrySessionIndex should be set before calling build().
   */
  public abstract Optional<String> retrySessionId();

  public abstract Optional<RetryType> retryType();

  /** Must be set if retrySessionId is set. This is for ATS Server retry. */
  public abstract Optional<String> retryResultDir();

  public abstract ImmutableList<String> moduleArgs();

  public abstract ImmutableList<String> extraArgs();

  public abstract String xtsType();

  public abstract Optional<String> pythonPkgIndexUrl();

  public abstract ImmutableSet<String> givenMatchedNonTfModules();

  /** Key is config file absolute path. */
  public abstract ImmutableMap<String, Configuration> v2ConfigsMap();

  /** Key is expanded module name (e.g. `arm64-v8a CtsBatteryHealthTestCases`). */
  public abstract ImmutableMap<String, Configuration> expandedModules();

  public abstract boolean enableModuleParameter();

  public abstract boolean enableModuleOptionalParameter();

  public abstract Duration jobTimeout();

  public abstract Duration startTimeout();

  public abstract ImmutableMap<String, String> envVars();

  public abstract Optional<String> subPlanName();

  /**
   * The backup subplan name for {@link #subPlanName()} in case the subplan file is modified during
   * test run.
   */
  public abstract Optional<String> subPlanNameBackup();

  public abstract boolean htmlInZip();

  public abstract Optional<String> sessionClientId();

  public abstract Optional<String> deviceType();

  public abstract Optional<Integer> maxBatteryLevel();

  public abstract Optional<Integer> minBatteryLevel();

  public abstract Optional<Integer> maxBatteryTemperature();

  public abstract Optional<Integer> minSdkLevel();

  public abstract Optional<Integer> maxSdkLevel();

  public abstract boolean isAtsServerRequest();

  public abstract Optional<String> remoteRunnerFilePathPrefix();

  public abstract boolean reportSystemCheckers();

  public abstract ShardingMode shardingMode();

  public abstract Optional<Boolean> skipDeviceInfo();

  public abstract Optional<Boolean> isXtsDynamicDownloadEnabled();

  public abstract Optional<Boolean> isMoblyResultstoreUploadEnabled();

  public abstract ImmutableMap<String, String> xtsSuiteInfo();

  public abstract Optional<TestSuiteInfo> testSuiteInfo();

  public abstract ImmutableSet<String> excludeRunners();

  // The test resources passed in from ATS server. Used for building command.xml file.
  public abstract ImmutableList<TestResource> atsServerTestResources();

  // The test environment passed in from ATS server. Used for building command.xml file.
  public abstract Optional<TestEnvironment> atsServerTestEnvironment();

  public abstract boolean allowPartialDeviceMatch();

  public abstract Optional<Boolean> enableDefaultLogs();

  public abstract boolean enableTokenSharding();

  public static Builder builder() {
    return new AutoValue_SessionRequestInfo.Builder()
        .setModuleNames(ImmutableList.of())
        .setDeviceSerials(ImmutableList.of())
        .setDeviceInfo(Optional.empty())
        .setExcludeDeviceSerials(ImmutableList.of())
        .setProductTypes(ImmutableList.of())
        .setDeviceProperties(ImmutableMap.of())
        .setIncludeFilters(ImmutableList.of())
        .setExcludeFilters(ImmutableList.of())
        .setModuleMetadataIncludeFilters(ImmutableMultimap.of())
        .setModuleMetadataExcludeFilters(ImmutableMultimap.of())
        .setModuleArgs(ImmutableList.of())
        .setExtraArgs(ImmutableList.of())
        .setGivenMatchedNonTfModules(ImmutableSet.of())
        .setV2ConfigsMap(ImmutableMap.of())
        .setExpandedModules(ImmutableMap.of())
        .setEnableModuleParameter(false)
        .setEnvVars(ImmutableMap.of())
        .setEnableModuleOptionalParameter(false)
        .setJobTimeout(Duration.ZERO)
        .setStartTimeout(Duration.ZERO)
        .setHtmlInZip(false)
        .setReportSystemCheckers(false)
        .setIsAtsServerRequest(false)
        .setShardingMode(ShardingMode.RUNNER)
        .setXtsSuiteInfo(ImmutableMap.of())
        .setAtsServerTestResources(ImmutableList.of())
        .setExcludeRunners(ImmutableSet.of())
        .setAllowPartialDeviceMatch(false)
        .setEnableTokenSharding(false);
  }

  public abstract Builder toBuilder();

  /** Builder to create SessionRequestInfo. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setTestPlan(String testPlan);

    public abstract Builder setCommandLineArgs(String commandLineArgs);

    public abstract Builder setXtsRootDir(String xtsRootDir);

    public abstract Builder setDeviceSerials(List<String> deviceSerials);

    public abstract Builder setExcludeDeviceSerials(List<String> excludeDeviceSerials);

    public abstract Builder setDeviceInfo(Optional<DeviceInfo> deviceInfo);

    public abstract Builder setProductTypes(List<String> productTypes);

    public abstract Builder setDeviceProperties(ImmutableMap<String, String> deviceProperties);

    public abstract Builder setModuleNames(List<String> moduleNames);

    public abstract Builder setTestName(String testName);

    public abstract Builder setShardCount(int shardCount);

    public abstract Builder setIncludeFilters(List<String> includeFilters);

    public abstract Builder setExcludeFilters(List<String> excludeFilters);

    public abstract Builder setModuleMetadataIncludeFilters(
        ImmutableMultimap<String, String> moduleMetadataIncludeFilters);

    public abstract Builder setModuleMetadataExcludeFilters(
        ImmutableMultimap<String, String> moduleMetadataExcludeFilters);

    public abstract Builder setRetrySessionIndex(Integer retrySessionIndex);

    public abstract Builder setRetrySessionResultDirName(String retrySessionResultDirName);

    public abstract Builder setRetrySessionId(String retrySessionId);

    public abstract Builder setRetryType(RetryType retryType);

    public abstract Builder setRetryResultDir(String retryResultDir);

    public abstract Builder setModuleArgs(List<String> moduleArgs);

    public abstract Builder setExtraArgs(List<String> extraArgs);

    public abstract Builder setXtsType(String xtsType);

    public abstract Builder setPythonPkgIndexUrl(String pythonPkgIndexUrl);

    public abstract Builder setAndroidXtsZip(String androidXtsZip);

    public abstract Builder setAndroidXtsZipDownloadUrl(String androidXtsZipDownloadUrl);

    public abstract Builder setAndroidXtsZipPassword(String androidXtsZipPassword);

    public abstract Builder setEnvVars(ImmutableMap<String, String> envVars);

    public abstract Builder setGivenMatchedNonTfModules(
        ImmutableSet<String> givenMatchedNonTfModules);

    public abstract Builder setV2ConfigsMap(ImmutableMap<String, Configuration> v2ConfigsMap);

    public abstract Builder setExpandedModules(ImmutableMap<String, Configuration> expandedModules);

    public abstract Builder setEnableModuleParameter(boolean enableModuleParameter);

    public abstract Builder setEnableModuleOptionalParameter(boolean enableModuleOptionalParameter);

    public abstract Builder setJobTimeout(Duration jobTimeout);

    public abstract Builder setStartTimeout(Duration startTimeout);

    public abstract Builder setSubPlanName(String subPlanName);

    public abstract Builder setSubPlanNameBackup(String subPlanNameBackup);

    public abstract Builder setHtmlInZip(boolean htmlInZip);

    public abstract Builder setSessionClientId(String sessionClientId);

    public abstract Builder setDeviceType(String deviceType);

    public abstract Builder setMaxBatteryLevel(int maxBatteryLevel);

    public abstract Builder setMinBatteryLevel(int minBatteryLevel);

    public abstract Builder setMaxBatteryTemperature(int maxBatteryTemperature);

    public abstract Builder setMinSdkLevel(int minSdkLevel);

    public abstract Builder setMaxSdkLevel(int maxSdkLevel);

    public abstract Builder setIsAtsServerRequest(boolean isAtsServerRequest);

    public abstract Builder setRemoteRunnerFilePathPrefix(String remoteRunnerFilePathPrefix);

    public abstract Builder setReportSystemCheckers(boolean reportSystemCheckers);

    public abstract Builder setShardingMode(ShardingMode shardingMode);

    public abstract Builder setSkipDeviceInfo(boolean skipDeviceInfo);

    public abstract Builder setIsXtsDynamicDownloadEnabled(boolean isXtsDynamicDownloadEnabled);

    public abstract Builder setIsMoblyResultstoreUploadEnabled(
        boolean isMoblyResultstoreUploadEnabled);

    public abstract Builder setXtsSuiteInfo(ImmutableMap<String, String> xtsSuiteInfo);

    public abstract Builder setTestSuiteInfo(TestSuiteInfo testSuiteInfo);

    public abstract Builder setExcludeRunners(ImmutableSet<String> excludeRunners);

    public abstract Builder setAtsServerTestResources(
        ImmutableList<TestResource> atsServerTestResources);

    public abstract Builder setAtsServerTestEnvironment(TestEnvironment atsServerTestEnvironment);

    public abstract Builder setAllowPartialDeviceMatch(boolean allowPartialDeviceMatch);

    public abstract Builder setEnableDefaultLogs(boolean enableDefaultLogs);

    public abstract Builder setEnableTokenSharding(boolean enableTokenSharding);

    protected abstract SessionRequestInfo autoBuild();

    public SessionRequestInfo build() {
      SessionRequestInfo sessionRequestInfo = autoBuild();
      if (sessionRequestInfo.testPlan().equals("retry")) {
        Preconditions.checkState(
            sessionRequestInfo.retrySessionIndex().isPresent()
                || sessionRequestInfo.retrySessionResultDirName().isPresent()
                || sessionRequestInfo.retrySessionId().isPresent());
        if (sessionRequestInfo.retrySessionId().isPresent()) {
          Preconditions.checkState(sessionRequestInfo.retryResultDir().isPresent());
        }
      }
      return sessionRequestInfo;
    }
  }
}
