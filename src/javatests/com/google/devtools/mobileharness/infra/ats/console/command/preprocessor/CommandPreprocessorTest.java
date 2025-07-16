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
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.SystemProperties;
import com.google.devtools.mobileharness.infra.ats.console.command.alias.AliasManager;
import com.google.devtools.mobileharness.infra.ats.console.command.preprocessor.CommandFileParser.CommandLine;
import com.google.devtools.mobileharness.infra.ats.console.command.preprocessor.CommandPreprocessor.PreprocessingResult;
import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.io.File;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class CommandPreprocessorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Bind @Mock private CommandFileParser commandFileParser;
  @Inject private AliasManager aliasManager;
  @Inject private CommandPreprocessor preprocessor;

  @Before
  public void setUp() throws Exception {
    String xtsRoot = "xts_root_dir";
    String xtsRootDirPath = tmpFolder.newFolder(xtsRoot).toString();
    tmpFolder.newFolder(xtsRoot, "android-cts/tools");
    ImmutableMap<String, String> systemProperties = ImmutableMap.of("XTS_ROOT", xtsRootDirPath);

    Guice.createInjector(
            BoundFieldModule.of(this),
            binder ->
                binder
                    .bind(new TypeLiteral<ImmutableMap<String, String>>() {})
                    .annotatedWith(SystemProperties.class)
                    .toInstance(systemProperties))
        .injectMembers(this);
  }

  @Test
  public void preprocess_runCommand() {
    ImmutableList<String> tokens = ImmutableList.of("run", "command", "cts", "-m", "CtsTest");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands())
        .hasValue(ImmutableList.of(ImmutableList.of("run", "cts", "-m", "CtsTest")));
    assertThat(result.errorMessage()).isEmpty();
  }

  @Test
  public void preprocess_runCommand_withAlias() {
    aliasManager.addAlias("usb_tests", "cts -m CtsUsbTests");
    ImmutableList<String> tokens = ImmutableList.of("run", "command", "usb_tests");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands())
        .hasValue(ImmutableList.of(ImmutableList.of("run", "cts", "-m", "CtsUsbTests")));
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
  public void preprocess_runCommandAndExit_withAlias() {
    aliasManager.addAlias("usb_tests", "cts -m CtsUsbTests");
    ImmutableList<String> tokens = ImmutableList.of("run", "commandAndExit", "usb_tests");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands())
        .hasValue(
            ImmutableList.of(
                ImmutableList.of("run", "cts", "-m", "CtsUsbTests"),
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

  @Test
  public void preprocess_runAlias() {
    aliasManager.addAlias("usb_tests", "cts -m CtsUsbTests");
    ImmutableList<String> tokens = ImmutableList.of("run", "usb_tests");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands())
        .hasValue(ImmutableList.of(ImmutableList.of("run", "cts", "-m", "CtsUsbTests")));
    assertThat(result.errorMessage()).isEmpty();
  }

  @Test
  public void preprocess_runAlias_withExtraArgs() {
    aliasManager.addAlias("usb_tests", "cts -m CtsUsbTests");
    ImmutableList<String> tokens = ImmutableList.of("run", "usb_tests", "--shard-count", "2");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands())
        .hasValue(
            ImmutableList.of(
                ImmutableList.of("run", "cts", "-m", "CtsUsbTests", "--shard-count", "2")));
    assertThat(result.errorMessage()).isEmpty();
  }

  @Test
  public void preprocess_runAlias_aliasNotFound() {
    ImmutableList<String> tokens = ImmutableList.of("run", "nonexistent_alias");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands()).isEmpty();
    assertThat(result.errorMessage()).isEmpty();
  }

  @Test
  public void preprocess_runAlias_invalidAliasCommand() {
    aliasManager.addAlias("invalid_alias", "cts -m \"CtsUsbTests"); // Unterminated quote
    ImmutableList<String> tokens = ImmutableList.of("run", "invalid_alias");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands()).isEmpty();
    assertThat(result.errorMessage())
        .hasValue("Failed to tokenize alias 'cts -m \"CtsUsbTests': unterminated quotation.");
  }

  @Test
  public void preprocess_multipleAliases() {
    aliasManager.addAlias("usb_module", "-m CtsUsbTests");
    aliasManager.addAlias("with_sharding", "--enable-token-sharding --shard-count 2");
    ImmutableList<String> tokens = ImmutableList.of("run", "cts", "usb_module", "with_sharding");

    PreprocessingResult result = preprocessor.preprocess(tokens);

    assertThat(result.modifiedCommands())
        .hasValue(
            ImmutableList.of(
                ImmutableList.of(
                    "run",
                    "cts",
                    "-m",
                    "CtsUsbTests",
                    "--enable-token-sharding",
                    "--shard-count",
                    "2")));
    assertThat(result.errorMessage()).isEmpty();
  }
}
