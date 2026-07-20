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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidRemoteProvisioningUtil;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfoMocker;
import java.util.Base64;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class ReportIntegrityCollectorDecoratorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Device device;
  @Mock private Driver decoratedDriver;
  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private AndroidRemoteProvisioningUtil androidRemoteProvisioningUtil;

  private TestInfo testInfo;
  private ReportIntegrityCollectorDecorator decorator;

  @Before
  public void setUp() throws Exception {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn("device_id");

    testInfo = TestInfoMocker.mockTestInfo();

    decorator =
        new ReportIntegrityCollectorDecorator(
            decoratedDriver, testInfo, androidAdbUtil, androidRemoteProvisioningUtil);
  }

  @Test
  public void setUp_collectsVbmetaAndCsrs() throws Exception {
    when(androidAdbUtil.getProperty("device_id", ImmutableList.of("ro.boot.vbmeta.digest")))
        .thenReturn("vbmeta_digest_value");
    when(androidRemoteProvisioningUtil.getInstanceNameToCsr("device_id"))
        .thenReturn(
            ImmutableMap.of(
                "instance1", "csr1_bytes".getBytes(UTF_8),
                "instance2", "csr2_bytes".getBytes(UTF_8)));

    decorator.setUp(SetupContext.create(testInfo));

    assertThat(testInfo.properties().get("cts:build_vb_meta_digest"))
        .isEqualTo("vbmeta_digest_value");
    assertThat(testInfo.properties().get("cts:csr_instance1"))
        .isEqualTo(Base64.getEncoder().encodeToString("csr1_bytes".getBytes(UTF_8)));
    assertThat(testInfo.properties().get("cts:csr_instance2"))
        .isEqualTo(Base64.getEncoder().encodeToString("csr2_bytes".getBytes(UTF_8)));
  }

  @Test
  public void setUp_collectVbmetaThrows_failsSetup() throws Exception {
    when(androidAdbUtil.getProperty("device_id", ImmutableList.of("ro.boot.vbmeta.digest")))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_UTIL_GET_DEVICE_PROPERTY_ERROR,
                "Failed to get property"));

    assertThrows(
        MobileHarnessException.class, () -> decorator.setUp(SetupContext.create(testInfo)));
  }

  @Test
  public void setUp_collectCsrThrows_doesNotFailSetup() throws Exception {
    when(androidAdbUtil.getProperty("device_id", ImmutableList.of("ro.boot.vbmeta.digest")))
        .thenReturn("vbmeta_digest_value");
    when(androidRemoteProvisioningUtil.getInstanceNameToCsr("device_id"))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_CSR_DECODE_ERROR,
                "Failed to get CSR"));

    decorator.setUp(SetupContext.create(testInfo));

    assertThat(testInfo.properties().get("cts:build_vb_meta_digest"))
        .isEqualTo("vbmeta_digest_value");
    assertThat(testInfo.properties().get("cts:csr_instance1")).isNull();
  }
}
