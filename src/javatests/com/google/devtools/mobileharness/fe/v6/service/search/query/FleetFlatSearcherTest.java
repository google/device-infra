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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Cell;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Column;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnSort;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFlatResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPageRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetUtilization;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Indicator;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Row;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DimensionOverlay;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.HostEnrichment;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.devtools.mobileharness.fe.v6.service.search.index.OverlayView;
import com.google.devtools.mobileharness.fe.v6.service.search.index.Postings;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw;
import com.google.inject.Guice;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetFlatSearcher}. */
@RunWith(JUnit4.class)
public final class FleetFlatSearcherTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  private static final ImmutableList<String> COLUMNS =
      ImmutableList.of(
          "device_field::uuid", "host_field::host_name", "device_field::status", "dimension::os");

  // Synthetic fleet built through the real index builder:
  //   device-0: host lab1 (1.1.1.1), IDLE, owner alice, dim os=android, dim model="Pixel 8".
  //   device-1: host lab1 (1.1.1.1), BUSY, owner bob,   dim os=android, dim model="Pixel 7".
  //   device-2: host lab2 (2.2.2.2), IDLE, owners alice + carol, dim os=ios, no model dimension.
  // FleetIndexBuilder and FleetFlatSearcher both have package-private @Inject constructors, so
  // obtain them through Guice rather than constructing directly.
  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final LazyPostings postings = new LazyPostings(snapshot.devices());
  private final DeviceCorpus corpus = new DeviceCorpus(snapshot, postings, null);
  private final FleetFlatSearcher searcher =
      Guice.createInjector().getInstance(FleetFlatSearcher.class);

  @Test
  public void noFilter_returnsAllRowsSortedByUuidAscending() {
    FleetFlatResults results =
        searcher.searchFlat(
            corpus,
            ImmutableList.of(),
            COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    assertThat(rowIds(results)).containsExactly("device-0", "device-1", "device-2").inOrder();
    assertThat(results.getTotal()).isEqualTo(3);
    assertThat(results.getRangeStart()).isEqualTo(1);
    assertThat(results.getRangeEnd()).isEqualTo(3);
    assertThat(results.getNextPageToken()).isEmpty();
    assertThat(results.getPrevPageToken()).isEmpty();
  }

  @Test
  public void filter_narrowsResults() {
    FleetFlatResults results =
        searcher.searchFlat(
            corpus,
            ImmutableList.of(simple("device_field::status", "IDLE")),
            COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    assertThat(rowIds(results)).containsExactly("device-0", "device-2").inOrder();
    assertThat(results.getTotal()).isEqualTo(2);
  }

  @Test
  public void columns_produceExpectedHeaders() {
    FleetFlatResults results =
        searcher.searchFlat(
            corpus,
            ImmutableList.of(),
            COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    assertThat(columnKeys(results))
        .containsExactly(
            "device_field::uuid", "host_field::host_name", "device_field::status", "dimension::os")
        .inOrder();
    assertThat(results.getColumns(0).getDisplayName()).isEqualTo("UUID");
    assertThat(results.getColumns(0).getDescription()).isEmpty();
    assertThat(results.getColumns(1).getDisplayName()).isEqualTo("Host Name");
    assertThat(results.getColumns(2).getDisplayName()).isEqualTo("Status");
    assertThat(results.getColumns(3).getDisplayName()).isEqualTo("OS");
    assertThat(corpus.column("device_field::version").getDescription())
        .isEqualTo(
            "The device's OS version: the release_version dimension (for example Android 16), or"
                + " software_version when release_version is missing (for example iOS). For the"
                + " Android API level, use SDK Version.");
  }

  @Test
  public void cells_haveExpectedTypedKinds() {
    FleetFlatResults results =
        searcher.searchFlat(
            corpus,
            ImmutableList.of(simple("device_field::uuid", "device-0")),
            COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    Row row = results.getRows(0);
    Cell uuidCell = row.getCells(0);
    assertThat(uuidCell.getKindCase()).isEqualTo(Cell.KindCase.LINK);
    assertThat(uuidCell.getLink().getText()).isEqualTo("device-0");
    assertThat(uuidCell.getLink().getTarget().getDevice().getId()).isEqualTo("device-0");
    assertThat(uuidCell.getLink().getTarget().getDevice().getHostName()).isEqualTo("lab1");
    assertThat(uuidCell.getLink().getTarget().getDevice().getHostIp()).isEqualTo("1.1.1.1");
    assertThat(uuidCell.getLink().getTarget().getDevice().getUniverse()).isEmpty();

    Cell hostCell = row.getCells(1);
    assertThat(hostCell.getKindCase()).isEqualTo(Cell.KindCase.LINK);
    assertThat(hostCell.getLink().getText()).isEqualTo("lab1");
    assertThat(hostCell.getLink().getTarget().getHost().getHostName()).isEqualTo("lab1");
    assertThat(hostCell.getLink().getTarget().getHost().getHostIp()).isEqualTo("1.1.1.1");
    assertThat(hostCell.getLink().getTarget().getHost().getUniverse()).isEmpty();

    Cell statusCell = row.getCells(2);
    assertThat(statusCell.getKindCase()).isEqualTo(Cell.KindCase.STATUS);
    assertThat(statusCell.getStatus().getText()).isEqualTo("IDLE");
    assertThat(statusCell.getStatus().getIndicator()).isEqualTo(Indicator.INDICATOR_OK);

    Cell osCell = row.getCells(3);
    assertThat(osCell.getKindCase()).isEqualTo(Cell.KindCase.TEXT);
    assertThat(osCell.getText().getValue()).isEqualTo("android");
  }

  @Test
  public void busyStatus_mapsToActiveIndicator() {
    FleetFlatResults results =
        searcher.searchFlat(
            corpus,
            ImmutableList.of(simple("device_field::uuid", "device-1")),
            COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    Cell statusCell = results.getRows(0).getCells(2);
    assertThat(statusCell.getStatus().getText()).isEqualTo("BUSY");
    assertThat(statusCell.getStatus().getIndicator()).isEqualTo(Indicator.INDICATOR_ACTIVE);
  }

  @Test
  public void multiValueKey_commaJoinedIntoTextCell() {
    FleetFlatResults results =
        searcher.searchFlat(
            corpus,
            ImmutableList.of(simple("device_field::uuid", "device-2")),
            ImmutableList.of("device_field::driver"),
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    Cell driverCell = results.getRows(0).getCells(0);
    assertThat(driverCell.getKindCase()).isEqualTo(Cell.KindCase.TEXT);
    assertThat(driverCell.getText().getValue()).isEqualTo("driverA, driverC");
  }

  @Test
  public void sortByStatus_ascending() {
    FleetFlatResults results =
        searcher.searchFlat(
            corpus,
            ImmutableList.of(),
            COLUMNS,
            FleetColumnSort.newBuilder().setKey("device_field::status").setAscending(true).build(),
            FleetPageRequest.getDefaultInstance());

    // BUSY sorts before IDLE; the two IDLE devices tie-break by UUID ascending.
    assertThat(rowIds(results)).containsExactly("device-1", "device-0", "device-2").inOrder();
  }

  @Test
  public void sortByStatus_descending() {
    FleetFlatResults results =
        searcher.searchFlat(
            corpus,
            ImmutableList.of(),
            COLUMNS,
            FleetColumnSort.newBuilder().setKey("device_field::status").setAscending(false).build(),
            FleetPageRequest.getDefaultInstance());

    // Descending reverses the whole ordering, tie-break included.
    assertThat(rowIds(results)).containsExactly("device-2", "device-0", "device-1").inOrder();
  }

  @Test
  public void pagination_walksPagesWithTokens() {
    FleetPageRequest firstPage = FleetPageRequest.newBuilder().setPageSize(2).build();
    FleetFlatResults page1 =
        searcher.searchFlat(
            corpus, ImmutableList.of(), COLUMNS, FleetColumnSort.getDefaultInstance(), firstPage);

    assertThat(rowIds(page1)).containsExactly("device-0", "device-1").inOrder();
    assertThat(page1.getTotal()).isEqualTo(3);
    assertThat(page1.getRangeStart()).isEqualTo(1);
    assertThat(page1.getRangeEnd()).isEqualTo(2);
    assertThat(page1.getNextPageToken()).isNotEmpty();
    assertThat(page1.getPrevPageToken()).isEmpty();

    FleetPageRequest secondPage =
        FleetPageRequest.newBuilder().setPageSize(2).setPageToken(page1.getNextPageToken()).build();
    FleetFlatResults page2 =
        searcher.searchFlat(
            corpus, ImmutableList.of(), COLUMNS, FleetColumnSort.getDefaultInstance(), secondPage);

    assertThat(rowIds(page2)).containsExactly("device-2");
    assertThat(page2.getRangeStart()).isEqualTo(3);
    assertThat(page2.getRangeEnd()).isEqualTo(3);
    assertThat(page2.getNextPageToken()).isEmpty();
    assertThat(page2.getPrevPageToken()).isNotEmpty();
  }

  @Test
  public void hostAttributeColumns_projectValuesWithEmptyFallback() {
    // Layer host attributes onto the fleet: lab1 runs debian with a Core Lab release, lab2 runs
    // macos with no HostInfoService enrichment. The host keys added by CL A must render their
    // stamped values rather than a blank cell.
    CoreFleetRawData raw =
        CoreFleetRawData.builder()
            .setLabData(hostAttributeFleet())
            .setHostEnrichments(
                ImmutableMap.of(
                    "lab1",
                    HostEnrichment.builder()
                        .setReleaseType(Optional.of("SHARED_LAB"))
                        .setReleaseStatus(Optional.of("RUNNING"))
                        .setDaemonStatus(Optional.of("RUNNING"))
                        .setLabServerVersion(Optional.of("1.2.3"))
                        .setAtsControllerId(Optional.of("vivo"))
                        .build()))
            .build();
    FleetSnapshot enriched =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(raw, BUILD_TIME);
    LazyPostings enrichedPostings = new LazyPostings(enriched.devices());

    ImmutableList<String> columns =
        ImmutableList.of(
            "device_field::uuid",
            "host_field::host_name",
            "host_field::lab_server_version",
            "host_property::host_os");
    FleetFlatResults results =
        searcher.searchFlat(
            new DeviceCorpus(enriched, enrichedPostings, null),
            ImmutableList.of(),
            columns,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    assertThat(rowIds(results)).containsExactly("device-0", "device-1", "device-2").inOrder();

    // device-0 on lab1: lab_server_version is 1.2.3, host_os is debian, universe is vivo.
    Row lab1Row = results.getRows(0);
    Cell uuidCell = lab1Row.getCells(0);
    assertThat(uuidCell.getLink().getTarget().getDevice().getUniverse()).isEqualTo("vivo");
    Cell hostCell = lab1Row.getCells(1);
    assertThat(hostCell.getLink().getTarget().getHost().getUniverse()).isEqualTo("vivo");
    Cell versionCell = lab1Row.getCells(2);
    assertThat(versionCell.getKindCase()).isEqualTo(Cell.KindCase.TEXT);
    assertThat(versionCell.getText().getValue()).isEqualTo("1.2.3");
    assertThat(lab1Row.getCells(3).getText().getValue()).isEqualTo("debian");

    // device-2 on lab2: no lab_server_version, so that cell is blank; host os still
    // renders from the LabInfo host property. universe is empty.
    Row lab2Row = results.getRows(2);
    assertThat(lab2Row.getCells(0).getLink().getTarget().getDevice().getUniverse()).isEmpty();
    assertThat(lab2Row.getCells(1).getLink().getTarget().getHost().getUniverse()).isEmpty();
    assertThat(lab2Row.getCells(2).getText().getValue()).isEmpty();
    assertThat(lab2Row.getCells(3).getText().getValue()).isEqualTo("macos");
  }

  @Test
  public void sortByHostKey_ordersRowsByHostAttribute() {
    FleetSnapshot enriched =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(CoreFleetRawData.builder().setLabData(hostAttributeFleet()).build(), BUILD_TIME);
    LazyPostings enrichedPostings = new LazyPostings(enriched.devices());
    ImmutableList<String> columns =
        ImmutableList.of("device_field::uuid", "host_property::host_os");

    // debian (lab1) sorts before macos (lab2); the two lab1 devices tie-break by UUID ascending.
    FleetFlatResults ascending =
        searcher.searchFlat(
            new DeviceCorpus(enriched, enrichedPostings, null),
            ImmutableList.of(),
            columns,
            FleetColumnSort.newBuilder()
                .setKey("host_property::host_os")
                .setAscending(true)
                .build(),
            FleetPageRequest.getDefaultInstance());
    assertThat(rowIds(ascending)).containsExactly("device-0", "device-1", "device-2").inOrder();

    // Descending reverses the whole ordering, tie-break included.
    FleetFlatResults descending =
        searcher.searchFlat(
            new DeviceCorpus(enriched, enrichedPostings, null),
            ImmutableList.of(),
            columns,
            FleetColumnSort.newBuilder()
                .setKey("host_property::host_os")
                .setAscending(false)
                .build(),
            FleetPageRequest.getDefaultInstance());
    assertThat(rowIds(descending)).containsExactly("device-2", "device-1", "device-0").inOrder();
  }

  @Test
  public void pagination_defaultPageSizeReturnsEverything() {
    FleetFlatResults results =
        searcher.searchFlat(
            corpus,
            ImmutableList.of(),
            COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    assertThat(results.getRowsCount()).isEqualTo(3);
    assertThat(results.getNextPageToken()).isEmpty();
  }

  @Test
  public void sortByOverlayDimension_ordersMixedCaseAndMissingValues() {
    String overlayKey = "dimension::carrier";
    DimensionOverlay overlay =
        DimensionOverlay.create(
            DimensionOverlayRaw.create(
                overlayKey,
                ImmutableMap.of(
                    "device-0", ImmutableList.of("Verizon"), "device-1", ImmutableList.of("att"))),
            snapshot);
    DeviceCorpus overlayCorpus =
        new DeviceCorpus(
            snapshot,
            postings,
            null,
            OverlayView.bind(snapshot, ImmutableMap.of(overlayKey, overlay)));
    ImmutableList<String> columns = ImmutableList.of("device_field::uuid", overlayKey);

    // Missing value ("") sorts first on ascending, and "Verizon" lowercases to "verizon" after
    // "att" despite 'V' < 'a' in ASCII.
    FleetFlatResults ascending =
        searcher.searchFlat(
            overlayCorpus,
            ImmutableList.of(),
            columns,
            FleetColumnSort.newBuilder().setKey(overlayKey).setAscending(true).build(),
            FleetPageRequest.getDefaultInstance());
    assertThat(rowIds(ascending)).containsExactly("device-2", "device-1", "device-0").inOrder();
    assertThat(ascending.getRows(2).getCells(1).getText().getValue()).isEqualTo("Verizon");

    FleetFlatResults descending =
        searcher.searchFlat(
            overlayCorpus,
            ImmutableList.of(),
            columns,
            FleetColumnSort.newBuilder().setKey(overlayKey).setAscending(false).build(),
            FleetPageRequest.getDefaultInstance());
    assertThat(rowIds(descending)).containsExactly("device-0", "device-1", "device-2").inOrder();
  }

  @Test
  public void boundedTopKPaging_multiLevelHeapWalksAllPagesAscendingAndDescending() {
    LabQueryResult eightDeviceFleet =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData(
                            "lab1",
                            "1.1.1.1",
                            deviceWithModel("dev-0", "Zulu"),
                            deviceWithModel("dev-1", "mike"),
                            deviceWithModel("dev-2", "Alpha"),
                            deviceWithModel("dev-3", ""),
                            deviceWithModel("dev-4", "charlie"),
                            deviceWithModel("dev-5", "Bravo"),
                            deviceWithModel("dev-6", "charlie"),
                            deviceWithModel("dev-7", "echo"))))
            .build();
    FleetSnapshot eightSnapshot =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(eightDeviceFleet, BUILD_TIME);
    DeviceCorpus eightCorpus =
        new DeviceCorpus(eightSnapshot, new LazyPostings(eightSnapshot.devices()), null);
    ImmutableList<String> columns = ImmutableList.of("device_field::uuid", "dimension::model");

    FleetColumnSort ascSort =
        FleetColumnSort.newBuilder().setKey("dimension::model").setAscending(true).build();
    FleetFlatResults ascPage1 =
        searcher.searchFlat(
            eightCorpus,
            ImmutableList.of(),
            columns,
            ascSort,
            FleetPageRequest.newBuilder().setPageSize(3).build());
    assertThat(rowIds(ascPage1)).containsExactly("dev-3", "dev-2", "dev-5").inOrder();
    assertThat(ascPage1.getRangeStart()).isEqualTo(1);
    assertThat(ascPage1.getRangeEnd()).isEqualTo(3);

    FleetFlatResults ascPage2 =
        searcher.searchFlat(
            eightCorpus,
            ImmutableList.of(),
            columns,
            ascSort,
            FleetPageRequest.newBuilder()
                .setPageSize(3)
                .setPageToken(ascPage1.getNextPageToken())
                .build());
    assertThat(rowIds(ascPage2)).containsExactly("dev-4", "dev-6", "dev-7").inOrder();
    assertThat(ascPage2.getRangeStart()).isEqualTo(4);
    assertThat(ascPage2.getRangeEnd()).isEqualTo(6);

    FleetFlatResults ascPage3 =
        searcher.searchFlat(
            eightCorpus,
            ImmutableList.of(),
            columns,
            ascSort,
            FleetPageRequest.newBuilder()
                .setPageSize(3)
                .setPageToken(ascPage2.getNextPageToken())
                .build());
    assertThat(rowIds(ascPage3)).containsExactly("dev-1", "dev-0").inOrder();
    assertThat(ascPage3.getRangeStart()).isEqualTo(7);
    assertThat(ascPage3.getRangeEnd()).isEqualTo(8);
    assertThat(ascPage3.getNextPageToken()).isEmpty();

    // Exercise a 4-element heap (child index 3 -> parent index 1 -> root 0).
    FleetFlatResults ascFour =
        searcher.searchFlat(
            eightCorpus,
            ImmutableList.of(),
            columns,
            ascSort,
            FleetPageRequest.newBuilder().setPageSize(4).build());
    assertThat(rowIds(ascFour)).containsExactly("dev-3", "dev-2", "dev-5", "dev-4").inOrder();

    // Exercise right-child selection at root 0 in siftDown (pageSize = 3: heap = [7, 2, 3] when 1
    // replaces 7) and even-child parent indexing in siftUp at child = 6 (pageSize = 7: true parent
    // (6 - 1) >>> 1 = 2 holds "3" while sibling index 6 >>> 1 = 3 holds "7", so "6" at child = 6
    // must sift up past "3" at index 2 so "5a" later evicts "6").
    LabQueryResult heapBranchFleet =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData(
                            "lab1",
                            "1.1.1.1",
                            deviceWithModel("dev-0", "9"),
                            deviceWithModel("dev-1", "8"),
                            deviceWithModel("dev-2", "3"),
                            deviceWithModel("dev-3", "7"),
                            deviceWithModel("dev-4", "2"),
                            deviceWithModel("dev-5", "1"),
                            deviceWithModel("dev-6", "6"),
                            deviceWithModel("dev-7", "0"),
                            deviceWithModel("dev-8", "4"),
                            deviceWithModel("dev-9", "5"),
                            deviceWithModel("dev-10", "5a"))))
            .build();
    FleetSnapshot heapBranchSnapshot =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(heapBranchFleet, BUILD_TIME);
    DeviceCorpus heapBranchCorpus =
        new DeviceCorpus(heapBranchSnapshot, new LazyPostings(heapBranchSnapshot.devices()), null);

    FleetFlatResults branchTop3 =
        searcher.searchFlat(
            heapBranchCorpus,
            ImmutableList.of(),
            columns,
            ascSort,
            FleetPageRequest.newBuilder().setPageSize(3).build());
    assertThat(rowIds(branchTop3)).containsExactly("dev-7", "dev-5", "dev-4").inOrder();

    FleetFlatResults branchTop7 =
        searcher.searchFlat(
            heapBranchCorpus,
            ImmutableList.of(),
            columns,
            ascSort,
            FleetPageRequest.newBuilder().setPageSize(7).build());
    assertThat(rowIds(branchTop7))
        .containsExactly("dev-7", "dev-5", "dev-4", "dev-2", "dev-8", "dev-9", "dev-10")
        .inOrder();

    FleetColumnSort descSort =
        FleetColumnSort.newBuilder().setKey("dimension::model").setAscending(false).build();
    FleetFlatResults descPage1 =
        searcher.searchFlat(
            eightCorpus,
            ImmutableList.of(),
            columns,
            descSort,
            FleetPageRequest.newBuilder().setPageSize(3).build());
    assertThat(rowIds(descPage1)).containsExactly("dev-0", "dev-1", "dev-7").inOrder();

    FleetFlatResults descPage2 =
        searcher.searchFlat(
            eightCorpus,
            ImmutableList.of(),
            columns,
            descSort,
            FleetPageRequest.newBuilder()
                .setPageSize(3)
                .setPageToken(descPage1.getNextPageToken())
                .build());
    assertThat(rowIds(descPage2)).containsExactly("dev-6", "dev-4", "dev-5").inOrder();

    FleetFlatResults descPage3 =
        searcher.searchFlat(
            eightCorpus,
            ImmutableList.of(),
            columns,
            descSort,
            FleetPageRequest.newBuilder()
                .setPageSize(3)
                .setPageToken(descPage2.getNextPageToken())
                .build());
    assertThat(rowIds(descPage3)).containsExactly("dev-2", "dev-3").inOrder();
    assertThat(descPage3.getNextPageToken()).isEmpty();
  }

  @Test
  public void emptyAndOutOfRangePages_skipSortKeyExtractionAndClampTokens() {
    CountingCorpus counting = new CountingCorpus(corpus);

    FleetFlatResults noMatches =
        searcher.searchFlat(
            counting,
            ImmutableList.of(simple("device_field::status", "NONEXISTENT")),
            COLUMNS,
            null,
            null);
    assertThat(noMatches.getRowsList()).isEmpty();
    assertThat(noMatches.getTotal()).isEqualTo(0);
    assertThat(noMatches.getRangeStart()).isEqualTo(0);
    assertThat(noMatches.getRangeEnd()).isEqualTo(0);
    assertThat(noMatches.getNextPageToken()).isEmpty();
    assertThat(noMatches.getPrevPageToken()).isEmpty();
    assertThat(counting.firstSortKeyCalls).isEqualTo(0);

    String pastEndToken = Base64.getUrlEncoder().encodeToString("o:10".getBytes(UTF_8));
    FleetFlatResults pastEnd =
        searcher.searchFlat(
            counting,
            ImmutableList.of(),
            COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.newBuilder().setPageSize(2).setPageToken(pastEndToken).build());
    assertThat(pastEnd.getRowsList()).isEmpty();
    assertThat(pastEnd.getTotal()).isEqualTo(3);
    assertThat(pastEnd.getRangeStart()).isEqualTo(0);
    assertThat(pastEnd.getRangeEnd()).isEqualTo(3);
    assertThat(pastEnd.getNextPageToken()).isEmpty();
    assertThat(pastEnd.getPrevPageToken()).isNotEmpty();
    assertThat(counting.firstSortKeyCalls).isEqualTo(0);

    // Malformed, foreign, non-numeric, and negative tokens fall back or clamp to page 1.
    for (String badToken :
        ImmutableList.of(
            "!!!not-base64!!!",
            Base64.getUrlEncoder().encodeToString("foreign:2".getBytes(UTF_8)),
            Base64.getUrlEncoder().encodeToString("o:not-an-int".getBytes(UTF_8)),
            Base64.getUrlEncoder().encodeToString("o:-5".getBytes(UTF_8)))) {
      FleetFlatResults fallback =
          searcher.searchFlat(
              corpus,
              ImmutableList.of(),
              COLUMNS,
              null,
              FleetPageRequest.newBuilder().setPageSize(1).setPageToken(badToken).build());
      assertThat(rowIds(fallback)).containsExactly("device-0");
      assertThat(fallback.getPrevPageToken()).isEmpty();
    }
  }

  @Test
  public void duplicateRecordId_preservesFirstEncounteredRecordInBoundedHeap() {
    LabQueryResult dupFleet =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(2)
                    .addLabData(labData("lab1", "1.1.1.1", deviceWithModel("dup-uuid", "Pixel 8")))
                    .addLabData(labData("lab2", "2.2.2.2", deviceWithModel("dup-uuid", "Pixel 8"))))
            .build();
    FleetSnapshot dupSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(dupFleet, BUILD_TIME);
    DeviceCorpus dupCorpus =
        new DeviceCorpus(dupSnapshot, new LazyPostings(dupSnapshot.devices()), null);

    FleetColumnSort ascStatusSort =
        FleetColumnSort.newBuilder().setKey("device_field::status").setAscending(true).build();
    FleetFlatResults ascFirst =
        searcher.searchFlat(
            dupCorpus,
            ImmutableList.of(),
            COLUMNS,
            ascStatusSort,
            FleetPageRequest.newBuilder().setPageSize(1).build());
    assertThat(ascFirst.getRows(0).getCells(1).getLink().getText()).isEqualTo("lab1");

    FleetFlatResults ascSecond =
        searcher.searchFlat(
            dupCorpus,
            ImmutableList.of(),
            COLUMNS,
            ascStatusSort,
            FleetPageRequest.newBuilder()
                .setPageSize(1)
                .setPageToken(ascFirst.getNextPageToken())
                .build());
    assertThat(ascSecond.getRows(0).getCells(1).getLink().getText()).isEqualTo("lab2");

    FleetFlatResults ascBoth =
        searcher.searchFlat(
            dupCorpus,
            ImmutableList.of(),
            COLUMNS,
            ascStatusSort,
            FleetPageRequest.newBuilder().setPageSize(2).build());
    assertThat(
            ImmutableList.of(
                ascBoth.getRows(0).getCells(1).getLink().getText(),
                ascBoth.getRows(1).getCells(1).getLink().getText()))
        .containsExactly("lab1", "lab2")
        .inOrder();

    FleetColumnSort descStatusSort =
        FleetColumnSort.newBuilder().setKey("device_field::status").setAscending(false).build();
    FleetFlatResults descFirst =
        searcher.searchFlat(
            dupCorpus,
            ImmutableList.of(),
            COLUMNS,
            descStatusSort,
            FleetPageRequest.newBuilder().setPageSize(1).build());
    assertThat(descFirst.getRows(0).getCells(1).getLink().getText()).isEqualTo("lab1");

    FleetFlatResults descSecond =
        searcher.searchFlat(
            dupCorpus,
            ImmutableList.of(),
            COLUMNS,
            descStatusSort,
            FleetPageRequest.newBuilder()
                .setPageSize(1)
                .setPageToken(descFirst.getNextPageToken())
                .build());
    assertThat(descSecond.getRows(0).getCells(1).getLink().getText()).isEqualTo("lab2");

    FleetFlatResults descBoth =
        searcher.searchFlat(
            dupCorpus,
            ImmutableList.of(),
            COLUMNS,
            descStatusSort,
            FleetPageRequest.newBuilder().setPageSize(2).build());
    assertThat(
            ImmutableList.of(
                descBoth.getRows(0).getCells(1).getLink().getText(),
                descBoth.getRows(1).getCells(1).getLink().getText()))
        .containsExactly("lab1", "lab2")
        .inOrder();
  }

  // --- Helpers ---

  private static ImmutableList<String> rowIds(FleetFlatResults results) {
    ImmutableList.Builder<String> ids = ImmutableList.builder();
    for (Row row : results.getRowsList()) {
      ids.add(row.getId());
    }
    return ids.build();
  }

  private static ImmutableList<String> columnKeys(FleetFlatResults results) {
    ImmutableList.Builder<String> keys = ImmutableList.builder();
    for (Column column : results.getColumnsList()) {
      keys.add(column.getKey());
    }
    return keys.build();
  }

  private static Filter simple(String key, String value) {
    return Filter.newBuilder()
        .setKey(key)
        .setSimple(SimpleMatch.newBuilder().addValues(FilterValue.newBuilder().setValue(value)))
        .build();
  }

  // --- Synthetic fleet (mirrors FleetFilterEngineTest) ---

  private static LabQueryResult fleet() {
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(2)
                .addLabData(labData("lab1", "1.1.1.1", device0(), device1()))
                .addLabData(labData("lab2", "2.2.2.2", device2())))
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

  // A fleet that stamps a host_os host property per host so the host attribute keys have values.
  private static LabQueryResult hostAttributeFleet() {
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(2)
                .addLabData(labDataWithHostOs("lab1", "1.1.1.1", "debian", device0(), device1()))
                .addLabData(labDataWithHostOs("lab2", "2.2.2.2", "macos", device2())))
        .build();
  }

  private static LabData labDataWithHostOs(
      String hostName, String ip, String hostOs, DeviceInfo... devices) {
    DeviceList.Builder deviceList = DeviceList.newBuilder().setDeviceTotalCount(devices.length);
    for (DeviceInfo device : devices) {
      deviceList.addDeviceInfo(device);
    }
    return LabData.newBuilder()
        .setLabInfo(
            LabInfo.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName(hostName).setIp(ip))
                .setLabStatus(LabStatus.LAB_RUNNING)
                .setLabServerFeature(
                    LabServerFeature.newBuilder()
                        .setHostProperties(
                            HostProperties.newBuilder()
                                .addHostProperty(
                                    HostProperty.newBuilder().setKey("host_os").setValue(hostOs)))))
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
                        .addSupportedDimension(dimension("os", "android"))
                        .addSupportedDimension(dimension("model", "Pixel 8"))))
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
                        .addSupportedDimension(dimension("os", "android"))
                        .addSupportedDimension(dimension("model", "Pixel 7"))))
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

  private static DeviceInfo deviceWithModel(String id, String model) {
    DeviceCompositeDimension.Builder dims =
        DeviceCompositeDimension.newBuilder().addSupportedDimension(dimension("os", "android"));
    if (!model.isEmpty()) {
      dims.addSupportedDimension(dimension("model", model));
    }
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId(id))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder().addType("AndroidRealDevice").setCompositeDimension(dims))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }

  private static final class CountingCorpus implements SearchCorpus {
    private final SearchCorpus delegate;
    int firstSortKeyCalls;

    CountingCorpus(SearchCorpus delegate) {
      this.delegate = delegate;
    }

    @Override
    public FleetIndex index() {
      return delegate.index();
    }

    @Override
    public Postings postings() {
      return delegate.postings();
    }

    @Override
    public int recordCount() {
      return delegate.recordCount();
    }

    @Override
    public String recordId(int index) {
      return delegate.recordId(index);
    }

    @Override
    public SearchEntity entity() {
      return delegate.entity();
    }

    @Override
    public String identifierKey() {
      return delegate.identifierKey();
    }

    @Override
    public boolean isIdentifierKey(String keyId) {
      return delegate.isIdentifierKey(keyId);
    }

    @Override
    public ImmutableSet<String> valuesForKey(int index, String keyId) {
      return delegate.valuesForKey(index, keyId);
    }

    @Override
    public String firstSortKey(int index, String keyId) {
      firstSortKeyCalls++;
      return delegate.firstSortKey(index, keyId);
    }

    @Override
    public Column column(String keyId) {
      return delegate.column(keyId);
    }

    @Override
    public Cell cell(int index, String keyId) {
      return delegate.cell(index, keyId);
    }

    @Override
    public Optional<FleetUtilization> utilization(List<Integer> memberIndices) {
      return delegate.utilization(memberIndices);
    }

    @Override
    @Nullable
    public ScenarioCuration curation() {
      return delegate.curation();
    }

    @Override
    public KeyVocabulary vocabulary() {
      return delegate.vocabulary();
    }
  }
}
