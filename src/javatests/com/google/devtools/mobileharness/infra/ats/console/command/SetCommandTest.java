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

package com.google.devtools.mobileharness.infra.ats.console.command;

import static com.google.common.truth.OptionalSubject.optionals;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer.ServerEnvironment;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.GuiceFactory;
import com.google.devtools.mobileharness.shared.constant.inject.Annotations.SystemProperties;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.nio.file.Path;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.jline.reader.LineReader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import picocli.CommandLine;

@RunWith(JUnit4.class)
public final class SetCommandTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @SystemProperties
  private static final ImmutableMap<String, String> SYSTEM_PROPERTIES = ImmutableMap.of();

  @Mock @Bind @Nullable @ConsoleLineReader private LineReader lineReader;

  @Inject private ConsoleInfo consoleInfo;
  private CommandLine commandLine;

  @Before
  public void setUp() {
    Injector injector = Guice.createInjector(BoundFieldModule.of(this));
    injector.injectMembers(this);
    commandLine =
        new CommandLine(
            RootCommand.class,
            new GuiceFactory(
                Guice.createInjector(
                    BoundFieldModule.of(this),
                    new ConsoleCommandTestModule(
                        consoleInfo,
                        ServerEnvironment.of(
                            Path.of("/fake_server_binary"),
                            Path.of("/fake_java_binary"),
                            Path.of("/fake_working_dir"))))));
  }

  @Test
  public void setPythonPackageIndexUrl_success() {
    String newPythonPackageIndexUrl = "https://pypi.tuna.tsinghua.edu.cn/simple";

    int exitCode = commandLine.execute("set", "python-package-index-url", newPythonPackageIndexUrl);

    assertThat(exitCode).isEqualTo(0);
    assertThat(consoleInfo.getPythonPackageIndexUrl().orElse(""))
        .isEqualTo(newPythonPackageIndexUrl);
  }

  @Test
  public void setPythonPackageIndexUrl_skipIfGivenUrlIsWhitespace() {
    String newPythonPackageIndexUrl = "  ";

    int exitCode = commandLine.execute("set", "python-package-index-url", newPythonPackageIndexUrl);

    assertThat(exitCode).isEqualTo(0);
    assertWithMessage("consoleInfo.getPythonPackageIndexUrl()")
        .about(optionals())
        .that(consoleInfo.getPythonPackageIndexUrl())
        .isEmpty();
  }
}
