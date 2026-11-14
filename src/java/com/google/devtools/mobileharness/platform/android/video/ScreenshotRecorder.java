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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Bytes;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.inject.Inject;

/**
 * An {@link AndroidVideoRecorder} that captures video by streaming screenshots from the device.
 *
 * <p>Screenshots are streamed with {@code adb exec-out screencap -p}, so nothing is stored on the
 * device. Each screenshot starts as soon as the previous one finishes, but no sooner than {@link
 * #MIN_CAPTURE_INTERVAL} after the previous one started. Consecutive identical screenshots are
 * dropped, and the remaining ones are downscaled and JPEG-encoded on the host. Each frame is
 * appended to {@link #FFCONCAT_FILE_NAME} with its real display duration, so the stitched video
 * follows the real timeline no matter how long each screenshot takes.
 */
public class ScreenshotRecorder implements AndroidVideoRecorder {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Name of the file in the screenshots directory that lists the frames for FFmpeg. */
  public static final String FFCONCAT_FILE_NAME = "ffconcat.txt";

  private static final String SCREENSHOTS_DIR_NAME = "screenshots";
  private static final String RAW_CAPTURE_FILE_NAME = ".raw_capture.png";
  private static final int MAX_DIMENSION = 1280;
  private static final float JPEG_QUALITY = 0.75f;

  /** The 8 bytes that every PNG file starts with. */
  private static final byte[] pngSignature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

  /**
   * Minimum interval between the starts of two screenshots. This caps fast devices at 5 screenshots
   * per second to bound the device and host CPU usage and the output size, while slower devices
   * still capture back to back.
   */
  @VisibleForTesting static final Duration MIN_CAPTURE_INTERVAL = Duration.ofMillis(200);

  /** Delay before retrying after a failed screenshot, e.g., when the device is offline. */
  @VisibleForTesting static final Duration FAILURE_BACKOFF = Duration.ofMillis(500);

  /** Minimum display duration of the last frame, so that the final screen stays visible. */
  private static final Duration MIN_LAST_FRAME_DURATION = Duration.ofMillis(500);

