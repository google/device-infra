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

package com.google.devtools.mobileharness.platform.android.app.telephony;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.DeviceInfo;
import com.google.devtools.mobileharness.api.model.lab.out.Properties;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class TelephonyHelperTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final String FAKE_APK_PATH = "/fake/path/to/TelephonyUtility.apk";
  private static final String DEVICE_ID = "device_id";
  private static final String GSM_OPERATOR_PROP = "gsm.sim.operator.numeric";
  private static final String ORANGE_SIM_ID = "20801";
  private static final String THALES_GEMALTO_SIM_ID = "00101";

  @Mock private AndroidInstrumentationUtil mockAndroidInstrumentationUtil;
  @Mock private ApkInstaller mockApkInstaller;
  @Mock private ResUtil mockResUtil;
  @Mock private Device mockDevice;
  @Mock private DeviceInfo mockDeviceInfo;
  @Mock private Properties mockDeviceProperties;

  private TelephonyHelper telephonyHelper;

  @Before
  public void setUp() throws Exception {
    when(mockResUtil.getResourceFile(any(), any())).thenReturn(FAKE_APK_PATH);
    when(mockDevice.getDeviceId()).thenReturn(DEVICE_ID);
    when(mockDevice.info()).thenReturn(mockDeviceInfo);
    when(mockDeviceInfo.properties()).thenReturn(mockDeviceProperties);
    when(mockDeviceProperties.get(GSM_OPERATOR_PROP)).thenReturn(Optional.empty());

    telephonyHelper =
        new TelephonyHelper(mockAndroidInstrumentationUtil, mockApkInstaller, mockResUtil);
  }

  private void setUpInstrumentationOutput(String output) throws Exception {
    when(mockAndroidInstrumentationUtil.instrument(eq(DEVICE_ID), any(), any(), any()))
        .thenReturn(output);
  }

  @Test
  public void updateSimDimensions_notSimReady() throws Exception {
    setUpInstrumentationOutput("INSTRUMENTATION_STATUS: sim_state=1\n");

    telephonyHelper.updateSimDimensions(mockDevice);

    verify(mockDevice, never()).updateDimension(any(Dimension.Name.class), any());
  }

  @Test
  public void updateSimDimensions_simReady() throws Exception {
    setUpInstrumentationOutput("INSTRUMENTATION_STATUS: sim_state=5\n");

    telephonyHelper.updateSimDimensions(mockDevice);

    verify(mockDevice)
        .updateDimension(Dimension.Name.SIM_CARD_TYPE, Dimension.SimCardTypeValue.SIM_CARD.name());
  }

  @Test
  public void updateSimDimensions_carrierPrivileges() throws Exception {
    setUpInstrumentationOutput(
        """
        INSTRUMENTATION_STATUS: sim_state=5
        INSTRUMENTATION_STATUS: has_carried_privileges=true
        """);

    telephonyHelper.updateSimDimensions(mockDevice);

    verify(mockDevice)
        .updateDimension(
            Dimension.Name.SIM_CARD_TYPE,
            Dimension.SimCardTypeValue.SIM_CARD.name(),
            Dimension.SimCardTypeValue.UICC_SIM_CARD.name());
  }

  @Test
  public void updateSimDimensions_secureElement_noMatchingOperator() throws Exception {
    setUpInstrumentationOutput(
        """
        INSTRUMENTATION_STATUS: sim_state=5
        INSTRUMENTATION_STATUS: has_carried_privileges=true
        INSTRUMENTATION_STATUS: has_secured_element=true
        INSTRUMENTATION_STATUS: has_se_service=true
        """);
    when(mockDeviceProperties.get(GSM_OPERATOR_PROP)).thenReturn(Optional.of("12345"));

    telephonyHelper.updateSimDimensions(mockDevice);

    verify(mockDevice)
        .updateDimension(
            Dimension.Name.SIM_CARD_TYPE,
            Dimension.SimCardTypeValue.SIM_CARD.name(),
            Dimension.SimCardTypeValue.UICC_SIM_CARD.name());
  }

  @Test
  public void updateSimDimensions_secureElement_matchingOperator() throws Exception {
    setUpInstrumentationOutput(
        """
        INSTRUMENTATION_STATUS: sim_state=5
        INSTRUMENTATION_STATUS: has_carried_privileges=true
        INSTRUMENTATION_STATUS: has_secured_element=true
        INSTRUMENTATION_STATUS: has_se_service=true
        """);
    when(mockDeviceProperties.get(GSM_OPERATOR_PROP)).thenReturn(Optional.of(ORANGE_SIM_ID));

    telephonyHelper.updateSimDimensions(mockDevice);

    verify(mockDevice)
        .updateDimension(
            Dimension.Name.SIM_CARD_TYPE,
            Dimension.SimCardTypeValue.SIM_CARD.name(),
            Dimension.SimCardTypeValue.UICC_SIM_CARD.name(),
            Dimension.SimCardTypeValue.SECURE_ELEMENT_SIM_CARD.name());
  }

  @Test
  public void updateSimDimensions_secureElement_matchingOperator_multipleIds() throws Exception {
    setUpInstrumentationOutput(
        """
        INSTRUMENTATION_STATUS: sim_state=5
        INSTRUMENTATION_STATUS: has_carried_privileges=true
        INSTRUMENTATION_STATUS: has_secured_element=true
        INSTRUMENTATION_STATUS: has_se_service=true
        """);
    when(mockDeviceProperties.get(GSM_OPERATOR_PROP))
        .thenReturn(Optional.of("12345," + THALES_GEMALTO_SIM_ID));

    telephonyHelper.updateSimDimensions(mockDevice);

    verify(mockDevice)
        .updateDimension(
            Dimension.Name.SIM_CARD_TYPE,
            Dimension.SimCardTypeValue.SIM_CARD.name(),
            Dimension.SimCardTypeValue.UICC_SIM_CARD.name(),
            Dimension.SimCardTypeValue.SECURE_ELEMENT_SIM_CARD.name());
  }

  @Test
  public void updateSimDimensions_secureElement_onlyHasSecuredElement() throws Exception {
    setUpInstrumentationOutput(
        """
        INSTRUMENTATION_STATUS: sim_state=5
        INSTRUMENTATION_STATUS: has_carried_privileges=true
        INSTRUMENTATION_STATUS: has_secured_element=true
        INSTRUMENTATION_STATUS: has_se_service=false
        """);
    when(mockDeviceProperties.get(GSM_OPERATOR_PROP)).thenReturn(Optional.of(ORANGE_SIM_ID));

    telephonyHelper.updateSimDimensions(mockDevice);

    verify(mockDevice)
        .updateDimension(
            Dimension.Name.SIM_CARD_TYPE,
            Dimension.SimCardTypeValue.SIM_CARD.name(),
            Dimension.SimCardTypeValue.UICC_SIM_CARD.name());
  }

  @Test
  public void updateSimDimensions_secureElement_onlyHasSeService() throws Exception {
    setUpInstrumentationOutput(
        """
        INSTRUMENTATION_STATUS: sim_state=5
        INSTRUMENTATION_STATUS: has_carried_privileges=true
        INSTRUMENTATION_STATUS: has_secured_element=false
        INSTRUMENTATION_STATUS: has_se_service=true
        """);
    when(mockDeviceProperties.get(GSM_OPERATOR_PROP)).thenReturn(Optional.of(ORANGE_SIM_ID));

    telephonyHelper.updateSimDimensions(mockDevice);

    verify(mockDevice)
        .updateDimension(
            Dimension.Name.SIM_CARD_TYPE,
            Dimension.SimCardTypeValue.SIM_CARD.name(),
            Dimension.SimCardTypeValue.UICC_SIM_CARD.name());
  }

  @Test
  public void updateSimDimensions_resUtilReturnsNull() throws Exception {
    when(mockResUtil.getResourceFile(any(), any())).thenReturn(null);
    telephonyHelper =
        new TelephonyHelper(mockAndroidInstrumentationUtil, mockApkInstaller, mockResUtil);

    telephonyHelper.updateSimDimensions(mockDevice);

    verify(mockDevice, never()).updateDimension(any(Dimension.Name.class), any());
  }

  @Test
  public void updateSimDimensions_resUtilThrowsException() throws Exception {
    when(mockResUtil.getResourceFile(any(), any()))
        .thenThrow(new MobileHarnessException(null, "ResUtil failed"));
    telephonyHelper =
        new TelephonyHelper(mockAndroidInstrumentationUtil, mockApkInstaller, mockResUtil);

    telephonyHelper.updateSimDimensions(mockDevice);

    verify(mockDevice, never()).updateDimension(any(Dimension.Name.class), any());
  }

  @Test
  public void updateSimDimensions_apkInstallFails() throws Exception {
    doThrow(new MobileHarnessException(null, "APK install failed"))
        .when(mockApkInstaller)
        .installApkIfNotExist(eq(mockDevice), any(ApkInstallArgs.class), any());

    assertThrows(
        MobileHarnessException.class, () -> telephonyHelper.updateSimDimensions(mockDevice));

    verify(mockApkInstaller).installApkIfNotExist(eq(mockDevice), any(ApkInstallArgs.class), any());
    verify(mockApkInstaller).uninstallApk(eq(mockDevice), any(), eq(true), any());
  }

  @Test
  public void updateSimDimensions_instrumentationFails() throws Exception {
    when(mockAndroidInstrumentationUtil.instrument(eq(DEVICE_ID), any(), any(), any()))
        .thenThrow(new MobileHarnessException(null, "Instrumentation failed"));

    assertThrows(
        MobileHarnessException.class, () -> telephonyHelper.updateSimDimensions(mockDevice));

    verify(mockApkInstaller).installApkIfNotExist(eq(mockDevice), any(ApkInstallArgs.class), any());
    verify(mockApkInstaller).uninstallApk(eq(mockDevice), any(), eq(true), any());
  }

  @Test
  public void updateSimDimensions_invalidInstrumentationOutput() throws Exception {
    setUpInstrumentationOutput(
        """
        INSTRUMENTATION_STATUS: sim_state=5
        SOME_OTHER_OUTPUT
        INSTRUMENTATION_STATUS: unknown_key=some_value
        """);

    telephonyHelper.updateSimDimensions(mockDevice);

    verify(mockDevice)
        .updateDimension(Dimension.Name.SIM_CARD_TYPE, Dimension.SimCardTypeValue.SIM_CARD.name());
  }
}
