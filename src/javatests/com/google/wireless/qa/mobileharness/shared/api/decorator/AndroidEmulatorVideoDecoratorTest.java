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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidEmulatorVideoDecoratorSpec;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidEmulatorVideoDecorator} */
@RunWith(JUnit4.class)
public class AndroidEmulatorVideoDecoratorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Mock private Device device;
  @Mock private Driver decoratedDriver;
  @Mock private JobInfo jobInfo;
  @Mock private TestInfo testInfo;
  @Mock private TestLocator testLocator;

  @Mock private Adb adb;

  private AndroidEmulatorVideoDecorator decorator;
  private File genFileDir;

  @Before
  public void setUp() throws Exception {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn("emulator-5554");
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(new Log(new Timing()));
    when(testInfo.locator()).thenReturn(testLocator);
    when(testLocator.getId()).thenReturn("test-id");

    genFileDir = tmpFolder.newFolder("gen_files");
    when(testInfo.getGenFileDir()).thenReturn(genFileDir.getAbsolutePath());

    doNothing().when(decoratedDriver).run(testInfo);

    decorator =
        new AndroidEmulatorVideoDecorator(decoratedDriver, testInfo, adb, Sleeper.noOpSleeper());
  }

  @Test
  public void decorator_run_succeeds() throws Exception {
    AndroidEmulatorVideoDecoratorSpec spec =
        AndroidEmulatorVideoDecoratorSpec.newBuilder()
            .setFps(5)
            .setBitRate(1000)
            .setTimeLimitSecs(900)
            .build();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void getIntervalMs_usesSpecValue() throws Exception {
    AndroidEmulatorVideoDecoratorSpec spec =
        AndroidEmulatorVideoDecoratorSpec.newBuilder().setTimeLimitSecs(100).build();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);

    decorator.onStart(testInfo);
    assertThat(decorator.getIntervalMs(testInfo)).isEqualTo(100000L);
  }

  @Test
  public void getIntervalMs_usesDefaultValue() throws Exception {
    AndroidEmulatorVideoDecoratorSpec spec = AndroidEmulatorVideoDecoratorSpec.getDefaultInstance();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);

    decorator.onStart(testInfo);
    assertThat(decorator.getIntervalMs(testInfo)).isEqualTo(900000L); // 15 minutes
  }

  @Test
  public void runTimerTask_startsRecording() throws Exception {
    AndroidEmulatorVideoDecoratorSpec spec =
        AndroidEmulatorVideoDecoratorSpec.newBuilder()
            .setFps(10)
            .setBitRate(500_000)
            .setTimeLimitSecs(60)
            .build();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);
    decorator.onStart(testInfo);

    decorator.runTimerTask(testInfo);

    String expectedPath = Path.of(genFileDir.getAbsolutePath()).resolve("video-1.webm").toString();
    verify(adb)
        .run(
            eq("emulator-5554"),
            eq(
                new String[] {
                  "emu",
                  "screenrecord",
                  "start",
                  "--bit-rate",
                  "500000",
                  "--fps",
                  "10",
                  "--time-limit",
                  "60",
                  expectedPath
                }));
  }

  @Test
  public void runTimerTask_usesDefaults() throws Exception {
    AndroidEmulatorVideoDecoratorSpec spec = AndroidEmulatorVideoDecoratorSpec.getDefaultInstance();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);
    decorator.onStart(testInfo);

    decorator.runTimerTask(testInfo);

    String expectedPath = Path.of(genFileDir.getAbsolutePath()).resolve("video-1.webm").toString();
    verify(adb)
        .run(
            eq("emulator-5554"),
            eq(
                new String[] {
                  "emu",
                  "screenrecord",
                  "start",
                  "--bit-rate",
                  "100000", // Default
                  "--fps",
                  "5", // Default
                  "--time-limit",
                  "900", // Default 15 mins
                  expectedPath
                }));
  }

  @Test
  public void runTimerTask_stopsPreviousRecordingIfRunning() throws Exception {
    AndroidEmulatorVideoDecoratorSpec spec = AndroidEmulatorVideoDecoratorSpec.getDefaultInstance();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);
    decorator.onStart(testInfo);

    // First call starts recording
    decorator.runTimerTask(testInfo);

    // Second call should stop previous and start new
    decorator.runTimerTask(testInfo);

    String expectedStopPath =
        Path.of(genFileDir.getAbsolutePath()).resolve("video-1.webm").toString();
    // Verify stop was called
    verify(adb)
        .run(
            eq("emulator-5554"),
            eq(new String[] {"emu", "screenrecord", "stop", expectedStopPath}));

    String expectedStartPath1 =
        Path.of(genFileDir.getAbsolutePath()).resolve("video-1.webm").toString();
    verify(adb)
        .run(
            eq("emulator-5554"),
            eq(
                new String[] {
                  "emu",
                  "screenrecord",
                  "start",
                  "--bit-rate",
                  "100000",
                  "--fps",
                  "5",
                  "--time-limit",
                  "900",
                  expectedStartPath1
                }));

    String expectedStartPath2 =
        Path.of(genFileDir.getAbsolutePath()).resolve("video-2.webm").toString();
    verify(adb)
        .run(
            eq("emulator-5554"),
            eq(
                new String[] {
                  "emu",
                  "screenrecord",
                  "start",
                  "--bit-rate",
                  "100000",
                  "--fps",
                  "5",
                  "--time-limit",
                  "900",
                  expectedStartPath2
                }));
  }

  @Test
  public void onEnd_stopsRecordingAndValidatesFiles() throws Exception {
    AndroidEmulatorVideoDecoratorSpec spec = AndroidEmulatorVideoDecoratorSpec.getDefaultInstance();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);
    decorator.onStart(testInfo);

    // Start recording so we have generatedFiles populated
    decorator.runTimerTask(testInfo);

    // Create a fake file to simulate success
    File videoFile = new File(genFileDir, "video-1.webm");
    videoFile.createNewFile();
    Files.writeString(videoFile.toPath(), "video data");

    decorator.onEnd(testInfo);

    String expectedStopPath =
        Path.of(genFileDir.getAbsolutePath()).resolve("video-1.webm").toString();
    verify(adb)
        .run(
            eq("emulator-5554"),
            eq(new String[] {"emu", "screenrecord", "stop", expectedStopPath}));
  }

  @Test
  public void onEnd_missingFiles_throwsException() throws Exception {
    AndroidEmulatorVideoDecoratorSpec spec = AndroidEmulatorVideoDecoratorSpec.getDefaultInstance();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);
    decorator.onStart(testInfo);

    decorator.runTimerTask(testInfo);

    // File "video-1.webm" is NOT created.

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> decorator.onEnd(testInfo));
    assertThat(e.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_EMULATOR_VIDEO_DECORATOR_VIDEO_FILE_ABSENT);
  }

  @Test
  public void onEnd_emptyFiles_throwsException() throws Exception {
    AndroidEmulatorVideoDecoratorSpec spec = AndroidEmulatorVideoDecoratorSpec.getDefaultInstance();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);
    decorator.onStart(testInfo);

    decorator.runTimerTask(testInfo);

    // Create empty file
    File videoFile = new File(genFileDir, "video-1.webm");
    videoFile.createNewFile();

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> decorator.onEnd(testInfo));
    assertThat(e.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_EMULATOR_VIDEO_DECORATOR_VIDEO_FILE_EMPTY);
  }
}
