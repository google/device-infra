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

package com.google.devtools.mobileharness.infra.ats.common;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AtsMasterUtilTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Bind @Mock private DeviceQuerier deviceQuerier;

  @Inject private AtsMasterUtil atsMasterUtil;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void queryAndroidDevicesFromMaster_success() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidRealDevice")
                        .setStatus("IDLE"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_2")
                        .addType("AndroidRealDevice")
                        .setStatus("MISSING"))
                .build());

    assertThat(atsMasterUtil.queryAndroidDevicesFromMaster()).hasSize(1);
  }

  @Test
  public void queryAndroidDevicesFromMaster_exception() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenThrow(new MobileHarnessException(InfraErrorId.OLCS_QUERY_DEVICE_ERROR, "error"));

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class, () -> atsMasterUtil.queryAndroidDevicesFromMaster());
    assertThat(exception.getErrorId()).isEqualTo(InfraErrorId.OLCS_QUERY_DEVICE_ERROR);
  }
}
