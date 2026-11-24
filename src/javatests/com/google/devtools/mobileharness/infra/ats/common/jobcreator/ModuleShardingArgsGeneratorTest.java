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
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfoUtil;
import com.google.devtools.mobileharness.infra.ats.common.ShardConstants;
import com.google.devtools.mobileharness.infra.ats.common.proto.FilterValues;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
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
    moduleShardingArgsGenerator = new ModuleShardingArgsGenerator();
  }

  @Test
  public void generateShardingArgs_noModulesForSharding() throws Exception {
    ImmutableSet<String> shardingArgs =
        ImmutableSet.copyOf(
            moduleShardingArgsGenerator
                .generateShardingArgsMap(
                    SessionRequestInfoUtil.buildAndValidate(
                        SessionRequestInfo.newBuilder()
                            .setTestPlan("cts")
                            .setCommandLineArgs("cts")
                            .setXtsType("cts")
                            .setXtsRootDir(XTS_ROOT_DIR_PATH)
                            .setShardingMode(ShardingMode.MODULE)
                            .addAllIncludeFilters(
                                ImmutableList.of("mock_module1[instant]", "mock_module2"))
                            .addAllExcludeFilters(
                                ImmutableList.of("mock_module1[instant] class#test"))),
                    ImmutableList.of("mock_module1", "mock_module2"))
                .values());

    assertThat(shardingArgs)
        .containsExactly(
            "--include-filter \"mock_module1[instant]\""
                + " --exclude-filter \"mock_module1[instant] class#test\""
                + " --skip-preconditions",
            "--include-filter \"mock_module2\" --skip-preconditions");
  }

  @Test
  public void generateShardingArgs_alreadyHasSkipPreconditions_notDuplicated() throws Exception {
    ImmutableSet<String> shardingArgs =
        ImmutableSet.copyOf(
            moduleShardingArgsGenerator
                .generateShardingArgsMap(
                    SessionRequestInfoUtil.buildAndValidate(
                        SessionRequestInfo.newBuilder()
                            .setTestPlan("cts")
                            .setCommandLineArgs("cts")
                            .setXtsType("cts")
                            .setXtsRootDir(XTS_ROOT_DIR_PATH)
                            .setShardingMode(ShardingMode.MODULE)
                            .addAllIncludeFilters(ImmutableList.of("mock_module1"))
                            .addExtraArgs("--skip-preconditions")),
                    ImmutableList.of("mock_module1"))
                .values());

    assertThat(shardingArgs)
        .containsExactly("--include-filter \"mock_module1\" --skip-preconditions");
  }

  @Test
  public void generateShardingArgs_withLargeModules() throws Exception {
    ImmutableSet<String> shardingArgs =
        ImmutableSet.copyOf(
            moduleShardingArgsGenerator
                .generateShardingArgsMap(
                    SessionRequestInfoUtil.buildAndValidate(
                        SessionRequestInfo.newBuilder()
                            .setTestPlan("cts")
                            .setCommandLineArgs("cts")
                            .setXtsType("cts")
                            .setXtsRootDir(XTS_ROOT_DIR_PATH)
                            .setShardingMode(ShardingMode.MODULE)),
                    ShardConstants.LARGE_MODULES.asList())
                .values());

    assertThat(shardingArgs).hasSize(ShardConstants.LARGE_MODULES.size() * LARGE_MODULE_SHARDS);
  }

  @Test
  public void generateShardingArgs_withShardModules() throws Exception {
    ImmutableSet<String> shardingArgs =
        ImmutableSet.copyOf(
            moduleShardingArgsGenerator
                .generateShardingArgsMap(
                    SessionRequestInfoUtil.buildAndValidate(
                        SessionRequestInfo.newBuilder()
                            .setTestPlan("cts")
                            .setCommandLineArgs("cts")
                            .setXtsType("cts")
                            .setXtsRootDir(XTS_ROOT_DIR_PATH)
                            .setShardingMode(ShardingMode.MODULE)),
                    ShardConstants.SHARD_MODULES.asList())
                .values());

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
        ImmutableSet.copyOf(
            moduleShardingArgsGenerator
                .generateShardingArgsMap(
                    SessionRequestInfoUtil.buildAndValidate(
                        SessionRequestInfo.newBuilder()
                            .setTestPlan("cts")
                            .setCommandLineArgs("cts")
                            .setXtsType("cts")
                            .setXtsRootDir(XTS_ROOT_DIR_PATH)
                            .setShardingMode(ShardingMode.MODULE)),
                    modules.build())
                .values());

    String nonShardingArgs =
        shardingArgs.stream().filter(arg -> arg.contains("mock_module")).findFirst().get();

    assertThat(shardingArgs)
        .hasSize(
            ShardConstants.LARGE_MODULES.size() * LARGE_MODULE_SHARDS
                + ShardConstants.SHARD_MODULES.size() * SHARD_MODULE_SHARDS
                + 1);
    assertThat(nonShardingArgs).contains("-m mock_module");
  }

  @SuppressWarnings("DirectInvocationOnMock")
  @Test
  public void generateShardingArgs_withAllModules() throws Exception {
    ImmutableSet.Builder<String> allLocalModulesBuilder = ImmutableSet.builder();
    String shardModule = ShardConstants.SHARD_MODULES.iterator().next();
    String largeModule = ShardConstants.LARGE_MODULES.iterator().next();
    allLocalModulesBuilder.add(shardModule).add(largeModule).add("local_module");

    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardingMode(ShardingMode.MODULE));

    when(sessionRequestHandlerUtil.getStaticMctsModules())
        .thenReturn(ImmutableSet.of("mcts_module"));
    when(sessionRequestHandlerUtil.getAllLocalTradefedModules(eq(sessionRequestInfo)))
        .thenReturn(allLocalModulesBuilder.build());

    ImmutableList<String> targetModules =
        ImmutableList.<String>builder()
            .addAll(sessionRequestHandlerUtil.getAllLocalTradefedModules(sessionRequestInfo))
            .addAll(sessionRequestHandlerUtil.getStaticMctsModules())
            .build();

    ImmutableSet<String> shardingArgs =
        ImmutableSet.copyOf(
            moduleShardingArgsGenerator
                .generateShardingArgsMap(sessionRequestInfo, targetModules)
                .values());

    assertThat(shardingArgs).hasSize(SHARD_MODULE_SHARDS + LARGE_MODULE_SHARDS + 2);
    for (int index = 0; index < SHARD_MODULE_SHARDS; index++) {
      int finalIndex = index;
      assertThat(
              shardingArgs.stream()
                  .filter(arg ->
                      arg.contains(String.format("-m %s", shardModule))
                          && arg.contains(
                              String.format("--shard-index %s", String.valueOf(finalIndex)))))
          .hasSize(1);
    }
    for (int index = 0; index < LARGE_MODULE_SHARDS; index++) {
      int finalIndex = index;
      assertThat(
              shardingArgs.stream()
                  .filter(arg ->
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

  @Test
  public void generateShardingArgsMap_mapsModulesToShardingArgs() throws Exception {
    ImmutableListMultimap<String, String> shardingArgsMap =
        moduleShardingArgsGenerator.generateShardingArgsMap(
            SessionRequestInfoUtil.buildAndValidate(
                SessionRequestInfo.newBuilder()
                    .setTestPlan("cts")
                    .setCommandLineArgs("cts")
                    .setXtsType("cts")
                    .setXtsRootDir(XTS_ROOT_DIR_PATH)
                    .setShardingMode(ShardingMode.MODULE)
                    .addAllIncludeFilters(ImmutableList.of("mock_module1[instant]", "mock_module2"))
                    .addAllExcludeFilters(ImmutableList.of("mock_module1[instant] class#test"))),
            ImmutableList.of("mock_module1", "mock_module2"));

    assertThat(shardingArgsMap.keySet()).containsExactly("mock_module1", "mock_module2");
    assertThat(shardingArgsMap.get("mock_module1"))
        .containsExactly(
            "--include-filter \"mock_module1[instant]\""
                + " --exclude-filter \"mock_module1[instant] class#test\""
                + " --skip-preconditions");
    assertThat(shardingArgsMap.get("mock_module2"))
        .containsExactly("--include-filter \"mock_module2\" --skip-preconditions");
  }

  @Test
  public void generateShardingArgsMap_forwardsCommonTradefedOptions() throws Exception {
    String largeModule = ShardConstants.LARGE_MODULES.iterator().next();
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardingMode(ShardingMode.MODULE)
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
                .addModuleArgs("mock_module:opt:\"val\"")
                .addExtraArgs("--flag \"val\"")
                .addExtraArgs("path\\to\\dir"));

    ImmutableListMultimap<String, String> shardingArgsMap =
        moduleShardingArgsGenerator.generateShardingArgsMap(
            sessionRequestInfo, ImmutableList.of("mock_module", largeModule));

    String expectedCommonArgs =
        " --skip-preconditions"
            + " --module-metadata-include-filter \"metaKey1\" \"metaVal1\""
            + " --module-metadata-exclude-filter \"metaKey2\" \"metaVal2\""
            + " --report-system-checkers"
            + " --skip-device-info true"
            + " --enable-default-logs false"
            + " --enable-token-sharding"
            + " --business-logic-url https://example.com/logic"
            + " --ignore-business-logic-failure"
            + " --module-arg \"mock_module:opt:\\\"val\\\"\""
            + " \"--flag \\\"val\\\"\""
            + " path\\\\to\\\\dir";
    assertThat(shardingArgsMap.get("mock_module"))
        .containsExactly("-m mock_module" + expectedCommonArgs);
    assertThat(shardingArgsMap.get(largeModule))
        .containsExactly(
            String.format(
                "-m %s --shard-count %d --shard-index 0%s",
                largeModule, LARGE_MODULE_SHARDS, expectedCommonArgs),
            String.format(
                "-m %s --shard-count %d --shard-index 1%s",
                largeModule, LARGE_MODULE_SHARDS, expectedCommonArgs),
            String.format(
                "-m %s --shard-count %d --shard-index 2%s",
                largeModule, LARGE_MODULE_SHARDS, expectedCommonArgs),
            String.format(
                "-m %s --shard-count %d --shard-index 3%s",
                largeModule, LARGE_MODULE_SHARDS, expectedCommonArgs),
            String.format(
                "-m %s --shard-count %d --shard-index 4%s",
                largeModule, LARGE_MODULE_SHARDS, expectedCommonArgs));
  }

  @Test
  public void generateShardingArgsMap_csuiteAppCrawl_omitsSkipDeviceInfo() throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("csuite-app-crawl")
                .setCommandLineArgs("csuite-app-crawl")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardingMode(ShardingMode.MODULE)
                .setSkipDeviceInfo(true));

    ImmutableListMultimap<String, String> shardingArgsMap =
        moduleShardingArgsGenerator.generateShardingArgsMap(
            sessionRequestInfo, ImmutableList.of("mock_module"));

    assertThat(shardingArgsMap.get("mock_module"))
        .containsExactly("-m mock_module --skip-preconditions");
  }

  @Test
  public void
      generateShardingArgsMap_strictIncludeFilters_forwardsSpecificityAndIgnoresOtherFilters()
          throws Exception {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardingMode(ShardingMode.MODULE)
                .addAllStrictIncludeFilters(
                    ImmutableList.of("arm64-v8a mock_module1[instant] class#test", "mock_module2"))
                .addAllIncludeFilters(ImmutableList.of("mock_module3"))
                .addAllExcludeFilters(
                    ImmutableList.of("arm64-v8a mock_module1[instant] class#test")));

    ImmutableListMultimap<String, String> shardingArgsMap =
        moduleShardingArgsGenerator.generateShardingArgsMap(
            sessionRequestInfo, ImmutableList.of("mock_module1", "mock_module2", "mock_module3"));

    assertThat(shardingArgsMap.keySet()).containsExactly("mock_module1", "mock_module2");
    assertThat(shardingArgsMap.get("mock_module1"))
        .containsExactly(
            "--strict-include-filter \"arm64-v8a mock_module1[instant] class#test\""
                + " --skip-preconditions");
    assertThat(shardingArgsMap.get("mock_module2"))
        .containsExactly("--strict-include-filter \"mock_module2\" --skip-preconditions");
  }

  @Test
  public void generateShardingArgsMap_strictIncludeFiltersOnLargeModules_shardsOnlyModuleLevel()
      throws Exception {
    ImmutableList<String> largeModules = ShardConstants.LARGE_MODULES.asList();
    String testOnlyLargeModule = largeModules.get(0);
    String abiOnlyLargeModule = largeModules.get(1);
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfoUtil.buildAndValidate(
            SessionRequestInfo.newBuilder()
                .setTestPlan("cts")
                .setCommandLineArgs("cts")
                .setXtsType("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardingMode(ShardingMode.MODULE)
                .addAllStrictIncludeFilters(
                    ImmutableList.of(
                        String.format("arm64-v8a %s class#test", testOnlyLargeModule),
                        String.format("arm64-v8a %s", abiOnlyLargeModule))));

    ImmutableListMultimap<String, String> shardingArgsMap =
        moduleShardingArgsGenerator.generateShardingArgsMap(
            sessionRequestInfo, ImmutableList.of(testOnlyLargeModule, abiOnlyLargeModule));

    assertThat(shardingArgsMap.get(testOnlyLargeModule))
        .containsExactly(
            String.format(
                "--strict-include-filter \"arm64-v8a %s class#test\" --skip-preconditions",
                testOnlyLargeModule));
    assertThat(shardingArgsMap.get(abiOnlyLargeModule))
        .containsExactly(
            String.format(
                "--strict-include-filter \"arm64-v8a %s\" --shard-count %d --shard-index 0"
                    + " --skip-preconditions",
                abiOnlyLargeModule, LARGE_MODULE_SHARDS),
            String.format(
                "--strict-include-filter \"arm64-v8a %s\" --shard-count %d --shard-index 1"
                    + " --skip-preconditions",
                abiOnlyLargeModule, LARGE_MODULE_SHARDS),
            String.format(
                "--strict-include-filter \"arm64-v8a %s\" --shard-count %d --shard-index 2"
                    + " --skip-preconditions",
                abiOnlyLargeModule, LARGE_MODULE_SHARDS),
            String.format(
                "--strict-include-filter \"arm64-v8a %s\" --shard-count %d --shard-index 3"
                    + " --skip-preconditions",
                abiOnlyLargeModule, LARGE_MODULE_SHARDS),
            String.format(
                "--strict-include-filter \"arm64-v8a %s\" --shard-count %d --shard-index 4"
                    + " --skip-preconditions",
                abiOnlyLargeModule, LARGE_MODULE_SHARDS));
  }
}
