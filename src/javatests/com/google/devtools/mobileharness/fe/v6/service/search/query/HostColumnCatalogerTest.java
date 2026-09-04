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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogEntry;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnCatalogSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.HostEnrichment;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDisplay;
import com.google.inject.Guice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link HostColumnCataloger}. */
@RunWith(JUnit4.class)
public final class HostColumnCatalogerTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  private HostColumnCataloger cataloger;
  private FleetSnapshot snapshot;
  private HostCorpus corpus;

  @Before
  public void setUp() {
    cataloger = new HostColumnCataloger();

    // Build synthetic fleet across two hosts with 1P enrichments:
    //   lab-a: RUNNING, SHARED_LAB, daemon RUNNING, daemon version 1.2.3, prop location=mtv, prop
    // rack=r1
    //   lab-b: RUNNING, prop location=sjc, prop rack=r2, prop env=test
    LabQueryResult labData =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .addLabData(
                        lab(
                            "lab-a",
                            "10.0.0.1",
                            LabStatus.LAB_RUNNING,
                            props(
                                "location", "mtv",
                                "rack", "r1",
                                "host_os", "linux"),
                            device("device-0", DeviceStatus.IDLE)))
                    .addLabData(
                        lab(
                            "lab-b",
                            "10.0.0.2",
                            LabStatus.LAB_RUNNING,
                            props(
                                "location", "sjc",
                                "rack", "r2",
                                "env", "test",
                                "host_os", "linux"),
                            device("device-1", DeviceStatus.IDLE))))
            .build();

    ImmutableMap<String, HostEnrichment> enrichments =
        ImmutableMap.of(
            "lab-a",
            HostEnrichment.builder()
                .setReleaseStatus(Optional.of("RUNNING"))
                .setReleaseType(Optional.of("SHARED_LAB"))
                .setDaemonStatus(Optional.of("RUNNING"))
                .setDaemonServerVersion(Optional.of("1.2.3"))
                .setLabServerVersion(Optional.of("v42"))
                .build());

    CoreFleetRawData rawData =
        CoreFleetRawData.builder().setLabData(labData).setHostEnrichments(enrichments).build();

    snapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(rawData, BUILD_TIME);

    HostKeyRegistry testRegistry =
        new HostKeyRegistry(
            ImmutableList.of(
                HostKeyDescriptor.builder()
                    .setId("host_field::lab_type")
                    .setDisplay(KeyDisplay.of("Lab Type"))
                    .build(),
                HostKeyDescriptor.builder()
                    .setId("host_field::daemon_status")
                    .setDisplay(KeyDisplay.of("Daemon Server Status"))
                    .build(),
                HostKeyDescriptor.builder()
                    .setId("host_field::daemon_server_version")
                    .setDisplay(KeyDisplay.of("Daemon Server Version"))
                    .build(),
                HostKeyDescriptor.builder()
                    .setId("host_field::release_status")
                    .setDisplay(KeyDisplay.of("Release Status"))
                    .build(),
                HostKeyDescriptor.builder()
                    .setId("host_field::release_type")
                    .setDisplay(KeyDisplay.of("Release Type"))
                    .build())) {};

    ScenarioCuration testCuration =
        new ScenarioCuration() {
          @Override
          public DeviceKeyRegistry deviceKeyRegistry() {
            return new AtsDeviceKeyRegistry();
          }

          @Override
          public HostKeyRegistry hostKeyRegistry() {
            return testRegistry;
          }

          @Override
          public ImmutableList<DeviceKeyDescriptor> deviceFilterByRow() {
            return ImmutableList.of();
          }

          @Override
          public ImmutableList<DeviceKeyDescriptor> deviceGroupByRow() {
            return ImmutableList.of();
          }

          @Override
          public ImmutableList<DeviceKeyDescriptor> deviceDefaultColumns() {
            return ImmutableList.of();
          }

          @Override
          public ImmutableList<DeviceKeyDescriptor> deviceRecommendedColumns() {
            return ImmutableList.of();
          }

          @Override
          public ImmutableList<HostKeyDescriptor> hostFilterByRow() {
            return ImmutableList.of();
          }

          @Override
          public ImmutableList<HostKeyDescriptor> hostGroupByRow() {
            return ImmutableList.of();
          }

          @Override
          public ImmutableList<HostKeyDescriptor> hostDefaultColumns() {
            return ImmutableList.of();
          }

          @Override
          public ImmutableList<HostKeyDescriptor> hostRecommendedColumns() {
            return ImmutableList.of();
          }

          @Override
          public KeyPriority keyPriority() {
            return FleetKeyPriority.INSTANCE;
          }

          @Override
          public boolean landingEnabled() {
            return false;
          }
        };

    corpus = new HostCorpus(snapshot, LazyPostings.forHosts(snapshot.hosts()), testCuration);
  }

  @Test
  public void browse_host_returnsBuiltinAndPropertiesSectionsWithoutDimensions() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(
            corpus,
            FleetColumnCatalogRequest.newBuilder()
                .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
                .build());

    List<String> headings = headings(response);
    assertThat(headings).containsExactly("Built-in fields", "Host properties").inOrder();
    assertThat(headings).doesNotContain("Dimensions");
  }

  @Test
  public void builtinSection_host_usesCleanDisplayNamesWithoutHostPrefixes() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(
            corpus,
            FleetColumnCatalogRequest.newBuilder()
                .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
                .build());

    FleetColumnCatalogSection builtin = section(response, "Built-in fields");

    // Clean host display names without redundant "Host " prefix
    assertThat(entryFor(builtin, "host_field::connectivity").getDisplayName())
        .isEqualTo("Lab Server Connectivity");
    assertThat(entryFor(builtin, "host_field::lab_type").getDisplayName()).isEqualTo("Lab Type");
    assertThat(entryFor(builtin, "host_field::daemon_status").getDisplayName())
        .isEqualTo("Daemon Server Status");
    assertThat(entryFor(builtin, "host_field::daemon_server_version").getDisplayName())
        .isEqualTo("Daemon Server Version");
    assertThat(entryFor(builtin, "host_field::release_status").getDisplayName())
        .isEqualTo("Release Status");
    assertThat(entryFor(builtin, "host_field::release_type").getDisplayName())
        .isEqualTo("Release Type");
    assertThat(entryFor(builtin, "host_field::lab_server_version").getDisplayName())
        .isEqualTo("Lab Server Version");
    assertThat(entryFor(builtin, "host_field::device_count").getDisplayName())
        .isEqualTo("Device Count");

    // Full section reports no totalAvailable
    assertThat(builtin.getTotalAvailable()).isEqualTo(0);
  }

  @Test
  public void hostProperties_rankedByHostCoverageWithTotalReported() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(
            corpus,
            FleetColumnCatalogRequest.newBuilder()
                .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
                .build());

    FleetColumnCatalogSection properties = section(response, "Host properties");
    // host_os, location, and rack are present on 2 hosts; env is present on 1 host.
    assertThat(keys(properties))
        .containsExactly(
            "host_property::host_os",
            "host_property::location",
            "host_property::rack",
            "host_property::env")
        .inOrder();

    // Verify Host Property prefix on title
    assertThat(entryFor(properties, "host_property::location").getDisplayName())
        .isEqualTo("Host Property location");
    assertThat(entryFor(properties, "host_property::location").getDeviceCount()).isEqualTo(2);
    assertThat(entryFor(properties, "host_property::env").getDeviceCount()).isEqualTo(1);

    assertThat(properties.getTotalAvailable()).isEqualTo(4);
  }

  @Test
  public void suggested_host_prioritizesActiveFiltersThenRecentKeys() {
    FleetColumnCatalogRequest request =
        FleetColumnCatalogRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
            .addFilters(Filter.newBuilder().setKey("host_property::location"))
            .addRecentKeys("host_field::daemon_status")
            .build();

    FleetColumnCatalogResponse response = cataloger.getColumnCatalog(corpus, request);

    assertThat(headings(response).get(0)).isEqualTo("Suggested for you");
    FleetColumnCatalogSection suggested = section(response, "Suggested for you");

    assertThat(keys(suggested))
        .containsExactly("host_property::location", "host_field::daemon_status")
        .inOrder();
    assertThat(entryFor(suggested, "host_property::location").getReason())
        .isEqualTo("in your active filters");
    assertThat(entryFor(suggested, "host_field::daemon_status").getReason())
        .isEqualTo("recently used");
    assertThat(suggested.getTotalAvailable()).isEqualTo(0);
  }

  @Test
  public void search_host_matchesHostKeysAndNeverReturnsDeviceDimensions() {
    FleetColumnCatalogRequest request =
        FleetColumnCatalogRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
            .setQuery("daemon")
            .build();

    FleetColumnCatalogResponse response = cataloger.getColumnCatalog(corpus, request);

    // Under Scheme B (Category Filter Tree Mode), categories are filtered in place.
    // Built-in fields matches daemon keys; Search results and Suggested are omitted.
    FleetColumnCatalogSection builtin = section(response, "Built-in fields");
    assertThat(keys(builtin))
        .containsExactly("host_field::daemon_server_version", "host_field::daemon_status");
    assertThat(headings(response)).doesNotContain("Search results");
    assertThat(headings(response)).doesNotContain("Suggested for you");

    // Search query for device dimension returns zero hits across all categories
    FleetColumnCatalogRequest dimSearch =
        FleetColumnCatalogRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
            .setQuery("pixel")
            .build();
    FleetColumnCatalogResponse dimResponse = cataloger.getColumnCatalog(corpus, dimSearch);
    assertThat(dimResponse.getSectionsList()).isEmpty();
  }

  @Test
  public void search_host_emptyQuery_omitsSearchSection() {
    FleetColumnCatalogResponse response =
        cataloger.getColumnCatalog(
            corpus,
            FleetColumnCatalogRequest.newBuilder()
                .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
                .build());

    assertThat(headings(response)).doesNotContain("Search results");
  }

  private static List<String> headings(FleetColumnCatalogResponse response) {
    return response.getSectionsList().stream().map(FleetColumnCatalogSection::getHeading).toList();
  }

  private static FleetColumnCatalogSection section(
      FleetColumnCatalogResponse response, String heading) {
    return response.getSectionsList().stream()
        .filter(s -> s.getHeading().equals(heading))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Section not found: " + heading));
  }

  private static List<String> keys(FleetColumnCatalogSection section) {
    return section.getEntriesList().stream().map(FleetColumnCatalogEntry::getKey).toList();
  }

  private static FleetColumnCatalogEntry entryFor(FleetColumnCatalogSection section, String keyId) {
    return section.getEntriesList().stream()
        .filter(e -> e.getKey().equals(keyId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Key not found in section: " + keyId));
  }

  private static LabData lab(
      String hostName, String ip, LabStatus status, HostProperties props, DeviceInfo... devices) {
    DeviceList.Builder deviceList = DeviceList.newBuilder();
    for (DeviceInfo d : devices) {
      deviceList.addDeviceInfo(d);
    }
    return LabData.newBuilder()
        .setLabInfo(
            LabInfo.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName(hostName).setIp(ip))
                .setLabStatus(status)
                .setLabServerFeature(LabServerFeature.newBuilder().setHostProperties(props)))
        .setDeviceList(deviceList)
        .build();
  }

  private static DeviceInfo device(String id, DeviceStatus status) {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId(id))
        .setDeviceStatus(status)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(
                            DeviceDimension.newBuilder().setName("model").setValue("pixel"))))
        .build();
  }

  private static HostProperties props(String... kv) {
    HostProperties.Builder b = HostProperties.newBuilder();
    for (int i = 0; i < kv.length; i += 2) {
      b.addHostProperty(HostProperty.newBuilder().setKey(kv[i]).setValue(kv[i + 1]));
    }
    return b.build();
  }
}
