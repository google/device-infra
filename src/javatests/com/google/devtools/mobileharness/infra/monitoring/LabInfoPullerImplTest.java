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

package com.google.devtools.mobileharness.infra.monitoring;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.infra.monitoring.proto.MonitorEntryProto.DeviceData;
import com.google.devtools.mobileharness.infra.monitoring.proto.MonitorEntryProto.HostData;
import com.google.devtools.mobileharness.infra.monitoring.proto.MonitorEntryProto.MonitorEntry;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoProvider;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
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
public final class LabInfoPullerImplTest {

  private static final String HOST_NAME = "fake_lab_host_name";
  private static final String HOST_IP = "fake_lab_ip";
  private static final String DEVICE_ID_1 = "fake_uuid_1";
  private static final String DEVICE_OWNER_1 = "fake_owner_1";
  private static final String DEVICE_TYPE_1 = "fake_device_type_1";
  private static final String DEVICE_DRIVER_1 = "fake_device_driver_1";
  private static final String DEVICE_DECORATOR_1 = "fake_device_decorator_1";
  private static final String DEVICE_MODEL_1 = "fake_device_model_1";
  private static final String DEVICE_SDK_VERSION_1 = "fake_device_sdk_version_1";
  private static final String DEVICE_HARDWARE_1 = "fake_device_hardware_1";
  private static final String DEVICE_BUILD_TYPE_1 = "fake_device_build_type_1";
  private static final String DEVICE_SUPPORTED_DIMENSION_NAME = "fake_dimension_name_1";
  private static final String DEVICE_SUPPORTED_DIMENSION_VALUE = "fake_dimension_value_1";
  private static final String DEVICE_REQUIRED_DIMENSION_NAME = "fake_dimension_name_2";
  private static final String DEVICE_REQUIRED_DIMENSION_VALUE = "fake_dimension_value__2";
  private static final String DEVICE_ID_2 = "fake_uuid_2";
  private static final String DEVICE_OWNER_2 = "fake_owner_2";
  private static final String DEVICE_TYPE_2 = "fake_device_type_2";
  private static final String DEVICE_DRIVER_2 = "fake_device_driver_2";
  private static final String DEVICE_DECORATOR_2 = "fake_device_decorator_2";
  private static final String DEVICE_MODEL_2 = "fake_device_model_2";
  private static final String DEVICE_SOFTWARE_VERSION_2 = "fake_device_software_version_2";

  private static final LabLocator LAB_LOCATOR =
      LabLocator.newBuilder().setIp(HOST_IP).setHostName(HOST_NAME).build();
  private static final LabInfo LAB_INFO = LabInfo.newBuilder().setLabLocator(LAB_LOCATOR).build();

