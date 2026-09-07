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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TextSegment;
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
      new HostCorpus(snapshot, LazyPostings.forHosts(snapshot.hosts()), new AtsCuration());

  // FleetSuggester needs the per-fleet ScenarioCuration map; bind the OSS ats curation under
  // FLEET_SELF so its host key ranking drives the ordering assertions below.
  private final FleetSuggester suggester =
      new FleetSuggester(
          Guice.createInjector().getInstance(FleetFilterEngine.class),
          ImmutableMap.of(Fleet.FLEET_SELF, new AtsCuration()));

  @Test
  public void emptyQuery_returnsNoSuggestions() {
    FleetSuggestionResponse response = suggester.suggest(corpus, request(""));
    assertThat(response.getItemsList()).isEmpty();
  }

  @Test
  public void valueSuggestion_offersHostKeyConditionWithCount() {
    // "debian" is the host_os value on lab-a and lab-b. It resolves onto host_property::host_os, a
    // host tier
    // 1 key, so it is offered first with the two matching hosts as its count.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("debian"));

    FleetSuggestion hostOs = firstApplyFilter(response, "host_property::host_os");
    assertThat(hostOs.getApplyFilter().getResultingFilter().getSimple().getValues(0).getValue())
        .isEqualTo("debian");
    assertThat(hostOs.getCount()).isEqualTo(2);
    assertThat(response.getItems(0).getApplyFilter().getResultingFilter().getKey())
        .isEqualTo("host_property::host_os");
  }

  @Test
  public void keyName_ranksHostTier1KeyAboveTier2Key() {
    FleetSuggestionResponse response =
        suggester.suggest(
            corpus,
            FleetSuggestionRequest.newBuilder()
                .setInput("host")
                .setFleet(Fleet.FLEET_SELF)
                .setLimit(50)
                .build());

    int nameIndex = openPickerIndex(response, "host_field::host_name");
    int osIndex = openPickerIndex(response, "host_property::host_os");
    int ipIndex = openPickerIndex(response, "host_field::host_ip");
    assertThat(nameIndex).isLessThan(ipIndex);
    assertThat(osIndex).isLessThan(ipIndex);
  }

  @Test
  public void groupBy_offersHostGroupByCandidates() {
    FleetSuggestionResponse response = suggester.suggest(corpus, request("group by"));

    FleetSuggestion connectivity = firstAddGroupBy(response, "host_field::connectivity");
    assertThat(connectivity.getLabel()).isEqualTo("Group by");
    assertThat(connectivity.getCountUnit()).isEqualTo("groups");
    assertThat(connectivity.getCount()).isEqualTo(2);
    assertThat(connectivity.getMainTextList())
        .containsExactly(
            TextSegment.newBuilder()
                .setText("Lab Server Connectivity")
                .setEmphasized(true)
                .build());
  }

  @Test
  public void deviceCountAlias_resolvesToHostDeviceCountKey() {
    // Typing the alias "device count" resolves onto the host device-count key, offering its picker.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("device count"));

    FleetSuggestion deviceCount = firstOpenPicker(response, "host_field::device_count");
    assertThat(deviceCount.getOpenPicker().getKey()).isEqualTo("host_field::device_count");
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
                        hostProperties("host_os", "debian"),
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
