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

package com.google.devtools.mobileharness.platform.android.dropbox;

import static com.google.devtools.mobileharness.platform.android.dropbox.DropboxTag.SYSTEM_TOMBSTONE;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Parser for dumpsys dropbox output which should used on a single command. */
class DropboxParser implements LineCallback {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  // Source:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/DropBoxManagerService.java
  private static final Pattern DROPBOX_ENTRY_SEPARATOR = Pattern.compile("^={40}$");
  private static final Pattern DROPBOX_ENTRY_PROCESS_LINE = Pattern.compile("^Process: (.+)");
  private static final Pattern DROPBOX_ENTRY_TOMBSTONE_PROCESS =
      Pattern.compile("^pid: \\d+, tid: \\d+, name: .+ {2}>>> (.+) <<<");

  private final List<DropboxEntry> entries = new ArrayList<>();

  // Variables below hold state to distinguish between different dropbox entries as dropbox can
  // contain multiple values.
  private boolean entryDetected = false;
  private boolean entrySeparatorDetected = false;
  private List<String> entryLines = new ArrayList<>();
  private Optional<String> entryPackageName = Optional.empty();
  private Optional<LocalDateTime> entryTimestamp = Optional.empty();

  private final DropboxTag tag;
  private final Pattern firstEntryLinePattern;

  DropboxParser(DropboxTag tag) {
    this.tag = tag;
    this.firstEntryLinePattern = firstEntryLineRegex(tag);
  }

  private static Pattern firstEntryLineRegex(DropboxTag tag) {
    return Pattern.compile(String.format("^([\\d\\-]+ [\\d\\:]+) %s .*", tag));
  }

  private void addEntryAndResetState() {
    if (entryTimestamp.isEmpty() || entryPackageName.isEmpty() || entryLines.isEmpty()) {
      logger.atWarning().log(
          "Parsing entry failed. Ignoring entry."
              + "\nTimestamp: %s\npackageName: %s\nentrySize=%d",
          entryTimestamp.orElse(LocalDateTime.MIN),
          entryPackageName.orElse("Absent package name"),
          entryLines.size());
    } else {
      entries.add(new DropboxEntry(entryTimestamp.get(), entryPackageName.get(), tag, entryLines));
    }
    entryDetected = false;
    entryTimestamp = Optional.empty();
    entryLines = new ArrayList<>();
    entryPackageName = Optional.empty();
  }

  @Override
  public Response onLine(String line) {
    if (DROPBOX_ENTRY_SEPARATOR.matcher(line).matches()) {
      if (entrySeparatorDetected) { // New entry
        addEntryAndResetState();
        return Response.empty();
      }
      entrySeparatorDetected = true; // First entry
    }
    var firstEntryLineMatcher = firstEntryLinePattern.matcher(line);
    if (entrySeparatorDetected && firstEntryLineMatcher.matches()) {
      entryDetected = true;
      String timestamp = firstEntryLineMatcher.group(1);
      try {
        entryTimestamp = Optional.of(LocalDateTime.parse(timestamp, DATE_TIME_FORMATTER));
      } catch (DateTimeParseException ex) {
        logger.atWarning().withCause(ex).log(
            "Error parsing datetime from dropbox line: %s. Entry will be not be processed. Line:"
                + " %s",
            timestamp, line);
      }
      entryLines.add(line);
      return Response.empty();
    }
    if (entryDetected) {
      // Tombstone entries do not have an explicit "Process: <package ID>" line.
      // They are in a different line.
      if (tag == SYSTEM_TOMBSTONE) {
        var tombstoneMatcher = DROPBOX_ENTRY_TOMBSTONE_PROCESS.matcher(line);
        if (tombstoneMatcher.matches()) {
          String pkgName = tombstoneMatcher.group(1);
          entryPackageName = Optional.of(pkgName);
        }
      } else {
        // data_app_anr, data_app_crash, data_app_native_crash,
        // system_app_anr, system_app_crash, system_app_native_crash
        // all have a "Process: <package ID>" line in their entry.
        var matcher = DROPBOX_ENTRY_PROCESS_LINE.matcher(line);
        if (matcher.matches()) {
          var pkgName = matcher.group(1);
          entryPackageName = Optional.of(pkgName);
        }
      }
      entryLines.add(line);
    }
    return Response.empty();
  }

  ImmutableList<DropboxEntry> getDropboxEntries() {
    // Last entry needs to be explicitly added.
    if (entryDetected) {
      addEntryAndResetState();
    }
    return ImmutableList.copyOf(entries);
  }

  record DropboxEntry(
      LocalDateTime timestamp, String packageName, DropboxTag tag, List<String> data) {}
}
