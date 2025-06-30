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

package com.google.devtools.mobileharness.infra.ats.console.command.preprocessor;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.ats.console.command.preprocessor.CommandFileParser.CommandLine;
import com.google.devtools.mobileharness.infra.ats.console.command.preprocessor.CommandPreprocessor.PreprocessingResult;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class CommandPreprocessorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private CommandFileParser commandFileParser;
  @InjectMocks private CommandPreprocessor preprocessor;

  @Test
  public void preprocess_runCommand() {
    ImmutableList<String> tokens = ImmutableList.of("run", "command", "cts", "-m", "CtsTest");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands())
        .hasValue(ImmutableList.of(ImmutableList.of("run", "cts", "-m", "CtsTest")));
    assertThat(result.errorMessage()).isEmpty();
  }

  @Test
  public void preprocess_runCommandAndExit() {
    ImmutableList<String> tokens =
        ImmutableList.of("run", "commandAndExit", "cts", "-m", "CtsTest");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands())
        .hasValue(
            ImmutableList.of(
                ImmutableList.of("run", "cts", "-m", "CtsTest"),
                ImmutableList.of("exit", "-c", "-s")));
    assertThat(result.errorMessage()).isEmpty();
  }

  @Test
  public void preprocess_runCmdfile() throws Exception {
    when(commandFileParser.parseFile(any(File.class)))
        .thenReturn(
            ImmutableList.of(
                new CommandLine(ImmutableList.of("cts", "-m", "CtsTest1"), null, 0),
                new CommandLine(ImmutableList.of("cts", "-m", "CtsTest2"), null, 0)));
    ImmutableList<String> tokens = ImmutableList.of("run", "cmdfile", "test.xml");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands())
        .hasValue(
            ImmutableList.of(
                ImmutableList.of("run", "cts", "-m", "CtsTest1"),
                ImmutableList.of("run", "cts", "-m", "CtsTest2")));
    assertThat(result.errorMessage()).isEmpty();
  }

  @Test
  public void preprocess_runCmdfileAndExit() throws Exception {
    when(commandFileParser.parseFile(any(File.class)))
        .thenReturn(
            ImmutableList.of(
                new CommandLine(ImmutableList.of("cts", "-m", "CtsTest1"), null, 0),
                new CommandLine(ImmutableList.of("cts", "-m", "CtsTest2"), null, 0)));
    ImmutableList<String> tokens = ImmutableList.of("run", "cmdfileAndExit", "test.xml");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands())
        .hasValue(
            ImmutableList.of(
                ImmutableList.of("run", "cts", "-m", "CtsTest1"),
                ImmutableList.of("run", "cts", "-m", "CtsTest2"),
                ImmutableList.of("exit", "-c", "-s")));
    assertThat(result.errorMessage()).isEmpty();
  }

  @Test
  public void preprocess_runCmdfile_noPath() {
    ImmutableList<String> tokens = ImmutableList.of("run", "cmdfile");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands()).isEmpty();
    assertThat(result.errorMessage()).hasValue("Cmdfile path is not specified");
  }

  @Test
  public void preprocess_unsupportedCommand() {
    ImmutableList<String> tokens = ImmutableList.of("list", "devices");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands()).isEmpty();
    assertThat(result.errorMessage()).isEmpty();
  }
}
