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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.time.testing.FakeTimeSource;
import com.google.common.util.PathUtil;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.testing.util.UndeclaredOutputs;
import com.google.wireless.qa.mobileharness.shared.api.device.NoOpDevice;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link MoblyGenericTest}. */
@RunWith(JUnit4.class)
public class MoblyGenericTestTest {

  private static final String MOBLY_TEST_TEST_PAR = "mobly_test_test.par";

  private static final String TEST_ID = "test_id";

  private static final String TEST_ID_SUB_DIR = "test_test_id";

  @Captor private ArgumentCaptor<Command> executedCommand;

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public TestName testName = new TestName();

  private TestInfo testInfo;
  private JobInfo jobInfo;
  private JobSetting jobSetting;
  private Clock clock;
  private Timing timing;
  private FakeTimeSource timeSource;
  private File genFileDir;
  private File tmpFileDir;
  private MoblyGenericTest moblyGenericTest;
  private LocalFileUtil localFileUtil;
  private String moblyTestLibPar;

  @Before
  public void setUp() throws Exception {
    timeSource = FakeTimeSource.create();
    clock = timeSource.asClockWithArbitraryZone();
    timing = new Timing(clock);
    timing.start();
    genFileDir = new File(UndeclaredOutputs.getUndeclaredOutputDir(), testName.getMethodName());
    tmpFileDir = Files.createTempDirectory("temp_dir").toFile();
    localFileUtil = new LocalFileUtil();
    jobSetting =
        JobSetting.newBuilder()
            .setGenFileDir(genFileDir.getPath())
            .setTmpFileDir(tmpFileDir.getPath())
            .build();
    createFakeMoblyParFile();
  }

  @Test
  public void runWithCompositeDevicePass() throws Exception {
    File file = new File(PathUtil.join(tmpFileDir.getPath(), MOBLY_TEST_TEST_PAR));
    file.getParentFile().mkdirs();
    file.createNewFile();

    setupTestInfo("test_pass");
    CommandExecutor mockCommandExecutor = getMockCommandExecutor();
    moblyGenericTest =
        new MoblyGenericTest(new NoOpDevice("device_name"), testInfo, mockCommandExecutor, clock);

    moblyGenericTest.run(testInfo);

    verify(mockCommandExecutor).start(executedCommand.capture());
    assertThat(executedCommand.getAllValues()).hasSize(1);

    setupTestInfo("test_pass");
    moblyGenericTest.run(testInfo);
    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);

    Path rawLog =
        Path.of(genFileDir.getPath(), TEST_ID_SUB_DIR).resolve("mobly_command_output.log");
    assertThat(localFileUtil.isFileExist(rawLog)).isTrue();
  }

  private void setupTestInfo(String testName) throws Exception {
    JobUser jobUser =
        JobUser.newBuilder().setRunAs("my_run_user").setActualUser("my_actual_user").build();
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                JobType.newBuilder()
                    .setDevice("device_type")
                    .setDriver("EmptyDriver")
                    .addDecorator("EmptyDecorator1")
                    .addDecorator("EmptyDecorator2")
                    .build())
            .setFileUtil(localFileUtil)
            .setJobUser(jobUser)
            .setSetting(jobSetting)
            .setTiming(timing)
            .build();
    jobInfo.params().add(MoblyGenericTest.TEST_SELECTOR_KEY, testName);
    jobInfo.params().add("use_python_sponge_converter", "false");
    jobInfo.files().add(MoblyGenericTest.FILE_TEST_LIB_PAR, moblyTestLibPar);
    testInfo = jobInfo.tests().add(TEST_ID, testName, timing, localFileUtil);
    // Set up the test lib par for the driver
    testInfo.files().add(MoblyGenericTest.FILE_TEST_LIB_PAR, moblyTestLibPar);
  }

  private CommandExecutor getMockCommandExecutor() throws Exception {
    CommandResult mockCommandResult = mock(CommandResult.class);
    CommandProcess mockCommandProcess = mock(CommandProcess.class);
    CommandExecutor mockCommandExecutor = mock(CommandExecutor.class);
    when(mockCommandExecutor.start(any(Command.class))).thenReturn(mockCommandProcess);
    when(mockCommandProcess.await()).thenReturn(mockCommandResult);
    when(mockCommandResult.exitCode()).thenReturn(0);
    return mockCommandExecutor;
  }

  private void createFakeMoblyParFile() throws Exception {
    File file = new File(PathUtil.join(tmpFileDir.getPath(), MOBLY_TEST_TEST_PAR));
    file.getParentFile().mkdirs();
    file.createNewFile();
    moblyTestLibPar = file.getAbsolutePath();
  }
}
