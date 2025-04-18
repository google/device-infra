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

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.truth.extensions.proto.FieldScope;
import com.google.common.truth.extensions.proto.FieldScopes;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabPort;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.PortType;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroup;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupKey;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupKey.HasDimensionValue;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupKey.HasDimensionValue.NoDimensionValue;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupKey.HasDimensionValueList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupKey.HasExecutorList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupKey.HasOwnerList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupKey.HasTypeList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroupResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupCondition;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupCondition.DimensionValueList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupCondition.ExecutorList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupCondition.OwnerList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupCondition.SingleDimensionValue;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupCondition.TypeList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupOperation;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.Page;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
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
public class LabInfoServiceTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final FieldScope FIELD_SCOPE =
      FieldScopes.ignoringFieldDescriptors(
          LabQueryResult.getDescriptor().findFieldByNumber(LabQueryResult.TIMESTAMP_FIELD_NUMBER));
  private static final LabPort LAB_PORT =
      LabPort.newBuilder().setType(PortType.LAB_SERVER_HTTP).setNum(1234).build();
  private static final LabLocator LAB_LOCATOR =
      LabLocator.newBuilder()
          .setIp("fake_lab_ip")
          .setHostName("fake_lab_host_name")
          .addPort(LAB_PORT)
          .build();
  private static final LabInfo LAB_INFO =
      LabInfo.newBuilder()
          .setLabLocator(LAB_LOCATOR)
          .setLabServerSetting(LabServerSetting.newBuilder().addPort(LAB_PORT))
          .setLabServerFeature(LabServerFeature.getDefaultInstance())
          .build();
  private static final DeviceInfo DEVICE_INFO_1 =
      DeviceInfo.newBuilder()
          .setDeviceLocator(
              DeviceLocator.newBuilder().setId("fake_uuid_1").setLabLocator(LAB_LOCATOR))
          .setDeviceStatus(DeviceStatus.BUSY)
          .setDeviceFeature(
              DeviceFeature.newBuilder()
                  .addOwner("fake_owner_1")
                  .addExecutor("fake_executor_2")
                  .addExecutor("fake_executor_1")
                  .addType("NoOpDevice")
                  .addType("AndroidRealDevice")
                  .addDriver("NoOpDriver")
                  .addDecorator("NoOpDecorator")
                  .setCompositeDimension(
                      DeviceCompositeDimension.newBuilder()
                          .addSupportedDimension(
                              DeviceDimension.newBuilder()
                                  .setName("fake_dimension_name")
                                  .setValue("fake_dimension_value_2"))
                          .addRequiredDimension(
                              DeviceDimension.newBuilder()
                                  .setName("fake_dimension_name")
                                  .setValue("fake_dimension_value_1"))))
          .build();
  private static final DeviceInfo DEVICE_INFO_2 =
      DeviceInfo.newBuilder()
          .setDeviceLocator(
              DeviceLocator.newBuilder().setId("fake_uuid_2").setLabLocator(LAB_LOCATOR))
          .setDeviceStatus(DeviceStatus.IDLE)
          .setDeviceFeature(
              DeviceFeature.newBuilder()
                  .addOwner("fake_owner_1")
                  .addOwner("fake_owner_2")
                  .addExecutor("fake_executor_1")
                  .addExecutor("fake_executor_2")
                  .addType("NoOpDevice")
                  .addDriver("NoOpDriver")
                  .addDecorator("NoOpDecorator")
                  .setCompositeDimension(DeviceCompositeDimension.getDefaultInstance()))
          .build();

  @Bind @Mock private LabInfoProvider labInfoProvider;

  @Inject private LabInfoService labInfoService;

  @Before
  public void setUp() throws Exception {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

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
  }

  @Test
  public void getLabInfo_emptyQuery_ordered() throws Exception {
    GetLabInfoResponse response =
        labInfoService.doGetLabInfo(GetLabInfoRequest.getDefaultInstance());

    assertThat(response)
        .withPartialScope(FIELD_SCOPE)
        .isEqualTo(
            GetLabInfoResponse.newBuilder()
                .setLabQueryResult(
                    LabQueryResult.newBuilder()
                        .setLabView(
                            LabView.newBuilder()
                                .setLabTotalCount(1)
                                .addLabData(
                                    LabData.newBuilder()
                                        .setLabInfo(LAB_INFO)
                                        .setDeviceList(
                                            DeviceList.newBuilder()
                                                .setDeviceTotalCount(2)
                                                .addDeviceInfo(DEVICE_INFO_1)
                                                .addDeviceInfo(DEVICE_INFO_2)))))
                .build());
  }

  @Test
  public void getLabInfo_emptyQuery_cached() throws Exception {
    GetLabInfoResponse response1 =
        labInfoService.doGetLabInfo(GetLabInfoRequest.getDefaultInstance());
    GetLabInfoResponse response2 =
        labInfoService.doGetLabInfo(GetLabInfoRequest.getDefaultInstance());

    assertThat(response1.getLabQueryResult().getTimestamp())
        .isEqualTo(response2.getLabQueryResult().getTimestamp());
  }

  @Test
  public void getLabInfo_deviceView() throws Exception {
    GetLabInfoResponse response =
        labInfoService.doGetLabInfo(
            GetLabInfoRequest.newBuilder()
                .setLabQuery(
                    LabQuery.newBuilder()
                        .setDeviceViewRequest(DeviceViewRequest.getDefaultInstance()))
                .build());

    assertThat(response)
        .withPartialScope(FIELD_SCOPE)
        .isEqualTo(
            GetLabInfoResponse.newBuilder()
                .setLabQueryResult(
                    LabQueryResult.newBuilder()
                        .setDeviceView(
                            DeviceView.newBuilder()
                                .setGroupedDevices(
                                    GroupedDevices.newBuilder()
                                        .setDeviceList(
                                            DeviceList.newBuilder()
                                                .setDeviceTotalCount(2)
                                                .addDeviceInfo(DEVICE_INFO_1)
                                                .addDeviceInfo(DEVICE_INFO_2)))))
                .build());
  }

  @Test
  public void getLabInfo_deviceView_page() throws Exception {
    GetLabInfoResponse response =
        labInfoService.doGetLabInfo(
            GetLabInfoRequest.newBuilder()
                .setLabQuery(
                    LabQuery.newBuilder()
                        .setDeviceViewRequest(DeviceViewRequest.getDefaultInstance()))
                .setPage(Page.newBuilder().setOffset(1).setLimit(0))
                .build());

    assertThat(response)
        .withPartialScope(FIELD_SCOPE)
        .isEqualTo(
            GetLabInfoResponse.newBuilder()
                .setLabQueryResult(
                    LabQueryResult.newBuilder()
                        .setDeviceView(
                            DeviceView.newBuilder()
                                .setGroupedDevices(
                                    GroupedDevices.newBuilder()
                                        .setDeviceList(
                                            DeviceList.newBuilder()
                                                .setDeviceTotalCount(2)
                                                .addDeviceInfo(DEVICE_INFO_2)))))
                .build());
  }

  @Test
  public void getLabInfo_deviceView_group_singleDimensionValue() throws Exception {
    DeviceGroupOperation deviceGroupOperation =
        DeviceGroupOperation.newBuilder()
            .setDeviceGroupCondition(
                DeviceGroupCondition.newBuilder()
                    .setSingleDimensionValue(
                        SingleDimensionValue.newBuilder().setDimensionName("fake_dimension_name")))
            .build();
    GetLabInfoResponse response =
        labInfoService.doGetLabInfo(
            GetLabInfoRequest.newBuilder()
                .setLabQuery(
                    LabQuery.newBuilder()
                        .setDeviceViewRequest(
                            DeviceViewRequest.newBuilder()
                                .addDeviceGroupOperation(deviceGroupOperation)))
                .build());

    DeviceGroupResult deviceGroupResult =
        DeviceGroupResult.newBuilder()
            .setDeviceGroupOperation(deviceGroupOperation)
            .setDeviceGroupTotalCount(3)
            .addDeviceGroup(
                DeviceGroup.newBuilder()
                    .setDeviceGroupKey(
                        DeviceGroupKey.newBuilder()
                            .setHasDimensionValue(
                                HasDimensionValue.newBuilder()
                                    .setDimensionName("fake_dimension_name")
                                    .setNoDimensionValue(NoDimensionValue.getDefaultInstance())))
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(1)
                                    .addDeviceInfo(DEVICE_INFO_2))))
            .addDeviceGroup(
                DeviceGroup.newBuilder()
                    .setDeviceGroupKey(
                        DeviceGroupKey.newBuilder()
                            .setHasDimensionValue(
                                HasDimensionValue.newBuilder()
                                    .setDimensionName("fake_dimension_name")
                                    .setDimensionValue("fake_dimension_value_1")))
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(1)
                                    .addDeviceInfo(DEVICE_INFO_1))))
            .addDeviceGroup(
                DeviceGroup.newBuilder()
                    .setDeviceGroupKey(
                        DeviceGroupKey.newBuilder()
                            .setHasDimensionValue(
                                HasDimensionValue.newBuilder()
                                    .setDimensionName("fake_dimension_name")
                                    .setDimensionValue("fake_dimension_value_2")))
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(1)
                                    .addDeviceInfo(DEVICE_INFO_1))))
            .build();
    assertThat(response)
        .withPartialScope(FIELD_SCOPE)
        .isEqualTo(
            GetLabInfoResponse.newBuilder()
                .setLabQueryResult(
                    LabQueryResult.newBuilder()
                        .setDeviceView(
                            DeviceView.newBuilder()
                                .setGroupedDevices(
                                    GroupedDevices.newBuilder()
                                        .setDeviceGroupResult(deviceGroupResult))))
                .build());
  }

  @Test
  public void getLabInfo_deviceView_group_dimensionValueList() throws Exception {
    DeviceGroupOperation deviceGroupOperation =
        DeviceGroupOperation.newBuilder()
            .setDeviceGroupCondition(
                DeviceGroupCondition.newBuilder()
                    .setDimensionValueList(
                        DimensionValueList.newBuilder().setDimensionName("fake_dimension_name")))
            .build();
    GetLabInfoResponse response =
        labInfoService.doGetLabInfo(
            GetLabInfoRequest.newBuilder()
                .setLabQuery(
                    LabQuery.newBuilder()
                        .setDeviceViewRequest(
                            DeviceViewRequest.newBuilder()
                                .addDeviceGroupOperation(deviceGroupOperation)))
                .build());

    DeviceGroupResult deviceGroupResult =
        DeviceGroupResult.newBuilder()
            .setDeviceGroupOperation(deviceGroupOperation)
            .setDeviceGroupTotalCount(2)
            .addDeviceGroup(
                DeviceGroup.newBuilder()
                    .setDeviceGroupKey(
                        DeviceGroupKey.newBuilder()
                            .setHasDimensionValueList(
                                HasDimensionValueList.newBuilder()
                                    .setDimensionName("fake_dimension_name")
                                    .addDimensionValues("fake_dimension_value_1")
                                    .addDimensionValues("fake_dimension_value_2")))
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(1)
                                    .addDeviceInfo(DEVICE_INFO_1))))
            .addDeviceGroup(
                DeviceGroup.newBuilder()
                    .setDeviceGroupKey(
                        DeviceGroupKey.newBuilder()
                            .setHasDimensionValueList(
                                HasDimensionValueList.newBuilder()
                                    .setDimensionName("fake_dimension_name")))
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(1)
                                    .addDeviceInfo(DEVICE_INFO_2))))
            .build();
    assertThat(response)
        .withPartialScope(FIELD_SCOPE)
        .isEqualTo(
            GetLabInfoResponse.newBuilder()
                .setLabQueryResult(
                    LabQueryResult.newBuilder()
                        .setDeviceView(
                            DeviceView.newBuilder()
                                .setGroupedDevices(
                                    GroupedDevices.newBuilder()
                                        .setDeviceGroupResult(deviceGroupResult))))
                .build());
  }

  @Test
  public void getLabInfo_deviceView_group_typeList() throws Exception {
    DeviceGroupOperation deviceGroupOperation =
        DeviceGroupOperation.newBuilder()
            .setDeviceGroupCondition(
                DeviceGroupCondition.newBuilder().setTypeList(TypeList.getDefaultInstance()))
            .build();
    GetLabInfoResponse response =
        labInfoService.doGetLabInfo(
            GetLabInfoRequest.newBuilder()
                .setLabQuery(
                    LabQuery.newBuilder()
                        .setDeviceViewRequest(
                            DeviceViewRequest.newBuilder()
                                .addDeviceGroupOperation(deviceGroupOperation)))
                .build());

    DeviceGroupResult deviceGroupResult =
        DeviceGroupResult.newBuilder()
            .setDeviceGroupOperation(deviceGroupOperation)
            .setDeviceGroupTotalCount(2)
            .addDeviceGroup(
                DeviceGroup.newBuilder()
                    .setDeviceGroupKey(
                        DeviceGroupKey.newBuilder()
                            .setHasTypeList(
                                HasTypeList.newBuilder()
                                    .addTypes("AndroidRealDevice")
                                    .addTypes("NoOpDevice")))
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(1)
                                    .addDeviceInfo(DEVICE_INFO_1))))
            .addDeviceGroup(
                DeviceGroup.newBuilder()
                    .setDeviceGroupKey(
                        DeviceGroupKey.newBuilder()
                            .setHasTypeList(HasTypeList.newBuilder().addTypes("NoOpDevice")))
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(1)
                                    .addDeviceInfo(DEVICE_INFO_2))))
            .build();
    assertThat(response)
        .withPartialScope(FIELD_SCOPE)
        .isEqualTo(
            GetLabInfoResponse.newBuilder()
                .setLabQueryResult(
                    LabQueryResult.newBuilder()
                        .setDeviceView(
                            DeviceView.newBuilder()
                                .setGroupedDevices(
                                    GroupedDevices.newBuilder()
                                        .setDeviceGroupResult(deviceGroupResult))))
                .build());
  }

  @Test
  public void getLabInfo_deviceView_group_ownerList() throws Exception {
    DeviceGroupOperation deviceGroupOperation =
        DeviceGroupOperation.newBuilder()
            .setDeviceGroupCondition(
                DeviceGroupCondition.newBuilder().setOwnerList(OwnerList.getDefaultInstance()))
            .build();
    GetLabInfoResponse response =
        labInfoService.doGetLabInfo(
            GetLabInfoRequest.newBuilder()
                .setLabQuery(
                    LabQuery.newBuilder()
                        .setDeviceViewRequest(
                            DeviceViewRequest.newBuilder()
                                .addDeviceGroupOperation(deviceGroupOperation)))
                .build());

    DeviceGroupResult deviceGroupResult =
        DeviceGroupResult.newBuilder()
            .setDeviceGroupOperation(deviceGroupOperation)
            .setDeviceGroupTotalCount(2)
            .addDeviceGroup(
                DeviceGroup.newBuilder()
                    .setDeviceGroupKey(
                        DeviceGroupKey.newBuilder()
                            .setHasOwnerList(HasOwnerList.newBuilder().addOwners("fake_owner_1")))
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(1)
                                    .addDeviceInfo(DEVICE_INFO_1))))
            .addDeviceGroup(
                DeviceGroup.newBuilder()
                    .setDeviceGroupKey(
                        DeviceGroupKey.newBuilder()
                            .setHasOwnerList(
                                HasOwnerList.newBuilder()
                                    .addOwners("fake_owner_1")
                                    .addOwners("fake_owner_2")))
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(1)
                                    .addDeviceInfo(DEVICE_INFO_2))))
            .build();
    assertThat(response)
        .withPartialScope(FIELD_SCOPE)
        .isEqualTo(
            GetLabInfoResponse.newBuilder()
                .setLabQueryResult(
                    LabQueryResult.newBuilder()
                        .setDeviceView(
                            DeviceView.newBuilder()
                                .setGroupedDevices(
                                    GroupedDevices.newBuilder()
                                        .setDeviceGroupResult(deviceGroupResult))))
                .build());
  }

  @Test
  public void getLabInfo_deviceView_group_executorList() throws Exception {
    DeviceGroupOperation deviceGroupOperation =
        DeviceGroupOperation.newBuilder()
            .setDeviceGroupCondition(
                DeviceGroupCondition.newBuilder()
                    .setExecutorList(ExecutorList.getDefaultInstance()))
            .build();
    GetLabInfoResponse response =
        labInfoService.doGetLabInfo(
            GetLabInfoRequest.newBuilder()
                .setLabQuery(
                    LabQuery.newBuilder()
                        .setDeviceViewRequest(
                            DeviceViewRequest.newBuilder()
                                .addDeviceGroupOperation(deviceGroupOperation)))
                .build());

    DeviceGroupResult deviceGroupResult =
        DeviceGroupResult.newBuilder()
            .setDeviceGroupOperation(deviceGroupOperation)
            .setDeviceGroupTotalCount(1)
            .addDeviceGroup(
                DeviceGroup.newBuilder()
                    .setDeviceGroupKey(
                        DeviceGroupKey.newBuilder()
                            .setHasExecutorList(
                                HasExecutorList.newBuilder()
                                    .addExecutors("fake_executor_1")
                                    .addExecutors("fake_executor_2")))
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(2)
                                    .addDeviceInfo(DEVICE_INFO_1)
                                    .addDeviceInfo(DEVICE_INFO_2))))
            .build();
    assertThat(response)
        .withPartialScope(FIELD_SCOPE)
        .isEqualTo(
            GetLabInfoResponse.newBuilder()
                .setLabQueryResult(
                    LabQueryResult.newBuilder()
                        .setDeviceView(
                            DeviceView.newBuilder()
                                .setGroupedDevices(
                                    GroupedDevices.newBuilder()
                                        .setDeviceGroupResult(deviceGroupResult))))
                .build());
  }
}
