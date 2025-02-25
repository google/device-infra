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

package com.google.devtools.mobileharness.platform.android.xts.agent;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Correspondence.transforming;
import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.platform.android.xts.agent.testdata.FakeTradefed.DEVICE_ID_TO_TRIGGER_CHECKED_INVOCATION_EXCEPTION;
import static com.google.devtools.mobileharness.platform.android.xts.agent.testdata.FakeTradefed.DEVICE_ID_TO_TRIGGER_UNCHECKED_INVOCATION_EXCEPTION;
import static com.google.devtools.mobileharness.platform.android.xts.agent.testdata.FakeTradefed.INVOCATION_CHECKED_EXCEPTION_MESSAGE;
import static com.google.devtools.mobileharness.platform.android.xts.agent.testdata.FakeTradefed.INVOCATION_UNCHECKED_EXCEPTION_MESSAGE;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo.TradefedInvocation;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil.XtsTradefedRuntimeInfoFileDetail;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TradefedInvocationAgentTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String FAKE_TRADEFED_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/platform/android/xts/"
              + "agent/testdata/fake_tradefed_deploy.jar");
  private static final String AGENT_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/devtools/mobileharness/platform/android/xts/"
              + "agent/tradefed_invocation_agent_deploy.jar");

  private final CommandExecutor commandExecutor = new CommandExecutor();
  private final SystemUtil systemUtil = new SystemUtil();
  private final XtsTradefedRuntimeInfoFileUtil xtsTradefedRuntimeInfoFileUtil =
      new XtsTradefedRuntimeInfoFileUtil();

  @Test
  public void premain() throws Exception {
    Path runtimeInfoFilePath = tempFolder.newFolder().toPath().resolve("runtime_info_file.txt");

    CommandProcess commandProcess =
        commandExecutor.start(
            Command.of(
                    systemUtil
                        .getJavaCommandCreator()
                        .createJavaCommand(
                            FAKE_TRADEFED_PATH,
                            ImmutableList.of("-s", "device1", "-s", "device2"),
                            ImmutableList.of(
                                String.format(
                                    "-javaagent:%s=%s", AGENT_PATH, runtimeInfoFilePath))))
                .showFullResultInException(true));

    List<XtsTradefedRuntimeInfo> runtimeInfos = new ArrayList<>();
    Instant lastModifiedTime = null;
    while (commandProcess.isAlive()) {
      Sleeper.defaultSleeper().sleep(Duration.ofMillis(200L));
      Optional<XtsTradefedRuntimeInfoFileDetail> fileDetail =
          xtsTradefedRuntimeInfoFileUtil.readInfo(runtimeInfoFilePath, lastModifiedTime);
      if (fileDetail.isPresent()) {
        lastModifiedTime = fileDetail.get().lastModifiedTime();
        runtimeInfos.add(fileDetail.get().runtimeInfo());
      }
    }

    // runtimeInfos should contain three elements:
    // 1. The first is the initial invocation record when tradefed is running.
    // 2. The second is when tradefed completes, so it's an empty XtsTradefedRuntimeInfo with no
    // running invocations.
    // 3. The third is when the agent checks for the tradefed invocation error, and saves the
    // invocation record with error message (and isRunning=false).

    assertThat(runtimeInfos)
        .comparingElementsUsing(
            transforming(
                (XtsTradefedRuntimeInfo runtimeInfo) ->
                    requireNonNull(runtimeInfo).invocations().stream()
                        .map(TradefedInvocation::deviceIds)
                        .collect(toImmutableList()),
                "has invocations containing device IDs of"))
        .containsExactly(
            ImmutableList.of(ImmutableList.of("device1", "device2")),
            ImmutableList.of(),
            ImmutableList.of(ImmutableList.of("device1", "device2")))
        .inOrder();

    assertThat(runtimeInfos)
        .comparingElementsUsing(
            transforming(
                (XtsTradefedRuntimeInfo runtimeInfo) ->
                    requireNonNull(runtimeInfo).invocations().stream()
                        .map(TradefedInvocation::status)
                        .collect(toImmutableList()),
                "has invocations containing statuses of"))
        .containsExactly(ImmutableList.of("init"), ImmutableList.of(), ImmutableList.of(""))
        .inOrder();

    assertThat(runtimeInfos)
        .comparingElementsUsing(
            transforming(
                (XtsTradefedRuntimeInfo runtimeInfo) ->
                    requireNonNull(runtimeInfo).invocations().stream()
                        .map(TradefedInvocation::errorMessage)
                        .collect(toImmutableList()),
                "has invocations containing error messages of"))
        .containsExactly(
            ImmutableList.of(""), ImmutableList.of(), ImmutableList.of("Fake error message"))
        .inOrder();

    assertThat(runtimeInfos)
        .comparingElementsUsing(
            transforming(
                (XtsTradefedRuntimeInfo runtimeInfo) ->
                    requireNonNull(runtimeInfo).invocations().stream()
                        .map(TradefedInvocation::isRunning)
                        .map(isRunning -> Boolean.toString(isRunning))
                        .collect(toImmutableList()),
                "has invocations containing isRunning values of"))
        .containsExactly(ImmutableList.of("true"), ImmutableList.of(), ImmutableList.of("false"))
        .inOrder();

    CommandResult commandResult = commandProcess.await();
    logger.atInfo().log("Command result: %s", commandResult);
    assertThat(commandResult.exitCode()).isEqualTo(0);
  }

  @Test
  public void premain_sharding() throws Exception {
    Path runtimeInfoFilePath = tempFolder.newFolder().toPath().resolve("runtime_info_file.txt");

    CommandProcess commandProcess =
        commandExecutor.start(
            Command.of(
                    systemUtil
                        .getJavaCommandCreator()
                        .createJavaCommand(
                            FAKE_TRADEFED_PATH,
                            // Enable sharding by specifying the --shard-count param:
                            ImmutableList.of(
                                "-s", "device1", "-s", "device2", "--shard-count", "2"),
                            ImmutableList.of(
                                String.format(
                                    "-javaagent:%s=%s", AGENT_PATH, runtimeInfoFilePath))))
                .showFullResultInException(true));

    List<XtsTradefedRuntimeInfo> runtimeInfos = new ArrayList<>();
    Instant lastModifiedTime = null;
    while (commandProcess.isAlive()) {
      Sleeper.defaultSleeper().sleep(Duration.ofMillis(200L));
      Optional<XtsTradefedRuntimeInfoFileDetail> fileDetail =
          xtsTradefedRuntimeInfoFileUtil.readInfo(runtimeInfoFilePath, lastModifiedTime);
      if (fileDetail.isPresent()) {
        lastModifiedTime = fileDetail.get().lastModifiedTime();
        runtimeInfos.add(fileDetail.get().runtimeInfo());
      }
    }

    // runtimeInfos should contain three elements:
    // 1. The first is the initial invocation record when tradefed is running.
    // 2. The second is when tradefed completes, so it's an empty XtsTradefedRuntimeInfo with no
    // running invocations.
    // 3. The third is when the agent checks for the tradefed invocation error, and saves the
    // invocation record with error message (and isRunning=false).

    assertThat(runtimeInfos)
        .comparingElementsUsing(
            transforming(
                (XtsTradefedRuntimeInfo runtimeInfo) ->
                    requireNonNull(runtimeInfo).invocations().stream()
                        .map(TradefedInvocation::deviceIds)
                        .collect(toImmutableSet()),
                "has invocations containing device IDs of"))
        // Not using containsExactly since how the test reads the runtime info file periodically
        // might also capture some other intermediary states.
        .containsAtLeast(
            ImmutableSet.of(ImmutableList.of("device1"), ImmutableList.of("device2")),
            ImmutableSet.of(),
            ImmutableSet.of(ImmutableList.of("device1"), ImmutableList.of("device2")))
        .inOrder();

    assertThat(runtimeInfos)
        .comparingElementsUsing(
            transforming(
                (XtsTradefedRuntimeInfo runtimeInfo) ->
                    requireNonNull(runtimeInfo).invocations().stream()
                        .map(TradefedInvocation::status)
                        .collect(toImmutableList()),
                "has invocations containing statuses of"))
        .containsAtLeast(
            ImmutableList.of("init", "init"), ImmutableList.of(), ImmutableList.of("", ""))
        .inOrder();

    assertThat(runtimeInfos)
        .comparingElementsUsing(
            transforming(
                (XtsTradefedRuntimeInfo runtimeInfo) ->
                    requireNonNull(runtimeInfo).invocations().stream()
                        .map(TradefedInvocation::errorMessage)
                        .collect(toImmutableList()),
                "has invocations containing error messages of"))
        .containsAtLeast(
            ImmutableList.of("", ""),
            ImmutableList.of(),
            ImmutableList.of("Fake error message", "Fake error message"))
        .inOrder();

    assertThat(runtimeInfos)
        .comparingElementsUsing(
            transforming(
                (XtsTradefedRuntimeInfo runtimeInfo) ->
                    requireNonNull(runtimeInfo).invocations().stream()
                        .map(TradefedInvocation::isRunning)
                        .map(isRunning -> Boolean.toString(isRunning))
                        .collect(toImmutableList()),
                "has invocations containing isRunning values of"))
        .containsAtLeast(
            ImmutableList.of("true", "true"),
            ImmutableList.of(),
            ImmutableList.of("false", "false"))
        .inOrder();

    CommandResult commandResult = commandProcess.await();
    logger.atInfo().log("Command result: %s", commandResult);
    assertThat(commandResult.exitCode()).isEqualTo(0);
  }

  @Test
  public void premain_invocationThrowsUncheckedException() throws Exception {
    Path runtimeInfoFilePath = tempFolder.newFolder().toPath().resolve("runtime_info_file.txt");

    CommandProcess commandProcess =
        commandExecutor.start(
            Command.of(
                    systemUtil
                        .getJavaCommandCreator()
                        .createJavaCommand(
                            FAKE_TRADEFED_PATH,
                            // Specifying the special device ID to trigger an invocation unchecked
                            // exception:
                            ImmutableList.of(
                                "-s", DEVICE_ID_TO_TRIGGER_UNCHECKED_INVOCATION_EXCEPTION),
                            ImmutableList.of(
                                String.format(
                                    "-javaagent:%s=%s",
                                    AGENT_PATH,
                                    String.format("%s:%s", runtimeInfoFilePath, "true")))))
                .showFullResultInException(true));

    CommandResult commandResult = commandProcess.await();
    assertThat(commandResult.exitCode()).isEqualTo(0);
    Optional<XtsTradefedRuntimeInfoFileDetail> fileDetail =
        xtsTradefedRuntimeInfoFileUtil.readInfo(runtimeInfoFilePath, /* lastModifiedTime= */ null);
    List<TradefedInvocation> invocations = fileDetail.orElseThrow().runtimeInfo().invocations();
    assertThat(invocations).hasSize(1);
    assertThat(invocations.get(0).deviceIds())
        .containsExactly(DEVICE_ID_TO_TRIGGER_UNCHECKED_INVOCATION_EXCEPTION);
    assertThat(invocations.get(0).errorMessage()).contains(INVOCATION_UNCHECKED_EXCEPTION_MESSAGE);
    assertThat(invocations.get(0).isRunning()).isFalse();
  }

  @Test
  public void premain_invocationThrowsCheckedException() throws Exception {
    Path runtimeInfoFilePath = tempFolder.newFolder().toPath().resolve("runtime_info_file.txt");

    CommandProcess commandProcess =
        commandExecutor.start(
            Command.of(
                    systemUtil
                        .getJavaCommandCreator()
                        .createJavaCommand(
                            FAKE_TRADEFED_PATH,
                            // Specifying the special device ID to trigger an invocation checked
                            // exception:
                            ImmutableList.of(
                                "-s", DEVICE_ID_TO_TRIGGER_CHECKED_INVOCATION_EXCEPTION),
                            ImmutableList.of(
                                String.format(
                                    "-javaagent:%s=%s",
                                    AGENT_PATH,
                                    String.format("%s:%s", runtimeInfoFilePath, "true")))))
                .showFullResultInException(true));

    CommandResult commandResult = commandProcess.await();
    assertThat(commandResult.exitCode()).isEqualTo(0);
    Optional<XtsTradefedRuntimeInfoFileDetail> fileDetail =
        xtsTradefedRuntimeInfoFileUtil.readInfo(runtimeInfoFilePath, /* lastModifiedTime= */ null);
    List<TradefedInvocation> invocations = fileDetail.orElseThrow().runtimeInfo().invocations();
    assertThat(invocations).hasSize(1);
    // The device ID list is empty since for this case, the agent won't have access to the
    // invocation context.
    assertThat(invocations.get(0).deviceIds()).isEmpty();
    assertThat(invocations.get(0).errorMessage()).contains(INVOCATION_CHECKED_EXCEPTION_MESSAGE);
    assertThat(invocations.get(0).isRunning()).isFalse();
  }
}
