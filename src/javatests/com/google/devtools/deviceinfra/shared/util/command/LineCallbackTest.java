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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.shared.util.command.LineCallback.Response;
import java.io.IOException;
import java.io.Writer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class LineCallbackTest {

  private static final String LINE = "Line";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Consumer<String> lineConsumer;
  @Mock private Writer writer;
  @Mock private Function<String, Optional<String>> answerFunction;
  @Mock private Predicate<String> stopPredicate;

  @Test
  public void does() throws LineCallbackException {
    assertThat(LineCallback.does(lineConsumer).onLine(LINE)).isEqualTo(Response.empty());

    verify(lineConsumer).accept(LINE);
  }

  @Test
  public void writeTo() throws LineCallbackException, IOException {
    assertThat(LineCallback.writeTo(writer).onLine(LINE)).isEqualTo(Response.empty());

    verify(writer).write(LINE + "\n");
  }

  @Test
  public void writeTo_ioException() throws IOException {
    doThrow(IOException.class).when(writer).write(anyString());

    LineCallbackException exception =
        assertThrows(LineCallbackException.class, () -> LineCallback.writeTo(writer).onLine(LINE));

    assertThat(exception.getKillCommand()).isFalse();
    assertThat(exception.getStopReadingOutput()).isTrue();
  }

  @Test
  public void answer() throws LineCallbackException {
    when(answerFunction.apply(LINE)).thenReturn(Optional.of("Answer")).thenReturn(Optional.empty());

    assertThat(LineCallback.answer(answerFunction).onLine(LINE))
        .isEqualTo(Response.answer("Answer"));
    assertThat(LineCallback.answer(answerFunction).onLine(LINE)).isEqualTo(Response.empty());
  }

  @Test
  public void answerLn() throws LineCallbackException {
    when(answerFunction.apply(LINE)).thenReturn(Optional.of("Answer")).thenReturn(Optional.empty());

    assertThat(LineCallback.answerLn(answerFunction).onLine(LINE))
        .isEqualTo(Response.answerLn("Answer"));
    assertThat(LineCallback.answerLn(answerFunction).onLine(LINE)).isEqualTo(Response.empty());
  }

  @Test
  public void stopWhen() throws LineCallbackException {
    when(stopPredicate.test(LINE)).thenReturn(true);

    assertThat(LineCallback.stopWhen(stopPredicate).onLine(LINE)).isEqualTo(Response.stop(true));
  }

  @Test
  public void response() {
    assertThat(Response.empty()).isEqualTo(Response.notStop());
    assertThat(Response.stop().getStop()).isTrue();
    assertThat(Response.notStop().getStop()).isFalse();
    assertThat(Response.stop(true).getStop()).isTrue();
    assertThat(Response.answer("Answer").getAnswer()).hasValue("Answer");
    assertThat(Response.answerLn("Answer").getAnswer()).hasValue("Answer\n");
    assertThat(Response.of(true, "Answer").getStop()).isTrue();
    assertThat(Response.of(true, "Answer").getAnswer()).hasValue("Answer");
    assertThat(Response.ofLn(true, "Answer").getAnswer()).hasValue("Answer\n");
    assertThat(Response.of(true, null).getAnswer()).isEmpty();
    assertThat(Response.ofLn(true, null).getAnswer()).isEmpty();
    assertThat(Response.answer("Answer").withStop().getStop()).isTrue();
    assertThat(Response.answer("Answer").withNotStop().getStop()).isFalse();
    assertThat(Response.answer("Answer").withStop(true).getStop()).isTrue();
    assertThat(Response.stop().withAnswer("Answer").getAnswer()).hasValue("Answer");
    assertThat(Response.stop().withAnswerLn("Answer").getAnswer()).hasValue("Answer\n");
  }
}
