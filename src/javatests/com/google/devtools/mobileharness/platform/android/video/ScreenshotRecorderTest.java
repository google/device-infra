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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Ticker;
import com.google.common.primitives.Bytes;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ScreenshotRecorderTest {

  private static final AndroidVideoDecoratorSpec SPEC =
      AndroidVideoDecoratorSpec.getDefaultInstance();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private Adb adb;
  @Mock private ScheduledExecutorService executorService;

  @Captor private ArgumentCaptor<Command> commandCaptor;
  @Captor private ArgumentCaptor<Runnable> runnableCaptor;

  private final LocalFileUtil localFileUtil = new LocalFileUtil();
  private final FakeTicker fakeTicker = new FakeTicker();
  private final FakeScheduledFuture<Object> scheduledFuture = new FakeScheduledFuture<>();
  private Path genDir;
  private Path screenshotsDir;
  private ScreenshotRecorder recorder;

  @Before
  public void setUp() throws Exception {
    genDir = tempFolder.getRoot().toPath();
    screenshotsDir = genDir.resolve("screenshots");
    when(executorService.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
        .thenAnswer(invocation -> scheduledFuture);
    when(executorService.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);
    recorder = ScreenshotRecorder.createForTesting(adb, localFileUtil, fakeTicker, executorService);
  }

  private static final class FakeTicker extends Ticker {
    private final AtomicLong nanos = new AtomicLong();

    void advance(Duration duration) {
      nanos.addAndGet(duration.toNanos());
    }

    @Override
    public long read() {
      return nanos.get();
    }
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

  private static byte[] createPngBytes(int width, int height, Color color) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = image.createGraphics();
    try {
      g2d.setColor(color);
      g2d.fillRect(0, 0, width, height);
    } finally {
      g2d.dispose();
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, "png", baos);
    return baos.toByteArray();
  }

  private void stubAdbScreencapBytes(byte[] output) throws Exception {
    stubAdbScreencapBytes(output, /* latency= */ Duration.ZERO);
  }

  /** Stubs screencap to output the given bytes, advancing the fake ticker by the given latency. */
  private void stubAdbScreencapBytes(byte[] output, Duration latency) throws Exception {
    doAnswer(
            invocation -> {
              fakeTicker.advance(latency);
              Command cmd = invocation.getArgument(0);
              Path stdoutPath = cmd.getStdoutPath().orElseThrow();
              Files.write(stdoutPath, output);
              return mock(CommandResult.class);
            })
        .when(adb)
        .run(any(Command.class));
  }

  /** Runs the most recently scheduled screenshot capture. */
  private void runScheduledCapture() {
    verify(executorService, atLeastOnce())
        .schedule(runnableCaptor.capture(), anyLong(), any(TimeUnit.class));
    runnableCaptor.getValue().run();
  }

  @Test
  public void start_preparesHostDirectoryAndSchedulesFirstCaptureImmediately() throws Exception {
    recorder.start("localhost:12345", "fake-test-id", genDir, SPEC);

    assertThat(Files.isDirectory(screenshotsDir)).isTrue();
    verify(executorService).schedule(any(Runnable.class), eq(0L), eq(MILLISECONDS));
  }

  @Test
  public void capture_streamsScreenshotFromDevice() throws Exception {
    recorder.start("localhost:12345", "fake-test-id", genDir, SPEC);
    stubAdbScreencapBytes(createPngBytes(100, 200, Color.RED));

    runScheduledCapture();

    verify(adb).run(commandCaptor.capture());
    Command cmd = commandCaptor.getValue();
    assertThat(cmd.getCommand())
        .containsExactly("adb", "-s", "localhost:12345", "exec-out", "screencap", "-p")
        .inOrder();
    assertThat(cmd.getRedirectStderr()).hasValue(false);
    assertThat(cmd.getStdoutPath()).hasValue(screenshotsDir.resolve(".raw_capture.png"));
  }

  @Test
  public void capture_warningBeforePng_recordsFrame() throws Exception {
    recorder.start("device_id", "test_id", genDir, SPEC);
    // On devices with multiple displays, screencap prints a warning before the PNG data.
    stubAdbScreencapBytes(
        Bytes.concat(
            "[Warning] Multiple displays were found, but no display id was specified!\n"
                .getBytes(UTF_8),
            createPngBytes(200, 400, Color.RED)));

    runScheduledCapture();
    fakeTicker.advance(Duration.ofSeconds(1));

    assertThat(recorder.stop())
        .containsExactly(
            screenshotsDir.resolve("ffconcat.txt"),
            screenshotsDir.resolve("screenshot_00000001.jpg"))
        .inOrder();
  }

  @Test
  public void capture_fasterThanMinInterval_waitsForRestOfInterval() throws Exception {
    recorder.start("device_id", "test_id", genDir, SPEC);
    stubAdbScreencapBytes(createPngBytes(100, 200, Color.RED), Duration.ofMillis(50));

    runScheduledCapture();

    // The next screenshot starts MIN_CAPTURE_INTERVAL after this one started.
    verify(executorService)
        .schedule(
            any(Runnable.class),
            eq(ScreenshotRecorder.MIN_CAPTURE_INTERVAL.minusMillis(50).toMillis()),
            eq(MILLISECONDS));
  }

  @Test
  public void capture_slowerThanMinInterval_schedulesNextCaptureImmediately() throws Exception {
    recorder.start("device_id", "test_id", genDir, SPEC);
    stubAdbScreencapBytes(
        createPngBytes(100, 200, Color.RED),
        ScreenshotRecorder.MIN_CAPTURE_INTERVAL.plusMillis(100));

    runScheduledCapture();

    // Both the first screenshot and the one after the slow screenshot are scheduled immediately.
    verify(executorService, times(2)).schedule(any(Runnable.class), eq(0L), eq(MILLISECONDS));
  }

  @Test
  public void capture_failure_backsOffBeforeNextCapture() throws Exception {
    doThrow(mock(MobileHarnessException.class)).when(adb).run(any(Command.class));
    recorder.start("device_id", "test_id", genDir, SPEC);

    runScheduledCapture();

    verify(executorService)
        .schedule(
            any(Runnable.class),
            eq(ScreenshotRecorder.FAILURE_BACKOFF.toMillis()),
            eq(MILLISECONDS));
  }

  @Test
  public void capture_runtimeException_keepsRecording() throws Exception {
    recorder.start("device_id", "test_id", genDir, SPEC);
    doThrow(new IllegalStateException("unexpected")).when(adb).run(any(Command.class));
    runScheduledCapture();

    // The next screenshot is recorded normally.
    stubAdbScreencapBytes(createPngBytes(200, 400, Color.RED));
    runScheduledCapture();
    fakeTicker.advance(Duration.ofSeconds(1));

    assertThat(recorder.stop())
        .containsExactly(
            screenshotsDir.resolve("ffconcat.txt"),
            screenshotsDir.resolve("screenshot_00000001.jpg"))
        .inOrder();
  }

  @Test
  public void captureAndStop_deduplicatesIdenticalFramesAndWritesFfconcat() throws Exception {
    recorder.start("device_id", "test_id", genDir, SPEC);
    byte[] redPng = createPngBytes(200, 400, Color.RED);
    byte[] bluePng = createPngBytes(200, 400, Color.BLUE);

    // Frame 1 at t = 0.0s (RED).
    stubAdbScreencapBytes(redPng);
    runScheduledCapture();
    // t = 0.3s: identical RED screenshot, dropped.
    fakeTicker.advance(Duration.ofMillis(300));
    runScheduledCapture();
    // Frame 2 at t = 1.8s (BLUE).
    fakeTicker.advance(Duration.ofMillis(1500));
    stubAdbScreencapBytes(bluePng);
    runScheduledCapture();
    // Stop at t = 2.5s.
    fakeTicker.advance(Duration.ofMillis(700));
    List<Path> result = recorder.stop();

    assertThat(result)
        .containsExactly(
            screenshotsDir.resolve("ffconcat.txt"),
            screenshotsDir.resolve("screenshot_00000001.jpg"),
            screenshotsDir.resolve("screenshot_00000002.jpg"))
        .inOrder();
    assertThat(Files.readString(screenshotsDir.resolve("ffconcat.txt")))
        .isEqualTo(
            """
            ffconcat version 1.0
            file 'screenshot_00000001.jpg'
            duration 1.800000
            file 'screenshot_00000002.jpg'
            duration 0.700000
            file 'screenshot_00000002.jpg'
            """);
    assertThat(Files.exists(screenshotsDir.resolve(".raw_capture.png"))).isFalse();
  }

  @Test
  public void stop_cancelsPendingCaptureAndShutsDownExecutor() throws Exception {
    recorder.start("device_id", "test_id", genDir, SPEC);

    var unused = recorder.stop();

    assertThat(scheduledFuture.isCancelled()).isTrue();
    verify(executorService).shutdownNow();
  }

  @Test
  public void stop_showsLastFrameForAtLeastMinimumDuration() throws Exception {
    recorder.start("device_id", "test_id", genDir, SPEC);
    stubAdbScreencapBytes(createPngBytes(200, 400, Color.RED));
    runScheduledCapture();
    fakeTicker.advance(Duration.ofMillis(100));

    List<Path> result = recorder.stop();

    assertThat(result).hasSize(2);
    assertThat(Files.readString(screenshotsDir.resolve("ffconcat.txt")))
        .isEqualTo(
            """
            ffconcat version 1.0
            file 'screenshot_00000001.jpg'
            duration 0.500000
            file 'screenshot_00000001.jpg'
            """);
  }

  @Test
  public void captureAndStop_writesDurationsWithMicrosecondPrecision() throws Exception {
    recorder.start("device_id", "test_id", genDir, SPEC);
    stubAdbScreencapBytes(createPngBytes(200, 400, Color.RED));
    runScheduledCapture();
    fakeTicker.advance(Duration.ofNanos(333_333_333));
    stubAdbScreencapBytes(createPngBytes(200, 400, Color.BLUE));
    runScheduledCapture();
    fakeTicker.advance(Duration.ofSeconds(1));

    List<Path> result = recorder.stop();

    assertThat(result).hasSize(3);
    assertThat(Files.readString(screenshotsDir.resolve("ffconcat.txt")))
        .contains("duration 0.333333\n");
  }

  @Test
  public void capture_afterStop_isDroppedAndNotRescheduled() throws Exception {
    recorder.start("device_id", "test_id", genDir, SPEC);
    verify(executorService).schedule(runnableCaptor.capture(), anyLong(), any(TimeUnit.class));
    Runnable pendingCapture = runnableCaptor.getValue();
    assertThat(recorder.stop()).isEmpty();
    stubAdbScreencapBytes(createPngBytes(200, 400, Color.RED));

    pendingCapture.run();

    verify(executorService).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    assertThat(Files.exists(screenshotsDir.resolve("screenshot_00000001.jpg"))).isFalse();
    assertThat(Files.exists(screenshotsDir.resolve("ffconcat.txt"))).isFalse();
  }

  @Test
  public void captureAndStop_downscalesLargeAndOddDimensionImagesToEvenJpeg() throws Exception {
    recorder.start("device_id", "test_id", genDir, SPEC);

    // 1441x2561 RGBA image exceeds max dimension 1280 and has odd dimensions.
    stubAdbScreencapBytes(createPngBytes(1441, 2561, Color.GREEN));
    runScheduledCapture();
    fakeTicker.advance(Duration.ofSeconds(1));
    List<Path> result = recorder.stop();
    assertThat(result).hasSize(2);

    Path jpegPath = screenshotsDir.resolve("screenshot_00000001.jpg");
    assertThat(Files.exists(jpegPath)).isTrue();
    BufferedImage decoded = ImageIO.read(jpegPath.toFile());
    assertThat(decoded).isNotNull();
    assertThat(decoded.getWidth()).isEqualTo(720);
    assertThat(decoded.getHeight()).isEqualTo(1280);
  }

  @Test
  public void stop_whenCaptureFails_returnsEmptyList() throws Exception {
    doThrow(mock(MobileHarnessException.class)).when(adb).run(any(Command.class));
    recorder.start("device_id", "test_id", genDir, SPEC);
    runScheduledCapture();

    List<Path> result = recorder.stop();

    assertThat(result).isEmpty();
    assertThat(Files.exists(screenshotsDir.resolve("ffconcat.txt"))).isFalse();
  }

  @Test
  public void stop_whenNotStarted_returnsEmptyList() throws Exception {
    assertThat(recorder.stop()).isEmpty();
  }
}
