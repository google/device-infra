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

package com.google.devtools.mobileharness.infra.ats.console.util.log;

import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleTextStyle;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord.SourceType;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Printer for printing {@link LogRecord}s to the console. */
@Singleton
public class LogRecordPrinter {

  private final ConsoleUtil consoleUtil;
  private final int minLogRecordImportance;

  @Inject
  LogRecordPrinter(ConsoleUtil consoleUtil) {
    this.consoleUtil = consoleUtil;
    this.minLogRecordImportance =
        Flags.instance().atsConsoleOlcServerMinLogRecordImportance.getNonNull();
  }

  public void printLogRecord(LogRecord logRecord) {
    if (logRecord.getImportance() >= minLogRecordImportance) {
      consoleUtil.printlnDirect(
          logRecord.getFormattedLogRecord(), getLogRecordStyle(logRecord), System.err);
    }
  }

  private static ConsoleTextStyle getLogRecordStyle(LogRecord logRecord) {
    if (logRecord.getSourceType() == SourceType.TF) {
      return ConsoleTextStyle.TF_STDOUT;
    } else {
      return ConsoleTextStyle.OLC_SERVER_LOG;
    }
  }
}
