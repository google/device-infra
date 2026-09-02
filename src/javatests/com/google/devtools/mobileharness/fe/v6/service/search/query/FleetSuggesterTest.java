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
import com.google.common.collect.ImmutableSet;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestion;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.DimensionCatalogStore;
import com.google.inject.Guice;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetSuggester}. */
@RunWith(JUnit4.class)
public final class FleetSuggesterTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  // Synthetic fleet built through the real index builder, across two hosts:
  //   device-0: IDLE, android, owner alice+bob, model pixel,  pool shared,    lab_location mtv.
  //   device-1: IDLE, android, owner alice,     model pixel,  pool shared,    lab_location mtv.
  //   device-2: BUSY, ios,     owner carol,     model iphone, pool dedicated, lab_location nyc.
  //   device-3: IDLE, android, owner alice,     model nexus,  pool shared,    (no lab_location).
  // FleetIndexBuilder and FleetSuggester have package-private @Inject constructors, so obtain them
  // through Guice rather than constructing directly.
  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final LazyPostings postings = new LazyPostings(snapshot.devices());
  private final DeviceCorpus corpus = new DeviceCorpus(snapshot, postings, null);

  // FleetSuggester needs the per-fleet ScenarioCuration map, which the production MapBinder wires
  // at activation. Construct it directly through the package-private @Inject constructor, binding
  // the OSS ats curation under FLEET_SELF so its scenario key ranking drives the ordering
  // assertions below. FleetFilterEngine has a package-private @Inject constructor, so obtain it
  // through Guice.
  private final FleetSuggester suggester =
      new FleetSuggester(
          Guice.createInjector().getInstance(FleetFilterEngine.class),
          ImmutableMap.of(Fleet.FLEET_SELF, new AtsCuration()));

  @Test
  public void valuePrefix_suggestsApplyFilterUnderMatchingKey() {
    // "pix" prefix-matches the model value "pixel"; nothing else in the fleet starts with it.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("pix"));

    FleetSuggestion pixel = firstApplyFilter(response, "dimension::model");
    assertThat(pixel.getApplyFilter().getResultingFilter().getSimple().getValues(0).getValue())
        .isEqualTo("pixel");
    // Two devices (device-0, device-1) carry model pixel.
    assertThat(pixel.getCount()).isEqualTo(2);
    // The main text names the key, then emphasizes the matched value.
    assertThat(pixel.getMainTextList()).hasSize(2);
    assertThat(pixel.getMainText(0).getText()).isEqualTo("Model is ");
    assertThat(pixel.getMainText(0).getEmphasized()).isFalse();
    assertThat(pixel.getMainText(1).getText()).isEqualTo("pixel");
    assertThat(pixel.getMainText(1).getEmphasized()).isTrue();
    assertThat(pixel.getLabel()).isEqualTo("Add filter");
  }

  @Test
  public void uuidValue_suggestsDeviceIdFilter() {
    // Typing a device UUID resolves through ordinary value search onto the UUID key, no dedicated
    // identifier detector.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("device-2"));

    FleetSuggestion uuid = firstApplyFilter(response, "device_field::uuid");
    assertThat(uuid.getApplyFilter().getResultingFilter().getSimple().getValues(0).getValue())
        .isEqualTo("device-2");
    assertThat(uuid.getCount()).isEqualTo(1);
  }

  @Test
  public void ownerValue_suggestsOwnerFilterWithPluralVerb() {
    // Typing a user name resolves onto the Owners key, again through value search.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("alice"));

    FleetSuggestion owner = firstApplyFilter(response, "device_field::owner");
    assertThat(owner.getApplyFilter().getResultingFilter().getSimple().getValues(0).getValue())
        .isEqualTo("alice");
    // Owner is multi-valued, so the verb reads "are".
    assertThat(owner.getMainText(0).getText()).isEqualTo("Owners are ");
    assertThat(owner.getMainText(1).getText()).isEqualTo("alice");
    assertThat(owner.getMainText(1).getEmphasized()).isTrue();
    // Devices 0, 1, 3 are owned by alice.
    assertThat(owner.getCount()).isEqualTo(3);
    assertThat(owner.getApplyFilter().getMetadata().getIsPlural()).isTrue();
  }

  @Test
  public void keyName_suggestsOpenPickerForThatKey() {
    // "status" is a key name; it should offer the key itself (opens the picker) as well as its
    // ready-to-apply values.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("status"));

    FleetSuggestion keyOnly = firstOpenPicker(response, "device_field::status");
    assertThat(keyOnly.getOpenPicker().hasNewChip()).isTrue();
    assertThat(keyOnly.getMainText(0).getText()).isEqualTo("Status");
    // The value conditions for the same key are also present (IDLE, BUSY).
    assertThat(firstApplyFilter(response, "device_field::status")).isNotNull();
  }

  @Test
  public void modifyExistingChip_usesPlusCountPrefixAndStagesValue() {
    // A chip already filters model=pixel. Typing another model value offers a modify: stage the
    // value in the picker, with the count shown as a "+" delta.
    FleetSuggestionResponse response =
        suggester.suggest(corpus, request("nexus", simple("dimension::model", "pixel")));

    FleetSuggestion modify = firstOpenPicker(response, "dimension::model");
    assertThat(modify.getLabel()).isEqualTo("Modify Model");
    assertThat(modify.getOpenPicker().getStagedModify().getValuesList()).containsExactly("nexus");
    assertThat(modify.getCountPrefix()).isEqualTo("+");
    // device-3 (model nexus) is the one device the modification would add.
    assertThat(modify.getCount()).isEqualTo(1);
    assertThat(modify.getMainText(0).getText()).isEqualTo("add ");
    assertThat(modify.getMainText(1).getText()).isEqualTo("nexus");
    assertThat(modify.getMainText(1).getEmphasized()).isTrue();
  }

  @Test
  public void groupBy_flagsOverMaxWhenGroupCountExceedsCap() {
    // A fleet with more than the suggestion cap of distinct pools: grouping by pool is flagged
    // over_max so the frontend can warn that the accordion would be unusably long.
    FleetSnapshot manyPools =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(manyPoolFleet(60), BUILD_TIME);

    LazyPostings manyPoolsPostings = new LazyPostings(manyPools.devices());
    FleetSuggestionResponse response =
        suggester.suggest(
            new DeviceCorpus(manyPools, manyPoolsPostings, null), request("group by pool"));

    FleetSuggestion group = firstAddGroupBy(response, "dimension::pool");
    assertThat(group.getLabel()).isEqualTo("Group by");
    assertThat(group.getCountUnit()).isEqualTo("groups");
    assertThat(group.getCount()).isEqualTo(60);
    assertThat(group.getOverMax()).isTrue();
  }

  @Test
  public void ranking_higherKeyPriorityFirst() {
    // The same value exists under a core key (Model, priority 3) and a raw dimension (priority 1).
    // The core key must rank first.
    FleetSnapshot fleet =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(dualKeyValueFleet(), BUILD_TIME);

    LazyPostings fleetPostings = new LazyPostings(fleet.devices());
    FleetSuggestionResponse response =
        suggester.suggest(new DeviceCorpus(fleet, fleetPostings, null), request("zephyr"));

    assertThat(response.getItemsCount()).isAtLeast(2);
    assertThat(response.getItems(0).getApplyFilter().getResultingFilter().getKey())
        .isEqualTo("dimension::model");
  }

  @Test
  public void emptyQuery_returnsCuratedStarterKeysAsOpenPickers() {
    FleetSuggestionResponse response = suggester.suggest(corpus, request(""));

    // The curated starter keys present in this fleet, in their fixed order. dimension::os is absent
    // from
    // the fleet, so it is skipped; every entry opens the value picker.
    ImmutableList.Builder<String> keys = ImmutableList.builder();
    for (FleetSuggestion item : response.getItemsList()) {
      assertThat(item.hasOpenPicker()).isTrue();
      keys.add(item.getOpenPicker().getKey());
    }
    assertThat(keys.build())
        .containsExactly(
            "device_field::status",
            "dimension::model",
            "device_field::type",
            "device_field::owner",
            "dimension::pool",
            "device_field::quarantined")
        .inOrder();
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

  private static FleetSuggestion firstAddGroupBy(FleetSuggestionResponse response, String key) {
    for (FleetSuggestion item : response.getItemsList()) {
      if (item.hasAddGroupBy() && item.getAddGroupBy().getKey().equals(key)) {
        return item;
      }
    }
    throw new AssertionError("no add-group-by suggestion for " + key);
  }

  private static FleetSuggestionRequest request(String input, Filter... filters) {
    FleetSuggestionRequest.Builder builder =
        FleetSuggestionRequest.newBuilder().setInput(input).setFleet(Fleet.FLEET_SELF);
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

  @Test
  public void keyMatch_discoveredDimensionInCatalog_suggestsAddFilterDimension() {
    DimensionCatalogStore catalogStore = new DimensionCatalogStore();
    catalogStore.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("build", "carrier"));
    FleetSuggester suggesterWithCatalog =
        new FleetSuggester(
            Guice.createInjector().getInstance(FleetFilterEngine.class),
            ImmutableMap.of(Fleet.FLEET_SELF, new AtsCuration()),
            catalogStore);

    FleetSuggestionResponse response =
        suggesterWithCatalog.suggest(
            corpus, FleetSuggestionRequest.newBuilder().setInput("build").setLimit(5).build());

    FleetSuggestion suggestion = firstOpenPicker(response, "dimension::build");
    assertThat(suggestion.getLabel()).isEqualTo("Add filter");
    assertThat(suggestion.getMainText(0).getText()).isEqualTo("Dimension build");
  }

  @Test
  public void keyMatch_explicitDimensionPrefix_suggestsAddFilterDimension() {
    DimensionCatalogStore catalogStore = new DimensionCatalogStore();
    catalogStore.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("build", "carrier"));
    FleetSuggester suggesterWithCatalog =
        new FleetSuggester(
            Guice.createInjector().getInstance(FleetFilterEngine.class),
            ImmutableMap.of(Fleet.FLEET_SELF, new AtsCuration()),
            catalogStore);

    FleetSuggestionResponse response =
        suggesterWithCatalog.suggest(
            corpus,
            FleetSuggestionRequest.newBuilder().setInput("dimension build").setLimit(5).build());

    FleetSuggestion suggestion = firstOpenPicker(response, "dimension::build");
    assertThat(suggestion.getLabel()).isEqualTo("Add filter");
    assertThat(suggestion.getMainText(0).getText()).isEqualTo("Dimension build");
  }

  @Test
  public void keyMatch_namespaceColon_withCatalogOnlyOrIndexOnlyDimension() {
    DimensionCatalogStore catalogStore = new DimensionCatalogStore();
    catalogStore.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("build"));
    FleetSuggester suggesterWithCatalog =
        new FleetSuggester(
            Guice.createInjector().getInstance(FleetFilterEngine.class),
            ImmutableMap.of(Fleet.FLEET_SELF, new AtsCuration()),
            catalogStore);

    FleetSuggestionResponse catalogResponse =
        suggesterWithCatalog.suggest(
            corpus,
            FleetSuggestionRequest.newBuilder()
                .setInput("dimension:build is prod")
                .setFleet(Fleet.FLEET_SELF)
                .build());
    assertThat(firstApplyFilter(catalogResponse, "dimension::build").getLabel())
        .isEqualTo("Add filter");

    FleetSuggestionResponse indexResponse =
        suggesterWithCatalog.suggest(
            corpus,
            FleetSuggestionRequest.newBuilder()
                .setInput("dimension:model is pixel")
                .setFleet(Fleet.FLEET_SELF)
                .build());
    assertThat(firstApplyFilter(indexResponse, "dimension::model").getLabel())
        .isEqualTo("Add filter");
  }

  @Test
  public void keyMatch_catalogOnlyDimension_prefixMatch() {
    DimensionCatalogStore catalogStore = new DimensionCatalogStore();
    catalogStore.setDimensionNames(
        Fleet.FLEET_SELF, ImmutableSet.of("screen_density", "big_screen"));
    FleetSuggester suggesterWithCatalog =
        new FleetSuggester(
            Guice.createInjector().getInstance(FleetFilterEngine.class),
            ImmutableMap.of(Fleet.FLEET_SELF, new AtsCuration()),
            catalogStore);

    FleetSuggestionResponse response =
        suggesterWithCatalog.suggest(
            corpus,
            FleetSuggestionRequest.newBuilder()
                .setInput("screen")
                .setFleet(Fleet.FLEET_SELF)
                .build());

    // Prefix match ("screen_density", tier 2) must rank before substring match ("big_screen", tier
    // 1),
    // killing mutant on prefix loop.
    assertThat(response.getItems(0).getMainText(0).getText()).isEqualTo("Dimension screen_density");
    assertThat(response.getItems(1).getMainText(0).getText()).isEqualTo("Dimension big_screen");
  }

  @Test
  public void keyMatch_catalogOnlyDimension_namespaceMatch() {
    DimensionCatalogStore catalogStore = new DimensionCatalogStore();
    catalogStore.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("screen_density"));
    FleetSuggester suggesterWithCatalog =
        new FleetSuggester(
            Guice.createInjector().getInstance(FleetFilterEngine.class),
            ImmutableMap.of(Fleet.FLEET_SELF, new AtsCuration()),
            catalogStore);

    FleetSuggestionResponse response =
        suggesterWithCatalog.suggest(
            corpus,
            FleetSuggestionRequest.newBuilder()
                .setInput("device dimension screen_density")
                .setFleet(Fleet.FLEET_SELF)
                .build());

    // 'device dimension screen_density' matches NAMESPACE_DIM in resolveKey, but normTerm
    // ('device_dimension_screen_density') cannot match display or bareName via startsWith or
    // contains.
    // Therefore, it exclusively depends on isDiscoveredDimension, killing mutant on lines
    // 1147-1150.
    assertThat(response.getItemsList()).isNotEmpty();
    FleetSuggestion suggestion = firstOpenPicker(response, "dimension::screen_density");
    assertThat(suggestion.getMainText(0).getText()).isEqualTo("Dimension screen_density");
  }

  @Test
  public void keyMatch_catalogOnlyDimension_substringMatch() {
    DimensionCatalogStore catalogStore = new DimensionCatalogStore();
    catalogStore.setDimensionNames(Fleet.FLEET_SELF, ImmutableSet.of("screen_density"));
    FleetSuggester suggesterWithCatalog =
        new FleetSuggester(
            Guice.createInjector().getInstance(FleetFilterEngine.class),
            ImmutableMap.of(Fleet.FLEET_SELF, new AtsCuration()),
            catalogStore);

    FleetSuggestionResponse response =
        suggesterWithCatalog.suggest(
            corpus,
            FleetSuggestionRequest.newBuilder()
                .setInput("density")
                .setFleet(Fleet.FLEET_SELF)
                .build());

    FleetSuggestion suggestion = firstOpenPicker(response, "dimension::screen_density");
    assertThat(suggestion.getLabel()).isEqualTo("Add filter");
    assertThat(suggestion.getMainText(0).getText()).isEqualTo("Dimension screen_density");
  }

  // --- Synthetic fleets ---

  private static LabQueryResult fleet() {
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(2)
                .addLabData(labData("lab-a", "1.1.1.1", device0(), device1()))
                .addLabData(labData("lab-b", "2.2.2.2", device2(), device3())))
        .build();
  }

  private static LabQueryResult manyPoolFleet(int count) {
    DeviceList.Builder deviceList = DeviceList.newBuilder().setDeviceTotalCount(count);
    for (int i = 0; i < count; i++) {
      deviceList.addDeviceInfo(
          DeviceInfo.newBuilder()
              .setDeviceLocator(DeviceLocator.newBuilder().setId("device-" + i))
              .setDeviceStatus(DeviceStatus.IDLE)
              .setDeviceFeature(
                  DeviceFeature.newBuilder()
                      .addType("android_real_device")
                      .setCompositeDimension(
                          DeviceCompositeDimension.newBuilder()
                              .addSupportedDimension(dimension("pool", "pool-" + i)))));
    }
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
                                .setLabStatus(LabStatus.LAB_RUNNING))
                        .setDeviceList(deviceList)))
        .build();
  }

  private static LabQueryResult dualKeyValueFleet() {
    DeviceInfo device =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("model", "zephyr"))
                            .addSupportedDimension(dimension("custom_tag", "zephyr"))))
            .build();
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(1)
                .addLabData(labData("lab-a", "1.1.1.1", device)))
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

  private static DeviceInfo device0() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("android_real_device")
                .addOwner("alice")
                .addOwner("bob")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("model", "pixel"))
                        .addSupportedDimension(dimension("pool", "shared"))
                        .addSupportedDimension(dimension("lab_location", "mtv"))))
        .build();
  }

  private static DeviceInfo device1() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-1"))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("android_real_device")
                .addOwner("alice")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("model", "pixel"))
                        .addSupportedDimension(dimension("pool", "shared"))
                        .addSupportedDimension(dimension("lab_location", "mtv"))))
        .build();
  }

  private static DeviceInfo device2() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-2"))
        .setDeviceStatus(DeviceStatus.BUSY)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("ios_real_device")
                .addOwner("carol")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("model", "iphone"))
                        .addSupportedDimension(dimension("pool", "dedicated"))
                        .addSupportedDimension(dimension("lab_location", "nyc"))))
        .build();
  }

  private static DeviceInfo device3() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-3"))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("android_real_device")
                .addOwner("alice")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("model", "nexus"))
                        .addSupportedDimension(dimension("pool", "shared"))))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }
}
