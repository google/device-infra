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
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class CommandTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final Instant INSTANT = Clock.systemUTC().instant();

  @SuppressWarnings("DoNotMockAutoValue")
  @Mock
  private Timeout timeout;

  @Mock private Predicate<String> successStartCondition;
  @Mock private Runnable timeoutCallback;
  @Mock private LineCallback lineCallback;
  @Mock private Consumer<CommandResult> exitCallback;

  @Test
  public void command() {
    assertThat(Command.of("e").getArguments()).isEmpty();
    assertThat(Command.of("e", "a1", "a2").getExecutable()).isEqualTo("e");
    assertThat(Command.of("e", "a1", "a2").getArguments()).containsExactly("a1", "a2").inOrder();
    assertThat(Command.of("e", "a1", "a2").getCommand()).containsExactly("e", "a1", "a2").inOrder();
    assertThat(Command.of("e", ImmutableList.of("a1", "a2")).getArguments())
        .containsExactly("a1", "a2")
        .inOrder();
    assertThat(Command.of(ImmutableList.of("e", "a1", "a2")).getExecutable()).isEqualTo("e");
    assertThat(Command.of(ImmutableList.of("e", "a1", "a2")).getArguments())
        .containsExactly("a1", "a2")
        .inOrder();
    assertThat(Command.of("e").executable("e2").getExecutable()).isEqualTo("e2");
    assertThat(Command.of("e", "a1", "a2").args("a3", "a4").getArguments())
        .containsExactly("a3", "a4")
        .inOrder();
    assertThat(Command.of("e", "a1", "a2").args(ImmutableList.of("a3", "a4")).getArguments())
        .containsExactly("a3", "a4")
        .inOrder();
    assertThat(Command.of("e", "a1", "a2").argsAppended("a3", "a4").getArguments())
        .containsExactly("a1", "a2", "a3", "a4")
        .inOrder();
    assertThat(
            Command.of("e", "a1", "a2").argsAppended(ImmutableList.of("a3", "a4")).getArguments())
        .containsExactly("a1", "a2", "a3", "a4")
        .inOrder();
    assertThat(Command.of("").timeout(timeout).getTimeout()).hasValue(timeout);
    assertThat(Command.of("").timeout(INSTANT).getTimeout()).hasValue(Timeout.deadline(INSTANT));
    assertThat(
            Command.of("")
                .timeout(Instant.parse("2007-12-03T10:15:30.00Z"))
                .getTimeout()
                .flatMap(Timeout::getDeadline))
        .hasValue(Instant.parse("2007-12-03T10:15:30.00Z"));
    assertThat(Command.of("").timeout(Duration.ofMillis(2L)).getTimeout())
        .hasValue(Timeout.fixed(Duration.ofMillis(2L)));
    assertThat(Command.of("").timeout(Duration.ofMillis(2L)).getTimeout())
        .hasValue(Timeout.fixed(Duration.ofMillis(2L)));
    assertThat(Command.of("").startTimeout(timeout).getStartTimeout()).hasValue(timeout);
    assertThat(Command.of("").startTimeout(INSTANT).getStartTimeout())
        .hasValue(Timeout.deadline(INSTANT));
    assertThat(
            Command.of("")
                .startTimeout(Instant.parse("2007-12-03T10:15:30.00Z"))
                .getStartTimeout()
                .flatMap(Timeout::getDeadline))
        .hasValue(Instant.parse("2007-12-03T10:15:30.00Z"));
    assertThat(Command.of("").startTimeout(Duration.ofMillis(2L)).getStartTimeout())
        .hasValue(Timeout.fixed(Duration.ofMillis(2L)));
    assertThat(Command.of("").startTimeout(Duration.ofMillis(2L)).getStartTimeout())
        .hasValue(Timeout.fixed(Duration.ofMillis(2L)));
    assertThat(Command.of("").getSuccessStartCondition())
        .isEqualTo(Command.DEFAULT_SUCCESS_START_CONDITION);
    assertThat(
            Command.of("").successStartCondition(successStartCondition).getSuccessStartCondition())
        .isEqualTo(successStartCondition);
    assertThat(Command.of("").onTimeout(timeoutCallback).getTimeoutCallback())
        .hasValue(timeoutCallback);
    assertThat(Command.of("").onStdout(lineCallback).getStdoutLineCallback())
        .hasValue(lineCallback);
    assertThat(Command.of("").onStderr(lineCallback).getStderrLineCallback())
        .hasValue(lineCallback);
    assertThat(Command.of("").onExit(exitCallback).getExitCallback()).hasValue(exitCallback);
    assertThat(Command.of("").getSuccessExitCodes())
        .containsExactly(Command.DEFAULT_SUCCESS_EXIT_CODE);
    assertThat(Command.of("").successExitCodes(1, 2, 3).getSuccessExitCodes())
        .containsExactly(1, 2, 3);
    assertThat(Command.of("").successExitCodes(ImmutableSet.of(1, 2, 3)).getSuccessExitCodes())
        .containsExactly(1, 2, 3);
    assertThat(Command.of("").input("Input").getInput()).hasValue("Input");
    assertThat(Command.of("").inputLn("Input").getInput()).hasValue("Input\n");
    assertThat(Command.of("").workDir(Paths.get("/")).getWorkDirectory().map(Path::toString))
        .hasValue("/");
    assertThat(Command.of("").workDir("/").getWorkDirectory().map(Path::toString)).hasValue("/");
    assertThat(Command.of("").getExtraEnvironment()).isEmpty();
    assertThat(Command.of("").extraEnv("k1", "v1", "k2", "v2").getExtraEnvironment())
        .containsExactly("k1", "v1", "k2", "v2");
    assertThat(
            Command.of("").extraEnv(ImmutableMap.of("k1", "v1", "k2", "v2")).getExtraEnvironment())
        .containsExactly("k1", "v1", "k2", "v2");
    assertThat(Command.of("").redirectStderr(true).getRedirectStderr()).hasValue(true);
    assertThat(Command.of("").getNeedStdoutInResult())
        .isEqualTo(Command.DEFAULT_NEED_STDOUT_IN_RESULT);
    assertThat(Command.of("").needStdoutInResult(false).getNeedStdoutInResult()).isFalse();
    assertThat(Command.of("").getNeedStderrInResult())
        .isEqualTo(Command.DEFAULT_NEED_STDERR_IN_RESULT);
    assertThat(Command.of("").needStderrInResult(false).getNeedStderrInResult()).isFalse();
    assertThat(Command.of("").getShowFullResultInException())
        .isEqualTo(Command.DEFAULT_SHOW_FULL_RESULT_IN_EXCEPTION);
    assertThat(Command.of("").showFullResultInException(true).getShowFullResultInException())
        .isTrue();
  }

  @Test
  public void testToString() {
    assertThat(Command.of("e", "a1", "a2").toString()).isEqualTo("e a1 a2");
  }

  @Test
  public void testEquals() {
    Command command1 = Command.of("e", "a1", "a2").timeout(Duration.ofSeconds(1L));
    Command command2 = Command.of("e", "a1", "a2").timeout(Duration.ofSeconds(2L));
    assertThat(command1).isEqualTo(command2);
    assertThat(command1.hashCode()).isEqualTo(command2.hashCode());
  }
}
