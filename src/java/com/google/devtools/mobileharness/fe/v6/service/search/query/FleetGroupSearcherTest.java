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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnSort;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFlatResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroup;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupSort;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupSortField;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupedResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetItemCountSort;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPageRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetUtilization;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Row;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.inject.Guice;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetGroupSearcher}. */
@RunWith(JUnit4.class)
public final class FleetGroupSearcherTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  private static final ImmutableList<String> EXPAND_COLUMNS =
      ImmutableList.of("field::uuid", "field::status");

  // Synthetic fleet built through the real index builder:
  //   device-0: IDLE, AndroidRealDevice, owner alice,        os=android.
  //   device-1: BUSY, AndroidRealDevice, owner bob,          os=android.
  //   device-2: IDLE, IosRealDevice,     owners alice+carol, os=ios.
  //   device-3: MISSING, no type, no owner, no os dimension  -> "(no value)" bucket.
  //   device-4: IDLE, FailedDevice,      owner alice,        os=android -> "other" utilization.
  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final FleetGroupSearcher searcher =
      Guice.createInjector().getInstance(FleetGroupSearcher.class);

  @Test
  public void groupByOs_partitionsIntoCountsWithNoValueBucket() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            snapshot,
            ImmutableList.of(),
            ImmutableList.of("dim::os"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    // Default sort is item count descending, so android (3) leads. The two size-1 groups tie on
    // count and break by name descending, putting "ios" before "(no value)".
    assertThat(groupValues(results)).containsExactly("android", "ios", "(no value)").inOrder();
    assertThat(itemCounts(results)).containsExactly(3, 1, 1).inOrder();
    assertThat(results.getTotalGroups()).isEqualTo(3);
    assertThat(results.getTotalItems()).isEqualTo(5);
    assertThat(results.getGroupByKeysCount()).isEqualTo(1);
    assertThat(results.getGroupByKeys(0).getKey()).isEqualTo("dim::os");
  }

  @Test
  public void groupByOs_utilizationBreakdown() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            snapshot,
            ImmutableList.of(),
            ImmutableList.of("dim::os"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    // android holds device-0 (idle), device-1 (busy), device-4 (idle but abnormal type -> other).
    FleetUtilization android = groupByFirstValue(results, "android").getUtilization();
    assertThat(android.getIdle()).isEqualTo(1);
    assertThat(android.getBusy()).isEqualTo(1);
    assertThat(android.getOther()).isEqualTo(1);
    assertThat(android.getTotal()).isEqualTo(3);

    // The "(no value)" bucket holds only the MISSING device, which is never serving.
    FleetUtilization noValue = groupByFirstValue(results, "(no value)").getUtilization();
    assertThat(noValue.getIdle()).isEqualTo(0);
    assertThat(noValue.getBusy()).isEqualTo(0);
    assertThat(noValue.getOther()).isEqualTo(1);
    assertThat(noValue.getTotal()).isEqualTo(1);
  }

  @Test
  public void groupId_roundTripsAndExpandsToItsDevices() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            snapshot,
            ImmutableList.of(),
            ImmutableList.of("dim::os"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
    String androidGroupId = groupByFirstValue(results, "android").getGroupId();

    // The opaque id decodes back to its single group-by entry.
    ImmutableList<FleetGroupSearcher.GroupEntry> entries =
        FleetGroupSearcher.decodeGroupId(androidGroupId);
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).key()).isEqualTo("dim::os");
    assertThat(entries.get(0).noValue()).isFalse();
    assertThat(entries.get(0).values()).containsExactly("android");

    // Expanding it returns exactly the android devices, sorted by uuid.
    FleetFlatResults expanded =
        searcher.expandGroup(
            snapshot,
            ImmutableList.of(),
            androidGroupId,
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            "");
    assertThat(rowIds(expanded)).containsExactly("device-0", "device-1", "device-4").inOrder();
    assertThat(expanded.getTotal()).isEqualTo(3);
  }

  @Test
  public void noValueGroup_expandsToDevicesLackingTheKey() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            snapshot,
            ImmutableList.of(),
            ImmutableList.of("dim::os"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
    String noValueId = groupByFirstValue(results, "(no value)").getGroupId();

    ImmutableList<FleetGroupSearcher.GroupEntry> entries =
        FleetGroupSearcher.decodeGroupId(noValueId);
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).noValue()).isTrue();

    FleetFlatResults expanded =
        searcher.expandGroup(
            snapshot,
            ImmutableList.of(),
            noValueId,
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            "");
    assertThat(rowIds(expanded)).containsExactly("device-3");
  }

  @Test
  public void multiValuedKey_wholeValueSetIsOneGroup() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            snapshot,
            ImmutableList.of(),
            ImmutableList.of("field::owner"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    // alice+carol is a single group, distinct from the alice-only group. alice holds device-0 and
    // device-4; bob holds device-1; alice+carol holds device-2; device-3 has no owner.
    assertThat(groupValues(results)).containsExactly("alice", "bob", "alice, carol", "(no value)");
    FleetGroup aliceCarol = groupByFirstValue(results, "alice, carol");
    assertThat(aliceCarol.getItemCount()).isEqualTo(1);

    FleetFlatResults expanded =
        searcher.expandGroup(
            snapshot,
            ImmutableList.of(),
            aliceCarol.getGroupId(),
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            "");
    // Exact-set membership: device-2 (alice+carol) only, not the alice-only devices.
    assertThat(rowIds(expanded)).containsExactly("device-2");
  }

  @Test
  public void sortByItemCountAscending_reordersGroups() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            snapshot,
            ImmutableList.of(),
            ImmutableList.of("dim::os"),
            FleetGroupSort.newBuilder()
                .setField(
                    FleetGroupSortField.newBuilder()
                        .setItemCount(FleetItemCountSort.getDefaultInstance()))
                .setAscending(true)
                .build(),
            FleetPageRequest.getDefaultInstance());

    // Ascending count: the two size-1 groups first, tie-broken by name ascending, then android (3).
    assertThat(groupValues(results)).containsExactly("(no value)", "ios", "android").inOrder();
  }

  @Test
  public void threeKeyGuard_capsToFirstThreeKnownKeys() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            snapshot,
            ImmutableList.of(),
            ImmutableList.of("dim::os", "field::status", "field::type", "field::owner"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    assertThat(results.getGroupByKeysCount()).isEqualTo(3);
    assertThat(results.getGroupByKeys(0).getKey()).isEqualTo("dim::os");
    assertThat(results.getGroupByKeys(1).getKey()).isEqualTo("field::status");
    assertThat(results.getGroupByKeys(2).getKey()).isEqualTo("field::type");
  }

  @Test
  public void groupHeaderPagination_walksPagesWithTokens() {
    FleetPageRequest firstPage = FleetPageRequest.newBuilder().setPageSize(2).build();
    FleetGroupedResults page1 =
        searcher.searchGrouped(
            snapshot,
            ImmutableList.of(),
            ImmutableList.of("field::owner"),
            FleetGroupSort.getDefaultInstance(),
            firstPage);

    assertThat(page1.getGroupsCount()).isEqualTo(2);
    assertThat(page1.getTotalGroups()).isEqualTo(4);
    assertThat(page1.getRangeStart()).isEqualTo(1);
    assertThat(page1.getRangeEnd()).isEqualTo(2);
    assertThat(page1.getNextPageToken()).isNotEmpty();
    assertThat(page1.getPrevPageToken()).isEmpty();

    FleetPageRequest secondPage =
        FleetPageRequest.newBuilder().setPageSize(2).setPageToken(page1.getNextPageToken()).build();
    FleetGroupedResults page2 =
        searcher.searchGrouped(
            snapshot,
            ImmutableList.of(),
            ImmutableList.of("field::owner"),
            FleetGroupSort.getDefaultInstance(),
            secondPage);

    assertThat(page2.getGroupsCount()).isEqualTo(2);
    assertThat(page2.getRangeStart()).isEqualTo(3);
    assertThat(page2.getRangeEnd()).isEqualTo(4);
    assertThat(page2.getNextPageToken()).isEmpty();
    assertThat(page2.getPrevPageToken()).isNotEmpty();
  }

  @Test
  public void expand_fixedPageSizeOfOneHundred() {
    // A single android group of 150 devices exercises the fixed 100-row expand page.
    FleetSnapshot large =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(uniformAndroidFleet(150), BUILD_TIME);
    FleetGroupedResults headers =
        searcher.searchGrouped(
            large,
            ImmutableList.of(),
            ImmutableList.of("dim::os"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
    String groupId = headers.getGroups(0).getGroupId();

    FleetFlatResults page1 =
        searcher.expandGroup(
            large,
            ImmutableList.of(),
            groupId,
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            "");
    assertThat(page1.getTotal()).isEqualTo(150);
    assertThat(page1.getRowsCount()).isEqualTo(100);
    assertThat(page1.getNextPageToken()).isNotEmpty();

    FleetFlatResults page2 =
        searcher.expandGroup(
            large,
            ImmutableList.of(),
            groupId,
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            page1.getNextPageToken());
    assertThat(page2.getRowsCount()).isEqualTo(50);
    assertThat(page2.getNextPageToken()).isEmpty();
  }

  @Test
  public void expand_unknownGroupIdReturnsNoRows() {
    FleetFlatResults expanded =
        searcher.expandGroup(
            snapshot,
            ImmutableList.of(),
            "not-a-real-group-id",
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            "");
    assertThat(expanded.getRowsCount()).isEqualTo(0);
    assertThat(expanded.getColumnsCount()).isEqualTo(EXPAND_COLUMNS.size());
  }

  // --- Helpers ---

  private static ImmutableList<String> groupValues(FleetGroupedResults results) {
    ImmutableList.Builder<String> values = ImmutableList.builder();
    for (FleetGroup group : results.getGroupsList()) {
      values.add(group.getValues(0));
    }
    return values.build();
  }

  private static ImmutableList<Integer> itemCounts(FleetGroupedResults results) {
    ImmutableList.Builder<Integer> counts = ImmutableList.builder();
    for (FleetGroup group : results.getGroupsList()) {
      counts.add(group.getItemCount());
    }
    return counts.build();
  }

  private static FleetGroup groupByFirstValue(FleetGroupedResults results, String firstValue) {
    for (FleetGroup group : results.getGroupsList()) {
      if (group.getValues(0).equals(firstValue)) {
        return group;
      }
    }
    throw new AssertionError("No group whose first value is " + firstValue);
  }

  private static ImmutableList<String> rowIds(FleetFlatResults results) {
    ImmutableList.Builder<String> ids = ImmutableList.builder();
    for (Row row : results.getRowsList()) {
      ids.add(row.getId());
    }
    return ids.build();
  }

  // --- Synthetic fleets ---

  private static LabQueryResult fleet() {
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(2)
                .addLabData(labData("lab1", "1.1.1.1", device0(), device1(), device4()))
                .addLabData(labData("lab2", "2.2.2.2", device2(), device3())))
        .build();
  }

  private static LabQueryResult uniformAndroidFleet(int count) {
    DeviceList.Builder deviceList = DeviceList.newBuilder().setDeviceTotalCount(count);
    for (int i = 0; i < count; i++) {
      deviceList.addDeviceInfo(
          DeviceInfo.newBuilder()
              .setDeviceLocator(DeviceLocator.newBuilder().setId(String.format("device-%03d", i)))
              .setDeviceStatus(DeviceStatus.IDLE)
              .setDeviceFeature(
                  DeviceFeature.newBuilder()
                      .addType("AndroidRealDevice")
                      .setCompositeDimension(
                          DeviceCompositeDimension.newBuilder()
                              .addSupportedDimension(dimension("os", "android"))))
              .build());
    }
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(1)
                .addLabData(
                    LabData.newBuilder()
                        .setLabInfo(
                            LabInfo.newBuilder()
                                .setLabLocator(
                                    LabLocator.newBuilder().setHostName("lab1").setIp("1.1.1.1"))
                                .setLabStatus(LabStatus.LAB_RUNNING))
                        .setDeviceList(deviceList)))
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
                .addType("AndroidRealDevice")
                .addOwner("alice")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("os", "android"))))
        .build();
  }

  private static DeviceInfo device1() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-1"))
        .setDeviceStatus(DeviceStatus.BUSY)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("AndroidRealDevice")
                .addOwner("bob")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("os", "android"))))
        .build();
  }

  private static DeviceInfo device2() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-2"))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("IosRealDevice")
                .addOwner("alice")
                .addOwner("carol")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("os", "ios"))))
        .build();
  }

  private static DeviceInfo device3() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-3"))
        .setDeviceStatus(DeviceStatus.MISSING)
        .setDeviceFeature(DeviceFeature.getDefaultInstance())
        .build();
  }

  private static DeviceInfo device4() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-4"))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("FailedDevice")
                .addOwner("alice")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("os", "android"))))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }
}
