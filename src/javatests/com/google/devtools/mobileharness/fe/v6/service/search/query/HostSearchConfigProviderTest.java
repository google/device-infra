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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.KeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.inject.Guice;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Host-entity tests for {@link FleetSearchConfigProvider}: when the request is a host search the
 * config draws its recommended and default columns from the {@link ScenarioCuration} host lists and
 * its column names from the host index, and locks the host name as the identifier column. The
 * device path is unchanged and covered by {@link FleetSearchConfigProviderTest}.
 */
@RunWith(JUnit4.class)
public final class HostSearchConfigProviderTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final FleetSearchConfigProvider provider = new FleetSearchConfigProvider();
  private final AtsOneCuration curation = new AtsOneCuration();

  @Test
  public void host_recommendedColumnsAreAtsOneHostList() {
    FleetSearchConfig config = provider.getConfig(snapshot, hostRequest(), curation);

    List<String> keys = new ArrayList<>();
    for (KeyDescriptor descriptor : config.getColumns().getRecommendedList()) {
      keys.add(descriptor.getKey());
    }
    assertThat(keys)
        .containsExactly(
            "host::connectivity",
            "host::device_count",
            "host::host_os",
            "host::lab_server_version",
            "host::host_ip")
        .inOrder();
  }

  @Test
  public void host_defaultColumnsAreAtsOneHostListWithHostNameLocked() {
    FleetSearchConfig config = provider.getConfig(snapshot, hostRequest(), curation);

    List<FleetColumnDescriptor> defaults = config.getColumns().getDefaultsList();
    List<String> keys = new ArrayList<>();
    for (FleetColumnDescriptor descriptor : defaults) {
      keys.add(descriptor.getKey());
    }
    assertThat(keys)
        .containsExactly(
            "host::host_name",
            "host::connectivity",
            "host::device_count",
            "host::host_ip",
            "host::lab_server_version")
        .inOrder();

    // The host name is the host identifier column, so it is locked; every other column is
    // removable.
    assertThat(defaults.get(0).getKey()).isEqualTo("host::host_name");
    assertThat(defaults.get(0).getLocked()).isTrue();
    assertThat(defaults.get(1).getLocked()).isFalse();
    assertThat(defaults.get(2).getLocked()).isFalse();

    // Names present in the host index render their built-in display names.
    assertThat(defaults.get(0).getDisplayName()).isEqualTo("Host Name");
    assertThat(defaults.get(2).getDisplayName()).isEqualTo("Device Count");
  }

  @Test
  public void host_landingCountIsHostCount() {
    FleetSearchConfig config = provider.getConfig(snapshot, hostRequest(), curation);

    assertThat(config.getLanding().getBrowseAllCount()).isEqualTo(snapshot.hosts().size());
    assertThat(config.getLanding().getBrowseAllCount()).isEqualTo(2);
  }

  @Test
  public void device_stillUsesDeviceColumns() {
    FleetSearchConfigRequest deviceRequest =
        FleetSearchConfigRequest.newBuilder().setEntity(SearchEntity.SEARCH_ENTITY_DEVICE).build();

    FleetSearchConfig config = provider.getConfig(snapshot, deviceRequest, curation);

    List<String> keys = new ArrayList<>();
    for (FleetColumnDescriptor descriptor : config.getColumns().getDefaultsList()) {
      keys.add(descriptor.getKey());
    }
    // The device default columns are the device curation list, not the host list.
    assertThat(keys).containsExactlyElementsIn(curation.deviceDefaultColumns()).inOrder();
    assertThat(config.getLanding().getBrowseAllCount()).isEqualTo(snapshot.deviceCount());
  }

  private static FleetSearchConfigRequest hostRequest() {
    return FleetSearchConfigRequest.newBuilder().setEntity(SearchEntity.SEARCH_ENTITY_HOST).build();
  }

  // --- Synthetic fleet: two running hosts, so the host count is two. ---

  private static LabQueryResult fleet() {
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(2)
                .addLabData(hostLab("lab-a", "1.1.1.1", "debian", 2))
                .addLabData(hostLab("lab-b", "2.2.2.2", "debian", 1)))
        .build();
  }

  private static LabData hostLab(String hostName, String ip, String hostOs, int deviceCount) {
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
}
