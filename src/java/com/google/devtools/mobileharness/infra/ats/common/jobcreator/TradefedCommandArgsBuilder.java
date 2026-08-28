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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Builder to generate Tradefed command line arguments from {@link SessionRequestInfo}. */
class TradefedCommandArgsBuilder {

  private SessionRequestInfo sessionRequestInfo;
  private ImmutableList<String> tfModules = ImmutableList.of();
  private boolean useTfRetry;
  private boolean shouldSkipDeviceInfoForRetry;

  static TradefedCommandArgsBuilder builder() {
    return new TradefedCommandArgsBuilder();
  }

  @CanIgnoreReturnValue
  TradefedCommandArgsBuilder setSessionRequestInfo(SessionRequestInfo sessionRequestInfo) {
    this.sessionRequestInfo = sessionRequestInfo;
    return this;
  }

  @CanIgnoreReturnValue
  TradefedCommandArgsBuilder setTfModules(List<String> tfModules) {
    this.tfModules = ImmutableList.copyOf(tfModules);
    return this;
  }

  @CanIgnoreReturnValue
  TradefedCommandArgsBuilder setUseTfRetry(boolean useTfRetry) {
    this.useTfRetry = useTfRetry;
    return this;
  }

  @CanIgnoreReturnValue
  TradefedCommandArgsBuilder setShouldSkipDeviceInfoForRetry(boolean shouldSkipDeviceInfoForRetry) {
    this.shouldSkipDeviceInfoForRetry = shouldSkipDeviceInfoForRetry;
    return this;
  }

  String build() {
    checkNotNull(sessionRequestInfo, "sessionRequestInfo must be set");
    return Joiner.on(' ')
        .join(
            Streams.concat(
                    getModuleFilters().stream(),
                    getTestNameArg().stream(),
                    getShardCountArg().stream(),
                    getFilterArgs(),
                    getMetadataFilters(),
                    getSystemCheckersAndLogs(),
                    getExecutionFlags(),
                    getModuleAndExtraArgs())
                .collect(toImmutableList()));
  }

  private ImmutableList<String> getModuleFilters() {
    if (SessionRequestHandlerUtil.isRunRetry(sessionRequestInfo.getTestPlan())) {
      if (useTfRetry) {
        // For "run retry" command handled by TF, pass the original modules to TF
        return sessionRequestInfo.getModuleNamesList().stream()
            .map(module -> String.format("-m %s", module))
            .collect(toImmutableList());
      } else {
        // For "run retry" command handled by the console, the given modules have been processed
        // when generating the subplan above, no need to pass these again to underneath TF
        return ImmutableList.of();
      }
    } else {
      return sessionRequestInfo.getModuleNamesList().isEmpty()
          ? ImmutableList.of()
          : tfModules.stream()
              .map(module -> String.format("-m %s", module))
              .collect(toImmutableList());
    }
  }

  private Optional<String> getTestNameArg() {
    return sessionRequestInfo.hasTestName()
        ? Optional.of(String.format("-t \"%s\"", sessionRequestInfo.getTestName()))
        : Optional.empty();
  }

  private Optional<String> getShardCountArg() {
    return sessionRequestInfo.hasShardCount() && sessionRequestInfo.getShardCount() > 0
        ? Optional.of(String.format("--shard-count %s", sessionRequestInfo.getShardCount()))
        : Optional.empty();
  }

  private Stream<String> getFilterArgs() {
    String testPlan = sessionRequestInfo.getTestPlan();
    boolean isRunRetry = SessionRequestHandlerUtil.isRunRetry(testPlan);
    boolean skipIncludeExcludeFilters =
        (!useTfRetry && isRunRetry) || !sessionRequestInfo.getStrictIncludeFiltersList().isEmpty();
    boolean skipStrictIncludeFilters = !useTfRetry && isRunRetry;

    Stream<String> includeFilters =
        skipIncludeExcludeFilters
            ? Stream.empty()
            : sessionRequestInfo.getIncludeFiltersList().stream()
                .map(filter -> String.format("--include-filter \"%s\"", filter));

    Stream<String> strictIncludeFilters =
        skipStrictIncludeFilters
            ? Stream.empty()
            : sessionRequestInfo.getStrictIncludeFiltersList().stream()
                .map(filter -> String.format("--strict-include-filter \"%s\"", filter));

    Stream<String> excludeFilters =
        skipIncludeExcludeFilters
            ? Stream.empty()
            : sessionRequestInfo.getExcludeFiltersList().stream()
                .map(filter -> String.format("--exclude-filter \"%s\"", filter));

    return Streams.concat(includeFilters, strictIncludeFilters, excludeFilters);
  }

