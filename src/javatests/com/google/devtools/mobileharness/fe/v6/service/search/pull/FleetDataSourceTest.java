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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDisplay;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for the default methods of {@link FleetDataSource}. */
@RunWith(JUnit4.class)
public final class FleetDataSourceTest {

  private static final DeviceKeyDescriptor CARRIER_KEY =
      new AtsDeviceKeyRegistry().createLongTailDimensionKey("carrier").get();
  private static final DeviceKeyDescriptor OS_VERSION_KEY =
      new AtsDeviceKeyRegistry().createLongTailDimensionKey("os_version").get();

  private final SingleDimensionSource source = new SingleDimensionSource();

  @Test
  public void pullDimensions_emptySet_returnsEmptyMapWithoutPulling() throws Exception {
    assertThat(source.pullDimensions(ImmutableSet.of()).get()).isEmpty();
    assertThat(source.pulledKeyIds).isEmpty();
  }

  @Test
  public void pullDimensions_keysEachResultByRequestedKeyId() throws Exception {
    ImmutableMap<String, DimensionOverlayRaw> result =
        source.pullDimensions(ImmutableSet.of(CARRIER_KEY, OS_VERSION_KEY)).get();

    // The fake reports a different keyId on each raw overlay, so the map must use the request.
    assertThat(result.keySet()).containsExactly("dimension::carrier", "dimension::os_version");
    assertThat(result.get("dimension::carrier").uuidToValues())
        .containsExactly("dev-1", ImmutableList.of("carrier"));
    assertThat(result.get("dimension::os_version").uuidToValues())
        .containsExactly("dev-1", ImmutableList.of("os_version"));
    assertThat(source.pulledKeyIds).containsExactly("dimension::carrier", "dimension::os_version");
  }

  @Test
  public void pullDimensions_descriptorsSharingAnId_yieldOneEntry() throws Exception {
    DeviceKeyDescriptor duplicateCarrierKey =
        DeviceKeyDescriptor.builder()
            .setId("dimension::carrier")
            .setDisplay(KeyDisplay.of("Carrier"))
            .setIsDimension(true)
            .setIsLongTail(true)
            .build();

    ImmutableMap<String, DimensionOverlayRaw> result =
        source.pullDimensions(ImmutableSet.of(CARRIER_KEY, duplicateCarrierKey)).get();

    assertThat(result.keySet()).containsExactly("dimension::carrier");
  }

  /** Implements only {@link FleetDataSource#pullDimension}, so batching uses the default method. */
  private static final class SingleDimensionSource implements FleetDataSource {
    final List<String> pulledKeyIds = new ArrayList<>();

    @Override
    public Fleet fleet() {
      return Fleet.FLEET_SELF;
    }

    @Override
    public ListenableFuture<CoreFleetRawData> pull() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<DimensionOverlayRaw> pullDimension(DeviceKeyDescriptor dimensionKey) {
      pulledKeyIds.add(dimensionKey.id());
      return immediateFuture(
          DimensionOverlayRaw.create(
              "reported::" + dimensionKey.bareName(),
              ImmutableMap.of("dev-1", ImmutableList.of(dimensionKey.bareName()))));
    }
  }
}
