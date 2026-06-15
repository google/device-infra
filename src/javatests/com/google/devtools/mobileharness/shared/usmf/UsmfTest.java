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
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.usmf.UsmfBinary.CommandInvocation;
import com.google.devtools.mobileharness.shared.usmf.UsmfRule.BinaryStateCondition;
import com.google.devtools.mobileharness.shared.usmf.UsmfRule.BinaryStateMutation;
import com.google.devtools.mobileharness.shared.usmf.UsmfRule.CommandBehavior;
import com.google.devtools.mobileharness.shared.usmf.UsmfRule.CommandCondition;
import com.google.devtools.mobileharness.shared.usmf.UsmfRule.LocalFileSideEffect;
import com.google.devtools.mobileharness.shared.usmf.UsmfRule.RuleCondition;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Standalone unit tests verifying all USMF framework core behaviors under the clean structural POJO
 * model, utilizing builders and statically generated factories.
 */
@RunWith(JUnit4.class)
public final class UsmfTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  private Path tempDir;
  private CommandExecutor executor;
  private final Gson gson = new Gson();

  @Before
  public void setUp() {
    tempDir = tmpFolder.getRoot().toPath();
    executor = new CommandExecutor();
  }

  @Test
  public void exactArgsMatching_executesMockRedirectionSuccessfully() throws Exception {
    // 1. Declare exact command match condition using factory
    RuleCondition exactArgs = CommandCondition.exactMatch("say", "hello", "world");

    // 2. Behavior response using builder factory method 'builder'
    CommandBehavior behavior = CommandBehavior.builder("Hello, Earth!\n", "", 0).build();

    UsmfRule rule = UsmfRule.builder().addCondition(exactArgs).setBehavior(behavior).build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // 3. Execute redirected command and assert
    Instant start = Instant.now();
    String stdout = executor.run(Command.of(mockCmd.getPath(), "say", "hello", "world"));
    Duration duration = Duration.between(start, Instant.now());
    logger.atInfo().log("Executed mock command say hello world in %d ms", duration.toMillis());
    assertThat(stdout).isEqualTo("Hello, Earth!\n");
    assertThat(
            mockCmd.readCommandInvocations().stream()
                .anyMatch(
                    invocation ->
                        invocation
                            .getArgs()
                            .containsAll(ImmutableList.of("say", "hello", "world"))))
        .isTrue();
  }

  @Test
  public void regexCommandMatching_executesRedirectionSuccessfully() throws Exception {
    // 1. Declare command regex condition using factory
    RuleCondition regexCond = CommandCondition.regexMatch(".*pull\\s+/sdcard/file.*");

    CommandBehavior behavior = CommandBehavior.builder("Success Pull\n", "", 0).build();

    UsmfRule rule = UsmfRule.builder().addCondition(regexCond).setBehavior(behavior).build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    String stdout = executor.run(Command.of(mockCmd.getPath(), "pull", "/sdcard/file"));
    assertThat(stdout).isEqualTo("Success Pull\n");
  }

  @Test
  public void commandResponseReuse_appliesExpectationCorrectly() throws Exception {
    // Share single behavior across different command rules
    CommandBehavior reusedBehavior = CommandBehavior.builder("Unified Response\n", "", 0).build();

    RuleCondition cond1 = CommandCondition.exactMatch("check", "a");
    RuleCondition cond2 = CommandCondition.exactMatch("check", "b");

    UsmfRule rule1 = UsmfRule.builder().addCondition(cond1).setBehavior(reusedBehavior).build();
    UsmfRule rule2 = UsmfRule.builder().addCondition(cond2).setBehavior(reusedBehavior).build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .addRule(rule1)
            .addRule(rule2)
            .buildAndDeploy();

    assertThat(executor.run(Command.of(mockCmd.getPath(), "check", "a")))
        .isEqualTo("Unified Response\n");
    assertThat(executor.run(Command.of(mockCmd.getPath(), "check", "b")))
        .isEqualTo("Unified Response\n");
  }

  @Test
  public void commandDelaySimulation_stallsExecutionCorrectly() throws Exception {
    RuleCondition slowCond = CommandCondition.exactMatch("slow", "query");

    CommandBehavior delayedBehavior =
        CommandBehavior.builder("Slow Response\n", "", 0).sleep(Duration.ofMillis(350)).build();

    UsmfRule rule = UsmfRule.builder().addCondition(slowCond).setBehavior(delayedBehavior).build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    long startTime = InstantSource.system().instant().toEpochMilli();
    String stdout = executor.run(Command.of(mockCmd.getPath(), "slow", "query"));
    long elapsedMs = Instant.now().minusMillis(startTime).toEpochMilli();

    assertThat(stdout).isEqualTo("Slow Response\n");
    assertThat(elapsedMs).isAtLeast(300);
  }

  @Test
  public void unmatchedCommand_triggersFallbackRule() throws Exception {
    CommandBehavior forbiddenBehavior =
        CommandBehavior.builder("", "Forbidden Command!", 12).build();

    UsmfRule rule = UsmfRule.builder().setBehavior(forbiddenBehavior).build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    Command command = Command.of(mockCmd.getPath(), "unmocked");
    Exception exception = assertThrows(Exception.class, () -> executor.run(command));

    assertThat(exception).hasMessageThat().contains("Forbidden Command!");
    assertThat(exception).hasMessageThat().contains("exit_code=12");
  }

  @Test
  public void statefulConditionAndMutation_transitesKVStateSuccessfully() throws Exception {
    // 1. Rule A: 'install' sets state and increments installment count
    RuleCondition installCond = CommandCondition.regexMatch(".*install.*target.apk.*");

    BinaryStateMutation setInstalled = BinaryStateMutation.stateNode("#S['installed']").set(true);
    BinaryStateMutation incrCount = BinaryStateMutation.stateNode("#S['count']").plus(1);

    CommandBehavior installBehavior =
        CommandBehavior.builder("Install Complete\n", "", 0)
            .addStateMutation(setInstalled)
            .addStateMutation(incrCount)
            .build();

    UsmfRule ruleInstall =
        UsmfRule.builder().addCondition(installCond).setBehavior(installBehavior).build();

    // 2. Rule B: 'query' only matches when 'installed' condition is exactly true
    RuleCondition queryCommandCond = CommandCondition.exactMatch("query");
    RuleCondition queryStateCond = BinaryStateCondition.stateNode("#S['installed']").equalTo(true);

    CommandBehavior queryBehavior = CommandBehavior.builder("App is Active\n", "", 0).build();

    UsmfRule ruleQuery =
        UsmfRule.builder()
            .addCondition(queryCommandCond)
            .addCondition(queryStateCond)
            .setBehavior(queryBehavior)
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .addRule(ruleQuery)
            .addRule(ruleInstall)
            .buildAndDeploy();

    // Test Case Part 1: Before install is invoked, query state check does not match (returns
    // fallback empty)
    String preInstallStdout = executor.run(Command.of(mockCmd.getPath(), "query"));
    assertThat(preInstallStdout).isEmpty();

    // Test Case Part 2: Execute install command to mutate state
    String installStdout = executor.run(Command.of(mockCmd.getPath(), "install", "target.apk"));
    assertThat(installStdout).isEqualTo("Install Complete\n");

    // Test Case Part 3: Assert mutated state directly on Java control API
    JsonObject state = mockCmd.readStateJson();
    assertThat(state.get("installed").getAsBoolean()).isTrue();
    assertThat(state.get("count").getAsInt()).isEqualTo(1);

    // Test Case Part 4: Now query command matches as state is transited successfully!
    String postInstallStdout = executor.run(Command.of(mockCmd.getPath(), "query"));
    assertThat(postInstallStdout).isEqualTo("App is Active\n");
  }

  @Test
  public void prefixArgsMatching_executesMockRedirectionSuccessfully() throws Exception {
    // Match any command that starts with "shell", "pm"
    RuleCondition prefixCond = CommandCondition.prefixMatch("shell", "pm");
    CommandBehavior behavior = CommandBehavior.builder("Prefix Match Success\n", "", 0).build();

    UsmfRule rule = UsmfRule.builder().addCondition(prefixCond).setBehavior(behavior).build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // 1. Matches because the CLI command starts with "shell", "pm"
    String stdoutPassed =
        executor.run(Command.of(mockCmd.getPath(), "shell", "pm", "list", "packages"));
    assertThat(stdoutPassed).isEqualTo("Prefix Match Success\n");

    // 2. Does not match because the CLI command doesn't start with "shell", "pm"
    String stdoutFailed = executor.run(Command.of(mockCmd.getPath(), "shell", "getprop"));
    assertThat(stdoutFailed).isEmpty();
  }

  @Test
  public void regexCapturesDynamicInterpolation_interpolatesStdoutStateAndSideEffects()
      throws Exception {
    // 1. Install command matches "install <package>" and extracts pkg named group
    RuleCondition installCond =
        CommandCondition.regexMatch(".*install\\s+(?P<pkg>[A-Za-z0-9_\\.]+).*");

    // 2. Behavior outputs dynamically with ${pkg}, mutates state list, and creates manifest side
    // effect
    CommandBehavior installBehavior =
        CommandBehavior.builder("Installed ${#C['pkg']}\n", "", 0)
            .addStateMutation(
                BinaryStateMutation.stateNode("#S['installed_packages']").addToList("${#C['pkg']}"))
            .addSideEffect(
                LocalFileSideEffect.createFile(
                    tempDir.toAbsolutePath() + "/manifests/${#C['pkg']}.txt",
                    "Manifest for ${#C['pkg']}"))
            .build();

    UsmfRule installRule =
        UsmfRule.builder().addCondition(installCond).setBehavior(installBehavior).build();

    // 3. Query command checks if com.example.app is active, only if it exists in the state
    RuleCondition queryCond = CommandCondition.exactMatch("query", "com.example.app");
    RuleCondition stateContainsCond =
        BinaryStateCondition.stateNode("#S['installed_packages']").contains("com.example.app");
    CommandBehavior queryBehavior =
        CommandBehavior.builder("com.example.app is installed\n", "", 0).build();

    UsmfRule queryRule =
        UsmfRule.builder()
            .addCondition(queryCond)
            .addCondition(stateContainsCond)
            .setBehavior(queryBehavior)
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .addRule(queryRule)
            .addRule(installRule)
            .buildAndDeploy();

    // Step A: Pre-install query - should not match active (returns empty / fallback)
    String preInstallStdout =
        executor.run(Command.of(mockCmd.getPath(), "query", "com.example.app"));
    assertThat(preInstallStdout).isEmpty();

    // Step B: Trigger install command with com.example.app
    String installStdout =
        executor.run(Command.of(mockCmd.getPath(), "install", "com.example.app"));
    assertThat(installStdout).isEqualTo("Installed com.example.app\n");

    // Step C: Verify side effect file exists and content is interpolated
    Path manifestFile = tempDir.resolve("manifests/com.example.app.txt");
    assertThat(Files.exists(manifestFile)).isTrue();
    assertThat(Files.readString(manifestFile)).isEqualTo("Manifest for com.example.app");

    // Step D: Verify post-install query - should successfully match queryRule as com.example.app
    // is now appended to state
    String postInstallStdout =
        executor.run(Command.of(mockCmd.getPath(), "query", "com.example.app"));
    assertThat(postInstallStdout).isEqualTo("com.example.app is installed\n");
  }

  @Test
  public void addToSet_enforcesUniqueness() throws Exception {
    UsmfRule installRule =
        UsmfRule.builder()
            .addCondition(CommandCondition.regexMatch(".*install\\s+(?P<pkg>[A-Za-z0-9_\\.]+).*"))
            .setBehavior(
                CommandBehavior.stdout("Installed ${#C['pkg']}\n")
                    .addStateMutation(
                        BinaryStateMutation.stateNode("#S['installed_packages']")
                            .addToSet("${#C['pkg']}"))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .addRule(installRule)
            .buildAndDeploy();

    executor.run(Command.of(mockCmd.getPath(), "install", "com.example.app"));
    executor.run(Command.of(mockCmd.getPath(), "install", "com.example.app"));

    JsonObject state = mockCmd.readStateJson();
    List<?> installedPackages = gson.fromJson(state.get("installed_packages"), List.class);
    assertThat(installedPackages).containsExactly("com.example.app");
  }

  @Test
  public void compareStateContains_withDictionary_matchesKeySuccessfully() throws Exception {
    RuleCondition searchCond = CommandCondition.exactMatch("search");
    RuleCondition stateContainsCond =
        BinaryStateCondition.stateNode("#S['installed_dict']").contains("com.example.app");
    CommandBehavior queryBehavior = CommandBehavior.builder("Key Found\n", "", 0).build();

    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(searchCond)
            .addCondition(stateContainsCond)
            .setBehavior(queryBehavior)
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "installed_dict": {
                    "com.example.app": "v1.0"
                  }
                }
                """)
            .getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "search"));
    assertThat(stdout).isEqualTo("Key Found\n");
  }

  @Test
  public void stateLoopsInterpolation_dynamicallyRendersForTemplate() throws Exception {
    UsmfRule installRule =
        UsmfRule.builder()
            .addCondition(CommandCondition.regexMatch(".*install\\s+(?P<pkg>[A-Za-z0-9_\\.]+).*"))
            .setBehavior(
                CommandBehavior.stdout("Success\n")
                    .addStateMutation(
                        BinaryStateMutation.stateNode("#S['installed_packages']")
                            .addToList("${#C['pkg']}"))
                    .build())
            .build();

    UsmfRule listRule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("list"))
            .setBehavior(CommandBehavior.stdout("${#S['installed_packages']:'item:[%s] '}").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .addRule(installRule)
            .addRule(listRule)
            .buildAndDeploy();

    String initialList = executor.run(Command.of(mockCmd.getPath(), "list"));
    assertThat(initialList).isEmpty();

    executor.run(Command.of(mockCmd.getPath(), "install", "com.app1"));
    executor.run(Command.of(mockCmd.getPath(), "install", "com.app2"));

    String populatedList = executor.run(Command.of(mockCmd.getPath(), "list"));
    assertThat(populatedList).isEqualTo("item:[com.app1] item:[com.app2] ");
  }

  @Test
  public void getCommandInvocations_containsChronologicalTimestamps() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.prefixMatch("run"))
            .setBehavior(CommandBehavior.stdout("Done\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    executor.run(Command.of(mockCmd.getPath(), "run", "1"));
    Sleeper.defaultSleeper().sleep(Duration.ofMillis(10));
    executor.run(Command.of(mockCmd.getPath(), "run", "2"));

    ImmutableList<CommandInvocation> invocations = mockCmd.readCommandInvocations();
    assertThat(invocations).hasSize(2);

    CommandInvocation invocation1 = invocations.get(0);
    CommandInvocation invocation2 = invocations.get(1);

    assertThat(invocation1.getArgs()).contains("1");
    assertThat(invocation2.getArgs()).contains("2");
    assertThat(invocation1.getStartInstant()).isLessThan(invocation2.getStartInstant());
    CommandInvocation.Result result1 = invocation1.getResultNonEmpty();
    assertThat(
            invocation1.getStartInstant().isBefore(result1.getEndInstant())
                || invocation1.getStartInstant().equals(result1.getEndInstant()))
        .isTrue();
  }

  @Test
  public void getCommandInvocations_withSleep_logsRunningThenFinished() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.prefixMatch("slow"))
            .setBehavior(CommandBehavior.stdout("Done\n").sleep(Duration.ofMillis(500)).build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    CommandProcess process = executor.start(Command.of(mockCmd.getPath(), "slow", "run"));
    try {
      // Wait for the command process to start & write RUNNING state
      ImmutableList<CommandInvocation> invocations = ImmutableList.of();
      for (int i = 0; i < 600; i++) { // 600 * 50ms = 30 seconds
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

      // Now wait for the command to finish completely
      ImmutableList<CommandInvocation> finishedInvocations = ImmutableList.of();
      for (int i = 0; i < 600; i++) { // 600 * 50ms = 30 seconds
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
  public void directStateInterpolation_replacesPlaceholderSuccessfully() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("greet"))
            .setBehavior(CommandBehavior.stdout("Hello, ${#S['user']}!\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "user": "Alice"
                }
                """)
            .getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "greet"));
    assertThat(stdout).isEqualTo("Hello, Alice!\n");
  }

  @Test
  public void singleStateInterpolationWithFormatTemplate_formatsDecimalPlacesSuccessfully()
      throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("ratio"))
            .setBehavior(CommandBehavior.stdout("Ratio is: ${#S['ratio']:'%.2f'}").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "ratio": 3.14159
                }
                """)
            .getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "ratio"));
    assertThat(stdout).isEqualTo("Ratio is: 3.14");
  }

  @Test
  public void nestedPlaceholderResolution_resolvesInnermostFirst() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.regexMatch("test\\s+(?P<suffix>\\d+)"))
            .setBehavior(
                CommandBehavior.stdout(
                        "Result1: ${#S['value_${#S[\'suffix_state\']}']} Result2:"
                            + " ${#S['value_${#C[\'suffix\']}']}\n")
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "suffix_state": "123",
                  "value_123": "nested_success"
                }
                """)
            .getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "test", "123"));
    assertThat(stdout).isEqualTo("Result1: nested_success Result2: nested_success\n");
  }

  @Test
  public void errorsField_capturesSideEffectExceptionsSuccessfully() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("fail_effect"))
            .setBehavior(
                CommandBehavior.stdout("Done\n")
                    .addSideEffect(
                        UsmfRule.LocalFileSideEffect.createFile(
                            "/sys/non_existent_folder/file.txt", "data"))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    executor.run(Command.of(mockCmd.getPath(), "fail_effect"));

    ImmutableList<CommandInvocation> invocations = mockCmd.readCommandInvocations();
    assertThat(invocations).hasSize(1);
    CommandInvocation invocation = invocations.get(0);
    assertThat(invocation.getErrors()).isNotEmpty();
    assertThat(invocation.getErrors().get(0)).contains("Fake USMF Side-Effect Exec Error");
  }

  @Test
  public void deploy_withExistingDirectory_throwsIllegalStateException() throws Exception {
    Path folder = tempDir.resolve("duplicate_sandbox");
    Files.createDirectories(folder);

    UsmfRule rule =
        UsmfRule.builder().setBehavior(CommandBehavior.builder("", "", 0).build()).build();

    UsmfBinary.Builder builder =
        UsmfBinary.builder("mock_cmd", tempDir, "duplicate_sandbox").addRule(rule);
    assertThrows(IllegalStateException.class, () -> builder.buildAndDeploy());
  }

  @Test
  public void regexMatching_nonRe2Syntax_fails() throws Exception {
    RuleCondition invalidRegexCond = CommandCondition.regexMatch(".*install\\s+(?=com.example).*");
    CommandBehavior behavior = CommandBehavior.builder("Matched\n", "", 0).build();
    UsmfRule rule = UsmfRule.builder().addCondition(invalidRegexCond).setBehavior(behavior).build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    Command command = Command.of(mockCmd.getPath(), "install", "com.example.app");
    Exception exception = assertThrows(Exception.class, () -> executor.run(command));
    assertThat(exception).hasMessageThat().contains("Regex compile error");
  }

  @Test
  public void exactArgsWithCoercion_matchesCorrectly() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .addRule(
                UsmfRule.builder()
                    .addCondition(CommandCondition.exactMatch("count", "1"))
                    .setBehavior(CommandBehavior.stdout("matched\n").build())
                    .build())
            .buildAndDeploy();

    // Manually edit rules/mock_rules.json to have numeric type (1 instead of "1")
    Path rulesFile = tempDir.resolve("mock_cmd_sandbox/rules/mock_rules.json");
    String configContent = Files.readString(rulesFile);
    configContent = configContent.replace("\"1\"", "1");
    Files.writeString(rulesFile, configContent);

    String stdout = executor.run(Command.of(mockCmd.getPath(), "count", "1"));
    assertThat(stdout).isEqualTo("matched\n");
  }

  @Test
  public void compareStateCoercion_matchesCorrectly() throws Exception {
    UsmfRule rule1 =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("check"))
            .addCondition(BinaryStateCondition.stateNode("#S['status']").equalTo(true))
            .setBehavior(CommandBehavior.stdout("boolean_matched\n").build())
            .build();

    UsmfRule rule2 =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("count"))
            .addCondition(BinaryStateCondition.stateNode("#S['val']").greaterThan(1.5))
            .setBehavior(CommandBehavior.stdout("numeric_matched\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .addRule(rule1)
            .addRule(rule2)
            .buildAndDeploy();

    // 1. Write the state value as a string "true" structure, check if boolean key matches true
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "status": "true"
                }
                """)
            .getAsJsonObject());
    String stdout1 = executor.run(Command.of(mockCmd.getPath(), "check"));
    assertThat(stdout1).isEqualTo("boolean_matched\n");

    // 2. Write the state value as string "2.5", check if greater than numeric 1.5 matches
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "status": "true",
                  "val": "2.5"
                }
                """)
            .getAsJsonObject());
    String stdout2 = executor.run(Command.of(mockCmd.getPath(), "count"));
    assertThat(stdout2).isEqualTo("numeric_matched\n");
  }

  @Test
  public void sleepMsAndExitCodeCoercion_coercesSuccessfully() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("run"))
            .setBehavior(CommandBehavior.stdout("coerced\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // Manually edit mock_rules.json to turn exit_code and sleep_ms into string representations
    Path rulesFile = tempDir.resolve("mock_cmd_sandbox/rules/mock_rules.json");
    String configContent = Files.readString(rulesFile);
    configContent =
        configContent
            .replace("\"exit_code\": 0", "\"exit_code\": \"42\"")
            .replace("\"exit_code\":0", "\"exit_code\":\"42\"");
    configContent =
        configContent
            .replace("\"sleep_ms\": 0", "\"sleep_ms\": \"120.5\"")
            .replace("\"sleep_ms\":0", "\"sleep_ms\":\"120.5\"");
    Files.writeString(rulesFile, configContent);

    long startTime = InstantSource.system().instant().toEpochMilli();
    Command command = Command.of(mockCmd.getPath(), "run");
    Exception exception = assertThrows(Exception.class, () -> executor.run(command));
    long elapsedMs = Instant.now().minusMillis(startTime).toEpochMilli();

    assertThat(exception).hasMessageThat().contains("exit_code=42");
    assertThat(elapsedMs).isAtLeast(100);
  }

  @Test
  public void conditionInterpolation_resolvesPlaceholdersInConditions() throws Exception {
    RuleCondition ruleCond = CommandCondition.regexMatch("check\\s+(?P<pkg>[A-Za-z0-9_\\.]+)");
    RuleCondition stateCond =
        BinaryStateCondition.stateNode("#S['status_${#C[\'pkg\']}']").equalTo("active");
    CommandBehavior behavior = CommandBehavior.stdout("active_matched\n").build();

    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(ruleCond)
            .addCondition(stateCond)
            .setBehavior(behavior)
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // 1. Try to run check without writing state - should not match (empty fallback output)
    String stdout1 = executor.run(Command.of(mockCmd.getPath(), "check", "com.foo.app"));
    assertThat(stdout1).isEmpty();

    // 2. Write state key status_com.foo.app = active, then run - should match!
    mockCmd.writeStateJson(
        JsonParser.parseString("{\"status_com.foo.app\": \"active\"}").getAsJsonObject());
    String stdout2 = executor.run(Command.of(mockCmd.getPath(), "check", "com.foo.app"));
    assertThat(stdout2).isEqualTo("active_matched\n");
  }

  @Test
  public void nestedPlaceholderWithHyphens_resolvesProperly() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("package-check"))
            .setBehavior(
                CommandBehavior.stdout(
                        "Package listing: ${#S['installed_packages_${#S[\'device-id\']}']}")
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "device-id": "emulator-5554",
                  "installed_packages_emulator-5554": "com.foo.bar"
                }
                """)
            .getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "package-check"));
    assertThat(stdout).isEqualTo("Package listing: com.foo.bar");
  }

  @Test
  public void nestedPlaceholderInFormat_resolvesProperly() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("format-check"))
            .setBehavior(CommandBehavior.stdout("Result: ${#S['value']:'${#S['format']}'}").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "format": "val:[%s]",
                  "value": 123.456
                }
                """)
            .getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "format-check"));
    assertThat(stdout).isEqualTo("Result: val:[123.456]");
  }

  @Test
  public void numericMutationWithStringInterpolation_addsCorrectly() throws Exception {
    UsmfRule installRule =
        UsmfRule.builder()
            .addCondition(CommandCondition.regexMatch(".*install\\s+(?P<pkg>[A-Za-z0-9_\\.]+).*"))
            .setBehavior(
                CommandBehavior.stdout("Installed ${#C['pkg']}\n")
                    .addStateMutation(
                        BinaryStateMutation.stateNode("#S['installed_count']")
                            .plus("${#S['step_size']}"))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .addRule(installRule)
            .buildAndDeploy();
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "step_size": "2",
                  "installed_count": 1
                }
                """)
            .getAsJsonObject());

    executor.run(Command.of(mockCmd.getPath(), "install", "com.example.app"));

    JsonObject state = mockCmd.readStateJson();
    assertThat(state.get("installed_count").getAsInt()).isEqualTo(3);
  }

  @Test
  public void corruptRulesFile_terminatesWithErrorCodeOne() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("test"))
            .setBehavior(CommandBehavior.stdout("matched\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // Damage mock_rules.json with bad JSON syntax
    Path rulesFile = tempDir.resolve("mock_cmd_sandbox/rules/mock_rules.json");
    Files.writeString(rulesFile, "{ corrupt json");

    Command command = Command.of(mockCmd.getPath(), "test");
    Exception exception = assertThrows(Exception.class, () -> executor.run(command));

    assertThat(exception).hasMessageThat().contains("exit_code=1");

    ImmutableList<CommandInvocation> invocations = mockCmd.readCommandInvocations();
    assertThat(invocations).hasSize(1);
    CommandInvocation invocation = invocations.get(0);
    assertThat(invocation.getResultNonEmpty().getExitCode()).isEqualTo(1);
    assertThat(invocation.getErrors().get(0)).contains("Mock USMF JSON Error for rules file");
  }

  @Test
  public void concurrentStateMutations_acquiresLockAndPreservesAll() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.prefixMatch("increment"))
            .setBehavior(
                CommandBehavior.stdout("incremented\n")
                    .sleep(Duration.ofMillis(300))
                    .addStateMutation(BinaryStateMutation.stateNode("#S['counter']").plus(1))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "counter": 0
                }
                """)
            .getAsJsonObject());

    // Spawn 10 concurrent processes mutating the state
    List<CommandProcess> processes = new ArrayList<>();
    for (int i = 0; i < 10; ++i) {
      processes.add(executor.start(Command.of(mockCmd.getPath(), "increment")));
    }
    for (CommandProcess p : processes) {
      p.await();
    }

    // Verify counter is exactly 10, meaning no updates were lost to race conditions
    JsonObject state = mockCmd.readStateJson();
    assertThat(state.get("counter").getAsInt()).isEqualTo(10);
  }

  @Test
  public void compareStateContains_withIntegerInString_matchesCoercedSuccessfully()
      throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("check"))
            .addCondition(BinaryStateCondition.stateNode("#S['val']").contains(123))
            .setBehavior(CommandBehavior.stdout("contains_matched\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "val": "emu_123"
                }
                """)
            .getAsJsonObject());
    String stdout = executor.run(Command.of(mockCmd.getPath(), "check"));
    assertThat(stdout).isEqualTo("contains_matched\n");
  }

  @Test
  public void compareStateContains_withMissingKey_returnsFalse() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("check"))
            .addCondition(BinaryStateCondition.stateNode("#S['missing_key']").contains("None"))
            .setBehavior(CommandBehavior.stdout("contains_matched\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // The state does not have "missing_key", so it is evaluated as None.
    // The contains("None") check should yield False (no match), falling back to exit 0 and empty
    // stdout.
    String stdout = executor.run(Command.of(mockCmd.getPath(), "check"));
    assertThat(stdout).isEmpty();
  }

  @Test
  public void coerceNumeric_withScientificNotation_parsesCorrectly() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("add"))
            .setBehavior(
                CommandBehavior.stdout("added\n")
                    .addStateMutation(BinaryStateMutation.stateNode("#S['val']").plus("1e2"))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "val": 10.0
                }
                """)
            .getAsJsonObject());

    executor.run(Command.of(mockCmd.getPath(), "add"));
    JsonObject state = mockCmd.readStateJson();
    assertThat(state.get("val").getAsDouble()).isEqualTo(110.0);
  }

  @Test
  public void unknownConditionType_rejectsAndDoesNotMatchRule() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("test"))
            .setBehavior(CommandBehavior.stdout("matched\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // Manually edit mock_rules.json to inject an unknown condition type (e.g. "type":"stat")
    Path rulesFile = tempDir.resolve("mock_cmd_sandbox/rules/mock_rules.json");
    String configContent = Files.readString(rulesFile);
    configContent = configContent.replace("\"type\":\"command\"", "\"type\":\"stat\"");
    Files.writeString(rulesFile, configContent);

    // Running should not match, since unknown condition type returns false. Output should be empty.
    String stdout = executor.run(Command.of(mockCmd.getPath(), "test"));
    assertThat(stdout).isEmpty();
  }

  @Test
  public void sideEffectWriteFile_handlesNonAsciiCharactersSuccessfully() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("write"))
            .setBehavior(
                CommandBehavior.stdout("Done\n")
                    .addSideEffect(
                        LocalFileSideEffect.createFile(
                            tempDir.toAbsolutePath() + "/non_ascii.txt", "Hello 測試"))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    executor.run(Command.of(mockCmd.getPath(), "write"));

    Path textFile = tempDir.resolve("non_ascii.txt");
    assertThat(Files.exists(textFile)).isTrue();
    assertThat(Files.readString(textFile)).isEqualTo("Hello 測試");
  }

  @Test
  public void prefixedInterpolation_resolvesCapturesAndStatesDistinctly() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(
                CommandCondition.regexMatch(".*install\\s+(?P<pkg>\\S+)\\s+(?P<device>\\S+).*"))
            .setBehavior(
                CommandBehavior.stdout(
                        "Installed ${#C['pkg']} on ${#C['device']} with count"
                            + " ${#S['installed_count']}\n")
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "installed_count": 5
                }
                """)
            .getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "install", "foo", "bar"));
    assertThat(stdout).isEqualTo("Installed foo on bar with count 5\n");
  }

  @Test
  public void nonPrefixedPlaceholder_isNotResolved() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("greet"))
            .setBehavior(CommandBehavior.stdout("Hello, ${user}!\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "user": "Alice"
                }
                """)
            .getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "greet"));
    assertThat(stdout).isEqualTo("Hello, ${user}!\n");
  }

  @Test
  public void concurrentStateMatching_preventsConflictAndMaintainsAtomicTransaction()
      throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.prefixMatch("try_increment"))
            .addCondition(BinaryStateCondition.stateNode("#S['counter']").lessThan(2))
            .setBehavior(
                CommandBehavior.stdout("success\n")
                    .addStateMutation(BinaryStateMutation.stateNode("#S['counter']").plus(1))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "counter": 0
                }
                """)
            .getAsJsonObject());

    // Spawn 10 concurrent processes trying to increment the counter
    List<CommandProcess> processes = new ArrayList<>();
    for (int i = 0; i < 10; ++i) {
      processes.add(executor.start(Command.of(mockCmd.getPath(), "try_increment")));
    }

    int successCount = 0;
    for (CommandProcess p : processes) {
      CommandResult result = p.await();
      if (result.stdout().contains("success")) {
        successCount++;
      }
    }

    // Since counter < 2 is checked under transactional state matching lock,
    // only exactly 2 invocations must match the rule and print success.
    assertThat(successCount).isEqualTo(2);
    JsonObject state = mockCmd.readStateJson();
    assertThat(state.get("counter").getAsInt()).isEqualTo(2);
  }

  @Test
  public void dictKeyInterpolation_interpolatesNestedDictKeys() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.regexMatch("register\\s+(?P<device>\\S+)"))
            .setBehavior(
                CommandBehavior.stdout("success\n")
                    .addStateMutation(
                        BinaryStateMutation.stateNode("#S['devices_map']")
                            .set(ImmutableMap.of("status_${#C['device']}", "online")))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    String stdout = executor.run(Command.of(mockCmd.getPath(), "register", "device-abc"));
    assertThat(stdout).isEqualTo("success\n");

    // Read the map state, the nested key status_${#C['device']} must be resolved to
    // status_device-abc
    JsonObject state = mockCmd.readStateJson();
    Map<?, ?> map = gson.fromJson(state.get("devices_map"), Map.class);
    assertThat(map).containsEntry("status_device-abc", "online");
  }

  @Test
  public void sideEffectCreateDirectory_createsDirectorySuccessfully() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("mkdir"))
            .setBehavior(
                CommandBehavior.stdout("Directory created\n")
                    .addSideEffect(
                        LocalFileSideEffect.createDirectory(
                            tempDir.toAbsolutePath() + "/new_test_dir"))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    String stdout = executor.run(Command.of(mockCmd.getPath(), "mkdir"));
    assertThat(stdout).isEqualTo("Directory created\n");

    Path createdDir = tempDir.resolve("new_test_dir");
    assertThat(Files.exists(createdDir)).isTrue();
    assertThat(Files.isDirectory(createdDir)).isTrue();
  }

  @Test
  public void compareStateGreaterThanOrEqualToAndLessThanOrEqualTo_matchesCorrectly()
      throws Exception {
    UsmfRule ruleGte =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("check-gte"))
            .addCondition(BinaryStateCondition.stateNode("#S['val']").greaterThanOrEqualTo(5))
            .setBehavior(CommandBehavior.stdout("gte_matched\n").build())
            .build();

    UsmfRule ruleLte =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("check-lte"))
            .addCondition(BinaryStateCondition.stateNode("#S['val']").lessThanOrEqualTo(5))
            .setBehavior(CommandBehavior.stdout("lte_matched\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .addRule(ruleGte)
            .addRule(ruleLte)
            .buildAndDeploy();

    mockCmd.writeStateJson(JsonParser.parseString("{\"val\": 6}").getAsJsonObject());
    String stdout1 = executor.run(Command.of(mockCmd.getPath(), "check-gte"));
    assertThat(stdout1).isEqualTo("gte_matched\n");

    mockCmd.writeStateJson(JsonParser.parseString("{\"val\": 5}").getAsJsonObject());
    String stdout2 = executor.run(Command.of(mockCmd.getPath(), "check-gte"));
    assertThat(stdout2).isEqualTo("gte_matched\n");

    mockCmd.writeStateJson(JsonParser.parseString("{\"val\": 4}").getAsJsonObject());
    String stdout3 = executor.run(Command.of(mockCmd.getPath(), "check-lte"));
    assertThat(stdout3).isEqualTo("lte_matched\n");

    mockCmd.writeStateJson(JsonParser.parseString("{\"val\": 5}").getAsJsonObject());
    String stdout4 = executor.run(Command.of(mockCmd.getPath(), "check-lte"));
    assertThat(stdout4).isEqualTo("lte_matched\n");
  }

  @Test
  public void deployWithZeroRules_triggersFallbackSuccessfully() throws Exception {
    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").buildAndDeploy();

    String stdout = executor.run(Command.of(mockCmd.getPath(), "any", "unmocked", "command"));
    assertThat(stdout).isEmpty();
  }

  @Test
  public void singleStateInterpolationWithFormatTemplate_formatsStringDecimalsSuccessfully()
      throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("ratio"))
            .setBehavior(CommandBehavior.stdout("Ratio is: ${#S['ratio']:'%.2f'}").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(JsonParser.parseString("{\"ratio\": \"3.14159\"}").getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "ratio"));
    assertThat(stdout).isEqualTo("Ratio is: 3.14");
  }

  @Test
  public void astExpressionEvaluator_nestedStateAndCaptures_resolvesSuccessfully()
      throws Exception {
    RuleCondition condRegex = CommandCondition.regexMatch("check-pkg\\s+(?P<device_id>\\S+)");
    RuleCondition condState =
        BinaryStateCondition.stateNode("#S['installed_packages'][#C['device_id']]")
            .contains("com.foo.app");

    CommandBehavior behavior =
        CommandBehavior.stdout("Found com.foo.app on ${#C['device_id']}\n").build();

    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(condRegex)
            .addCondition(condState)
            .setBehavior(behavior)
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // 1. Write nested state via Java API
    mockCmd.writeStateJson(
        JsonParser.parseString(
                """
                {
                  "installed_packages": {
                    "device-123": ["com.foo.app", "com.bar.app"]
                  }
                }
                """)
            .getAsJsonObject());

    // 2. Execute command and verify
    String stdout = executor.run(Command.of(mockCmd.getPath(), "check-pkg", "device-123"));
    assertThat(stdout).isEqualTo("Found com.foo.app on device-123\n");
  }

  @Test
  public void variablesMappingWithVContext_translatesApkTargetSuccessfully() throws Exception {
    RuleCondition condInstall = CommandCondition.regexMatch("install\\s+(?P<apk>\\S+)");

    // Mutation value using bare expression to translate apk name to package name via #V
    BinaryStateMutation installMut =
        BinaryStateMutation.stateNode("#S['installed_apk']").set("${#V['pkg_map'][#C['apk']]}");

    CommandBehavior behavior =
        CommandBehavior.stdout("Successfully installed ${#V['pkg_map'][#C['apk']]}\n")
            .addStateMutation(installMut)
            .build();

    UsmfRule rule = UsmfRule.builder().addCondition(condInstall).setBehavior(behavior).build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .setVariables(
                JsonParser.parseString(
                        """
                        {
                          "pkg_map": {
                            "foo.apk": "com.foo.app",
                            "bar.apk": "com.bar.app"
                          }
                        }
                        """)
                    .getAsJsonObject())
            .addRule(rule)
            .buildAndDeploy();

    // 1. Run command
    String stdout = executor.run(Command.of(mockCmd.getPath(), "install", "foo.apk"));
    assertThat(stdout).isEqualTo("Successfully installed com.foo.app\n");

    // 2. Read state verify
    JsonObject state = mockCmd.readStateJson();
    assertThat(state.get("installed_apk").getAsString()).isEqualTo("com.foo.app");
  }

  @Test
  public void astExpressionMutation_autoCreatesIntermediateContainers() throws Exception {
    RuleCondition condIncr = CommandCondition.exactMatch("incr-count");

    // Target is recursively an IndexAccessExpr where intermediate map is missing
    BinaryStateMutation plusMut =
        BinaryStateMutation.stateNode("#S['stats_by_device']['device-456']").plus(1);

    CommandBehavior behavior = CommandBehavior.stdout("done\n").addStateMutation(plusMut).build();

    UsmfRule rule = UsmfRule.builder().addCondition(condIncr).setBehavior(behavior).build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // stats_by_device container is NOT set in state, verify it is auto-created as dict
    String stdout = executor.run(Command.of(mockCmd.getPath(), "incr-count"));
    assertThat(stdout).isEqualTo("done\n");

    JsonObject state = mockCmd.readStateJson();
    assertThat(state.getAsJsonObject("stats_by_device").get("device-456").getAsInt()).isEqualTo(1);
  }

  @Test
  public void nullCoalescing_safetyDefaulting_resolvesPlaceholderSuccessful() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("greet"))
            .setBehavior(CommandBehavior.stdout("Hello, ${#S['user']?'DefaultUser'}!\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // 1. Run without writing state, verify it falls back to the safety default
    String stdoutDefault = executor.run(Command.of(mockCmd.getPath(), "greet"));
    assertThat(stdoutDefault).isEqualTo("Hello, DefaultUser!\n");

    // 2. Write state, verify it uses the state value instead of default
    mockCmd.writeStateJson(JsonParser.parseString("{\"user\": \"Bob\"}").getAsJsonObject());
    String stdoutBob = executor.run(Command.of(mockCmd.getPath(), "greet"));
    assertThat(stdoutBob).isEqualTo("Hello, Bob!\n");
  }

  @Test
  public void nullCoalescingAndFormat_resolvesInPriorityOrder() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.regexMatch("get-ip(?:\\s+(?P<device>\\S+))?"))
            .setBehavior(CommandBehavior.stdout("IP: ${#C['device']?'':'device-%s'}\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // 1. Run without device capture (None), coalescing resolves to empty string, formatting runs
    // resulting in 'device-'
    String stdoutEmpty = executor.run(Command.of(mockCmd.getPath(), "get-ip"));
    assertThat(stdoutEmpty).isEqualTo("IP: device-\n");

    // 2. Run with device capture, coalescing skipped because it's not None, formatting formats the
    // string
    String stdoutDevice = executor.run(Command.of(mockCmd.getPath(), "get-ip", "phone1"));
    assertThat(stdoutDevice).isEqualTo("IP: device-phone1\n");
  }

  @Test
  public void invalidStateNodeSyntax_withSuffixOperators_registersAsError() throws Exception {
    // Rule condition has normal matching, behavior uses stateNode with forbidden "?" operator for
    // mutation
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("check"))
            .setBehavior(
                CommandBehavior.stdout("success\n")
                    .addStateMutation(
                        BinaryStateMutation.stateNode("#S['status']?'default'").set("active"))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    executor.run(Command.of(mockCmd.getPath(), "check"));

    ImmutableList<CommandInvocation> invocations = mockCmd.readCommandInvocations();
    assertThat(invocations).hasSize(1);
    CommandInvocation invocation = invocations.get(0);
    assertThat(invocation.getErrors()).isNotEmpty();
    assertThat(invocation.getErrors().get(0)).contains("SyntaxError");
  }

  @Test
  public void sequentialMatching_firstMatchWin_runsFirstMatchedRuleOnly() throws Exception {
    UsmfRule rule1 =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("greet"))
            .setBehavior(CommandBehavior.stdout("first\n").build())
            .build();
    UsmfRule rule2 =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("greet"))
            .setBehavior(CommandBehavior.stdout("second\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox")
            .addRule(rule1)
            .addRule(rule2)
            .buildAndDeploy();

    String stdout = executor.run(Command.of(mockCmd.getPath(), "greet"));
    assertThat(stdout).isEqualTo("first\n");
  }

  @Test
  public void listIndexMutation_nonExistingListIndex_logsErrorAndFails() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("mutate"))
            .setBehavior(
                CommandBehavior.stdout("mutated\n")
                    .addStateMutation(BinaryStateMutation.stateNode("#S['items']['0']").set("val"))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // Initialize 'items' explicitly as empty list
    mockCmd.writeStateJson(JsonParser.parseString("{\"items\": []}").getAsJsonObject());

    executor.run(Command.of(mockCmd.getPath(), "mutate"));

    ImmutableList<CommandInvocation> invocations = mockCmd.readCommandInvocations();
    assertThat(invocations).hasSize(1);
    CommandInvocation invocation = invocations.get(0);
    assertThat(invocation.getErrors()).isNotEmpty();
    assertThat(invocation.getErrors().get(0)).contains("target must be a mutable state node");
  }

  @Test
  public void listIndexLookup_resolvesValueSuccessfully() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("get-item"))
            .setBehavior(CommandBehavior.stdout("Item: ${#S['items']['0']}\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(
        JsonParser.parseString("{\"items\": [\"first_element\"]}").getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "get-item"));
    assertThat(stdout).isEqualTo("Item: first_element\n");
  }

  @Test
  public void astMutation_indexAccessInRightChild_doesNotLazyBackfill() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("mutate"))
            .setBehavior(
                CommandBehavior.stdout("done\n")
                    .addStateMutation(
                        BinaryStateMutation.stateNode(
                                "#S['stats_by_device'][#S['missing_lookup']['key']]")
                            .plus(1))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    executor.run(Command.of(mockCmd.getPath(), "mutate"));

    // Verify errors log contains mutation error
    ImmutableList<CommandInvocation> invocations = mockCmd.readCommandInvocations();
    assertThat(invocations).hasSize(1);
    assertThat(invocations.get(0).getErrors()).isNotEmpty();
    assertThat(invocations.get(0).getErrors().get(0))
        .contains("target must be a mutable state node");

    // Verify stats_by_device was backfilled on the left side path
    JsonObject state = mockCmd.readStateJson();
    assertThat(state.getAsJsonObject("stats_by_device").size()).isEqualTo(0);

    // Verify missing_lookup on the right side index path was NOT backfilled
    assertThat(state.get("missing_lookup")).isNull();
  }

  @Test
  public void conditionStateNodeWithSuffixOperators_swallowsParsingExceptionAndUnmatches()
      throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("check"))
            .addCondition(
                BinaryStateCondition.stateNode("#S['status']?'default'").equalTo("default"))
            .setBehavior(CommandBehavior.stdout("matched\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(JsonParser.parseString("{\"status\": \"active\"}").getAsJsonObject());

    // It contains invalid suffix operator '?' inside condition's state_node.
    // Exception is swallowed during matching, evaluating condition as false (unmatched).
    String stdout = executor.run(Command.of(mockCmd.getPath(), "check"));
    assertThat(stdout).isEmpty();
  }

  @Test
  public void listIndexMutation_existingListIndex_updatesSuccessfully() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("mutate"))
            .setBehavior(
                CommandBehavior.stdout("done\n")
                    .addStateMutation(
                        BinaryStateMutation.stateNode("#S['items']['0']").set("new_value"))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(
        JsonParser.parseString("{\"items\": [\"old_value\"]}").getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "mutate"));
    assertThat(stdout).isEqualTo("done\n");
    JsonObject state = mockCmd.readStateJson();
    List<?> items = gson.fromJson(state.get("items"), List.class);
    assertThat(items).containsExactly("new_value");
  }

  @Test
  public void listIndexMutation_existingListIndexWithPlus_incrementsSuccessfully()
      throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("increment"))
            .setBehavior(
                CommandBehavior.stdout("done\n")
                    .addStateMutation(BinaryStateMutation.stateNode("#S['counts']['1']").plus(5))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    // Initialize 'counts' list with multiple items.
    mockCmd.writeStateJson(JsonParser.parseString("{\"counts\": [10, 20]}").getAsJsonObject());

    executor.run(Command.of(mockCmd.getPath(), "increment"));
    JsonObject state = mockCmd.readStateJson();
    List<?> counts = gson.fromJson(state.get("counts"), List.class);
    assertThat(counts).containsExactly(10.0, 25.0).inOrder();
  }

  @Test
  public void invalidStateNodeSyntax_unquotedIndexLiteral_registersAsError() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("check"))
            .setBehavior(
                CommandBehavior.stdout("success\n")
                    .addStateMutation(
                        BinaryStateMutation.stateNode("#S['stats'][device-abc]").set("value"))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    executor.run(Command.of(mockCmd.getPath(), "check"));

    ImmutableList<CommandInvocation> invocations = mockCmd.readCommandInvocations();
    assertThat(invocations).hasSize(1);
    assertThat(invocations.get(0).getErrors()).isNotEmpty();
    // Verify that the parser throws ValueError containing the quote constraint reminder.
    assertThat(invocations.get(0).getErrors().get(0))
        .contains("USMF Syntax Error: Literal strings must be quoted");
  }

  @Test
  public void nestedLookup_withMissingParent_resolvesToEmptyOrCoalesced() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("test"))
            .setBehavior(
                CommandBehavior.stdout(
                        "val1: ${#S['missing_map']['key']} val2:"
                            + " ${#S['missing_map']['key']?'default'}\n")
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    String stdout = executor.run(Command.of(mockCmd.getPath(), "test"));
    assertThat(stdout).isEqualTo("val1:  val2: default\n");
    ImmutableList<CommandInvocation> invocations = mockCmd.readCommandInvocations();
    assertThat(invocations).hasSize(1);
    assertThat(invocations.get(0).getErrors()).isEmpty();
  }

  @Test
  public void invalidPlaceholderSyntax_fallsBackToOriginalPlaceholderString() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("greet"))
            .setBehavior(CommandBehavior.stdout("Ratio is: ${#S['unclosed_index").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // Verify that syntactically invalid placeholder resolves to itself since the parsing fails.
    String stdout = executor.run(Command.of(mockCmd.getPath(), "greet"));
    assertThat(stdout).isEqualTo("Ratio is: ${#S['unclosed_index");
  }

  @Test
  public void conditionEvaluation_withNestedMissingKeys_doesNotLazyBackfill() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("check"))
            .addCondition(
                BinaryStateCondition.stateNode("#S['missing_parent']['child']").equalTo("active"))
            .setBehavior(CommandBehavior.stdout("matched\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // Verify condition matching fails, outputting empty fallback.
    String stdout = executor.run(Command.of(mockCmd.getPath(), "check"));
    assertThat(stdout).isEmpty();

    // Verify missing_parent was NOT backfilled and remains null.
    JsonObject state = mockCmd.readStateJson();
    assertThat(state.get("missing_parent")).isNull();
  }

  @Test
  public void stateCondition_withNullExpected_matchesCorrectly() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("check"))
            .addCondition(BinaryStateCondition.stateNode("#S['installed']").equalTo(null))
            .setBehavior(CommandBehavior.stdout("matched_null\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // 1. Initially, state 'installed' is missing (null). Verify it matches the null condition.
    String stdoutNull = executor.run(Command.of(mockCmd.getPath(), "check"));
    assertThat(stdoutNull).isEqualTo("matched_null\n");

    // 2. Write non-null state, verify it no longer matches.
    mockCmd.writeStateJson(JsonParser.parseString("{\"installed\": true}").getAsJsonObject());
    String stdoutTrue = executor.run(Command.of(mockCmd.getPath(), "check"));
    assertThat(stdoutTrue).isEmpty();

    // 3. Write null state explicitly, verify it matches again.
    mockCmd.writeStateJson(JsonParser.parseString("{\"installed\": null}").getAsJsonObject());
    String stdoutExplicitNull = executor.run(Command.of(mockCmd.getPath(), "check"));
    assertThat(stdoutExplicitNull).isEqualTo("matched_null\n");
  }

  @Test
  public void uString_withQuotedLiteralPlaceholder_evaluatesAndFoldsCorrectly() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("greet"))
            .setBehavior(CommandBehavior.stdout("Result: ${'hello'}!\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    String stdout = executor.run(Command.of(mockCmd.getPath(), "greet"));
    assertThat(stdout).isEqualTo("Result: hello!\n");
  }

  @Test
  public void stateNode_withUnbalancedBracketInQuotedKey_resolvesAndMutatesSuccessfully()
      throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("mutate"))
            .setBehavior(
                CommandBehavior.stdout("done\n")
                    .addStateMutation(
                        BinaryStateMutation.stateNode("#S['items']['file[1.txt']").set("val"))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(JsonParser.parseString("{\"items\": {}}").getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "mutate"));
    assertThat(stdout).isEqualTo("done\n");

    JsonObject state = mockCmd.readStateJson();
    JsonObject items = state.getAsJsonObject("items");
    assertThat(items.get("file[1.txt").getAsString()).isEqualTo("val");
  }

  @Test
  public void stateNode_withoutHashPrefix_failsActionAndRegistersSyntaxError() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("mutate"))
            .setBehavior(
                CommandBehavior.stdout("done\n")
                    .addStateMutation(BinaryStateMutation.stateNode("S['items']").set("val"))
                    .build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    executor.run(Command.of(mockCmd.getPath(), "mutate"));

    ImmutableList<CommandInvocation> invocations = mockCmd.readCommandInvocations();
    assertThat(invocations).hasSize(1);
    assertThat(invocations.get(0).getErrors()).isNotEmpty();
    assertThat(invocations.get(0).getErrors().get(0)).contains("Literal strings must be quoted");
  }

  @Test
  public void stdout_withoutHashPrefix_remainsUninterpolated() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("greet"))
            .setBehavior(CommandBehavior.stdout("Hello ${S['user']}!\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();
    mockCmd.writeStateJson(JsonParser.parseString("{\"user\": \"Bob\"}").getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "greet"));
    assertThat(stdout).isEqualTo("Hello ${S['user']}!\n");
  }

  @Test
  public void regexCommandMatching_withSpaceArguments_quotesCorrectly() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.regexMatch(".*install\\s+'a b'.*"))
            .setBehavior(CommandBehavior.stdout("matched\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    String stdout = executor.run(Command.of(mockCmd.getPath(), "install", "a b"));
    assertThat(stdout).isEqualTo("matched\n");
  }

  @Test
  public void regexCommandMatching_startsAtZeroOnly_anchoredCorrectly() throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.regexMatch("install"))
            .setBehavior(CommandBehavior.stdout("matched\n").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    // Starts with install - should match
    String stdout1 = executor.run(Command.of(mockCmd.getPath(), "install"));
    assertThat(stdout1).isEqualTo("matched\n");

    // Does not start with install - should not match
    String stdout2 = executor.run(Command.of(mockCmd.getPath(), "adb", "install"));
    assertThat(stdout2).isEmpty();
  }

  @Test
  public void singleStateInterpolationWithFormatTemplate_withNewlineFormat_resolvesSuccessfully()
      throws Exception {
    UsmfRule rule =
        UsmfRule.builder()
            .addCondition(CommandCondition.exactMatch("list"))
            .setBehavior(CommandBehavior.stdout("${#S['val']:'prefix:%s\n'}").build())
            .build();

    UsmfBinary mockCmd =
        UsmfBinary.builder("mock_cmd", tempDir, "mock_cmd_sandbox").addRule(rule).buildAndDeploy();

    mockCmd.writeStateJson(JsonParser.parseString("{\"val\": \"my_value\"}").getAsJsonObject());

    String stdout = executor.run(Command.of(mockCmd.getPath(), "list"));
    assertThat(stdout).isEqualTo("prefix:my_value\n");
  }

  @Test
  public void usmfEnvironment_withCustomParentDir_outputsSummary() throws Exception {
    Path customParent = tempDir.resolve("custom_parent_dir");
    Files.createDirectories(customParent);

    UsmfEnvironment environment = new UsmfEnvironment(customParent);
    Description description =
        Description.createTestDescription(
            UsmfTest.class, "usmfEnvironment_withCustomParentDir_outputsSummary");

    UsmfBinary mockCmd;
    environment.starting(description);
    try {
      UsmfRule rule =
          UsmfRule.builder()
              .addCondition(CommandCondition.exactMatch("run"))
              .setBehavior(CommandBehavior.stdout("Done\n").build())
              .build();

      mockCmd = environment.createBinary("mock_cmd").addRule(rule).buildAndDeploy();

      executor.run(Command.of(mockCmd.getPath(), "run"));
    } finally {
      environment.finished(description);
    }

    // Verify the summary.json is generated because we are not using a temporary folder.
    Path summaryFile =
        Path.of(mockCmd.getSandboxDir()).resolve(UsmfBinary.LOGS_DIR_NAME).resolve("summary.json");
    assertThat(Files.exists(summaryFile)).isTrue();
    String summaryContent = Files.readString(summaryFile);
    assertThat(summaryContent).contains("run");
  }
}
