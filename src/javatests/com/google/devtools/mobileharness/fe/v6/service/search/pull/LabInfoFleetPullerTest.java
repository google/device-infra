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

package com.google.devtools.mobileharness.fe.v6.service.search.pull;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDisplay;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.DeviceDimensionSummary;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetAllDeviceDimensionsRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetAllDeviceDimensionsResponse;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.protobuf.FieldMask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LabInfoFleetPuller}. */
@RunWith(JUnit4.class)
public final class LabInfoFleetPullerTest {

  private final FakeLabInfoProvider labInfoProvider = new FakeLabInfoProvider();
  private final LabInfoFleetPuller puller = new LabInfoFleetPuller(labInfoProvider);

  @Test
  public void pull_buildsRequestWithPassedMasks() throws Exception {
    DeviceInfoMask deviceMask =
        DeviceInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("device_locator.id"))
            .build();
    LabInfoMask labMask =
        LabInfoMask.newBuilder()
            .setFieldMask(FieldMask.newBuilder().addPaths("lab_locator.host_name"))
            .build();

    LabQueryResult expectedResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .addLabData(
                        LabData.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .addDeviceInfo(
                                        DeviceInfo.newBuilder()
                                            .setDeviceLocator(
                                                DeviceLocator.newBuilder().setId("dev-1"))))))
            .build();
    labInfoProvider.setResult(expectedResult);

    LabQueryResult actualResult = puller.pull(deviceMask, labMask).get();

    assertThat(actualResult).isEqualTo(expectedResult);
    assertThat(labInfoProvider.lastRequest.getLabQuery().getMask().getDeviceInfoMask())
        .isEqualTo(deviceMask);
    assertThat(labInfoProvider.lastRequest.getLabQuery().getMask().getLabInfoMask())
        .isEqualTo(labMask);
    assertThat(labInfoProvider.lastRequest.getPage().getLimit()).isEqualTo(0);
    assertThat(labInfoProvider.lastRequest.getUseRealtimeData()).isFalse();
  }

  @Test
  public void pullDimension_extractsSingleDimensionValues() throws Exception {
    DeviceInfo device =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("dev-1"))
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("carrier").setValue("Verizon"))
                            .addRequiredDimension(
                                DeviceDimension.newBuilder()
                                    .setName("carrier")
                                    .setValue("T-Mobile"))
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("model").setValue("Pixel 8"))))
            .build();
    LabQueryResult labResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .addLabData(
                        LabData.newBuilder()
                            .setDeviceList(DeviceList.newBuilder().addDeviceInfo(device))))
            .build();
    labInfoProvider.setResult(labResult);

    DeviceKeyDescriptor carrierKey =
        new AtsDeviceKeyRegistry().createLongTailDimensionKey("carrier").get();
    DimensionOverlayRaw raw = puller.pullDimension(carrierKey).get();

    assertThat(raw.keyId()).isEqualTo("dimension::carrier");
    assertThat(raw.uuidToValues())
        .containsExactly("dev-1", ImmutableList.of("Verizon", "T-Mobile"));
    assertThat(labInfoProvider.lastRequest.getLabQuery().getMask().getLabInfoMask())
        .isEqualTo(LabInfoMask.newBuilder().setFieldMask(FieldMask.getDefaultInstance()).build());
    assertThat(
            labInfoProvider
                .lastRequest
                .getLabQuery()
                .getMask()
                .getDeviceInfoMask()
                .getSupportedDimensionsMask()
                .getDimensionNamesList())
        .containsExactly("carrier");
  }

  @Test
  public void pullDimensions_batchesMultipleDimensionsInSingleGetLabInfoCall() throws Exception {
    DeviceInfo dev1 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("dev-1"))
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("carrier").setValue("Verizon"))
                            .addRequiredDimension(
                                DeviceDimension.newBuilder().setName("os_version").setValue("16"))))
            .build();
    DeviceInfo dev2 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("dev-2"))
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(
                                DeviceDimension.newBuilder().setName("os_version").setValue("15"))))
            .build();
    DeviceInfo unnamed = DeviceInfo.getDefaultInstance();
    labInfoProvider.setResult(
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .addLabData(
                        LabData.newBuilder()
                            .setDeviceList(
                                DeviceList.newBuilder()
                                    .addDeviceInfo(dev1)
                                    .addDeviceInfo(dev2)
                                    .addDeviceInfo(unnamed))))
            .build());

    AtsDeviceKeyRegistry registry = new AtsDeviceKeyRegistry();
    DeviceKeyDescriptor carrierKey = registry.createLongTailDimensionKey("carrier").get();
    DeviceKeyDescriptor duplicateCarrierKey =
        DeviceKeyDescriptor.builder()
            .setId("dimension::carrier")
            .setDisplay(KeyDisplay.of("Carrier"))
            .setIsDimension(true)
            .setIsLongTail(true)
            .build();
    DeviceKeyDescriptor osVersionKey = registry.createLongTailDimensionKey("os_version").get();
    DeviceKeyDescriptor absentKey = registry.createLongTailDimensionKey("absent_dim").get();

    ImmutableMap<String, DimensionOverlayRaw> overlays =
        puller
            .pullDimensions(
                ImmutableSet.of(carrierKey, duplicateCarrierKey, osVersionKey, absentKey))
            .get();

    assertThat(labInfoProvider.getLabInfoCalls).isEqualTo(1);
    assertThat(
            labInfoProvider
                .lastRequest
                .getLabQuery()
                .getMask()
                .getDeviceInfoMask()
                .getSupportedDimensionsMask()
                .getDimensionNamesList())
        .containsExactly("carrier", "os_version", "absent_dim");
    assertThat(
            labInfoProvider
                .lastRequest
                .getLabQuery()
                .getMask()
                .getDeviceInfoMask()
                .getRequiredDimensionsMask()
                .getDimensionNamesList())
        .containsExactly("carrier", "os_version", "absent_dim");
    assertThat(overlays.get("dimension::carrier").uuidToValues())
        .containsExactly("dev-1", ImmutableList.of("Verizon"));
    assertThat(overlays.get("dimension::os_version").uuidToValues())
        .containsExactly("dev-1", ImmutableList.of("16"), "dev-2", ImmutableList.of("15"));
    assertThat(overlays.get("dimension::absent_dim").uuidToValues()).isEmpty();
  }

  @Test
  public void pullDimensions_emptySet_returnsEmptyWithoutGetLabInfoCall() throws Exception {
    assertThat(puller.pullDimensions(ImmutableSet.of()).get()).isEmpty();
    assertThat(labInfoProvider.getLabInfoCalls).isEqualTo(0);
  }

  @Test
  public void pullDimensions_nonDimensionKey_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> puller.pullDimensions(ImmutableSet.of(DeviceKeys.UUID)));
    assertThrows(IllegalArgumentException.class, () -> puller.pullDimension(DeviceKeys.UUID));
    assertThat(labInfoProvider.getLabInfoCalls).isEqualTo(0);
  }

  @Test
  public void pullDimensionCounts_extractsDimensionCountsAndFiltersInvalidEntries()
      throws Exception {
    labInfoProvider.setDimensionsResponse(
        GetAllDeviceDimensionsResponse.newBuilder()
            .addDimensions(DeviceDimensionSummary.newBuilder().setName("model").setDeviceCount(120))
            .addDimensions(
                DeviceDimensionSummary.newBuilder().setName("carrier").setDeviceCount(15))
            .addDimensions(
                DeviceDimensionSummary.newBuilder()
                    .setName("subdevice_dimensions")
                    .setDeviceCount(90))
            .addDimensions(DeviceDimensionSummary.newBuilder().setName("empty_count"))
            .addDimensions(DeviceDimensionSummary.newBuilder().setName("").setDeviceCount(5))
            .build());

    ImmutableMap<String, Integer> counts = puller.pullDimensionCounts(UniverseScope.SELF).get();

    assertThat(counts).containsExactly("model", 120, "carrier", 15);
  }

  private static final class FakeLabInfoProvider implements LabInfoProvider {
    private LabQueryResult result = LabQueryResult.getDefaultInstance();
    private GetAllDeviceDimensionsResponse dimensionsResponse =
        GetAllDeviceDimensionsResponse.getDefaultInstance();
    private GetLabInfoRequest lastRequest;
    private int getLabInfoCalls;

    void setResult(LabQueryResult result) {
      this.result = result;
    }

    void setDimensionsResponse(GetAllDeviceDimensionsResponse dimensionsResponse) {
      this.dimensionsResponse = dimensionsResponse;
    }

    @Override
    public ListenableFuture<GetLabInfoResponse> getLabInfoAsync(
        GetLabInfoRequest request, UniverseScope universe) {
      this.lastRequest = request;
      this.getLabInfoCalls++;
      return immediateFuture(GetLabInfoResponse.newBuilder().setLabQueryResult(result).build());
    }

    @Override
    public ListenableFuture<GetAllDeviceDimensionsResponse> getAllDeviceDimensionsAsync(
        GetAllDeviceDimensionsRequest request, UniverseScope universe) {
      return immediateFuture(dimensionsResponse);
    }
  }
}
