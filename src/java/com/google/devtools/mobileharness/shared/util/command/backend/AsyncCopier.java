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

package com.google.devtools.mobileharness.shared.util.command.backend;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Copies an InputStream to an OutputStream asynchronously. */
final class AsyncCopier {
  /** Injectable strategy for performing a synchronous copy. */
  @VisibleForTesting
  interface CopyStrategy {
    void copy(InputStream from, OutputStream to) throws IOException;
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting static final CopyStrategy REAL_COPY_STRATEGY = ByteStreams::copy;

  /**
   * Starts an asynchronous copy from the input stream to the output stream. The returned copier
   * assumes responsibility for closing the streams when the copy is complete.
   */
  static AsyncCopier start(
      InputStream from, OutputStream to, Consumer<IOException> ioExceptionHandler) {
    return new AsyncCopier(
        from, to, ioExceptionHandler, REAL_COPY_STRATEGY, NativeProcess.EXECUTOR_SERVICE);
  }

  private final InputStream source;
  private final OutputStream sink;
  private final Consumer<IOException> ioExceptionHandler;
  private final CopyStrategy copyStrategy;
  private final Future<?> copyFuture;

  private final CountDownLatch copyStarted = new CountDownLatch(1);
  private final CountDownLatch copyTerminated = new CountDownLatch(1);

  @VisibleForTesting
  AsyncCopier(
      InputStream source,
      OutputStream sink,
      Consumer<IOException> ioExceptionHandler,
      CopyStrategy copyStrategy,
      ExecutorService executorService) {
    this.source = source;
    this.sink = sink;
    this.ioExceptionHandler = ioExceptionHandler;
    this.copyStrategy = copyStrategy;

    // Submit the copy task and wait uninterruptibly, but very briefly, for it to actually start.
    copyFuture = executorService.submit(this::copy);
    Uninterruptibles.awaitUninterruptibly(copyStarted);
  }

  private void copy() {
    copyStarted.countDown();
    try {
      try {
        copyStrategy.copy(source, sink);
      } catch (IOException e) {
        ioExceptionHandler.accept(e);
      } finally {
        closeStreams();
      }
    } catch (RuntimeException e) {
      logger.atSevere().withCause(e).log();
    } finally {
      copyTerminated.countDown();
    }
  }

  /** Waits for the asynchronous copy to complete. */
  void await() throws InterruptedException {
    copyTerminated.await();
  }

  /** Stops the asynchronous copy and blocks until it terminates. */
  @SuppressWarnings("Interruption")
  void stop() throws InterruptedException {
    // Unfortunately, if the streams are blocked, there is no provided method to unblock them.
    // That said, interrupting and then closing the streams appears to work for all streams used in
    // practice and likely for all conceivably valid streams in existence, so we do that.
    // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=4514257
    // http://stackoverflow.com/a/4182848

    // Interrupt the copyFuture, in case the input source interruptible, like PipedInputStream.
    copyFuture.cancel(true);

    // Close the streams, which should unblock all other kinds of streams.
    closeStreams();

    await();
  }

  private void closeStreams() {
    try {
      try {
        source.close();
      } finally {
        sink.close();
      }
    } catch (IOException e) {
      ioExceptionHandler.accept(e);
    }
  }
}
