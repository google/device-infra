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

package com.google.devtools.deviceaction.common.utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.history.CommandRecord;
import com.google.devtools.mobileharness.shared.util.command.history.CommandRecorderListener;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/** A {@link CommandRecorderListener} to record the commands to a file. */
public class CommandHistoryWriter implements CommandRecorderListener {

  /** The output file to save all command history. */
  private static final String FILE_NAME = "command_history.txt";

  private static final String CHECK_IF_DEVICE_IS_READY = "am broadcast  check.if.device.is.ready";

  private static final String SYS_BOOT_COMPLETED = "getprop sys.boot_completed";

  private static final String DEV_BOOTCOMPLETE = "getprop dev.bootcomplete";

  /** Common commands to skip to avoid large history file. */
  private static final ImmutableList<String> SKIP_COMMANDS =
      ImmutableList.of(CHECK_IF_DEVICE_IS_READY, SYS_BOOT_COMPLETED, DEV_BOOTCOMPLETE);

  private static final String ARG_SEPARATOR = " ";

  private final Path outputFile;

  private final LocalFileUtil localFileUtil;

  @Inject
  @SuppressWarnings("UnnecessarilyVisible")
  public CommandHistoryWriter(ResourceHelper helper, LocalFileUtil localFileUtil)
      throws DeviceActionException {
    this(helper.getGenFileDir(), localFileUtil);
  }

  @VisibleForTesting
  CommandHistoryWriter(Path dir, LocalFileUtil localFileUtil) {
    this.outputFile = dir.resolve(FILE_NAME);
    this.localFileUtil = localFileUtil;
  }

  public void init() throws DeviceActionException {
    try {
      this.localFileUtil.resetFile(outputFile.toString());
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(
          "CREATE_FILE_ERROR",
          ErrorType.DEPENDENCY_ISSUE,
          "Failed to create the file " + outputFile,
          e);
    }
  }

  /** Appends new command and its result to the command history file. */
  @Override
  public void onAddCommandResult(CommandRecord commandRecord, CommandResult result) {
    List<String> records = getRecords(commandRecord, result);
    try {
      Files.write(outputFile, records, StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<String> getRecords(CommandRecord commandRecord, CommandResult result) {
    List<String> records = new ArrayList<>();
    if (needRecord(commandRecord)) {
      records.add(commandRecord.startTime().toString());
      records.add(Joiner.on(ARG_SEPARATOR).join(commandRecord.command()));
      records.add(result.toString());
      records.add("\n");
    }
    return records;
  }

  static boolean needRecord(CommandRecord commandRecord) {
    return commandRecord.command().stream().noneMatch(SKIP_COMMANDS::contains);
  }
}
