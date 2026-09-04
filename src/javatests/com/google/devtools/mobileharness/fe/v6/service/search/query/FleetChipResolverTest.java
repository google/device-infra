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
import static org.junit.Assert.assertThrows;

import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ComplexMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ContainsSubstring;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetChipResolverResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetResolvedFilterChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetResolvedGroupByChip;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesExactly;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesRegex;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.NoValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import io.grpc.Status;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FleetChipResolver}. */
@RunWith(JUnit4.class)
public final class FleetChipResolverTest {

  private final FleetChipResolver resolver = new FleetChipResolver();

  @Test
  public void simpleSingleValue_pillAndMetadata() {
    FleetChipResolverResponse response =
        resolver.resolve(request(simple("device_field::status", "IDLE")));

    assertThat(response.getFilterChipsCount()).isEqualTo(1);
    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.hasValid()).isTrue();
    assertThat(chip.getValid().getPillKey()).isEqualTo("Status");
    assertThat(chip.getValid().getPillCondition()).isEqualTo("IDLE");
    assertThat(chip.getValid().getMetadata().getKeyDisplayName()).isEqualTo("Status");
    assertThat(chip.getValid().getMetadata().getIsPlural()).isFalse();
    assertThat(chip.getValid().getMetadata().getCanUseAdvanced()).isTrue();
  }

  @Test
  public void simpleMultiValue_showsCount() {
    FleetChipResolverResponse response =
        resolver.resolve(request(simple("device_field::status", "IDLE", "BUSY")));

    assertThat(response.getFilterChips(0).getValid().getPillCondition()).isEqualTo("2");
  }

  @Test
  public void negatedSingleValue_notEqualValue() {
    FleetChipResolverResponse response =
        resolver.resolve(request(simpleNegated("device_field::status", "IDLE")));

    assertThat(response.getFilterChips(0).getValid().getPillCondition()).isEqualTo("\u2260 IDLE");
  }

  @Test
  public void negatedMultiValue_notEqualCount() {
    FleetChipResolverResponse response =
        resolver.resolve(request(simpleNegated("device_field::status", "IDLE", "BUSY")));

    assertThat(response.getFilterChips(0).getValid().getPillCondition()).isEqualTo("\u2260 2");
  }

  @Test
  public void noValueEntry_emptyText() {
    assertThat(
            resolver
                .resolve(request(noValue("dimension::os", false)))
                .getFilterChips(0)
                .getValid()
                .getPillCondition())
        .isEqualTo("empty");
    assertThat(
            resolver
                .resolve(request(noValue("dimension::os", true)))
                .getFilterChips(0)
                .getValid()
                .getPillCondition())
        .isEqualTo("not empty");
  }

  @Test
  public void pluralKey_driver_isPluralMetadata() {
    // The compact condition text does not carry a verb; is_plural is metadata for the frontend.
    FleetChipResolverResponse response =
        resolver.resolve(request(simpleNegated(DeviceKeys.DRIVER.id(), "AndroidRealDeviceDriver")));

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.hasValid()).isTrue();
    assertThat(chip.getValid().getPillKey()).isEqualTo("Supported Drivers");
    assertThat(chip.getValid().getPillCondition()).isEqualTo("\u2260 AndroidRealDeviceDriver");
    assertThat(chip.getValid().getMetadata().getIsPlural()).isTrue();
  }

  @Test
  public void complexContains_conditionText() {
    FleetChipResolverResponse response =
        resolver.resolve(request(contains("dimension::model", "pix")));

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.hasValid()).isTrue();
    assertThat(chip.getValid().getPillKey()).isEqualTo("Model");
    assertThat(chip.getValid().getPillCondition()).isEqualTo("contains pix");
  }

  @Test
  public void complexMatchesExactly_singleValueOriginalCasing() {
    FleetChipResolverResponse response =
        resolver.resolve(request(exactly("dimension::model", "Pixel 8")));

    assertThat(response.getFilterChips(0).getValid().getPillCondition())
        .isEqualTo("is exactly Pixel 8");
  }

  @Test
  public void complexMatchesExactly_multipleValuesShowCount() {
    FleetChipResolverResponse response =
        resolver.resolve(request(exactly("dimension::model", "Pixel 8", "Galaxy")));

    assertThat(response.getFilterChips(0).getValid().getPillCondition()).isEqualTo("is exactly 2");
  }

  @Test
  public void filterChip_canUseAdvanced() {
    FleetChipResolverResponse response =
        resolver.resolve(request(simple("device_field::status", "IDLE")));

    assertThat(response.getFilterChips(0).getValid().getMetadata().getCanUseAdvanced()).isTrue();
  }

  @Test
  public void groupByChips_pillKeyAndDisplayName() {
    FleetChipResolverResponse response =
        resolver.resolve(
            FleetChipResolverRequest.newBuilder()
                .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
                .addGroupByKeys("dimension::model")
                .addGroupByKeys("host_field::host_name")
                .build());

    assertThat(response.getGroupByChipsCount()).isEqualTo(2);
    FleetResolvedGroupByChip model = response.getGroupByChips(0);
    assertThat(model.hasValid()).isTrue();
    assertThat(model.getValid().getPillKey()).isEqualTo("Model");
    assertThat(model.getValid().getDisplayName()).isEqualTo("Model");
    FleetResolvedGroupByChip hostName = response.getGroupByChips(1);
    assertThat(hostName.hasValid()).isTrue();
    assertThat(hostName.getValid().getPillKey()).isEqualTo("Host Name");
    assertThat(hostName.getValid().getDisplayName()).isEqualTo("Host Name");
  }

  @Test
  public void resolve_arraysParallelToRequestInOrder() {
    FleetChipResolverResponse response =
        resolver.resolve(
            FleetChipResolverRequest.newBuilder()
                .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
                .addFilters(simple("device_field::status", "IDLE"))
                .addFilters(simple("device_field::type", "AndroidRealDevice"))
                .addFilters(contains("dimension::model", "pix"))
                .addGroupByKeys("dimension::os")
                .build());

    assertThat(response.getFilterChipsCount()).isEqualTo(3);
    assertThat(response.getFilterChips(0).getValid().getPillKey()).isEqualTo("Status");
    assertThat(response.getFilterChips(1).getValid().getPillKey()).isEqualTo("Type");
    assertThat(response.getFilterChips(2).getValid().getPillKey()).isEqualTo("Model");
    assertThat(response.getGroupByChipsCount()).isEqualTo(1);
    assertThat(response.getGroupByChips(0).getValid().getPillKey()).isEqualTo("OS");
  }

  @Test
  public void validFilterChip_hasValidTrue() {
    FleetChipResolverResponse response =
        resolver.resolve(request(simple("device_field::status", "IDLE")));

    assertThat(response.getFilterChips(0).hasValid()).isTrue();
  }

  @Test
  public void invalidFilterKey_hasInvalidTrueWithDescriptiveReason() {
    FleetChipResolverResponse response =
        resolver.resolve(request(simple("bogus_unknown_key", "foo")));

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.hasInvalid()).isTrue();
    assertThat(chip.getInvalid().getReason())
        .contains("Unknown device filter key: bogus_unknown_key");
  }

  @Test
  public void hostSearch_dimensionKey_hasInvalidTrue() {
    FleetChipResolverRequest req =
        FleetChipResolverRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
            .addFilters(simple("dimension::model", "pixel"))
            .build();

    FleetChipResolverResponse response = resolver.resolve(req);

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.hasInvalid()).isTrue();
    assertThat(chip.getInvalid().getReason()).contains("Unknown host filter key: dimension::model");
  }

  @Test
  public void deviceSearch_hostOnlyKey_hasInvalidTrue() {
    FleetChipResolverRequest req =
        FleetChipResolverRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
            .addFilters(simple("host_field::device_count", "1"))
            .build();

    FleetChipResolverResponse response = resolver.resolve(req);

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.hasInvalid()).isTrue();
    assertThat(chip.getInvalid().getReason())
        .contains("Unknown device filter key: host_field::device_count");
  }

  @Test
  public void hostSearch_bareHostPropertyPrefix_hasInvalidTrue() {
    FleetChipResolverRequest req =
        FleetChipResolverRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
            .addFilters(simple("host_property::", "rack1"))
            .build();

    FleetChipResolverResponse response = resolver.resolve(req);

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.hasInvalid()).isTrue();
    assertThat(chip.getInvalid().getReason()).contains("Unknown host filter key: host_property::");
  }

  @Test
  public void deviceSearch_bareDimensionPrefix_hasInvalidTrue() {
    FleetChipResolverRequest req =
        FleetChipResolverRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
            .addFilters(simple("dimension::", "pixel"))
            .build();

    FleetChipResolverResponse response = resolver.resolve(req);

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.hasInvalid()).isTrue();
    assertThat(chip.getInvalid().getReason()).contains("Unknown device filter key: dimension::");
  }

  @Test
  public void hostSearch_unknownHostField_hasInvalidTrue() {
    FleetChipResolverRequest req =
        FleetChipResolverRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
            .addFilters(simple("host_field::bogus_field", "val"))
            .build();

    FleetChipResolverResponse response = resolver.resolve(req);

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.hasInvalid()).isTrue();
    assertThat(chip.getInvalid().getReason())
        .contains("Unknown host filter key: host_field::bogus_field");
  }

  @Test
  public void hostSearch_validHostProperty_hasValidTrue() {
    FleetChipResolverRequest req =
        FleetChipResolverRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
            .addFilters(simple("host_property::rack", "rack1"))
            .build();

    FleetChipResolverResponse response = resolver.resolve(req);

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.hasValid()).isTrue();
    assertThat(chip.getValid().getPillKey()).isEqualTo("rack");
  }

  @Test
  public void hostSearch_validHostField_hasValidTrue() {
    FleetChipResolverRequest req =
        FleetChipResolverRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
            .addFilters(simple("host_field::host_name", "lab1"))
            .build();

    FleetChipResolverResponse response = resolver.resolve(req);

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.hasValid()).isTrue();
    assertThat(chip.getValid().getPillKey()).isEqualTo("Host Name");
  }

  @Test
  public void invalidRegex_hasInvalidTrueWithSyntaxDetail() {
    FleetChipResolverResponse response =
        resolver.resolve(request(regex("device_field::status", "[unclosed_regex")));

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.hasInvalid()).isTrue();
    assertThat(chip.getInvalid().getReason()).contains("Invalid regular expression");
  }

  @Test
  public void emptySimpleMatch_hasInvalidTrue() {
    Filter emptySimple =
        Filter.newBuilder()
            .setKey("device_field::status")
            .setSimple(SimpleMatch.getDefaultInstance())
            .build();

    FleetChipResolverResponse response = resolver.resolve(request(emptySimple));

    FleetResolvedFilterChip chip = response.getFilterChips(0);
    assertThat(chip.hasInvalid()).isTrue();
    assertThat(chip.getInvalid().getReason())
        .contains("Simple filter condition must have at least one value");
  }

  @Test
  public void groupByKeys_validationStatus() {
    FleetChipResolverRequest req =
        FleetChipResolverRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
            .addGroupByKeys("dimension::os")
            .addGroupByKeys("unknown_group_key")
            .addGroupByKeys("host_field::device_count")
            .build();

    FleetChipResolverResponse response = resolver.resolve(req);

    assertThat(response.getGroupByChips(0).hasValid()).isTrue();
    assertThat(response.getGroupByChips(1).hasInvalid()).isTrue();
    assertThat(response.getGroupByChips(1).getInvalid().getReason())
        .contains("Unknown device group-by key: unknown_group_key");
    assertThat(response.getGroupByChips(2).hasInvalid()).isTrue();
    assertThat(response.getGroupByChips(2).getInvalid().getReason())
        .contains("Unknown device group-by key: host_field::device_count");
  }

  @Test
  public void unspecifiedEntity_throwsFeServiceException() {
    FleetChipResolverRequest req =
        FleetChipResolverRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_UNSPECIFIED)
            .addFilters(simple("device_field::status", "idle"))
            .build();

    FeServiceException e = assertThrows(FeServiceException.class, () -> resolver.resolve(req));
    assertThat(e.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  public void unsupportedFleet_throwsFeServiceException() {
    FleetChipResolverRequest req =
        FleetChipResolverRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
            .setFleet(Fleet.FLEET_ATS)
            .addFilters(simple("device_field::status", "idle"))
            .build();

    FeServiceException e = assertThrows(FeServiceException.class, () -> resolver.resolve(req));
    assertThat(e.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  // --- Request and filter helpers ---

  private static FleetChipResolverRequest request(Filter filter) {
    return FleetChipResolverRequest.newBuilder()
        .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
        .addFilters(filter)
        .build();
  }

  private static Filter simple(String key, String... values) {
    SimpleMatch.Builder simple = SimpleMatch.newBuilder();
    for (String value : values) {
      simple.addValues(FilterValue.newBuilder().setValue(value));
    }
    return Filter.newBuilder().setKey(key).setSimple(simple).build();
  }

  private static Filter simpleNegated(String key, String... values) {
    SimpleMatch.Builder simple = SimpleMatch.newBuilder().setNegated(true);
    for (String value : values) {
      simple.addValues(FilterValue.newBuilder().setValue(value));
    }
    return Filter.newBuilder().setKey(key).setSimple(simple).build();
  }

  private static Filter noValue(String key, boolean negated) {
    return Filter.newBuilder()
        .setKey(key)
        .setSimple(
            SimpleMatch.newBuilder()
                .addValues(FilterValue.newBuilder().setNoValue(NoValue.getDefaultInstance()))
                .setNegated(negated))
        .build();
  }

  private static Filter contains(String key, String needle) {
    return Filter.newBuilder()
        .setKey(key)
        .setComplex(
            ComplexMatch.newBuilder()
                .setContainsSubstring(ContainsSubstring.newBuilder().setValue(needle)))
        .build();
  }

  private static Filter exactly(String key, String... values) {
    MatchesExactly.Builder exactly = MatchesExactly.newBuilder();
    for (String value : values) {
      exactly.addValues(value);
    }
    return Filter.newBuilder()
        .setKey(key)
        .setComplex(ComplexMatch.newBuilder().setMatchesExactly(exactly))
        .build();
  }

  private static Filter regex(String key, String pattern) {
    return Filter.newBuilder()
        .setKey(key)
        .setComplex(
            ComplexMatch.newBuilder().setMatchesRegex(MatchesRegex.newBuilder().setValue(pattern)))
        .build();
  }
}
