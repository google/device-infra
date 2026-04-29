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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class StepSkippableLifecycleDecoratorTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Driver decorated;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;

  private Properties properties;
  private StepSkippableLifecycleDecorator decorator;

  private static class TestStepSkippableLifecycleDecorator extends StepSkippableLifecycleDecorator {
    private TestStepSkippableLifecycleDecorator(Driver decorated, TestInfo testInfo)
        throws MobileHarnessException {
      super(decorated, testInfo);
    }

    @Override
    protected void skippableSetUp(TestInfo testInfo)
        throws MobileHarnessException, InterruptedException {}

    @Override
    protected void skippableTearDown(TestInfo testInfo)
        throws MobileHarnessException, InterruptedException {}
  }

  @Before
  public void setUp() {
    properties = new Properties(new Timing());
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(new Log(new Timing()));
    when(jobInfo.properties()).thenReturn(properties);

    // BaseDriver calls getDevice(). checkNotNull(device) means we shouldn't return null.
    when(decorated.getDevice()).thenReturn(Mockito.mock(Device.class));
  }

  private void initDecorator() throws Exception {
    decorator = spy(new TestStepSkippableLifecycleDecorator(decorated, testInfo));
  }

  @Test
  public void run_fullMode_executesAll() throws Exception {
    initDecorator();
    decorator.run(testInfo);

    InOrder inOrder = inOrder(decorator, decorated);
    inOrder.verify(decorator).skippableSetUp(testInfo);
    inOrder.verify(decorated).run(testInfo);
    inOrder.verify(decorator).skippableTearDown(testInfo);
  }

  @Test
  public void run_setupOnlyMode_skipsTeardown() throws Exception {
    properties.add(
        StepSkippableLifecycleDecorator.PROP_EXECUTION_MODE,
        StepSkippableLifecycleDecorator.ExecutionMode.SETUP_ONLY.name());
    initDecorator();

    decorator.run(testInfo);

    verify(decorator).skippableSetUp(testInfo);
    verify(decorated).run(testInfo);
    verify(decorator, never()).skippableTearDown(testInfo);
  }

  @Test
  public void run_teardownOnlyMode_skipsSetup() throws Exception {
    properties.add(
        StepSkippableLifecycleDecorator.PROP_EXECUTION_MODE,
        StepSkippableLifecycleDecorator.ExecutionMode.TEARDOWN_ONLY.name());
    initDecorator();

    decorator.run(testInfo);

    verify(decorator, never()).skippableSetUp(testInfo);
    verify(decorated).run(testInfo);
    verify(decorator).skippableTearDown(testInfo);
  }

  @Test
  public void run_explicitFullMode_executesAll() throws Exception {
    properties.add(
        StepSkippableLifecycleDecorator.PROP_EXECUTION_MODE,
        StepSkippableLifecycleDecorator.ExecutionMode.FULL.name());
    initDecorator();

    decorator.run(testInfo);

    InOrder inOrder = inOrder(decorator, decorated);
    inOrder.verify(decorator).skippableSetUp(testInfo);
    inOrder.verify(decorated).run(testInfo);
    inOrder.verify(decorator).skippableTearDown(testInfo);
  }

  @Test
  public void run_invalidMode_throwsException() throws Exception {
    properties.add(StepSkippableLifecycleDecorator.PROP_EXECUTION_MODE, "INVALID_MODE");

    assertThrows(MobileHarnessException.class, () -> initDecorator());
  }

  @Test
  public void statePersistance_success() throws Exception {
    decorator = new TestStepSkippableLifecycleDecorator(decorated, testInfo);
    decorator.setState(testInfo.jobInfo(), "key1", "value1");

    Optional<String> val = decorator.getState(testInfo.jobInfo(), "key1");
    assertThat(val).hasValue("value1");
    assertThat(
            properties.get(
                "step_skippable_lifecycle_decorator_state_TestStepSkippableLifecycleDecorator_key1"))
        .isEqualTo("value1");
  }
}
