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

package com.google.devtools.mobileharness.infra.controller.test.util;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalTestEndingEvent;
import com.google.devtools.mobileharness.shared.util.command.history.CommandHistory;
import com.google.devtools.mobileharness.shared.util.command.history.CommandRecord;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.stream.Stream;

/**
 * Test command history saver for saving the history of all sub processes created in the MH test in
 * a text file.
 *
 * <p>Currently a sub process will be recorded in the file only if:
 *
 * <ol>
 *   <li>It is created by MH command API (CommandExecutor).
 *   <li>It <b>belongs</b> to the MH test. A sub process belongs to a MH test if it matches
 *       <b>any</b> condition of the following:
 *       <ol>
 *         <li>It is created in the test engine process.
 *         <li>It is created in the main thread of the test (so the thread name contains the test
 *             ID).
 *         <li>Its command contains any allocated device ID of the test <b>and</b> its starting time
 *             is after the test starting time.
 *       </ol>
 * </ol>
 *
 * <p>Some command names will be simplified (e.g., ".../.../platform-tools/adb" -> "mhadb").
 *
 * <p>This saver will be loaded as a built-in lab plugin of each test.
 */
public class TestCommandHistorySaver {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String FILE_NAME = "command_history.txt";

  private static final String FIRST_LINE = "start_time(sec) end_time(sec) exit_code command";
  private static final String SECOND_LINE = "0.000 NA NA # testInfo startTime ";

  // ISO_INSTANT formatter which always emits milliseconds, even when zero.
  private static final DateTimeFormatter FORMATTER =
      new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

  private final CommandHistory commandHistory;
  private final boolean saveAllHistory;

  public TestCommandHistorySaver() {
    this(CommandHistory.getInstance(), false);
  }

  @VisibleForTesting
  TestCommandHistorySaver(CommandHistory commandHistory, boolean saveAllHistory) {
    this.commandHistory = commandHistory;
    this.saveAllHistory = saveAllHistory;
  }

  @Subscribe
  @VisibleForTesting
  void onTestEnding(LocalTestEndingEvent testEndingEvent)
      throws MobileHarnessException, IOException {
    TestInfo testInfo = testEndingEvent.getTest();
    Allocation allocation = testEndingEvent.getAllocation();
    testInfo.log().atInfo().alsoTo(logger).log("Saving test command history...");
    // TODO: Remove !saveAllHistory branch after non-container mode is deprecated.
    List<CommandRecord> testCommands =
        saveAllHistory
            ? commandHistory.getAllCommands()
            : commandHistory.searchCommands(
                commandRecord ->
                    commandRecord.threadName().contains(testInfo.locator().getId())
                        || (commandRecord.startTime().isAfter(testInfo.timing().getStartTime())
                            && commandRecord.command().stream()
                                .anyMatch(
                                    command ->
                                        allocation.getAllDevices().stream()
                                            .map(DeviceLocator::id)
                                            .anyMatch(command::contains))));
    ImmutableList<String> result =
        testCommands.stream()
            .map(
                commandRecord ->
                    TestCommandHistorySaver.formatCommandRecord(
                        commandRecord, testInfo.timing().getStartTime()))
            .collect(toImmutableList());
    Files.write(
        Paths.get(testInfo.getGenFileDir(), FILE_NAME),
        Iterables.concat(
            ImmutableList.of(
                FIRST_LINE, SECOND_LINE + FORMATTER.format(testInfo.timing().getStartTime())),
            result));
  }

  private static String formatCommandRecord(CommandRecord commandRecord, Instant testStartTime) {
    Stream.Builder<String> builder = Stream.builder();
    builder.add(formatCommandTime(Duration.between(testStartTime, commandRecord.startTime())));
    builder.add(
        commandRecord
            .endTime()
            .map(endTime -> formatCommandTime(Duration.between(testStartTime, endTime)))
            .orElse("NA"));
    builder.add(
        commandRecord.result().map(result -> Integer.toString(result.exitCode())).orElse("NA"));
    return Stream.concat(builder.build(), formatCommand(commandRecord.command()))
        .collect(joining(" "));
  }

  private static String formatCommandTime(Duration relativeCommandTime) {
    return String.format("%.3f", relativeCommandTime.toMillis() / 1_000.0);
  }

  private static Stream<String> formatCommand(List<String> command) {
    return Stream.concat(
        command.isEmpty() ? Stream.empty() : Stream.of(simplifyExecutable(command.get(0))),
        command.stream().skip(1L));
  }

  private static String simplifyExecutable(String executable) {
    return executable.endsWith("/platform-tools/adb") ? "mhadb" : executable;
  }
}