  private Stream<String> getMetadataFilters() {
    Stream<String> includeMetadata =
        sessionRequestInfo.getModuleMetadataIncludeFiltersMap().entrySet().stream()
            .flatMap(
                entry ->
                    entry.getValue().getValuesList().stream()
                        .map(
                            value ->
                                String.format(
                                    "--module-metadata-include-filter \"%s\" \"%s\"",
                                    entry.getKey(), value)));

    Stream<String> excludeMetadata =
        sessionRequestInfo.getModuleMetadataExcludeFiltersMap().entrySet().stream()
            .flatMap(
                entry ->
                    entry.getValue().getValuesList().stream()
                        .map(
                            value ->
                                String.format(
                                    "--module-metadata-exclude-filter \"%s\" \"%s\"",
                                    entry.getKey(), value)));

    return Streams.concat(includeMetadata, excludeMetadata);
  }

  private Stream<String> getSystemCheckersAndLogs() {
    Stream.Builder<String> builder = Stream.builder();
    if (sessionRequestInfo.getReportSystemCheckers()) {
      builder.add("--report-system-checkers");
    }

    getSkipDeviceInfoArg().ifPresent(builder::add);

    if (sessionRequestInfo.hasEnableDefaultLogs()) {
      builder.add(
          String.format(
              "--enable-default-logs %s",
              sessionRequestInfo.getEnableDefaultLogs() ? "true" : "false"));
    }
    return builder.build();
  }

  private Optional<String> getSkipDeviceInfoArg() {
    // TODO Temporary solution to unblock app compat test post processing. This command
    // does not recognize skipDeviceInfoArg flag, so remove from command args list.
    // SessionRequestInfo still need this flag so that the result processing can ignore build
    // fingerprint check.
    if (sessionRequestInfo.getTestPlan().equals("csuite-app-crawl")) {
      return Optional.empty();
    }
    if (sessionRequestInfo.hasSkipDeviceInfo()) {
      return Optional.of(
          String.format("--skip-device-info %s", sessionRequestInfo.getSkipDeviceInfo()));
    }
    if (shouldSkipDeviceInfoForRetry) {
      return Optional.of("--skip-device-info true");
    }
    return Optional.empty();
  }

  private Stream<String> getExecutionFlags() {
    Stream.Builder<String> builder = Stream.builder();
    if (sessionRequestInfo.getEnableTokenSharding()) {
      builder.add("--enable-token-sharding");
    }
    if (sessionRequestInfo.hasBusinessLogicUrl()) {
      builder.add(
          String.format("--business-logic-url %s", sessionRequestInfo.getBusinessLogicUrl()));
    }
    if (sessionRequestInfo.getIgnoreBusinessLogicFailure()) {
      builder.add("--ignore-business-logic-failure");
    }
    return builder.build();
  }

  private Stream<String> getModuleAndExtraArgs() {
    Stream<String> moduleArgs =
        sessionRequestInfo.getModuleArgsList().stream()
            .map(arg -> String.format("--module-arg \"%s\"", escapeQuotes(arg)));

    Stream<String> extraArgs =
        sessionRequestInfo.getExtraArgsList().stream()
            .map(TradefedCommandArgsBuilder::formatExtraArg);

    return Streams.concat(moduleArgs, extraArgs);
  }

  private static String formatExtraArg(String arg) {
    String escaped = escapeQuotes(arg.replace("\\", "\\\\"));
    return escaped.contains(" ") ? String.format("\"%s\"", escaped) : escaped;
  }

  private static String escapeQuotes(String arg) {
    return arg.replace("\"", "\\\"");
  }
}
