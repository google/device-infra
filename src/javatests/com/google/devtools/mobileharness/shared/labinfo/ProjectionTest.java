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
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceGroup;
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
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupCondition.SingleStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupCondition.TypeList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupOperation;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask.DimensionsMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.shared.labinfo.Projection.AddedSortKey;
import com.google.devtools.mobileharness.shared.util.filter.CompiledDeviceInfoMask;
import com.google.devtools.mobileharness.shared.util.filter.CompiledLabInfoMask;
import com.google.devtools.mobileharness.shared.util.filter.MaskUtils;
import com.google.protobuf.FieldMask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ProjectionTest {

  private static final LabInfoMask LAB_STATUS_ONLY =
      LabInfoMask.newBuilder().setFieldMask(FieldMask.newBuilder().addPaths("lab_status")).build();

  private static final DeviceInfoMask DEVICE_STATUS_ONLY =
      DeviceInfoMask.newBuilder()
          .setFieldMask(FieldMask.newBuilder().addPaths("device_status"))
          .build();

  private static LabQuery labViewQuery(Mask mask) {
    return LabQuery.newBuilder().setMask(mask).build();
  }

  private static LabQuery deviceViewQuery(Mask mask, DeviceGroupCondition... groupConditions) {
    DeviceViewRequest.Builder deviceView = DeviceViewRequest.newBuilder();
    for (DeviceGroupCondition condition : groupConditions) {
      deviceView.addDeviceGroupOperation(
          DeviceGroupOperation.newBuilder().setDeviceGroupCondition(condition));
    }
    return LabQuery.newBuilder().setMask(mask).setDeviceViewRequest(deviceView).build();
  }

  private static DeviceGroupCondition groupByDimension(String name) {
    return DeviceGroupCondition.newBuilder()
        .setSingleDimensionValue(SingleDimensionValue.newBuilder().setDimensionName(name))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }

  private static DeviceInfoMask deviceFieldMask(String... paths) {
    FieldMask.Builder fieldMask = FieldMask.newBuilder();
    for (String path : paths) {
      fieldMask.addPaths(path);
    }
    return DeviceInfoMask.newBuilder().setFieldMask(fieldMask).build();
  }

  /** A lab view result with one lab and one device, as a push-down provider would produce it. */
  private static LabQueryResult.Builder labViewResult(LabInfo labInfo, DeviceInfo deviceInfo) {
    return LabQueryResult.newBuilder()
        .setLabView(
            LabView.newBuilder()
                .addLabData(
                    LabData.newBuilder()
                        .setLabInfo(labInfo)
                        .setDeviceList(
                            DeviceList.newBuilder()
                                .setDeviceTotalCount(1)
                                .addDeviceInfo(deviceInfo))));
  }

  @Test
  public void of_noMask_retainsEverythingAndWidensNothing() {
    Projection projection = Projection.of(LabQuery.getDefaultInstance());

    assertThat(projection.labInfoMask()).isSameInstanceAs(CompiledLabInfoMask.retainAll());
    assertThat(projection.deviceInfoMask()).isSameInstanceAs(CompiledDeviceInfoMask.retainAll());
    assertThat(projection.widening().isEmpty()).isTrue();

    LabQueryResult.Builder result =
        labViewResult(
            LabInfo.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName("host1"))
                .build(),
            DeviceInfo.newBuilder()
                .setDeviceLocator(DeviceLocator.newBuilder().setId("dev1"))
                .build());
    LabQueryResult before = result.build();
    projection.undoWidening(result);
    assertThat(result.build()).isEqualTo(before);
  }

  @Test
  public void of_sortKeysAlreadyRequested_widensNothing() {
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(
                LabInfoMask.newBuilder()
                    .setFieldMask(FieldMask.newBuilder().addPaths("lab_locator")))
            .setDeviceInfoMask(deviceFieldMask("device_locator"))
            .build();

    assertThat(Projection.of(labViewQuery(mask)).widening().isEmpty()).isTrue();
  }

  @Test
  public void of_labView_dropAllMasks_keepsHostNameForSortingAndDropsDevices() {
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(LabInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
            .build();

    Projection projection = Projection.of(labViewQuery(mask));

    assertThat(projection.labInfoMask().keepsLabInfo()).isTrue();
    assertThat(projection.labInfoMask().keepsLabLocator()).isTrue();
    assertThat(projection.labInfoMask().keepsLabStatus()).isFalse();
    assertThat(projection.deviceInfoMask().keepsDeviceInfo()).isFalse();
    assertThat(projection.widening().labHostName()).isEqualTo(AddedSortKey.CLEAR_MESSAGE);
    assertThat(projection.widening().deviceId()).isEqualTo(AddedSortKey.NOT_ADDED);
    assertThat(projection.widening().touchesDevices()).isFalse();

    LabQueryResult.Builder undone =
        LabQueryResult.newBuilder()
            .setLabView(
                LabView.newBuilder()
                    .addLabData(
                        LabData.newBuilder()
                            .setLabInfo(
                                LabInfo.newBuilder()
                                    .setLabLocator(LabLocator.newBuilder().setHostName("host1")))
                            .setDeviceList(DeviceList.newBuilder().setDeviceTotalCount(2))));
    LabQueryResult.Builder trimmed = undone.clone();

    projection.undoWidening(undone);
    MaskUtils.trimLabQueryResult(trimmed, mask);

    assertThat(undone.getLabView().getLabData(0).hasLabInfo()).isFalse();
    assertThat(undone.getLabView().getLabData(0).getDeviceList().getDeviceTotalCount())
        .isEqualTo(2);
    assertThat(undone.build()).isEqualTo(trimmed.build());
  }

  @Test
  public void of_deviceView_dropAllMasks_dropsLabInfoAndClearsDevicesAfterGrouping() {
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(LabInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
            .build();

    Projection projection = Projection.of(deviceViewQuery(mask, groupByDimension("pool")));

    assertThat(projection.labInfoMask().keepsLabInfo()).isFalse();
    assertThat(projection.deviceInfoMask().keepsDeviceInfo()).isTrue();
    assertThat(projection.deviceInfoMask().keepsDeviceLocator()).isTrue();
    assertThat(projection.deviceInfoMask().keepsSupportedDimensions()).isTrue();
    assertThat(projection.deviceInfoMask().keepsRequiredDimensions()).isTrue();
    assertThat(projection.widening().labHostName()).isEqualTo(AddedSortKey.NOT_ADDED);
    assertThat(projection.widening().deviceId()).isEqualTo(AddedSortKey.CLEAR_MESSAGE);

    DeviceInfo widenedDevice =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("dev1"))
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("pool", "shared"))))
            .build();
    LabQueryResult.Builder undone =
        LabQueryResult.newBuilder()
            .setDeviceView(
                DeviceView.newBuilder()
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceGroupResult(
                                DeviceGroupResult.newBuilder()
                                    .setDeviceGroupTotalCount(1)
                                    .addDeviceGroup(
                                        DeviceGroup.newBuilder()
                                            .setGroupedDevices(
                                                GroupedDevices.newBuilder()
                                                    .setDeviceList(
                                                        DeviceList.newBuilder()
                                                            .setDeviceTotalCount(1)
                                                            .addDeviceInfo(widenedDevice)))))));
    LabQueryResult.Builder trimmed = undone.clone();

    projection.undoWidening(undone);
    MaskUtils.trimLabQueryResult(trimmed, mask);

    DeviceList leafList =
        undone
            .getDeviceView()
            .getGroupedDevices()
            .getDeviceGroupResult()
            .getDeviceGroup(0)
            .getGroupedDevices()
            .getDeviceList();
    assertThat(leafList.getDeviceTotalCount()).isEqualTo(1);
    assertThat(leafList.getDeviceInfoList()).isEmpty();
    assertThat(undone.build()).isEqualTo(trimmed.build());
  }

  @Test
  public void of_labView_keepsHostNameAndDeviceIdForSorting() {
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(LAB_STATUS_ONLY)
            .setDeviceInfoMask(DEVICE_STATUS_ONLY)
            .build();

    Projection projection = Projection.of(labViewQuery(mask));

    assertThat(projection.labInfoMask().keepsLabLocator()).isTrue();
    assertThat(projection.labInfoMask().keepsLabStatus()).isTrue();
    assertThat(projection.deviceInfoMask().keepsDeviceLocator()).isTrue();
    assertThat(projection.deviceInfoMask().keepsDeviceStatus()).isTrue();
    assertThat(projection.widening().labHostName()).isEqualTo(AddedSortKey.CLEAR_LOCATOR);
    assertThat(projection.widening().deviceId()).isEqualTo(AddedSortKey.CLEAR_LOCATOR);
  }

  @Test
  public void of_labView_sortKeyNextToRequestedSiblings_isClearedAlone() {
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(
                LabInfoMask.newBuilder()
                    .setFieldMask(FieldMask.newBuilder().addPaths("lab_locator.ip")))
            .setDeviceInfoMask(deviceFieldMask("device_locator.lab_locator"))
            .build();

    Projection projection = Projection.of(labViewQuery(mask));

    assertThat(projection.widening().labHostName()).isEqualTo(AddedSortKey.CLEAR_KEY);
    assertThat(projection.widening().deviceId()).isEqualTo(AddedSortKey.CLEAR_KEY);
  }

  @Test
  public void of_deviceView_doesNotKeepHostNameBecauseLabsAreNotSorted() {
    Mask mask = Mask.newBuilder().setLabInfoMask(LAB_STATUS_ONLY).build();

    Projection projection = Projection.of(deviceViewQuery(mask));

    assertThat(projection.labInfoMask().keepsLabLocator()).isFalse();
    assertThat(projection.widening().isEmpty()).isTrue();
  }

  @Test
  public void of_groupByType_keepsOnlyThatFeatureSubField() {
    Mask mask = Mask.newBuilder().setDeviceInfoMask(deviceFieldMask("device_locator.id")).build();
    DeviceGroupCondition byType =
        DeviceGroupCondition.newBuilder().setTypeList(TypeList.getDefaultInstance()).build();

    Projection projection = Projection.of(deviceViewQuery(mask, byType));

    assertThat(projection.deviceInfoMask().keepsDeviceFeature()).isTrue();
    assertThat(projection.deviceInfoMask().keepsSupportedDimensions()).isFalse();
    assertThat(projection.deviceInfoMask().keepsDeviceCondition()).isFalse();
    assertThat(projection.widening().deviceFeature()).isTrue();
    assertThat(projection.widening().featureFields())
        .containsExactly(Projection.WidenedFeatureField.TYPE);

    LabQueryResult.Builder result =
        LabQueryResult.newBuilder()
            .setDeviceView(
                DeviceView.newBuilder()
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(1)
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder().setId("dev1"))
                                            .setDeviceFeature(
                                                DeviceFeature.newBuilder()
                                                    .addType("AndroidRealDevice"))))));
    projection.undoWidening(result);
    assertThat(
            result
                .getDeviceView()
                .getGroupedDevices()
                .getDeviceList()
                .getDeviceInfo(0)
                .hasDeviceFeature())
        .isFalse();
  }

  @Test
  public void of_groupByOwnerExecutorDimensionValueListAndUnset_widensAndClearsExpectedFields() {
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(deviceFieldMask("device_locator.id", "device_feature.driver"))
            .build();
    DeviceGroupCondition byOwner =
        DeviceGroupCondition.newBuilder().setOwnerList(OwnerList.getDefaultInstance()).build();
    DeviceGroupCondition byExecutor =
        DeviceGroupCondition.newBuilder()
            .setExecutorList(ExecutorList.getDefaultInstance())
            .build();
    DeviceGroupCondition byType =
        DeviceGroupCondition.newBuilder().setTypeList(TypeList.getDefaultInstance()).build();
    DeviceGroupCondition unset = DeviceGroupCondition.getDefaultInstance();

    Projection projection =
        Projection.of(deviceViewQuery(mask, byOwner, byExecutor, byType, unset));

    assertThat(projection.widening().deviceFeature()).isFalse();
    assertThat(projection.widening().featureFields())
        .containsExactly(
            Projection.WidenedFeatureField.OWNER,
            Projection.WidenedFeatureField.EXECUTOR,
            Projection.WidenedFeatureField.TYPE);

    LabQueryResult.Builder result =
        LabQueryResult.newBuilder()
            .setDeviceView(
                DeviceView.newBuilder()
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(1)
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder().setId("dev1"))
                                            .setDeviceFeature(
                                                DeviceFeature.newBuilder()
                                                    .addDriver("NoOpDriver")
                                                    .addOwner("owner1")
                                                    .addExecutor("exec1")
                                                    .addType("AndroidRealDevice"))))));
    projection.undoWidening(result);
    DeviceFeature undoneFeature =
        result
            .getDeviceView()
            .getGroupedDevices()
            .getDeviceList()
            .getDeviceInfo(0)
            .getDeviceFeature();
    assertThat(undoneFeature.getDriverList()).containsExactly("NoOpDriver");
    assertThat(undoneFeature.getOwnerList()).isEmpty();
    assertThat(undoneFeature.getExecutorList()).isEmpty();
    assertThat(undoneFeature.getTypeList()).isEmpty();

    Mask dimensionWhitelistMask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder()
                    .setFieldMask(FieldMask.newBuilder().addPaths("device_locator.id"))
                    .setSupportedDimensionsMask(
                        DimensionsMask.newBuilder().addDimensionNames("model")))
            .build();
    DeviceGroupCondition byDimensionValueList =
        DeviceGroupCondition.newBuilder()
            .setDimensionValueList(DimensionValueList.newBuilder().setDimensionName("Pool"))
            .build();
    Projection dimListProjection =
        Projection.of(deviceViewQuery(dimensionWhitelistMask, byDimensionValueList));
    assertThat(dimListProjection.deviceInfoMask().keepsSupportedDimensions()).isTrue();
    assertThat(dimListProjection.deviceInfoMask().keepsRequiredDimensions()).isTrue();
    assertThat(dimListProjection.widening().featureFields())
        .containsExactly(
            Projection.WidenedFeatureField.SUPPORTED_DIMENSION,
            Projection.WidenedFeatureField.REQUIRED_DIMENSION);
    assertThat(dimListProjection.widening().supportedDimensionNames()).isPresent();
    assertThat(dimListProjection.widening().supportedDimensionNames().get().matches("pool"))
        .isTrue();

    assertThat(
            Projection.addsCompositeDimension(
                ImmutableSet.of(Projection.WidenedFeatureField.SUPPORTED_DIMENSION)))
        .isTrue();
    assertThat(
            Projection.addsCompositeDimension(
                ImmutableSet.of(Projection.WidenedFeatureField.REQUIRED_DIMENSION)))
        .isTrue();
    assertThat(
            Projection.addsCompositeDimension(ImmutableSet.of(Projection.WidenedFeatureField.TYPE)))
        .isFalse();
  }

  @Test
  public void of_groupByStatus_keepsDeviceStatus() {
    Mask mask = Mask.newBuilder().setDeviceInfoMask(deviceFieldMask("device_locator.id")).build();
    DeviceGroupCondition byStatus =
        DeviceGroupCondition.newBuilder()
            .setSingleStatus(SingleStatus.getDefaultInstance())
            .build();

    Projection projection = Projection.of(deviceViewQuery(mask, byStatus));

    assertThat(projection.deviceInfoMask().keepsDeviceStatus()).isTrue();
    assertThat(projection.widening().deviceStatus()).isTrue();
  }

  @Test
  public void of_groupByDimension_addsDimensionToNonEmptyWhitelistsOnly() {
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder()
                    .setFieldMask(
                        FieldMask.newBuilder()
                            .addPaths("device_locator.id")
                            .addPaths("device_feature"))
                    .setSupportedDimensionsMask(
                        DimensionsMask.newBuilder().addDimensionNames("model"))
                    .setRequiredDimensionsMask(DimensionsMask.getDefaultInstance()))
            .build();
    DeviceDimension model = dimension("model", "pixel");
    DeviceDimension supportedPool = dimension("pool", "shared");
    DeviceDimension carrier = dimension("carrier", "verizon");
    DeviceDimension requiredPool = dimension("pool", "required");
    DeviceFeature feature =
        DeviceFeature.newBuilder()
            .setCompositeDimension(
                DeviceCompositeDimension.newBuilder()
                    .addSupportedDimension(model)
                    .addSupportedDimension(supportedPool)
                    .addSupportedDimension(carrier)
                    .addRequiredDimension(requiredPool))
            .build();

    Projection projection = Projection.of(deviceViewQuery(mask, groupByDimension("Pool")));

    DeviceCompositeDimension projected =
        projection.deviceInfoMask().projectDeviceFeature(feature).getCompositeDimension();
    // "pool" joins the supported whitelist next to "model"; the empty required whitelist already
    // keeps everything and stays empty.
    assertThat(projected.getSupportedDimensionList()).containsExactly(model, supportedPool);
    assertThat(projected.getRequiredDimensionList()).containsExactly(requiredPool);
    assertThat(projection.widening().supportedDimensionNames()).isPresent();
    assertThat(projection.widening().requiredDimensionNames()).isEmpty();
    assertThat(projection.widening().touchesDevices()).isTrue();
    assertThat(projection.widening().isEmpty()).isFalse();

    Mask requiredWhitelistMask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder()
                    .setFieldMask(
                        FieldMask.newBuilder()
                            .addPaths("device_locator.id")
                            .addPaths("device_feature"))
                    .setRequiredDimensionsMask(
                        DimensionsMask.newBuilder().addDimensionNames("carrier")))
            .build();
    Projection requiredProjection =
        Projection.of(deviceViewQuery(requiredWhitelistMask, groupByDimension("Pool")));
    DeviceCompositeDimension requiredProjected =
        requiredProjection.deviceInfoMask().projectDeviceFeature(feature).getCompositeDimension();
    assertThat(requiredProjected.getRequiredDimensionList()).containsExactly(requiredPool);
    assertThat(requiredProjection.widening().supportedDimensionNames()).isEmpty();
    assertThat(requiredProjection.widening().requiredDimensionNames()).isPresent();
    assertThat(requiredProjection.widening().touchesDevices()).isTrue();
    assertThat(requiredProjection.widening().isEmpty()).isFalse();
  }

  @Test
  public void of_groupByDimensionAlreadyWhitelisted_widensNothing() {
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder()
                    .setFieldMask(
                        FieldMask.newBuilder()
                            .addPaths("device_locator.id")
                            .addPaths("device_feature"))
                    .setSupportedDimensionsMask(
                        DimensionsMask.newBuilder().addDimensionNames("Pool")))
            .build();

    assertThat(Projection.of(deviceViewQuery(mask, groupByDimension("pool"))).widening().isEmpty())
        .isTrue();
  }

  @Test
  public void of_groupByDimensionWithOnlySupportedDimensionInFieldMask_widensRequiredDimension() {
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                deviceFieldMask(
                    "device_locator.id", "device_feature.composite_dimension.supported_dimension"))
            .build();

    Projection projection = Projection.of(deviceViewQuery(mask, groupByDimension("pool")));

    assertThat(projection.deviceInfoMask().keepsSupportedDimensions()).isTrue();
    assertThat(projection.deviceInfoMask().keepsRequiredDimensions()).isTrue();
    assertThat(projection.widening().deviceFeature()).isFalse();
    assertThat(projection.widening().compositeDimension()).isFalse();
    assertThat(projection.widening().featureFields())
        .containsExactly(Projection.WidenedFeatureField.REQUIRED_DIMENSION);

    DeviceDimension supported = dimension("pool", "shared");
    DeviceDimension required = dimension("pool", "required");
    LabQueryResult.Builder result =
        LabQueryResult.newBuilder()
            .setDeviceView(
                DeviceView.newBuilder()
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(1)
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder().setId("dev1"))
                                            .setDeviceFeature(
                                                DeviceFeature.newBuilder()
                                                    .setCompositeDimension(
                                                        DeviceCompositeDimension.newBuilder()
                                                            .addSupportedDimension(supported)
                                                            .addRequiredDimension(required))))
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder().setId("dev2"))
                                            .setDeviceFeature(
                                                DeviceFeature.newBuilder()
                                                    .addType("AndroidRealDevice"))))));

    projection.undoWidening(result);

    DeviceInfo undone = result.getDeviceView().getGroupedDevices().getDeviceList().getDeviceInfo(0);
    assertThat(undone.getDeviceFeature().getCompositeDimension().getSupportedDimensionList())
        .containsExactly(supported);
    assertThat(undone.getDeviceFeature().getCompositeDimension().getRequiredDimensionList())
        .isEmpty();
    DeviceInfo undoneWithoutDimensions =
        result.getDeviceView().getGroupedDevices().getDeviceList().getDeviceInfo(1);
    assertThat(undoneWithoutDimensions.getDeviceFeature().hasCompositeDimension()).isFalse();
  }

  @Test
  public void of_groupByDimensionWithOnlyRequiredDimensionInFieldMask_widensSupportedDimension() {
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                deviceFieldMask(
                    "device_locator.id", "device_feature.composite_dimension.required_dimension"))
            .build();

    Projection projection = Projection.of(deviceViewQuery(mask, groupByDimension("pool")));

    assertThat(projection.deviceInfoMask().keepsSupportedDimensions()).isTrue();
    assertThat(projection.deviceInfoMask().keepsRequiredDimensions()).isTrue();
    assertThat(projection.widening().deviceFeature()).isFalse();
    assertThat(projection.widening().compositeDimension()).isFalse();
    assertThat(projection.widening().featureFields())
        .containsExactly(Projection.WidenedFeatureField.SUPPORTED_DIMENSION);

    DeviceDimension supported = dimension("pool", "shared");
    DeviceDimension required = dimension("pool", "required");
    LabQueryResult.Builder result =
        LabQueryResult.newBuilder()
            .setDeviceView(
                DeviceView.newBuilder()
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(2)
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder().setId("dev1"))
                                            .setDeviceFeature(
                                                DeviceFeature.newBuilder()
                                                    .setCompositeDimension(
                                                        DeviceCompositeDimension.newBuilder()
                                                            .addSupportedDimension(supported)
                                                            .addRequiredDimension(required))))
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder().setId("dev2"))
                                            .setDeviceFeature(
                                                DeviceFeature.newBuilder()
                                                    .addType("AndroidRealDevice"))))));

    projection.undoWidening(result);

    DeviceInfo undone = result.getDeviceView().getGroupedDevices().getDeviceList().getDeviceInfo(0);
    assertThat(undone.getDeviceFeature().getCompositeDimension().getSupportedDimensionList())
        .isEmpty();
    assertThat(undone.getDeviceFeature().getCompositeDimension().getRequiredDimensionList())
        .containsExactly(required);
    DeviceInfo undoneWithoutDimensions =
        result.getDeviceView().getGroupedDevices().getDeviceList().getDeviceInfo(1);
    assertThat(undoneWithoutDimensions.getDeviceFeature().hasCompositeDimension()).isFalse();
  }

  @Test
  public void undoWidening_groupByDimensionWithRequestedFeatureSibling_clearsCompositeDimension() {
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(deviceFieldMask("device_locator.id", "device_feature.type"))
            .build();

    Projection projection = Projection.of(deviceViewQuery(mask, groupByDimension("pool")));

    assertThat(projection.widening().deviceFeature()).isFalse();
    assertThat(projection.widening().compositeDimension()).isTrue();

    LabQueryResult.Builder result =
        LabQueryResult.newBuilder()
            .setDeviceView(
                DeviceView.newBuilder()
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .setDeviceTotalCount(1)
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder().setId("dev1"))
                                            .setDeviceFeature(
                                                DeviceFeature.newBuilder()
                                                    .addType("AndroidRealDevice")
                                                    .setCompositeDimension(
                                                        DeviceCompositeDimension.newBuilder()
                                                            .addSupportedDimension(
                                                                dimension("pool", "shared"))))))));

    projection.undoWidening(result);

    DeviceInfo undone = result.getDeviceView().getGroupedDevices().getDeviceList().getDeviceInfo(0);
    assertThat(undone.getDeviceFeature().getTypeList()).containsExactly("AndroidRealDevice");
    assertThat(undone.getDeviceFeature().hasCompositeDimension()).isFalse();
  }

  @Test
  public void undoWidening_removesSortKeysKeptOnlyForSorting() {
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(LAB_STATUS_ONLY)
            .setDeviceInfoMask(DEVICE_STATUS_ONLY)
            .build();
    Projection projection = Projection.of(labViewQuery(mask));
    LabQueryResult.Builder result =
        labViewResult(
            LabInfo.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName("host1"))
                .setLabStatus(LabStatus.LAB_RUNNING)
                .build(),
            DeviceInfo.newBuilder()
                .setDeviceLocator(DeviceLocator.newBuilder().setId("dev1"))
                .setDeviceStatus(DeviceStatus.IDLE)
                .build());

    projection.undoWidening(result);

    LabData undone = result.getLabView().getLabData(0);
    assertThat(undone.getLabInfo())
        .isEqualTo(LabInfo.newBuilder().setLabStatus(LabStatus.LAB_RUNNING).build());
    assertThat(undone.getDeviceList().getDeviceTotalCount()).isEqualTo(1);
    assertThat(undone.getDeviceList().getDeviceInfo(0))
        .isEqualTo(DeviceInfo.newBuilder().setDeviceStatus(DeviceStatus.IDLE).build());
  }

  @Test
  public void undoWidening_keepsRequestedLocatorSiblings() {
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(
                LabInfoMask.newBuilder()
                    .setFieldMask(FieldMask.newBuilder().addPaths("lab_locator.ip")))
            .setDeviceInfoMask(deviceFieldMask("device_locator.lab_locator"))
            .build();
    Projection projection = Projection.of(labViewQuery(mask));
    LabLocator labLocator = LabLocator.newBuilder().setHostName("host1").setIp("1.2.3.4").build();
    LabQueryResult.Builder result =
        labViewResult(
            LabInfo.newBuilder().setLabLocator(labLocator).build(),
            DeviceInfo.newBuilder()
                .setDeviceLocator(
                    DeviceLocator.newBuilder().setId("dev1").setLabLocator(labLocator))
                .build());

    projection.undoWidening(result);

    LabData undone = result.getLabView().getLabData(0);
    assertThat(undone.getLabInfo())
        .isEqualTo(
            LabInfo.newBuilder().setLabLocator(LabLocator.newBuilder().setIp("1.2.3.4")).build());
    assertThat(undone.getDeviceList().getDeviceInfo(0))
        .isEqualTo(
            DeviceInfo.newBuilder()
                .setDeviceLocator(DeviceLocator.newBuilder().setLabLocator(labLocator))
                .build());
  }

  @Test
  public void undoWidening_deviceView_removesGroupByFieldsAndDimensionsAtEveryDepth() {
    Mask mask =
        Mask.newBuilder()
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder()
                    .setFieldMask(
                        FieldMask.newBuilder()
                            .addPaths("device_locator.id")
                            .addPaths("device_feature"))
                    .setSupportedDimensionsMask(
                        DimensionsMask.newBuilder().addDimensionNames("model"))
                    .setRequiredDimensionsMask(
                        DimensionsMask.newBuilder().addDimensionNames("label")))
            .build();
    DeviceGroupCondition byStatus =
        DeviceGroupCondition.newBuilder()
            .setSingleStatus(SingleStatus.getDefaultInstance())
            .build();
    Projection projection =
        Projection.of(deviceViewQuery(mask, byStatus, groupByDimension("pool")));
    DeviceDimension model = dimension("model", "pixel");
    DeviceDimension label = dimension("label", "prod");
    DeviceInfo widened =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("dev1"))
            .setDeviceStatus(DeviceStatus.BUSY)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addOwner("owner1")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(model)
                            .addSupportedDimension(dimension("pool", "shared"))
                            .addRequiredDimension(label)
                            .addRequiredDimension(dimension("pool", "required"))))
            .build();
    LabQueryResult.Builder result =
        LabQueryResult.newBuilder()
            .setDeviceView(
                DeviceView.newBuilder()
                    .setGroupedDevices(
                        GroupedDevices.newBuilder()
                            .setDeviceGroupResult(
                                DeviceGroupResult.newBuilder()
                                    .addDeviceGroup(
                                        DeviceGroup.newBuilder()
                                            .setGroupedDevices(
                                                GroupedDevices.newBuilder()
                                                    .setDeviceList(
                                                        DeviceList.newBuilder()
                                                            .setDeviceTotalCount(1)
                                                            .addDeviceInfo(widened)))))));

    projection.undoWidening(result);

    DeviceInfo undone =
        result
            .getDeviceView()
            .getGroupedDevices()
            .getDeviceGroupResult()
            .getDeviceGroup(0)
            .getGroupedDevices()
            .getDeviceList()
            .getDeviceInfo(0);
    assertThat(undone)
        .isEqualTo(
            DeviceInfo.newBuilder()
                .setDeviceLocator(DeviceLocator.newBuilder().setId("dev1"))
                .setDeviceFeature(
                    DeviceFeature.newBuilder()
                        .addOwner("owner1")
                        .setCompositeDimension(
                            DeviceCompositeDimension.newBuilder()
                                .addSupportedDimension(model)
                                .addRequiredDimension(label)))
                .build());
  }

  @Test
  public void undoWidening_matchesTrimmingWithClientMask() {
    Mask mask =
        Mask.newBuilder()
            .setLabInfoMask(LAB_STATUS_ONLY)
            .setDeviceInfoMask(
                DeviceInfoMask.newBuilder()
                    .setFieldMask(
                        FieldMask.newBuilder()
                            .addPaths("device_status")
                            .addPaths("device_feature.composite_dimension"))
                    .setSupportedDimensionsMask(
                        DimensionsMask.newBuilder().addDimensionNames("model")))
            .build();
    Projection projection = Projection.of(labViewQuery(mask));
    LabQueryResult.Builder undone =
        labViewResult(
            LabInfo.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName("host1"))
                .setLabStatus(LabStatus.LAB_RUNNING)
                .build(),
            DeviceInfo.newBuilder()
                .setDeviceLocator(DeviceLocator.newBuilder().setId("dev1"))
                .setDeviceStatus(DeviceStatus.IDLE)
                .setDeviceFeature(
                    DeviceFeature.newBuilder()
                        .setCompositeDimension(
                            DeviceCompositeDimension.newBuilder()
                                .addSupportedDimension(dimension("model", "pixel"))))
                .build());
    LabQueryResult.Builder trimmed = undone.clone();

    projection.undoWidening(undone);
    MaskUtils.trimLabQueryResult(trimmed, mask);

    assertThat(undone.build()).isEqualTo(trimmed.build());
  }
}
