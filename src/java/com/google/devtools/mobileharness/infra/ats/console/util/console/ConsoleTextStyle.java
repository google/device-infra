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

import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

/** Constants of text styles in ATS console. */
@SuppressWarnings("ImmutableEnumChecker") // AttributedStyle is immutable.
public enum ConsoleTextStyle {
  CONSOLE_STDOUT(AttributedStyle.DEFAULT),
  CONSOLE_STDERR(AttributedStyle.DEFAULT.italic().foreground(AttributedStyle.MAGENTA)),
  OLC_SERVER_LOG(AttributedStyle.DEFAULT.italic().foreground(AttributedStyle.BLUE)),
  TF_STDOUT(AttributedStyle.DEFAULT.italic().foreground(AttributedStyle.GREEN));

  private final AttributedStyle style;

  ConsoleTextStyle(AttributedStyle style) {
    this.style = style;
  }

  void printAbove(LineReader lineReader, String text) {
    if (style.equals(AttributedStyle.DEFAULT)) {
      lineReader.printAbove(text);
    } else {
      lineReader.printAbove(new AttributedString(text, style));
    }
  }
}
