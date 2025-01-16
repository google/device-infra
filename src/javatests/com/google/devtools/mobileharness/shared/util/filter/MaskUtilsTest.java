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

package com.google.devtools.mobileharness.shared.util.filter;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.protobuf.FieldMask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MaskUtilsTest {

  private static final LabQueryResult ORIGINAL_RESULT =
      LabQueryResult.newBuilder()
          .setLabView(
              LabView.newBuilder()
                  .addLabData(
                      LabData.newBuilder()
                          .setLabInfo(
                              LabInfo.newBuilder()
                                  .setLabLocator(
                                      LabLocator.newBuilder().setIp("1.1.1.1").setHostName("lab1"))
                                  .setLabStatus(LabStatus.LAB_RUNNING)
                                  .setLabServerSetting(LabServerSetting.getDefaultInstance())
                                  .setLabServerFeature(LabServerFeature.getDefaultInstance()))
                          .setDeviceList(
                              DeviceList.newBuilder()
                                  .setDeviceTotalCount(2)
                                  .addDeviceInfo(
                                      DeviceInfo.newBuilder()
                                          .setDeviceLocator(
                                              DeviceLocator.newBuilder()
                                                  .setId("device1")
                                                  .setLabLocator(
                                                      LabLocator.newBuilder()
                                                          .setIp("1.1.1.1")
                                                          .setHostName("lab1")))
                                          .setDeviceStatus(DeviceStatus.IDLE)
                                          .setDeviceFeature(DeviceFeature.getDefaultInstance()))
                                  .addDeviceInfo(
                                      DeviceInfo.newBuilder()
                                          .setDeviceLocator(
                                              DeviceLocator.newBuilder()
                                                  .setId("device2")
                                                  .setLabLocator(
                                                      LabLocator.newBuilder()
                                                          .setIp("1.1.1.1")
                                                          .setHostName("lab1")))
                                          .setDeviceStatus(DeviceStatus.BUSY))))
                  .addLabData(
                      LabData.newBuilder()
                          .setLabInfo(
                              LabInfo.newBuilder()
                                  .setLabLocator(
                                      LabLocator.newBuilder().setIp("2.2.2.2").setHostName("lab2"))
                                  .setLabStatus(LabStatus.LAB_MISSING)
                                  .setLabServerSetting(LabServerSetting.getDefaultInstance())
                                  .setLabServerFeature(LabServerFeature.getDefaultInstance()))))
          .build();

  @Test
  public void trimLabQueryResult_withLabInfoMask_returnsFilteredResponse() {
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(
                LabInfoMask.newBuilder()
                    .setFieldMask(
                        FieldMask.newBuilder().addPaths("lab_locator").addPaths("lab_status")))
            .build();

    LabQueryResult.Builder expectedResult = ORIGINAL_RESULT.toBuilder();
    expectedResult
        .getLabViewBuilder()
        .getLabDataBuilderList()
        .forEach(
            labDataBuilder ->
                labDataBuilder.getLabInfoBuilder().clearLabServerSetting().clearLabServerFeature());

    assertThat(MaskUtils.trimLabQueryResult(ORIGINAL_RESULT, mask))
        .isEqualTo(expectedResult.build());
  }

  @Test
  public void trimLabQueryResult_withEmptyLabInfoMask_returnsEmptyLabInfo() {
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(LabInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
            .build();

    LabQueryResult.Builder expectedResult = ORIGINAL_RESULT.toBuilder();
    expectedResult
        .getLabViewBuilder()
        .getLabDataBuilderList()
        .forEach(LabData.Builder::clearLabInfo);

    assertThat(MaskUtils.trimLabQueryResult(ORIGINAL_RESULT, mask))
        .isEqualTo(expectedResult.build());
  }

  @Test
  public void trimLabQueryResult_withDeviceInfoMask_returnsFilteredResponse() {
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder()
                    .setFieldMask(
                        FieldMask.newBuilder()
                            .addPaths("device_locator.id")
                            .addPaths("device_status")))
            .build();

    LabQueryResult.Builder expectedResult = ORIGINAL_RESULT.toBuilder();
    expectedResult
        .getLabViewBuilder()
        .getLabDataBuilderList()
        .forEach(
            labDataBuilder ->
                labDataBuilder
                    .getDeviceListBuilder()
                    .getDeviceInfoBuilderList()
                    .forEach(
                        deviceInfoBuilder ->
                            deviceInfoBuilder
                                .clearDeviceFeature()
                                .getDeviceLocatorBuilder()
                                .clearLabLocator()));

    assertThat(MaskUtils.trimLabQueryResult(ORIGINAL_RESULT, mask))
        .isEqualTo(expectedResult.build());
  }

  @Test
  public void trimLabQueryResult_withEmptyDeviceInfoMask_returnsEmptyDeviceInfo() {
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
            .build();
    LabQueryResult.Builder expectedResult = ORIGINAL_RESULT.toBuilder();
    expectedResult
        .getLabViewBuilder()
        .getLabDataBuilderList()
        .forEach(labDataBuilder -> labDataBuilder.getDeviceListBuilder().clearDeviceInfo());

    assertThat(MaskUtils.trimLabQueryResult(ORIGINAL_RESULT, mask))
        .isEqualTo(expectedResult.build());
  }

  @Test
  public void trimLabQueryResult_withEmptyMask_returnsOriginalResult() {
    assertThat(MaskUtils.trimLabQueryResult(ORIGINAL_RESULT, Mask.getDefaultInstance()))
        .isEqualTo(ORIGINAL_RESULT);
  }
}