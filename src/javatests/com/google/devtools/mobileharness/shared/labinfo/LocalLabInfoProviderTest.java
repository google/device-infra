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

package com.google.devtools.mobileharness.shared.labinfo;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatusWithTimestamp;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.infra.controller.device.DeviceStatusInfo;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.shared.labinfo.DeviceTempRequiredDimensionManager.DeviceKey;
import com.google.devtools.mobileharness.shared.labinfo.DeviceTempRequiredDimensionManager.DeviceTempRequiredDimensions;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.time.Instant;
import java.util.Optional;
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
public class LocalLabInfoProviderTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private LocalDeviceManager localDeviceManager;
  @Bind private ListenableFuture<LocalDeviceManager> localDeviceManagerFuture;
  @Mock @Bind private DeviceTempRequiredDimensionManager tempRequiredDimensionManager;
  @Mock private Device device;

  @SuppressWarnings("DoNotMockAutoValue")
  @Mock
  private DeviceStatusInfo deviceStatusInfo;

  @Inject private LocalLabInfoProvider localLabInfoProvider;

  @Before
  public void setUp() throws Exception {
    localDeviceManagerFuture = Futures.immediateFuture(localDeviceManager);
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void getLabInfos_withTempDimensions() throws Exception {
    String deviceUuid = "device_uuid";
    when(device.getDeviceUuid()).thenReturn(deviceUuid);

    DeviceStatusWithTimestamp statusWithTimestamp =
        DeviceStatusWithTimestamp.newBuilder().setStatus(DeviceStatus.IDLE).build();
    when(deviceStatusInfo.getDeviceStatusWithTimestamp()).thenReturn(statusWithTimestamp);

    when(localDeviceManager.getAllDeviceStatus(false))
        .thenReturn(ImmutableMap.of(device, deviceStatusInfo));

    DeviceFeature feature = DeviceFeature.getDefaultInstance();
    when(device.toFeature()).thenReturn(feature);

    DeviceKey deviceKey = new DeviceKey(LabLocator.LOCALHOST.hostName(), deviceUuid);

    ImmutableListMultimap<String, String> dimensions = ImmutableListMultimap.of("key1", "value1");
    Instant expireTime = Instant.ofEpochMilli(12345L);

    when(tempRequiredDimensionManager.getDimensions(deviceKey))
        .thenReturn(Optional.of(new DeviceTempRequiredDimensions(dimensions, expireTime)));

    LabView labView = localLabInfoProvider.getLabInfos(null);

    assertThat(labView.getLabDataCount()).isEqualTo(1);
    assertThat(labView.getLabData(0).getDeviceList().getDeviceInfoCount()).isEqualTo(1);

    DeviceInfo deviceInfo = labView.getLabData(0).getDeviceList().getDeviceInfo(0);
    assertThat(deviceInfo.getDeviceStatus()).isEqualTo(DeviceStatus.IDLE);

    // Verify condition
    DeviceCondition condition = deviceInfo.getDeviceCondition();
    assertThat(condition.getTempDimensionCount()).isEqualTo(1);
    assertThat(condition.getTempDimension(0).getDimension().getName()).isEqualTo("key1");
    assertThat(condition.getTempDimension(0).getDimension().getValue()).isEqualTo("value1");
    assertThat(condition.getTempDimension(0).getExpireTimestampMs()).isEqualTo(12345L);

    // Verify feature
    assertThat(deviceInfo.getDeviceFeature().getCompositeDimension().getRequiredDimensionCount())
        .isEqualTo(1);
    assertThat(
            deviceInfo.getDeviceFeature().getCompositeDimension().getRequiredDimension(0).getName())
        .isEqualTo("key1");
  }
}
