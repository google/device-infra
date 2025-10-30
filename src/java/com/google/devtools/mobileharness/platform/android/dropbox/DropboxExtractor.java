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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.dropbox.DropboxParser.DropboxEntry;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import javax.inject.Inject;

/** Class to extract dropbox entries from the device. */
public class DropboxExtractor {
  private static final Duration DUMPSYS_COMMAND_TIMEOUT = Duration.ofSeconds(30);

  private final Adb adb;
  private final LocalFileUtil fileUtil;

  @Inject
  DropboxExtractor(Adb adb, LocalFileUtil fileUtil) {
    this.adb = adb;
    this.fileUtil = fileUtil;
  }

  /**
   * Extract dropbox entries generated on the device.
   *
   * @param deviceId UDID of the device
   * @param packagestoMonitor list of packages which generated dropbox entries
   * @param tags list of dropbox tags to scan for
   * @param outputDir directory on the host to write the dropbox entries
   * @param minimumTimestamp look for entries generated only after this time.
   */
  public void extract(
      String deviceId,
      ImmutableSet<String> packagestoMonitor,
      ImmutableSet<DropboxTag> tags,
      LocalDateTime minimumTimestamp,
      Path outputDir)
      throws InterruptedException, MobileHarnessException {
    var entriesBuilder = ImmutableList.<DropboxEntry>builder();
    for (var tag : tags) {
      entriesBuilder.addAll(extractForTag(deviceId, tag));
    }
    var filteredEntries =
        entriesBuilder.build().stream()
            .filter(entry -> entry.timestamp().isAfter(minimumTimestamp))
            .filter(entry -> packagestoMonitor.contains(entry.packageName()))
            .collect(toImmutableList());

    for (var i = 0; i < filteredEntries.size(); i++) {
      var entry = filteredEntries.get(i);
      var fileName =
          String.format(
              Locale.ENGLISH,
              "%s_%d_%s.txt",
              entry.tag(),
              i,
              entry.packageName().replace('.', '_'));
      var fullPath = outputDir.resolve(fileName);
      fileUtil.writeToFile(fullPath.toString(), String.join(System.lineSeparator(), entry.data()));
    }
  }

  private ImmutableList<DropboxEntry> extractForTag(String deviceId, DropboxTag tag)
      throws InterruptedException, MobileHarnessException {
    var dropboxParser = new DropboxParser(tag);
    String unused =
        adb.runShell(
            deviceId, "dumpsys dropbox --print " + tag, DUMPSYS_COMMAND_TIMEOUT, dropboxParser);
    return dropboxParser.getDropboxEntries();
  }
}
