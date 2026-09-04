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
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnSort;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetCountedValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFlatResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroup;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupSort;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupedResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPageRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetValueListResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Indicator;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Row;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.inject.Guice;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Host-entity search tests over a {@link HostCorpus}: flat search, group-by, value list, and column
 * catalog. The host index is built by the real {@link FleetIndexBuilder}. Device search behavior is
 * covered by its own tests and is not touched here.
 *
 * <p>Synthetic fleet, one host per lab:
 *
 * <ul>
 *   <li>lab-a: running, host_os debian, lab_type slaas (two ui lab types), two devices.
 *   <li>lab-b: missing, host_os debian, one device.
 *   <li>lab-c: running, no host_os property (defaults to Unknown), no devices.
 * </ul>
 */
@RunWith(JUnit4.class)
public final class HostSearchTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  private static final ImmutableList<String> COLUMNS =
      ImmutableList.of(
          "host_field::host_name",
          "host_field::connectivity",
          "host_field::device_count",
          "host_field::lab_server_version",
          "host_property::host_os");

  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final HostCorpus corpus =
      new HostCorpus(snapshot, LazyPostings.forHosts(snapshot.hosts()), null);

  private final FleetFlatSearcher flatSearcher =
      Guice.createInjector().getInstance(FleetFlatSearcher.class);
  private final FleetGroupSearcher groupSearcher =
      Guice.createInjector().getInstance(FleetGroupSearcher.class);
  private final FleetValueLister valueLister =
      Guice.createInjector().getInstance(FleetValueLister.class);
  private final FleetColumnCataloger columnCataloger =
      Guice.createInjector().getInstance(FleetColumnCataloger.class);

  // --- Flat search ---

  @Test
  public void flat_returnsHostRowsSortedByHostNameAscending() {
    FleetFlatResults results =
        flatSearcher.searchFlat(
            corpus,
            ImmutableList.of(),
            COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    assertThat(rowIds(results)).containsExactly("lab-a", "lab-b", "lab-c").inOrder();
    assertThat(results.getTotal()).isEqualTo(3);
    assertThat(columnKeys(results)).containsExactlyElementsIn(COLUMNS).inOrder();
    assertThat(results.getColumns(0).getDisplayName()).isEqualTo("Host Name");
    assertThat(results.getColumns(2).getDisplayName()).isEqualTo("Device Count");
  }

  @Test
  public void flat_typesHostCellsByKey() {
    FleetFlatResults results =
        flatSearcher.searchFlat(
            corpus,
            ImmutableList.of(simple("host_field::host_name", "lab-a")),
            COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    Row row = results.getRows(0);

    // host_name is a link into a HostRef carrying the host name and ip.
    assertThat(row.getCells(0).hasLink()).isTrue();
    assertThat(row.getCells(0).getLink().getText()).isEqualTo("lab-a");
    assertThat(row.getCells(0).getLink().getTarget().getHost().getHostName()).isEqualTo("lab-a");
    assertThat(row.getCells(0).getLink().getTarget().getHost().getHostIp()).isEqualTo("1.1.1.1");

    // connectivity is a StatusCell with a green (OK) indicator.
    assertThat(row.getCells(1).hasStatus()).isTrue();
    assertThat(row.getCells(1).getStatus().getText()).isEqualTo("Running");
    assertThat(row.getCells(1).getStatus().getIndicator()).isEqualTo(Indicator.INDICATOR_OK);

    // device_count is a TextCell.
    assertThat(row.getCells(2).hasText()).isTrue();
    assertThat(row.getCells(2).getText().getValue()).isEqualTo("2");

    // lab_server_version is a TextCell from the host version property.
    assertThat(row.getCells(3).hasText()).isTrue();
    assertThat(row.getCells(3).getText().getValue()).isEqualTo("1.0.0");

    // host_os is a TextCell from the host property.
    assertThat(row.getCells(4).hasText()).isTrue();
    assertThat(row.getCells(4).getText().getValue()).isEqualTo("debian");
  }

  @Test
  public void flat_missingConnectivityMapsToErrorIndicator() {
    FleetFlatResults results =
        flatSearcher.searchFlat(
            corpus,
            ImmutableList.of(simple("host_field::host_name", "lab-b")),
            COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    Cell connectivityCell = results.getRows(0).getCells(1);
    assertThat(connectivityCell.getStatus().getText()).isEqualTo("Missing");
    assertThat(connectivityCell.getStatus().getIndicator()).isEqualTo(Indicator.INDICATOR_ERROR);
  }

  @Test
  public void flat_zeroDeviceHostRendersUnknownOsAndCountZero() {
    FleetFlatResults results =
        flatSearcher.searchFlat(
            corpus,
            ImmutableList.of(simple("host_field::host_name", "lab-c")),
            COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    Row row = results.getRows(0);
    // count is 0
    assertThat(row.getCells(2).getText().getValue()).isEqualTo("0");
    // lab type is empty
    assertThat(row.getCells(3).getText().getValue()).isEmpty();
    // host os defaults to Unknown
    assertThat(row.getCells(4).getText().getValue()).isEqualTo("Unknown");
  }

  @Test
  public void flat_filtersByHostKey() {
    FleetFlatResults results =
        flatSearcher.searchFlat(
            corpus,
            ImmutableList.of(simple("host_field::host_name", "lab-b")),
            COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    assertThat(rowIds(results)).containsExactly("lab-b");
  }

  @Test
  public void flat_filtersByLabServerVersion() {
    FleetFlatResults results =
        flatSearcher.searchFlat(
            corpus,
            ImmutableList.of(simple("host_field::lab_server_version", "1.0.0")),
            COLUMNS,
            FleetColumnSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    assertThat(rowIds(results)).containsExactly("lab-a");
  }

  @Test
  public void flat_sortsByHostNameDescending() {
    FleetFlatResults results =
        flatSearcher.searchFlat(
            corpus,
            ImmutableList.of(),
            COLUMNS,
            FleetColumnSort.newBuilder()
                .setKey("host_field::host_name")
                .setAscending(false)
                .build(),
            FleetPageRequest.getDefaultInstance());

    assertThat(rowIds(results)).containsExactly("lab-c", "lab-b", "lab-a").inOrder();
  }

  // --- Group by ---

  @Test
  public void group_bucketsByHostKeyWithNoUtilization() {
    FleetGroupedResults results =
        groupSearcher.searchGrouped(
            corpus,
            ImmutableList.of(),
            ImmutableList.of("host_property::host_os"),
            FleetGroupSort.getDefaultInstance(),
            FleetPageRequest.getDefaultInstance());

    assertThat(results.getTotalGroups()).isEqualTo(2);
    assertThat(results.getTotalItems()).isEqualTo(3);
    // Groups sort by item count descending: debian (two hosts) before Unknown (one host).
    FleetGroup debian = results.getGroups(0);
    assertThat(debian.getValuesList()).containsExactly("debian");
    assertThat(debian.getItemCount()).isEqualTo(2);
    FleetGroup unknown = results.getGroups(1);
    assertThat(unknown.getValuesList()).containsExactly("Unknown");
    assertThat(unknown.getItemCount()).isEqualTo(1);
    // Utilization is a device concept, so host groups omit it.
    for (FleetGroup group : results.getGroupsList()) {
      assertThat(group.hasUtilization()).isFalse();
    }
  }

  // --- Value list ---

  @Test
  public void valueList_hostNameIsPlainWithoutCounts() {
    FleetValueListResponse response =
        valueLister.listValues(corpus, "host_field::host_name", ImmutableList.of());

    assertThat(response.hasPlain()).isTrue();
    ImmutableList.Builder<String> values = ImmutableList.builder();
    response.getPlain().getValuesList().forEach(value -> values.add(value.getValue()));
    assertThat(values.build()).containsExactly("lab-a", "lab-b", "lab-c").inOrder();
  }

  @Test
  public void valueList_deviceCountIsCounted() {
    FleetValueListResponse response =
        valueLister.listValues(corpus, "host_field::device_count", ImmutableList.of());

    assertThat(response.hasCounted()).isTrue();
    assertThat(totalFor(response, "0")).isEqualTo(1);
    assertThat(totalFor(response, "1")).isEqualTo(1);
    assertThat(totalFor(response, "2")).isEqualTo(1);
  }

  @Test
  public void valueList_connectivityIsCounted() {
    FleetValueListResponse response =
        valueLister.listValues(corpus, "host_field::connectivity", ImmutableList.of());

    assertThat(response.hasCounted()).isTrue();
    int running = totalFor(response, "Running");
    int missing = totalFor(response, "Missing");
    assertThat(running).isEqualTo(2);
    assertThat(missing).isEqualTo(1);
  }

  // --- Column catalog ---

  @Test
  public void columnCatalog_listsHostBuiltinKeys() {
    FleetColumnCatalogResponse response =
        columnCataloger.getColumnCatalog(corpus, FleetColumnCatalogRequest.getDefaultInstance());

    FleetColumnCatalogSection builtin = sectionByHeading(response, "Built-in fields");
    ImmutableList.Builder<String> keys = ImmutableList.builder();
    for (FleetColumnCatalogEntry entry : builtin.getEntriesList()) {
      keys.add(entry.getKey());
    }
    assertThat(keys.build())
        .containsAtLeast(
            "host_field::host_name",
            "host_field::host_ip",
            "host_field::connectivity",
            "host_field::device_count",
            "host_field::lab_server_version");
    // The device count column is offered, named, and carries the per-key host count.
    FleetColumnCatalogEntry deviceCount = entryByKey(builtin, "host_field::device_count");
    assertThat(deviceCount.getDisplayName()).isEqualTo("Device Count");
    assertThat(deviceCount.getDeviceCount()).isEqualTo(3);
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

  private static int totalFor(FleetValueListResponse response, String value) {
    for (FleetCountedValue counted : response.getCounted().getValuesList()) {
      if (counted.getValue().equals(value)) {
        return counted.getTotal();
      }
    }
    return -1;
  }

  private static FleetColumnCatalogSection sectionByHeading(
      FleetColumnCatalogResponse response, String heading) {
    for (FleetColumnCatalogSection section : response.getSectionsList()) {
      if (section.getHeading().equals(heading)) {
        return section;
      }
    }
    throw new AssertionError("No section with heading: " + heading);
  }

  private static FleetColumnCatalogEntry entryByKey(FleetColumnCatalogSection section, String key) {
    for (FleetColumnCatalogEntry entry : section.getEntriesList()) {
      if (entry.getKey().equals(key)) {
        return entry;
      }
    }
    throw new AssertionError("No entry with key: " + key);
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
                .setLabTotalCount(3)
                .addLabData(
                    hostLab(
                        "lab-a",
                        "1.1.1.1",
                        LabStatus.LAB_RUNNING,
                        hostProperties("host_os", "debian", "host_version", "1.0.0"),
                        2))
                .addLabData(
                    hostLab(
                        "lab-b",
                        "2.2.2.2",
                        LabStatus.LAB_MISSING,
                        hostProperties("host_os", "debian"),
                        1))
                .addLabData(
                    hostLab(
                        "lab-c",
                        "3.3.3.3",
                        LabStatus.LAB_RUNNING,
                        HostProperties.getDefaultInstance(),
                        0)))
        .build();
  }

  private static LabData hostLab(
      String hostName, String ip, LabStatus status, HostProperties properties, int deviceCount) {
    DeviceList.Builder deviceList = DeviceList.newBuilder().setDeviceTotalCount(deviceCount);
    for (int i = 0; i < deviceCount; i++) {
      deviceList.addDeviceInfo(
          DeviceInfo.newBuilder()
              .setDeviceLocator(DeviceLocator.newBuilder().setId(hostName + "-device-" + i)));
    }
    return LabData.newBuilder()
        .setLabInfo(
            LabInfo.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName(hostName).setIp(ip))
                .setLabStatus(status)
                .setLabServerFeature(LabServerFeature.newBuilder().setHostProperties(properties)))
        .setDeviceList(deviceList)
        .build();
  }

  private static HostProperties hostProperties(String... keyValues) {
    HostProperties.Builder properties = HostProperties.newBuilder();
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      properties.addHostProperty(
          HostProperty.newBuilder().setKey(keyValues[i]).setValue(keyValues[i + 1]));
    }
    return properties.build();
  }
}
