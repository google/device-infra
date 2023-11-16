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

package com.google.devtools.mobileharness.infra.ats.console;

import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleOutput;
import com.google.errorprone.annotations.FormatMethod;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.zip.ZipFile;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.io.FilenameUtils;

/** Util class for the console. */
@Singleton
public class ConsoleUtil {

  private final Object consoleOutputLock = new Object();

  @GuardedBy("consoleOutputLock")
  private final PrintStream consoleOutputOut;

  @GuardedBy("consoleOutputLock")
  private final PrintStream consoleOutputErr;

  @Inject
  ConsoleUtil(
      @ConsoleOutput(ConsoleOutput.Type.OUT_STREAM) PrintStream consoleOutputOut,
      @ConsoleOutput(ConsoleOutput.Type.ERR_STREAM) PrintStream consoleOutputErr) {
    this.consoleOutputOut = consoleOutputOut;
    this.consoleOutputErr = consoleOutputErr;
  }

  /** Expands path prefixed with "~/" with user home expanded path. */
  public String completeHomeDirectory(String path) {
    if (path.startsWith("~" + File.separator)) {
      return System.getProperty("user.home") + path.substring(1);
    }
    if (path.equals("~")) {
      return System.getProperty("user.home");
    }
    return path;
  }

  /**
   * Displays a text (command output or user-requested help) (without trailing "\n") on the console
   * (stdout).
   *
   * <p>The method is thread safe.
   */
  @FormatMethod
  public void printStdout(String format, Object... args) {
    synchronized (consoleOutputLock) {
      consoleOutputOut.printf(format, args);
    }
  }

  /**
   * Displays a text (command output or user-requested help) (without trailing "\n") on the console
   * (stdout).
   *
   * <p>The method is thread safe.
   */
  public void printStdout(String text) {
    synchronized (consoleOutputLock) {
      consoleOutputOut.print(text);
    }
  }

  /**
   * Displays a text line (command output or user-requested help) on the console (stdout).
   *
   * <p>The method is thread safe.
   */
  @FormatMethod
  public void printlnStdout(String format, Object... args) {
    synchronized (consoleOutputLock) {
      consoleOutputOut.printf(format, args);
      consoleOutputOut.println();
    }
  }

  /**
   * Displays a text line (command output or user-requested help) on the console (stdout).
   *
   * <p>The method is thread safe.
   */
  public void printlnStdout(String text) {
    synchronized (consoleOutputLock) {
      consoleOutputOut.print(text);
      consoleOutputOut.println();
    }
  }

  /**
   * Displays a text (error messages or log) (without trailing "\n") on the console (stderr).
   *
   * <p>The method is thread safe.
   */
  @FormatMethod
  public void printStderr(String format, Object... args) {
    synchronized (consoleOutputLock) {
      consoleOutputErr.printf(format, args);
    }
  }

  /**
   * Displays a text (error messages or log) (without trailing "\n") on the console (stderr).
   *
   * <p>The method is thread safe.
   */
  public void printStderr(String text) {
    synchronized (consoleOutputLock) {
      consoleOutputErr.print(text);
    }
  }

  /**
   * Displays a text line (error messages or log) on the console (stderr).
   *
   * <p>The method is thread safe.
   */
  @FormatMethod
  public void printlnStderr(String format, Object... args) {
    synchronized (consoleOutputLock) {
      consoleOutputErr.printf(format, args);
      consoleOutputErr.println();
    }
  }

  /**
   * Displays a text line (error messages or log) on the console (stderr).
   *
   * <p>The method is thread safe.
   */
  public void printlnStderr(String text) {
    synchronized (consoleOutputLock) {
      consoleOutputErr.print(text);
      consoleOutputErr.println();
    }
  }

  public void flushConsoleOutput() {
    synchronized (consoleOutputLock) {
      consoleOutputOut.flush();
      consoleOutputErr.flush();
    }
  }

  /** Checks if the given file is a zip file. */
  public boolean isZipFile(File file) {
    String fileExt = FilenameUtils.getExtension(file.getPath());
    if (!fileExt.isEmpty() && !fileExt.equals("zip")) {
      return false;
    }
    try (ZipFile ignored = new ZipFile(file)) {
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
