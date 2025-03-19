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

package com.google.devtools.mobileharness.shared.util.shell;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.protobuf.ByteString;

/**
 * Utility class that encapsulates the fancy curses-type stuff that you can do using standard ANSI
 * terminal control sequences.
 */
public final class AnsiTerminal {

  /**
   * An enumeration of all terminal colors, containing the escape sequences for both background and
   * foreground settings.
   */
  public enum Color {
    RED("^[31m", "^[41m"),
    GREEN("^[32m", "^[42m"),
    YELLOW("^[33m", "^[43m"),
    BLUE("^[34m", "^[44m"),
    MAGENTA("^[35m", "^[45m"),
    CYAN("^[36m", "^[46m"),
    GRAY("^[37m", "^[47m"),

    DEFAULT("^[0m", "^[0m");

    private final ByteString escapeSeq;
    private final ByteString backgroundEscapeSeq;

    private Color(String escapeSeq, String backgroundEscapeSeq) {
      this.escapeSeq = ByteString.copyFrom(escapeSeq.replace('^', (char) 27).getBytes(US_ASCII));
      this.backgroundEscapeSeq =
          ByteString.copyFrom(backgroundEscapeSeq.replace('^', (char) 27).getBytes(US_ASCII));
    }

    public byte[] getEscapeSeq() {
      return escapeSeq.toByteArray();
    }

    public String getEscapeSeqString() {
      return new String(getEscapeSeq(), US_ASCII);
    }

    public byte[] getBackgroundEscapeSeq() {
      return backgroundEscapeSeq.toByteArray();
    }

    public String getBackgroundEscapeSeqString() {
      return new String(getBackgroundEscapeSeq(), US_ASCII);
    }
  }

  /**
   * Highlights the given message with the given color.
   *
   * <p>Restore the default color after the message.
   *
   * @param message the message to highlight
   * @param color the color to highlight the message with
   * @return the highlighted message
   */
  public static String highlight(String message, Color color) {
    return color.getEscapeSeqString() + message + Color.DEFAULT.getEscapeSeqString();
  }

  private AnsiTerminal() {}
}
