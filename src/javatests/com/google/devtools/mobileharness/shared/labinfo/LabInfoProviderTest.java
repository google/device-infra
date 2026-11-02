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
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupCondition;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupCondition.SingleDimensionValue;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest.DeviceGroupOperation;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask.DimensionsMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.shared.util.filter.CompiledDeviceInfoMask;
import com.google.devtools.mobileharness.shared.util.filter.CompiledLabInfoMask;
import com.google.protobuf.FieldMask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LabInfoProviderTest {

  private static final LabInfo RAW_LAB_INFO =
      LabInfo.newBuilder()
          .setLabLocator(LabLocator.newBuilder().setHostName("lab1"))
          .setLabStatus(LabStatus.LAB_RUNNING)
          .setLabServerFeature(LabServerFeature.getDefaultInstance())
          .build();

  private static final DeviceInfo RAW_DEVICE_INFO =
      DeviceInfo.newBuilder()
          .setDeviceLocator(DeviceLocator.newBuilder().setId("device1"))
          .setDeviceStatus(DeviceStatus.IDLE)
          .setDeviceFeature(
              DeviceFeature.newBuilder()
                  .addOwner("owner1")
                  .setCompositeDimension(
                      DeviceCompositeDimension.newBuilder()
                          .addSupportedDimension(
                              DeviceDimension.newBuilder().setName("model").setValue("pixel"))
                          .addSupportedDimension(
                              DeviceDimension.newBuilder().setName("sdk").setValue("34"))))
          .build();

  private static final LabView FULL_VIEW =
      LabView.newBuilder()
          .setLabTotalCount(1)
          .addLabData(
              LabData.newBuilder()
                  .setLabInfo(RAW_LAB_INFO)
                  .setDeviceList(
                      DeviceList.newBuilder()
                          .setDeviceTotalCount(1)
                          .addDeviceInfo(RAW_DEVICE_INFO)))
          .build();

  /** A provider that only knows how to build the full view, like a lab server's. */
  private static final LabInfoProvider FULL_VIEW_PROVIDER = filter -> FULL_VIEW;

  /** A provider that builds its view through MaskedLabInfoBuilder and MaskedDeviceInfoBuilder. */
  private static final LabInfoProvider MASKED_BUILDER_PROVIDER =
      new LabInfoProvider() {
        @Override
        public LabView getLabInfos(Filter filter) {
          return getLabInfos(
              filter, CompiledLabInfoMask.retainAll(), CompiledDeviceInfoMask.retainAll());
        }

        @Override
        public LabView getLabInfos(
            Filter filter, CompiledLabInfoMask labInfoMask, CompiledDeviceInfoMask deviceInfoMask) {
          LabData.Builder labData = LabData.newBuilder();
          labInfoMask
              .newMaskedLabInfoBuilder()
              .setMaskedLabLocator(
                  RAW_LAB_INFO, info -> info.getLabLocator().getHostName(), LabInfo::getLabLocator)
              .setMaskedLabStatus(RAW_LAB_INFO, LabInfo::getLabStatus)
              .setMaskedLabServerFeature(RAW_LAB_INFO, LabInfo::getLabServerFeature)
              .build()
              .ifPresent(labData::setLabInfo);
          DeviceList.Builder deviceList = DeviceList.newBuilder().setDeviceTotalCount(1);
          deviceInfoMask
              .newMaskedDeviceInfoBuilder()
              .setMaskedDeviceLocator(
                  RAW_DEVICE_INFO,
                  info -> info.getDeviceLocator().getId(),
                  DeviceInfo::getDeviceLocator)
              .setMaskedDeviceStatus(RAW_DEVICE_INFO, DeviceInfo::getDeviceStatus)
              .setMaskedDeviceFeature(RAW_DEVICE_INFO, DeviceInfo::getDeviceFeature)
              .build()
              .ifPresent(deviceList::addDeviceInfo);
          return LabView.newBuilder()
              .setLabTotalCount(1)
              .addLabData(labData.setDeviceList(deviceList))
              .build();
        }
      };

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

    assertThat(view).isEqualTo(FULL_VIEW);
  }

  @Test
  public void createNewLabQueryResult_pushdownDisabled_callsUnmaskedOverloadAndTrimsPostBuild()
      throws Exception {
    LabInfoProvider unmaskedOnlyProvider =
        new LabInfoProvider() {
          @Override
          public LabView getLabInfos(Filter filter) {
            return FULL_VIEW;
          }

          @Override
          public LabView getLabInfos(
              Filter filter,
              CompiledLabInfoMask labInfoMask,
              CompiledDeviceInfoMask deviceInfoMask) {
            throw new AssertionError("Masked overload should not be called when pushdown is off");
          }
        };
    LabQuery query =
        LabQuery.newBuilder()
            .setMask(
                Mask.newBuilder()
                    .setLabInfoMask(
                        LabInfoMask.newBuilder()
                            .setFieldMask(FieldMask.newBuilder().addPaths("lab_status")))
                    .setDeviceInfoMask(
                        DeviceInfoMask.newBuilder()
                            .setFieldMask(FieldMask.newBuilder().addPaths("device_status"))))
            .build();

    LabQueryResult pushdownOffResult =
        LabQueryUtils.createNewLabQueryResult(
            query, unmaskedOnlyProvider, /* useProjectionPushdown= */ false);
    LabQueryResult pushdownOnResult =
        LabQueryUtils.createNewLabQueryResult(
            query, MASKED_BUILDER_PROVIDER, /* useProjectionPushdown= */ true);

    assertThat(pushdownOffResult.toBuilder().clearTimestamp().build())
        .isEqualTo(pushdownOnResult.toBuilder().clearTimestamp().build());
  }

  @Test
  public void
      createNewLabQueryResult_deviceViewGroupByWithDimensionWhitelist_matchesBetweenPushdownOnAndOff()
          throws Exception {
    LabQuery query =
        LabQuery.newBuilder()
            .setDeviceViewRequest(
                DeviceViewRequest.newBuilder()
                    .addDeviceGroupOperation(
                        DeviceGroupOperation.newBuilder()
                            .setDeviceGroupCondition(
                                DeviceGroupCondition.newBuilder()
                                    .setSingleDimensionValue(
                                        SingleDimensionValue.newBuilder()
                                            .setDimensionName("sdk")))))
            .setMask(
                Mask.newBuilder()
                    .setLabInfoMask(
                        LabInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()))
                    .setDeviceInfoMask(
                        DeviceInfoMask.newBuilder()
                            .setFieldMask(
                                FieldMask.newBuilder()
                                    .addPaths("device_status")
                                    .addPaths("device_feature.composite_dimension"))
                            .setSupportedDimensionsMask(
                                DimensionsMask.newBuilder().addDimensionNames("model"))))
            .build();

    LabQueryResult pushdownOffResult =
        LabQueryUtils.createNewLabQueryResult(
            query, MASKED_BUILDER_PROVIDER, /* useProjectionPushdown= */ false);
    LabQueryResult pushdownOnResult =
        LabQueryUtils.createNewLabQueryResult(
            query, MASKED_BUILDER_PROVIDER, /* useProjectionPushdown= */ true);

    assertThat(pushdownOffResult.toBuilder().clearTimestamp().build())
        .isEqualTo(pushdownOnResult.toBuilder().clearTimestamp().build());
  }
}
