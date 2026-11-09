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
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
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
      ImmutableList.of("device_field::uuid", "device_field::status");

  // Synthetic fleet built through the real index builder:
  //   device-0: IDLE, AndroidRealDevice, owner alice,        os=android.
  //   device-1: BUSY, AndroidRealDevice, owner bob,          os=android.
  //   device-2: IDLE, IosRealDevice,     owners alice+carol, os=ios.
  //   device-3: MISSING, no type, no owner, no os dimension  -> "(no value)" bucket.
  //   device-4: IDLE, FailedDevice,      owner alice,        os=android -> "other" utilization.
  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final LazyPostings postings = new LazyPostings(snapshot.devices());
  private final DeviceCorpus corpus = new DeviceCorpus(snapshot, postings, null);
  private final FleetGroupSearcher searcher =
      Guice.createInjector().getInstance(FleetGroupSearcher.class);

  @Test
  public void groupByOs_partitionsIntoCountsWithNoValueBucket() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            corpus,
            ImmutableList.of(),
            ImmutableList.of("dimension::os"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    // Default sort is item count descending, so android (3) leads. The two size-1 groups tie on
    // count and break by name descending, putting "ios" before "(no value)".
    assertThat(groupValues(results)).containsExactly("android", "ios", "(no value)").inOrder();
    assertThat(itemCounts(results)).containsExactly(3, 1, 1).inOrder();
    assertThat(results.getTotalGroups()).isEqualTo(3);
    assertThat(results.getTotalItems()).isEqualTo(5);
    assertThat(results.getGroupByKeysCount()).isEqualTo(1);
    assertThat(results.getGroupByKeys(0).getKey()).isEqualTo("dimension::os");
  }

  @Test
  public void groupByOs_utilizationBreakdown() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            corpus,
            ImmutableList.of(),
            ImmutableList.of("dimension::os"),
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
            corpus,
            ImmutableList.of(),
            ImmutableList.of("dimension::os"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
    String androidGroupId = groupByFirstValue(results, "android").getGroupId();

    // The opaque id decodes back to its single group-by entry.
    ImmutableList<FleetGroupSearcher.GroupEntry> entries =
        FleetGroupSearcher.decodeGroupId(androidGroupId);
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).key()).isEqualTo("dimension::os");
    assertThat(entries.get(0).noValue()).isFalse();
    assertThat(entries.get(0).values()).containsExactly("android");

    // Expanding it returns exactly the android devices, sorted by uuid.
    FleetFlatResults expanded =
        searcher.expandGroup(
            corpus,
            ImmutableList.of(),
            androidGroupId,
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
    assertThat(rowIds(expanded)).containsExactly("device-0", "device-1", "device-4").inOrder();
    assertThat(expanded.getTotal()).isEqualTo(3);
  }

  @Test
  public void expandGroup_sortsRowsByRequestedColumnAndDirection() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            corpus,
            ImmutableList.of(),
            ImmutableList.of("dimension::os"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
    String androidGroupId = groupByFirstValue(results, "android").getGroupId();

    // Sort by status ascending: BUSY (device-1), then IDLE (device-0, device-4 tie-broken by uuid).
    FleetFlatResults sortedByStatusAsc =
        searcher.expandGroup(
            corpus,
            ImmutableList.of(),
            androidGroupId,
            EXPAND_COLUMNS,
            FleetColumnSort.newBuilder().setKey("device_field::status").setAscending(true).build(),
            FleetPageRequest.getDefaultInstance());
    assertThat(rowIds(sortedByStatusAsc))
        .containsExactly("device-1", "device-0", "device-4")
        .inOrder();

    // Sort by uuid descending: device-4, device-1, device-0.
    FleetFlatResults sortedByUuidDesc =
        searcher.expandGroup(
            corpus,
            ImmutableList.of(),
            androidGroupId,
            EXPAND_COLUMNS,
            FleetColumnSort.newBuilder().setKey("device_field::uuid").setAscending(false).build(),
            FleetPageRequest.getDefaultInstance());
    assertThat(rowIds(sortedByUuidDesc))
        .containsExactly("device-4", "device-1", "device-0")
        .inOrder();
  }

  @Test
  public void noValueGroup_expandsToDevicesLackingTheKey() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            corpus,
            ImmutableList.of(),
            ImmutableList.of("dimension::os"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
    String noValueId = groupByFirstValue(results, "(no value)").getGroupId();

    ImmutableList<FleetGroupSearcher.GroupEntry> entries =
        FleetGroupSearcher.decodeGroupId(noValueId);
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).noValue()).isTrue();

    FleetFlatResults expanded =
        searcher.expandGroup(
            corpus,
            ImmutableList.of(),
            noValueId,
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
    assertThat(rowIds(expanded)).containsExactly("device-3");
  }

  @Test
  public void multiValuedKey_wholeValueSetIsOneGroup() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            corpus,
            ImmutableList.of(),
            ImmutableList.of("device_field::driver"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    // driverA+driverC is a single group, distinct from the driverA-only group. driverA holds
    // device-0 and
    // device-4; driverB holds device-1; driverA+driverC holds device-2; device-3 has no driver.
    assertThat(groupValues(results))
        .containsExactly("driverA", "driverB", "driverA, driverC", "(no value)");
    FleetGroup driverAc = groupByFirstValue(results, "driverA, driverC");
    assertThat(driverAc.getItemCount()).isEqualTo(1);

    FleetFlatResults expanded =
        searcher.expandGroup(
            corpus,
            ImmutableList.of(),
            driverAc.getGroupId(),
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
    // Exact-set membership: device-2 (driverA+driverC) only, not the driverA-only devices.
    assertThat(rowIds(expanded)).containsExactly("device-2");
  }

  @Test
  public void sortByItemCountAscending_reordersGroups() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            corpus,
            ImmutableList.of(),
            ImmutableList.of("dimension::os"),
            FleetGroupSort.newBuilder()
                .setField(
                    FleetGroupSortField.newBuilder()
                        .setItemCount(FleetItemCountSort.getDefaultInstance()))
                .setAscending(true)
                .build(),
            FleetPageRequest.getDefaultInstance());

    // Ascending count: the two size-1 groups first (present value "ios" before "(no value)"), then
    // android (3).
    assertThat(groupValues(results)).containsExactly("ios", "(no value)", "android").inOrder();
  }

  @Test
  public void sortByItemCount_breaksEqualCountTiesInNumericOrderWithNoValueLast() {
    LabQueryResult tempFleet =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData(
                            "lab1",
                            "1.1.1.1",
                            deviceWithTemp("dev-0", "9"),
                            deviceWithTemp("dev-1", "38"),
                            deviceWithTemp("dev-2", "-1"),
                            deviceWithTemp("dev-3", ""),
                            deviceWithTemp("dev-4", "19"),
                            deviceWithTemp("dev-5", "19"))))
            .build();
    FleetSnapshot tempSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(tempFleet, BUILD_TIME);
    DeviceCorpus tempCorpus =
        new DeviceCorpus(tempSnapshot, new LazyPostings(tempSnapshot.devices()), null);

    FleetGroupedResults countDesc =
        searcher.searchGrouped(
            tempCorpus,
            ImmutableList.of(),
            ImmutableList.of("dimension::battery_temperature"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
    assertThat(groupValues(countDesc))
        .containsExactly("19", "-1", "9", "38", "(no value)")
        .inOrder();

    FleetGroupedResults countAsc =
        searcher.searchGrouped(
            tempCorpus,
            ImmutableList.of(),
            ImmutableList.of("dimension::battery_temperature"),
            FleetGroupSort.newBuilder()
                .setField(
                    FleetGroupSortField.newBuilder()
                        .setItemCount(FleetItemCountSort.getDefaultInstance()))
                .setAscending(true)
                .build(),
            FleetPageRequest.getDefaultInstance());
    assertThat(groupValues(countAsc))
        .containsExactly("-1", "9", "38", "(no value)", "19")
        .inOrder();
  }

  @Test
  public void multiValueNumericGroup_formatsAndSortsPartsNumericallyAndExpands() {
    LabQueryResult sdkFleet =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData(
                            "lab1",
                            "1.1.1.1",
                            deviceWithDimensions("dev-multi", "sdk_version", "100", "9"),
                            deviceWithDimensions("dev-eight", "sdk_version", "8"),
                            deviceWithDimensions("dev-twelve", "sdk_version", "12"))))
            .build();
    FleetSnapshot sdkSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(sdkFleet, BUILD_TIME);
    DeviceCorpus sdkCorpus =
        new DeviceCorpus(sdkSnapshot, new LazyPostings(sdkSnapshot.devices()), null);

    FleetGroupedResults grouped =
        searcher.searchGrouped(
            sdkCorpus,
            ImmutableList.of(),
            ImmutableList.of("dimension::sdk_version"),
            FleetGroupSort.newBuilder()
                .setField(FleetGroupSortField.newBuilder().setGroupKey("dimension::sdk_version"))
                .setAscending(true)
                .build(),
            FleetPageRequest.getDefaultInstance());
    assertThat(groupValues(grouped)).containsExactly("8", "9, 100", "12").inOrder();

    FleetGroup multiGroup = groupByFirstValue(grouped, "9, 100");
    FleetFlatResults expanded =
        searcher.expandGroup(
            sdkCorpus,
            ImmutableList.of(),
            multiGroup.getGroupId(),
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
    assertThat(rowIds(expanded)).containsExactly("dev-multi");
  }

  @Test
  public void multiValueVersionGroup_ordersHeadersByVersionSegments() {
    LabQueryResult versionFleet =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData(
                            "lab1",
                            "1.1.1.1",
                            deviceWithDimensions("dev-0", "software_version", "18.0", "17.10"),
                            deviceWithDimensions("dev-1", "software_version", "17.5"),
                            deviceWithDimensions("dev-2", "software_version", "18.0", "17.4"))))
            .build();
    FleetSnapshot versionSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(versionFleet, BUILD_TIME);
    DeviceCorpus versionCorpus =
        new DeviceCorpus(versionSnapshot, new LazyPostings(versionSnapshot.devices()), null);

    FleetGroupedResults grouped =
        searcher.searchGrouped(
            versionCorpus,
            ImmutableList.of(),
            ImmutableList.of("dimension::software_version"),
            FleetGroupSort.newBuilder()
                .setField(
                    FleetGroupSortField.newBuilder().setGroupKey("dimension::software_version"))
                .setAscending(true)
                .build(),
            FleetPageRequest.getDefaultInstance());
    assertThat(groupValues(grouped)).containsExactly("17.4, 18.0", "17.5", "17.10, 18.0").inOrder();
  }

  @Test
  public void sortByGroupKey_ordersNumericGroupsNumericallyWithNoValueLast() {
    LabQueryResult tempFleet =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData(
                            "lab1",
                            "1.1.1.1",
                            deviceWithTemp("dev-0", "9"),
                            deviceWithTemp("dev-1", "38"),
                            deviceWithTemp("dev-2", "-1"),
                            deviceWithTemp("dev-3", ""),
                            deviceWithTemp("dev-4", "25"))))
            .build();
    FleetSnapshot tempSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(tempFleet, BUILD_TIME);
    DeviceCorpus tempCorpus =
        new DeviceCorpus(tempSnapshot, new LazyPostings(tempSnapshot.devices()), null);

    FleetGroupedResults ascending =
        searcher.searchGrouped(
            tempCorpus,
            ImmutableList.of(),
            ImmutableList.of("dimension::battery_temperature"),
            FleetGroupSort.newBuilder()
                .setField(
                    FleetGroupSortField.newBuilder().setGroupKey("dimension::battery_temperature"))
                .setAscending(true)
                .build(),
            FleetPageRequest.getDefaultInstance());
    assertThat(groupValues(ascending))
        .containsExactly("-1", "9", "25", "38", "(no value)")
        .inOrder();

    FleetGroupedResults descending =
        searcher.searchGrouped(
            tempCorpus,
            ImmutableList.of(),
            ImmutableList.of("dimension::battery_temperature"),
            FleetGroupSort.newBuilder()
                .setField(
                    FleetGroupSortField.newBuilder().setGroupKey("dimension::battery_temperature"))
                .setAscending(false)
                .build(),
            FleetPageRequest.getDefaultInstance());
    assertThat(groupValues(descending))
        .containsExactly("38", "25", "9", "-1", "(no value)")
        .inOrder();
  }

  @Test
  public void threeKeyGuard_capsToFirstThreeKnownKeys() {
    FleetGroupedResults results =
        searcher.searchGrouped(
            corpus,
            ImmutableList.of(),
            ImmutableList.of(
                "dimension::os",
                "device_field::status",
                "device_field::type",
                "device_field::driver"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    assertThat(results.getGroupByKeysCount()).isEqualTo(3);
    assertThat(results.getGroupByKeys(0).getKey()).isEqualTo("dimension::os");
    assertThat(results.getGroupByKeys(1).getKey()).isEqualTo("device_field::status");
    assertThat(results.getGroupByKeys(2).getKey()).isEqualTo("device_field::type");
  }

  @Test
  public void groupHeaderPagination_walksPagesWithTokens() {
    FleetPageRequest firstPage = FleetPageRequest.newBuilder().setPageSize(2).build();
    FleetGroupedResults page1 =
        searcher.searchGrouped(
            corpus,
            ImmutableList.of(),
            ImmutableList.of("device_field::driver"),
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
            corpus,
            ImmutableList.of(),
            ImmutableList.of("device_field::driver"),
            FleetGroupSort.getDefaultInstance(),
            secondPage);

    assertThat(page2.getGroupsCount()).isEqualTo(2);
    assertThat(page2.getRangeStart()).isEqualTo(3);
    assertThat(page2.getRangeEnd()).isEqualTo(4);
    assertThat(page2.getNextPageToken()).isEmpty();
    assertThat(page2.getPrevPageToken()).isNotEmpty();
  }

  @Test
  public void expand_defaultPageSizeAndCustomPaginationWalksPages() {
    // A single android group of 150 devices exercises default page size (100) and custom page size.
    FleetSnapshot large =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(uniformAndroidFleet(150), BUILD_TIME);
    DeviceCorpus largeCorpus = new DeviceCorpus(large, new LazyPostings(large.devices()), null);
    FleetGroupedResults headers =
        searcher.searchGrouped(
            largeCorpus,
            ImmutableList.of(),
            ImmutableList.of("dimension::os"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
    String groupId = headers.getGroups(0).getGroupId();

    // Null page request and default page request both use 100 rows per page, and a page token with
    // an unset page size still advances to the next 100-row page.
    FleetFlatResults nullPage =
        searcher.expandGroup(
            largeCorpus,
            ImmutableList.of(),
            groupId,
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            /* page= */ null);
    assertThat(nullPage.getRowsCount()).isEqualTo(100);
    assertThat(nullPage.getRangeStart()).isEqualTo(1);
    assertThat(nullPage.getRangeEnd()).isEqualTo(100);

    FleetFlatResults defaultPage1 =
        searcher.expandGroup(
            largeCorpus,
            ImmutableList.of(),
            groupId,
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
    assertThat(defaultPage1.getTotal()).isEqualTo(150);
    assertThat(defaultPage1.getRowsCount()).isEqualTo(100);
    assertThat(defaultPage1.getRangeStart()).isEqualTo(1);
    assertThat(defaultPage1.getRangeEnd()).isEqualTo(100);
    assertThat(defaultPage1.getNextPageToken()).isNotEmpty();
    assertThat(defaultPage1.getPrevPageToken()).isEmpty();

    FleetFlatResults defaultPage2 =
        searcher.expandGroup(
            largeCorpus,
            ImmutableList.of(),
            groupId,
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.newBuilder().setPageToken(defaultPage1.getNextPageToken()).build());
    assertThat(defaultPage2.getRowsCount()).isEqualTo(50);
    assertThat(defaultPage2.getRangeStart()).isEqualTo(101);
    assertThat(defaultPage2.getRangeEnd()).isEqualTo(150);
    assertThat(defaultPage2.getNextPageToken()).isEmpty();

    // Custom page size (50) walks page 1 -> page 2 -> page 3 -> prev back to page 1.
    FleetFlatResults page1 =
        searcher.expandGroup(
            largeCorpus,
            ImmutableList.of(),
            groupId,
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.newBuilder().setPageSize(50).build());
    assertThat(page1.getRowsCount()).isEqualTo(50);
    assertThat(page1.getRangeStart()).isEqualTo(1);
    assertThat(page1.getRangeEnd()).isEqualTo(50);
    assertThat(page1.getRows(0).getId()).isEqualTo("device-000");
    assertThat(page1.getRows(49).getId()).isEqualTo("device-049");
    assertThat(page1.getNextPageToken()).isNotEmpty();
    assertThat(page1.getPrevPageToken()).isEmpty();

    FleetFlatResults page2 =
        searcher.expandGroup(
            largeCorpus,
            ImmutableList.of(),
            groupId,
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.newBuilder()
                .setPageSize(50)
                .setPageToken(page1.getNextPageToken())
                .build());
    assertThat(page2.getRowsCount()).isEqualTo(50);
    assertThat(page2.getRangeStart()).isEqualTo(51);
    assertThat(page2.getRangeEnd()).isEqualTo(100);
    assertThat(page2.getRows(0).getId()).isEqualTo("device-050");
    assertThat(page2.getRows(49).getId()).isEqualTo("device-099");
    assertThat(page2.getNextPageToken()).isNotEmpty();
    assertThat(page2.getPrevPageToken()).isNotEmpty();

    FleetFlatResults page3 =
        searcher.expandGroup(
            largeCorpus,
            ImmutableList.of(),
            groupId,
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.newBuilder()
                .setPageSize(50)
                .setPageToken(page2.getNextPageToken())
                .build());
    assertThat(page3.getRowsCount()).isEqualTo(50);
    assertThat(page3.getRangeStart()).isEqualTo(101);
    assertThat(page3.getRangeEnd()).isEqualTo(150);
    assertThat(page3.getRows(0).getId()).isEqualTo("device-100");
    assertThat(page3.getNextPageToken()).isEmpty();
    assertThat(page3.getPrevPageToken()).isNotEmpty();

    FleetFlatResults backToPage1 =
        searcher.expandGroup(
            largeCorpus,
            ImmutableList.of(),
            groupId,
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.newBuilder()
                .setPageSize(50)
                .setPageToken(page2.getPrevPageToken())
                .build());
    assertThat(backToPage1.getRangeStart()).isEqualTo(1);
    assertThat(backToPage1.getRangeEnd()).isEqualTo(50);
    assertThat(backToPage1.getPrevPageToken()).isEmpty();
  }

  @Test
  public void expand_unknownGroupIdReturnsNoRows() {
    FleetFlatResults expanded =
        searcher.expandGroup(
            corpus,
            ImmutableList.of(),
            "not-a-real-group-id",
            EXPAND_COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());
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

  @Test
  public void groupByUuid_overTenThousandGroups_returnsResultsWithPagination() {
    int count = 10_005;
    FleetSnapshot largeSnapshot =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(uniformAndroidFleet(count), BUILD_TIME);
    LazyPostings largePostings = new LazyPostings(largeSnapshot.devices());
    DeviceCorpus largeCorpus = new DeviceCorpus(largeSnapshot, largePostings, null);

    FleetGroupedResults results =
        searcher.searchGrouped(
            largeCorpus,
            ImmutableList.of(),
            ImmutableList.of("device_field::uuid"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.newBuilder().setPageSize(25).build());

    assertThat(results.getTotalGroups()).isEqualTo(count);
    assertThat(results.getTotalItems()).isEqualTo(count);
    assertThat(results.getGroupsCount()).isEqualTo(25);
    assertThat(results.getNextPageToken()).isNotEmpty();
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
                .addDriver("driverA")
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
                .addDriver("driverB")
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
                .addDriver("driverA")
                .addDriver("driverC")
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
                .addDriver("driverA")
                .addOwner("alice")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("os", "android"))))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }

  private static DeviceInfo deviceWithTemp(String id, String temp) {
    return temp.isEmpty()
        ? deviceWithDimensions(id, "battery_temperature")
        : deviceWithDimensions(id, "battery_temperature", temp);
  }

  private static DeviceInfo deviceWithDimensions(String id, String name, String... values) {
    DeviceCompositeDimension.Builder dims = DeviceCompositeDimension.newBuilder();
    for (String value : values) {
      dims.addSupportedDimension(dimension(name, value));
    }
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId(id))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder().addType("AndroidRealDevice").setCompositeDimension(dims))
        .build();
  }
}
