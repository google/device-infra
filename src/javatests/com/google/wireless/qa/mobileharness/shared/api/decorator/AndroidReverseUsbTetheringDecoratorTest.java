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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.devtools.mobileharness.platform.android.connectivity.UsbReverseTetheringUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidRealDevice;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidReverseUsbTetheringDecoratorSpec;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidReverseUsbTetheringDecorator}. */
@RunWith(JUnit4.class)
public class AndroidReverseUsbTetheringDecoratorTest {

  private static final String DEVICE_ID = "44121FDKD00042";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private UsbReverseTetheringUtil usbReverseTetheringUtil;
  @Mock private Driver decoratedDriver;
  @Mock private AndroidRealDevice device;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Result result;
  @Mock private Warnings warnings;

  private Log log;
  private AndroidReverseUsbTetheringDecorator decorator;

  @Before
  public void setUp() {
    log = new Log(new Timing());
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.log()).thenReturn(log);
    when(testInfo.resultWithCause()).thenReturn(result);
    when(testInfo.warnings()).thenReturn(warnings);
    when(usbReverseTetheringUtil.isHostSupported()).thenReturn(true);
    decorator =
        new AndroidReverseUsbTetheringDecorator(decoratedDriver, testInfo, usbReverseTetheringUtil);
  }

  @Test
  public void run_defaultSpec_enablesRunsDriverAndDisablesInTeardown() throws Exception {
    when(usbReverseTetheringUtil.isDeviceSupported(DEVICE_ID)).thenReturn(true);
    when(jobInfo.combinedSpec(decorator, DEVICE_ID))
        .thenReturn(AndroidReverseUsbTetheringDecoratorSpec.getDefaultInstance());

    decorator.run(testInfo);

    InOrder inOrder = inOrder(usbReverseTetheringUtil, decoratedDriver);
    inOrder
        .verify(usbReverseTetheringUtil)
        .enableUsbReverseTethering(device, true, Duration.ofSeconds(30), null, log);
    inOrder.verify(decoratedDriver).run(testInfo);
    inOrder.verify(usbReverseTetheringUtil).disableUsbReverseTethering(device, log);
    verify(usbReverseTetheringUtil, never()).clearStateProperties(device);
  }

  @Test
  public void run_customSpec_passesCustomOptionsAndDisablesInTeardown() throws Exception {
    when(usbReverseTetheringUtil.isDeviceSupported(DEVICE_ID)).thenReturn(true);
    AndroidReverseUsbTetheringDecoratorSpec spec =
        AndroidReverseUsbTetheringDecoratorSpec.newBuilder()
            .setPersistAcrossReboot(false)
            .setConnectivityTimeoutSec(45)
            .setConnectivityCheckHost("dns.google")
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    decorator.run(testInfo);

    verify(usbReverseTetheringUtil)
        .enableUsbReverseTethering(device, false, Duration.ofSeconds(45), "dns.google", log);
    verify(decoratedDriver).run(testInfo);
    verify(usbReverseTetheringUtil).disableUsbReverseTethering(device, log);
    verify(usbReverseTetheringUtil, never()).clearStateProperties(device);
  }

  @Test
  public void run_overTcpDevice_throwsAndSkipsDisable() throws Exception {
    when(device.getDeviceId()).thenReturn("127.0.0.1:5555");
    when(jobInfo.combinedSpec(decorator, "127.0.0.1:5555"))
        .thenReturn(AndroidReverseUsbTetheringDecoratorSpec.getDefaultInstance());

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_OVER_TCP_NOT_SUPPORTED);
    verify(usbReverseTetheringUtil, never())
        .enableUsbReverseTethering(any(), anyBoolean(), any(), any(), any());
    verify(usbReverseTetheringUtil, never()).disableUsbReverseTethering(any(), any());
    verify(decoratedDriver, never()).run(any());
  }

  @Test
  public void run_hostNotSupported_throwsAndSkipsDisable() throws Exception {
    when(usbReverseTetheringUtil.isHostSupported()).thenReturn(false);
    when(jobInfo.combinedSpec(decorator, DEVICE_ID))
        .thenReturn(AndroidReverseUsbTetheringDecoratorSpec.getDefaultInstance());

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_HOST_NOT_SUPPORTED);
    verify(usbReverseTetheringUtil, never())
        .enableUsbReverseTethering(any(), anyBoolean(), any(), any(), any());
    verify(usbReverseTetheringUtil, never()).disableUsbReverseTethering(any(), any());
  }

  @Test
  public void run_deviceNotSupported_throwsAndSkipsDisable() throws Exception {
    when(usbReverseTetheringUtil.isDeviceSupported(DEVICE_ID)).thenReturn(false);
    when(jobInfo.combinedSpec(decorator, DEVICE_ID))
        .thenReturn(AndroidReverseUsbTetheringDecoratorSpec.getDefaultInstance());

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(thrown.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_DEVICE_NOT_SUPPORTED);
    verify(usbReverseTetheringUtil, never())
        .enableUsbReverseTethering(any(), anyBoolean(), any(), any(), any());
    verify(usbReverseTetheringUtil, never()).disableUsbReverseTethering(any(), any());
  }

  @Test
  public void run_enableConnectivityFails_stillCleansUpInTeardown() throws Exception {
    when(usbReverseTetheringUtil.isDeviceSupported(DEVICE_ID)).thenReturn(true);
    when(jobInfo.combinedSpec(decorator, DEVICE_ID))
        .thenReturn(AndroidReverseUsbTetheringDecoratorSpec.getDefaultInstance());
    MobileHarnessException enableFailure =
        new MobileHarnessException(
            AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_CONNECTIVITY_VERIFICATION_FAILED,
            "ping failed");
    doThrow(enableFailure)
        .when(usbReverseTetheringUtil)
        .enableUsbReverseTethering(eq(device), eq(true), any(), eq(null), eq(log));

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(thrown).isSameInstanceAs(enableFailure);
    verify(usbReverseTetheringUtil).disableUsbReverseTethering(device, log);
    verify(decoratedDriver, never()).run(any());
  }

  @Test
  public void run_driverAndTeardownBothFail_suppressesTeardownException() throws Exception {
    when(usbReverseTetheringUtil.isDeviceSupported(DEVICE_ID)).thenReturn(true);
    when(jobInfo.combinedSpec(decorator, DEVICE_ID))
        .thenReturn(AndroidReverseUsbTetheringDecoratorSpec.getDefaultInstance());
    MobileHarnessException driverError =
        new MobileHarnessException(AndroidErrorId.ANDROID_ADB_UTIL_CMD_ERROR, "driver failed");
    MobileHarnessException teardownError =
        new MobileHarnessException(
            AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_DISABLE_ERROR, "teardown failed");
    doThrow(driverError).when(decoratedDriver).run(testInfo);
    doThrow(teardownError).when(usbReverseTetheringUtil).disableUsbReverseTethering(device, log);

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));

    assertThat(thrown).isSameInstanceAs(driverError);
    assertThat(thrown.getSuppressed()).asList().containsExactly(teardownError);
    verify(usbReverseTetheringUtil, never()).clearStateProperties(device);
  }
}
