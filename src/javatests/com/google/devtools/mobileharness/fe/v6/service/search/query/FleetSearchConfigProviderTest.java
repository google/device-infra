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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.KeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsHostKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyRegistry;
import com.google.inject.Guice;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetSearchConfigProvider}. */
@RunWith(JUnit4.class)
public final class FleetSearchConfigProviderTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  // Three devices across two hosts, all carrying uuid, status, type, owner, and a model dimension,
  // so every curated key below is present in the index and has its built-in display name.
  //   device-0, device-1: on lab-a, IDLE, android, model pixel.
  //   device-2:           on lab-b, BUSY, ios,     model iphone.
  // FleetIndexBuilder has a package-private @Inject constructor, so obtain it through Guice.
  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final FleetSearchConfigProvider provider = new FleetSearchConfigProvider();

  // A curation with known column sets and a landing page enabled, so the assertions do not depend
  // on any real deployment curation.
  private static final ScenarioCuration CURATION =
      new ScenarioCuration() {
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
          return ImmutableList.of(
              DeviceKeys.UUID, DeviceKeys.HOST_NAME, DeviceKeys.STATUS, DeviceKeys.MODEL);
        }

        @Override
        public ImmutableList<DeviceKeyDescriptor> deviceRecommendedColumns() {
          return ImmutableList.of(DeviceKeys.STATUS, DeviceKeys.TYPE, DeviceKeys.MODEL);
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
        public DeviceKeyRegistry deviceKeyRegistry() {
          return new AtsDeviceKeyRegistry();
        }

        @Override
        public HostKeyRegistry hostKeyRegistry() {
          return new AtsHostKeyRegistry();
        }

        @Override
        public boolean landingEnabled() {
          return true;
        }
      };

  @Test
  public void recommended_hasCuratedKeysWithDisplayNamesInOrder() {
    FleetSearchConfig config = provider.getConfig(snapshot, deviceRequest(), CURATION);

    List<KeyDescriptor> recommended = config.getColumns().getRecommendedList();
    List<String> keys = new ArrayList<>();
    for (KeyDescriptor descriptor : recommended) {
      keys.add(descriptor.getKey());
      assertThat(descriptor.getDisplayName()).isNotEmpty();
    }
    assertThat(keys)
        .containsExactly("device_field::status", "device_field::type", "dimension::model")
        .inOrder();
    assertThat(recommended.get(0).getDisplayName()).isEqualTo("Status");
    assertThat(recommended.get(1).getDisplayName()).isEqualTo("Type");
    assertThat(recommended.get(2).getDisplayName()).isEqualTo("Model");
  }

  @Test
  public void defaults_haveKeysDisplayNamesAndLockedFlag() {
    FleetSearchConfig config = provider.getConfig(snapshot, deviceRequest(), CURATION);

    List<FleetColumnDescriptor> defaults = config.getColumns().getDefaultsList();
    List<String> keys = new ArrayList<>();
    for (FleetColumnDescriptor descriptor : defaults) {
      keys.add(descriptor.getKey());
    }
    assertThat(keys)
        .containsExactly(
            "device_field::uuid",
            "host_field::host_name",
            "device_field::status",
            "dimension::model")
        .inOrder();

    assertThat(defaults.get(0).getDisplayName()).isEqualTo("UUID");
    assertThat(defaults.get(1).getDisplayName()).isEqualTo("Host Name");
    assertThat(defaults.get(2).getDisplayName()).isEqualTo("Status");
    assertThat(defaults.get(3).getDisplayName()).isEqualTo("Model");

    // The device identifier column is locked; every other default column is removable.
    assertThat(defaults.get(0).getLocked()).isTrue();
    assertThat(defaults.get(1).getLocked()).isFalse();
    assertThat(defaults.get(2).getLocked()).isFalse();
    assertThat(defaults.get(3).getLocked()).isFalse();
  }

  @Test
  public void landing_reflectsCurationAndDeviceCount() {
    FleetSearchConfig config = provider.getConfig(snapshot, deviceRequest(), CURATION);

    assertThat(config.getLanding().getEnabled()).isTrue();
    assertThat(config.getLanding().getBrowseAllCount()).isEqualTo(snapshot.deviceCount());
    assertThat(config.getLanding().getBrowseAllCount()).isEqualTo(3);
    // The try categories are populated by a follow-up, so none are returned yet.
    assertThat(config.getLanding().getTryCategoriesList()).isEmpty();
  }

  @Test
  public void hostEntity_browseAllCountIsHostCount() {
    FleetSearchConfigRequest hostRequest =
        FleetSearchConfigRequest.newBuilder().setEntity(SearchEntity.SEARCH_ENTITY_HOST).build();

    FleetSearchConfig config = provider.getConfig(snapshot, hostRequest, CURATION);

    assertThat(config.getLanding().getBrowseAllCount()).isEqualTo(snapshot.hosts().size());
    assertThat(config.getLanding().getBrowseAllCount()).isEqualTo(2);
  }

  private static FleetSearchConfigRequest deviceRequest() {
    return FleetSearchConfigRequest.newBuilder()
        .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
        .build();
  }

  // --- Synthetic fleet ---

  private static LabQueryResult fleet() {
    DeviceInfo device0 =
        device("device-0", DeviceStatus.IDLE, "android_real_device", "alice", "pixel");
    DeviceInfo device1 =
        device("device-1", DeviceStatus.IDLE, "android_real_device", "alice", "pixel");
    DeviceInfo device2 =
        device("device-2", DeviceStatus.BUSY, "ios_real_device", "carol", "iphone");

    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(2)
                .addLabData(labData("lab-a", "1.1.1.1", device0, device1))
                .addLabData(labData("lab-b", "2.2.2.2", device2)))
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

  private static DeviceInfo device(
      String id, DeviceStatus status, String type, String owner, String model) {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId(id))
        .setDeviceStatus(status)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType(type)
                .addOwner(owner)
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(
                            DeviceDimension.newBuilder().setName("model").setValue(model))))
        .build();
  }
}
