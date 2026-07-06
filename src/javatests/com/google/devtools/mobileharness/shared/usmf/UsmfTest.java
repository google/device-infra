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

package com.google.devtools.mobileharness.shared.usmf;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.usmf.UsmfBinary.CommandInvocation;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Standalone unit tests verifying USMF framework core behaviors under the Starlark model. */
@RunWith(JUnit4.class)
public final class UsmfTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  private Path tempDir;
  private CommandExecutor executor;
  private final InstantSource clock = InstantSource.system();

  @Before
  public void setUp() {
    tempDir = tmpFolder.getRoot().toPath();
    executor = new CommandExecutor();
  }

  @Test
  public void exactArgsMatching_executesMockRedirectionSuccessfully() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .setRules(
                """
                def rule(ctx):
                    if ctx.args == ["say", "hello", "world"]:
                        return Result(stdout="Hello, Earth!\\n")
                    return None
                usmf_rules = [rule]
                """)
            .buildAndDeploy();

    Instant start = clock.instant();
    String stdout = executor.run(Command.of(mockCmd.getPath(), "say", "hello", "world"));
    Duration duration = Duration.between(start, clock.instant());
    logger.atInfo().log("Executed mock command say hello world in %d ms", duration.toMillis());
    assertThat(stdout).isEqualTo("Hello, Earth!\n");

    ImmutableList<CommandInvocation> invocations = mockCmd.readCommandInvocations();
    assertThat(invocations).hasSize(1);
    CommandInvocation invocation = invocations.get(0);
    assertThat(invocation.getArgs()).containsExactly("say", "hello", "world");
    assertThat(invocation.getRuleName()).hasValue("rule");
  }

  @Test
  public void regexCommandMatching_executesRedirectionSuccessfully() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .setRules(
                """
                def rule(ctx):
                    if re_search(r"pull\\s+/sdcard/file", ctx.command):
                        return Result(stdout="Success Pull\\n")
                    return None
                usmf_rules = [rule]
                """)
            .buildAndDeploy();

    String stdout = executor.run(Command.of(mockCmd.getPath(), "pull", "/sdcard/file"));
    assertThat(stdout).isEqualTo("Success Pull\n");
  }

  @Test
  public void commandDelaySimulation_stallsExecutionCorrectly() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .setRules(
                """
                def rule(ctx):
                    if ctx.args == ["slow"]:
                        return Result(stdout="Slow Response\\n", sleep_ms=350)
                    return None
                usmf_rules = [rule]
                """)
            .buildAndDeploy();

    long startTime = clock.instant().toEpochMilli();
    String stdout = executor.run(Command.of(mockCmd.getPath(), "slow"));
    long elapsedMs = clock.instant().toEpochMilli() - startTime;

    assertThat(stdout).isEqualTo("Slow Response\n");
    assertThat(elapsedMs).isAtLeast(300);
  }

  @Test
  public void deployWithZeroRules_triggersFallbackSuccessfully() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").buildAndDeploy();

    String stdout = executor.run(Command.of(mockCmd.getPath(), "any", "unmocked", "command"));
    assertThat(stdout).isEmpty();
  }

  @Test
  public void regexCapturesDynamicInterpolation_interpolatesStdoutStateAndSideEffects()
      throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .setRules(
                """
                def rule(ctx):
                    m = re_search(r"install\\s+(?P<pkg>[A-Za-z0-9_\\.]+)", ctx.command)
                    if not m:
                        return None
                    pkg = m["pkg"]
                    installed = ctx.state.get("installed_packages", [])
                    if pkg not in installed:
                        installed.append(pkg)
                    ctx.state["installed_packages"] = installed
                    manifest_path = ctx.state["manifest_dir"] + "/" + pkg + ".txt"
                    return Result(
                        stdout="Installed " + pkg + "\\n",
                        side_effects=[
                            WriteFile(manifest_path, "Manifest for " + pkg)
                        ]
                    )
                usmf_rules = [rule]
                """)
            .buildAndDeploy();

    JsonObject initialState = new JsonObject();
    initialState.addProperty("manifest_dir", tempDir.toAbsolutePath().toString());
    mockCmd.writeState(initialState);

    String installStdout =
        executor.run(Command.of(mockCmd.getPath(), "install", "com.example.app"));
    assertThat(installStdout).isEqualTo("Installed com.example.app\n");

    Path manifestFile = tempDir.resolve("com.example.app.txt");
    assertThat(Files.exists(manifestFile)).isTrue();
    assertThat(Files.readString(manifestFile)).isEqualTo("Manifest for com.example.app");

    JsonObject state = mockCmd.readState();
    JsonArray installed = state.getAsJsonArray("installed_packages");
    assertThat(installed.size()).isEqualTo(1);
    assertThat(installed.get(0).getAsString()).isEqualTo("com.example.app");
  }

  @Test
  public void listRemove_mutatesStateSuccessfully() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .setRules(
                """
                def rule(ctx):
                    m = re_search(r"uninstall\\s+(?P<pkg>\\S+)", ctx.command)
                    if not m:
                        return None
                    pkg = m["pkg"]
                    if "installed_packages" in ctx.state:
                        if pkg in ctx.state["installed_packages"]:
                            ctx.state["installed_packages"].remove(pkg)
                    return Result(stdout="Success\\n")
                usmf_rules = [rule]
                """)
            .buildAndDeploy();

    JsonObject initialState =
        JsonParser.parseString(
                """
                {
                  "installed_packages": ["com.foo.app", "com.bar.app"]
                }
                """)
            .getAsJsonObject();
    mockCmd.writeState(initialState);

    String uninstallStdout =
        executor.run(Command.of(mockCmd.getPath(), "uninstall", "com.foo.app"));
    assertThat(uninstallStdout).isEqualTo("Success\n");

    JsonObject state = mockCmd.readState();
    JsonArray installed = state.getAsJsonArray("installed_packages");
    assertThat(installed.size()).isEqualTo(1);
    assertThat(installed.get(0).getAsString()).isEqualTo("com.bar.app");
  }

  @Test
  public void sideEffectCreateDirectory_createsDirectorySuccessfully() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .setRules(
                """
                def rule(ctx):
                    if ctx.args == ["mkdir"]:
                        return Result(stdout="Directory created\\n", side_effects=[CreateDir(ctx.state["dir_to_create"])])
                    return None
                usmf_rules = [rule]
                """)
            .buildAndDeploy();

    JsonObject initialState = new JsonObject();
    initialState.addProperty(
        "dir_to_create", tempDir.resolve("new_test_dir").toAbsolutePath().toString());
    mockCmd.writeState(initialState);

    String stdout = executor.run(Command.of(mockCmd.getPath(), "mkdir"));
    assertThat(stdout).isEqualTo("Directory created\n");

    Path createdDir = tempDir.resolve("new_test_dir");
    assertThat(Files.exists(createdDir)).isTrue();
    assertThat(Files.isDirectory(createdDir)).isTrue();
  }

  @Test
  public void concurrentStateMutations_acquiresLockAndPreservesAll() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .setRules(
                """
                def rule(ctx):
                    if "increment" in ctx.args:
                        count = ctx.state.get("counter", 0)
                        ctx.state["counter"] = count + 1
                        return Result(stdout="incremented\\n")
                    return None
                usmf_rules = [rule]
                """)
            .buildAndDeploy();

    JsonObject initialState = new JsonObject();
    initialState.addProperty("counter", 0);
    mockCmd.writeState(initialState);

    // Spawn 10 concurrent processes mutating the state.
    List<CommandProcess> processes = new ArrayList<>();
    for (int i = 0; i < 10; ++i) {
      processes.add(executor.start(Command.of(mockCmd.getPath(), "increment")));
    }
    for (CommandProcess p : processes) {
      p.await();
    }

    JsonObject state = mockCmd.readState();
    assertThat(state.get("counter").getAsInt()).isEqualTo(10);
  }

  @Test
  public void matchIndexAndNamedGroup_fallbackToNone() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .setRules(
                """
                def rule(ctx):
                    m = re_search(r"test\\s+(?P<val>[a-z]+)", ctx.command)
                    if not m:
                        return None
                    res = "0:%s, 1:%s, 2:%s, val:%s, missing:%s" % (
                        m[0],
                        m[1],
                        "None" if m[2] == None else m[2],
                        m["val"],
                        "None" if m["missing"] == None else m["missing"]
                    )
                    return Result(stdout=res)
                usmf_rules = [rule]
                """)
            .buildAndDeploy();

    String stdout = executor.run(Command.of(mockCmd.getPath(), "test", "xyz"));
    assertThat(stdout).isEqualTo("0:test xyz, 1:xyz, 2:None, val:xyz, missing:None");
  }

  @Test
  public void deploy_withExistingDirectory_throwsIllegalStateException() throws Exception {
    Path folder = tempDir.resolve("duplicate_sandbox");
    Files.createDirectories(folder);

    UsmfBinary.Builder builder =
        UsmfBinary.builder("mock_cmd", tempDir, "duplicate_sandbox")
            .setRules(
                """
                usmf_rules = []
                """);
    assertThrows(IllegalStateException.class, () -> builder.buildAndDeploy());
  }

  @Test
  public void unmatchedRule_rulesRollbackStateSuccessfully() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .setRules(
                """
                def rule1(ctx):
                    ctx.state["modified"] = "should_not_stay"
                    return None
                def rule2(ctx):
                    if ctx.args == ["check"]:
                        return Result(stdout="ok\\n")
                    return None
                usmf_rules = [rule1, rule2]
                """)
            .buildAndDeploy();

    mockCmd.writeState(new JsonObject());
    String stdout = executor.run(Command.of(mockCmd.getPath(), "check"));
    assertThat(stdout).isEqualTo("ok\n");

    JsonObject state = mockCmd.readState();
    assertThat(state.get("modified")).isNull();
  }

  @Test
  public void getCommandInvocations_logsRunningThenFinished() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .setRules(
                """
                def slow_rule(ctx):
                    if "slow" in ctx.args:
                        return Result(stdout="Done\\n", sleep_ms=500)
                    return None
                usmf_rules = [slow_rule]
                """)
            .buildAndDeploy();

    CommandProcess process = executor.start(Command.of(mockCmd.getPath(), "slow"));
    try {
      ImmutableList<CommandInvocation> invocations = ImmutableList.of();
      for (int i = 0; i < 600; i++) {
        invocations = mockCmd.readCommandInvocations();
        if (!invocations.isEmpty()) {
          break;
        }
        Sleeper.defaultSleeper().sleep(Duration.ofMillis(50));
      }
      assertThat(invocations).hasSize(1);
      CommandInvocation runningInvocation = invocations.get(0);
      assertThat(runningInvocation.getStatus()).isEqualTo(CommandInvocation.Status.RUNNING);
      assertThat(runningInvocation.getResult()).isEmpty();

      ImmutableList<CommandInvocation> finishedInvocations = ImmutableList.of();
      for (int i = 0; i < 600; i++) {
        finishedInvocations = mockCmd.readCommandInvocations();
        if (!finishedInvocations.isEmpty()
            && finishedInvocations.get(0).getStatus() == CommandInvocation.Status.FINISHED) {
          break;
        }
        Sleeper.defaultSleeper().sleep(Duration.ofMillis(50));
      }
      assertThat(finishedInvocations).hasSize(1);
      CommandInvocation finishedInvocation = finishedInvocations.get(0);
      assertThat(finishedInvocation.getStatus()).isEqualTo(CommandInvocation.Status.FINISHED);
      CommandInvocation.Result finishedResult = finishedInvocation.getResultNonEmpty();
      assertThat(finishedResult.getExitCode()).isEqualTo(0);
      assertThat(finishedResult.getStdout()).isEqualTo("Done\n");
      assertThat(finishedResult.getStderr()).isEmpty();
    } finally {
      process.kill();
    }
  }

  @Test
  public void nowMs_exposesCurrentTimeMs() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .setRules(
                """
                def rule(ctx):
                    if "time" in ctx.args:
                        # Return current time as stdout string
                        return Result(stdout=str(ctx.now_ms) + "\\n")
                    return None
                usmf_rules = [rule]
                """)
            .buildAndDeploy();

    long timeBeforeRun = clock.instant().toEpochMilli();
    String stdout = executor.run(Command.of(mockCmd.getPath(), "time"));
    long timeAfterRun = clock.instant().toEpochMilli();

    long reportedTime = Long.parseLong(stdout.trim());
    assertThat(reportedTime).isAtLeast(timeBeforeRun - 1000);
    assertThat(reportedTime).isAtMost(timeAfterRun + 1000);
  }

  @Test
  public void rand_generatesDoubleBetweenZeroAndOne() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .setRules(
                """
                def rule(ctx):
                    if "rand" in ctx.args:
                        return Result(stdout=str(rand()) + "\\n")
                    return None
                usmf_rules = [rule]
                """)
            .buildAndDeploy();

    String stdout1 = executor.run(Command.of(mockCmd.getPath(), "rand"));
    String stdout2 = executor.run(Command.of(mockCmd.getPath(), "rand"));

    double val1 = Double.parseDouble(stdout1.trim());
    double val2 = Double.parseDouble(stdout2.trim());

    assertThat(val1).isAtLeast(0.0);
    assertThat(val1).isLessThan(1.0);
    assertThat(val2).isAtLeast(0.0);
    assertThat(val2).isLessThan(1.0);
    assertThat(val1).isNotEqualTo(val2);
  }
}
