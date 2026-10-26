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

package com.google.devtools.mobileharness.fe.v6.service.config.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BatchDeviceConfigUtilTest {

  @Test
  public void cleanDimensionName_stripsDimPrefixAndTrims() {
    assertThat(BatchDeviceConfigUtil.cleanDimensionName("  dim::pool  ")).isEqualTo("pool");
    assertThat(BatchDeviceConfigUtil.cleanDimensionName("DIM::custom_key")).isEqualTo("custom_key");
    assertThat(BatchDeviceConfigUtil.cleanDimensionName("label")).isEqualTo("label");
    assertThat(BatchDeviceConfigUtil.cleanDimensionName(null)).isEmpty();
  }

  @Test
  public void isValidConfigurableDimensionKey_checksCharactersAndSystemBlocklist() {
    assertThat(BatchDeviceConfigUtil.isValidConfigurableDimensionKey("pool")).isTrue();
    assertThat(BatchDeviceConfigUtil.isValidConfigurableDimensionKey("custom_key-1.0")).isTrue();
    assertThat(BatchDeviceConfigUtil.isValidConfigurableDimensionKey("")).isFalse();
    assertThat(BatchDeviceConfigUtil.isValidConfigurableDimensionKey("model")).isFalse();
    assertThat(BatchDeviceConfigUtil.isValidConfigurableDimensionKey("sdk_version")).isFalse();
    assertThat(BatchDeviceConfigUtil.isValidConfigurableDimensionKey("subdevice_dimensions"))
        .isFalse();
    assertThat(BatchDeviceConfigUtil.isValidConfigurableDimensionKey("UpperCase")).isFalse();
    assertThat(BatchDeviceConfigUtil.isValidConfigurableDimensionKey("invalid space")).isFalse();
  }

  @Test
  public void checkDimensionTarget_validatesNameAndDimensionList() {
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "pool", DimensionList.DIMENSION_LIST_REQUIRED))
        .isEmpty();
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "label", DimensionList.DIMENSION_LIST_SUPPORTED))
        .isEmpty();
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget("", DimensionList.DIMENSION_LIST_REQUIRED))
        .hasValue("Dimension name must not be empty.");
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "model", DimensionList.DIMENSION_LIST_SUPPORTED))
        .hasValue(
            "\"model\" is automatically detected by OmniLab and cannot be configured manually.");
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "bad key", DimensionList.DIMENSION_LIST_SUPPORTED))
        .hasValue("Invalid dimension name: bad key");
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "pool", DimensionList.DIMENSION_LIST_UNSPECIFIED))
        .hasValue("Dimension list must be DIMENSION_LIST_SUPPORTED or DIMENSION_LIST_REQUIRED.");
  }

  @Test
  public void canonicalizeValues_sortsAndLowercases() {
    assertThat(BatchDeviceConfigUtil.canonicalizeValues(ImmutableList.of("Beta", "", "alpha")))
        .containsExactly("alpha", "beta")
        .inOrder();
  }

  @Test
  public void getDimensionValues_handlesEmptyNameSupportedAndRequiredLists() {
    BasicDeviceConfig basicConfig =
        BasicDeviceConfig.newBuilder()
            .setCompositeDimension(
                DeviceCompositeDimension.newBuilder()
                    .addSupportedDimension(
                        DeviceDimension.newBuilder().setName("label").setValue("v1"))
                    .addSupportedDimension(
                        DeviceDimension.newBuilder().setName("label").setValue(""))
                    .addRequiredDimension(
                        DeviceDimension.newBuilder().setName("pool").setValue("shared")))
            .build();

    assertThat(
            BatchDeviceConfigUtil.getDimensionValues(
                basicConfig, "   ", DimensionList.DIMENSION_LIST_SUPPORTED))
        .isEmpty();
    assertThat(
            BatchDeviceConfigUtil.getDimensionValues(
                basicConfig, "dim::label", DimensionList.DIMENSION_LIST_SUPPORTED))
        .containsExactly("v1");
    assertThat(
            BatchDeviceConfigUtil.getDimensionValues(
                basicConfig, "pool", DimensionList.DIMENSION_LIST_REQUIRED))
        .containsExactly("shared");
  }

  @Test
  public void replaceDimensionValues_updatesOnlyTargetListAndKey() {
    DeviceCompositeDimension original =
        DeviceCompositeDimension.newBuilder()
            .addSupportedDimension(DeviceDimension.newBuilder().setName("label").setValue("old1"))
            .addSupportedDimension(DeviceDimension.newBuilder().setName("cluster").setValue("c1"))
            .addRequiredDimension(DeviceDimension.newBuilder().setName("pool").setValue("shared"))
            .build();

    DeviceCompositeDimension updatedSupported =
        BatchDeviceConfigUtil.replaceDimensionValues(
            original,
            "label",
            DimensionList.DIMENSION_LIST_SUPPORTED,
            ImmutableList.of("new1", "", "new2"));

    assertThat(updatedSupported.getSupportedDimensionList())
        .containsExactly(
            DeviceDimension.newBuilder().setName("cluster").setValue("c1").build(),
            DeviceDimension.newBuilder().setName("label").setValue("new1").build(),
            DeviceDimension.newBuilder().setName("label").setValue("new2").build())
        .inOrder();
    assertThat(updatedSupported.getRequiredDimensionList())
        .containsExactly(DeviceDimension.newBuilder().setName("pool").setValue("shared").build());

    DeviceCompositeDimension updatedRequired =
        BatchDeviceConfigUtil.replaceDimensionValues(
            original, "pool", DimensionList.DIMENSION_LIST_REQUIRED, ImmutableList.of("dedicated"));
    assertThat(updatedRequired.getRequiredDimensionList())
        .containsExactly(
            DeviceDimension.newBuilder().setName("pool").setValue("dedicated").build());
  }

  @Test
  public void extractFleetDeviceIds_handlesMissingLabViewAndExtractsNonEmptyIds() {
    assertThat(BatchDeviceConfigUtil.extractFleetDeviceIds(GetLabInfoResponse.getDefaultInstance()))
        .isEmpty();

    GetLabInfoResponse response =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setLabView(
                        LabView.newBuilder()
                            .addLabData(
                                LabData.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder()
                                            .addDeviceInfo(DeviceInfo.getDefaultInstance())
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder().setId("")))
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder()
                                                            .setId("dev-1")))))))
            .build();
    assertThat(BatchDeviceConfigUtil.extractFleetDeviceIds(response)).containsExactly("dev-1");
  }
}
