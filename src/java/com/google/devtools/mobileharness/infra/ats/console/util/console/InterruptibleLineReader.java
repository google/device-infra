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

package com.google.devtools.mobileharness.infra.ats.console.util.console;

import static com.google.common.base.Preconditions.checkState;

import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.infra.ats.console.util.command.CommandHelper;
import java.util.Optional;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

/** Interruptible {@link LineReader}. */
@Singleton
public class InterruptibleLineReader {

  private final LineReader lineReader;
  private final ConsoleUtil consoleUtil;
  private final CommandHelper commandHelper;

  private final Object lock = new Object();

  @GuardedBy("lock")
  private Thread runningThread;

  @GuardedBy("lock")
  private boolean interrupted;

  @Inject
  InterruptibleLineReader(
      @ConsoleLineReader LineReader lineReader,
      ConsoleUtil consoleUtil,
      CommandHelper commandHelper) {
    this.lineReader = lineReader;
    this.consoleUtil = consoleUtil;
    this.commandHelper = commandHelper;
  }

  /**
   * Reads a line from the console.
   *
   * @return a {@link String} containing the line to parse and run, or empty if the console is not
   *     available, interrupted (for example Ctrl-C or kill/exit command), or an EOF has been found
   *     (for example Ctrl-D)
   */
  public Optional<String> readLine() {
    synchronized (lock) {
      checkState(runningThread == null);
      if (interrupted) {
        interrupted = false;
        return Optional.empty();
      }
      runningThread = Thread.currentThread();
    }

    try {
      return Optional.of(
          lineReader.readLine(String.format("%s-console > ", commandHelper.getXtsType())));
    } catch (UserInterruptException e) {
      consoleUtil.printlnStderr("Interrupted by the user.");
      return Optional.empty();
    } catch (EndOfFileException e) {
      consoleUtil.printlnStderr("Received EOF.");
      return Optional.empty();
    } finally {

      synchronized (lock) {
        // Clears the interrupted flag.
        Thread.interrupted();
        runningThread = null;
      }
    }
  }

  /**
   * Interrupts a running {@link #readLine()} call (if any), or makes the next call return empty
   * immediately.
   *
   * @apiNote this method will make the console exit, and the shutdown hook will kill the OLC server
   */
  @SuppressWarnings("Interruption")
  public void interrupt() {
    consoleUtil.printlnStdout("Exiting...");
    synchronized (lock) {
      if (runningThread == null) {
        consoleUtil.printlnStderr("Stop line reader");
        interrupted = true;
      } else {
        consoleUtil.printlnStderr("Interrupt line reader");
        runningThread.interrupt();
      }
    }
  }
}
