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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Indicator;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Row;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DeviceEnrichment;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.HostEnrichment;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.inject.Guice;
import java.time.Instant;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetFlatSearcher}. */
@RunWith(JUnit4.class)
public final class FleetFlatSearcherTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  private static final ImmutableList<String> COLUMNS =
      ImmutableList.of("field::uuid", "host::host_name", "field::status", "dim::os");

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
            ImmutableList.of(simple("field::status", "IDLE")),
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
        .containsExactly("field::uuid", "host::host_name", "field::status", "dim::os")
        .inOrder();
    assertThat(results.getColumns(0).getDisplayName()).isEqualTo("UUID");
    assertThat(results.getColumns(1).getDisplayName()).isEqualTo("Host Name");
    assertThat(results.getColumns(2).getDisplayName()).isEqualTo("Status");
    assertThat(results.getColumns(3).getDisplayName()).isEqualTo("OS");
  }

  @Test
  public void cells_haveExpectedTypedKinds() {
    FleetFlatResults results =
        searcher.searchFlat(
            corpus,
            ImmutableList.of(simple("field::uuid", "device-0")),
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

    Cell hostCell = row.getCells(1);
    assertThat(hostCell.getKindCase()).isEqualTo(Cell.KindCase.LINK);
    assertThat(hostCell.getLink().getText()).isEqualTo("lab1");
    assertThat(hostCell.getLink().getTarget().getHost().getHostName()).isEqualTo("lab1");
    assertThat(hostCell.getLink().getTarget().getHost().getHostIp()).isEqualTo("1.1.1.1");

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
            ImmutableList.of(simple("field::uuid", "device-1")),
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
            ImmutableList.of(simple("field::uuid", "device-2")),
            ImmutableList.of("field::owner"),
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    Cell ownerCell = results.getRows(0).getCells(0);
    assertThat(ownerCell.getKindCase()).isEqualTo(Cell.KindCase.TEXT);
    assertThat(ownerCell.getText().getValue()).isEqualTo("alice, carol");
  }

  @Test
  public void sortByStatus_ascending() {
    FleetFlatResults results =
        searcher.searchFlat(
            corpus,
            ImmutableList.of(),
            COLUMNS,
            FleetColumnSort.newBuilder().setKey("field::status").setAscending(true).build(),
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
            FleetColumnSort.newBuilder().setKey("field::status").setAscending(false).build(),
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
  public void atsControllerColumn_showsFriendlyDisplayWithIdFallback() {
    // Enrich the same fleet with per-device ATS controllers and a controller-display registry that
    // only covers ctrl-1, so the cell shows the friendly display for ctrl-1 and falls back to the
    // raw id for the unregistered ctrl-2.
    CoreFleetRawData raw =
        CoreFleetRawData.builder()
            .setLabData(fleet())
            .setDeviceEnrichments(
                ImmutableMap.of(
                    "device-0",
                    DeviceEnrichment.builder().setAtsController(Optional.of("ctrl-1")).build(),
                    "device-1",
                    DeviceEnrichment.builder().setAtsController(Optional.of("ctrl-2")).build()))
            .setAtsControllerDisplays(ImmutableMap.of("ctrl-1", "ATS Lab One"))
            .build();
    FleetSnapshot enriched =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(raw, BUILD_TIME);

    FleetFlatResults results =
        searcher.searchFlat(
            new DeviceCorpus(enriched, postings, null),
            ImmutableList.of(),
            ImmutableList.of("field::uuid", "host::ats_controller"),
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    assertThat(rowIds(results)).containsExactly("device-0", "device-1", "device-2").inOrder();
    // device-0: ctrl-1 mapped to its friendly display.
    Cell registered = results.getRows(0).getCells(1);
    assertThat(registered.getKindCase()).isEqualTo(Cell.KindCase.TEXT);
    assertThat(registered.getText().getValue()).isEqualTo("ATS Lab One");
    // device-1: ctrl-2 has no registry entry, so it falls back to the raw controller id.
    assertThat(results.getRows(1).getCells(1).getText().getValue()).isEqualTo("ctrl-2");
    // device-2: no controller at all, so the cell is empty.
    assertThat(results.getRows(2).getCells(1).getText().getValue()).isEmpty();
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
                        .build()))
            .build();
    FleetSnapshot enriched =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(raw, BUILD_TIME);
    LazyPostings enrichedPostings = new LazyPostings(enriched.devices());

    ImmutableList<String> columns =
        ImmutableList.of("field::uuid", "host::lab_type", "host::host_os", "host::release_status");
    FleetFlatResults results =
        searcher.searchFlat(
            new DeviceCorpus(enriched, enrichedPostings, null),
            ImmutableList.of(),
            columns,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    assertThat(rowIds(results)).containsExactly("device-0", "device-1", "device-2").inOrder();

    // device-0 on lab1: every host key projects a value, lab type as a comma-joinable TextCell.
    Row lab1Row = results.getRows(0);
    Cell labTypeCell = lab1Row.getCells(1);
    assertThat(labTypeCell.getKindCase()).isEqualTo(Cell.KindCase.TEXT);
    assertThat(labTypeCell.getText().getValue()).isEqualTo("Core Lab");
    assertThat(lab1Row.getCells(2).getText().getValue()).isEqualTo("debian");
    assertThat(lab1Row.getCells(3).getText().getValue()).isEqualTo("RUNNING");

    // device-2 on lab2: no lab type and no release status, so those cells are blank; host os still
    // renders from the LabInfo host property.
    Row lab2Row = results.getRows(2);
    assertThat(lab2Row.getCells(1).getText().getValue()).isEmpty();
    assertThat(lab2Row.getCells(2).getText().getValue()).isEqualTo("macos");
    assertThat(lab2Row.getCells(3).getText().getValue()).isEmpty();
  }

  @Test
  public void sortByHostKey_ordersRowsByHostAttribute() {
    FleetSnapshot enriched =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(CoreFleetRawData.builder().setLabData(hostAttributeFleet()).build(), BUILD_TIME);
    LazyPostings enrichedPostings = new LazyPostings(enriched.devices());
    ImmutableList<String> columns = ImmutableList.of("field::uuid", "host::host_os");

    // debian (lab1) sorts before macos (lab2); the two lab1 devices tie-break by UUID ascending.
    FleetFlatResults ascending =
        searcher.searchFlat(
            new DeviceCorpus(enriched, enrichedPostings, null),
            ImmutableList.of(),
            columns,
            FleetColumnSort.newBuilder().setKey("host::host_os").setAscending(true).build(),
            FleetPageRequest.getDefaultInstance());
    assertThat(rowIds(ascending)).containsExactly("device-0", "device-1", "device-2").inOrder();

    // Descending reverses the whole ordering, tie-break included.
    FleetFlatResults descending =
        searcher.searchFlat(
            new DeviceCorpus(enriched, enrichedPostings, null),
            ImmutableList.of(),
            columns,
            FleetColumnSort.newBuilder().setKey("host::host_os").setAscending(false).build(),
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
                .addOwner("alice")
                .addOwner("carol")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("os", "ios"))))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }
}
