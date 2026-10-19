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

package com.google.devtools.mobileharness.platform.android.xts.suite.retry;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.console.command.parser.CommandLineParser;
import com.google.devtools.mobileharness.platform.android.xts.suite.ModuleArg;
import java.util.List;
import java.util.Optional;

/**
 * Carries over the previous session's command line args to a "run retry" session handled by ATS.
 *
 * <p>All args are carried over except the ones cleared in {@link #clearNonPreservableArgs}: what to
 * run (captured by the retry subplan) and invocation specific args (owned by the retry command).
 */
public final class RetryCommandLineArgsPreserver {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private RetryCommandLineArgsPreserver() {}

  /**
   * Returns {@code retrySessionRequestInfo} merged on top of the preservable args of {@code
   * previousSessionCommandLineArgs}: values set in the retry request override the previous ones,
   * and repeated args (e.g. extra args) are appended. Returns {@code retrySessionRequestInfo}
   * unchanged if the previous command line is absent, identical to the retry command line or cannot
   * be parsed.
   */
  public static SessionRequestInfo preservePreviousSessionArgs(
      SessionRequestInfo retrySessionRequestInfo, Optional<String> previousSessionCommandLineArgs) {
    String previousCommandLine = previousSessionCommandLineArgs.map(String::trim).orElse("");
    if (previousCommandLine.isEmpty()
        || previousCommandLine.equals(retrySessionRequestInfo.getCommandLineArgs().trim())) {
      return retrySessionRequestInfo;
    }
    SessionRequestInfo.Builder previous;
    try {
      previous = CommandLineParser.getInstance().parseCommandLine(previousCommandLine);
    } catch (MobileHarnessException | RuntimeException e) {
      logger.atWarning().withCause(e).log(
          "Failed to parse the previous session's command line [%s], its arguments will not be"
              + " preserved in the retry session.",
          previousCommandLine);
      return retrySessionRequestInfo;
    }

    clearNonPreservableArgs(previous);
    // Repeated fields are concatenated by mergeFrom, so drop the previous values the retry command
    // overrides or repeats.
    ImmutableList<String> previousModuleArgs =
        getNotOverriddenModuleArgs(
            previous.getModuleArgsList(), retrySessionRequestInfo.getModuleArgsList());
    previous.clearModuleArgs().addAllModuleArgs(previousModuleArgs);
    if (previous.getExtraArgsList().equals(retrySessionRequestInfo.getExtraArgsList())) {
      previous.clearExtraArgs();
    }

    SessionRequestInfo merged = previous.mergeFrom(retrySessionRequestInfo).build();
    logger.atInfo().log(
        "Retry session args after preserving the previous session's args: module_args=%s,"
            + " extra_args=%s",
        merged.getModuleArgsList(), merged.getExtraArgsList());
    return merged;
  }

  private static void clearNonPreservableArgs(SessionRequestInfo.Builder previous) {
    // What to run, which is captured by the retry subplan.
    previous
        .clearTestPlan()
        .clearModuleNames()
        .clearTestName()
        .clearIncludeFilters()
        .clearExcludeFilters()
        .clearStrictIncludeFilters()
        .clearModuleMetadataIncludeFilters()
        .clearModuleMetadataExcludeFilters()
        .clearExcludeRunners();
    // Invocation specific args, which come from the retry command.
    previous.clearRetryType().clearShardCount().clearShardingMode();
  }

  /** Returns the previous module args which are not overridden by the retry module args. */
  private static ImmutableList<String> getNotOverriddenModuleArgs(
      List<String> previousModuleArgs, List<String> retryModuleArgs) {
    ImmutableSet<String> retryModuleArgIds =
        retryModuleArgs.stream()
            .map(RetryCommandLineArgsPreserver::getModuleArgId)
            .collect(toImmutableSet());
    return previousModuleArgs.stream()
        .filter(moduleArg -> !retryModuleArgIds.contains(getModuleArgId(moduleArg)))
        .collect(toImmutableList());
  }

  /** Returns "module:argName:argKey" of a module arg, or the raw string if it is invalid. */
  private static String getModuleArgId(String moduleArgStr) {
    try {
      ModuleArg moduleArg = ModuleArg.create(moduleArgStr);
      return String.format(
          "%s:%s:%s", moduleArg.moduleName(), moduleArg.argName(), moduleArg.argKey());
    } catch (MobileHarnessException e) {
      return moduleArgStr;
    }
  }
}
