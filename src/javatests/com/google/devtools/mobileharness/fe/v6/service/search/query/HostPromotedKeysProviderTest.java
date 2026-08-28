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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedFilterKey;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedGroupByKey;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedKeysRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedKeysResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDisplay;
import com.google.inject.Guice;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Host-entity promoted-keys tests over a {@link HostCorpus}: the provider must read the host
 * candidate rows ({@link ScenarioCuration#hostFilterByRow} and {@link
 * ScenarioCuration#hostGroupByRow}) rather than the device rows, and apply the same dead-end,
 * applied, and count trimming. Device promoted-keys behavior is covered by {@link
 * FleetPromotedKeysProviderTest} and is not touched here.
 *
 * <p>Synthetic fleet, one host per lab:
 *
 * <ul>
 *   <li>lab-a: running, host_os debian, lab_type slaas, two devices.
 *   <li>lab-b: missing, host_os debian, one device.
 *   <li>lab-c: running, no host_os property, no devices.
 * </ul>
 */
@RunWith(JUnit4.class)
public final class HostPromotedKeysProviderTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final HostCorpus corpus =
      new HostCorpus(snapshot, LazyPostings.forHosts(snapshot.hosts()), new FakeCuration());
  private final FleetPromotedKeysProvider provider =
      new FleetPromotedKeysProvider(Guice.createInjector().getInstance(FleetFilterEngine.class));

  @Test
  public void filterKeys_noFilters_followsHostCuratedOrder() {
    FleetPromotedKeysResponse response = provider.getPromotedKeys(corpus, request());

    // Every curated host filter key is present in this fleet, so the whole anchor row shows in
    // order.
    assertThat(filterKeys(response))
        .containsExactly(
            "host_field::host_name",
            "host_field::connectivity",
            "host_field::device_count",
            "host_property::host_os")
        .inOrder();
    // The host device-count key is named through the host index, not the device one.
    assertThat(filterKey(response, "host_field::device_count").getMetadata().getKeyDisplayName())
        .isEqualTo("Device Count");
  }

  @Test
  public void groupByKeys_followsHostCuratedOrderWithCounts() {
    FleetPromotedKeysResponse response = provider.getPromotedKeys(corpus, request());

    assertThat(groupByKeys(response))
        .containsExactly("host_property::host_os", "host_field::lab_type")
        .inOrder();
    // host_os has two values (debian, Unknown) and no missing hosts: two groups.
    assertThat(groupByKey(response, "host_property::host_os").getGroupCount()).isEqualTo(2);
    // lab_type is present on lab-a only, so lab-b and lab-c form a "(no value)" bucket: two groups.
    assertThat(groupByKey(response, "host_field::lab_type").getGroupCount()).isEqualTo(2);
  }

  @Test
  public void filterKeys_excludeAppliedKeyAndDeadEnds() {
    // Filtering connectivity=Running leaves lab-a and lab-c. Within that set:
    //   connectivity: applied, so excluded from the promoted row.
    //   host_name:    lab-a, lab-c        -> kept.
    //   device_count: 2, 0                -> kept.
    //   host_os:      debian, Unknown     -> kept.
    FleetPromotedKeysResponse response =
        provider.getPromotedKeys(corpus, request(simple("host_field::connectivity", "Running")));

    assertThat(filterKeys(response))
        .containsExactly(
            "host_field::host_name", "host_field::device_count", "host_property::host_os")
        .inOrder();
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
        FleetPromotedKeysRequest.newBuilder()
            .setFleet(Fleet.FLEET_SELF)
            .setEntity(SearchEntity.SEARCH_ENTITY_HOST);
    for (Filter filter : filters) {
      builder.addFilters(filter);
    }
    return builder.build();
  }

  private static Filter simple(String key, String value) {
    return Filter.newBuilder()
        .setKey(key)
        .setSimple(SimpleMatch.newBuilder().addValues(FilterValue.newBuilder().setValue(value)))
        .build();
  }

  /**
   * A fake curation returning host candidate rows so the trimming assertions hold over the
   * synthetic fleet. The device rows are left empty because the host corpus never reads them.
   */
  private static final class FakeCuration implements ScenarioCuration {
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
      return ImmutableList.of(
          HostKeys.HOST_NAME, HostKeys.CONNECTIVITY, HostKeys.DEVICE_COUNT, HostKeys.HOST_OS);
    }

    @Override
    public ImmutableList<HostKeyDescriptor> hostGroupByRow() {
      return ImmutableList.of(
          HostKeys.HOST_OS,
          HostKeyDescriptor.builder()
              .setId(HostKeys.PREFIX_HOST_FIELD + "lab_type")
              .setDisplay(KeyDisplay.of("Lab Type"))
              .build());
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
    public int keyPriority(String keyId) {
      return 0;
    }

    @Override
    public boolean landingEnabled() {
      return true;
    }
  }

  // --- Synthetic fleet (mirrors HostSearchTest) ---

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
                        hostProperties("host_os", "debian", "lab_type", "slaas"),
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
