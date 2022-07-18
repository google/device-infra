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

package com.google.devtools.deviceinfra.shared.util.command;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommandResultTest {

  @Test
  public void test() {
    CommandResult commandResult = new CommandResult("stdout\r\n", "stderr\n\r", 123, false, false);

    assertThat(commandResult.stdout()).isEqualTo("stdout\r\n");
    assertThat(commandResult.stderr()).isEqualTo("stderr\n\r");
    assertThat(commandResult.exitCode()).isEqualTo(123);
    assertThat(commandResult.toString()).isEqualTo("code=123, out=[stdout\r\n], err=[stderr\n\r]");
    assertThat(commandResult.stdoutWithoutTrailingLineTerminator()).isEqualTo("stdout");
    assertThat(commandResult.stderrWithoutTrailingLineTerminator()).isEqualTo("stderr\n");

    commandResult = new CommandResult("stdout\r\r\n", "\n", 123, false, false);
    assertThat(commandResult.stdoutWithoutTrailingLineTerminator()).isEqualTo("stdout\r");
    assertThat(commandResult.stderrWithoutTrailingLineTerminator()).isEmpty();
  }

  @Test
  public void toString_truncatesOnLongOutput() {
    String longOutput = getLongString();
    CommandResult commandResult = new CommandResult(longOutput, "stderr", 123, false, false);

    assertThat(commandResult.toString()).doesNotContain(longOutput);
  }

  @Test
  public void toStringWithoutTruncation() {
    String longOutput = getLongString();
    CommandResult commandResult = new CommandResult(longOutput, "stderr", 123, false, false);

    assertThat(commandResult.toStringWithoutTruncation()).contains(longOutput);
  }

  static String getLongString() {
    return "This is at least 10 characters".repeat(1000);
  }
}
