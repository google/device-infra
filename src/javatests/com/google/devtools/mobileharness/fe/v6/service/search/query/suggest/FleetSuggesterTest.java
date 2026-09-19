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

import com.google.common.collect.ImmutableList;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TextSegment;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.devtools.mobileharness.fe.v6.service.search.index.OverlayView;
import com.google.devtools.mobileharness.fe.v6.service.search.query.AtsCuration;
import com.google.devtools.mobileharness.fe.v6.service.search.query.DeviceCorpus;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetFilterEngine;
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
  private final DeviceCorpus corpus = new DeviceCorpus(snapshot, postings, new AtsCuration());

  // FleetSuggester and FleetFilterEngine have package-private @Inject constructors; obtain the
  // engine through Guice. Key ranking comes from the corpus curation (the OSS ats curation here).
  private final FleetSuggester suggester =
      new FleetSuggester(Guice.createInjector().getInstance(FleetFilterEngine.class));

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
    assertThat(pixel.getApplyFilter().getPillCondition()).isEqualTo("pixel");
    assertThat(pixel.getApplyFilter().getMetadata().getCanUseAdvanced()).isTrue();
  }

  @Test
  public void uuidPrefix_collapsesToSingleStartsWithSuggestion() {
    FleetSuggestionResponse response = suggester.suggest(corpus, request("device-"));

    FleetSuggestion collapsed = firstOpenPicker(response, "device_field::uuid");
    assertThat(collapsed.getCount()).isEqualTo(4);
    assertThat(collapsed.getMainText(0).getText()).isEqualTo("UUID starts with ");
    assertThat(collapsed.getMainText(1).getText()).isEqualTo("device-");
    assertThat(collapsed.getMainText(2).getText()).isEqualTo(" (4)");
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
  public void driverValue_suggestsDriverFilterWithPluralVerb() {
    FleetSuggestionResponse response =
        suggester.suggest(corpus, request("AndroidRealDeviceDriver"));

    FleetSuggestion driver = firstApplyFilter(response, "device_field::driver");
    assertThat(driver.getApplyFilter().getResultingFilter().getSimple().getValues(0).getValue())
        .isEqualTo("AndroidRealDeviceDriver");
    // Driver is multi-valued/plural, so the verb reads "are".
    assertThat(driver.getMainText(0).getText()).isEqualTo("Supported Drivers are ");
    assertThat(driver.getMainText(1).getText()).isEqualTo("AndroidRealDeviceDriver");
    assertThat(driver.getMainText(1).getEmphasized()).isTrue();
    assertThat(driver.getApplyFilter().getMetadata().getIsPlural()).isTrue();
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
  public void keyName_closesWithGroupByRowForTheMatchedKey() {
    // A token that names a key also offers to group by it, as the last row, so the user can pivot
    // to grouping without typing "group by".
    FleetSuggestionResponse response = suggester.suggest(corpus, request("status"));

    FleetSuggestion last = response.getItems(response.getItemsCount() - 1);
    assertThat(last.hasAddGroupBy()).isTrue();
    assertThat(last.getAddGroupBy().getKey()).isEqualTo("device_field::status");
    assertThat(last.getLabel()).isEqualTo("Group by");
    assertThat(last.getCount()).isEqualTo(2); // IDLE and BUSY
    assertThat(last.getMainTextList())
        .containsExactly(TextSegment.newBuilder().setText("Status").setEmphasized(true).build());
    assertThat(response.getItemsList().stream().filter(FleetSuggestion::hasAddGroupBy).count())
        .isEqualTo(1);
  }

  @Test
  public void keyName_prefixMatchGroupsByTheBestMatchedKey() {
    FleetSuggestionResponse response = suggester.suggest(corpus, request("mod"));

    FleetSuggestion last = response.getItems(response.getItemsCount() - 1);
    assertThat(last.getAddGroupBy().getKey()).isEqualTo("dimension::model");
  }

  @Test
  public void keyName_groupByRowTakesOneSlotOfTheLimitAndStaysLast() {
    FleetSuggestionResponse response =
        suggester.suggest(corpus, request("model").toBuilder().setLimit(2).build());

    assertThat(response.getItemsCount()).isEqualTo(2);
    assertThat(response.getItems(0).hasAddGroupBy()).isFalse();
    assertThat(response.getItems(1).getAddGroupBy().getKey()).isEqualTo("dimension::model");
  }

  @Test
  public void keyName_omitsGroupByRowWhenAlreadyGroupedOrAtTheCap() {
    FleetSuggestionResponse alreadyGrouped =
        suggester.suggest(corpus, requestWithGroupBys("status", "device_field::status"));
    assertThat(alreadyGrouped.getItemsList().stream().noneMatch(FleetSuggestion::hasAddGroupBy))
        .isTrue();

    FleetSuggestionResponse atCap =
        suggester.suggest(
            corpus,
            requestWithGroupBys(
                "status", "dimension::model", "dimension::pool", "device_field::type"));
    assertThat(atCap.getItemsList().stream().noneMatch(FleetSuggestion::hasAddGroupBy)).isTrue();
  }

  @Test
  public void valueOnlyToken_hasNoGroupByRow() {
    // "pixel" is a value of Model, not the name of any key, so nothing is offered to group by.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("pixel"));

    assertThat(response.getItemsCount()).isGreaterThan(0);
    assertThat(response.getItemsList().stream().noneMatch(FleetSuggestion::hasAddGroupBy)).isTrue();
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
            new DeviceCorpus(manyPools, manyPoolsPostings, new AtsCuration()),
            request("group by pool"));

    FleetSuggestion group = firstAddGroupBy(response, "dimension::pool");
    assertThat(group.getLabel()).isEqualTo("Group by");
    assertThat(group.getCountUnit()).isEqualTo("groups");
    assertThat(group.getCount()).isEqualTo(60);
    assertThat(group.getOverMax()).isTrue();
    assertThat(group.getMainTextList())
        .containsExactly(
            TextSegment.newBuilder().setText("Dimension pool").setEmphasized(true).build());
  }

  @Test
  public void emptinessWithActiveChip_opensExistingPickerAndDemotesFullCoverage() {
    // When the key already carries an active chip, an emptiness condition opens the existing
    // picker instead of applying a second chip on the same key.
    FleetSuggestionResponse response =
        suggester.suggest(
            corpus, request("has lab location", simple("dimension::lab_location", "mtv")));

    FleetSuggestion modify = firstOpenPicker(response, "dimension::lab_location");
    assertThat(modify.getLabel()).isEqualTo("Modify Dimension lab_location");
    assertThat(modify.getOpenPicker().hasViewExisting()).isTrue();
    assertThat(modify.getCount()).isEqualTo(2);
  }

  @Test
  public void groupByPrefix_returnsNothingWhenAlreadyAtMaxGroupBys() {
    FleetSuggestionResponse response =
        suggester.suggest(
            corpus,
            request("group by status").toBuilder()
                .addGroupBy("dimension::model")
                .addGroupBy("dimension::pool")
                .addGroupBy("device_field::type")
                .build());

    assertThat(response.getItemsList()).isEmpty();
  }

  @Test
  public void groupByPrefix_exactResolutionRanksAheadOfPrefixMatchAtSamePriority() {
    // dimension::lab_location (3 groups) and dimension::lab_location_zone (2 groups) are both
    // long-tail dimensions with equal priority. Exact resolution (matchRank 0) must outrank prefix
    // match (matchRank 1) even though the prefix match has fewer groups.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("group by lab_location"));

    assertThat(response.getItems(0).getAddGroupBy().getKey()).isEqualTo("dimension::lab_location");
    assertThat(response.getItems(1).getAddGroupBy().getKey())
        .isEqualTo("dimension::lab_location_zone");
  }

  @Test
  public void keyNameContains_matchesDisplayTitleSubstringAndNegatedAndMultiValueConditions() {
    // "supported" appears in the display title "Supported Drivers" (not in bareName "driver").
    FleetSuggestionResponse containsTitle = suggester.suggest(corpus, request("supported"));
    assertThat(firstOpenPicker(containsTitle, "device_field::driver")).isNotNull();

    // "has lab location" (no active chip) produces a no-value filter with pill condition "not
    // empty".
    FleetSuggestionResponse hasLab = suggester.suggest(corpus, request("has lab location"));
    FleetSuggestion hasLabFilter = firstApplyFilter(hasLab, "dimension::lab_location");
    assertThat(hasLabFilter.getApplyFilter().getPillCondition()).isEqualTo("not empty");
    assertThat(hasLabFilter.getCount()).isEqualTo(3);

    // "<key> is not <value>" produces a negated single-value filter with "≠ <value>" pill
    // condition.
    FleetSuggestionResponse negatedKv = suggester.suggest(corpus, request("status is not busy"));
    FleetSuggestion notBusy = firstApplyFilter(negatedKv, "device_field::status");
    assertThat(notBusy.getApplyFilter().getPillCondition()).isEqualTo("\u2260 BUSY");
    assertThat(notBusy.getCount()).isEqualTo(3);

    // Multi-value OR ("pixel, iphone") counts the union across the current scope (3 unfiltered,
    // 2 when filtered to IDLE because device-2 iphone is BUSY) and sets pill condition to "2".
    FleetSuggestionResponse orUnfiltered = suggester.suggest(corpus, request("pixel, iphone"));
    FleetSuggestion orModel = firstApplyFilter(orUnfiltered, "dimension::model");
    assertThat(orModel.getMainTextList())
        .containsExactly(
            TextSegment.newBuilder().setText("Model is ").setEmphasized(false).build(),
            TextSegment.newBuilder().setText("pixel").setEmphasized(true).build(),
            TextSegment.newBuilder().setText(" or ").setEmphasized(false).build(),
            TextSegment.newBuilder().setText("iphone").setEmphasized(true).build())
        .inOrder();
    assertThat(orModel.getApplyFilter().getPillCondition()).isEqualTo("2");
    assertThat(orModel.getCount()).isEqualTo(3);

    // Unknown values in a key-value comma list are filtered out so only present values remain.
    FleetSuggestionResponse partialOr =
        suggester.suggest(corpus, request("model is pixel, unknown_value"));
    assertThat(firstApplyFilter(partialOr, "dimension::model").getApplyFilter().getPillCondition())
        .isEqualTo("pixel");

    FleetSuggestionResponse orFiltered =
        suggester.suggest(corpus, request("pixel, iphone", simple("device_field::status", "IDLE")));
    assertThat(firstApplyFilter(orFiltered, "dimension::model").getCount()).isEqualTo(2);

    // When a multi-value OR or single value has 0 hits in the filtered scope, it is omitted,
    // whereas an uncounted cold long-tail condition is preserved even under active filters.
    assertThat(
            suggester
                .suggest(
                    corpus, request("iphone, dedicated", simple("device_field::status", "IDLE")))
                .getItemsList())
        .isEmpty();
    assertThat(
            suggester
                .suggest(corpus, request("iphone", simple("device_field::status", "IDLE")))
                .getItemsList())
        .isEmpty();
    assertThat(
            suggester
                .suggest(
                    corpus,
                    request("custom_cold_dim is foo", simple("device_field::status", "IDLE")))
                .getItemsList())
        .isNotEmpty();

    // Count descending tie-breaker: IDLE (3 devices) ranks before BUSY (1 device).
    FleetSuggestionResponse statusResponse = suggester.suggest(corpus, request("status"));
    assertThat(statusResponse.getItems(0).getCount()).isEqualTo(3);
    assertThat(statusResponse.getItems(1).getCount()).isEqualTo(1);

    assertThat(SuggestionIntentParser.parse("status"))
        .isEqualTo(
            new SuggestionIntentParser.Intent(
                SuggestionIntentParser.IntentPattern.SINGLE_TOKEN,
                "",
                "",
                false,
                false,
                ImmutableList.of()));
    assertThat(SuggestionIntentParser.parse("no lab location"))
        .isEqualTo(
            new SuggestionIntentParser.Intent(
                SuggestionIntentParser.IntentPattern.EMPTINESS,
                "lab location",
                "",
                true,
                false,
                ImmutableList.of()));
    assertThat(SuggestionIntentParser.parse("lab location is empty"))
        .isEqualTo(
            new SuggestionIntentParser.Intent(
                SuggestionIntentParser.IntentPattern.EMPTINESS,
                "lab location",
                "",
                true,
                false,
                ImmutableList.of()));
    assertThat(SuggestionIntentParser.parse("lab location is not empty"))
        .isEqualTo(
            new SuggestionIntentParser.Intent(
                SuggestionIntentParser.IntentPattern.EMPTINESS,
                "lab location",
                "",
                false,
                false,
                ImmutableList.of()));
    assertThat(SuggestionIntentParser.parse("status != busy"))
        .isEqualTo(
            new SuggestionIntentParser.Intent(
                SuggestionIntentParser.IntentPattern.KEY_VALUE_NEGATED,
                "status",
                "busy",
                false,
                true,
                ImmutableList.of()));
    assertThat(SuggestionIntentParser.parse("status is not"))
        .isEqualTo(
            new SuggestionIntentParser.Intent(
                SuggestionIntentParser.IntentPattern.KEY_WITH_OPERATOR,
                "status",
                "",
                false,
                true,
                ImmutableList.of()));
    assertThat(SuggestionIntentParser.parse("status !="))
        .isEqualTo(
            new SuggestionIntentParser.Intent(
                SuggestionIntentParser.IntentPattern.KEY_WITH_OPERATOR,
                "status",
                "",
                false,
                true,
                ImmutableList.of()));
    assertThat(SuggestionIntentParser.parse("status is"))
        .isEqualTo(
            new SuggestionIntentParser.Intent(
                SuggestionIntentParser.IntentPattern.KEY_WITH_OPERATOR,
                "status",
                "",
                false,
                false,
                ImmutableList.of()));
    assertThat(SuggestionIntentParser.parse("status:"))
        .isEqualTo(
            new SuggestionIntentParser.Intent(
                SuggestionIntentParser.IntentPattern.KEY_WITH_OPERATOR,
                "status",
                "",
                false,
                false,
                ImmutableList.of()));
    assertThat(SuggestionIntentParser.parse("status: busy"))
        .isEqualTo(
            new SuggestionIntentParser.Intent(
                SuggestionIntentParser.IntentPattern.KEY_VALUE,
                "status",
                "busy",
                false,
                false,
                ImmutableList.of()));
    assertThat(SuggestionIntentParser.parse("not busy"))
        .isEqualTo(
            new SuggestionIntentParser.Intent(
                SuggestionIntentParser.IntentPattern.NEGATED_VALUE,
                "",
                "busy",
                false,
                true,
                ImmutableList.of()));
  }

  @Test
  public void groupBy_rendersOnlyEmphasizedKeyInMainText() {
    FleetSuggestionResponse response = suggester.suggest(corpus, request("group by"));

    FleetSuggestion model = firstAddGroupBy(response, "dimension::model");
    assertThat(model.getLabel()).isEqualTo("Group by");
    assertThat(model.getMainTextList())
        .containsExactly(TextSegment.newBuilder().setText("Model").setEmphasized(true).build());
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
        suggester.suggest(
            new DeviceCorpus(fleet, fleetPostings, new AtsCuration()), request("zephyr"));

    assertThat(response.getItemsCount()).isAtLeast(2);
    assertThat(response.getItems(0).getApplyFilter().getResultingFilter().getKey())
        .isEqualTo("dimension::model");
  }

  @Test
  public void emptyQuery_returnsNoSuggestions() {
    FleetSuggestionResponse response = suggester.suggest(corpus, request(""));
    assertThat(response.getItemsList()).isEmpty();
  }

  @Test
  public void emptyFilter_zeroCountCondition_isNotSuggested() {
    // Every device in the fleet has a status, so "status is empty" matches 0 devices and must not
    // be suggested.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("status is empty"));
    for (FleetSuggestion item : response.getItemsList()) {
      if (item.hasApplyFilter()) {
        assertThat(item.getApplyFilter().getResultingFilter().getKey())
            .isNotEqualTo("device_field::status");
      }
    }
  }

  @Test
  public void emptyFilter_partiallyEmptyKey_suggestsEmptyAndNotEmpty() {
    // device-3 lacks lab_location (3 of 4 devices have it), so "lab_location is empty" matches 1
    // device.
    FleetSuggestionResponse emptyResp = suggester.suggest(corpus, request("lab_location is empty"));
    FleetSuggestion emptyItem = firstApplyFilter(emptyResp, "dimension::lab_location");
    assertThat(emptyItem.getCount()).isEqualTo(1);
    assertThat(emptyItem.getApplyFilter().getResultingFilter().getSimple().getNegated()).isFalse();
    assertThat(
            emptyItem.getApplyFilter().getResultingFilter().getSimple().getValues(0).hasNoValue())
        .isTrue();

    // "lab_location is not empty" matches 3 devices.
    FleetSuggestionResponse notEmptyResp =
        suggester.suggest(corpus, request("lab_location is not empty"));
    FleetSuggestion notEmptyItem = firstApplyFilter(notEmptyResp, "dimension::lab_location");
    assertThat(notEmptyItem.getCount()).isEqualTo(3);
    assertThat(notEmptyItem.getApplyFilter().getResultingFilter().getSimple().getNegated())
        .isTrue();
    assertThat(
            notEmptyItem
                .getApplyFilter()
                .getResultingFilter()
                .getSimple()
                .getValues(0)
                .hasNoValue())
        .isTrue();
  }

  @Test
  public void emptyFilter_globallyFullKey_notEmptyIsNotSuggested() {
    // Every device in the fleet has a status globally, so "status is not empty" has zero
    // discrimination globally and must not be suggested (dropped completely).
    FleetSuggestionResponse response = suggester.suggest(corpus, request("status is not empty"));
    for (FleetSuggestion item : response.getItemsList()) {
      if (item.hasApplyFilter()) {
        assertThat(item.getApplyFilter().getResultingFilter().getKey())
            .isNotEqualTo("device_field::status");
      }
    }
  }

  @Test
  public void emptyFilter_filteredContextLocallyFullKey_isSuggestedWithLocalDemotion() {
    // Under active filter pool=dedicated (matches device-2 only), device-2 has lab_location,
    // so in this filtered subset, count == base == 1. Because lab_location is globally
    // discriminative (device-3 lacks it), it is NOT dropped globally; it is suggested with local
    // demotion.
    FleetSuggestionResponse response =
        suggester.suggest(
            corpus, request("lab_location is not empty", simple("dimension::pool", "dedicated")));
    FleetSuggestion item = firstApplyFilter(response, "dimension::lab_location");
    assertThat(item.getCount()).isEqualTo(1);
    assertThat(item.getApplyFilter().getResultingFilter().getSimple().getNegated()).isTrue();
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

  private static FleetSuggestionRequest requestWithGroupBys(String input, String... groupBys) {
    return request(input).toBuilder().addAllGroupBy(ImmutableList.copyOf(groupBys)).build();
  }

  private static Filter simple(String key, String value) {
    return Filter.newBuilder()
        .setKey(key)
        .setSimple(SimpleMatch.newBuilder().addValues(FilterValue.newBuilder().setValue(value)))
        .build();
  }

  @Test
  public void keyMatch_discoveredDimensionInCatalog_suggestsAddFilterDimension() {
    FleetSuggestionResponse response =
        suggester.suggest(
            corpusWithCatalog("build", "carrier"),
            FleetSuggestionRequest.newBuilder().setInput("build").setLimit(5).build());

    FleetSuggestion suggestion = firstOpenPicker(response, "dimension::build");
    assertThat(suggestion.getLabel()).isEqualTo("Add filter");
    assertThat(suggestion.getMainText(0).getText()).isEqualTo("Dimension build");
  }

  @Test
  public void keyMatch_explicitDimensionPrefix_suggestsAddFilterDimension() {
    FleetSuggestionResponse response =
        suggester.suggest(
            corpusWithCatalog("build", "carrier"),
            FleetSuggestionRequest.newBuilder().setInput("dimension build").setLimit(5).build());

    FleetSuggestion suggestion = firstOpenPicker(response, "dimension::build");
    assertThat(suggestion.getLabel()).isEqualTo("Add filter");
    assertThat(suggestion.getMainText(0).getText()).isEqualTo("Dimension build");
  }

  @Test
  public void keyMatch_namespaceColon_withCatalogOnlyOrIndexOnlyDimension() {
    DeviceCorpus withCatalog = corpusWithCatalog("build");

    FleetSuggestionResponse catalogResponse =
        suggester.suggest(
            withCatalog,
            FleetSuggestionRequest.newBuilder()
                .setInput("dimension:build is prod")
                .setFleet(Fleet.FLEET_SELF)
                .build());
    assertThat(firstApplyFilter(catalogResponse, "dimension::build").getLabel())
        .isEqualTo("Add filter");

    FleetSuggestionResponse indexResponse =
        suggester.suggest(
            withCatalog,
            FleetSuggestionRequest.newBuilder()
                .setInput("dimension:model is pixel")
                .setFleet(Fleet.FLEET_SELF)
                .build());
    assertThat(firstApplyFilter(indexResponse, "dimension::model").getLabel())
        .isEqualTo("Add filter");
  }

  @Test
  public void keyMatch_catalogOnlyDimension_prefixMatch() {
    FleetSuggestionResponse response =
        suggester.suggest(
            corpusWithCatalog("screen_density", "big_screen"),
            FleetSuggestionRequest.newBuilder()
                .setInput("screen")
                .setFleet(Fleet.FLEET_SELF)
                .build());

    // A prefix match ("screen_density") ranks before a substring match ("big_screen").
    assertThat(response.getItems(0).getMainText(0).getText()).isEqualTo("Dimension screen_density");
    assertThat(response.getItems(1).getMainText(0).getText()).isEqualTo("Dimension big_screen");
  }

  @Test
  public void keyMatch_catalogOnlyDimension_namespaceMatch() {
    FleetSuggestionResponse response =
        suggester.suggest(
            corpusWithCatalog("screen_density"),
            FleetSuggestionRequest.newBuilder()
                .setInput("device dimension screen_density")
                .setFleet(Fleet.FLEET_SELF)
                .build());

    // The namespaced spelling resolves exactly even though the normalized text
    // ("device_dimension_screen_density") matches no display or bare name by prefix or substring,
    // so only the catalog can make this key discoverable.
    assertThat(response.getItemsList()).isNotEmpty();
    FleetSuggestion suggestion = firstOpenPicker(response, "dimension::screen_density");
    assertThat(suggestion.getMainText(0).getText()).isEqualTo("Dimension screen_density");
  }

  @Test
  public void keyMatch_catalogOnlyDimension_substringMatch() {
    FleetSuggestionResponse response =
        suggester.suggest(
            corpusWithCatalog("screen_density"),
            FleetSuggestionRequest.newBuilder()
                .setInput("density")
                .setFleet(Fleet.FLEET_SELF)
                .build());

    FleetSuggestion suggestion = firstOpenPicker(response, "dimension::screen_density");
    assertThat(suggestion.getLabel()).isEqualTo("Add filter");
    assertThat(suggestion.getMainText(0).getText()).isEqualTo("Dimension screen_density");
  }

  /** The test corpus with the given dimension names discovered fleet-wide but not indexed. */
  private DeviceCorpus corpusWithCatalog(String... dimensionNames) {
    return new DeviceCorpus(
        snapshot,
        postings,
        new AtsCuration(),
        OverlayView.empty(),
        ImmutableSet.copyOf(dimensionNames));
  }

  @Test
  public void kv_unknownDimensionBareToken_suggestsAddFilterWithoutCount() {
    // "custom_tag is special" where custom_tag is not in the index and not in the catalog.
    // Falls back to synthesizing a dimension filter without count.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("custom_tag is special"));

    FleetSuggestion suggestion = firstApplyFilter(response, "dimension::custom_tag");
    assertThat(suggestion.getLabel()).isEqualTo("Add filter");
    assertThat(suggestion.getMainText(0).getText()).isEqualTo("Dimension custom_tag is ");
    assertThat(suggestion.getMainText(1).getText()).isEqualTo("special");
    assertThat(suggestion.getApplyFilter().getResultingFilter().getSimple().getValues(0).getValue())
        .isEqualTo("special");
    assertThat(suggestion.getApplyFilter().getResultingFilter().getSimple().getNegated()).isFalse();
    assertThat(suggestion.hasCount()).isFalse();
  }

  @Test
  public void kv_unknownDimensionNegatedBareToken_suggestsAddFilterWithoutCount() {
    FleetSuggestionResponse response = suggester.suggest(corpus, request("custom_tag != special"));

    FleetSuggestion suggestion = firstApplyFilter(response, "dimension::custom_tag");
    assertThat(suggestion.getLabel()).isEqualTo("Add filter");
    assertThat(suggestion.getMainText(0).getText()).isEqualTo("Dimension custom_tag is not ");
    assertThat(suggestion.getMainText(1).getText()).isEqualTo("special");
    assertThat(suggestion.getApplyFilter().getResultingFilter().getSimple().getValues(0).getValue())
        .isEqualTo("special");
    assertThat(suggestion.getApplyFilter().getResultingFilter().getSimple().getNegated()).isTrue();
    assertThat(suggestion.hasCount()).isFalse();
  }

  @Test
  public void kv_unknownDimensionExplicitPrefix_suggestsAddFilterWithoutCount() {
    FleetSuggestionResponse response =
        suggester.suggest(corpus, request("dimension:my_dim is my_val"));

    FleetSuggestion suggestion = firstApplyFilter(response, "dimension::my_dim");
    assertThat(suggestion.getLabel()).isEqualTo("Add filter");
    assertThat(suggestion.getMainText(0).getText()).isEqualTo("Dimension my_dim is ");
    assertThat(suggestion.getMainText(1).getText()).isEqualTo("my_val");
    assertThat(suggestion.getApplyFilter().getResultingFilter().getSimple().getValues(0).getValue())
        .isEqualTo("my_val");
    assertThat(suggestion.hasCount()).isFalse();
  }

  @Test
  public void kv_unknownDimensionCommaSeparated_suggestsAddFilterWithoutCount() {
    FleetSuggestionResponse response = suggester.suggest(corpus, request("custom_tag is foo, bar"));

    FleetSuggestion suggestion = firstApplyFilter(response, "dimension::custom_tag");
    assertThat(suggestion.getLabel()).isEqualTo("Add filter");
    assertThat(suggestion.getMainText(0).getText()).isEqualTo("Dimension custom_tag is ");
    assertThat(suggestion.getMainText(1).getText()).isEqualTo("foo, bar");
    assertThat(suggestion.getApplyFilter().getResultingFilter().getSimple().getValuesList())
        .containsExactly(
            FilterValue.newBuilder().setValue("foo").build(),
            FilterValue.newBuilder().setValue("bar").build())
        .inOrder();
    assertThat(suggestion.hasCount()).isFalse();
  }

  @Test
  public void kv_unknownDimensionOnlyCommas_doesNotSuggestEmptyFilter() {
    FleetSuggestionResponse response = suggester.suggest(corpus, request("custom_tag is ,"));
    for (FleetSuggestion item : response.getItemsList()) {
      if (item.hasApplyFilter()) {
        assertThat(item.getApplyFilter().getResultingFilter().getKey())
            .isNotEqualTo("dimension::custom_tag");
      }
    }
  }

  @Test
  public void deviceSearch_neverLeaksHostOnlyKeysOrColdFallbackOnIndexedKeys() {
    // 1. Host-only alias "device count" must never resolve to host_field::device_count on
    // DeviceCorpus.
    FleetSuggestionResponse deviceCountKv = suggester.suggest(corpus, request("device count is 5"));
    for (FleetSuggestion item : deviceCountKv.getItemsList()) {
      if (item.hasApplyFilter()) {
        assertThat(item.getApplyFilter().getResultingFilter().getKey())
            .isNotEqualTo("host_field::device_count");
      }
    }

    // 2. Built-in indexed field "device_field::status" with an unmatched value must not emit a
    // cold long-tail fallback suggestion.
    FleetSuggestionResponse bogusStatusKv =
        suggester.suggest(corpus, request("status is nonexistent_status"));
    assertThat(bogusStatusKv.getItemsList()).isEmpty();
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
                .addDriver("AndroidRealDeviceDriver")
                .addOwner("alice")
                .addOwner("bob")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("model", "pixel"))
                        .addSupportedDimension(dimension("pool", "shared"))
                        .addSupportedDimension(dimension("lab_location", "mtv"))
                        .addSupportedDimension(dimension("lab_location_zone", "zone1"))))
        .build();
  }

  private static DeviceInfo device1() {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-1"))
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("android_real_device")
                .addDriver("AndroidRealDeviceDriver")
                .addOwner("alice")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(dimension("model", "pixel"))
                        .addSupportedDimension(dimension("pool", "shared"))
                        .addSupportedDimension(dimension("lab_location", "mtv"))
                        .addSupportedDimension(dimension("lab_location_zone", "zone1"))))
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
                        .addSupportedDimension(dimension("lab_location", "nyc"))
                        .addSupportedDimension(dimension("lab_location_zone", "zone2"))))
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
                        .addSupportedDimension(dimension("pool", "shared"))
                        .addSupportedDimension(dimension("lab_location_zone", "zone2"))))
        .build();
  }

  private static DeviceDimension dimension(String name, String value) {
    return DeviceDimension.newBuilder().setName(name).setValue(value).build();
  }
}
