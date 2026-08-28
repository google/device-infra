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

package com.google.devtools.mobileharness.infra.ats.common.jobcreator;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfoUtil;
import com.google.devtools.mobileharness.infra.ats.common.proto.FilterValues;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TradefedCommandArgsBuilderTest {

  @Test
  public void build_emptySessionRequestInfo_returnsEmptyString() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir("/path/to/xts_root_dir"));

    String args =
        TradefedCommandArgsBuilder.builder().setSessionRequestInfo(sessionRequestInfo).build();

    assertThat(args).isEmpty();
  }

  @Test
  public void build_standardRun_generatesExpectedArgs() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir("/path/to/xts_root_dir")
                .setTestName("testCase")
                .setShardCount(3)
                .addAllIncludeFilters(ImmutableList.of("incFilter1", "incFilter2"))
                .addAllExcludeFilters(ImmutableList.of("excFilter1"))
                .putModuleMetadataIncludeFilters(
                    "metaKey1", FilterValues.newBuilder().addValues("metaVal1").build())
                .putModuleMetadataExcludeFilters(
                    "metaKey2", FilterValues.newBuilder().addValues("metaVal2").build())
                .setReportSystemCheckers(true)
                .setSkipDeviceInfo(true)
                .setEnableDefaultLogs(false)
                .setEnableTokenSharding(true)
                .setBusinessLogicUrl("https://example.com/logic")
                .setIgnoreBusinessLogicFailure(true)
                .addModuleArgs("module_arg_name:value")
                .addExtraArgs("--flag \"val\"")
                .addExtraArgs("--flag_no_space")
                .addAllModuleNames(ImmutableList.of("moduleA")));

    String args =
        TradefedCommandArgsBuilder.builder()
            .setSessionRequestInfo(sessionRequestInfo)
            .setTfModules(ImmutableList.of("moduleA", "moduleB"))
            .build();

    assertThat(args)
        .isEqualTo(
            "-m moduleA -m moduleB"
                + " -t \"testCase\""
                + " --shard-count 3"
                + " --include-filter \"incFilter1\""
                + " --include-filter \"incFilter2\""
                + " --exclude-filter \"excFilter1\""
                + " --module-metadata-include-filter \"metaKey1\" \"metaVal1\""
                + " --module-metadata-exclude-filter \"metaKey2\" \"metaVal2\""
                + " --report-system-checkers"
                + " --skip-device-info true"
                + " --enable-default-logs false"
                + " --enable-token-sharding"
                + " --business-logic-url https://example.com/logic"
                + " --ignore-business-logic-failure"
                + " --module-arg \"module_arg_name:value\""
                + " \"--flag \\\"val\\\"\""
                + " --flag_no_space");
  }

  @Test
  public void build_strictIncludeFilters_skipsIncludeAndExcludeFilters() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir("/path/to/xts_root_dir")
                .addAllIncludeFilters(ImmutableList.of("includeFilter"))
                .addAllExcludeFilters(ImmutableList.of("excludeFilter"))
                .addAllStrictIncludeFilters(ImmutableList.of("strictFilter")));

    String args =
        TradefedCommandArgsBuilder.builder().setSessionRequestInfo(sessionRequestInfo).build();

    assertThat(args).isEqualTo("--strict-include-filter \"strictFilter\"");
  }

  @Test
  public void build_runRetryWithoutTfRetry_skipsFilterArgs() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("retry")
                .setCommandLineArgs("retry")
                .setXtsType("cts")
                .setXtsRootDir("/path/to/xts_root_dir")
                .setRetrySessionIndex(0)
                .addAllModuleNames(ImmutableList.of("moduleA"))
                .addAllIncludeFilters(ImmutableList.of("includeFilter"))
                .addAllStrictIncludeFilters(ImmutableList.of("strictFilter"))
                .addAllExcludeFilters(ImmutableList.of("excludeFilter")));

    String args =
        TradefedCommandArgsBuilder.builder()
            .setSessionRequestInfo(sessionRequestInfo)
            .setUseTfRetry(false)
            .setTfModules(ImmutableList.of("moduleA"))
            .build();

    assertThat(args).isEmpty();
  }

  @Test
  public void build_runRetryWithTfRetry_passesOriginalModulesAndFilters() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("retry")
                .setCommandLineArgs("retry")
                .setXtsType("cts")
                .setXtsRootDir("/path/to/xts_root_dir")
                .setRetrySessionIndex(0)
                .addAllModuleNames(ImmutableList.of("moduleA"))
                .addAllIncludeFilters(ImmutableList.of("includeFilter"))
                .addAllExcludeFilters(ImmutableList.of("excludeFilter")));

    String args =
        TradefedCommandArgsBuilder.builder()
            .setSessionRequestInfo(sessionRequestInfo)
            .setUseTfRetry(true)
            .setTfModules(ImmutableList.of("moduleB"))
            .build();

    assertThat(args)
        .isEqualTo(
            "-m moduleA --include-filter \"includeFilter\" --exclude-filter \"excludeFilter\"");
  }

  @Test
  public void build_csuiteAppCrawl_omitsSkipDeviceInfo() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("csuite-app-crawl")
                .setCommandLineArgs("csuite-app-crawl")
                .setXtsType("cts")
                .setXtsRootDir("/path/to/xts_root_dir")
                .setSkipDeviceInfo(true));

    String args =
        TradefedCommandArgsBuilder.builder()
            .setSessionRequestInfo(sessionRequestInfo)
            .setShouldSkipDeviceInfoForRetry(true)
            .build();

    assertThat(args).isEmpty();
  }

  @Test
  public void build_shouldSkipDeviceInfoForRetry_appliesWhenNotOverridden() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir("/path/to/xts_root_dir"));

    String args =
        TradefedCommandArgsBuilder.builder()
            .setSessionRequestInfo(sessionRequestInfo)
            .setShouldSkipDeviceInfoForRetry(true)
            .build();

    assertThat(args).isEqualTo("--skip-device-info true");
  }

  @Test
  public void build_quotesAndBackslashes_escapesCorrectly() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir("/path/to/xts_root_dir")
                .addModuleArgs("arg:\"with_quotes\"")
                .addExtraArgs("path\\to\\dir")
                .addExtraArgs("param with spaces"));

    String args =
        TradefedCommandArgsBuilder.builder().setSessionRequestInfo(sessionRequestInfo).build();

    assertThat(args)
        .isEqualTo(
            "--module-arg \"arg:\\\"with_quotes\\\"\""
                + " path\\\\to\\\\dir"
                + " \"param with spaces\"");
  }
}
