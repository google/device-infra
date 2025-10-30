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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class DropboxExtractorTest {

  private static final ImmutableList<String> DROPBOX_APP_CRASH_OUTPUT =
      ImmutableList.of(
          "========================================",
          "2025-10-23 10:00:00 data_app_crash (text, 123 bytes)",
          "Process: com.test.app.one",
          "========================================",
          "2025-10-24 12:00:00 data_app_crash (text, 456 bytes)",
          "Process: com.test.app.two");

  private static final ImmutableList<String> DROPBOX_TOMBSTONE_OUTPUT =
      ImmutableList.of(
          "========================================",
          "2025-10-24 14:11:35 SYSTEM_TOMBSTONE (compressed text, 18396 bytes)",
          "pid: 4220, tid: 4220, name: g.nativecrasher  >>> com.test.app.nativecrasher <<<");

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Adb adb;
  @Mock private LocalFileUtil localFileUtil;

  private DropboxExtractor dropboxExtractor;
  private Path outputDir;

  @Before
  public void setUp() throws Exception {
    dropboxExtractor = new DropboxExtractor(adb, localFileUtil);
    outputDir = Path.of("/tmp/test_output");
    when(adb.runShell(
            eq("device_id"),
            eq("dumpsys dropbox --print data_app_crash"),
            any(Duration.class),
            any(LineCallback.class)))
        .thenAnswer(
            invocation -> {
              DropboxParser parser = invocation.getArgument(3);
              DROPBOX_APP_CRASH_OUTPUT.forEach(
                  line -> {
                    var unused = parser.onLine(line);
                  });
              return "";
            });
  }

  @Test
  public void extract_filtersOnTimestamp() throws Exception {
    dropboxExtractor.extract(
        "device_id",
        ImmutableSet.of("com.test.app.one", "com.test.app.two"),
        ImmutableSet.of(DropboxTag.DATA_APP_CRASH),
        LocalDateTime.of(2025, 10, 24, 02, 0, 0),
        outputDir);

    verify(localFileUtil, never())
        .writeToFile(
            eq("/tmp/test_output/data_app_crash_0_com_test_app_one.txt"), any(String.class));
    verify(localFileUtil)
        .writeToFile(
            eq("/tmp/test_output/data_app_crash_0_com_test_app_two.txt"), any(String.class));
  }

  @Test
  public void extract_filtersOnProcessName() throws Exception {
    dropboxExtractor.extract(
        "device_id",
        ImmutableSet.of("com.test.app.two"),
        ImmutableSet.of(DropboxTag.DATA_APP_CRASH),
        LocalDateTime.of(2025, 10, 23, 02, 0, 0),
        outputDir);

    verify(localFileUtil, never())
        .writeToFile(
            eq("/tmp/test_output/data_app_crash_0_com_test_app_one.txt"), any(String.class));
    verify(localFileUtil)
        .writeToFile(
            eq("/tmp/test_output/data_app_crash_0_com_test_app_two.txt"), any(String.class));
  }

  @Test
  public void extract_multipleDifferentCrashes() throws Exception {
    when(adb.runShell(
            eq("device_id"),
            eq("dumpsys dropbox --print SYSTEM_TOMBSTONE"),
            any(Duration.class),
            any(LineCallback.class)))
        .thenAnswer(
            invocation -> {
              DropboxParser parser = invocation.getArgument(3);
              DROPBOX_TOMBSTONE_OUTPUT.forEach(
                  line -> {
                    var unused = parser.onLine(line);
                  });
              return "";
            });

    dropboxExtractor.extract(
        "device_id",
        ImmutableSet.of("com.test.app.one", "com.test.app.nativecrasher"),
        ImmutableSet.of(DropboxTag.DATA_APP_CRASH, DropboxTag.SYSTEM_TOMBSTONE),
        LocalDateTime.of(2025, 10, 23, 1, 0),
        outputDir);

    verify(localFileUtil)
        .writeToFile(
            eq("/tmp/test_output/data_app_crash_0_com_test_app_one.txt"), any(String.class));
    verify(localFileUtil)
        .writeToFile(
            eq("/tmp/test_output/SYSTEM_TOMBSTONE_1_com_test_app_nativecrasher.txt"),
            any(String.class));
  }
}
