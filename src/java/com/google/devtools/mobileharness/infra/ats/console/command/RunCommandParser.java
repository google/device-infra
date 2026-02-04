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

package com.google.devtools.mobileharness.infra.ats.console.command;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.RunCommandParsingResultFuture;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;

/** Parser for parsing {@link RunCommand} options. */
@Command(name = "run-command-parser", description = "Parse options of RunCommand.")
public class RunCommandParser implements Callable<Integer> {

  @Mixin private RunCommandOptions options;

  private final Consumer<ListenableFuture<SessionRequestInfo.Builder>> resultFuture;

  @Inject
  RunCommandParser(
      @RunCommandParsingResultFuture
          Consumer<ListenableFuture<SessionRequestInfo.Builder>> resultFuture) {
    this.resultFuture = resultFuture;
  }

  @Override
  public Integer call() throws MobileHarnessException, InterruptedException {
    try {
      // TODO: Add additional command line options from ats console.
      resultFuture.accept(immediateFuture(createParseResult()));
      return ExitCode.OK;
    } catch (RuntimeException | Error e) {
      resultFuture.accept(immediateFailedFuture(e));
      return ExitCode.SOFTWARE;
    } finally {
      options.moduleTestOptionsGroups = null; // Resets the group to clear the history
    }
  }

  private SessionRequestInfo.Builder createParseResult() throws MobileHarnessException {
    SessionRequestInfo.Builder sessionRequestBuilder = SessionRequestInfo.builder();
    options.validateCommandParameters();
    sessionRequestBuilder
        .setTestPlan(options.config)
        .setModuleNames(options.getModules())
        .setIncludeFilters(
            this.options.includeFilters == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(this.options.includeFilters))
        .setExcludeFilters(
            this.options.excludeFilters == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(this.options.excludeFilters))
        .setStrictIncludeFilters(
            this.options.strictIncludeFilters == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(this.options.strictIncludeFilters))
        .setModuleMetadataIncludeFilters(
            this.options.moduleMetadataIncludeFilters == null
                ? ImmutableMultimap.of()
                : ImmutableMultimap.copyOf(this.options.moduleMetadataIncludeFilters))
        .setModuleMetadataExcludeFilters(
            this.options.moduleMetadataExcludeFilters == null
                ? ImmutableMultimap.of()
                : ImmutableMultimap.copyOf(this.options.moduleMetadataExcludeFilters))
        .setHtmlInZip(options.htmlInZip);
    if (this.options.shardCount > 0) {
      sessionRequestBuilder.setShardCount(this.options.shardCount);
    }
    if (!this.options.getTest().isEmpty()) {
      sessionRequestBuilder.setTestName(this.options.getTest());
    }
    ImmutableList<String> moduleArgs =
        options.moduleCmdArgs != null
            ? ImmutableList.copyOf(options.moduleCmdArgs)
            : ImmutableList.of();
    ImmutableList<String> extraArgs =
        options.extraRunCmdArgs != null
            ? ImmutableList.copyOf(options.extraRunCmdArgs)
            : ImmutableList.of();
    ImmutableSet<String> excludeRunners =
        options.excludeRunnerOpt != null
            ? ImmutableSet.copyOf(options.excludeRunnerOpt)
            : ImmutableSet.of();
    if (this.options.retryType != null) {
      sessionRequestBuilder.setRetryType(
          RetryType.valueOf(Ascii.toUpperCase(this.options.retryType.name())));
    }
    if (options.isSkipDeviceInfo().isPresent()) {
      sessionRequestBuilder.setSkipDeviceInfo(options.isSkipDeviceInfo().get());
    }
    if (options.enableDefaultLogs != null) {
      sessionRequestBuilder.setEnableDefaultLogs(options.enableDefaultLogs);
    }
    sessionRequestBuilder.setEnableTokenSharding(options.enableTokenSharding);

    return sessionRequestBuilder
        .setModuleArgs(moduleArgs)
        .setExtraArgs(extraArgs)
        .setExcludeRunners(excludeRunners);
  }
}
