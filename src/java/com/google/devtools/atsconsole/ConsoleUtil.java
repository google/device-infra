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

package com.google.devtools.atsconsole;

import com.google.devtools.atsconsole.Annotations.ConsoleOutput;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.zip.ZipFile;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.io.FilenameUtils;

/** Util class for the console. */
@Singleton
public class ConsoleUtil {

  private final PrintStream consoleOutputOut;
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
   * Displays a text line (command output or user-requested help) on the console.
   *
   * @param output which is displayed on the console (stdout)
   */
  public void printLine(String output) {
    consoleOutputOut.println(output);
  }

  /**
   * Displays a text line (error messages or log) on the console.
   *
   * @param error which is displayed on the console (stderr)
   */
  public void printErrorLine(String error) {
    consoleOutputErr.println(error);
  }

  public void flushConsoleOutput() {
    consoleOutputOut.flush();
    consoleOutputErr.flush();
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
