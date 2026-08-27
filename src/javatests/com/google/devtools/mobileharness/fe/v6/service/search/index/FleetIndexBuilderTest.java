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

package com.google.devtools.mobileharness.fe.v6.service.search.index;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetIndexBuilder}. */
@RunWith(JUnit4.class)
public final class FleetIndexBuilderTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  private final FleetIndexBuilder builder = new FleetIndexBuilder();

  @Test
  public void build_indexesDevicesHostsAndValues() {
    // Host lab1: two Android devices.
    //   device-0: IDLE, owner alice, dim os=android, dim model=pixel (supported + required).
    //   device-1: BUSY, owner bob, dim os=android.
    // Host lab2: one iOS device.
    //   device-2: IDLE, owner alice, dim os=ios, quarantined.
    LabQueryResult labResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(2)
                    .addLabData(
                        labData(
                            "lab1",
                            "1.1.1.1",
                            LabStatus.LAB_RUNNING,
                            HostProperties.newBuilder()
                                .addHostProperty(prop("location", "mtv"))
                                .addHostProperty(prop("host_version", "v42"))
                                .addHostProperty(prop("host_os", "ubuntu"))
                                .build(),
                            device0(),
                            device1()))
                    .addLabData(
                        labData(
                            "lab2",
                            "2.2.2.2",
                            LabStatus.LAB_MISSING,
                            HostProperties.getDefaultInstance(),
                            device2())))
            .build();

    // device-0 carries a WiFi SSID; device-1 and device-2 carry none. Host lab1 carries enrichment
    // (lab types plus release and daemon attributes); host lab2 carries none.
    CoreFleetRawData raw =
        CoreFleetRawData.builder()
            .setLabData(labResult)
            .setDeviceEnrichments(ImmutableMap.of("device-0", deviceEnrichment("GoogleGuest")))
            .setHostEnrichments(ImmutableMap.of("lab1", lab1Enrichment()))
            .build();

    FleetSnapshot snapshot = builder.build(raw, BUILD_TIME);
    FleetIndex index = snapshot.index();

    // Forward store.
    assertThat(snapshot.buildTime()).isEqualTo(BUILD_TIME);
    assertThat(snapshot.deviceCount()).isEqualTo(3);
    assertThat(snapshot.hostCount()).isEqualTo(2);
    assertThat(snapshot.devices().get(0).deviceId()).isEqualTo("device-0");
    assertThat(snapshot.devices().get(2).values("device_field::quarantined"))
        .containsExactly("Yes");
    assertThat(snapshot.devices().get(0).values("device_field::quarantined")).containsExactly("No");

    // Host records: device count and lab-server version derived from host_version.
    assertThat(snapshot.hosts().get(0).hostName()).isEqualTo("lab1");
    assertThat(snapshot.hosts().get(0).deviceCount()).isEqualTo(2);
    assertThat(snapshot.hosts().get(0).values("host_field::lab_server_version"))
        .containsExactly("v42");
    assertThat(snapshot.hosts().get(1).values("host_field::lab_server_version")).isEmpty();

    // Value counts are distinct-device counts, keyed by normalized (lowercased) value.
    assertThat(index.valueCount("device_field::status", "idle")).isEqualTo(2);
    assertThat(index.valueCount("device_field::status", "busy")).isEqualTo(1);
    assertThat(index.valueCount("dimension::os", "android")).isEqualTo(2);
    assertThat(index.valueCount("dimension::os", "ios")).isEqualTo(1);
    assertThat(index.valueCount("device_field::owner", "alice")).isEqualTo(2);
    assertThat(index.valueCount("device_field::quarantined", "yes")).isEqualTo(1);
    assertThat(index.valueCount("device_field::quarantined", "no")).isEqualTo(2);
    // Host property indexed for every device on the host.
    assertThat(index.valueCount("host_property::location", "mtv")).isEqualTo(2);

    // Posting lists (via LazyPostings) point at device indices in devices() order.
    LazyPostings postings = new LazyPostings(snapshot.devices());
    assertThat(posting(postings, "device_field::status", "idle")).containsExactly(0, 2).inOrder();
    assertThat(posting(postings, "device_field::status", "busy")).containsExactly(1);
    assertThat(posting(postings, "dimension::os", "android")).containsExactly(0, 1).inOrder();
    assertThat(posting(postings, "host_field::host_name", "lab1")).containsExactly(0, 1).inOrder();

    // A device carrying a dimension in both supported and required is counted once per distinct
    // value in the index.
    assertThat(index.valueCount("dimension::model", "pixel")).isEqualTo(1);
    assertThat(posting(postings, "dimension::model", "pixel")).containsExactly(0);

    // Sorted distinct values.
    assertThat(index.sortedValues("dimension::os")).containsExactly("android", "ios").inOrder();
    assertThat(index.sortedValues("device_field::status"))
        .containsExactly("busy", "idle")
        .inOrder();

    // Original casing preserved for display.
    assertThat(index.valueDisplays("device_field::status")).containsEntry("idle", "IDLE");

    // Key catalog: dimension and property keys discovered from data; display names resolved.
    assertThat(index.keyIds())
        .containsAtLeast(
            "device_field::uuid",
            "device_field::status",
            "device_field::owner",
            "device_field::type",
            "dimension::os",
            "dimension::model",
            "device_field::quarantined",
            "host_property::location",
            "host_property::host_version",
            "host_field::host_name",
            "host_field::host_ip");
    // Device enrichment: wifi_ssid is single-valued, indexed under its normalized term with the
    // original casing preserved for display.
    assertThat(snapshot.devices().get(0).values("device_config::wifi_ssid"))
        .containsExactly("GoogleGuest");
    assertThat(index.valueCount("device_config::wifi_ssid", "googleguest")).isEqualTo(1);
    LazyPostings wifiPostings = new LazyPostings(snapshot.devices());
    assertThat(posting(wifiPostings, "device_config::wifi_ssid", "googleguest")).containsExactly(0);
    assertThat(index.valueDisplays("device_config::wifi_ssid"))
        .containsEntry("googleguest", "GoogleGuest");

    // Host enrichment: the lab type is computed from the release enum (SHARED_LAB maps to Core) and
    // stamped, as a display name, on every device of the enriched host (device-0 and device-1 on
    // lab1), and absent for devices on an unenriched host (device-2).
    assertThat(index.valueCount("host_field::lab_type", "core lab")).isEqualTo(2);
    assertThat(index.sortedValues("host_field::lab_type")).containsExactly("core lab");
    assertThat(index.valueDisplays("host_field::lab_type")).containsEntry("core lab", "Core Lab");

    // The host keys are stamped onto every device of the host. Host OS defaults
    // to "Unknown" when the property is absent (lab2); connectivity buckets the lab status.
    assertThat(index.valueCount("host_property::host_os", "ubuntu")).isEqualTo(2);
    assertThat(index.valueCount("host_property::host_os", "unknown")).isEqualTo(1);
    assertThat(index.valueCount("host_field::connectivity", "running")).isEqualTo(2);
    assertThat(index.valueCount("host_field::connectivity", "missing")).isEqualTo(1);
    assertThat(index.valueDisplays("host_field::connectivity")).containsEntry("running", "Running");
    assertThat(index.valueCount("host_field::daemon_status", "running")).isEqualTo(2);
    assertThat(index.valueCount("host_field::release_status", "running")).isEqualTo(2);
    assertThat(index.valueCount("host_field::release_type", "shared_lab")).isEqualTo(2);
    assertThat(index.valueCount("host_field::lab_server_version", "v42")).isEqualTo(2);

    // Posting lists resolve the host keys through the device forward store, so filtering by a host
    // attribute selects the devices on matching hosts.
    LazyPostings hostPostings = new LazyPostings(snapshot.devices());
    assertThat(posting(hostPostings, "host_field::lab_type", "core lab"))
        .containsExactly(0, 1)
        .inOrder();
    assertThat(posting(hostPostings, "host_field::connectivity", "missing")).containsExactly(2);
    assertThat(posting(hostPostings, "host_field::daemon_status", "running"))
        .containsExactly(0, 1)
        .inOrder();

    // Host record enrichment fields populated for the enriched host.
    assertThat(snapshot.hosts().get(0).values("host_field::lab_type")).containsExactly("Core Lab");
    assertThat(snapshot.hosts().get(0).values("host_field::release_status"))
        .containsExactly("RUNNING");
    assertThat(snapshot.hosts().get(0).values("host_field::release_type"))
        .containsExactly("SHARED_LAB");
    assertThat(snapshot.hosts().get(0).values("host_field::daemon_status"))
        .containsExactly("RUNNING");

    // Data-driven gating: device-2 is on the unenriched host lab2, so the HostInfoService-sourced
    // keys carry no value for it and lab2 has no lab type. Host OS and connectivity, available in
    // every deployment, still carry values for it.
    assertThat(snapshot.devices().get(1).values("device_config::wifi_ssid")).isEmpty();
    assertThat(posting(hostPostings, "host_field::lab_type", "core lab")).doesNotContain(2);
    assertThat(index.valueCount("host_field::daemon_status", "running")).isEqualTo(2);
    assertThat(snapshot.hosts().get(1).values("host_field::lab_type")).isEmpty();
    assertThat(snapshot.hosts().get(1).values("host_field::release_status")).isEmpty();
    assertThat(snapshot.hosts().get(1).values("host_field::daemon_status")).isEmpty();

    // Host record OS and connectivity: sourced from the host_os property (defaulting to "Unknown")
    // and the lab status bucket.
    assertThat(snapshot.hosts().get(0).values("host_property::host_os")).containsExactly("ubuntu");
    assertThat(snapshot.hosts().get(0).values("host_field::connectivity"))
        .containsExactly("Running");
    assertThat(snapshot.hosts().get(1).values("host_property::host_os")).containsExactly("Unknown");
    assertThat(snapshot.hosts().get(1).values("host_field::connectivity"))
        .containsExactly("Missing");

    // Host index: a parallel index over hosts, not devices. Counts are distinct-host counts, so
    // each of the two hosts contributes at most one to any value. The device count is a host-only
    // key stamped as its decimal string.
    FleetIndex hostIndex = snapshot.hostIndex();
    assertThat(hostIndex.valueCount("host_field::device_count", "2")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_field::device_count", "1")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_field::host_name", "lab1")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_field::host_name", "lab2")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_field::host_ip", "1.1.1.1")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_field::host_ip", "2.2.2.2")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_property::host_os", "ubuntu")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_property::host_os", "unknown")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_field::connectivity", "running")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_field::connectivity", "missing")).isEqualTo(1);
    // The HostInfoService-sourced keys and lab type are carried only by the enriched host (lab1).
    assertThat(hostIndex.valueCount("host_field::lab_type", "core lab")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_field::daemon_status", "running")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_field::release_status", "running")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_field::release_type", "shared_lab")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_field::lab_server_version", "v42")).isEqualTo(1);
    // Host properties are indexed per host, so the location on lab1 counts once (not per device).
    assertThat(hostIndex.valueCount("host_property::location", "mtv")).isEqualTo(1);

    // Host posting lists resolve host keys through the host forward store, keyed by host index in
    // hosts() order.
    LazyPostings hostIndexPostings = LazyPostings.forHosts(snapshot.hosts());
    assertThat(posting(hostIndexPostings, "host_field::device_count", "2")).containsExactly(0);
    assertThat(posting(hostIndexPostings, "host_field::device_count", "1")).containsExactly(1);
    assertThat(posting(hostIndexPostings, "host_field::lab_type", "core lab")).containsExactly(0);
    assertThat(posting(hostIndexPostings, "host_field::connectivity", "missing"))
        .containsExactly(1);
  }

  @Test
  public void build_hostIndex_includesZeroDeviceHost() {
    LabQueryResult labResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData(
                            "empty-lab",
                            "9.9.9.9",
                            LabStatus.LAB_RUNNING,
                            HostProperties.getDefaultInstance())))
            .build();

    FleetSnapshot snapshot = builder.build(labResult, BUILD_TIME);

    assertThat(snapshot.deviceCount()).isEqualTo(0);
    assertThat(snapshot.hostCount()).isEqualTo(1);

    FleetIndex hostIndex = snapshot.hostIndex();
    assertThat(hostIndex.valueCount("host_field::device_count", "0")).isEqualTo(1);
    assertThat(hostIndex.valueCount("host_field::host_name", "empty-lab")).isEqualTo(1);
    LazyPostings hostPostings = LazyPostings.forHosts(snapshot.hosts());
    assertThat(posting(hostPostings, "host_field::device_count", "0")).containsExactly(0);

    assertThat(snapshot.index().valueCount("host_field::host_name", "empty-lab")).isEqualTo(0);
  }

  @Test
  public void build_hostLabType_combinesHostPropertyAndReleaseEnum() {
    LabQueryResult labResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(2)
                    .addLabData(
                        labData(
                            "lab-core",
                            "1.1.1.1",
                            LabStatus.LAB_RUNNING,
                            HostProperties.newBuilder()
                                .addHostProperty(prop("lab_type", "slaas"))
                                .addHostProperty(prop("dm_type", "fusion"))
                                .addHostProperty(prop("host_os", "debian"))
                                .build(),
                            device0()))
                    .addLabData(
                        labData(
                            "lab-ats",
                            "2.2.2.2",
                            LabStatus.LAB_RUNNING,
                            HostProperties.getDefaultInstance(),
                            device1())))
            .build();

    CoreFleetRawData raw =
        CoreFleetRawData.builder()
            .setLabData(labResult)
            .setHostEnrichments(
                ImmutableMap.of(
                    "lab-core",
                    HostEnrichment.builder().setReleaseType(Optional.of("SHARED_LAB")).build()))
            .build();

    FleetSnapshot snapshot = builder.build(raw, BUILD_TIME);
    FleetIndex index = snapshot.index();

    assertThat(snapshot.hosts().get(0).values("host_field::lab_type"))
        .containsExactly("Satellite Lab", "SLaaS", "Core Lab", "Fusion Lab")
        .inOrder();

    assertThat(index.sortedValues("host_field::lab_type"))
        .containsExactly("core lab", "fusion lab", "satellite lab", "slaas")
        .inOrder();
    assertThat(index.valueDisplays("host_field::lab_type")).containsEntry("slaas", "SLaaS");
    assertThat(index.valueDisplays("host_field::lab_type"))
        .containsEntry("fusion lab", "Fusion Lab");
    assertThat(index.valueCount("host_field::lab_type", "core lab")).isEqualTo(1);

    LazyPostings postings = new LazyPostings(snapshot.devices());
    assertThat(posting(postings, "host_field::lab_type", "core lab")).containsExactly(0);
    assertThat(snapshot.hosts().get(1).values("host_field::lab_type")).isEmpty();
    assertThat(posting(postings, "host_field::lab_type", "slaas")).doesNotContain(1);

    assertThat(index.valueCount("host_field::daemon_status", "running")).isEqualTo(0);
  }

  @Test
  public void build_atsController_termIsIdDisplayIsFriendly() {
    LabQueryResult labResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(2)
                    .addLabData(
                        labData(
                            "lab1",
                            "1.1.1.1",
                            LabStatus.LAB_RUNNING,
                            HostProperties.getDefaultInstance(),
                            device0()))
                    .addLabData(
                        labData(
                            "lab2",
                            "1.1.1.2",
                            LabStatus.LAB_RUNNING,
                            HostProperties.getDefaultInstance(),
                            device1())))
            .build();

    CoreFleetRawData raw =
        CoreFleetRawData.builder()
            .setLabData(labResult)
            .setHostEnrichments(
                ImmutableMap.of(
                    "lab1", hostAtsControllerEnrichment("xiaomi"),
                    "lab2", hostAtsControllerEnrichment("acme")))
            .setAtsControllerDisplays(ImmutableMap.of("xiaomi", "Partner Lab: Xiaomi"))
            .build();

    FleetSnapshot atsSnapshot = builder.build(raw, BUILD_TIME);
    FleetIndex index = atsSnapshot.index();

    assertThat(index.keyIds()).contains("host_field::ats_controller");

    LazyPostings atsPostings = new LazyPostings(atsSnapshot.devices());
    assertThat(posting(atsPostings, "host_field::ats_controller", "xiaomi")).containsExactly(0);
    assertThat(posting(atsPostings, "host_field::ats_controller", "acme")).containsExactly(1);
    assertThat(index.valueCount("host_field::ats_controller", "xiaomi")).isEqualTo(1);

    assertThat(index.valueDisplays("host_field::ats_controller"))
        .containsEntry("xiaomi", "Partner Lab: Xiaomi");
    assertThat(index.valueDisplays("host_field::ats_controller")).containsEntry("acme", "acme");
  }

  @Test
  public void build_hostAtsController_termIsIdDisplayIsFriendly() {
    LabQueryResult labResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(2)
                    .addLabData(
                        labData(
                            "lab1",
                            "1.1.1.1",
                            LabStatus.LAB_RUNNING,
                            HostProperties.getDefaultInstance(),
                            device0()))
                    .addLabData(
                        labData(
                            "lab2",
                            "2.2.2.2",
                            LabStatus.LAB_RUNNING,
                            HostProperties.getDefaultInstance(),
                            device1())))
            .build();

    CoreFleetRawData raw =
        CoreFleetRawData.builder()
            .setLabData(labResult)
            .setHostEnrichments(ImmutableMap.of("lab1", hostAtsControllerEnrichment("xiaomi")))
            .setAtsControllerDisplays(ImmutableMap.of("xiaomi", "Partner Lab: Xiaomi"))
            .build();

    FleetSnapshot snapshot = builder.build(raw, BUILD_TIME);
    FleetIndex hostIndex = snapshot.hostIndex();

    assertThat(snapshot.hosts().get(0).values("host_field::ats_controller"))
        .containsExactly("xiaomi");
    assertThat(snapshot.hosts().get(1).values("host_field::ats_controller")).isEmpty();

    assertThat(hostIndex.keyIds()).contains("host_field::ats_controller");

    assertThat(hostIndex.valueCount("host_field::ats_controller", "xiaomi")).isEqualTo(1);
    assertThat(hostIndex.sortedValues("host_field::ats_controller")).containsExactly("xiaomi");
    LazyPostings hostPostings = LazyPostings.forHosts(snapshot.hosts());
    assertThat(posting(hostPostings, "host_field::ats_controller", "xiaomi")).containsExactly(0);

    assertThat(hostIndex.valueDisplays("host_field::ats_controller"))
        .containsEntry("xiaomi", "Partner Lab: Xiaomi");
  }

  @Test
  public void build_emptyResult_returnsEmptySnapshot() {
    FleetSnapshot snapshot =
        builder.build(CoreFleetRawData.ofLabData(LabQueryResult.getDefaultInstance()), BUILD_TIME);

    assertThat(snapshot.deviceCount()).isEqualTo(0);
    assertThat(snapshot.hostCount()).isEqualTo(0);
    assertThat(snapshot.index().keyIds()).isEmpty();
    assertThat(snapshot.index().valueCount("device_field::status", "idle")).isEqualTo(0);
    LazyPostings emptyPostings = new LazyPostings(snapshot.devices());
    assertThat(posting(emptyPostings, "device_field::status", "idle")).isEmpty();
  }

  @Test
  public void emptyDimensionValue_notIndexedAsValue() {
    DeviceInfo emptyOs =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-empty"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("os", ""))))
            .build();
    DeviceInfo realOs =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-real"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("os", "android"))))
            .build();

    LabQueryResult labResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData(
                            "lab1",
                            "1.1.1.1",
                            LabStatus.LAB_RUNNING,
                            HostProperties.getDefaultInstance(),
                            emptyOs,
                            realOs)))
            .build();

    FleetSnapshot snapshot = builder.build(labResult, BUILD_TIME);
    FleetIndex index = snapshot.index();

    assertThat(index.sortedValues("dimension::os")).containsExactly("android");
    assertThat(index.sortedValues("dimension::os")).doesNotContain("");

    assertThat(index.valueCount("dimension::os", "android")).isEqualTo(1);
    assertThat(index.valueCount("dimension::os", "")).isEqualTo(0);
  }

  private static LabData labData(
      String hostName,
      String ip,
      LabStatus labStatus,
      HostProperties hostProperties,
      DeviceInfo... devices) {
    DeviceList.Builder deviceList = DeviceList.newBuilder().setDeviceTotalCount(devices.length);
    for (DeviceInfo device : devices) {
      deviceList.addDeviceInfo(device);
    }
    return LabData.newBuilder()
        .setLabInfo(
            LabInfo.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName(hostName).setIp(ip))
                .setLabStatus(labStatus)
                .setLabServerFeature(
                    LabServerFeature.newBuilder().setHostProperties(hostProperties)))
        .setDeviceList(deviceList)
        .build();
  }

  private static HostProperty prop(String key, String value) {
    return HostProperty.newBuilder().setKey(key).setValue(value).build();
  }

  private static DeviceEnrichment deviceEnrichment(String wifiSsid) {
    return DeviceEnrichment.builder().setWifiSsid(Optional.of(wifiSsid)).build();
  }

  private static HostEnrichment hostAtsControllerEnrichment(String controllerId) {
    return HostEnrichment.builder().setAtsController(Optional.of(controllerId)).build();
  }

  private static HostEnrichment lab1Enrichment() {
    return HostEnrichment.builder()
        .setReleaseStatus(Optional.of("RUNNING"))
        .setReleaseType(Optional.of("SHARED_LAB"))
        .setDaemonStatus(Optional.of("RUNNING"))
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
                        .addSupportedDimension(dimension("model", "pixel"))
                        .addRequiredDimension(dimension("model", "pixel"))))
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
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("os", "ios"))))
        .setDeviceCondition(
            DeviceCondition.newBuilder()
                .addTempDimension(
                    TempDimension.newBuilder().setDimension(dimension("quarantined", "true"))))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }

  private static List<Integer> posting(LazyPostings postings, String keyId, String value) {
    int[] arr = postings.get(keyId, value);
    List<Integer> result = new ArrayList<>(arr.length);
    for (int idx : arr) {
      result.add(idx);
    }
    return result;
  }
}
