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
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsHostKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDisplay;
import com.google.inject.Guice;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Specification of the two {@link KeyVocabulary} implementations.
 *
 * <p>The central property under test is <b>entity purity</b>: nothing a device vocabulary returns
 * is unknown to a device registry, and nothing a host vocabulary returns is unknown to a host
 * registry, regardless of what the user typed or what the dimension catalog contains. The remaining
 * tests pin down resolution order, that aliases come from the registry and nowhere else, and the
 * entity-specific presentation of a {@link KeyDescriptor}.
 *
 * <p>Fixture: the standalone ATS registries, one lab with one device. The device carries the
 * dimensions {@code model=pixel} and {@code custom_tag=alpha}; the host carries the property {@code
 * rack=r1}. The dimension catalog additionally lists {@code battery_status} and {@code
 * monsoon_status}, which are not indexed. Built-in keys are referenced by their descriptor
 * constants; long-tail keys are obtained from the registry, never spelled as ids.
 */
@RunWith(JUnit4.class)
public final class KeyVocabularyTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);
  private static final ImmutableSet<String> CATALOG =
      ImmutableSet.of("battery_status", "monsoon_status");

  /** Inputs exercising every resolution path; used by both purity tests. */
  private static final ImmutableList<String> TOKENS =
      ImmutableList.of(
          "status",
          "model",
          "device count",
          "devices",
          "host name",
          "host",
          "os",
          "version",
          "battery_status",
          "monsoon status",
          "custom_tag",
          "rack",
          "dimension:battery_status",
          "device dimension model",
          "host property:rack",
          "host_property rack",
          "pool",
          "lab location",
          "location",
          "wifi",
          "owner",
          "nonexistent_key");

  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final DeviceKeyRegistry deviceRegistry = new AtsDeviceKeyRegistry();
  private final HostKeyRegistry hostRegistry = new AtsHostKeyRegistry();
  private final AtsCuration curation = new AtsCuration();

  private final KeyVocabulary device =
      new DeviceKeyVocabulary(deviceRegistry, snapshot.index(), CATALOG, curation);
  private final KeyVocabulary host =
      new HostKeyVocabulary(hostRegistry, snapshot.hostIndex(), curation);

  // Long-tail keys of the fixture, obtained from the registries rather than spelled as ids.
  private final DeviceKeyDescriptor customTag = deviceRegistry.dimensionKey("custom_tag").get();
  private final DeviceKeyDescriptor batteryStatus =
      deviceRegistry.dimensionKey("battery_status").get();
  private final DeviceKeyDescriptor rackOnDevice = deviceRegistry.hostPropertyKey("rack").get();
  private final HostKeyDescriptor rackOnHost = hostRegistry.hostPropertyKey("rack").get();

  // ---- Entity purity: the property that prevents the reported bug class ----

  @Test
  public void hostVocabulary_neverYieldsAKeyUnknownToTheHostRegistry() {
    for (String token : TOKENS) {
      for (KeyDescriptor key : host.resolve(token)) {
        assertWithMessage("host resolve(%s) yielded %s", token, key.id())
            .that(hostRegistry.getKey(key.id()))
            .isPresent();
      }
    }
    for (KeyDescriptor key : host.discoverableKeys()) {
      assertWithMessage("host discoverable %s", key.id())
          .that(hostRegistry.getKey(key.id()))
          .isPresent();
    }
    for (KeyDescriptor key : host.groupByCandidates()) {
      assertWithMessage("host group-by %s", key.id())
          .that(hostRegistry.getKey(key.id()))
          .isPresent();
    }
  }

  @Test
  public void deviceVocabulary_neverYieldsAKeyUnknownToTheDeviceRegistry() {
    for (String token : TOKENS) {
      for (KeyDescriptor key : device.resolve(token)) {
        assertWithMessage("device resolve(%s) yielded %s", token, key.id())
            .that(deviceRegistry.getKey(key.id()))
            .isPresent();
      }
    }
    for (KeyDescriptor key : device.discoverableKeys()) {
      assertWithMessage("device discoverable %s", key.id())
          .that(deviceRegistry.getKey(key.id()))
          .isPresent();
    }
    for (KeyDescriptor key : device.groupByCandidates()) {
      assertWithMessage("device group-by %s", key.id())
          .that(deviceRegistry.getKey(key.id()))
          .isPresent();
    }
  }

  // ---- The reported bug, stated as a unit invariant ----

  @Test
  public void hostVocabulary_ignoresTheDimensionCatalogAndTheDimensionNamespace() {
    assertThat(host.resolve("battery_status")).isEmpty();
    assertThat(host.resolve("dimension:battery_status")).isEmpty();
    assertThat(host.discoverableKeys()).doesNotContain(batteryStatus);
    assertThat(host.describe(batteryStatus.id())).isEmpty();
    assertThat(host.knows(batteryStatus.id())).isFalse();
  }

  @Test
  public void deviceVocabulary_resolvesCatalogDimensionsEvenWhenUnindexed() {
    assertThat(device.resolve("battery_status")).containsExactly(batteryStatus);
    assertThat(device.resolve("monsoon status"))
        .containsExactly(deviceRegistry.dimensionKey("monsoon_status").get());
    assertThat(device.discoverableKeys()).contains(batteryStatus);
  }

  // ---- Resolution order ----

  @Test
  public void resolve_prefersNamespaceThenAliasThenBareToken() {
    // Namespace wins even when the bare word is also an alias, and yields the built-in descriptor.
    assertThat(device.resolve("dimension:model")).containsExactly(DeviceKeys.MODEL);
    // Alias.
    assertThat(device.resolve("Device Model")).containsExactly(DeviceKeys.MODEL);
    // Bare indexed dimension with no alias.
    assertThat(device.resolve("custom_tag")).containsExactly(customTag);
    // Bare indexed host property, in both entities, each as its own entity's descriptor.
    assertThat(device.resolve("rack")).containsExactly(rackOnDevice);
    assertThat(host.resolve("rack")).containsExactly(rackOnHost);
  }

  @Test
  public void resolve_acceptsTheDisplayNameItself() {
    assertThat(device.resolve("Supported Drivers")).containsExactly(DeviceKeys.DRIVER);
    assertThat(device.resolve("host lab server connectivity"))
        .containsExactly(DeviceKeys.HOST_CONNECTIVITY);
    assertThat(host.resolve("Lab Server Connectivity")).containsExactly(HostKeys.CONNECTIVITY);
    assertThat(host.resolve("device count")).containsExactly(HostKeys.DEVICE_COUNT);
  }

  @Test
  public void resolve_keepsSharedAliasesInRegistryOrder() {
    assertThat(device.resolve("version"))
        .containsExactly(DeviceKeys.SDK_VERSION, DeviceKeys.SOFTWARE_VERSION)
        .inOrder();
    assertThat(host.resolve("version")).containsExactly(HostKeys.LAB_SERVER_VERSION);
  }

  @Test
  public void resolve_sameWordMeansDifferentKeysPerEntity() {
    assertThat(device.resolve("os")).containsExactly(DeviceKeys.OS);
    assertThat(host.resolve("os")).containsExactly(HostKeys.HOST_OS);
    assertThat(device.resolve("device count")).isEmpty();
    assertThat(host.resolve("devices")).containsExactly(HostKeys.DEVICE_COUNT);
    assertThat(device.resolve("host")).containsExactly(DeviceKeys.HOST_NAME);
    assertThat(host.resolve("host")).containsExactly(HostKeys.HOST_NAME);
    assertThat(device.resolve("owners")).isEmpty();
    assertThat(device.resolve("wifi")).containsExactly(AtsDeviceKeys.WIFI_SSID);
  }

  @Test
  public void mintLongTailKey_usesEachEntitysLongTailFamily() {
    assertThat(device.mintLongTailKey("Some Tag"))
        .hasValue(deviceRegistry.dimensionKey("some_tag").get());
    assertThat(host.mintLongTailKey("Some Prop"))
        .hasValue(hostRegistry.hostPropertyKey("some_prop").get());
    assertThat(device.mintLongTailKey("  ")).isEmpty();
    assertThat(host.mintLongTailKey("")).isEmpty();
    // A namespaced token that failed to resolve names a family the entity lacks; it is not minted.
    assertThat(host.mintLongTailKey("dimension:battery_status")).isEmpty();
    assertThat(device.mintLongTailKey("host property:rack")).isEmpty();
  }

  // ---- Aliases come from the registry and nowhere else ----

  /**
   * A deployment-specific key exists, with its aliases, only in the deployment that declares it.
   * This is the mechanism that keeps 1P and partner ATS aliases out of a standalone ATS build.
   */
  @Test
  public void aliases_existExactlyWhenTheRegistryDeclaresTheKey() {
    DeviceKeyDescriptor rackSlot =
        DeviceKeyDescriptor.builder()
            .setId(DeviceKeys.dimensionKeyId("rack_slot"))
            .setDisplay(KeyDisplay.of("Rack Slot"))
            .setAliases("slot", "slot number(s)")
            .setIsDimension(true)
            .build();
    DeviceKeyRegistry withRackSlot = new DeviceKeyRegistry(ImmutableList.of(rackSlot)) {};
    KeyVocabulary richer = new DeviceKeyVocabulary(withRackSlot, snapshot.index(), CATALOG, null);

    assertThat(richer.resolve("slot")).containsExactly(rackSlot);
    assertThat(richer.resolve("slot numbers")).containsExactly(rackSlot);
    assertThat(richer.resolve("rack slot")).containsExactly(rackSlot);
    // The standalone ATS registry has no such key, so the same words name nothing.
    assertThat(device.resolve("slot")).isEmpty();
    assertThat(device.resolve("rack slot")).isEmpty();
  }

  @Test
  public void aliasTable_indexesDisplayNameAndExpandedAliases() {
    KeyAliasTable table = KeyAliasTable.of(ImmutableList.of(DeviceKeys.DRIVER, HostKeys.HOST_OS));
    assertThat(table.spellings())
        .containsExactly(
            "supported_drivers",
            "driver",
            "drivers",
            "supported_driver",
            "device_supported_driver",
            "device_supported_drivers",
            "host_os",
            "os");
    assertThat(table.lookup("Device  Supported Drivers")).containsExactly(DeviceKeys.DRIVER);
    assertThat(table.lookup("OS")).containsExactly(HostKeys.HOST_OS);
  }

  // ---- Entity-specific presentation of a descriptor ----

  @Test
  public void describe_yieldsDescriptorsWithEntityPresentationRules() {
    KeyDescriptor deviceModel = device.describe(DeviceKeys.MODEL.id()).get();
    assertThat(device.titleDisplayName(deviceModel)).isEqualTo("Model");
    assertThat(deviceModel.isLongTail()).isFalse();
    assertThat(device.isColdCapable(deviceModel)).isTrue();

    assertThat(device.titleDisplayName(customTag)).isEqualTo("Dimension custom_tag");
    assertThat(device.pillKey(customTag)).isEqualTo("custom_tag");
    assertThat(customTag.bareName()).isEqualTo("custom_tag");
    assertThat(customTag.isLongTail()).isTrue();

    assertThat(device.isColdCapable(DeviceKeys.STATUS)).isFalse();

    assertThat(host.titleDisplayName(rackOnHost)).isEqualTo("Host Property rack");
    assertThat(rackOnHost.isHostProperty()).isTrue();
    assertThat(host.isColdCapable(rackOnHost)).isTrue();

    assertThat(host.titleDisplayName(HostKeys.HOST_NAME)).isEqualTo("Host Name");
    assertThat(host.isColdCapable(HostKeys.HOST_NAME)).isFalse();
  }

  @Test
  public void sharedHostKey_isPresentedThroughTheAskedEntity() {
    // The same underlying host attribute reads differently depending on which entity is searched,
    // so presentation is a vocabulary method rather than a descriptor field. Either descriptor
    // object may be passed; the vocabulary answers with its own entity's view.
    assertThat(device.titleDisplayName(HostKeys.CONNECTIVITY))
        .isEqualTo("Host Lab Server Connectivity");
    assertThat(host.titleDisplayName(DeviceKeys.HOST_CONNECTIVITY))
        .isEqualTo("Lab Server Connectivity");
  }

  @Test
  public void descriptorMethods_rejectKeysOfAnotherEntity() {
    // Asking a vocabulary about a key its entity does not know is a programming error, never a
    // silent fallback: the suggester must only hold descriptors the vocabulary produced.
    assertThrows(IllegalArgumentException.class, () -> device.priority(HostKeys.DEVICE_COUNT));
    assertThrows(
        IllegalArgumentException.class, () -> device.titleDisplayName(HostKeys.DEVICE_COUNT));
    assertThrows(IllegalArgumentException.class, () -> host.priority(DeviceKeys.MODEL));
    assertThrows(IllegalArgumentException.class, () -> host.isColdCapable(DeviceKeys.MODEL));
  }

  @Test
  public void entityAndCurationAccessors() {
    assertThat(device.entity()).isEqualTo(SearchEntity.SEARCH_ENTITY_DEVICE);
    assertThat(host.entity()).isEqualTo(SearchEntity.SEARCH_ENTITY_HOST);
    assertThat(device.priority(DeviceKeys.STATUS)).isGreaterThan(0);
    assertThat(host.priority(HostKeys.HOST_NAME)).isGreaterThan(0);
    assertThat(device.groupByCandidates())
        .containsExactlyElementsIn(curation.deviceGroupByCandidates())
        .inOrder();
    assertThat(host.groupByCandidates())
        .containsExactlyElementsIn(curation.hostGroupByCandidates())
        .inOrder();
  }

  @Test
  public void withoutCuration_ranksEverythingEquallyAndOffersDefaultGroupBys() {
    KeyVocabulary bareDevice =
        new DeviceKeyVocabulary(deviceRegistry, snapshot.index(), CATALOG, null);
    assertThat(bareDevice.priority(DeviceKeys.STATUS)).isEqualTo(0);
    assertThat(bareDevice.groupByCandidates())
        .containsExactly(DeviceKeys.STATUS, DeviceKeys.MODEL, DeviceKeys.TYPE, DeviceKeys.HOST_NAME)
        .inOrder();

    KeyVocabulary bareHost = new HostKeyVocabulary(hostRegistry, snapshot.hostIndex(), null);
    assertThat(bareHost.priority(HostKeys.HOST_NAME)).isEqualTo(0);
    assertThat(bareHost.groupByCandidates())
        .containsExactly(HostKeys.HOST_NAME, HostKeys.CONNECTIVITY, HostKeys.DEVICE_COUNT)
        .inOrder();
  }

  // ---- Fixture ----

  private static LabQueryResult fleet() {
    DeviceInfo device =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("model", "pixel"))
                            .addSupportedDimension(dimension("custom_tag", "alpha"))))
            .build();
    HostProperties properties =
        HostProperties.newBuilder()
            .addHostProperty(HostProperty.newBuilder().setKey("rack").setValue("r1"))
            .build();
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(1)
                .addLabData(
                    LabData.newBuilder()
                        .setLabInfo(
                            LabInfo.newBuilder()
                                .setLabLocator(
                                    LabLocator.newBuilder().setHostName("lab-a").setIp("1.1.1.1"))
                                .setLabStatus(LabStatus.LAB_RUNNING)
                                .setLabServerFeature(
                                    LabServerFeature.newBuilder().setHostProperties(properties)))
                        .setDeviceList(
                            DeviceList.newBuilder().setDeviceTotalCount(1).addDeviceInfo(device))))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }
}
