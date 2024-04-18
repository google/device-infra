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
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

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

  public abstract ImmutableList<String> deviceSerials();

  public abstract ImmutableList<String> moduleNames();

  public abstract Optional<String> testName();

  public abstract Optional<Integer> shardCount();

  public abstract ImmutableList<String> includeFilters();

  public abstract ImmutableList<String> excludeFilters();

  /**
   * When testPlan is "retry", at least one of retrySessionId or retrySessionIndex should be set
   * before calling build().
   */
  public abstract OptionalInt retrySessionIndex();

  /**
   * When testPlan is "retry", at least one of retrySessionId or retrySessionIndex should be set
   * before calling build().
   */
  public abstract Optional<String> retrySessionId();

  public abstract Optional<RetryType> retryType();

  public abstract Optional<String> retryResultDir();

  public abstract ImmutableList<String> extraArgs();

  public abstract String xtsType();

  public abstract Optional<String> pythonPkgIndexUrl();

  public abstract ImmutableSet<String> givenMatchedNonTfModules();

  public abstract ImmutableMap<String, Configuration> v2ConfigsMap();

  public abstract ImmutableMap<String, Configuration> expandedModules();

  public abstract boolean enableModuleParameter();

  public abstract boolean enableModuleOptionalParameter();

  public abstract Duration jobTimeout();

  public abstract Duration startTimeout();

  public abstract ImmutableMap<String, String> envVars();

  public abstract Optional<String> subPlanName();

  public abstract boolean htmlInZip();

  public abstract Optional<String> testPlanFile();

  public static Builder builder() {
    return new AutoValue_SessionRequestInfo.Builder()
        .setModuleNames(ImmutableList.of())
        .setDeviceSerials(ImmutableList.of())
        .setIncludeFilters(ImmutableList.of())
        .setExcludeFilters(ImmutableList.of())
        .setExtraArgs(ImmutableList.of())
        .setGivenMatchedNonTfModules(ImmutableSet.of())
        .setV2ConfigsMap(ImmutableMap.of())
        .setExpandedModules(ImmutableMap.of())
        .setEnableModuleParameter(false)
        .setEnvVars(ImmutableMap.of())
        .setEnableModuleOptionalParameter(false)
        .setJobTimeout(Duration.ZERO)
        .setStartTimeout(Duration.ZERO)
        .setHtmlInZip(false);
  }

  public abstract Builder toBuilder();

  /** Builder to create SessionRequestInfo. */
  @SuppressWarnings("UnusedReturnValue")
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setTestPlan(String testPlan);

    public abstract Builder setCommandLineArgs(String commandLineArgs);

    public abstract Builder setXtsRootDir(String xtsRootDir);

    public abstract Builder setDeviceSerials(List<String> deviceSerials);

    public abstract Builder setModuleNames(List<String> moduleNames);

    public abstract Builder setTestName(String testName);

    public abstract Builder setShardCount(int shardCount);

    public abstract Builder setIncludeFilters(List<String> includeFilters);

    public abstract Builder setExcludeFilters(List<String> excludeFilters);

    /**
     * When testPlan is "retry", at least one of retrySessionId or retrySessionIndex should be set
     * before calling build().
     */
    public abstract Builder setRetrySessionIndex(int retrySessionIndex);

    /**
     * When testPlan is "retry", at least one of retrySessionId or retrySessionIndex should be set
     * before calling build().
     */
    public abstract Builder setRetrySessionId(String retrySessionId);

    public abstract Builder setRetryType(RetryType retryType);

    /** Must be set if retrySessionId is set. This is for ATS Server retry. */
    public abstract Builder setRetryResultDir(String retryResultDir);

    public abstract Builder setExtraArgs(List<String> extraArgs);

    public abstract Builder setXtsType(String xtsType);

    public abstract Builder setPythonPkgIndexUrl(String pythonPkgIndexUrl);

    public abstract Builder setAndroidXtsZip(String androidXtsZip);

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

    public abstract Builder setHtmlInZip(boolean htmlInZip);

    public abstract Builder setTestPlanFile(String testPlanFile);

    protected abstract SessionRequestInfo autoBuild();

    public SessionRequestInfo build() {
      SessionRequestInfo sessionRequestInfo = autoBuild();
      if (sessionRequestInfo.testPlan().equals("retry")) {
        Preconditions.checkState(
            sessionRequestInfo.retrySessionIndex().isPresent()
                || sessionRequestInfo.retrySessionId().isPresent());
        if (sessionRequestInfo.retrySessionId().isPresent()) {
          Preconditions.checkState(sessionRequestInfo.retryResultDir().isPresent());
        }
      }
      return sessionRequestInfo;
    }
  }
}
