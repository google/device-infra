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

package com.google.devtools.mobileharness.fe.v6.service.search.query.suggest;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

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
import com.google.devtools.mobileharness.fe.v6.service.search.index.OverlayView;
import com.google.devtools.mobileharness.fe.v6.service.search.query.AtsCuration;
import com.google.devtools.mobileharness.fe.v6.service.search.query.DeviceCorpus;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetFilterEngine;
import com.google.devtools.mobileharness.fe.v6.service.search.query.HostCorpus;
import com.google.devtools.mobileharness.fe.v6.service.search.query.SearchCorpus;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsHostKeyRegistry;
import com.google.inject.Guice;
import java.time.Instant;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Entity purity of the whole suggestion pipeline: no matter what is typed, with or without active
 * filters, every key that reaches a suggestion's action is known to the searched entity's registry.
 *
 * <p>This is the end-to-end form of the property {@code KeyVocabularyTest} checks per vocabulary.
 * The device corpus carries a populated dimension catalog, so device-only keys are as discoverable
 * as they get; the host corpus is searched with inputs that name device keys by alias, by bare
 * dimension name, and by explicit namespace.
 */
@RunWith(JUnit4.class)
public final class SuggesterEntityPurityTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);
  private static final ImmutableSet<String> CATALOG =
      ImmutableSet.of("battery_status", "monsoon_status", "screen_density");

  /** Inputs covering every intent shape, several of them naming keys of the other entity. */
  private static final ImmutableList<String> INPUTS =
      ImmutableList.of(
          "status",
          "stat",
          "model",
          "model is pixel",
          "model is not pixel",
          "model is",
          "model !=",
          "dimension:battery_status is ok",
          "battery_status is ok",
          "device count is 2",
          "devices is 2",
          "host name is lab-a",
          "host",
          "os",
          "version",
          "location",
          "lab location is mtv",
          "no owner",
          "has model",
          "model is empty",
          "rack is not empty",
          "pixel",
          "not pixel",
          "pixel, iphone",
          "debian",
          "custom_tag is alpha",
          "some_new_key is x",
          "group by",
          "group by status",
          "group by model",
          "group by rack");

  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final FleetSuggester suggester =
      new FleetSuggester(Guice.createInjector().getInstance(FleetFilterEngine.class));

  private final DeviceCorpus deviceCorpus =
      new DeviceCorpus(
          snapshot,
          new LazyPostings(snapshot.devices()),
          new AtsCuration(),
          OverlayView.empty(),
          CATALOG);
  private final HostCorpus hostCorpus =
      new HostCorpus(snapshot, LazyPostings.forHosts(snapshot.hosts()), new AtsCuration());

  @Test
  public void hostSearch_onlyEverSuggestsHostKeys() {
    Predicate<String> known = keyId -> new AtsHostKeyRegistry().getKey(keyId).isPresent();
    assertAllKeysSatisfy(hostCorpus, ImmutableList.of(), known);
    assertAllKeysSatisfy(
        hostCorpus, ImmutableList.of(simple("host_property::host_os", "debian")), known);
  }

  @Test
  public void deviceSearch_onlyEverSuggestsDeviceKeys() {
    Predicate<String> known = keyId -> new AtsDeviceKeyRegistry().getKey(keyId).isPresent();
    assertAllKeysSatisfy(deviceCorpus, ImmutableList.of(), known);
    assertAllKeysSatisfy(
        deviceCorpus, ImmutableList.of(simple("dimension::model", "pixel")), known);
  }

  @Test
  public void ranker_dropsCandidateUnknownToTheEntityVocabulary() {
    SuggestionContext context =
        SuggestionContext.create(
            deviceCorpus,
            ImmutableList.of(),
            Guice.createInjector().getInstance(FleetFilterEngine.class));
    SuggestionRanker ranker =
        new SuggestionRanker(Guice.createInjector().getInstance(FleetFilterEngine.class));
    SuggestionCandidate foreignCandidate =
        SuggestionCandidate.keyOnly(
            new AtsHostKeyRegistry().getKey("host_field::device_count").get(),
            /* tier= */ 3.0,
            FleetSuggestion.newBuilder().setLabel("Add filter"));

    assertThat(ranker.rank(context, ImmutableList.of(foreignCandidate), 10).getItemsList())
        .isEmpty();
  }

  private void assertAllKeysSatisfy(
      SearchCorpus corpus, ImmutableList<Filter> filters, Predicate<String> known) {
    for (String input : INPUTS) {
      FleetSuggestionResponse response =
          suggester.suggest(
              corpus,
              FleetSuggestionRequest.newBuilder()
                  .setInput(input)
                  .setFleet(Fleet.FLEET_SELF)
                  .addAllFilters(filters)
                  .setLimit(50)
                  .build());
      for (FleetSuggestion item : response.getItemsList()) {
        String keyId = actionKey(item);
        assertWithMessage(
                "%s search, input %s, suggestion %s", corpus.vocabulary().entity(), input, item)
            .that(known.test(keyId))
            .isTrue();
      }
    }
  }

  private static String actionKey(FleetSuggestion item) {
    return switch (item.getActionCase()) {
      case APPLY_FILTER -> item.getApplyFilter().getResultingFilter().getKey();
      case OPEN_PICKER -> item.getOpenPicker().getKey();
      case ADD_GROUP_BY -> item.getAddGroupBy().getKey();
      case ACTION_NOT_SET -> throw new AssertionError("suggestion without an action: " + item);
    };
  }

  private static Filter simple(String key, String value) {
    return Filter.newBuilder()
        .setKey(key)
        .setSimple(SimpleMatch.newBuilder().addValues(FilterValue.newBuilder().setValue(value)))
        .build();
  }

  // ---- Fixture: two hosts, three devices ----

  private static LabQueryResult fleet() {
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(2)
                .addLabData(
                    lab(
                        "lab-a",
                        "1.1.1.1",
                        hostProperties("host_os", "debian", "rack", "r1", "lab_location", "mtv"),
                        device("device-0", DeviceStatus.IDLE, "pixel", "alpha"),
                        device("device-1", DeviceStatus.BUSY, "pixel", "beta")))
                .addLabData(
                    lab(
                        "lab-b",
                        "2.2.2.2",
                        hostProperties("host_os", "debian"),
                        device("device-2", DeviceStatus.IDLE, "iphone", "alpha"))))
        .build();
  }

  private static LabData lab(
      String hostName, String ip, HostProperties properties, DeviceInfo... devices) {
    DeviceList.Builder deviceList = DeviceList.newBuilder().setDeviceTotalCount(devices.length);
    for (DeviceInfo device : devices) {
      deviceList.addDeviceInfo(device);
    }
    return LabData.newBuilder()
        .setLabInfo(
            LabInfo.newBuilder()
                .setLabLocator(LabLocator.newBuilder().setHostName(hostName).setIp(ip))
                .setLabStatus(LabStatus.LAB_RUNNING)
                .setLabServerFeature(LabServerFeature.newBuilder().setHostProperties(properties)))
        .setDeviceList(deviceList)
        .build();
  }

  private static DeviceInfo device(String id, DeviceStatus status, String model, String tag) {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId(id))
        .setDeviceStatus(status)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("android_real_device")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("model", model))
                        .addSupportedDimension(dimension("custom_tag", tag))))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
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
