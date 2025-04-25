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

package com.google.devtools.mobileharness.infra.ats.server.util;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.shared.util.network.NetworkUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
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
public final class AtsServerSessionUtilTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Bind @Mock private NetworkUtil networkUtil;
  @Bind @Mock private DeviceQuerier deviceQuerier;

  @Inject private AtsServerSessionUtil atsServerSessionUtil;

  @Before
  public void doBeforeEachTest() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void isLocalMode_sameHost() throws Exception {
    when(networkUtil.getLocalHostName()).thenReturn("host_name");
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("host_name").setValue("host_name")))
                .build());
    assertThat(atsServerSessionUtil.isLocalMode()).isTrue();
  }

  @Test
  public void isLocalMode_differentHost() throws Exception {
    when(networkUtil.getLocalHostName()).thenReturn("host_name");
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_1")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("host_name").setValue("host_name2")))
                .addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setId("device_id_2")
                        .addType("AndroidOnlineDevice")
                        .addDimension(
                            Dimension.newBuilder().setName("host_name").setValue("host_name")))
                .build());
    assertThat(atsServerSessionUtil.isLocalMode()).isFalse();
  }
}
