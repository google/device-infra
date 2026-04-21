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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableListMultimap;
import com.google.devtools.mobileharness.shared.labinfo.DeviceTempRequiredDimensionManager;
import com.google.devtools.mobileharness.shared.labinfo.DeviceTempRequiredDimensionManager.DeviceKey;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.model.lab.LabLocator;
import java.time.Duration;
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
public class AtsDeviceReserverTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private DeviceTempRequiredDimensionManager manager;

  @Captor private ArgumentCaptor<DeviceKey> deviceKeyCaptor;

  private AtsDeviceReserver reserver;

  @Before
  public void setUp() {
    reserver = new AtsDeviceReserver(manager);
  }

  @Test
  public void addTempAllocationKeyToDevice() {
    LabLocator labLocator = new LabLocator("192.168.1.1", "remote-host");
    DeviceLocator deviceLocator = new DeviceLocator("uuid-123", labLocator);

    reserver.addTempAllocationKeyToDevice(
        deviceLocator, "dimension", "value", Duration.ofMinutes(1));

    verify(manager)
        .addOrRemoveDimensions(
            deviceKeyCaptor.capture(),
            eq(ImmutableListMultimap.of("dimension", "value")),
            eq(Duration.ofMinutes(1)));

    DeviceKey key = deviceKeyCaptor.getValue();
    assertThat(key.labHostName()).isEqualTo("remote-host");
    assertThat(key.deviceUuid()).isEqualTo("uuid-123");
  }
}
