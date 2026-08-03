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

package com.google.wireless.qa.mobileharness.shared.api.decorator.base;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.protobuf.TextFormat;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.constant.PhaseSkippableDecoratorConstants;
import com.google.wireless.qa.mobileharness.shared.api.decorator.constant.PhaseSkippableDecoratorConstants.ExecutionMode;
import com.google.wireless.qa.mobileharness.shared.api.decorator.util.PhaseSkippableDecoratorUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class PhaseSkippableDecoratorTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Driver decorated;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;

  private Properties properties;

  public static class TestSetupOnlyDecorator extends SetupOnlyDecorator {
    public static final AtomicBoolean setUpCalled = new AtomicBoolean(false);

    public TestSetupOnlyDecorator(Driver decorated, TestInfo testInfo) {
      super(decorated, testInfo);
    }

    @Override
    public SetupResult setUp(SetupContext context)
        throws MobileHarnessException, InterruptedException {
      setUpCalled.set(true);
      JobType state =
          JobType.newBuilder().setDevice("test_device").setDriver("test_driver").build();
      PhaseSkippableDecoratorUtil.setState(context.testInfo(), getDevice().getDeviceId(), state);
      return SetupResult.continueDecorated();
    }
  }

  public static class TestTeardownOnlyDecorator extends TeardownOnlyDecorator {
    public static final AtomicBoolean tearDownCalled = new AtomicBoolean(false);
    public static final AtomicReference<Optional<JobType>> retrievedState =
        new AtomicReference<>(Optional.empty());

    public TestTeardownOnlyDecorator(Driver decorated, TestInfo testInfo) {
      super(decorated, testInfo);
    }

    @Override
    public void tearDown(TeardownContext context)
        throws MobileHarnessException, InterruptedException {
      tearDownCalled.set(true);
      try {
        retrievedState.set(
            PhaseSkippableDecoratorUtil.getState(
                context.testInfo(), getDevice().getDeviceId(), JobType.getDefaultInstance()));
      } catch (TextFormat.ParseException e) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR, "Failed to parse text proto", e);
      }
    }
  }

  public static class TestPhaseSkippableDecorator
      extends PhaseSkippableDecorator<TestSetupOnlyDecorator, TestTeardownOnlyDecorator> {
    public TestPhaseSkippableDecorator(Driver decorated, TestInfo testInfo)
        throws MobileHarnessException {
      super(decorated, testInfo);
    }
  }

  @Before
  public void setUp() {
    TestSetupOnlyDecorator.setUpCalled.set(false);
    TestTeardownOnlyDecorator.tearDownCalled.set(false);
    TestTeardownOnlyDecorator.retrievedState.set(Optional.empty());

    properties = new Properties(new Timing());
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(new Log(new Timing()));
    when(jobInfo.properties()).thenReturn(properties);
    when(testInfo.properties()).thenReturn(properties);
    Device mockDevice = Mockito.mock(Device.class);
    when(mockDevice.getDeviceId()).thenReturn("device_id_123");
    when(decorated.getDevice()).thenReturn(mockDevice);
  }

  @Test
  public void run_fullMode_executesBothSetupAndTeardown() throws Exception {
    TestPhaseSkippableDecorator decorator = new TestPhaseSkippableDecorator(decorated, testInfo);
    decorator.run(testInfo);

    assertThat(TestSetupOnlyDecorator.setUpCalled.get()).isTrue();
    verify(decorated).run(testInfo);
    assertThat(TestTeardownOnlyDecorator.tearDownCalled.get()).isTrue();
  }

  @Test
  public void run_setupOnlyMode_skipsTeardown() throws Exception {
    properties.add(
        PhaseSkippableDecoratorConstants.PROP_EXECUTION_MODE, ExecutionMode.SETUP_ONLY.name());
    TestPhaseSkippableDecorator decorator = new TestPhaseSkippableDecorator(decorated, testInfo);

    decorator.run(testInfo);

    assertThat(TestSetupOnlyDecorator.setUpCalled.get()).isTrue();
    verify(decorated).run(testInfo);
    assertThat(TestTeardownOnlyDecorator.tearDownCalled.get()).isFalse();
  }

  @Test
  public void run_teardownOnlyMode_skipsSetup() throws Exception {
    properties.add(
        PhaseSkippableDecoratorConstants.PROP_EXECUTION_MODE, ExecutionMode.TEARDOWN_ONLY.name());
    TestPhaseSkippableDecorator decorator = new TestPhaseSkippableDecorator(decorated, testInfo);

    decorator.run(testInfo);

    assertThat(TestSetupOnlyDecorator.setUpCalled.get()).isFalse();
    verify(decorated).run(testInfo);
    assertThat(TestTeardownOnlyDecorator.tearDownCalled.get()).isTrue();
  }

  @Test
  public void run_explicitFullMode_executesAll() throws Exception {
    properties.add(PhaseSkippableDecoratorConstants.PROP_EXECUTION_MODE, ExecutionMode.FULL.name());
    TestPhaseSkippableDecorator decorator = new TestPhaseSkippableDecorator(decorated, testInfo);

    decorator.run(testInfo);

    assertThat(TestSetupOnlyDecorator.setUpCalled.get()).isTrue();
    verify(decorated).run(testInfo);
    assertThat(TestTeardownOnlyDecorator.tearDownCalled.get()).isTrue();
  }

  @Test
  public void run_invalidMode_throwsException() {
    properties.add(PhaseSkippableDecoratorConstants.PROP_EXECUTION_MODE, "INVALID_MODE");

    assertThrows(
        MobileHarnessException.class, () -> new TestPhaseSkippableDecorator(decorated, testInfo));
  }

  @Test
  public void statePropagation_setupToTeardown_success() throws Exception {
    TestPhaseSkippableDecorator decorator = new TestPhaseSkippableDecorator(decorated, testInfo);
    decorator.run(testInfo);

    assertThat(TestSetupOnlyDecorator.setUpCalled.get()).isTrue();
    assertThat(TestTeardownOnlyDecorator.tearDownCalled.get()).isTrue();
    assertThat(TestTeardownOnlyDecorator.retrievedState.get()).isPresent();
    assertThat(TestTeardownOnlyDecorator.retrievedState.get().get().getDevice())
        .isEqualTo("test_device");
    assertThat(TestTeardownOnlyDecorator.retrievedState.get().get().getDriver())
        .isEqualTo("test_driver");
  }
}
