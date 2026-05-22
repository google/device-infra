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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfoMocker;
import com.google.wireless.qa.mobileharness.shared.proto.spec.Google3File;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.SlateDriverSpec;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link SlateDriver}. */
@RunWith(JUnit4.class)
public final class SlateDriverTest {

  private static final String DEVICE_ID = "device_id";
  private static final String SAMPLE_TARGET = "sample_target";
  private static final String SLATE_BINARY_PATH = "slate_binary_path";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Mock private Device device;
  @Mock private CommandExecutor cmdExecutor;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private CommandResult cmdResult;
  @Mock private JobInfo jobInfo;

  private SlateDriver slateDriver;
  private TestInfo testInfo;
  private SlateDriverSpec spec;

  @Before
  public void setUp() throws Exception {
    testInfo = TestInfoMocker.mockTestInfo();
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.getGenFileDir())
        .thenReturn(tmpFolder.newFolder("gen_file_dir").getAbsolutePath());
    when(testInfo.getTmpFileDir())
        .thenReturn(tmpFolder.newFolder("tmp_file_dir").getAbsolutePath());

    when(device.getDeviceId()).thenReturn(DEVICE_ID);

    when(cmdResult.exitCode()).thenReturn(0);
    when(cmdExecutor.exec(any(Command.class))).thenReturn(cmdResult);

    spec =
        SlateDriverSpec.newBuilder()
            .addTarget(SAMPLE_TARGET)
            .setSlateBinary(Google3File.newBuilder().addOutput(SLATE_BINARY_PATH))
            .build();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);

    slateDriver = new SlateDriver(device, testInfo, cmdExecutor, localFileUtil);
  }

  @Test
  public void run_setsResultToPass() throws Exception {
    // Act
    slateDriver.run(testInfo);

    // Assert
    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.PASS);
  }

  @Test
  public void run_executesCorrectCommand() throws Exception {
    // Act
    slateDriver.run(testInfo);

    // Assert
    ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(cmdExecutor).exec(commandCaptor.capture());
    Command command = commandCaptor.getValue();
    assertThat(command.toString()).contains("--target " + SAMPLE_TARGET);
    assertThat(command.toString()).contains("--device " + DEVICE_ID);
    assertThat(command.toString())
        .contains("--output_base_dir " + Path.of(testInfo.getGenFileDir(), "slate_history"));
  }

  @Test
  public void run_copiesBinaryToWorkDir() throws Exception {
    // Act
    slateDriver.run(testInfo);

    // Assert
    verify(localFileUtil).copyFileOrDir(eq(SLATE_BINARY_PATH), anyString());
  }

  @Test
  public void run_fail_setsResultToFail() throws Exception {
    // Arrange
    when(cmdResult.exitCode()).thenReturn(1);

    // Act
    slateDriver.run(testInfo);

    // Assert
    assertThat(testInfo.resultWithCause().get().type()).isEqualTo(TestResult.FAIL);
  }

  @Test
  public void run_executesCorrectCommand_multipleTargets() throws Exception {
    // Arrange
    spec =
        SlateDriverSpec.newBuilder()
            .addTarget("target1")
            .addTarget("target2")
            .addTarget("target3")
            .setSlateBinary(Google3File.newBuilder().addOutput(SLATE_BINARY_PATH))
            .build();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);

    // Act
    slateDriver.run(testInfo);

    // Assert
    ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
    verify(cmdExecutor).exec(commandCaptor.capture());
    Command command = commandCaptor.getValue();
    assertThat(command.toString()).contains("--target target1 target2 target3");
    assertThat(command.toString()).contains("--device " + DEVICE_ID);
  }

  @Test
  public void run_emptyTargets_throwsException() throws Exception {
    // Arrange
    spec =
        SlateDriverSpec.newBuilder()
            .setSlateBinary(Google3File.newBuilder().addOutput(SLATE_BINARY_PATH))
            .build();
    when(jobInfo.combinedSpec(any())).thenReturn(spec);

    // Act & Assert
    assertThrows(MobileHarnessException.class, () -> slateDriver.run(testInfo));
  }
}
