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
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ConfigurableDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BatchDeviceConfigUtilTest {

  private static final OssBatchDeviceConfigPolicy OSS_POLICY = new OssBatchDeviceConfigPolicy();

  private static final BatchDeviceConfigPolicy CUSTOM_ALLOWING_POLICY =
      new FakeBatchDeviceConfigPolicy();

  @Test
  public void cleanDimensionName_stripsDimPrefixAndTrims() {
    assertThat(BatchDeviceConfigUtil.cleanDimensionName("  dim::pool  ")).isEqualTo("pool");
    assertThat(BatchDeviceConfigUtil.cleanDimensionName("DIM::custom_key")).isEqualTo("custom_key");
    assertThat(BatchDeviceConfigUtil.cleanDimensionName("label")).isEqualTo("label");
    assertThat(BatchDeviceConfigUtil.cleanDimensionName(null)).isEmpty();
  }

  @Test
  public void ossBatchDeviceConfigPolicy_exposesAtsCurationAndSystemDimensions() {
    assertThat(OSS_POLICY.allowCustomDimensions()).isFalse();
    assertThat(OSS_POLICY.discoverFleetDimensionsFromDeviceConfigs()).isTrue();
    assertThat(OSS_POLICY.getCuratedDimensions())
        .containsExactly(
            ConfigurableDimension.newBuilder()
                .setDimensionName("recovery")
                .setDisplayName("recovery")
                .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
                .build(),
            ConfigurableDimension.newBuilder()
                .setDimensionName("skip_suw_app")
                .setDisplayName("skip_suw_app")
                .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
                .build(),
            ConfigurableDimension.newBuilder()
                .setDimensionName("pool")
                .setDisplayName("Required Dimension: pool")
                .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                .build(),
            ConfigurableDimension.newBuilder()
                .setDimensionName("persist_test_harness")
                .setDisplayName("Required Dimension: persist_test_harness")
                .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
                .build())
        .inOrder();
    assertThat(OSS_POLICY.getDefaultConfigurableKeys(DimensionList.DIMENSION_LIST_SUPPORTED))
        .containsExactly("recovery", "skip_suw_app");
    assertThat(OSS_POLICY.getDefaultConfigurableKeys(DimensionList.DIMENSION_LIST_REQUIRED))
        .containsExactly("pool", "persist_test_harness");
    assertThat(OSS_POLICY.getDefaultConfigurableKeys(DimensionList.DIMENSION_LIST_UNSPECIFIED))
        .isEmpty();
    for (String name : OSS_POLICY.getSystemDetectedDimensionNames()) {
      assertThat(name).isEqualTo(name.toLowerCase(Locale.ROOT));
    }
    assertThat(OSS_POLICY.getSortedSystemDetectedDimensionNames()).isInOrder();
    assertThat(OSS_POLICY.getSortedSystemDetectedDimensionNames())
        .hasSize(OSS_POLICY.getSystemDetectedDimensionNames().size());
  }

  @Test
  public void isValidConfigurableDimensionKey_checksCharactersAndSystemBlocklist() {
    assertThat(
            BatchDeviceConfigUtil.isValidConfigurableDimensionKey("pool", CUSTOM_ALLOWING_POLICY))
        .isTrue();
    assertThat(
            BatchDeviceConfigUtil.isValidConfigurableDimensionKey(
                "custom_key-1.0", CUSTOM_ALLOWING_POLICY))
        .isTrue();
    assertThat(BatchDeviceConfigUtil.isValidConfigurableDimensionKey("", CUSTOM_ALLOWING_POLICY))
        .isFalse();
    assertThat(
            BatchDeviceConfigUtil.isValidConfigurableDimensionKey("model", CUSTOM_ALLOWING_POLICY))
        .isFalse();
    assertThat(
            BatchDeviceConfigUtil.isValidConfigurableDimensionKey(
                "sdk_version", CUSTOM_ALLOWING_POLICY))
        .isFalse();
    assertThat(
            BatchDeviceConfigUtil.isValidConfigurableDimensionKey(
                "subdevice_dimensions", CUSTOM_ALLOWING_POLICY))
        .isFalse();
    assertThat(
            BatchDeviceConfigUtil.isValidConfigurableDimensionKey(
                "UpperCase", CUSTOM_ALLOWING_POLICY))
        .isFalse();
    assertThat(
            BatchDeviceConfigUtil.isValidConfigurableDimensionKey(
                "invalid space", CUSTOM_ALLOWING_POLICY))
        .isFalse();
  }

  @Test
  public void checkDimensionTarget_validatesNameAndDimensionList() {
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "pool", DimensionList.DIMENSION_LIST_REQUIRED, CUSTOM_ALLOWING_POLICY))
        .isEmpty();
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "label", DimensionList.DIMENSION_LIST_SUPPORTED, CUSTOM_ALLOWING_POLICY))
        .isEmpty();
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "", DimensionList.DIMENSION_LIST_REQUIRED, CUSTOM_ALLOWING_POLICY))
        .hasValue("Dimension name must not be empty.");
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "model", DimensionList.DIMENSION_LIST_SUPPORTED, CUSTOM_ALLOWING_POLICY))
        .hasValue(
            "\"model\" is automatically detected by OmniLab and cannot be configured manually.");
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "bad key", DimensionList.DIMENSION_LIST_SUPPORTED, CUSTOM_ALLOWING_POLICY))
        .hasValue("Invalid dimension name: bad key");
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "pool", DimensionList.DIMENSION_LIST_UNSPECIFIED, CUSTOM_ALLOWING_POLICY))
        .hasValue("Dimension list must be DIMENSION_LIST_SUPPORTED or DIMENSION_LIST_REQUIRED.");
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "recovery", DimensionList.DIMENSION_LIST_SUPPORTED, OSS_POLICY))
        .isEmpty();
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "pool", DimensionList.DIMENSION_LIST_REQUIRED, OSS_POLICY))
        .isEmpty();
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "label", DimensionList.DIMENSION_LIST_SUPPORTED, OSS_POLICY))
        .hasValue("Dimension \"label\" in DIMENSION_LIST_SUPPORTED is not configurable in ATS.");
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "pool", DimensionList.DIMENSION_LIST_SUPPORTED, OSS_POLICY))
        .hasValue("Dimension \"pool\" in DIMENSION_LIST_SUPPORTED is not configurable in ATS.");

    BatchDeviceConfigPolicy defaultRestrictedPolicy =
        new BatchDeviceConfigPolicy() {
          @Override
          public ImmutableList<ConfigurableDimension> getCuratedDimensions() {
            return OSS_POLICY.getCuratedDimensions();
          }

          @Override
          public boolean allowCustomDimensions() {
            return false;
          }

          @Override
          public boolean discoverFleetDimensionsFromDeviceConfigs() {
            return true;
          }

          @Override
          public ImmutableSet<String> getSystemDetectedDimensionNames() {
            return BatchDeviceConfigUtil.COMMON_SYSTEM_DETECTED_DIMENSION_NAMES;
          }
        };
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "recovery", DimensionList.DIMENSION_LIST_SUPPORTED, defaultRestrictedPolicy))
        .isEmpty();
    assertThat(
            BatchDeviceConfigUtil.checkDimensionTarget(
                "label", DimensionList.DIMENSION_LIST_SUPPORTED, defaultRestrictedPolicy))
        .hasValue("Dimension \"label\" in DIMENSION_LIST_SUPPORTED is not configurable.");
  }

  @Test
  public void isValidDeviceUuid_andCheckDimensionValues() {
    assertThat(BatchDeviceConfigUtil.isValidDeviceUuid("127.0.0.1:5555")).isTrue();
    assertThat(BatchDeviceConfigUtil.isValidDeviceUuid("Instrument_CL200A(local)")).isTrue();
    assertThat(BatchDeviceConfigUtil.isValidDeviceUuid("")).isFalse();
    assertThat(BatchDeviceConfigUtil.isValidDeviceUuid("   ")).isFalse();
    assertThat(BatchDeviceConfigUtil.isValidDeviceUuid(null)).isFalse();
    assertThat(BatchDeviceConfigUtil.isValidDeviceUuid("unrecognizable device")).isFalse();
    assertThat(BatchDeviceConfigUtil.isValidDeviceUuid("../etc")).isFalse();

    assertThat(BatchDeviceConfigUtil.checkDimensionValues("pool", ImmutableList.of("shared", "  ")))
        .isEmpty();
    assertThat(BatchDeviceConfigUtil.checkDimensionValues("pool", ImmutableList.of("REGEX:foo")))
        .hasValue("The value of dimension \"pool\" must not start with \"regex:\".");
    assertThat(BatchDeviceConfigUtil.checkDimensionValues("pool", ImmutableList.of("Exclude")))
        .hasValue("The value of dimension \"pool\" must not be \"exclude\".");
  }

  @Test
  public void canonicalizeValues_trimsDeduplicatesAndSortsCaseSensitively() {
    assertThat(
            BatchDeviceConfigUtil.canonicalizeValues(
                ImmutableList.of(" Beta ", "", "   ", "alpha", "Beta", "BETA")))
        .containsExactly("BETA", "Beta", "alpha")
        .inOrder();
    assertThat(
            BatchDeviceConfigUtil.normalizeValues(
                ImmutableList.of(" Beta ", "", "   ", "Beta", "alpha")))
        .containsExactly("Beta", "alpha")
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
            ImmutableList.of(" new1 ", "", "   ", "new1", "new2"));

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
  public void extractFleetDeviceIds_filtersInvalidUuidsAndDeduplicates() {
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
                                                            .setId("unrecognizable device")))
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder().setId("dev-1")))
                                            .addDeviceInfo(
                                                DeviceInfo.newBuilder()
                                                    .setDeviceLocator(
                                                        DeviceLocator.newBuilder()
                                                            .setId("dev-1")))))))
            .build();
    assertThat(BatchDeviceConfigUtil.extractFleetDeviceIds(response)).containsExactly("dev-1");
  }
}
