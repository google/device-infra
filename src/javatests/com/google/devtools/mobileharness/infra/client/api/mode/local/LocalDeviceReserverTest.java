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

package com.google.devtools.mobileharness.infra.client.api.mode.local;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableListMultimap;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import com.google.devtools.mobileharness.infra.client.api.util.dimension.DeviceTempRequiredDimensionManager;
import com.google.devtools.mobileharness.infra.client.api.util.dimension.DeviceTempRequiredDimensionManager.DeviceKey;
import com.google.devtools.mobileharness.infra.controller.device.DeviceIdManager;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.model.lab.LabLocator;
import java.time.Duration;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class LocalDeviceReserverTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private DeviceTempRequiredDimensionManager manager;
  @Mock private DeviceIdManager idManager;

  @Captor private ArgumentCaptor<DeviceKey> deviceKeyCaptor;

  private LocalDeviceReserver reserver;

  @Before
  public void setUp() throws Exception {
    reserver = new LocalDeviceReserver(manager, idManager);
  }

  @Test
  public void addTempAllocationKeyToDevice_inputIsUuid_usesUuid() {
    when(idManager.containsUuid("uuid-123")).thenReturn(true);

    DeviceLocator locator = new DeviceLocator("uuid-123", LabLocator.LOCALHOST);

    reserver.addTempAllocationKeyToDevice(locator, "dimension", "value", Duration.ofMinutes(1));

    verify(manager)
        .addOrRemoveDimensions(
            deviceKeyCaptor.capture(),
            eq(ImmutableListMultimap.of("dimension", "value")),
            eq(Duration.ofMinutes(1)));

    DeviceKey key = deviceKeyCaptor.getValue();
    assertThat(key.deviceUuid()).isEqualTo("uuid-123");
    assertThat(key.labHostName()).isEqualTo(LabLocator.LOCALHOST.getHostName());
  }

  @Test
  public void addTempAllocationKeyToDevice_inputIsControlId_translatesToUuid() {
    DeviceId deviceId = DeviceId.of("control-123", "uuid-123");
    when(idManager.containsUuid("control-123")).thenReturn(false);
    when(idManager.getDeviceIdFromControlId("control-123")).thenReturn(Optional.of(deviceId));

    DeviceLocator locator = new DeviceLocator("control-123", LabLocator.LOCALHOST);

    reserver.addTempAllocationKeyToDevice(locator, "dimension", "value", Duration.ofMinutes(1));

    verify(manager)
        .addOrRemoveDimensions(
            deviceKeyCaptor.capture(),
            eq(ImmutableListMultimap.of("dimension", "value")),
            eq(Duration.ofMinutes(1)));

    DeviceKey key = deviceKeyCaptor.getValue();
    assertThat(key.deviceUuid()).isEqualTo("uuid-123");
    assertThat(key.labHostName()).isEqualTo(LabLocator.LOCALHOST.getHostName());
  }

  @Test
  public void addTempAllocationKeyToDevice_inputIsUnknown_fallsBackToInput() {
    when(idManager.containsUuid("unknown-id")).thenReturn(false);

    DeviceLocator locator = new DeviceLocator("unknown-id", LabLocator.LOCALHOST);

    reserver.addTempAllocationKeyToDevice(locator, "dimension", "value", Duration.ofMinutes(1));

    verify(manager)
        .addOrRemoveDimensions(
            deviceKeyCaptor.capture(),
            eq(ImmutableListMultimap.of("dimension", "value")),
            eq(Duration.ofMinutes(1)));

    DeviceKey key = deviceKeyCaptor.getValue();
    assertThat(key.deviceUuid()).isEqualTo("unknown-id");
    assertThat(key.labHostName()).isEqualTo(LabLocator.LOCALHOST.getHostName());
  }
}
