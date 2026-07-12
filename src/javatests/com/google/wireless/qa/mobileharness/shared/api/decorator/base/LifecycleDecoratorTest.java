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
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.junit.rule.CaptureLogs;
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
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class LifecycleDecoratorTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Rule
  public final CaptureLogs captureLogs = new CaptureLogs(LifecycleDecorator.class.getName(), true);

  @Mock private Driver decorated;
  @Mock private TestInfo testInfo;
  @Mock private Device device;

  private LifecycleDecorator decorator;
  private String logPrefix;

  @Before
  public void setUp() {
    when(decorated.getDevice()).thenReturn(device);
    when(testInfo.log()).thenReturn(new Log(new Timing()));
    decorator =
        mock(
            LifecycleDecorator.class,
            withSettings().useConstructor(decorated, testInfo).defaultAnswer(CALLS_REAL_METHODS));
    logPrefix = "Decorator [" + decorator.getClass().getSimpleName() + "] ";
  }

  @Test
  public void run_normalExecution_executesInOrderAndLogs() throws Exception {
    decorator.run(testInfo);

    InOrder inOrder = inOrder(decorator, decorated);
    inOrder.verify(decorator).setUp(testInfo);
    inOrder.verify(decorated).run(testInfo);
    inOrder.verify(decorator).tearDown(testInfo);

    assertThat(captureLogs.getLogs()).contains(logPrefix + "setup starting.");
    assertThat(captureLogs.getLogs()).contains(logPrefix + "setup finished.");
    assertThat(captureLogs.getLogs()).contains(logPrefix + "teardown starting.");
    assertThat(captureLogs.getLogs()).contains(logPrefix + "teardown finished.");
  }

  @Test
  public void run_innerDriverThrowsException_runsTeardownAndBubblesExceptionAndLogs()
      throws Exception {
    MobileHarnessException exception =
        new MobileHarnessException(BasicErrorId.JOB_TIMEOUT, "Test failed");
    doThrow(exception).when(decorated).run(testInfo);

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
    assertThat(thrown).isSameInstanceAs(exception);

    verify(decorator).setUp(testInfo);
    verify(decorator).tearDown(testInfo);

    assertThat(captureLogs.getLogs()).contains(logPrefix + "setup finished.");
    assertThat(captureLogs.getLogs()).contains(logPrefix + "teardown finished.");
  }

  @Test
  public void run_setupThrowsException_doesNotRunDriverOrTeardownAndLogsFailure() throws Exception {
    MobileHarnessException exception =
        new MobileHarnessException(
            InfraErrorId.CLIENT_LOCAL_MODE_ALLOCATED_DEVICE_NOT_FOUND, "Setup failed");
    doThrow(exception).when(decorator).setUp(testInfo);

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
    assertThat(thrown).isSameInstanceAs(exception);

    verify(decorated, never()).run(testInfo);
    verify(decorator, never()).tearDown(testInfo);

    assertThat(captureLogs.getLogs()).contains(logPrefix + "setup starting.");
    assertThat(captureLogs.getLogs())
        .contains(
            logPrefix
                + "setup finished with failure ["
                + MoreThrowables.shortDebugString(exception)
                + "]");
  }

  @Test
  public void run_teardownThrowsException_bubblesExceptionAndLogsFailure() throws Exception {
    MobileHarnessException exception =
        new MobileHarnessException(BasicErrorId.JOB_TIMEOUT, "Teardown failed");
    doThrow(exception).when(decorator).tearDown(testInfo);

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
    assertThat(thrown).isSameInstanceAs(exception);

    verify(decorator).setUp(testInfo);
    verify(decorated).run(testInfo);
    verify(decorator).tearDown(testInfo);

    assertThat(captureLogs.getLogs()).contains(logPrefix + "teardown starting.");
    assertThat(captureLogs.getLogs())
        .contains(
            logPrefix
                + "teardown finished with failure ["
                + MoreThrowables.shortDebugString(exception)
                + "]");
  }

  @Test
  public void run_driverAndTeardownThrowException_suppressesTeardownExceptionAndLogsFailure()
      throws Exception {
    MobileHarnessException runException =
        new MobileHarnessException(BasicErrorId.JOB_TIMEOUT, "Run failed");
    MobileHarnessException teardownException =
        new MobileHarnessException(BasicErrorId.JOB_TIMEOUT, "Teardown failed");
    doThrow(runException).when(decorated).run(testInfo);
    doThrow(teardownException).when(decorator).tearDown(testInfo);

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
    assertThat(thrown).isSameInstanceAs(runException);
    assertThat(thrown.getSuppressed()).asList().containsExactly(teardownException);

    verify(decorator).setUp(testInfo);
    verify(decorated).run(testInfo);
    verify(decorator).tearDown(testInfo);

    assertThat(captureLogs.getLogs())
        .contains(
            logPrefix
                + "teardown finished with failure ["
                + MoreThrowables.shortDebugString(teardownException)
                + "]");
  }
}
