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

package com.google.devtools.mobileharness.shared.util.signal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.shared.util.command.LineCallback.does;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.junit.rule.MonitoredStringBuilders;
import com.google.devtools.mobileharness.shared.util.junit.rule.PrintTestName;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.KillSignal;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.util.truth.Correspondences;
import java.time.Duration;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SignalsTest {

  @Rule public final PrintTestName printTestName = new PrintTestName();
  @Rule public final MonitoredStringBuilders strings = new MonitoredStringBuilders();

  private static final String FAKE_PROGRAM_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/shared/util/signal/fake_program_deploy.jar");

  private final CommandExecutor commandExecutor = new CommandExecutor();
  private final SystemUtil systemUtil = new SystemUtil();

  private CommandProcess commandProcess;

  @After
  public void tearDown() {
    if (commandProcess != null && commandProcess.isAlive()) {
      commandProcess.kill();
    }
  }

  @Test
  public void monitorKnownSignals() throws Exception {
    StringBuilder stdout = strings.getOrCreate("fake program stdout");
    StringBuilder stderr = strings.getOrCreate("fake program stderr");

    commandProcess =
        commandExecutor.start(
            Command.of(
                    systemUtil
                        .getJavaCommandCreator()
                        .createJavaCommand(
                            FAKE_PROGRAM_PATH, ImmutableList.of("true"), ImmutableList.of()))
                .successExitCodes(130)
                .redirectStderr(false)
                .showFullResultInException(true)
                .timeout(Duration.ofSeconds(10L))
                .onStdout(does(line -> stdout.append(line).append('\n')))
                .onStderr(does(line -> stderr.append(line).append('\n'))));

    Sleeper.defaultSleeper().sleep(Duration.ofSeconds(3L));

    commandProcess.killWithSignal(KillSignal.SIGINT.value());

    Sleeper.defaultSleeper().sleep(Duration.ofSeconds(1L));

    assertThat(commandProcess.isAlive()).isFalse();
    CommandResult commandResult = commandProcess.await();
    assertThat(commandResult.exitCode()).isEqualTo(130);
    assertThat(commandResult.stdout()).isEqualTo("");
    assertThat(Splitter.on('\n').splitToList(commandResult.stderr()))
        .comparingElementsUsing(Correspondences.contains())
        .contains("Receive signal [SIGINT], terminating...");
  }

  @Test
  public void monitorKnownSignals_disabled() throws Exception {
    StringBuilder stdout = strings.getOrCreate("fake program stdout");
    StringBuilder stderr = strings.getOrCreate("fake program stderr");

    commandProcess =
        commandExecutor.start(
            Command.of(
                    systemUtil
                        .getJavaCommandCreator()
                        .createJavaCommand(
                            FAKE_PROGRAM_PATH, ImmutableList.of("false"), ImmutableList.of()))
                .successExitCodes(130)
                .redirectStderr(false)
                .showFullResultInException(true)
                .timeout(Duration.ofSeconds(10L))
                .onStdout(does(line -> stdout.append(line).append('\n')))
                .onStderr(does(line -> stderr.append(line).append('\n'))));

    Sleeper.defaultSleeper().sleep(Duration.ofSeconds(1L));

    commandProcess.killWithSignal(KillSignal.SIGINT.value());

    Sleeper.defaultSleeper().sleep(Duration.ofSeconds(1L));

    assertThat(commandProcess.isAlive()).isFalse();
    CommandResult commandResult = commandProcess.await();
    assertThat(commandResult.exitCode()).isEqualTo(130);
    assertThat(commandResult.stdout()).isEqualTo("");
    assertThat(commandResult.stderr()).doesNotContain("Receive signal");
  }
}
