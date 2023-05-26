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

package com.google.devtools.mobileharness.shared.util.command.io;

import com.google.devtools.mobileharness.shared.util.command.io.LineReader.LineHandler;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/** Line collector for collecting lines from {@link LineReader}. */
@ThreadSafe
public class LineCollector implements LineHandler {

  private final CountDownLatch sourceCloseLatch;
  private final boolean needAllLines;
  private final Object handleLineLock = new Object();

  @GuardedBy("handleLineLock")
  private final StringBuilder allLinesBuilder = new StringBuilder();

  @GuardedBy("handleLineLock")
  private String allLines;

  @GuardedBy("handleLineLock")
  private Predicate<String> lineConsumer;

  @GuardedBy("handleLineLock")
  private boolean stopConsumingLines;

  public LineCollector(int numSource, boolean needAllLines) {
    this.sourceCloseLatch = new CountDownLatch(numSource);
    this.needAllLines = needAllLines;
  }

  @Override
  public boolean handleLine(String line, String end) {
    synchronized (handleLineLock) {
      if (needAllLines) {
        allLinesBuilder.append(line);
        allLinesBuilder.append(end);
      }
      if (!stopConsumingLines && lineConsumer != null) {
        stopConsumingLines = lineConsumer.test(line);
      }
      return !needAllLines && stopConsumingLines;
    }
  }

  @Override
  public void onSourceClosed() {
    sourceCloseLatch.countDown();
  }

  public void setLineConsumer(Predicate<String> lineConsumer) {
    synchronized (handleLineLock) {
      this.lineConsumer = lineConsumer;
    }
  }

  /** Returns after all sources are closed and all lines are handled. */
  public String waitForAllLines() throws InterruptedException {
    sourceCloseLatch.await();
    return getAllLines();
  }

  /** Returns after all sources are closed and all lines are handled or the timeout is reached. */
  public String waitForAllLines(Duration timeout) throws InterruptedException {
    sourceCloseLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    return getAllLines();
  }

  public boolean notAllSourceClosed() {
    return sourceCloseLatch.getCount() != 0L;
  }

  private String getAllLines() {
    synchronized (handleLineLock) {
      if (allLines == null) {
        allLines = allLinesBuilder.toString();
      }
      return allLines;
    }
  }
}
