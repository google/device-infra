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
import static com.google.devtools.mobileharness.infra.ats.common.jobcreator.ModuleShardingArgsGenerator.LARGE_MODULE_SHARDS;
import static com.google.devtools.mobileharness.infra.ats.common.jobcreator.ModuleShardingArgsGenerator.SHARD_MODULE_SHARDS;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.ShardConstants;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ModuleShardingArgsGeneratorTest {

  private static final String XTS_ROOT_DIR_PATH = "/path/to/xts_root_dir";

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock private SessionRequestHandlerUtil sessionRequestHandlerUtil;

  private ModuleShardingArgsGenerator moduleShardingArgsGenerator;

  @Before
  public void setup() throws Exception {
    moduleShardingArgsGenerator = new ModuleShardingArgsGenerator(sessionRequestHandlerUtil);
  }

  @Test
  public void generateShardingArgs_noModulesForSharding() throws Exception {
    ImmutableSet<String> shardingArgs =
        moduleShardingArgsGenerator.generateShardingArgs(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardingMode(ShardingMode.MODULE)
                .setIncludeFilters(ImmutableList.of("mock_module1[instant]", "mock_module2"))
                .setExcludeFilters(ImmutableList.of("mock_module1[instant] class#test"))
                .build(),
            ImmutableList.of("mock_module1", "mock_module2"));

    assertThat(shardingArgs)
        .containsExactly(
            "-m mock_module1"
                + " --include-filter \"mock_module1[instant]\""
                + " --exclude-filter \"mock_module1[instant] class#test\"",
            "-m mock_module2" + " --include-filter \"mock_module2\"");
  }

  @Test
  public void generateShardingArgs_withLargeModules() throws Exception {
    ImmutableSet<String> shardingArgs =
        moduleShardingArgsGenerator.generateShardingArgs(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardingMode(ShardingMode.MODULE)
                .build(),
            ShardConstants.LARGE_MODULES.asList());

    assertThat(shardingArgs).hasSize(ShardConstants.LARGE_MODULES.size() * LARGE_MODULE_SHARDS);
  }

  @Test
  public void generateShardingArgs_withShardModules() throws Exception {
    ImmutableSet<String> shardingArgs =
        moduleShardingArgsGenerator.generateShardingArgs(
            SessionRequestInfo.builder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardingMode(ShardingMode.MODULE)
                .build(),
            ShardConstants.SHARD_MODULES.asList());

    assertThat(shardingArgs).hasSize(ShardConstants.SHARD_MODULES.size() * SHARD_MODULE_SHARDS);
  }

  @Test
  public void generateShardingArgs_withMixedModules() throws Exception {
    ImmutableList.Builder<String> modules = ImmutableList.builder();
    modules
        .addAll(ShardConstants.SHARD_MODULES)
        .addAll(ShardConstants.LARGE_MODULES)
        .add("mock_module");

    ImmutableSet<String> shardingArgs =
        moduleShardingArgsGenerator.generateShardingArgs(
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
            ShardConstants.LARGE_MODULES.size() * LARGE_MODULE_SHARDS
                + ShardConstants.SHARD_MODULES.size() * SHARD_MODULE_SHARDS
                + 1);
    assertThat(nonShardingArgs).contains("-m mock_module");
  }

  @Test
  public void generateShardingArgs_withAllModules() throws Exception {
    ImmutableSet.Builder<String> allLocalModulesBuilder = ImmutableSet.builder();
    String shardModule = ShardConstants.SHARD_MODULES.iterator().next();
    String largeModule = ShardConstants.LARGE_MODULES.iterator().next();
    allLocalModulesBuilder.add(shardModule).add(largeModule).add("local_module");

    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("cts")
            .setCommandLineArgs("cts")
            .setXtsType("cts")
            .setXtsRootDir(XTS_ROOT_DIR_PATH)
            .setShardingMode(ShardingMode.MODULE)
            .build();

    when(sessionRequestHandlerUtil.getStaticMctsModules())
        .thenReturn(ImmutableSet.of("mcts_module"));
    when(sessionRequestHandlerUtil.getAllLocalTradefedModules(eq(sessionRequestInfo)))
        .thenReturn(allLocalModulesBuilder.build());

    ImmutableSet<String> shardingArgs =
        moduleShardingArgsGenerator.generateShardingArgs(sessionRequestInfo, ImmutableList.of());

    assertThat(shardingArgs).hasSize(SHARD_MODULE_SHARDS + LARGE_MODULE_SHARDS + 2);
    for (int index = 0; index < SHARD_MODULE_SHARDS; index++) {
      int finalIndex = index;
      assertThat(
              shardingArgs.stream()
                  .filter(
                      arg ->
                          arg.contains(String.format("-m %s", shardModule))
                              && arg.contains(
                                  String.format("--shard-index %s", String.valueOf(finalIndex)))))
          .hasSize(1);
    }
    for (int index = 0; index < LARGE_MODULE_SHARDS; index++) {
      int finalIndex = index;
      assertThat(
              shardingArgs.stream()
                  .filter(
                      arg ->
                          arg.contains(String.format("-m %s", largeModule))
                              && arg.contains(
                                  String.format("--shard-index %s", String.valueOf(finalIndex)))))
          .hasSize(1);
    }
    assertThat(shardingArgs.stream().filter(arg -> arg.contains("-m local_module"))).hasSize(1);
    assertThat(shardingArgs.stream().filter(arg -> arg.contains("-m mcts_module"))).hasSize(1);
  }

  @Test
  public void includeTestOnly_withParams() throws Exception {
    assertThat(moduleShardingArgsGenerator.includeTestOnly(ImmutableList.of())).isFalse();
    assertThat(
            moduleShardingArgsGenerator.includeTestOnly(
                ImmutableList.of(
                    SuiteTestFilter.create("module[param1]"),
                    SuiteTestFilter.create("module[param2]"))))
        .isFalse();
    assertThat(
            moduleShardingArgsGenerator.includeTestOnly(
                ImmutableList.of(
                    SuiteTestFilter.create("module[param1] class#test"),
                    SuiteTestFilter.create("module[param1]"))))
        .isTrue();
    assertThat(
            moduleShardingArgsGenerator.includeTestOnly(
                ImmutableList.of(
                    SuiteTestFilter.create("module[param1] class#test"),
                    SuiteTestFilter.create("module[param2]"))))
        .isFalse();
  }

  @Test
  public void includeTestOnly_withAbi() throws Exception {
    assertThat(
            moduleShardingArgsGenerator.includeTestOnly(
                ImmutableList.of(
                    SuiteTestFilter.create("abi1 module test#1"),
                    SuiteTestFilter.create("abi2 module test#1"),
                    SuiteTestFilter.create("module"))))
        .isFalse();
    assertThat(
            moduleShardingArgsGenerator.includeTestOnly(
                ImmutableList.of(
                    SuiteTestFilter.create("abi1 module test#1"),
                    SuiteTestFilter.create("module"),
                    SuiteTestFilter.create("abi2 module test#1"))))
        .isFalse();
    assertThat(
            moduleShardingArgsGenerator.includeTestOnly(
                ImmutableList.of(
                    SuiteTestFilter.create("abi1 module"),
                    SuiteTestFilter.create("abi2 module"),
                    SuiteTestFilter.create("module test#1"))))
        .isTrue();
    assertThat(
            moduleShardingArgsGenerator.includeTestOnly(
                ImmutableList.of(
                    SuiteTestFilter.create("module test#1"),
                    SuiteTestFilter.create("abi1 module"),
                    SuiteTestFilter.create("abi2 module"))))
        .isTrue();
  }
}
