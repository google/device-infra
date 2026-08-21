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

package com.google.devtools.mobileharness.platform.android.video;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class EmulatorConsoleRecorderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private Adb adb;
  @Mock private Sleeper sleeper;
  @Mock private ScheduledExecutorService executorService;

  private EmulatorConsoleRecorder recorder;

  @Before
  public void setUp() {
    when(executorService.scheduleAtFixedRate(
            any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
        .thenAnswer(
            invocation -> {
              Runnable runnable = invocation.getArgument(0);
              runnable.run();
              return new FakeScheduledFuture<>();
            });
    recorder = new EmulatorConsoleRecorder(adb, sleeper, executorService);
  }

  private static final class FakeScheduledFuture<V> implements ScheduledFuture<V> {
    private boolean cancelled = false;

    @Override
    public long getDelay(TimeUnit unit) {
      return 0;
    }

    @Override
    public int compareTo(Delayed o) {
      return 0;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      this.cancelled = true;
      return true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public boolean isDone() {
      return cancelled;
    }

    @Override
    public V get() {
      return null;
    }

    @Override
    public V get(long timeout, TimeUnit unit) {
      return null;
    }
  }

  @Test
  public void getEmulatorSerialForConsole_getsPort() throws Exception {
    when(adb.runShell("localhost:12345", "getprop emulator_port")).thenReturn("5554\n");
    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();

    recorder.start("localhost:12345", "test_id", tempFolder.getRoot().toPath(), spec);

    verify(adb).run(eq("emulator-5554"), any());
  }

  @Test
  public void getEmulatorSerialForConsole_fallsBackWhenEmpty() throws Exception {
    when(adb.runShell("localhost:12345", "getprop emulator_port")).thenReturn("");
    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();

    recorder.start("localhost:12345", "test_id", tempFolder.getRoot().toPath(), spec);

    verify(adb).run(eq("localhost:12345"), any());
  }

  @Test
  public void start_propagatesExceptionIfGetPropFails() throws Exception {
    when(adb.runShell(anyString(), anyString()))
        .thenThrow(Mockito.mock(MobileHarnessException.class));
    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();

    assertThrows(
        MobileHarnessException.class,
        () -> recorder.start("localhost:12345", "test_id", tempFolder.getRoot().toPath(), spec));
  }

  @Test
  public void stop_propagatesExceptionIfAdbRunFails() throws Exception {
    when(adb.runShell("localhost:12345", "getprop emulator_port")).thenReturn("5554\n");
    when(adb.run(anyString(), any())).thenThrow(Mockito.mock(MobileHarnessException.class));
    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();

    recorder.start("localhost:12345", "test_id", tempFolder.getRoot().toPath(), spec);

    assertThrows(MobileHarnessException.class, () -> recorder.stop());
  }

  @Test
  public void stop_throwsExceptionIfGeneratedFilesAbsent() throws Exception {
    when(adb.runShell("localhost:12345", "getprop emulator_port")).thenReturn("5554\n");
    when(adb.run(eq("emulator-5554"), any())).thenReturn("");
    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();

    recorder.start("localhost:12345", "test_id", tempFolder.getRoot().toPath(), spec);

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> recorder.stop());
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_VIDEO_DECORATOR_EMULATOR_CONSOLE_FILE_ABSENT);
  }

  @Test
  public void stop_throwsExceptionIfGeneratedFilesEmpty() throws Exception {
    Path genDir = tempFolder.getRoot().toPath();
    when(adb.runShell("localhost:12345", "getprop emulator_port")).thenReturn("5554\n");
    when(adb.run(eq("emulator-5554"), any())).thenReturn("");
    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();

    recorder.start("localhost:12345", "test_id", genDir, spec);

    // Create empty file
    File emptyFile = genDir.resolve("emulator_video_1.webm").toFile();
    assertThat(emptyFile.createNewFile()).isTrue();

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> recorder.stop());
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_VIDEO_DECORATOR_EMULATOR_CONSOLE_FILE_EMPTY);
  }

  @Test
  public void stop_returnsGeneratedFilesIfTheyExistAndAreNotEmpty() throws Exception {
    Path genDir = tempFolder.getRoot().toPath();
    when(adb.runShell("localhost:12345", "getprop emulator_port")).thenReturn("5554\n");
    when(adb.run(eq("emulator-5554"), any())).thenReturn("");
    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();

    recorder.start("localhost:12345", "test_id", genDir, spec);

    // Create non-empty file
    File validFile = genDir.resolve("emulator_video_1.webm").toFile();
    assertThat(validFile.createNewFile()).isTrue();
    Files.write(validFile.toPath(), new byte[] {1, 2, 3});

    List<Path> generatedFiles = recorder.stop();

    assertThat(generatedFiles).hasSize(1);
    assertThat(generatedFiles.get(0).toString()).isEqualTo(validFile.getAbsolutePath());
  }
}
