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

import com.google.devtools.deviceinfra.api.error.id.defined.BasicErrorId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommandFailureExceptionTest {

  @Test
  public void result() {
    CommandResult commandResult = new CommandResult("out", "err", 123, false, false);
    CommandFailureException commandFailureException =
        new CommandFailureException(null, Command.of("").successExitCodes(1, 2, 3), commandResult);
    assertThat(commandFailureException.result()).isEqualTo(commandResult);
    assertThat(commandFailureException)
        .hasMessageThat()
        .startsWith("Failed command with exit_code=123 and success_exit_codes=");
    assertThat(commandFailureException).hasMessageThat().contains("result=[" + commandResult + "]");
    assertThat(commandFailureException.getErrorId()).isEqualTo(BasicErrorId.CMD_EXEC_FAIL);
  }

  @Test
  public void getMessage_longResult() {
    String longStdout = CommandResultTest.getLongString();
    CommandResult commandResult = new CommandResult(longStdout, "err", 123, false, false);

    CommandFailureException commandFailureException =
        new CommandFailureException(null, Command.of(""), commandResult);
    assertThat(commandFailureException).hasMessageThat().doesNotContain(longStdout);

    commandFailureException =
        new CommandFailureException(
            null, Command.of("").showFullResultInException(true), commandResult);
    assertThat(commandFailureException).hasMessageThat().contains(longStdout);
  }
}
