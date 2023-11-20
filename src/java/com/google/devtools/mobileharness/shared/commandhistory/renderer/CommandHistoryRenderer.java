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

package com.google.devtools.mobileharness.shared.commandhistory.renderer;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toJavaDuration;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

import com.google.devtools.mobileharness.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecord;
import com.google.devtools.mobileharness.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecords;
import com.google.devtools.mobileharness.shared.commandhistory.renderer.CommandShortener.CommandShorteningNote;
import com.google.devtools.mobileharness.shared.commandhistory.renderer.CommandShortener.CommandShorteningResult;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Command history renderer for rendering {@linkplain LocalCommandRecords LocalCommandRecords} to a
 * readable string.
 *
 * <p>An example result is:
 *
 * <pre>
 * # notes:
 * # {adb}=...
 * # {test_gen_file}=...
 *
 * #  tid pid  start end  time(s) code cmd
 * 0  14  4681 1.28  1.31 0.03    0    {adb} -H localhost -P 14924 connect localhost:14925
 * 1  21  4691 1.36  1.64 0.28    0    {adb} -H localhost -P 14924 -s localhost:14925 shell printenv
 * </pre>
 */
public class CommandHistoryRenderer {

  private static final String REPORT_TABLE_HEADER = "#\ttid\tpid\tstart\tend\ttime(s)\tcode\tcmd\n";

  public String renderCommandHistory(
      LocalCommandRecords commandHistory, List<CommandShortener> commandShorteners) {
    Duration processStartTime = toJavaDuration(commandHistory.getLocalStartElapsedTime());

    // Groups the records by command sequence number and renders each single command.
    Set<CommandShorteningNote> commandShorteningHistory = new HashSet<>();
    String table =
        commandHistory.getRecordList().stream()
            .collect(
                groupingBy(
                    LocalCommandRecord::getLocalCommandSequenceNumber,
                    LinkedHashMap::new,
                    collectingAndThen(
                        toImmutableList(),
                        subprocess ->
                            renderSingleSubprocess(
                                subprocess,
                                processStartTime,
                                commandShorteners,
                                commandShorteningHistory))))
            .values()
            .stream()
            .collect(joining("\n", REPORT_TABLE_HEADER, /* suffix= */ ""));

    // Renders command shortening history.
    if (commandShorteningHistory.isEmpty()) {
      return table;
    } else {
      String notes = renderCommandShorteningHistory(commandShorteningHistory);
      return notes + table;
    }
  }

  private static String renderSingleSubprocess(
      List<LocalCommandRecord> commandRecords,
      Duration processStartTime,
      List<CommandShortener> commandShorteners,
      Set<CommandShorteningNote> commandShorteningHistory) {
    long commandSequenceNumber = commandRecords.get(0).getLocalCommandSequenceNumber();

    Optional<LocalCommandRecord> commandStarted =
        commandRecords.stream().filter(LocalCommandRecord::hasCommandStartedEvent).findFirst();
    Optional<LocalCommandRecord> commandEnded =
        commandRecords.stream().filter(LocalCommandRecord::hasCommandEndedEvent).findFirst();

    // Calculates relative command start/end time.
    Optional<Duration> startTime =
        commandStarted.map(
            started -> toJavaDuration(started.getLocalElapsedTime()).minus(processStartTime));
    Optional<Duration> endTime =
        commandEnded.map(
            ended -> toJavaDuration(ended.getLocalElapsedTime()).minus(processStartTime));
    Optional<Duration> diffTime = startTime.flatMap(start -> endTime.map(end -> end.minus(start)));

    return String.format(
        "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s",
        commandSequenceNumber,
        commandStarted
            .map(
                started ->
                    Long.toString(
                        started.getCommandStartedEvent().getInvocationInfo().getThreadId()))
            .orElse("na"),
        commandStarted
            .filter(started -> started.getCommandStartedEvent().hasStartSuccess())
            .map(
                started ->
                    Long.toString(started.getCommandStartedEvent().getStartSuccess().getPid()))
            .orElse("na"),
        startTime.map(CommandHistoryRenderer::formatTime).orElse("na"),
        endTime.map(CommandHistoryRenderer::formatTime).orElse("na"),
        diffTime.map(CommandHistoryRenderer::formatTime).orElse("na"),
        commandEnded
            .map(ended -> Integer.toString(ended.getCommandEndedEvent().getExitCode()))
            .orElse("na"),
        commandStarted
            .map(
                started ->
                    formatCommand(
                        started.getCommandStartedEvent().getCommandList(),
                        commandShorteners,
                        commandShorteningHistory))
            .orElse("na"));
  }

  @SuppressWarnings("DurationSecondsToDouble")
  private static String formatTime(Duration time) {
    return String.format("%.2f", time.getSeconds() + time.getNano() / 1e9);
  }

  private static String formatCommand(
      List<String> command,
      List<CommandShortener> commandShorteners,
      Set<CommandShorteningNote> commandShorteningHistory) {
    // Applies each command shortener to each command argument.
    return command.stream()
        .map(
            commandArgument ->
                shortenCommandArgument(
                    commandArgument, commandShorteners, commandShorteningHistory))
        .collect(joining(" "));
  }

  private static String shortenCommandArgument(
      String commandArgument,
      List<CommandShortener> commandShorteners,
      Set<CommandShorteningNote> commandShorteningHistory) {
    for (CommandShortener shortener : commandShorteners) {
      Optional<CommandShorteningResult> commandShorteningResult =
          shortener.shortenCommand(commandArgument);
      if (commandShorteningResult.isPresent()) {
        commandArgument = commandShorteningResult.get().resultCommand();
        commandShorteningHistory.add(commandShorteningResult.get().note());
      }
    }
    return commandArgument;
  }

  private static String renderCommandShorteningHistory(
      Set<CommandShorteningNote> commandShorteningHistory) {
    return commandShorteningHistory.stream()
        .sorted(
            comparing(CommandShorteningNote::toPart).thenComparing(CommandShorteningNote::fromPart))
        .map(
            commandShorteningResult ->
                String.format(
                    "# %s=%s",
                    commandShorteningResult.toPart(), commandShorteningResult.fromPart()))
        .collect(joining("\n", "# notes:\n", "\n\n"));
  }
}
