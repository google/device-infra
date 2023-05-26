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

package com.google.devtools.mobileharness.shared.util.command;

import com.google.common.io.ByteSink;
import com.google.devtools.mobileharness.shared.util.command.backend.Command;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Command output sink for receiving stdout/stderr of a command.
 *
 * <p>It can be passed to {@link Command#withStdoutTo(ByteSink)} and {@link
 * Command#withStderrTo(ByteSink)} as replacements of {@code OutputSink#toProcessOut()} and {@code
 * OutputSink#toProcessErr()}, which keep the whole output of stdout/stderr in memory and are not
 * fit for long-lasting commands.
 *
 * <p>Use {@link #getBufferedReader()} to read real-time data and use {@link #awaitResult()} to get
 * the whole result after {@link OutputStream#close()} on {@link #openStream()} and {@link
 * #closePipe()} (if it needs real-time data) have been invoked.
 */
class CommandOutputSink extends ByteSink {

  private final CompositeOutputStream compositeOutputStream;
  private final BufferedReader bufferedReader;

  /**
   * Constructor.
   *
   * @param needResult if it is true, {@link #awaitResult()} will return a string containing all
   *     data written to {@link #openStream()} before the stream is closed, and an empty string
   *     otherwise
   * @param needReader if it is true, {@link #getBufferedReader()} will return a reader containing
   *     all data written to {@link #openStream()} before the stream is closed, and an empty reader
   *     otherwise. If it is true, {@link #awaitResult()} will block until {@link #closePipe()} is
   *     invoked.
   * @param openStreamCount the count that {@link #openStream()} will be invoked. Only after all
   *     output streams return by it is closed, {@link #awaitResult()} will return.
   */
  CommandOutputSink(boolean needResult, boolean needReader, int openStreamCount) {
    try {
      compositeOutputStream = new CompositeOutputStream(needResult, needReader, openStreamCount);
      bufferedReader =
          new BufferedReader(
              new InputStreamReader(
                  new PipedInputStream(compositeOutputStream.pipedOutputStream),
                  StandardCharsets.UTF_8));
      if (!needReader) {
        compositeOutputStream.pipedOutputStream.close();
      }
      if (openStreamCount == 0) {
        compositeOutputStream.close();
      }
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public OutputStream openStream() {
    return compositeOutputStream;
  }

  /**
   * Gets a buffered reader using UTF-8 encode and containing all data written to {@link
   * #openStream()} and which ends when the stream is closed.
   */
  BufferedReader getBufferedReader() {
    return bufferedReader;
  }

  /**
   * Closes the pipe and indicates that there will be no more read operation on {@link
   * #getBufferedReader()}.
   */
  void closePipe() throws IOException {
    compositeOutputStream.closePipeLatch.countDown();
    if (compositeOutputStream.writeToPipe.getAndSet(false)) {
      while (bufferedReader.ready() && bufferedReader.read() != -1) {}
    }
  }

  /** Returns whether following {@link #awaitResult()} will block. */
  boolean isClosed() {
    return compositeOutputStream.closePipeLatch.getCount() == 0L
        && compositeOutputStream.stringOutputStream.isClosed();
  }

  /**
   * Blocks until {@link #openStream()} is closed and {@link #closePipe()} is invoked (if {@code
   * needReader} is {@code true} in the constructor) and then returns a string containing all data
   * written to the stream.
   */
  String awaitResult() throws InterruptedException {
    compositeOutputStream.closePipeLatch.await();
    return compositeOutputStream.stringOutputStream.await();
  }

  /**
   * Blocks until {@link #openStream()} is closed and {@link #closePipe()} is invoked (if {@code
   * needReader} is {@code true} in the constructor) or the timeout is reached and then returns a
   * string containing all data written to the stream.
   */
  String awaitResult(Duration timeout) throws InterruptedException, TimeoutException {
    Instant deadline = Clock.systemUTC().instant().plus(timeout);
    if (!compositeOutputStream.closePipeLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
      throw new TimeoutException("Command process is still handling stdout/stderr");
    }
    return compositeOutputStream.stringOutputStream.await(
        Duration.between(Clock.systemUTC().instant(), deadline));
  }

  private static class CompositeOutputStream extends OutputStream {

    private final boolean writeToString;
    private final AtomicBoolean writeToPipe;
    private final CountDownLatch closePipeLatch;
    private final AtomicInteger restCloseCount;

    private final StringOutputStream stringOutputStream = new StringOutputStream();
    private final PipedOutputStream pipedOutputStream = new PipedOutputStream();

    private CompositeOutputStream(boolean writeToString, boolean writeToPipe, int restCloseCount) {
      this.writeToString = writeToString;
      this.writeToPipe = new AtomicBoolean(writeToPipe);
      this.closePipeLatch = new CountDownLatch(writeToPipe ? 1 : 0);
      this.restCloseCount = new AtomicInteger(restCloseCount);
    }

    @Override
    public void write(int b) throws IOException {
      if (writeToString) {
        stringOutputStream.write(b);
      }
      if (writeToPipe.get()) {
        pipedOutputStream.write(b);

        // Flushes the piped output stream here to reduce output callback delay.
        pipedOutputStream.flush();
      }
    }

    @Override
    public void flush() throws IOException {
      super.flush();
      if (writeToString) {
        stringOutputStream.flush();
      }
      if (writeToPipe.get()) {
        pipedOutputStream.flush();
      }
    }

    /**
     * Closes the output stream and indicates that no more data will be written to the sink.
     *
     * <p>{@inheritDoc}
     */
    @Override
    public void close() throws IOException {
      if (restCloseCount.decrementAndGet() <= 0) {
        super.close();
        stringOutputStream.close();
        pipedOutputStream.close();
      }
    }
  }

  private static class StringOutputStream extends ByteArrayOutputStream {

    private final CountDownLatch closeLatch = new CountDownLatch(1);
    private final Object lock = new Object();
    private volatile String string;

    @Override
    public void close() throws IOException {
      super.close();
      closeLatch.countDown();
    }

    private boolean isClosed() {
      return closeLatch.getCount() == 0L;
    }

    private String await() throws InterruptedException {
      closeLatch.await();
      return getString();
    }

    private String await(Duration timeout) throws InterruptedException, TimeoutException {
      if (!closeLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new TimeoutException("Command process stdout/stderr stream is still open");
      }
      return getString();
    }

    private String getString() {
      if (string == null) {
        synchronized (lock) {
          if (string == null) {
            string = new String(buf, 0, count, StandardCharsets.UTF_8);
          }
        }
      }
      return string;
    }
  }
}
