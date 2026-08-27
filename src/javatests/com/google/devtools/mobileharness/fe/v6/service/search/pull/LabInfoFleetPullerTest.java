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

import com.google.common.collect.ImmutableList;
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
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
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

    DimensionOverlayRaw raw = puller.pullDimension("dimension::carrier").get();

    assertThat(raw.keyId()).isEqualTo("dimension::carrier");
    assertThat(raw.uuidToValues())
        .containsExactly("dev-1", ImmutableList.of("Verizon", "T-Mobile"));
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

  private static final class FakeLabInfoProvider implements LabInfoProvider {
    private LabQueryResult result = LabQueryResult.getDefaultInstance();
    private GetLabInfoRequest lastRequest;

    void setResult(LabQueryResult result) {
      this.result = result;
    }

    @Override
    public ListenableFuture<GetLabInfoResponse> getLabInfoAsync(
        GetLabInfoRequest request, UniverseScope universe) {
      this.lastRequest = request;
      return immediateFuture(GetLabInfoResponse.newBuilder().setLabQueryResult(result).build());
    }
  }
}
