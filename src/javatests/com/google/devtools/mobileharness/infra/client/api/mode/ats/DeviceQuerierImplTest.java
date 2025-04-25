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

package com.google.devtools.mobileharness.infra.client.api.mode.ats;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier.LabQueryResult;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class DeviceQuerierImplTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock @Bind private RemoteDeviceManager remoteDeviceManager;
  @Bind private ListeningExecutorService threadPool;
  @Inject private DeviceQuerierImpl deviceQuerier;

  private static final String HOST_IP_DIMENSION_NAME = Ascii.toLowerCase(Name.HOST_IP.name());
  private static final String HOST_NAME_DIMENSION_NAME = Ascii.toLowerCase(Name.HOST_NAME.name());

  @Before
  public void setUp() {
    threadPool = ThreadPools.createStandardThreadPool("device-querier-test");
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void queryDevicesByLab_success() throws Exception {
    DeviceQuery.DeviceInfo device1 =
        DeviceQuery.DeviceInfo.newBuilder()
            .setId("device_id_1")
            .setStatus("IDLE")
            .addDimension(
                Dimension.newBuilder().setName(HOST_NAME_DIMENSION_NAME).setValue("host_name_1"))
            .addDimension(
                Dimension.newBuilder().setName(HOST_IP_DIMENSION_NAME).setValue("host_ip_1"))
            .build();
    DeviceQuery.DeviceInfo device2 =
        DeviceQuery.DeviceInfo.newBuilder()
            .setId("device_id_2")
            .setStatus("IDLE")
            .addDimension(
                Dimension.newBuilder().setName(HOST_NAME_DIMENSION_NAME).setValue("host_name_1"))
            .addDimension(
                Dimension.newBuilder().setName(HOST_IP_DIMENSION_NAME).setValue("host_ip_1"))
            .build();
    DeviceQuery.DeviceInfo device3 =
        DeviceQuery.DeviceInfo.newBuilder()
            .setId("device_id_3")
            .setStatus("BUSY")
            .addDimension(
                Dimension.newBuilder().setName(HOST_NAME_DIMENSION_NAME).setValue("host_name_2"))
            .addDimension(
                Dimension.newBuilder().setName(HOST_IP_DIMENSION_NAME).setValue("host_ip_2"))
            .build();
    when(remoteDeviceManager.getDeviceInfos())
        .thenReturn(ImmutableList.of(device1, device2, device3));
    // Invoke the method under test
    List<LabQueryResult> results =
        deviceQuerier.queryDevicesByLab(DeviceQueryFilter.getDefaultInstance());

    // Assertions
    assertThat(results).hasSize(2);

    // Verify Lab 1
    LabQueryResult lab1Result =
        results.stream().filter(l -> l.hostname().equals("host_name_1")).findFirst().get();
    ImmutableList<String> deviceIds1 =
        lab1Result.devices().stream().map(d -> d.locator().getSerial()).collect(toImmutableList());
    assertThat(deviceIds1).containsExactly("device_id_1", "device_id_2");

    // Verify Lab 2
    LabQueryResult lab2Result =
        results.stream().filter(l -> l.hostname().equals("host_name_2")).findFirst().get();
    ImmutableList<String> deviceIds2 =
        lab2Result.devices().stream().map(d -> d.locator().getSerial()).collect(toImmutableList());
    assertThat(deviceIds2).containsExactly("device_id_3");
  }
}
