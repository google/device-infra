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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.ShardConstants;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import java.util.HashMap;
import java.util.stream.Stream;
import javax.inject.Inject;

/** A generator to generate sharding args for the sharding mode - {@code ShardingMode.MODULE}. */
class ModuleShardingArgsGenerator {

  // Shard number for a single shared module
  @VisibleForTesting static final int SHARD_MODULE_SHARDS = 10;

  // Shard number for a single large module
  @VisibleForTesting static final int LARGE_MODULE_SHARDS = 5;

  private final SessionRequestHandlerUtil sessionRequestHandlerUtil;

  @Inject
  ModuleShardingArgsGenerator(SessionRequestHandlerUtil sessionRequestHandlerUtil) {
    this.sessionRequestHandlerUtil = sessionRequestHandlerUtil;
  }

  @VisibleForTesting
  ImmutableSet<String> generateShardingArgs(
      SessionRequestInfo sessionRequestInfo, ImmutableList<String> tfModules)
      throws MobileHarnessException {
    ImmutableSet<String> targetModules =
        tfModules.isEmpty()
            ? ImmutableSet.<String>builder()
                .addAll(sessionRequestHandlerUtil.getAllLocalTradefedModules(sessionRequestInfo))
                .addAll(sessionRequestHandlerUtil.getStaticMctsModules())
                .build()
            : ImmutableSet.copyOf(tfModules);

    ImmutableList<SuiteTestFilter> originalIncludeFilters =
        sessionRequestInfo.includeFilters().stream()
            .map(SuiteTestFilter::create)
            .collect(toImmutableList());
    ImmutableList<SuiteTestFilter> originalExcludeFilters =
        sessionRequestInfo.excludeFilters().stream()
            .map(SuiteTestFilter::create)
            .collect(toImmutableList());

    ImmutableSet.Builder<String> shardingArgs = ImmutableSet.builder();

    // Shard the shared modules to jobs by default.
    for (String module : ShardConstants.SHARD_MODULES) {
      if (targetModules.contains(module)) {
        shardingArgs.addAll(
            generateShardingArgsForModule(
                module,
                sessionRequestInfo,
                originalIncludeFilters,
                originalExcludeFilters,
                SHARD_MODULE_SHARDS));
      }
    }

    // Make a module with a specific parameter per job for large modules.
    for (String module : ShardConstants.LARGE_MODULES) {
      if (targetModules.contains(module)) {
        shardingArgs.addAll(
            generateShardingArgsForModule(
                module,
                sessionRequestInfo,
                originalIncludeFilters,
                originalExcludeFilters,
                LARGE_MODULE_SHARDS));
      }
    }

    // Make a module with all parameters per job for other modules.
    for (String module : targetModules) {
      if (!ShardConstants.SHARD_MODULES.contains(module)
          && !ShardConstants.LARGE_MODULES.contains(module)) {
        shardingArgs.addAll(
            generateShardingArgsForModule(
                module,
                sessionRequestInfo,
                originalIncludeFilters,
                originalExcludeFilters,
                /* shardCount= */ 1));
      }
    }

    return shardingArgs.build();
  }

