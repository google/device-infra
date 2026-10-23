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

import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.shared.util.filter.CompiledDeviceInfoMask;
import com.google.devtools.mobileharness.shared.util.filter.CompiledLabInfoMask;
import com.google.protobuf.FieldMask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LabInfoProviderTest {

  private static final LabView FULL_VIEW =
      LabView.newBuilder()
          .setLabTotalCount(1)
          .addLabData(
              LabData.newBuilder()
                  .setLabInfo(
                      LabInfo.newBuilder()
                          .setLabLocator(LabLocator.newBuilder().setHostName("lab1"))
                          .setLabStatus(LabStatus.LAB_RUNNING)
                          .setLabServerFeature(LabServerFeature.getDefaultInstance()))
                  .setDeviceList(
                      DeviceList.newBuilder()
                          .setDeviceTotalCount(1)
                          .addDeviceInfo(
                              DeviceInfo.newBuilder()
                                  .setDeviceLocator(DeviceLocator.newBuilder().setId("device1"))
                                  .setDeviceStatus(DeviceStatus.IDLE)
                                  .setDeviceFeature(
                                      DeviceFeature.newBuilder()
                                          .addOwner("owner1")
                                          .setCompositeDimension(
                                              DeviceCompositeDimension.newBuilder()
                                                  .addSupportedDimension(
                                                      DeviceDimension.newBuilder()
                                                          .setName("model")
                                                          .setValue("pixel")))))))
          .build();

  /** A provider that only knows how to build the full view, like a lab server's. */
  private static final LabInfoProvider FULL_VIEW_PROVIDER = filter -> FULL_VIEW;

  @Test
  public void getLabInfos_defaultMaskedOverload_trimsTheFullView() throws Exception {
    CompiledLabInfoMask labInfoMask =
        CompiledLabInfoMask.of(
            LabInfoMask.newBuilder()
                .setFieldMask(FieldMask.newBuilder().addPaths("lab_locator"))
                .build());
    CompiledDeviceInfoMask deviceInfoMask =
        CompiledDeviceInfoMask.of(
            DeviceInfoMask.newBuilder()
                .setFieldMask(FieldMask.newBuilder().addPaths("device_locator"))
                .build());

    LabView view =
        FULL_VIEW_PROVIDER.getLabInfos(Filter.getDefaultInstance(), labInfoMask, deviceInfoMask);

    assertThat(view.getLabTotalCount()).isEqualTo(1);
    LabData labData = view.getLabData(0);
    assertThat(labData.getLabInfo().getLabLocator().getHostName()).isEqualTo("lab1");
    assertThat(labData.getLabInfo().getLabStatus()).isEqualTo(LabStatus.LAB_STATUS_UNSPECIFIED);
    assertThat(labData.getLabInfo().hasLabServerFeature()).isFalse();
    assertThat(labData.getDeviceList().getDeviceTotalCount()).isEqualTo(1);
    DeviceInfo device = labData.getDeviceList().getDeviceInfo(0);
    assertThat(device.getDeviceLocator().getId()).isEqualTo("device1");
    assertThat(device.getDeviceStatus()).isEqualTo(DeviceStatus.INIT);
    assertThat(device.hasDeviceFeature()).isFalse();
  }

  @Test
  public void getLabInfos_defaultMaskedOverload_retainAll_returnsTheFullView() throws Exception {
    LabView view =
        FULL_VIEW_PROVIDER.getLabInfos(
            Filter.getDefaultInstance(),
            CompiledLabInfoMask.retainAll(),
            CompiledDeviceInfoMask.retainAll());

    assertThat(view).isSameInstanceAs(FULL_VIEW);
  }
}
