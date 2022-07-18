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

package com.google.devtools.deviceinfra.shared.util.command.io;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class LineCollectorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Predicate<String> lineConsumer;

  private LineReader lineReader;

  @Before
  public void setUp() throws IOException {
    lineReader = new LineReader();
    try (OutputStreamWriter writer =
        new OutputStreamWriter(lineReader.openStream(), StandardCharsets.UTF_8)) {
      writer.write("A\nB\nC\nD");
    }
  }

  @Test
  public void handleLine() throws IOException, InterruptedException {
    LineCollector lineCollector = new LineCollector(1 /* numSource */, true /* needAllLines */);
    lineCollector.setLineConsumer(lineConsumer);

    lineReader.start(lineCollector);

    verify(lineConsumer).test("A");
    verify(lineConsumer).test("B");
    verify(lineConsumer).test("C");
    verify(lineConsumer).test("D");
    assertThat(lineCollector.waitForAllLines()).isEqualTo("A\nB\nC\nD");
  }

  @Test
  public void handleLine_multiSources() throws IOException, InterruptedException {
    LineCollector lineCollector = new LineCollector(2 /* numSource */, true /* needAllLines */);
    lineCollector.setLineConsumer(lineConsumer);

    LineReader lineReader1 = new LineReader();
    try (OutputStreamWriter writer1 =
        new OutputStreamWriter(lineReader1.openStream(), StandardCharsets.UTF_8)) {
      writer1.write("A\nB\n");
    }
    lineReader1.start(lineCollector);

    LineReader lineReader2 = new LineReader();
    try (OutputStreamWriter writer2 =
        new OutputStreamWriter(lineReader2.openStream(), StandardCharsets.UTF_8)) {
      writer2.write("C\nD\n");
    }
    lineReader2.start(lineCollector);

    verify(lineConsumer).test("A");
    verify(lineConsumer).test("B");
    verify(lineConsumer).test("C");
    verify(lineConsumer).test("D");
    assertThat(lineCollector.waitForAllLines()).isEqualTo("A\nB\nC\nD\n");
  }

  @Test
  public void handleLine_stopConsumingLines() throws IOException, InterruptedException {
    when(lineConsumer.test("B")).thenReturn(true);

    LineCollector lineCollector = new LineCollector(1 /* numSource */, true /* needAllLines */);
    lineCollector.setLineConsumer(lineConsumer);

    lineReader.start(lineCollector);

    verify(lineConsumer).test("A");
    verify(lineConsumer).test("B");
    verify(lineConsumer, never()).test("C");
    verify(lineConsumer, never()).test("D");
    assertThat(lineCollector.waitForAllLines(Duration.ofSeconds(1L))).isEqualTo("A\nB\nC\nD");
  }

  @Test
  public void handleLine_notNeedAllLines() throws IOException, InterruptedException {
    LineCollector lineCollector = new LineCollector(1 /* numSource */, false /* needAllLines */);
    lineCollector.setLineConsumer(lineConsumer);

    lineReader.start(lineCollector);

    verify(lineConsumer).test("A");
    verify(lineConsumer).test("B");
    verify(lineConsumer).test("C");
    verify(lineConsumer).test("D");
    assertThat(lineCollector.waitForAllLines()).isEmpty();
  }
}
