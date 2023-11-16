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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import javax.annotation.concurrent.GuardedBy;
import org.jline.reader.LineReader;

/**
 * An OutputStream that can be used to make {@code System.err.print()} play nice with the user's
 * {@link LineReader} unfinishedLine.
 */
public class ConsoleReaderOutputStream extends OutputStream {

  private final LineReader lineReader;
  private final Object lock = new Object();

  @GuardedBy("lock")
  private String unfinishedLine = "";

  public ConsoleReaderOutputStream(LineReader lineReader) {
    this.lineReader = lineReader;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    synchronized (lock) {
      String str = unfinishedLine + new String(b, off, len, UTF_8);
      int indexOfLastNewLine = str.lastIndexOf("\n");
      if (indexOfLastNewLine == -1) {
        unfinishedLine = str;
      } else {
        unfinishedLine = str.substring(indexOfLastNewLine + 1);
        lineReader.printAbove(str.substring(0, indexOfLastNewLine));
      }
    }
  }

  @Override
  public void write(int b) throws IOException {
    synchronized (lock) {
      char[] str = new char[] {(char) (b & 0xff)};
      lineReader.printAbove(new String(str));
    }
  }
}
