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

package com.google.devtools.mobileharness.fe.v6.service.search.query;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedFilterKey;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedGroupByKey;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedKeysRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedKeysResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.inject.Guice;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetPromotedKeysProvider}. */
@RunWith(JUnit4.class)
public final class FleetPromotedKeysProviderTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  // Synthetic fleet built through the real index builder, across two hosts:
  //   device-0: IDLE, android, owner alice+bob, model pixel,  pool shared,    lab_location mtv.
  //   device-1: IDLE, android, owner alice,     model pixel,  pool shared,    lab_location mtv.
  //   device-2: BUSY, ios,     owner carol,     model iphone, pool dedicated, lab_location nyc.
  //   device-3: IDLE, android, owner alice,     model nexus,  pool shared,    (no lab_location).
  // device-3 lacks lab_location so grouping by it produces a "(no value)" bucket.
  // FleetIndexBuilder and FleetFilterEngine have package-private @Inject constructors, so obtain
  // them through Guice rather than constructing directly. The provider takes a per-fleet
  // Map<Fleet, ScenarioCuration>; inject a fake curation under FLEET_SELF that returns the device
  // 1p candidate rows, so these tests exercise the dead-end, applied, and limit trimming over a
  // known candidate order without depending on a real deployment curation.
  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final LazyPostings postings = new LazyPostings(snapshot.devices());
  private final FleetPromotedKeysProvider provider =
      new FleetPromotedKeysProvider(
          Guice.createInjector().getInstance(FleetFilterEngine.class),
          ImmutableMap.of(Fleet.FLEET_SELF, new FakeCuration()));

  @Test
  public void filterKeys_noFilters_followsCuratedOrder() {
    FleetPromotedKeysResponse response = provider.getPromotedKeys(snapshot, request(), postings);

    // Every curated 1p filter key is present in this fleet, so the whole anchor row shows in order.
    assertThat(filterKeys(response))
        .containsExactly(
            "field::status",
            "dim::model",
            "field::type",
            "field::owner",
            "dim::pool",
            "host::host_name")
        .inOrder();
  }

  @Test
  public void filterKeys_excludeAppliedKeyAndDeadEnds() {
    // Filtering status=IDLE leaves devices 0, 1, 3. Within that set:
    //   status: applied, so excluded from the promoted row.
    //   type:   all android (one value)   -> dead end, dropped.
    //   pool:   all shared (one value)    -> dead end, dropped.
    //   model:  pixel, nexus              -> kept.
    //   owner:  {alice,bob}, {alice}      -> kept.
    //   host_name: lab-a, lab-b           -> kept.
    FleetPromotedKeysResponse response =
        provider.getPromotedKeys(snapshot, request(simple("field::status", "IDLE")), postings);

    assertThat(filterKeys(response))
        .containsExactly("dim::model", "field::owner", "host::host_name")
        .inOrder();
  }

  @Test
  public void filterKeys_metadataFields() {
    FleetPromotedKeysResponse response = provider.getPromotedKeys(snapshot, request(), postings);

    // Owner is multi-valued: verb agreement is plural. Its display name is the built-in "Owners".
    FleetPromotedFilterKey owner = filterKey(response, "field::owner");
    assertThat(owner.getMetadata().getIsPlural()).isTrue();
    assertThat(owner.getMetadata().getCanUseAdvanced()).isTrue();
    assertThat(owner.getMetadata().getKeyDisplayName()).isEqualTo("Owners");

    // Model is single-valued: verb agreement is singular, and advanced mode is available.
    FleetPromotedFilterKey model = filterKey(response, "dim::model");
    assertThat(model.getMetadata().getIsPlural()).isFalse();
    assertThat(model.getMetadata().getCanUseAdvanced()).isTrue();
    assertThat(model.getMetadata().getKeyDisplayName()).isEqualTo("Model");
  }

  @Test
  public void groupByKeys_curatedOrderCountsAndDisplayNames() {
    FleetPromotedKeysResponse response = provider.getPromotedKeys(snapshot, request(), postings);

    // host::lab_type is absent from this fleet and is skipped; the rest keep the curated order.
    // An identifier key such as field::uuid is never in the group-by row.
    assertThat(groupByKeys(response))
        .containsExactly("dim::lab_location", "field::type", "field::status", "host::host_name")
        .inOrder();
    assertThat(groupByKeys(response)).doesNotContain("field::uuid");

    // lab_location has two values (mtv, nyc) plus the "(no value)" bucket for device-3: 3 groups.
    FleetPromotedGroupByKey labLocation = groupByKey(response, "dim::lab_location");
    assertThat(labLocation.getGroupCount()).isEqualTo(3);
    assertThat(labLocation.getDisplayName()).isEqualTo("Dimension lab_location");

    // The other keys have two values each and no missing devices.
    assertThat(groupByKey(response, "field::type").getGroupCount()).isEqualTo(2);
    assertThat(groupByKey(response, "field::type").getDisplayName()).isEqualTo("Type");
    assertThat(groupByKey(response, "field::status").getGroupCount()).isEqualTo(2);
    assertThat(groupByKey(response, "host::host_name").getGroupCount()).isEqualTo(2);
    assertThat(groupByKey(response, "host::host_name").getDisplayName()).isEqualTo("Host Name");
  }

  @Test
  public void groupByKeys_excludeAppliedGroupBy() {
    FleetPromotedKeysResponse response =
        provider.getPromotedKeys(
            snapshot,
            FleetPromotedKeysRequest.newBuilder()
                .setFleet(Fleet.FLEET_SELF)
                .addGroupBy("field::status")
                .build(),
            postings);

    assertThat(groupByKeys(response))
        .containsExactly("dim::lab_location", "field::type", "host::host_name")
        .inOrder();
  }

  @Test
  public void groupByKeys_hiddenWhenThreeApplied() {
    FleetPromotedKeysResponse response =
        provider.getPromotedKeys(
            snapshot,
            FleetPromotedKeysRequest.newBuilder()
                .setFleet(Fleet.FLEET_SELF)
                .addGroupBy("field::type")
                .addGroupBy("field::status")
                .addGroupBy("host::host_name")
                .build(),
            postings);

    assertThat(response.getGroupByKeysList()).isEmpty();
  }

  @Test
  public void groupByKeys_countsOverFilteredSetAndDropsSingleBucketKeys() {
    // Filtering owner=alice leaves devices 0, 1, 3. Within that set:
    //   type:   all android           -> one bucket, dropped.
    //   status: all IDLE              -> one bucket, dropped.
    //   lab_location: mtv plus device-3's "(no value)" bucket -> two buckets, kept.
    //   host_name: lab-a, lab-b       -> two buckets, kept.
    FleetPromotedKeysResponse response =
        provider.getPromotedKeys(snapshot, request(simple("field::owner", "alice")), postings);

    assertThat(groupByKeys(response))
        .containsExactly("dim::lab_location", "host::host_name")
        .inOrder();
    assertThat(groupByKey(response, "dim::lab_location").getGroupCount()).isEqualTo(2);
    assertThat(groupByKey(response, "host::host_name").getGroupCount()).isEqualTo(2);
  }

  // --- Helpers ---

  private static ImmutableList<String> filterKeys(FleetPromotedKeysResponse response) {
    ImmutableList.Builder<String> keys = ImmutableList.builder();
    for (FleetPromotedFilterKey key : response.getFilterKeysList()) {
      keys.add(key.getKey());
    }
    return keys.build();
  }

  private static ImmutableList<String> groupByKeys(FleetPromotedKeysResponse response) {
    ImmutableList.Builder<String> keys = ImmutableList.builder();
    for (FleetPromotedGroupByKey key : response.getGroupByKeysList()) {
      keys.add(key.getKey());
    }
    return keys.build();
  }

  private static FleetPromotedFilterKey filterKey(FleetPromotedKeysResponse response, String key) {
    for (FleetPromotedFilterKey entry : response.getFilterKeysList()) {
      if (entry.getKey().equals(key)) {
        return entry;
      }
    }
    throw new AssertionError("no promoted filter key " + key);
  }

  private static FleetPromotedGroupByKey groupByKey(
      FleetPromotedKeysResponse response, String key) {
    for (FleetPromotedGroupByKey entry : response.getGroupByKeysList()) {
      if (entry.getKey().equals(key)) {
        return entry;
      }
    }
    throw new AssertionError("no promoted group-by key " + key);
  }

  private static FleetPromotedKeysRequest request(Filter... filters) {
    FleetPromotedKeysRequest.Builder builder =
        FleetPromotedKeysRequest.newBuilder().setFleet(Fleet.FLEET_SELF);
    for (Filter filter : filters) {
      builder.addFilters(filter);
    }
    return builder.build();
  }

  /**
   * A fake curation returning the device 1p candidate rows. The filter row is the former {@code
   * FILTER_BY_ROW['1p']} and the group-by row the former {@code GROUP_BY_ROW['1p']}, so the
   * trimming assertions below hold over the synthetic fleet regardless of the real deployment
   * curations.
   */
  private static final class FakeCuration implements ScenarioCuration {
    @Override
    public ImmutableList<String> filterByRow() {
      return ImmutableList.of(
          "field::status",
          "dim::model",
          "field::type",
          "field::owner",
          "dim::pool",
          "host::host_name");
    }

    @Override
    public ImmutableList<String> groupByRow() {
      return ImmutableList.of(
          "host::lab_type", "dim::lab_location", "field::type", "field::status", "host::host_name");
    }

    @Override
    public ImmutableList<String> defaultColumns() {
      return ImmutableList.of();
    }

    @Override
    public ImmutableList<String> recommendedColumns() {
      return ImmutableList.of();
    }

    @Override
    public int keyPriority(String keyId) {
      return 0;
    }

    @Override
    public boolean landingEnabled() {
      return true;
    }
  }

  private static Filter simple(String key, String value) {
    return Filter.newBuilder()
        .setKey(key)
        .setSimple(SimpleMatch.newBuilder().addValues(FilterValue.newBuilder().setValue(value)))
        .build();
  }

  // --- Synthetic fleet ---

  private static LabQueryResult fleet() {
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(2)
                .addLabData(labData("lab-a", "1.1.1.1", device0(), device1()))
                .addLabData(labData("lab-b", "2.2.2.2", device2(), device3())))
        .build();
  }

  private static LabData labData(String hostName, String ip, DeviceInfo... devices) {
    DeviceList.Builder deviceList = DeviceList.newBuilder().setDeviceTotalCount(devices.length);
    for (DeviceInfo device : devices) {
      deviceList.addDeviceInfo(device);
    }
    return LabData.newBuilder()
        .setLabInfo(
            LabInfo.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName(hostName).setIp(ip))
                .setLabStatus(LabStatus.LAB_RUNNING))
        .setDeviceList(deviceList)
        .build();
  }

  private static DeviceInfo device0() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("android_real_device")
                .addOwner("alice")
                .addOwner("bob")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("model", "pixel"))
                        .addSupportedDimension(dimension("pool", "shared"))
                        .addSupportedDimension(dimension("lab_location", "mtv"))))
        .build();
  }

  private static DeviceInfo device1() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-1"))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("android_real_device")
                .addOwner("alice")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("model", "pixel"))
                        .addSupportedDimension(dimension("pool", "shared"))
                        .addSupportedDimension(dimension("lab_location", "mtv"))))
        .build();
  }

  private static DeviceInfo device2() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-2"))
        .setDeviceStatus(DeviceStatus.BUSY)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("ios_real_device")
                .addOwner("carol")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("model", "iphone"))
                        .addSupportedDimension(dimension("pool", "dedicated"))
                        .addSupportedDimension(dimension("lab_location", "nyc"))))
        .build();
  }

  private static DeviceInfo device3() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-3"))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("android_real_device")
                .addOwner("alice")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("model", "nexus"))
                        .addSupportedDimension(dimension("pool", "shared"))))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }
}
