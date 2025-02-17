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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroup;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.protobuf.FieldMask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MaskUtilsTest {

  private static final DeviceFeature DEVICE_FEATURE_1 =
      createDeviceFeature(
          "owner1",
          ImmutableList.of(
              DeviceDimension.newBuilder().setName("pool").setValue("required_pool").build()),
          ImmutableList.of(
              DeviceDimension.newBuilder().setName("pool").setValue("supported_pool").build(),
              DeviceDimension.newBuilder().setName("label").setValue("label_1").build()));

  private static final DeviceFeature DEVICE_FEATURE_2 =
      createDeviceFeature(
          "owner2",
          ImmutableList.of(
              DeviceDimension.newBuilder().setName("pool").setValue("fake_pool_1").build()),
          ImmutableList.of(
              DeviceDimension.newBuilder().setName("pool").setValue("fake_pool_1").build()));

  private static final DeviceInfo DEVICE_INFO_1 =
      createDeviceInfo(
          "device1",
          LabLocator.newBuilder().setIp("1.1.1.1").setHostName("lab1").build(),
          DeviceStatus.IDLE,
          DEVICE_FEATURE_1);

  private static final DeviceInfo DEVICE_INFO_2 =
      createDeviceInfo(
          "device2",
          LabLocator.newBuilder().setIp("1.1.1.1").setHostName("lab1").build(),
          DeviceStatus.BUSY,
          DEVICE_FEATURE_2);

  private static final LabQueryResult LAB_QUERY_RESULT_WITH_LAB_VIEW =
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
                                  .addDeviceInfo(DEVICE_INFO_1)
                                  .addDeviceInfo(DEVICE_INFO_2)))
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

  private static final LabQueryResult LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_DEVICE_LIST =
      LabQueryResult.newBuilder()
          .setDeviceView(
              DeviceView.newBuilder()
                  .setGroupedDevices(
                      createGroupedDevicesWithOnlyDeviceList(
                          ImmutableList.of(DEVICE_INFO_1, DEVICE_INFO_2))))
          .build();

  private static final LabQueryResult LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_GROUPED_DEVICES =
      LabQueryResult.newBuilder()
          .setDeviceView(
              DeviceView.newBuilder()
                  .setGroupedDevices(
                      createGroupedDevicesWithNestedGroupedDevices(
                          ImmutableList.of(DEVICE_INFO_1, DEVICE_INFO_2))))
          .build();

  @Test
  public void trimLabQueryResult_withLabView_withLabInfoMask_returnsFilteredResponse() {
    LabQueryResult.Builder resultToTrim = LAB_QUERY_RESULT_WITH_LAB_VIEW.toBuilder();
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(
                LabInfoMask.newBuilder()
                    .setFieldMask(
                        FieldMask.newBuilder().addPaths("lab_locator").addPaths("lab_status")))
            .build();

    LabQueryResult.Builder expectedResult = LAB_QUERY_RESULT_WITH_LAB_VIEW.toBuilder();
    expectedResult
        .getLabViewBuilder()
        .getLabDataBuilderList()
        .forEach(
            labDataBuilder ->
                labDataBuilder.getLabInfoBuilder().clearLabServerSetting().clearLabServerFeature());

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult.build());
  }

  @Test
  public void trimLabQueryResult_withLabView_withEmptyLabInfoMask_returnsEmptyLabInfo() {
    LabQueryResult.Builder resultToTrim = LAB_QUERY_RESULT_WITH_LAB_VIEW.toBuilder();
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(LabInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
            .build();

    LabQueryResult.Builder expectedResult = LAB_QUERY_RESULT_WITH_LAB_VIEW.toBuilder();
    expectedResult
        .getLabViewBuilder()
        .getLabDataBuilderList()
        .forEach(LabData.Builder::clearLabInfo);

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult.build());
  }

  @Test
  public void trimLabQueryResult_withLabView_withDeviceInfoMask_returnsFilteredResponse() {
    LabQueryResult.Builder resultToTrim = LAB_QUERY_RESULT_WITH_LAB_VIEW.toBuilder();
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder()
                    .setFieldMask(
                        FieldMask.newBuilder()
                            .addPaths("device_locator.id")
                            .addPaths("device_status")))
            .build();

    LabQueryResult.Builder expectedResult = LAB_QUERY_RESULT_WITH_LAB_VIEW.toBuilder();
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

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult.build());
  }

  @Test
  public void trimLabQueryResult_withLabView_withEmptyDeviceInfoMask_returnsEmptyDeviceInfo() {
    LabQueryResult.Builder resultToTrim = LAB_QUERY_RESULT_WITH_LAB_VIEW.toBuilder();
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
            .build();

    LabQueryResult.Builder expectedResult = LAB_QUERY_RESULT_WITH_LAB_VIEW.toBuilder();
    expectedResult
        .getLabViewBuilder()
        .getLabDataBuilderList()
        .forEach(labDataBuilder -> labDataBuilder.getDeviceListBuilder().clearDeviceInfo());

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult.build());
  }

  @Test
  public void trimLabQueryResult_withLabView_withEmptyMask_returnsOriginalResult() {
    LabQueryResult.Builder resultToTrim = LAB_QUERY_RESULT_WITH_LAB_VIEW.toBuilder();
    Mask mask = Mask.getDefaultInstance();

    LabQueryResult expectedResult = LAB_QUERY_RESULT_WITH_LAB_VIEW;

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult);
  }

  @Test
  public void
      trimLabQueryResult_withDeviceView_withGroupedDevices_withDeviceInfoMask_returnsFilteredResponse() {
    LabQueryResult.Builder resultToTrim =
        LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_GROUPED_DEVICES.toBuilder();
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder()
                    .setFieldMask(
                        FieldMask.newBuilder()
                            .addPaths("device_locator.id")
                            .addPaths("device_status")))
            .build();

    LabQueryResult.Builder expectedResult =
        LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_GROUPED_DEVICES.toBuilder();
    expectedResult
        .getDeviceViewBuilder()
        .getGroupedDevicesBuilder()
        .getDeviceGroupResultBuilder()
        .getDeviceGroupBuilderList()
        .forEach(
            deviceGroupBuilder ->
                deviceGroupBuilder
                    .getGroupedDevicesBuilder()
                    .getDeviceListBuilder()
                    .getDeviceInfoBuilderList()
                    .forEach(
                        deviceInfoBuilder ->
                            deviceInfoBuilder
                                .clearDeviceFeature()
                                .getDeviceLocatorBuilder()
                                .clearLabLocator()));

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult.build());
  }

  @Test
  public void
      trimLabQueryResult_withDeviceView_withGroupedDevices_withEmptyDeviceInfoMask_returnsEmptyDeviceInfo() {
    LabQueryResult.Builder resultToTrim =
        LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_GROUPED_DEVICES.toBuilder();
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
            .build();

    LabQueryResult.Builder expectedResult =
        LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_GROUPED_DEVICES.toBuilder();
    expectedResult
        .getDeviceViewBuilder()
        .getGroupedDevicesBuilder()
        .getDeviceGroupResultBuilder()
        .getDeviceGroupBuilderList()
        .forEach(
            deviceGroupBuilder ->
                deviceGroupBuilder
                    .getGroupedDevicesBuilder()
                    .getDeviceListBuilder()
                    .clearDeviceInfo());

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult.build());
  }

  @Test
  public void
      trimLabQueryResult_withDeviceView_withGroupedDevices_withEmptyMask_returnsOriginalResult() {
    LabQueryResult.Builder resultToTrim =
        LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_GROUPED_DEVICES.toBuilder();
    Mask mask = Mask.getDefaultInstance();

    LabQueryResult expectedResult = LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_GROUPED_DEVICES;

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult);
  }

  @Test
  public void
      trimLabQueryResult_withDeviceView_withDeviceList_withDeviceInfoMask_returnsFilteredResponse() {
    LabQueryResult.Builder resultToTrim =
        LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_DEVICE_LIST.toBuilder();
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder()
                    .setFieldMask(
                        FieldMask.newBuilder()
                            .addPaths("device_locator.id")
                            .addPaths("device_status")))
            .build();

    LabQueryResult.Builder expectedResult =
        LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_DEVICE_LIST.toBuilder();
    expectedResult
        .getDeviceViewBuilder()
        .getGroupedDevicesBuilder()
        .getDeviceListBuilder()
        .getDeviceInfoBuilderList()
        .forEach(
            deviceInfoBuilder ->
                deviceInfoBuilder.clearDeviceFeature().getDeviceLocatorBuilder().clearLabLocator());

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult.build());
  }

  @Test
  public void
      trimLabQueryResult_withDeviceView_withDeviceList_withEmptyDeviceInfoMask_returnsEmptyDeviceInfo() {
    LabQueryResult.Builder resultToTrim =
        LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_DEVICE_LIST.toBuilder();
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
            .build();

    LabQueryResult.Builder expectedResult =
        LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_DEVICE_LIST.toBuilder();
    expectedResult
        .getDeviceViewBuilder()
        .getGroupedDevicesBuilder()
        .getDeviceListBuilder()
        .clearDeviceInfo();

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult.build());
  }

  @Test
  public void
      trimLabQueryResult_withDeviceView_withDeviceList_withEmptyMask_returnsOriginalResult() {
    LabQueryResult.Builder resultToTrim =
        LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_DEVICE_LIST.toBuilder();
    Mask mask = Mask.getDefaultInstance();

    LabQueryResult expectedResult = LAB_QUERY_RESULT_WITH_DEVICE_VIEW_WITH_DEVICE_LIST;

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult);
  }

  @Test
  public void trimLabQueryResult_withSelectedDimensionNames() {
    LabQueryResult.Builder resultToTrim = LAB_QUERY_RESULT_WITH_LAB_VIEW.toBuilder();
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(LabInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder()
                    .setFieldMask(FieldMask.newBuilder().addPaths("device_feature"))
                    .addSelectedDimensionNames("pool"))
            .build();

    LabQueryResult expectedResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabView.newBuilder()
                    .addLabData(
                        LabData.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(2)
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceFeature(
                                                DeviceFeature.newBuilder()
                                                    .addOwner("owner1")
                                                    .setCompositeDimension(
                                                        DeviceCompositeDimension.newBuilder()
                                                            .addSupportedDimension(
                                                                DeviceDimension.newBuilder()
                                                                    .setName("pool")
                                                                    .setValue("supported_pool"))
                                                            .addRequiredDimension(
                                                                DeviceDimension.newBuilder()
                                                                    .setName("pool")
                                                                    .setValue("required_pool")))
                                                    .build()))
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceFeature(DEVICE_FEATURE_2))))
                    // returns empty DeviceList because the DeviceInfoMask is not empty.
                    .addLabData(
                        LabData.newBuilder().setDeviceList(DeviceList.getDefaultInstance())))
            .build();

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult);
  }

  @Test
  public void trimLabQueryResult_withRequiredDimensionPathsAndNames() {
    LabQueryResult.Builder resultToTrim = LAB_QUERY_RESULT_WITH_LAB_VIEW.toBuilder();
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(LabInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder()
                    .setFieldMask(
                        FieldMask.newBuilder()
                            .addPaths("device_feature.composite_dimension.required_dimension"))
                    .addSelectedDimensionNames("pool"))
            .build();

    LabQueryResult expectedResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabView.newBuilder()
                    .addLabData(
                        LabData.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(2)
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceFeature(
                                                DeviceFeature.newBuilder()
                                                    .setCompositeDimension(
                                                        DeviceCompositeDimension.newBuilder()
                                                            .addRequiredDimension(
                                                                DeviceDimension.newBuilder()
                                                                    .setName("pool")
                                                                    .setValue("required_pool")))
                                                    .build()))
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceFeature(
                                                DeviceFeature.newBuilder()
                                                    .setCompositeDimension(
                                                        DeviceCompositeDimension.newBuilder()
                                                            .addRequiredDimension(
                                                                DeviceDimension.newBuilder()
                                                                    .setName("pool")
                                                                    .setValue("fake_pool_1")))))))
                    // returns empty DeviceList because the DeviceInfoMask is not empty.
                    .addLabData(
                        LabData.newBuilder().setDeviceList(DeviceList.getDefaultInstance())))
            .build();

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult);
  }

  @Test
  public void trimLabQueryResult_withDimensionNameNotMatch_returnsEmptyDimension() {
    LabQueryResult.Builder resultToTrim = LAB_QUERY_RESULT_WITH_LAB_VIEW.toBuilder();
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(LabInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder()
                    .setFieldMask(FieldMask.newBuilder().addPaths("device_feature"))
                    .addSelectedDimensionNames("fake_dimension"))
            .build();

    LabQueryResult expectedResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabView.newBuilder()
                    .addLabData(
                        LabData.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(2)
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceFeature(
                                                DeviceFeature.newBuilder()
                                                    .addOwner("owner1")
                                                    .setCompositeDimension(
                                                        DeviceCompositeDimension
                                                            .getDefaultInstance())))
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceFeature(
                                                DeviceFeature.newBuilder()
                                                    .addOwner("owner2")
                                                    .setCompositeDimension(
                                                        DeviceCompositeDimension
                                                            .getDefaultInstance())))))
                    // returns empty DeviceList because the DeviceInfoMask is not empty.
                    .addLabData(
                        LabData.newBuilder().setDeviceList(DeviceList.getDefaultInstance())))
            .build();

    MaskUtils.trimLabQueryResult(resultToTrim, mask);
    assertThat(resultToTrim.build()).isEqualTo(expectedResult);
  }

  /**
   * Creates a {@link GroupedDevices} with nested {@link GroupedDevices} and a {@link DeviceList}.
   */
  private static GroupedDevices createGroupedDevicesWithNestedGroupedDevices(
      ImmutableList<DeviceInfo> deviceInfos) {
    return GroupedDevices.newBuilder()
        .setDeviceGroupResult(
            DeviceGroupResult.newBuilder()
                .addDeviceGroup(
                    DeviceGroup.newBuilder()
                        .setGroupedDevices(
                            GroupedDevices.newBuilder()
                                .setDeviceList(
                                    DeviceList.newBuilder()
                                        .setDeviceTotalCount(deviceInfos.size())
                                        .addAllDeviceInfo(deviceInfos)))))
        .build();
  }

  /** Creates a {@link GroupedDevices} with only a {@link DeviceList}. */
  private static GroupedDevices createGroupedDevicesWithOnlyDeviceList(
      ImmutableList<DeviceInfo> deviceInfos) {
    return GroupedDevices.newBuilder()
        .setDeviceList(
            DeviceList.newBuilder()
                .setDeviceTotalCount(deviceInfos.size())
                .addAllDeviceInfo(deviceInfos))
        .build();
  }

  private static DeviceInfo createDeviceInfo(
      String deviceId,
      LabLocator labLocator,
      DeviceStatus deviceStatus,
      DeviceFeature deviceFeature) {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId(deviceId).setLabLocator(labLocator))
        .setDeviceStatus(deviceStatus)
        .setDeviceFeature(deviceFeature)
        .build();
  }

  private static DeviceFeature createDeviceFeature(
      String owner,
      ImmutableList<DeviceDimension> requiredDimensions,
      ImmutableList<DeviceDimension> supportedDimensions) {
    return DeviceFeature.newBuilder()
        .addOwner(owner)
        .setCompositeDimension(
            DeviceCompositeDimension.newBuilder()
                .addAllSupportedDimension(supportedDimensions)
                .addAllRequiredDimension(requiredDimensions))
        .build();
  }
}