  private ImmutableSet<String> generateShardingArgsForModule(
      String module,
      SessionRequestInfo sessionRequestInfo,
      ImmutableList<SuiteTestFilter> originalIncludeFilters,
      ImmutableList<SuiteTestFilter> originalExcludeFilters,
      int shardCount) {
    ImmutableSet.Builder<String> shardingArgs = ImmutableSet.builder();
    ImmutableList<String> extraArgs = sessionRequestInfo.extraArgs();

    ImmutableList<SuiteTestFilter> matchedIncludeFilters =
        findMatchedFilters(module, originalIncludeFilters);
    if (!originalIncludeFilters.isEmpty() && matchedIncludeFilters.isEmpty()) {
      // No matched include filters. Skip this module.
      return ImmutableSet.of();
    }

    ImmutableList<SuiteTestFilter> matchedExcludeFilters =
        findMatchedFilters(module, originalExcludeFilters);

    if (shardCount > 1 && !includeTestOnly(matchedIncludeFilters)) {
      for (int index = 0; index < shardCount; index++) {
        String sessionRequestInfoArgs =
            createSessionRequestInfoArgs(
                matchedIncludeFilters, matchedExcludeFilters, extraArgs, module, shardCount, index);
        shardingArgs.add(sessionRequestInfoArgs);
      }
    } else {
      String sessionRequestInfoArgs =
          createSessionRequestInfoArgs(
              matchedIncludeFilters,
              matchedExcludeFilters,
              extraArgs,
              module,
              /* shardCount= */ 1,
              /* shardIndex= */ 0);
      shardingArgs.add(sessionRequestInfoArgs);
    }
    // }
    return shardingArgs.build();
  }

  /** Find all matched filters for a module. */
  private static ImmutableList<SuiteTestFilter> findMatchedFilters(
      String module, ImmutableList<SuiteTestFilter> filters) {
    return filters.stream()
        .filter(filter -> filter.matchModuleName(module))
        .collect(toImmutableList());
  }

  /** Check if given include filters include tests only. */
  @VisibleForTesting
  boolean includeTestOnly(ImmutableList<SuiteTestFilter> includeFilters) {
    if (includeFilters.isEmpty()) {
      // No include filters means all modules will be included.
      return false;
    }

    HashMap<String, HashMap<String, Boolean>> moduleToTestOnlyMap = new HashMap<>();
    for (SuiteTestFilter filter : includeFilters) {
      String moduleName = filter.moduleName();
      String abi = filter.abi().orElse("");
      HashMap<String, Boolean> abiToTestOnlyMap =
          moduleToTestOnlyMap.computeIfAbsent(moduleName, k -> new HashMap<>());

      if (abi.isEmpty()) {
        if (filter.testName().isPresent()) {
          abiToTestOnlyMap.put(abi, true);
          abiToTestOnlyMap.keySet().forEach(abiKey -> abiToTestOnlyMap.put(abiKey, true));
        } else {
          abiToTestOnlyMap.computeIfAbsent(abi, k -> false);
        }
      } else {
        if (filter.testName().isPresent()) {
          abiToTestOnlyMap.put(abi, true);
        } else {
          // Include filters without ABIs specified will be applied to all ABIs.
          boolean testOnlyForAllAbis = abiToTestOnlyMap.getOrDefault("", false);
          abiToTestOnlyMap.computeIfAbsent(abi, k -> testOnlyForAllAbis);
        }
      }
    }

    // Return if all modules are test only.
    return moduleToTestOnlyMap.values().stream()
        .allMatch(abiToTestOnly -> abiToTestOnly.values().stream().allMatch(testOnly -> testOnly));
  }

  private static String createSessionRequestInfoArgs(
      ImmutableList<SuiteTestFilter> includeFilters,
      ImmutableList<SuiteTestFilter> excludeFilters,
      ImmutableList<String> extraArgs,
      String moduleName,
      int shardCount,
      int shardIndex) {
    return Joiner.on(' ')
        .join(
            Streams.concat(
                    Stream.of(String.format("-m %s", moduleName)),
                    includeFilters.stream()
                        .map(
                            includeFilter ->
                                String.format(
                                    "--include-filter \"%s\"", includeFilter.filterString())),
                    excludeFilters.stream()
                        .map(
                            excludeFilter ->
                                String.format(
                                    "--exclude-filter \"%s\"", excludeFilter.filterString())),
                    shardCount > 1
                        ? Stream.concat(
                            Stream.of(String.format("--shard-count %s", shardCount)),
                            Stream.of(String.format("--shard-index %s", shardIndex)))
                        : Stream.empty(),
                    extraArgs.stream()
                        .map(arg -> arg.contains(" ") ? String.format("\"%s\"", arg) : arg))
                .collect(toImmutableList()));
  }
}