  private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);

  private final Adb adb;
  private final LocalFileUtil localFileUtil;
  private final Ticker ticker;
  private final Supplier<ScheduledExecutorService> executorServiceFactory;

  private final Object lock = new Object();

  @GuardedBy("lock")
  private ScheduledExecutorService executorService;

  @GuardedBy("lock")
  private ScheduledFuture<?> captureFuture;

  @GuardedBy("lock")
  private boolean stopped = true;

  @GuardedBy("lock")
  private byte[] previousFrameBytes;

  @GuardedBy("lock")
  private final List<String> frameFileNames = new ArrayList<>();

  /** Ticker reading when the last recorded frame was captured. */
  @GuardedBy("lock")
  private long lastFrameNanos;

  /** Whether the display duration of the last recorded frame is yet to be written. */
  @GuardedBy("lock")
  private boolean lastFrameDurationPending;

  @GuardedBy("lock")
  private int captureCount;

  @GuardedBy("lock")
  private int duplicateCount;

  @GuardedBy("lock")
  private int failureCount;

  @GuardedBy("lock")
  private long totalCaptureMillis;

  // Set by start() before the first screenshot is scheduled.
  private String deviceId;
  private Path hostFolder;
  private long startNanos;

  @Inject
  ScreenshotRecorder(Adb adb, LocalFileUtil localFileUtil, Ticker ticker) {
    this(adb, localFileUtil, ticker, ScreenshotRecorder::newExecutorService);
  }

  private ScreenshotRecorder(
      Adb adb,
      LocalFileUtil localFileUtil,
      Ticker ticker,
      Supplier<ScheduledExecutorService> executorServiceFactory) {
    this.adb = adb;
    this.localFileUtil = localFileUtil;
    this.ticker = ticker;
    this.executorServiceFactory = executorServiceFactory;
  }

  /** Creates a recorder that runs the screenshot captures on the given executor. */
  @VisibleForTesting
  static ScreenshotRecorder createForTesting(
      Adb adb,
      LocalFileUtil localFileUtil,
      Ticker ticker,
      ScheduledExecutorService executorService) {
    return new ScreenshotRecorder(adb, localFileUtil, ticker, () -> executorService);
  }

  private static ScheduledExecutorService newExecutorService() {
    return Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("screenshot-recorder-%d").setDaemon(true).build());
  }

  @Override
  public void start(String deviceId, String testId, Path genFileDir, AndroidVideoDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Starting screenshot recorder for device %s...", deviceId);
    this.deviceId = deviceId;
    this.hostFolder = genFileDir.resolve(SCREENSHOTS_DIR_NAME);
    localFileUtil.prepareDir(hostFolder.toString());
    startNanos = ticker.read();
    synchronized (lock) {
      stopped = false;
      previousFrameBytes = null;
      frameFileNames.clear();
      lastFrameDurationPending = false;
      captureCount = 0;
      duplicateCount = 0;
      failureCount = 0;
      totalCaptureMillis = 0;
      if (executorService == null) {
        executorService = executorServiceFactory.get();
      }
    }
    scheduleNextCapture(Duration.ZERO);
  }

  private void scheduleNextCapture(Duration delay) {
    synchronized (lock) {
      if (stopped || executorService == null) {
        return;
      }
      try {
        captureFuture =
            executorService.schedule(this::captureAndScheduleNext, delay.toMillis(), MILLISECONDS);
      } catch (RejectedExecutionException e) {
        logger.atInfo().log("Screenshot recorder is shutting down, skip the next screenshot.");
      }
    }
  }

  private void captureAndScheduleNext() {
    long captureStartNanos = ticker.read();
    if (!captureScreenshotSafely()) {
      scheduleNextCapture(FAILURE_BACKOFF);
      return;
    }
    // Start the next screenshot MIN_CAPTURE_INTERVAL after this one started, or right away if this
    // one took longer.
    Duration delay = MIN_CAPTURE_INTERVAL.minusNanos(ticker.read() - captureStartNanos);
    scheduleNextCapture(delay.isNegative() ? Duration.ZERO : delay);
  }

  /** Captures one screenshot and returns whether it succeeded. */
  private boolean captureScreenshotSafely() {
    try {
      captureScreenshot();
      return true;
    } catch (InterruptedException e) {
      // stop() interrupts the in-flight screenshot.
      Thread.currentThread().interrupt();
      return false;
    } catch (MobileHarnessException | IOException | RuntimeException e) {
      // Runtime exceptions (e.g., from decoding malformed image data) are caught too. Otherwise
      // they would silently end the capture loop for the rest of the test.
      if (Thread.currentThread().isInterrupted()) {
        return false;
      }
      synchronized (lock) {
        failureCount++;
      }
      logger.atWarning().atMostEvery(30, SECONDS).withCause(e).log(
          "Failed to capture screenshot from device %s.", deviceId);
      return false;
    }
  }

  private void captureScreenshot()
      throws MobileHarnessException, InterruptedException, IOException {
    Path rawOutputPath = hostFolder.resolve(RAW_CAPTURE_FILE_NAME);
    long captureNanos = ticker.read();
    try {
      var unused =
          adb.run(
              Command.of("adb", "-s", deviceId, "exec-out", "screencap", "-p")
                  .redirectStderr(false)
                  .writeStdoutTo(rawOutputPath));
      long captureMillis = NANOSECONDS.toMillis(ticker.read() - captureNanos);
      byte[] pngBytes =
          extractPng(Files.exists(rawOutputPath) ? Files.readAllBytes(rawOutputPath) : new byte[0]);
      String fileName;
      synchronized (lock) {
        captureCount++;
        totalCaptureMillis += captureMillis;
        if (Arrays.equals(pngBytes, previousFrameBytes)) {
          duplicateCount++;
          return;
        }
        fileName = String.format("screenshot_%08d.jpg", frameFileNames.size() + 1);
      }

      Path framePath = hostFolder.resolve(fileName);
      writeScaledJpeg(decodePng(pngBytes), framePath);

      synchronized (lock) {
        if (stopped) {
          // stop() may have already finalized the ffconcat file, so drop this frame.
          Files.deleteIfExists(framePath);
          return;
        }
        if (frameFileNames.isEmpty()) {
          writeFfconcat("ffconcat version 1.0\n" + fileLine(fileName), /* append= */ false);
        } else {
          writeFfconcat(
              durationLine(Duration.ofNanos(captureNanos - lastFrameNanos)) + fileLine(fileName),
              /* append= */ true);
        }
        previousFrameBytes = pngBytes;
        frameFileNames.add(fileName);
        lastFrameNanos = captureNanos;
        lastFrameDurationPending = true;
      }
    } finally {
      Files.deleteIfExists(rawOutputPath);
    }
  }

  /**
   * Returns the PNG data in the screencap output, skipping any text before it. For example, on
   * devices with multiple displays, screencap prints a warning to stderr, which {@code adb
   * exec-out} merges into stdout ahead of the PNG data.
   */
  private static byte[] extractPng(byte[] output) throws IOException {
    int pngStart = Bytes.indexOf(output, pngSignature);
    if (pngStart < 0) {
      String outputPrefix = new String(output, 0, Math.min(output.length, 200), UTF_8);
      throw new IOException("No PNG data in screencap output: " + outputPrefix.trim());
    }
    return pngStart == 0 ? output : Arrays.copyOfRange(output, pngStart, output.length);
  }

  /**
   * Decodes the PNG in memory. {@code ImageIO.read(InputStream)} would cache the data in a
   * temporary file.
   */
  private static BufferedImage decodePng(byte[] pngBytes) throws IOException {
    ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(pngBytes));
    BufferedImage image = ImageIO.read(input);
    if (image == null) {
      // ImageIO.read() closes the stream itself unless no image reader supports the data.
      input.close();
      throw new IOException("Failed to decode screenshot PNG");
    }
    return image;
  }

  private static void writeScaledJpeg(BufferedImage image, Path outputPath) throws IOException {
    int width = image.getWidth();
    int height = image.getHeight();
    if (Math.max(width, height) > MAX_DIMENSION) {
      double scale = (double) MAX_DIMENSION / Math.max(width, height);
      width = (int) Math.round(width * scale);
      height = (int) Math.round(height * scale);
    }
    // Ensure even dimensions for H.264 / yuv420p encoding (minimum 2x2).
    width = Math.max(2, width & ~1);
    height = Math.max(2, height & ~1);

    BufferedImage rgbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = rgbImage.createGraphics();
    try {
      g2d.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2d.drawImage(image, 0, 0, width, height, null);
    } finally {
      g2d.dispose();
    }

    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) {
      throw new IOException("No JPEG ImageWriter found");
    }
    ImageWriter writer = writers.next();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputPath.toFile())) {
      writer.setOutput(ios);
      ImageWriteParam param = writer.getDefaultWriteParam();
      if (param.canWriteCompressed()) {
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
      }
      writer.write(null, new IIOImage(rgbImage, null, null), param);
    } finally {
      writer.dispose();
    }
  }

  private static String fileLine(String fileName) {
    return "file '" + fileName + "'\n";
  }

  /**
   * Returns an ffconcat duration directive with microsecond precision, so that rounding errors do
   * not add up over thousands of frames.
   */
  private static String durationLine(Duration duration) {
    long micros = duration.toNanos() / 1000L;
    return String.format(Locale.ROOT, "duration %d.%06d\n", micros / 1_000_000, micros % 1_000_000);
  }

  @GuardedBy("lock")
  private void writeFfconcat(String text, boolean append) throws MobileHarnessException {
    localFileUtil.writeToFile(hostFolder.resolve(FFCONCAT_FILE_NAME).toString(), text, append);
  }

  @Override
  public List<Path> stop() throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Stopping screenshot recorder...");
    ScheduledExecutorService executor;
    synchronized (lock) {
      stopped = true;
      if (captureFuture != null) {
        // Keep a pending screenshot from starting. shutdownNow() below interrupts an in-flight
        // screenshot, which kills its adb process instead of holding the device until it completes.
        captureFuture.cancel(/* mayInterruptIfRunning= */ false);
        captureFuture = null;
      }
      executor = executorService;
      executorService = null;
    }
    if (executor != null) {
      executor.shutdownNow();
      if (!executor.awaitTermination(STOP_TIMEOUT.toMillis(), MILLISECONDS)) {
        logger.atWarning().log("Screenshot capture did not stop within %s.", STOP_TIMEOUT);
      }
    }
    if (hostFolder == null) {
      return ImmutableList.of();
    }

    long stopNanos = ticker.read();
    synchronized (lock) {
      try {
        Files.deleteIfExists(hostFolder.resolve(RAW_CAPTURE_FILE_NAME));
        if (lastFrameDurationPending) {
          Duration lastFrameDuration = Duration.ofNanos(stopNanos - lastFrameNanos);
          if (lastFrameDuration.compareTo(MIN_LAST_FRAME_DURATION) < 0) {
            lastFrameDuration = MIN_LAST_FRAME_DURATION;
          }
          // Repeat the last file directive so that FFmpeg's concat demuxer honors its duration.
          writeFfconcat(
              durationLine(lastFrameDuration) + fileLine(Iterables.getLast(frameFileNames)),
              /* append= */ true);
          lastFrameDurationPending = false;
        }
      } catch (IOException | MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Failed to finalize %s.", FFCONCAT_FILE_NAME);
      }
      logSummary(stopNanos);

      if (frameFileNames.isEmpty()) {
        return ImmutableList.of();
      }
      ImmutableList.Builder<Path> files = ImmutableList.builder();
      files.add(hostFolder.resolve(FFCONCAT_FILE_NAME));
      for (String frameFileName : frameFileNames) {
        files.add(hostFolder.resolve(frameFileName));
      }
      return files.build();
    }
  }

  @GuardedBy("lock")
  private void logSummary(long stopNanos) {
    double seconds = (stopNanos - startNanos) / 1e9;
    logger.atInfo().log(
        "Screenshot recorder captured %d screenshots in %.1f s (%.2f/s): %d unique frames,"
            + " %d duplicates skipped, %d failures, average screencap latency %d ms",
        captureCount,
        seconds,
        seconds > 0 ? captureCount / seconds : 0.0,
        frameFileNames.size(),
        duplicateCount,
        failureCount,
        captureCount > 0 ? totalCaptureMillis / captureCount : 0);
  }
}
