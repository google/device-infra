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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
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
public final class SetupOnlyDecoratorTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Driver decorated;
  @Mock private TestInfo testInfo;

  private SetupOnlyDecorator decorator;

  private static class MySetupOnlyDecorator extends SetupOnlyDecorator {
    private MySetupOnlyDecorator(Driver decorated, TestInfo testInfo) {
      super(decorated, testInfo);
    }

    @Override
    protected SetupResult setUp(SetupContext context)
        throws MobileHarnessException, InterruptedException {
      return SetupResult.continueDecorated();
    }
  }

  @Before
  public void setUp() {
    when(testInfo.log()).thenReturn(new Log(new Timing()));
    when(decorated.getDevice()).thenReturn(Mockito.mock(Device.class));
  }

  @Test
  public void run_callsSetUpAndDriver() throws Exception {
    decorator = Mockito.spy(new MySetupOnlyDecorator(decorated, testInfo));

    decorator.run(testInfo);

    InOrder inOrder = inOrder(decorator, decorated);
    inOrder.verify(decorator).setUp(any(SetupContext.class));
    inOrder.verify(decorated).run(testInfo);
  }
}
