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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AbstractLifecycleDecoratorTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Driver decorated;
  @Mock private TestInfo testInfo;
  @Mock private Device device;

  private TestLifecycleDecorator decorator;

  private static class TestLifecycleDecorator extends AbstractLifecycleDecorator {
    private TestLifecycleDecorator(Driver decorated, TestInfo testInfo) {
      super(decorated, testInfo);
    }

    @Override
    protected void setUp(TestInfo testInfo) throws MobileHarnessException, InterruptedException {}

    @Override
    protected void tearDown(TestInfo testInfo)
        throws MobileHarnessException, InterruptedException {}
  }

  @Before
  public void setUp() {
    when(decorated.getDevice()).thenReturn(device);
    decorator = spy(new TestLifecycleDecorator(decorated, testInfo));
  }

  @Test
  public void run_normalExecution_executesInOrder() throws Exception {
    decorator.run(testInfo);

    InOrder inOrder = inOrder(decorator, decorated);
    inOrder.verify(decorator).setUp(testInfo);
    inOrder.verify(decorated).run(testInfo);
    inOrder.verify(decorator).tearDown(testInfo);
  }

  @Test
  public void run_innerDriverThrowsException_runsTeardownAndBubblesException() throws Exception {
    MobileHarnessException exception =
        new MobileHarnessException(BasicErrorId.JOB_TIMEOUT, "Test failed");
    doThrow(exception).when(decorated).run(testInfo);

    try {
      decorator.run(testInfo);
    } catch (MobileHarnessException e) {
      // Expected
    }

    verify(decorator).setUp(testInfo);
    verify(decorator).tearDown(testInfo);
  }

  @Test
  public void run_setupThrowsException_doesNotRunDriverOrTeardown() throws Exception {
    MobileHarnessException exception =
        new MobileHarnessException(
            InfraErrorId.CLIENT_LOCAL_MODE_ALLOCATED_DEVICE_NOT_FOUND, "Setup failed");
    doThrow(exception).when(decorator).setUp(testInfo);

    try {
      decorator.run(testInfo);
    } catch (MobileHarnessException e) {
      // Expected
    }

    verify(decorated, never()).run(testInfo);
    verify(decorator, never()).tearDown(testInfo);
  }
}
