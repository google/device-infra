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

package com.google.devtools.mobileharness.infra.ats.local;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.shared.util.command.LineCallback.does;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AtsLocalRunnerIntegrationTest {
  private static final String ATS_LOCAL_RUNNER_PATH =
      RunfilesUtil.getRunfilesLocation(
          "java/com/google/devtools/mobileharness/infra/ats/local/ats_local_runner_deploy.jar");
  private static final String OLC_SERVER_BINARY =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/infra/ats/local/"
              + "olc_server_for_local_runner_testing_deploy.jar");
  private static final String TEST_CONFIG_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/infra/ats/local/testdata/test.xml");

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  private CommandProcess serverProcess;

  @After
  public void tearDown() {
    if (serverProcess != null) {
      serverProcess.kill();
    }
  }

  @Test
  public void runTest() throws Exception {
    int serverPort = PortProber.pickUnusedPort();
    String olcServerPublicDir = tmpFolder.newFolder("olc_server_public_dir").toString();
    String olcServerTmpDir = tmpFolder.newFolder("olc_server_tmp_dir").toString();
    ImmutableList<String> deviceInfraServiceFlags =
        ImmutableList.of(
            "--alr_olc_server_path=" + OLC_SERVER_BINARY,
            "--detect_adb_device=false",
            "--no_op_device_num=2",
            "--olc_server_port=" + serverPort,
            "--public_dir=" + olcServerPublicDir,
            "--simplified_log_format=true",
            "--tmp_dir_root=" + olcServerTmpDir);
    StringBuilder stdoutBuilder = new StringBuilder();
    Command command =
        Command.of(
                new SystemUtil()
                    .getJavaCommandCreator()
                    .createJavaCommand(
                        ATS_LOCAL_RUNNER_PATH,
                        ImmutableList.of("--alr_test_config=" + TEST_CONFIG_PATH),
                        ImmutableList.of(
                            "--add-opens=java.base/java.lang=ALL-UNNAMED",
                            String.format(
                                "-DDEVICE_INFRA_SERVICE_FLAGS=%s",
                                Joiner.on(" ").join(deviceInfraServiceFlags)))))
            .onStdout(
                does(
                    stdout -> {
                      System.out.printf("server_stdout %s\n", stdout);
                      stdoutBuilder.append(stdout);
                    }))
            .onStderr(does(stderr -> System.err.printf("server_stderr %s\n", stderr)))
            .redirectStderr(false)
            .showFullResultInException(true);

    serverProcess = new CommandExecutor().start(command);
    serverProcess.await();

    String stdout = stdoutBuilder.toString();
    assertThat(stdout).contains("Job result: PASS");
  }
}
