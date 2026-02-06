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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.PythonVersionCheckDecoratorSpec;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link PythonVersionCheckDecorator}. */
@RunWith(JUnit4.class)
public class PythonVersionCheckDecoratorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Driver decoratedDriver;
  @Mock private Device device;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Result result;
  @Mock private CommandExecutor commandExecutor;
  @Mock private TestInfo rootTestInfo;
  @Mock private Result rootResult;

  @Captor private ArgumentCaptor<MobileHarnessException> exceptionCaptor;

  private PythonVersionCheckDecorator decorator;

  @Before
  public void setUp() throws Exception {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn("fake_device_id");
    decorator = new PythonVersionCheckDecorator(decoratedDriver, testInfo, commandExecutor);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.resultWithCause()).thenReturn(result);
    when(testInfo.getRootTest()).thenReturn(rootTestInfo);
    when(rootTestInfo.resultWithCause()).thenReturn(rootResult);
  }

  @Test
  public void run_noRequirement_callsDecoratedDriver() throws Exception {
    PythonVersionCheckDecoratorSpec spec = PythonVersionCheckDecoratorSpec.getDefaultInstance();
    when(jobInfo.combinedSpec(any(PythonVersionCheckDecorator.class), anyString()))
        .thenReturn(spec);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_meetMinVersion_callsDecoratedDriver() throws Exception {
    PythonVersionCheckDecoratorSpec spec =
        PythonVersionCheckDecoratorSpec.newBuilder().setMinPythonVersion("3.8.0").build();
    when(jobInfo.combinedSpec(any(PythonVersionCheckDecorator.class), anyString()))
        .thenReturn(spec);
    when(commandExecutor.run(any(Command.class))).thenReturn("Python 3.10.12");

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_lowerThanMinVersion_setsResultToError() throws Exception {
    PythonVersionCheckDecoratorSpec spec =
        PythonVersionCheckDecoratorSpec.newBuilder().setMinPythonVersion("3.11.0").build();
    when(jobInfo.combinedSpec(any(PythonVersionCheckDecorator.class), anyString()))
        .thenReturn(spec);
    when(commandExecutor.run(any(Command.class))).thenReturn("Python 3.10.12");

    decorator.run(testInfo);

    verify(result).setNonPassing(eq(TestResult.ERROR), exceptionCaptor.capture());
    assertThat(exceptionCaptor.getValue().getErrorId())
        .isEqualTo(ExtErrorId.PYTHON_VERSION_CHECK_DECORATOR_VERSION_NOT_MATCH);
    verify(rootResult).setNonPassing(eq(TestResult.ERROR), eq(exceptionCaptor.getValue()));
  }

  @Test
  public void run_meetSpecificVersion_callsDecoratedDriver() throws Exception {
    PythonVersionCheckDecoratorSpec spec =
        PythonVersionCheckDecoratorSpec.newBuilder().setPythonVersion("3.10.12").build();
    when(jobInfo.combinedSpec(any(PythonVersionCheckDecorator.class), anyString()))
        .thenReturn(spec);
    when(commandExecutor.run(any(Command.class))).thenReturn("Python 3.10.12");

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_notMeetSpecificVersion_setsResultToError() throws Exception {
    PythonVersionCheckDecoratorSpec spec =
        PythonVersionCheckDecoratorSpec.newBuilder().setPythonVersion("3.10.11").build();
    when(jobInfo.combinedSpec(any(PythonVersionCheckDecorator.class), anyString()))
        .thenReturn(spec);
    when(commandExecutor.run(any(Command.class))).thenReturn("Python 3.10.12");

    decorator.run(testInfo);

    verify(result).setNonPassing(eq(TestResult.ERROR), exceptionCaptor.capture());
    assertThat(exceptionCaptor.getValue().getErrorId())
        .isEqualTo(ExtErrorId.PYTHON_VERSION_CHECK_DECORATOR_VERSION_NOT_MATCH);
    verify(rootResult).setNonPassing(eq(TestResult.ERROR), eq(exceptionCaptor.getValue()));
  }

  @Test
  public void run_meetAll_callsDecoratedDriver() throws Exception {
    // Meets min version but not specific
    PythonVersionCheckDecoratorSpec spec =
        PythonVersionCheckDecoratorSpec.newBuilder()
            .setMinPythonVersion("3.8.0")
            .setPythonVersion("3.9.0")
            .build();
    when(jobInfo.combinedSpec(any(PythonVersionCheckDecorator.class), anyString()))
        .thenReturn(spec);
    when(commandExecutor.run(any(Command.class))).thenReturn("Python 3.9.0");

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_meetSpecificButNotMin_setsResultToError() throws Exception {
    // Meets specific version but lower than min
    PythonVersionCheckDecoratorSpec spec =
        PythonVersionCheckDecoratorSpec.newBuilder()
            .setMinPythonVersion("3.11.0")
            .setPythonVersion("3.10.12")
            .build();
    when(jobInfo.combinedSpec(any(PythonVersionCheckDecorator.class), anyString()))
        .thenReturn(spec);
    when(commandExecutor.run(any(Command.class))).thenReturn("Python 3.10.12");

    decorator.run(testInfo);

    verify(result).setNonPassing(eq(TestResult.ERROR), exceptionCaptor.capture());
    assertThat(exceptionCaptor.getValue().getErrorId())
        .isEqualTo(ExtErrorId.PYTHON_VERSION_CHECK_DECORATOR_VERSION_NOT_MATCH);
    verify(rootResult).setNonPassing(eq(TestResult.ERROR), eq(exceptionCaptor.getValue()));
  }

  @Test
  public void run_meetMinButNotSpecific_setsResultToError() throws Exception {
    PythonVersionCheckDecoratorSpec spec =
        PythonVersionCheckDecoratorSpec.newBuilder()
            .setMinPythonVersion("3.10.12")
            .setPythonVersion("3.12.0")
            .build();
    when(jobInfo.combinedSpec(any(PythonVersionCheckDecorator.class), anyString()))
        .thenReturn(spec);
    when(commandExecutor.run(any(Command.class))).thenReturn("Python 3.11.0");

    decorator.run(testInfo);

    verify(result).setNonPassing(eq(TestResult.ERROR), exceptionCaptor.capture());
    assertThat(exceptionCaptor.getValue().getErrorId())
        .isEqualTo(ExtErrorId.PYTHON_VERSION_CHECK_DECORATOR_VERSION_NOT_MATCH);
    verify(rootResult).setNonPassing(eq(TestResult.ERROR), eq(exceptionCaptor.getValue()));
  }

  @Test
  public void run_parseError_setsResultToError() throws Exception {
    PythonVersionCheckDecoratorSpec spec =
        PythonVersionCheckDecoratorSpec.newBuilder().setPythonVersion("3.10.11").build();
    when(jobInfo.combinedSpec(any(PythonVersionCheckDecorator.class), anyString()))
        .thenReturn(spec);
    when(commandExecutor.run(any(Command.class))).thenReturn("Invalid output");

    decorator.run(testInfo);

    verify(result).setNonPassing(eq(TestResult.ERROR), exceptionCaptor.capture());
    assertThat(exceptionCaptor.getValue().getErrorId())
        .isEqualTo(ExtErrorId.PYTHON_VERSION_CHECK_DECORATOR_PARSE_VERSION_ERROR);
    verify(rootResult).setNonPassing(eq(TestResult.ERROR), eq(exceptionCaptor.getValue()));
  }
}
