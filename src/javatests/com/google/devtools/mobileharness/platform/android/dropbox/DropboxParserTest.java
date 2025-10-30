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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.dropbox.DropboxParser.DropboxEntry;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DropboxParserTest {

  private static final String SYSTEM_TOMBSTONE_FILE =
      "javatests/com/google/devtools/mobileharness/platform/android/dropbox/testdata/system_tombstone.txt";
  private static final String DATA_APP_ANR_FILE =
      "javatests/com/google/devtools/mobileharness/platform/android/dropbox/testdata/data_app_anr.txt";
  private static final String DATA_APP_CRASH_FILE =
      "javatests/com/google/devtools/mobileharness/platform/android/dropbox/testdata/data_app_crash.txt";
  private static final String DATA_APP_NATIVE_CRASH_FILE =
      "javatests/com/google/devtools/mobileharness/platform/android/dropbox/testdata/data_app_native_crash.txt";

  @Test
  public void getDropboxEntries_dataAppNativeCrash() throws Exception {
    var parser = new DropboxParser(DropboxTag.DATA_APP_NATIVE_CRASH);
    List<String> lines =
        Files.readAllLines(Path.of(RunfilesUtil.getRunfilesLocation(DATA_APP_NATIVE_CRASH_FILE)));
    for (String line : lines) {
      var unused = parser.onLine(line);
    }

    ImmutableList<DropboxEntry> entries = parser.getDropboxEntries();
    assertThat(entries).hasSize(2);

    assertThat(entries.get(0).tag()).isEqualTo(DropboxTag.DATA_APP_NATIVE_CRASH);
    assertThat(entries.get(0).timestamp()).isEqualTo(LocalDateTime.of(2025, 10, 24, 15, 13, 55));
    assertThat(entries.get(0).packageName()).isEqualTo("com.test.app.native");

    assertThat(entries.get(1).tag()).isEqualTo(DropboxTag.DATA_APP_NATIVE_CRASH);
    assertThat(entries.get(1).timestamp()).isEqualTo(LocalDateTime.of(2025, 10, 24, 15, 46, 14));
    assertThat(entries.get(1).packageName()).isEqualTo("com.test.app.native1");
  }

  @Test
  public void getDropboxEntries_dataAppCrash() throws Exception {
    var parser = new DropboxParser(DropboxTag.DATA_APP_CRASH);
    List<String> lines =
        Files.readAllLines(Path.of(RunfilesUtil.getRunfilesLocation(DATA_APP_CRASH_FILE)));
    for (String line : lines) {
      var unused = parser.onLine(line);
    }

    ImmutableList<DropboxEntry> entries = parser.getDropboxEntries();
    assertThat(entries).hasSize(2);

    assertThat(entries.get(0).tag()).isEqualTo(DropboxTag.DATA_APP_CRASH);
    assertThat(entries.get(0).timestamp()).isEqualTo(LocalDateTime.of(2025, 10, 24, 16, 33, 24));
    assertThat(entries.get(0).packageName()).isEqualTo("com.test.app.jetpack");

    assertThat(entries.get(1).tag()).isEqualTo(DropboxTag.DATA_APP_CRASH);
    assertThat(entries.get(1).timestamp()).isEqualTo(LocalDateTime.of(2025, 10, 24, 17, 33, 24));
    assertThat(entries.get(1).packageName()).isEqualTo("com.test.app.jetpack1");
  }

  @Test
  public void getDropboxEntries_dataAppAnr() throws Exception {
    var parser = new DropboxParser(DropboxTag.DATA_APP_ANR);
    List<String> lines =
        Files.readAllLines(Path.of(RunfilesUtil.getRunfilesLocation(DATA_APP_ANR_FILE)));
    for (String line : lines) {
      var unused = parser.onLine(line);
    }

    ImmutableList<DropboxEntry> entries = parser.getDropboxEntries();
    assertThat(entries).hasSize(2);

    assertThat(entries.get(0).tag()).isEqualTo(DropboxTag.DATA_APP_ANR);
    assertThat(entries.get(0).timestamp()).isEqualTo(LocalDateTime.of(2025, 10, 24, 14, 11, 11));
    assertThat(entries.get(0).packageName()).isEqualTo("com.test.app.anr");

    assertThat(entries.get(1).tag()).isEqualTo(DropboxTag.DATA_APP_ANR);
    assertThat(entries.get(1).timestamp()).isEqualTo(LocalDateTime.of(2023, 2, 13, 14, 51, 11));
    assertThat(entries.get(1).packageName()).isEqualTo("com.test.app.anr2");
  }

  @Test
  public void getDropboxEntries_systemTombstone() throws Exception {
    var parser = new DropboxParser(DropboxTag.SYSTEM_TOMBSTONE);
    List<String> lines =
        Files.readAllLines(Path.of(RunfilesUtil.getRunfilesLocation(SYSTEM_TOMBSTONE_FILE)));
    for (String line : lines) {
      var unused = parser.onLine(line);
    }

    ImmutableList<DropboxEntry> entries = parser.getDropboxEntries();
    assertThat(entries).hasSize(2);

    assertThat(entries.get(0).tag()).isEqualTo(DropboxTag.SYSTEM_TOMBSTONE);
    assertThat(entries.get(0).timestamp()).isEqualTo(LocalDateTime.of(2025, 10, 24, 14, 11, 35));
    assertThat(entries.get(0).packageName()).isEqualTo("com.test.app.nativecrasher");

    assertThat(entries.get(1).tag()).isEqualTo(DropboxTag.SYSTEM_TOMBSTONE);
    assertThat(entries.get(1).timestamp()).isEqualTo(LocalDateTime.of(2025, 10, 24, 15, 9, 8));
    assertThat(entries.get(1).packageName()).isEqualTo("com.test.app.nativecrasher2");
  }

  @Test
  public void getDropboxEntries_otherTags_hasNoEntry() {
    var parser = new DropboxParser(DropboxTag.DATA_APP_CRASH);
    var lines =
        ImmutableList.of(
            "Drop box contents: 140 entries",
            "Max entries: 1000",
            "========================================",
            "2025-10-24 16:33:24 random_tag (text, 1836 bytes)",
            "Process: com.test.app.jetpack",
            "PID: 9667",
            "UID: 10172",
            "fake info");

    for (String line : lines) {
      var unused = parser.onLine(line);
    }

    assertThat(parser.getDropboxEntries()).isEmpty();
  }
}
