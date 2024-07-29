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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.ShardConstants;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName;
import com.google.devtools.mobileharness.infra.ats.common.plan.TestPlanParser;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryGenerator;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class XtsJobCreatorTest {

  private static final String XTS_ROOT_DIR_PATH = "/path/to/xts_root_dir";

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private TestPlanParser testPlanParser;
  @Mock private RetryGenerator retryGenerator;

  private XtsJobCreator xtsJobCreator;

  @Before
  public void setup() {
    xtsJobCreator =
        new XtsJobCreator(
            sessionRequestHandlerUtil, localFileUtil, testPlanParser, retryGenerator) {
          @Override
          protected boolean prepareTfRetry(
              SessionRequestInfo sessionRequestInfo,
              Map<String, String> driverParams,
              ImmutableMap.Builder<XtsPropertyName, String> extraJobProperties) {
            return true;
          }

          @Override
          protected Path prepareRunRetryTfSubPlanXmlFile(
              SessionRequestInfo sessionRequestInfo, SubPlan subPlan) {
            return Path.of("/tmp/subplan.xml");
          }

          @Override
          protected Optional<SubPlan> prepareRunRetrySubPlan(
              SessionRequestInfo sessionRequestInfo, boolean forTf) {
            return Optional.empty();
          }

          @Override
          protected void injectEnvSpecificProperties(
              SessionRequestInfo sessionRequestInfo, Map<String, String> driverParams) {}

          @Override
          protected Optional<Path> getPrevSessionTestReportProperties(
              SessionRequestInfo sessionRequestInfo) {
            return Optional.empty();
          }
        };
  }

  @Test
  public void generateShardingArgs_noModulesForSharding() throws Exception {
    ImmutableSet<String> shardingArgs =
        xtsJobCreator.generateShardingArgs(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardingMode(ShardingMode.MODULE)
                .build(),
            ImmutableList.of("mock_module"));
    assertThat(shardingArgs).hasSize(1);
  }

  @Test
  public void generateShardingArgs_withLargeModules() throws Exception {
    ImmutableSet<String> shardingArgs =
        xtsJobCreator.generateShardingArgs(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardingMode(ShardingMode.MODULE)
                .build(),
            ImmutableList.copyOf(ShardConstants.LARGE_MODULES));

    assertThat(shardingArgs).hasSize(ShardConstants.LARGE_MODULES.size());
  }

  @Test
  public void generateShardingArgs_withShardModules() throws Exception {
    ImmutableSet<String> shardingArgs =
        xtsJobCreator.generateShardingArgs(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardingMode(ShardingMode.MODULE)
                .build(),
            ImmutableList.copyOf(ShardConstants.SHARED_MODULES));

    assertThat(shardingArgs)
        .hasSize(ShardConstants.SHARED_MODULES.size() * ShardConstants.MAX_MODULE_SHARDS);
  }

  @Test
  public void generateShardingArgs_withMixedModules() throws Exception {
    ImmutableList.Builder<String> modules = ImmutableList.builder();
    modules.addAll(ShardConstants.SHARED_MODULES);
    modules.addAll(ShardConstants.LARGE_MODULES);
    modules.add("mock_module");

    ImmutableSet<String> shardingArgs =
        xtsJobCreator.generateShardingArgs(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardingMode(ShardingMode.MODULE)
                .build(),
            modules.build());
    String nonShardingArgs =
        shardingArgs.stream().filter(arg -> arg.contains("mock_module")).findFirst().get();

    assertThat(shardingArgs)
        .hasSize(
            ShardConstants.LARGE_MODULES.size()
                + ShardConstants.SHARED_MODULES.size() * ShardConstants.MAX_MODULE_SHARDS
                + 1);
    assertThat(nonShardingArgs)
        .contains(
            Joiner.on(' ')
                .join(
                    Streams.concat(
                            ShardConstants.SHARED_MODULES.stream()
                                .map(module -> String.format("--exclude-filter \"%s\"", module)),
                            ShardConstants.LARGE_MODULES.stream()
                                .map(module -> String.format("--exclude-filter \"%s\"", module)))
                        .collect(toImmutableList())));
  }

  @Test
  public void generateShardingArgs_withAllModules() throws Exception {
    ImmutableSet.Builder<String> allLocalModulesBuilder = ImmutableSet.builder();
    String sharedModule = ShardConstants.SHARED_MODULES.iterator().next();
    String largeModule = ShardConstants.LARGE_MODULES.iterator().next();
    allLocalModulesBuilder.add(sharedModule).add(largeModule);

    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setShardingMode(ShardingMode.MODULE)
            .build();

    when(sessionRequestHandlerUtil.getAllLocalTradefedModules(eq(sessionRequestInfo)))
        .thenReturn(allLocalModulesBuilder.build());

    ImmutableSet<String> shardingArgs =
        xtsJobCreator.generateShardingArgs(sessionRequestInfo, ImmutableList.of());

    assertThat(shardingArgs).hasSize(ShardConstants.MAX_MODULE_SHARDS + 1);
    for (int index = 0; index < ShardConstants.MAX_MODULE_SHARDS; index++) {
      int finalIndex = index;
      assertThat(
              shardingArgs.stream()
                  .filter(
                      arg ->
                          arg.contains(String.format("-m %s", sharedModule))
                              && arg.contains(
                                  String.format("--shard-index %s", String.valueOf(finalIndex)))))
          .hasSize(1);
    }
    assertThat(
            shardingArgs.stream().filter(arg -> arg.contains(String.format("-m %s", largeModule))))
        .hasSize(1);
  }
}
