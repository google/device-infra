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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestion;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.inject.Guice;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Host-entity suggestion tests over a {@link HostCorpus}: the host empty state, host value and key
 * suggestions ranked by host key priority, the host group-by candidates, and the host device-count
 * alias. Device suggestion behavior is covered by {@link FleetSuggesterTest} and is not touched
 * here.
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
public final class HostSuggesterTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final HostCorpus corpus =
      new HostCorpus(snapshot, LazyPostings.forHosts(snapshot.hosts()), new AtsOneCuration());

  // FleetSuggester needs the per-fleet ScenarioCuration map; bind the OSS ats-one curation under
  // FLEET_SELF so its host key ranking drives the ordering assertions below.
  private final FleetSuggester suggester =
      new FleetSuggester(
          Guice.createInjector().getInstance(FleetFilterEngine.class),
          ImmutableMap.of(Fleet.FLEET_SELF, new AtsOneCuration()));

  @Test
  public void emptyQuery_returnsHostStarterKeysAsOpenPickers() {
    FleetSuggestionResponse response = suggester.suggest(corpus, request(""));

    // The host empty-state keys present in this fleet, in their fixed order. host::release_status
    // is
    // absent from the fleet, so it is skipped; every entry opens the value picker.
    ImmutableList.Builder<String> keys = ImmutableList.builder();
    for (FleetSuggestion item : response.getItemsList()) {
      assertThat(item.hasOpenPicker()).isTrue();
      keys.add(item.getOpenPicker().getKey());
    }
    assertThat(keys.build())
        .containsExactly(
            "host::host_name", "host::lab_type", "host::connectivity", "host::device_count")
        .inOrder();
  }

  @Test
  public void valueSuggestion_offersHostKeyConditionWithCount() {
    // "debian" is the host_os value on lab-a and lab-b. It resolves onto host::host_os, a host tier
    // 1 key, so it is offered first with the two matching hosts as its count.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("debian"));

    FleetSuggestion hostOs = firstApplyFilter(response, "host::host_os");
    assertThat(hostOs.getApplyFilter().getResultingFilter().getSimple().getValues(0).getValue())
        .isEqualTo("debian");
    assertThat(hostOs.getCount()).isEqualTo(2);
    assertThat(response.getItems(0).getApplyFilter().getResultingFilter().getKey())
        .isEqualTo("host::host_os");
  }

  @Test
  public void keyName_ranksHostTier1KeyAboveTier2Key() {
    // "host" matches host::host_name and host::host_os (host tier 1, priority 3) and host::host_ip
    // (host tier 2, priority 1). Priority is the primary sort, so the tier 1 keys are offered
    // before
    // the tier 2 key. A generous limit keeps the low-priority tier 2 key from being trimmed so the
    // relative order can be asserted.
    FleetSuggestionResponse response =
        suggester.suggest(
            corpus,
            FleetSuggestionRequest.newBuilder()
                .setInput("host")
                .setFleet(Fleet.FLEET_SELF)
                .setLimit(50)
                .build());

    int nameIndex = openPickerIndex(response, "host::host_name");
    int osIndex = openPickerIndex(response, "host::host_os");
    int ipIndex = openPickerIndex(response, "host::host_ip");
    assertThat(nameIndex).isLessThan(ipIndex);
    assertThat(osIndex).isLessThan(ipIndex);
  }

  @Test
  public void groupBy_offersHostGroupByCandidates() {
    // A bare "group by" prefix offers the curated host group-by candidates. host::lab_type yields
    // two buckets (lab-a's lab types plus the "(no value)" bucket for lab-b and lab-c); the other
    // candidate host::release_status is absent from the fleet and is dropped.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("group by"));

    FleetSuggestion labType = firstAddGroupBy(response, "host::lab_type");
    assertThat(labType.getLabel()).isEqualTo("Group by");
    assertThat(labType.getCountUnit()).isEqualTo("groups");
    assertThat(labType.getCount()).isEqualTo(2);
  }

  @Test
  public void deviceCountAlias_resolvesToHostDeviceCountKey() {
    // Typing the alias "device count" resolves onto the host device-count key, offering its picker.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("device count"));

    FleetSuggestion deviceCount = firstOpenPicker(response, "host::device_count");
    assertThat(deviceCount.getOpenPicker().getKey()).isEqualTo("host::device_count");
  }

  // --- Helpers ---

  private static FleetSuggestion firstApplyFilter(FleetSuggestionResponse response, String key) {
    for (FleetSuggestion item : response.getItemsList()) {
      if (item.hasApplyFilter()
          && item.getApplyFilter().getResultingFilter().getKey().equals(key)) {
        return item;
      }
    }
    throw new AssertionError("no apply-filter suggestion for " + key);
  }

  private static FleetSuggestion firstOpenPicker(FleetSuggestionResponse response, String key) {
    for (FleetSuggestion item : response.getItemsList()) {
      if (item.hasOpenPicker() && item.getOpenPicker().getKey().equals(key)) {
        return item;
      }
    }
    throw new AssertionError("no open-picker suggestion for " + key);
  }

  private static int openPickerIndex(FleetSuggestionResponse response, String key) {
    for (int i = 0; i < response.getItemsCount(); i++) {
      FleetSuggestion item = response.getItems(i);
      if (item.hasOpenPicker() && item.getOpenPicker().getKey().equals(key)) {
        return i;
      }
    }
    throw new AssertionError("no open-picker suggestion for " + key);
  }

  private static FleetSuggestion firstAddGroupBy(FleetSuggestionResponse response, String key) {
    for (FleetSuggestion item : response.getItemsList()) {
      if (item.hasAddGroupBy() && item.getAddGroupBy().getKey().equals(key)) {
        return item;
      }
    }
    throw new AssertionError("no add-group-by suggestion for " + key);
  }

  private static FleetSuggestionRequest request(String input) {
    return FleetSuggestionRequest.newBuilder().setInput(input).setFleet(Fleet.FLEET_SELF).build();
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