  private static final DeviceInfo DEVICE_INFO_1 =
      DeviceInfo.newBuilder()
          .setDeviceLocator(
              DeviceLocator.newBuilder().setId(DEVICE_ID_1).setLabLocator(LAB_LOCATOR))
          .setDeviceStatus(DeviceStatus.BUSY)
          .setDeviceFeature(
              DeviceFeature.newBuilder()
                  .addOwner(DEVICE_OWNER_1)
                  .addType(DEVICE_TYPE_1)
                  .addDriver(DEVICE_DRIVER_1)
                  .addDecorator(DEVICE_DECORATOR_1)
                  .setCompositeDimension(
                      DeviceCompositeDimension.newBuilder()
                          .addSupportedDimension(
                              DeviceDimension.newBuilder()
                                  .setName("model")
                                  .setValue(DEVICE_MODEL_1))
                          .addSupportedDimension(
                              DeviceDimension.newBuilder()
                                  .setName("sdk_version")
                                  .setValue(DEVICE_SDK_VERSION_1))
                          .addSupportedDimension(
                              DeviceDimension.newBuilder()
                                  .setName("hardware")
                                  .setValue(DEVICE_HARDWARE_1))
                          .addSupportedDimension(
                              DeviceDimension.newBuilder()
                                  .setName("build_type")
                                  .setValue(DEVICE_BUILD_TYPE_1))
                          .addSupportedDimension(
                              DeviceDimension.newBuilder()
                                  .setName(DEVICE_SUPPORTED_DIMENSION_NAME)
                                  .setValue(DEVICE_SUPPORTED_DIMENSION_VALUE))
                          .addRequiredDimension(
                              DeviceDimension.newBuilder()
                                  .setName(DEVICE_REQUIRED_DIMENSION_NAME)
                                  .setValue(DEVICE_REQUIRED_DIMENSION_VALUE))))
          .build();
  private static final DeviceInfo DEVICE_INFO_2 =
      DeviceInfo.newBuilder()
          .setDeviceLocator(
              DeviceLocator.newBuilder().setId(DEVICE_ID_2).setLabLocator(LAB_LOCATOR))
          .setDeviceStatus(DeviceStatus.IDLE)
          .setDeviceFeature(
              DeviceFeature.newBuilder()
                  .addOwner(DEVICE_OWNER_2)
                  .addType(DEVICE_TYPE_2)
                  .addDriver(DEVICE_DRIVER_2)
                  .addDecorator(DEVICE_DECORATOR_2)
                  .setCompositeDimension(
                      DeviceCompositeDimension.newBuilder()
                          .addSupportedDimension(
                              DeviceDimension.newBuilder()
                                  .setName("model")
                                  .setValue(DEVICE_MODEL_2))
                          .addSupportedDimension(
                              DeviceDimension.newBuilder()
                                  .setName("software_version")
                                  .setValue(DEVICE_SOFTWARE_VERSION_2))))
          .build();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Bind @Mock private LabInfoProvider labInfoProvider;

  @Inject private LabInfoPullerImpl labInfoPuller;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void pull_empty() throws Exception {
    when(labInfoProvider.getLabInfos(any(Filter.class))).thenReturn(LabView.getDefaultInstance());

    ImmutableList<MonitorEntry> monitorEntries = labInfoPuller.pull();

    assertThat(monitorEntries).isEmpty();
  }

  @Test
  public void pull_success() throws Exception {

    when(labInfoProvider.getLabInfos(any(Filter.class)))
        .thenReturn(
            LabView.newBuilder()
                .setLabTotalCount(1)
                .addLabData(
                    LabData.newBuilder()
                        .setLabInfo(LAB_INFO)
                        .setDeviceList(
                            DeviceList.newBuilder()
                                .setDeviceTotalCount(2)
                                .addDeviceInfo(DEVICE_INFO_2)
                                .addDeviceInfo(DEVICE_INFO_1)))
                .build());

    ImmutableList<MonitorEntry> monitorEntries = labInfoPuller.pull();

    assertThat(monitorEntries).hasSize(1);
    MonitorEntry monitorEntry = monitorEntries.get(0);

    assertThat(monitorEntry.getHostData())
        .isEqualTo(HostData.newBuilder().setHostName(HOST_NAME).setHostIp(HOST_IP).build());
    assertThat(monitorEntry.getDeviceDataCount()).isEqualTo(2);
    assertThat(monitorEntry.getDeviceDataList())
        .containsExactly(
            DeviceData.newBuilder()
                .setId(DEVICE_ID_1)
                .setStatus("BUSY")
                .addDeviceType(DEVICE_TYPE_1)
                .setModel(DEVICE_MODEL_1)
                .setVersion(DEVICE_SDK_VERSION_1)
                .setHardware(DEVICE_HARDWARE_1)
                .setBuildType(DEVICE_BUILD_TYPE_1)
                .build(),
            DeviceData.newBuilder()
                .setId(DEVICE_ID_2)
                .setStatus("IDLE")
                .addDeviceType(DEVICE_TYPE_2)
                .setModel(DEVICE_MODEL_2)
                .setVersion(DEVICE_SOFTWARE_VERSION_2)
                .build());
  }
}
