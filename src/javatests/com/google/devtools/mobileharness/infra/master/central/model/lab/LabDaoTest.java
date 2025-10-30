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

package com.google.devtools.mobileharness.infra.master.central.model.lab;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.infra.master.central.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.infra.master.central.proto.Device.DeviceProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LabDao}. */
@RunWith(JUnit4.class)
public class LabDaoTest {
  private static final LabLocator LAB_LOCATOR = LabLocator.of("192.168.0.1", "lab.example.com");

  @Test
  public void deviceOperations() {
    String deviceIdPrefix = "device_id_";
    int deviceCount = 3;
    List<DeviceDao> devices = new ArrayList<>(deviceCount);
    for (int i = 0; i < 3; i++) {
      DeviceDao device =
          DeviceDao.create(
              DeviceLocator.of(deviceIdPrefix + i, LAB_LOCATOR),
              DeviceProfile.newBuilder()
                  .setFeature(
                      DeviceFeature.newBuilder()
                          .setCompositeDimension(
                              DeviceCompositeDimension.newBuilder()
                                  .addRequiredDimension(
                                      DeviceDimension.newBuilder()
                                          .setName("pool")
                                          .setValue("satellite")
                                          .build())
                                  .build())
                          .build())
                  .build(),
              DeviceCondition.getDefaultInstance());
      devices.add(device);
    }

    LabDao lab = LabDao.create(LAB_LOCATOR, Optional.empty(), Optional.empty());
    assertThat(lab.getDevices()).isEmpty();

    // Add device 0.
    lab.addDevice(devices.get(0));
    assertThat(lab.getDevices()).hasSize(1);
    assertThat(lab.getDevice(deviceIdPrefix + 0)).hasValue(devices.get(0));
    assertThat(lab.getDevice(deviceIdPrefix + 1)).isEmpty();
    assertThat(lab.removeDevice(deviceIdPrefix + 1)).isEmpty();

    // Add device 1, 2.
    lab.addDevice(devices.get(1));
    lab.addDevice(devices.get(2));
    assertThat(lab.getDevices()).hasSize(3);
    for (int i = 0; i < deviceCount; i++) {
      assertThat(lab.getDevice(deviceIdPrefix + i)).hasValue(devices.get(i));
    }

    // Adding the existing devices again has no effect.
    for (int i = 0; i < deviceCount; i++) {
      lab.addDevice(devices.get(i));
    }
    assertThat(lab.getDevices()).hasSize(3);

    // Remove device 2.
    assertThat(lab.removeDevice(deviceIdPrefix + 2)).hasValue(devices.get(2));
    assertThat(lab.removeDevice(deviceIdPrefix + 2)).isEmpty();
    assertThat(lab.getDevices()).hasSize(2);
  }

  @Test
  public void isSharedPoolLab_true() {
    DeviceDao deviceDao =
        DeviceDao.create(
            DeviceLocator.of("id", LAB_LOCATOR),
            DeviceProfile.newBuilder()
                .setFeature(
                    DeviceFeature.newBuilder()
                        .setCompositeDimension(
                            DeviceCompositeDimension.newBuilder()
                                .addRequiredDimension(
                                    DeviceDimension.newBuilder()
                                        .setName("pool")
                                        .setValue("shared"))))
                .build(),
            DeviceCondition.getDefaultInstance());
    LabDao lab = LabDao.create(LAB_LOCATOR, Optional.empty(), Optional.empty());
    lab.addDevice(deviceDao);
    assertThat(lab.isSharedPoolLab()).isTrue();
  }

  @Test
  public void isSharedPoolLab_false() {
    DeviceDao deviceDao =
        DeviceDao.create(
            DeviceLocator.of("id", LAB_LOCATOR),
            DeviceProfile.newBuilder()
                .setFeature(
                    DeviceFeature.newBuilder()
                        .setCompositeDimension(
                            DeviceCompositeDimension.newBuilder()
                                .addRequiredDimension(
                                    DeviceDimension.newBuilder()
                                        .setName("pool")
                                        .setValue("satellite"))))
                .build(),
            DeviceCondition.getDefaultInstance());
    LabDao lab = LabDao.create(LAB_LOCATOR, Optional.empty(), Optional.empty());
    lab.addDevice(deviceDao);
    assertThat(lab.isSharedPoolLab()).isFalse();
  }
}
