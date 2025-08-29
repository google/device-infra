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
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.DEFAULT_INVENTORY_SERVICE_HOST;
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.DEFAULT_INVENTORY_SERVICE_PORT;
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.INVENTORY_SERVICE_HOST;
import static com.google.wireless.qa.mobileharness.shared.api.spec.CrosDecoratorSpec.INVENTORY_SERVICE_PORT;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link CrosBaseDecorator}. */
@RunWith(JUnit4.class)
public class CrosBaseDecoratorTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private Driver decoratedDriver;
  @Mock private Device device;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Log testLogger;
  @Mock private Params params;
  @Mock private Api atInfo;

  private TestCrosBaseDecorator decorator;

  private static class TestCrosBaseDecorator extends CrosBaseDecorator {
    TestCrosBaseDecorator(Driver decoratedDriver, TestInfo testInfo) {
      super(decoratedDriver, testInfo);
    }

    @Override
    protected void prepare(TestInfo testInfo) throws MobileHarnessException {
      // Do nothing.
    }

    @Override
    protected void tearDown(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
      // Do nothing.
    }
  }

  @Before
  public void setUp() {
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(testLogger);
    when(jobInfo.params()).thenReturn(params);
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn("test_device:1234");
    when(testLogger.atInfo()).thenReturn(atInfo);
    when(testLogger.atWarning()).thenReturn(atInfo);
    when(atInfo.alsoTo(any(FluentLogger.class))).thenReturn(atInfo);
    when(atInfo.withCause(any(Throwable.class))).thenReturn(atInfo);
    decorator = spy(new TestCrosBaseDecorator(decoratedDriver, testInfo));
  }

  @Test
  public void run_successfulExecution_callsPrepareRunAndTearDown() throws Exception {
    decorator.run(testInfo);

    verify(decorator).prepare(testInfo);
    verify(decoratedDriver).run(testInfo);
    verify(decorator).tearDown(testInfo);
  }

  @Test
  public void run_driverThrowsException() throws Exception {
    doThrow(new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "test"))
        .when(decoratedDriver)
        .run(testInfo);

    assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    verify(decorator).prepare(testInfo);
    verify(decoratedDriver).run(testInfo);
    verify(decorator).tearDown(testInfo);
  }

  @Test
  public void run_prepareThrowsException_callsTearDownAndDoesNotCallRun() throws Exception {
    doThrow(new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "test"))
        .when(decorator)
        .prepare(testInfo);

    assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    verify(decorator).prepare(testInfo);
    verify(decorator).tearDown(testInfo);
    verify(decoratedDriver, never()).run(testInfo);
  }

  @Test
  public void run_tearDownThrowsInterruptedException_propagatesException() throws Exception {
    doThrow(new InterruptedException("test")).when(decorator).tearDown(testInfo);

    assertThrows(InterruptedException.class, () -> decorator.run(testInfo));

    verify(decorator).prepare(testInfo);
    verify(decoratedDriver).run(testInfo);
    verify(decorator).tearDown(testInfo);
    verify(testLogger, never()).atWarning();
  }

  @Test
  public void run_tearDownThrowsMobileHarnessException_isCaughtAndLoggedAsWarning()
      throws Exception {
    doThrow(
            new MobileHarnessException(
                BasicErrorId.NON_MH_EXCEPTION, "test", new InterruptedException("test")))
        .when(decorator)
        .tearDown(testInfo);

    decorator.run(testInfo);

    verify(decorator).prepare(testInfo);
    verify(decoratedDriver).run(testInfo);
    verify(decorator).tearDown(testInfo);
    verify(testLogger).atWarning();
  }

  @Test
  public void run_driverThrowsException_tearDownIsCalled() throws Exception {
    doThrow(new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "test"))
        .when(decoratedDriver)
        .run(testInfo);

    assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    verify(decorator).prepare(testInfo);
    verify(decoratedDriver).run(testInfo);
    verify(decorator).tearDown(testInfo);
  }

  @Test
  public void run_driverAndTearDownThrow_tearDownExceptionPropagates() throws Exception {
    MobileHarnessException driverException =
        new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "driver_exception");
    InterruptedException tearDownException = new InterruptedException("teardown_exception");
    doThrow(driverException).when(decoratedDriver).run(testInfo);
    doThrow(tearDownException).when(decorator).tearDown(testInfo);

    InterruptedException e =
        assertThrows(InterruptedException.class, () -> decorator.run(testInfo));
    assertThat(e).isEqualTo(tearDownException);

    verify(decorator).prepare(testInfo);
    verify(decoratedDriver).run(testInfo);
    verify(decorator).tearDown(testInfo);
  }

  @Test
  public void run_driverAndTearDownMHEThrow_driverExceptionPropagates() throws Exception {
    MobileHarnessException driverException =
        new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "driver_exception");
    MobileHarnessException tearDownException =
        new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "teardown_exception");
    doThrow(driverException).when(decoratedDriver).run(testInfo);
    doThrow(tearDownException).when(decorator).tearDown(testInfo);

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
    assertThat(e).isEqualTo(driverException);

    verify(decorator).prepare(testInfo);
    verify(decoratedDriver).run(testInfo);
    verify(decorator).tearDown(testInfo);
    verify(testLogger).atWarning();
  }

  @Test
  public void getInventoryServiceHostname_paramExists() {
    when(params.get(INVENTORY_SERVICE_HOST, DEFAULT_INVENTORY_SERVICE_HOST)).thenReturn("testhost");
    assertThat(decorator.getInventoryServiceHostname()).isEqualTo("testhost");
  }

  @Test
  public void getInventoryServiceHostname_paramMissing_returnsDefault() {
    when(params.get(INVENTORY_SERVICE_HOST, DEFAULT_INVENTORY_SERVICE_HOST))
        .thenReturn("localhost");
    assertThat(decorator.getInventoryServiceHostname()).isEqualTo("localhost");
  }

  @Test
  public void getInventoryServicePort_paramExists() {
    when(params.getInt(INVENTORY_SERVICE_PORT, DEFAULT_INVENTORY_SERVICE_PORT)).thenReturn(1234);
    assertThat(decorator.getInventoryServicePort()).isEqualTo(1234);
  }

  @Test
  public void getInventoryServicePort_paramMissing_returnsDefault() {
    when(params.getInt(INVENTORY_SERVICE_PORT, DEFAULT_INVENTORY_SERVICE_PORT)).thenReturn(1485);
    assertThat(decorator.getInventoryServicePort()).isEqualTo(1485);
  }

  @Test
  public void getInventoryServiceAddress() {
    when(params.get(INVENTORY_SERVICE_HOST, DEFAULT_INVENTORY_SERVICE_HOST)).thenReturn("testhost");
    when(params.getInt(INVENTORY_SERVICE_PORT, DEFAULT_INVENTORY_SERVICE_PORT)).thenReturn(1234);
    assertThat(decorator.getInventoryServiceAddress()).isEqualTo("testhost:1234");
  }

  @Test
  public void deviceId() {
    assertThat(decorator.deviceId()).isEqualTo("test_device:1234");
  }

  @Test
  public void deviceName_deviceIdWithPort_returnsDeviceIdWithoutPort() {
    assertThat(decorator.deviceName("test_device1")).isEqualTo("test_device1");
    assertThat(decorator.deviceName("test_device2:1234")).isEqualTo("test_device2");
    assertThat(decorator.deviceName("test_device3:5555")).isEqualTo("test_device3");
  }
}
