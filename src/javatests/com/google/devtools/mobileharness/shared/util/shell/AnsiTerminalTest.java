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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.shell.AnsiTerminal.Color;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AnsiTerminalTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Test
  public void highlight_red() {
    String message = AnsiTerminal.highlight("Big red hello", Color.RED) + ", not highlighted";
    // Check the color directly from the log.
    logger.atInfo().log("Highlighted message: %s", message);
    assertThat(message).contains("hello");
  }
}
