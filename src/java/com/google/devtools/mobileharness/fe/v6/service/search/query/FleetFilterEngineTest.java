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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ComplexMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ContainsSubstring;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesAtLeast;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesExactly;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesRegex;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.NoValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.StartsWith;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.inject.Guice;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetFilterEngine}. */
@RunWith(JUnit4.class)
public final class FleetFilterEngineTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  // Synthetic fleet built through the real index builder:
  //   device-0: host lab1, IDLE, owner alice, dim os=android, dim model="Pixel 8".
  //   device-1: host lab1, BUSY, owner bob,   dim os=android, dim model="Pixel 7".
  //   device-2: host lab2, IDLE, owners alice + carol, dim os=ios, no model dimension.
  // FleetIndexBuilder has a package-private @Inject constructor in a sibling package, so obtain it
  // through Guice rather than constructing it directly. FleetFilterEngine is in this package, so it
  // can be constructed directly.
  private final FleetSnapshot snapshot =
      Guice.createInjector().getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
  private final LazyPostings postings = new LazyPostings(snapshot.devices());
  private final FleetFilterEngine engine = new FleetFilterEngine();

  @Test
  public void noFilters_returnsAllDevices() {
    assertThat(engine.match(snapshot, ImmutableList.of(), postings))
        .containsExactly(0, 1, 2)
        .inOrder();
  }

  @Test
  public void simpleMatch_singleValue() {
    assertThat(
            engine.match(
                snapshot, ImmutableList.of(simple("field::status", false, "IDLE")), postings))
        .containsExactly(0, 2)
        .inOrder();
  }

  @Test
  public void simpleMatch_multipleValues_ored() {
    assertThat(
            engine.match(
                snapshot,
                ImmutableList.of(simple("field::owner", false, "alice", "bob")),
                postings))
        .containsExactly(0, 1, 2)
        .inOrder();
  }

  @Test
  public void simpleMatch_caseInsensitiveValue() {
    // The index stores values lowercased; the engine lowercases the query value before lookup.
    assertThat(
            engine.match(
                snapshot, ImmutableList.of(simple("field::status", false, "idle")), postings))
        .containsExactly(0, 2)
        .inOrder();
  }

  @Test
  public void simpleMatch_negated() {
    assertThat(
            engine.match(
                snapshot, ImmutableList.of(simple("field::status", true, "IDLE")), postings))
        .containsExactly(1);
  }

  @Test
  public void simpleMatch_noValue() {
    // device-2 has no model dimension.
    assertThat(engine.match(snapshot, ImmutableList.of(noValue("dim::model")), postings))
        .containsExactly(2);
  }

  @Test
  public void simpleMatch_valueOrNoValue() {
    // "Pixel 8" OR (no value) picks up device-0 and device-2.
    Filter filter =
        Filter.newBuilder()
            .setKey("dim::model")
            .setSimple(
                SimpleMatch.newBuilder()
                    .addValues(FilterValue.newBuilder().setValue("Pixel 8"))
                    .addValues(FilterValue.newBuilder().setNoValue(NoValue.getDefaultInstance())))
            .build();
    assertThat(engine.match(snapshot, ImmutableList.of(filter), postings))
        .containsExactly(0, 2)
        .inOrder();
  }

  @Test
  public void startsWith_prefixOverSortedValues() {
    assertThat(
            engine.match(snapshot, ImmutableList.of(startsWith("dim::model", "Pixel")), postings))
        .containsExactly(0, 1)
        .inOrder();
  }

  @Test
  public void startsWith_narrowerPrefix() {
    assertThat(
            engine.match(snapshot, ImmutableList.of(startsWith("dim::model", "pixel 8")), postings))
        .containsExactly(0);
  }

  @Test
  public void containsSubstring() {
    assertThat(
            engine.match(snapshot, ImmutableList.of(contains("dim::model", "8", false)), postings))
        .containsExactly(0);
  }

  @Test
  public void containsSubstring_negated_excludesMatchesButKeepsNoValue() {
    // Devices whose model does not contain "8": device-1 (Pixel 7) and device-2 (no model).
    assertThat(
            engine.match(snapshot, ImmutableList.of(contains("dim::model", "8", true)), postings))
        .containsExactly(1, 2)
        .inOrder();
  }

  @Test
  public void matchesRegex() {
    assertThat(
            engine.match(
                snapshot, ImmutableList.of(regex("dim::os", "^android$", false)), postings))
        .containsExactly(0, 1)
        .inOrder();
  }

  @Test
  public void matchesRegex_caseInsensitive() {
    // Uppercase pattern still matches the lowercased index values.
    assertThat(
            engine.match(snapshot, ImmutableList.of(regex("dim::os", "ANDROID", false)), postings))
        .containsExactly(0, 1)
        .inOrder();
  }

  @Test
  public void matchesRegex_negated() {
    assertThat(
            engine.match(snapshot, ImmutableList.of(regex("dim::os", "^android$", true)), postings))
        .containsExactly(2);
  }

  @Test
  public void matchesExactly_fullSetEquality() {
    // device-2 owners == {alice, carol}; device-0 owner alice only, so it is excluded.
    assertThat(
            engine.match(
                snapshot, ImmutableList.of(exactly("field::owner", "alice", "carol")), postings))
        .containsExactly(2);
  }

  @Test
  public void matchesExactly_singleValueExcludesSuperset() {
    // Only devices whose entire owner set is exactly {alice}: device-0. device-2 also has carol.
    assertThat(engine.match(snapshot, ImmutableList.of(exactly("field::owner", "alice")), postings))
        .containsExactly(0);
  }

  @Test
  public void matchesAtLeast_superset() {
    // Devices whose owner set contains alice: device-0 and device-2.
    assertThat(engine.match(snapshot, ImmutableList.of(atLeast("field::owner", "alice")), postings))
        .containsExactly(0, 2)
        .inOrder();
  }

  @Test
  public void matchesAtLeast_multipleValues() {
    assertThat(
            engine.match(
                snapshot, ImmutableList.of(atLeast("field::owner", "alice", "carol")), postings))
        .containsExactly(2);
  }

  @Test
  public void multipleFilters_andedTogether() {
    // status IDLE AND os android: only device-0 (device-2 is idle but ios).
    assertThat(
            engine.match(
                snapshot,
                ImmutableList.of(
                    simple("field::status", false, "IDLE"), simple("dim::os", false, "android")),
                postings))
        .containsExactly(0);
  }

  // --- Filter builders ---

  private static Filter simple(String key, boolean negated, String... values) {
    SimpleMatch.Builder match = SimpleMatch.newBuilder().setNegated(negated);
    for (String value : values) {
      match.addValues(FilterValue.newBuilder().setValue(value));
    }
    return Filter.newBuilder().setKey(key).setSimple(match).build();
  }

  private static Filter noValue(String key) {
    return Filter.newBuilder()
        .setKey(key)
        .setSimple(
            SimpleMatch.newBuilder()
                .addValues(FilterValue.newBuilder().setNoValue(NoValue.getDefaultInstance())))
        .build();
  }

  private static Filter startsWith(String key, String value) {
    return Filter.newBuilder()
        .setKey(key)
        .setComplex(
            ComplexMatch.newBuilder().setStartsWith(StartsWith.newBuilder().setValue(value)))
        .build();
  }

  private static Filter contains(String key, String value, boolean negated) {
    return Filter.newBuilder()
        .setKey(key)
        .setComplex(
            ComplexMatch.newBuilder()
                .setContainsSubstring(
                    ContainsSubstring.newBuilder().setValue(value).setNegated(negated)))
        .build();
  }

  private static Filter regex(String key, String value, boolean negated) {
    return Filter.newBuilder()
        .setKey(key)
        .setComplex(
            ComplexMatch.newBuilder()
                .setMatchesRegex(MatchesRegex.newBuilder().setValue(value).setNegated(negated)))
        .build();
  }

  private static Filter exactly(String key, String... values) {
    MatchesExactly.Builder match = MatchesExactly.newBuilder();
    for (String value : values) {
      match.addValues(value);
    }
    return Filter.newBuilder()
        .setKey(key)
        .setComplex(ComplexMatch.newBuilder().setMatchesExactly(match))
        .build();
  }

  private static Filter atLeast(String key, String... values) {
    MatchesAtLeast.Builder match = MatchesAtLeast.newBuilder();
    for (String value : values) {
      match.addValues(value);
    }
    return Filter.newBuilder()
        .setKey(key)
        .setComplex(ComplexMatch.newBuilder().setMatchesAtLeast(match))
        .build();
  }

  // --- Synthetic fleet ---

  private static LabQueryResult fleet() {
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(2)
                .addLabData(labData("lab1", "1.1.1.1", device0(), device1()))
                .addLabData(labData("lab2", "2.2.2.2", device2())))
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
                .addType("AndroidRealDevice")
                .addOwner("alice")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("os", "android"))
                        .addSupportedDimension(dimension("model", "Pixel 8"))))
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
                        .addSupportedDimension(dimension("os", "android"))
                        .addSupportedDimension(dimension("model", "Pixel 7"))))
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
                .addOwner("carol")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("os", "ios"))))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }
}
