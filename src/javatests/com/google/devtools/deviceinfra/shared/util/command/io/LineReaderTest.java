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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.shared.util.command.io.LineReader.LineHandler;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class LineReaderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private LineHandler lineHandler;

  @Test
  public void start() throws IOException {
    LineReader lineReader = new LineReader();
    try (OutputStreamWriter writer =
        new OutputStreamWriter(lineReader.openStream(), StandardCharsets.UTF_8)) {
      writer.write("A\nB\nC\nD");
    }

    lineReader.start(lineHandler);

    verify(lineHandler).handleLine("A", "\n");
    verify(lineHandler).handleLine("B", "\n");
    verify(lineHandler).handleLine("C", "\n");
    verify(lineHandler).handleLine("D", "");
    verify(lineHandler).onSourceClosed();
  }

  @Test
  public void start_stopReceiving() throws IOException {
    when(lineHandler.handleLine("B", "\n")).thenReturn(true);

    LineReader lineReader = new LineReader();
    try (OutputStreamWriter writer =
        new OutputStreamWriter(lineReader.openStream(), StandardCharsets.UTF_8)) {
      writer.write("A\nB\nC\nD");
    }

    lineReader.start(lineHandler);

    verify(lineHandler).handleLine("A", "\n");
    verify(lineHandler).handleLine("B", "\n");
    verify(lineHandler, never()).handleLine("C", "\n");
  }
}
