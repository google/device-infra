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
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommandTimeoutExceptionTest {

  @Test
  public void timeout() {
    CommandTimeoutException exception =
        new CommandTimeoutException(
            Command.of("whatever"),
            Duration.ofSeconds(15L),
            Duration.ofSeconds(5L),
            new CommandResult("stdout", "stderr", 123, true, false));
    assertThat(exception)
        .hasMessageThat()
        .startsWith("Command timeout (timeout=PT15S, start_timeout=PT5S)");
    assertThat(exception.getErrorId()).isEqualTo(BasicErrorId.CMD_EXEC_TIMEOUT);
  }
}
