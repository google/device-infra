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

import com.google.common.io.ByteSink;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Line reader that implements the line reading algorithm. Line separators are per {@link
 * java.io.BufferedReader}: line feed, carriage return, or carriage return followed immediately by a
 * linefeed.
 */
@NotThreadSafe
public class LineReader extends ByteSink {

  /** Handler for handling a line with line separator. */
  interface LineHandler {

    /**
     * Called for each line found in the character data.
     *
     * @param line a line of text (possibly empty), without any line separators
     * @param end the line separator; one of {@code "\r"}, {@code "\n"}, {@code "\r\n"}, or {@code
     *     ""}
     * @return whether to stop handling new lines
     */
    boolean handleLine(String line, String end);

    /**
     * Called after the output stream returned by {@link #openStream()} is closed and all lines are
     * handled.
     */
    void onSourceClosed();
  }

  private static final int READ_BUFFER_SIZE = 512;

  /** Holds partial line contents. */
  private StringBuilder line = new StringBuilder();

  /** Whether a line ending with a CR is pending processing. */
  private boolean sawReturn;

  /** Whether to stop handling new lines. Line reading will not be affected. */
  private boolean stopHandling;

  private final PipedOutputStream outputStream;
  private final Reader reader;
  private final char[] readBuffer = new char[READ_BUFFER_SIZE];

  public LineReader() {
    this.outputStream = new PipedOutputStream();
    try {
      this.reader =
          new InputStreamReader(new PipedInputStream(this.outputStream), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public OutputStream openStream() {
    return outputStream;
  }

  /**
   * Starts to read lines from data written into the output stream returned by {@link
   * #openStream()}.
   *
   * <p>This method returns when the output stream is closed and all lines are handled.
   */
  public void start(LineHandler lineHandler) throws IOException {
    try {
      int count;
      while ((count = reader.read(readBuffer)) != -1) {
        if (!stopHandling) {
          add(count, lineHandler);
        }
      }
      finish(lineHandler);
    } finally {
      lineHandler.onSourceClosed();
    }
  }

  /**
   * Process additional characters from the stream. When a line separator is found the contents of
   * the line and the line separator itself are passed to the {@link LineHandler#handleLine} method.
   *
   * @param len the number of characters to process
   * @see #finish
   */
  private void add(int len, LineHandler lineHandler) {
    int pos = 0;
    if (sawReturn && len > 0) {
      // Last call to add ended with a CR; we can handle the line now.
      if (finishLine(readBuffer[pos] == '\n', lineHandler)) {
        pos++;
      }
    }

    int start = pos;
    for (; pos < len; pos++) {
      switch (readBuffer[pos]) {
        case '\r':
          line.append(readBuffer, start, pos - start);
          sawReturn = true;
          if (pos + 1 < len) {
            if (finishLine(readBuffer[pos + 1] == '\n', lineHandler)) {
              pos++;
            }
          }
          start = pos + 1;
          break;

        case '\n':
          line.append(readBuffer, start, pos - start);
          finishLine(true, lineHandler);
          start = pos + 1;
          break;

        default:
          // Does nothing.
      }
    }
    line.append(readBuffer, start, len - start);
  }

  /** Called when a line is complete. */
  @CanIgnoreReturnValue
  private boolean finishLine(boolean sawNewline, LineHandler lineHandler) {
    String separator = sawReturn ? (sawNewline ? "\r\n" : "\r") : (sawNewline ? "\n" : "");
    if (!stopHandling) {
      stopHandling = lineHandler.handleLine(line.toString(), separator);
    }
    line = new StringBuilder();
    sawReturn = false;
    return sawNewline;
  }

  /**
   * Must call this method after finishing character processing, in order to ensure that any
   * unterminated line in the buffer is passed to {@link LineHandler#handleLine}.
   */
  private void finish(LineHandler lineHandler) {
    if (sawReturn || line.length() > 0) {
      finishLine(false, lineHandler);
    }
  }
}
