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

package com.google.devtools.deviceinfra.shared.subprocess.agent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.extensions.proto.ProtoTruth.protos;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.truth.Correspondence;
import com.google.common.truth.extensions.proto.FieldScope;
import com.google.common.truth.extensions.proto.FieldScopes;
import com.google.devtools.deviceinfra.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecord;
import com.google.devtools.deviceinfra.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecord.CommandEndedEvent;
import com.google.devtools.deviceinfra.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecord.CommandStartedEvent;
import com.google.devtools.deviceinfra.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecord.CommandStartedEvent.CommandStartFailure;
import com.google.devtools.deviceinfra.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecord.CommandStartedEvent.CommandStartSuccess;
import com.google.devtools.deviceinfra.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecords;
import com.google.devtools.deviceinfra.shared.commandhistory.renderer.CommandHistoryRenderer;
import com.google.devtools.deviceinfra.shared.subprocess.proto.AgentConfigProto.UdclusterAgentConfig;
import com.google.devtools.deviceinfra.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UdclusterAgentTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  private static final String FAKE_PROGRAM_DATA_PATH =
      "javatests/com/google/devtools/deviceinfra/shared/"
          + "subprocess/agent/fake_program_deploy.jar";
  private static final String STRESS_TEST_PROGRAM_DATA_PATH =
      "javatests/com/google/devtools/deviceinfra/shared/"
          + "subprocess/agent/stress_test_program_deploy.jar";

  private static final String AGENT_DATA_PATH =
      "java/com/google/devtools/deviceinfra/shared/"
          + "subprocess/agent/udcluster_agent_deploy.jar";
  private static final String AGENT_BOOT_CLASS_DATA_PATH =
      "java/com/google/devtools/deviceinfra/shared/"
          + "subprocess/agent/udcluster_agent_boot_class_deploy.jar";

  private static final FieldScope LOCAL_COMMAND_RECORD_FIELD_SCOPE =
      FieldScopes.ignoringFieldDescriptors(
          LocalCommandRecords.getDescriptor()
              .findFieldByNumber(LocalCommandRecords.LOCAL_START_ELAPSED_TIME_FIELD_NUMBER),
          LocalCommandRecords.getDescriptor()
              .findFieldByNumber(LocalCommandRecords.LOCAL_START_TIMESTAMP_FIELD_NUMBER),
          LocalCommandRecord.getDescriptor()
              .findFieldByNumber(LocalCommandRecord.LOCAL_ELAPSED_TIME_FIELD_NUMBER),
          LocalCommandRecord.getDescriptor()
              .findFieldByNumber(LocalCommandRecord.LOCAL_TIMESTAMP_FIELD_NUMBER),
          CommandStartedEvent.getDescriptor()
              .findFieldByNumber(CommandStartedEvent.INVOCATION_INFO_FIELD_NUMBER),
          CommandStartSuccess.getDescriptor()
              .findFieldByNumber(CommandStartSuccess.PID_FIELD_NUMBER),
          CommandStartFailure.getDescriptor()
              .findFieldByNumber(CommandStartFailure.EXCEPTION_FIELD_NUMBER));

  @Test
  public void test() throws Exception {
    String binaryFilePath = RunfilesUtil.getRunfilesLocation(FAKE_PROGRAM_DATA_PATH);
    String commandHistoryFilePath = tmpFolder.newFile("command_history.pb").getAbsolutePath();

    CommandResult commandResult =
        runBinaryWithAgent(
            binaryFilePath, commandHistoryFilePath, ImmutableList.of("fake_arg_1", "fake_arg_2"));

    LocalCommandRecords commandRecords =
        LocalCommandRecords.parseFrom(
            Files.readAllBytes(Path.of(commandHistoryFilePath)), ExtensionRegistry.newInstance());

    assertWithMessage("Command result: [%s]", commandResult.toStringWithoutTruncation())
        .about(protos())
        .that(commandRecords)
        .withPartialScope(LOCAL_COMMAND_RECORD_FIELD_SCOPE)
        .isEqualTo(
            LocalCommandRecords.newBuilder()
                .addRecord(
                    LocalCommandRecord.newBuilder()
                        .setLocalCommandSequenceNumber(0L)
                        .setCommandStartedEvent(
                            CommandStartedEvent.newBuilder()
                                .addCommand("echo")
                                .addCommand("fake_arg_1")
                                .setStartSuccess(CommandStartSuccess.getDefaultInstance())))
                .addRecord(
                    LocalCommandRecord.newBuilder()
                        .setLocalCommandSequenceNumber(0L)
                        .setCommandEndedEvent(CommandEndedEvent.newBuilder().setExitCode(0)))
                .addRecord(
                    LocalCommandRecord.newBuilder()
                        .setLocalCommandSequenceNumber(1L)
                        .setCommandStartedEvent(
                            CommandStartedEvent.newBuilder()
                                .addCommand("echo")
                                .addCommand("fake_arg_2")
                                .setStartSuccess(CommandStartSuccess.getDefaultInstance())))
                .addRecord(
                    LocalCommandRecord.newBuilder()
                        .setLocalCommandSequenceNumber(1L)
                        .setCommandEndedEvent(CommandEndedEvent.newBuilder().setExitCode(0)))
                .addRecord(
                    LocalCommandRecord.newBuilder()
                        .setLocalCommandSequenceNumber(2L)
                        .setCommandStartedEvent(
                            CommandStartedEvent.newBuilder()
                                .addCommand("wrong_command")
                                .setStartFailure(CommandStartFailure.getDefaultInstance())))
                .build());

    assertThat(
            Splitter.on('\n')
                .split(
                    new CommandHistoryRenderer()
                        .renderCommandHistory(commandRecords, ImmutableList.of())))
        .comparingElementsUsing(
            Correspondence.<String, String>from(
                (actual, expected) -> requireNonNull(actual).contains(requireNonNull(expected)),
                "contains"))
        .containsExactly(
            "#\ttid\tpid\tstart\tend\ttime(s)\tcode\tcmd",
            "0\techo fake_arg_1",
            "0\techo fake_arg_2",
            "na\twrong_command")
        .inOrder();
  }

  @Test
  public void stressTest() throws Exception {
    int subprocessNum = 500;
    String sleepTimeArgument = "10s";

    String binaryFilePath = RunfilesUtil.getRunfilesLocation(STRESS_TEST_PROGRAM_DATA_PATH);
    String commandHistoryFilePath = tmpFolder.newFile("command_history.pb").getAbsolutePath();

    CommandResult commandResult =
        runBinaryWithAgent(
            binaryFilePath,
            commandHistoryFilePath,
            ImmutableList.of(Integer.toString(subprocessNum), sleepTimeArgument));

    LocalCommandRecords commandRecords =
        LocalCommandRecords.parseFrom(
            Files.readAllBytes(Path.of(commandHistoryFilePath)), ExtensionRegistry.newInstance());

    LocalCommandRecords.Builder expected = LocalCommandRecords.newBuilder();
    for (int i = 0; i < subprocessNum; i++) {
      expected
          .addRecord(
              LocalCommandRecord.newBuilder()
                  .setLocalCommandSequenceNumber(i)
                  .setCommandStartedEvent(
                      CommandStartedEvent.newBuilder()
                          .addCommand("sleep")
                          .addCommand(sleepTimeArgument)
                          .setStartSuccess(CommandStartSuccess.getDefaultInstance())))
          .addRecord(
              LocalCommandRecord.newBuilder()
                  .setLocalCommandSequenceNumber(i)
                  .setCommandEndedEvent(CommandEndedEvent.newBuilder().setExitCode(0)));
    }

    assertWithMessage("Command result: [%s]", commandResult.toStringWithoutTruncation())
        .about(protos())
        .that(commandRecords)
        .withPartialScope(LOCAL_COMMAND_RECORD_FIELD_SCOPE)
        .ignoringRepeatedFieldOrderOfFieldDescriptors(
            LocalCommandRecords.getDescriptor()
                .findFieldByNumber(LocalCommandRecords.RECORD_FIELD_NUMBER))
        .isEqualTo(expected.build());
  }

  private CommandResult runBinaryWithAgent(
      String binaryFilePath, String commandHistoryFilePath, List<String> arguments)
      throws IOException, CommandException, InterruptedException {
    String agentFilePath = RunfilesUtil.getRunfilesLocation(AGENT_DATA_PATH);
    String agentBootClassFilePath = RunfilesUtil.getRunfilesLocation(AGENT_BOOT_CLASS_DATA_PATH);

    String agentConfigFilePath = tmpFolder.newFile("agent_config.textproto").getAbsolutePath();

    UdclusterAgentConfig agentConfig =
        UdclusterAgentConfig.newBuilder()
            .setEnableCommandHistory(true)
            .setCommandHistoryFilePath(commandHistoryFilePath)
            .build();
    Files.write(
        Path.of(agentConfigFilePath),
        ImmutableList.of(TextFormat.printer().printToString(agentConfig)));

    CommandExecutor commandExecutor = new CommandExecutor();
    Command command =
        Command.of(
                new SystemUtil()
                    .getJavaCommandCreator()
                    .createJavaCommand(
                        binaryFilePath,
                        arguments,
                        ImmutableList.of(
                            String.format("-javaagent:%s=%s", agentFilePath, agentConfigFilePath),
                            "-Xbootclasspath/a:" + agentBootClassFilePath)))
            .onStdout(LineCallback.does(stdout -> logger.atInfo().log("stdout %s", stdout)))
            .onStderr(LineCallback.does(stderr -> logger.atInfo().log("stderr %s", stderr)))
            .redirectStderr(false)
            .showFullResultInException(true);
    logger.atInfo().log("command=%s", command);

    return commandExecutor.exec(command);
  }
}
