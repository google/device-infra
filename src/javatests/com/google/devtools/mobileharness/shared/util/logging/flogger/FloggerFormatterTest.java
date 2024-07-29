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

package com.google.devtools.mobileharness.shared.util.logging.flogger;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FloggerFormatterTest {

  private static final String FAKE_PROGRAM_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/shared/util/"
              + "logging/flogger/fake_program_deploy.jar");

  private final CommandExecutor commandExecutor = new CommandExecutor();
  private final SystemUtil systemUtil = new SystemUtil();

  @Test
  public void initialize_withContext() throws Exception {
    String output =
        commandExecutor.run(
            Command.of(
                    systemUtil
                        .getJavaCommandCreator()
                        .createJavaCommand(
                            FAKE_PROGRAM_PATH, ImmutableList.of(), ImmutableList.of()))
                .extraEnv(FloggerFormatterConstants.ENV_VAR_FLOGGER_WITHOUT_CONTEXT, "false")
                .showFullResultInException(true));

    assertThat(output).endsWith("FakeProgram [main] Foo [CONTEXT importance=\"IMPORTANT\" ]\n");
  }

  @Test
  public void initialize_withoutContext() throws Exception {
    String output =
        commandExecutor.run(
            Command.of(
                    systemUtil
                        .getJavaCommandCreator()
                        .createJavaCommand(
                            FAKE_PROGRAM_PATH, ImmutableList.of(), ImmutableList.of()))
                .extraEnv(FloggerFormatterConstants.ENV_VAR_FLOGGER_WITHOUT_CONTEXT, "true")
                .showFullResultInException(true));

    assertThat(output).endsWith("FakeProgram [main] Foo\n");
  }
}
