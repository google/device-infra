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

package com.google.devtools.mobileharness.platform.android.logcat;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public final class CrashDialogDetectorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  private static final String TEST_DATA_PREFIX =
      "javatests/com/google/devtools/mobileharness/platform/android/logcat/testdata/";
  private static final String DEVICE_ID = "device_id";

  @Mock private TestInfo testInfo;
  @Mock private Device device;
  @Mock private Adb adb;

  private CrashDialogDetector detector;

  @Before
  public void setUp() {
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.log()).thenReturn(new Log(new Timing()));

    detector = new CrashDialogDetector(adb, Sleeper.noOpSleeper());
  }

  @Test
  public void scan_detectsCrashDialog() throws Exception {
    String testDataPath =
        RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "dumpsys_window_crash.txt");
    List<String> lines = Files.readAllLines(Path.of(testDataPath));

    when(adb.runShell(
            eq(DEVICE_ID), eq("dumpsys window"), any(Duration.class), any(LineCallback.class)))
        .thenAnswer(new DumpsysWindowCmdAnswer(lines));

    detector.scan(testInfo, device, ImmutableList.of());

    assertThat(detector.crashDialogPackageName()).hasValue("com.example.app");
    assertThat(detector.crashDialogScreenshot())
        .hasValue("/data/local/tmp/crash-dialog-com_example_app.png");
    verify(adb)
        .runShell(
            eq(DEVICE_ID),
            eq("screencap -p /data/local/tmp/crash-dialog-com_example_app.png"),
            any(Duration.class));
  }

  @Test
  public void scan_detectsAnrDialog() throws Exception {
    String testDataPath =
        RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "dumpsys_window_anr.txt");
    List<String> lines = Files.readAllLines(Path.of(testDataPath));

    when(adb.runShell(
            eq(DEVICE_ID), eq("dumpsys window"), any(Duration.class), any(LineCallback.class)))
        .thenAnswer(new DumpsysWindowCmdAnswer(lines));

    detector.scan(testInfo, device, ImmutableList.of());

    assertThat(detector.crashDialogPackageName()).hasValue("com.example.app");
    assertThat(detector.crashDialogScreenshot())
        .hasValue("/data/local/tmp/crash-dialog-com_example_app.png");
    verify(adb)
        .runShell(
            eq(DEVICE_ID),
            eq("screencap -p /data/local/tmp/crash-dialog-com_example_app.png"),
            any(Duration.class));
  }

  @Test
  public void scan_ignoresPackages() throws Exception {
    String testDataPath =
        RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "dumpsys_window_crash.txt");
    List<String> lines = Files.readAllLines(Path.of(testDataPath));

    when(adb.runShell(
            eq(DEVICE_ID), eq("dumpsys window"), any(Duration.class), any(LineCallback.class)))
        .thenAnswer(new DumpsysWindowCmdAnswer(lines));

    detector.scan(testInfo, device, ImmutableList.of("com.example.app"));

    assertThat(detector.crashDialogPackageName()).isEmpty();
    assertThat(detector.crashDialogScreenshot()).isEmpty();
  }

  @Test
  public void scan_noDialogDetected() throws Exception {
    String testDataPath =
        RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + "dumpsys_window_normal.txt");
    List<String> lines = Files.readAllLines(Path.of(testDataPath));

    when(adb.runShell(
            eq(DEVICE_ID), eq("dumpsys window"), any(Duration.class), any(LineCallback.class)))
        .thenAnswer(new DumpsysWindowCmdAnswer(lines));

    detector.scan(testInfo, device, ImmutableList.of());

    assertThat(detector.crashDialogPackageName()).isEmpty();
    assertThat(detector.crashDialogScreenshot()).isEmpty();
  }

  private static class DumpsysWindowCmdAnswer implements Answer<Void> {
    private final List<String> lines;

    DumpsysWindowCmdAnswer(List<String> lines) {
      this.lines = lines;
    }

    @Override
    public Void answer(InvocationOnMock invocation) {
      LineCallback callback = invocation.getArgument(3);
      for (String line : lines) {
        callback.onLine(line);
      }
      return null;
    }
  }
}
