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
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Correspondence.BinaryPredicate;
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
                            ImmutableList.of("device1", "device2"),
                            ImmutableList.of(
                                String.format(
                                    "-javaagent:%s=%s", AGENT_PATH, runtimeInfoFilePath))))
                .redirectStderr(false)
                .showFullResultInException(true));

    List<XtsTradefedRuntimeInfo> runtimeInfos = new ArrayList<>();
    Instant lastModifiedTime = null;
    while (commandProcess.isAlive()) {
      Sleeper.defaultSleeper().sleep(Duration.ofSeconds(1L));

      Optional<XtsTradefedRuntimeInfoFileDetail> fileDetail =
          xtsTradefedRuntimeInfoFileUtil.readInfo(runtimeInfoFilePath, lastModifiedTime);
      if (fileDetail.isPresent()) {
        lastModifiedTime = fileDetail.get().lastModifiedTime();
        runtimeInfos.add(fileDetail.get().runtimeInfo());
      }
    }

    Correspondence<XtsTradefedRuntimeInfo, List<List<String>>> correspondence =
        Correspondence.from(
            (BinaryPredicate<XtsTradefedRuntimeInfo, List<List<String>>>)
                (actual, expected) ->
                    actual.invocations().stream()
                        .map(TradefedInvocation::deviceIds)
                        .collect(toImmutableList())
                        .equals(expected),
            "has invocations containing devices that");
    assertThat(runtimeInfos)
        .comparingElementsUsing(correspondence)
        .contains(ImmutableList.of(ImmutableList.of("device1", "device2")));
    assertThat(runtimeInfos).comparingElementsUsing(correspondence).contains(ImmutableList.of());

    CommandResult commandResult = commandProcess.await();
    assertThat(commandResult.exitCode()).isEqualTo(0);
  }
}
