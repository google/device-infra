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

import static com.google.common.collect.ImmutableList.toImmutableList;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Cell;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Column;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ComplexMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestion;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetUtilization;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.NoValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.StartsWith;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TextSegment;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.devtools.mobileharness.fe.v6.service.search.index.OverlayView;
import com.google.devtools.mobileharness.fe.v6.service.search.index.Postings;
import com.google.devtools.mobileharness.fe.v6.service.search.query.AtsCuration;
import com.google.devtools.mobileharness.fe.v6.service.search.query.DeviceCorpus;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetFilterEngine;
import com.google.devtools.mobileharness.fe.v6.service.search.query.KeyVocabulary;
import com.google.devtools.mobileharness.fe.v6.service.search.query.ScenarioCuration;
import com.google.devtools.mobileharness.fe.v6.service.search.query.SearchCorpus;
import com.google.inject.Guice;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
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
    FleetFilterEngine filterEngine = Guice.createInjector().getInstance(FleetFilterEngine.class);
    FleetSuggestionResponse response = suggester.suggest(corpus, request("device-"));

    FleetSuggestion collapsed = firstApplyFilter(response, "device_field::uuid");
    assertThat(collapsed.getCount()).isEqualTo(4);
    assertThat(collapsed.getMainTextList())
        .containsExactly(
            TextSegment.newBuilder().setText("UUID starts with ").setEmphasized(false).build(),
            TextSegment.newBuilder().setText("device-").setEmphasized(true).build())
        .inOrder();
    assertThat(
            collapsed.getApplyFilter().getResultingFilter().getComplex().getStartsWith().getValue())
        .isEqualTo("device-");
    assertThat(collapsed.getApplyFilter().getPillKey()).isEqualTo("UUID");
    assertThat(collapsed.getApplyFilter().getPillCondition()).isEqualTo("starts with device-");
    assertThat(
            filterEngine.match(
                corpus, ImmutableList.of(collapsed.getApplyFilter().getResultingFilter())))
        .hasSize(collapsed.getCount());

    // With an active chip on another key, the collapsed row's count reflects the active filter
    // scope and matches what FleetFilterEngine produces when resulting_filter is applied.
    Filter busyChip = simple("device_field::status", "BUSY");
    FleetSuggestionResponse withBusyChip = suggester.suggest(corpus, request("device-", busyChip));
    FleetSuggestion busyCollapsed = firstApplyFilter(withBusyChip, "device_field::uuid");
    assertThat(busyCollapsed.getCount()).isEqualTo(1);
    assertThat(
            filterEngine.match(
                corpus,
                ImmutableList.of(busyChip, busyCollapsed.getApplyFilter().getResultingFilter())))
        .containsExactly(2);

    // A quoted or uppercase prefix preserves the stripped case in the displayed text and filter
    // while matching case-insensitively against the lowercased index.
    FleetSuggestionResponse quotedUppercase = suggester.suggest(corpus, request("'DEVICE-'"));
    FleetSuggestion quotedCollapsed = firstApplyFilter(quotedUppercase, "device_field::uuid");
    assertThat(quotedCollapsed.getCount()).isEqualTo(4);
    assertThat(quotedCollapsed.getMainTextList())
        .containsExactly(
            TextSegment.newBuilder().setText("UUID starts with ").setEmphasized(false).build(),
            TextSegment.newBuilder().setText("DEVICE-").setEmphasized(true).build())
        .inOrder();
    assertThat(
            quotedCollapsed
                .getApplyFilter()
                .getResultingFilter()
                .getComplex()
                .getStartsWith()
                .getValue())
        .isEqualTo("DEVICE-");
    assertThat(quotedCollapsed.getApplyFilter().getPillCondition())
        .isEqualTo("starts with DEVICE-");
    assertThat(
            filterEngine.match(
                corpus, ImmutableList.of(quotedCollapsed.getApplyFilter().getResultingFilter())))
        .hasSize(4);

    // When the identifier key already carries an active chip, or when the prefix is negated, the
    // collapsed "starts with" row is suppressed.
    FleetSuggestionResponse withUuidChip =
        suggester.suggest(corpus, request("device-", simple("device_field::uuid", "device-0")));
    assertThat(
            withUuidChip.getItemsList().stream()
                .noneMatch(
                    item ->
                        item.hasApplyFilter()
                            && item.getApplyFilter().getResultingFilter().hasComplex()))
        .isTrue();

    FleetSuggestionResponse negated = suggester.suggest(corpus, request("not device-"));
    assertThat(
            negated.getItemsList().stream()
                .noneMatch(
                    item ->
                        item.hasApplyFilter()
                            && item.getApplyFilter().getResultingFilter().hasComplex()))
        .isTrue();
  }

  @Test
  public void uuidValue_suggestsDeviceIdFilter() {
    // Typing a device UUID resolves through ordinary value search onto the UUID key, no dedicated
    // identifier detector. When the exact ID is the only prefix match, no redundant "starts with"
    // row is emitted.
    FleetSuggestionResponse response = suggester.suggest(corpus, request("device-2"));

    FleetSuggestion uuid = firstApplyFilter(response, "device_field::uuid");
    assertThat(uuid.getApplyFilter().getResultingFilter().getSimple().getValues(0).getValue())
        .isEqualTo("device-2");
    assertThat(uuid.getCount()).isEqualTo(1);
    assertThat(
            response.getItemsList().stream()
                .filter(
                    item ->
                        item.hasApplyFilter()
                            && item.getApplyFilter()
                                .getResultingFilter()
                                .getKey()
                                .equals("device_field::uuid")))
        .hasSize(1);

    // When an exact ID also shares a prefix with other IDs ("device-1" and "device-10".."device-19"
    // in a 60-device fleet), both the exact match and the collapsed "starts with" row are offered.
    FleetSnapshot manyPools =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(manyPoolFleet(60), BUILD_TIME);
    DeviceCorpus manyCorpus =
        new DeviceCorpus(manyPools, new LazyPostings(manyPools.devices()), new AtsCuration());
    FleetSuggestionResponse prefixAndExact = suggester.suggest(manyCorpus, request("device-1"));
    ImmutableList<FleetSuggestion> uuidSuggestions =
        prefixAndExact.getItemsList().stream()
            .filter(
                item ->
                    item.hasApplyFilter()
                        && item.getApplyFilter()
                            .getResultingFilter()
                            .getKey()
                            .equals("device_field::uuid"))
            .collect(toImmutableList());
    assertThat(uuidSuggestions).hasSize(2);
    assertThat(uuidSuggestions.get(0).getCount()).isEqualTo(1);
    assertThat(
            uuidSuggestions
                .get(0)
                .getApplyFilter()
                .getResultingFilter()
                .getSimple()
                .getValues(0)
                .getValue())
        .isEqualTo("device-1");
    assertThat(uuidSuggestions.get(1).getCount()).isEqualTo(11);
    assertThat(
            uuidSuggestions
                .get(1)
                .getApplyFilter()
                .getResultingFilter()
                .getComplex()
                .getStartsWith()
                .getValue())
        .isEqualTo("device-1");
    assertThat(uuidSuggestions.get(1).getApplyFilter().getPillCondition())
        .isEqualTo("starts with device-1");

    // When an active filter narrows the scope to only device-1, the collapsed "starts with" row
    // repeats the exact row and is suppressed; when only device-10 is in scope, the collapsed row
    // stays with count 1; when neither is in scope, both rows are omitted.
    FleetSuggestionResponse onlyExactInScope =
        suggester.suggest(manyCorpus, request("device-1", simple("dimension::pool", "pool-1")));
    ImmutableList<FleetSuggestion> onlyExactUuidSuggestions =
        onlyExactInScope.getItemsList().stream()
            .filter(
                item ->
                    item.hasApplyFilter()
                        && item.getApplyFilter()
                            .getResultingFilter()
                            .getKey()
                            .equals("device_field::uuid"))
            .collect(toImmutableList());
    assertThat(onlyExactUuidSuggestions).hasSize(1);
    assertThat(onlyExactUuidSuggestions.get(0).getCount()).isEqualTo(1);
    assertThat(
            onlyExactUuidSuggestions
                .get(0)
                .getApplyFilter()
                .getResultingFilter()
                .getSimple()
                .getValues(0)
                .getValue())
        .isEqualTo("device-1");

    FleetSuggestionResponse onlyExtendedInScope =
        suggester.suggest(manyCorpus, request("device-1", simple("dimension::pool", "pool-10")));
    FleetSuggestion extendedCollapsed = firstApplyFilter(onlyExtendedInScope, "device_field::uuid");
    assertThat(extendedCollapsed.getCount()).isEqualTo(1);
    assertThat(extendedCollapsed.getApplyFilter().getResultingFilter().hasComplex()).isTrue();

    FleetSuggestionResponse noneInScope =
        suggester.suggest(manyCorpus, request("device-1", simple("dimension::pool", "pool-50")));
    assertThat(
            noneInScope.getItemsList().stream()
                .noneMatch(
                    item ->
                        item.hasApplyFilter()
                            && item.getApplyFilter()
                                .getResultingFilter()
                                .getKey()
                                .equals("device_field::uuid")))
        .isTrue();
  }

  @Test
  public void identifierDimensionPrefix_countsMatchingRecordsWithoutBuildingPostings() {
    // device-0 carries two iccids ("8901-a", "8901-b") and testbed_name "tb-alpha"; device-1
    // carries testbed_name "tb-alpha"; BUSY device-2 carries testbed_name "tb-alpha-2". Unfiltered
    // "device-" answers from index value counts without scanning records, whereas unfiltered
    // "8901-" on multi-valued iccids scans dimension::iccids once to count matching records (1,
    // not 2). Neither builds posting lists.
    ImmutableList<DeviceInfo> devices =
        ImmutableList.of(
            deviceWith(
                "device-0",
                DeviceStatus.IDLE,
                dimension("iccids", "8901-a"),
                dimension("iccids", "8901-b"),
                dimension("testbed_name", "tb-alpha")),
            deviceWith("device-1", DeviceStatus.IDLE, dimension("testbed_name", "tb-alpha")),
            deviceWith("device-2", DeviceStatus.BUSY, dimension("testbed_name", "tb-alpha-2")));
    FleetSnapshot idDimSnapshot =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(labOf(devices), BUILD_TIME);
    Set<String> postingKeysScanned = new HashSet<>();
    LazyPostings trackingPostings =
        new LazyPostings(
            new LazyPostings.RecordValues() {
              @Override
              public int recordCount() {
                return idDimSnapshot.devices().size();
              }

              @Override
              public ImmutableSet<String> valuesForKey(int index, String keyId) {
                postingKeysScanned.add(keyId);
                return idDimSnapshot.devices().get(index).normalizedValues(keyId);
              }
            });
    TrackingCorpus tracking =
        new TrackingCorpus(new DeviceCorpus(idDimSnapshot, trackingPostings, new AtsCuration()));

    FleetSuggestion uuidCollapsed =
        firstApplyFilter(suggester.suggest(tracking, request("device-")), "device_field::uuid");
    assertThat(uuidCollapsed.getCount()).isEqualTo(3);
    assertThat(tracking.keysScannedInScope).isEmpty();
    assertThat(postingKeysScanned).isEmpty();

    FleetSuggestion iccidsCollapsed =
        suggester.suggest(tracking, request("8901-")).getItemsList().stream()
            .filter(
                item ->
                    item.hasApplyFilter()
                        && item.getApplyFilter()
                            .getResultingFilter()
                            .getKey()
                            .equals("dimension::iccids")
                        && item.getApplyFilter().getResultingFilter().hasComplex())
            .findFirst()
            .orElseThrow();
    assertThat(iccidsCollapsed.getCount()).isEqualTo(1);
    assertThat(iccidsCollapsed.getApplyFilter().getPillCondition()).isEqualTo("starts with 8901-");
    assertThat(tracking.keysScannedInScope).containsExactly("dimension::iccids");
    assertThat(postingKeysScanned).isEmpty();

    // An exact multi-valued identifier ("8901-a") with no longer prefix extensions skips
    // scopePrefixCount via the pre-count guard without scanning records in scope.
    tracking.keysScannedInScope.clear();
    FleetSuggestionResponse exactIccid = suggester.suggest(tracking, request("8901-a"));
    ImmutableList<FleetSuggestion> iccidSuggestions =
        exactIccid.getItemsList().stream()
            .filter(
                item ->
                    item.hasApplyFilter()
                        && item.getApplyFilter()
                            .getResultingFilter()
                            .getKey()
                            .equals("dimension::iccids"))
            .collect(toImmutableList());
    assertThat(iccidSuggestions).hasSize(1);
    assertThat(iccidSuggestions.get(0).getCount()).isEqualTo(1);
    assertThat(iccidSuggestions.get(0).getApplyFilter().getResultingFilter().hasSimple()).isTrue();
    assertThat(tracking.keysScannedInScope).isEmpty();
    assertThat(postingKeysScanned).isEmpty();

    // Under status=IDLE, "tb-alpha" has a longer global extension ("tb-alpha-2" on BUSY device-2,
    // so hiIndex - loIndex == 2), but all 2 in-scope matches equal "tb-alpha", so the post-count
    // guard suppresses the redundant "starts with" row.
    FleetSuggestionResponse sharedTestbedExact =
        suggester.suggest(tracking, request("tb-alpha", simple("device_field::status", "IDLE")));
    ImmutableList<FleetSuggestion> testbedSuggestions =
        sharedTestbedExact.getItemsList().stream()
            .filter(
                item ->
                    item.hasApplyFilter()
                        && item.getApplyFilter()
                            .getResultingFilter()
                            .getKey()
                            .equals("dimension::testbed_name"))
            .collect(toImmutableList());
    assertThat(testbedSuggestions).hasSize(1);
    assertThat(testbedSuggestions.get(0).getCount()).isEqualTo(2);
    assertThat(testbedSuggestions.get(0).getApplyFilter().getResultingFilter().hasSimple())
        .isTrue();
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
    assertThat(keyOnly.getOpenPicker().getMetadata().getDescription()).isEmpty();
    // The value conditions for the same key are also present (IDLE, BUSY).
    assertThat(firstApplyFilter(response, "device_field::status")).isNotNull();
  }

  @Test
  public void versionSuggestions_carryKeyDescriptionInMetadata() {
    FleetSnapshot versionSnapshot =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(
                labOf(
                    ImmutableList.of(
                        deviceWith(
                            "device-0",
                            DeviceStatus.IDLE,
                            dimension("release_version", "16"),
                            dimension("sdk_version", "36"),
                            dimension("software_version", "16")))),
                BUILD_TIME);
    DeviceCorpus versionCorpus =
        new DeviceCorpus(
            versionSnapshot, new LazyPostings(versionSnapshot.devices()), new AtsCuration());

    FleetSuggestionResponse response = suggester.suggest(versionCorpus, request("version"));

    ImmutableList<String> openPickerKeys =
        response.getItemsList().stream()
            .filter(FleetSuggestion::hasOpenPicker)
            .map(s -> s.getOpenPicker().getKey())
            .collect(toImmutableList());
    assertThat(openPickerKeys.get(0)).isEqualTo("device_field::version");
    assertThat(openPickerKeys)
        .containsAtLeast(
            "device_field::version", "dimension::sdk_version", "dimension::software_version")
        .inOrder();
    FleetSuggestion openPicker = firstOpenPicker(response, "device_field::version");
    assertThat(openPicker.getOpenPicker().getMetadata().getDescription()).isNotEmpty();
    FleetSuggestion applyFilter = firstApplyFilter(response, "device_field::version");
    assertThat(applyFilter.getApplyFilter().getMetadata().getDescription())
        .isEqualTo(openPicker.getOpenPicker().getMetadata().getDescription());
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
    assertThat(response.getItemsList().stream().filter(FleetSuggestion::hasAddGroupBy)).hasSize(1);
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
  public void modifyExistingChip_usesPlusCountPrefixAndAppliesMergedFilterDirectly() {
    FleetFilterEngine filterEngine = Guice.createInjector().getInstance(FleetFilterEngine.class);
    // A chip already filters model=pixel. Typing another model value offers a one-click modify that
    // merges "nexus" into the existing chip with a "+" delta count and "Modify filter" label.
    FleetSuggestionResponse response =
        suggester.suggest(corpus, request("nexus", simple("dimension::model", "pixel")));

    FleetSuggestion modify = firstApplyFilter(response, "dimension::model");
    assertThat(modify.getLabel()).isEqualTo("Modify filter");
    assertThat(modify.hasOpenPicker()).isFalse();
    assertThat(modify.getCountPrefix()).isEqualTo("+");
    // device-3 (model nexus) is the one device the modification would add.
    assertThat(modify.getCount()).isEqualTo(1);
    assertThat(modify.getMainTextList())
        .containsExactly(
            TextSegment.newBuilder().setText("Model: add ").setEmphasized(false).build(),
            TextSegment.newBuilder().setText("nexus").setEmphasized(true).build())
        .inOrder();
    assertThat(modify.getApplyFilter().getPillKey()).isEqualTo("Model");
    assertThat(modify.getApplyFilter().getPillCondition()).isEqualTo("2");
    assertThat(modify.getApplyFilter().getResultingFilter().getSimple().getValuesList())
        .containsExactly(
            FilterValue.newBuilder().setValue("pixel").build(),
            FilterValue.newBuilder().setValue("nexus").build())
        .inOrder();
    assertThat(
            filterEngine.match(
                corpus, ImmutableList.of(modify.getApplyFilter().getResultingFilter())))
        .containsExactly(0, 1, 3);

    // A value already present in the active chip is suppressed rather than re-suggested.
    assertThat(
            suggester
                .suggest(corpus, request("model is pixel", simple("dimension::model", "pixel")))
                .getItemsList())
        .isEmpty();

    // When a key carries a complex chip (such as UUID starts with device-), typing the key name
    // offers "Modify filter  UUID" as a view_existing picker opener without repeating the key name.
    Filter uuidPrefixChip =
        Filter.newBuilder()
            .setKey("device_field::uuid")
            .setComplex(
                ComplexMatch.newBuilder()
                    .setStartsWith(StartsWith.newBuilder().setValue("device-")))
            .build();
    FleetSuggestionResponse uuidKeyResp =
        suggester.suggest(corpus, request("uuid", uuidPrefixChip));
    FleetSuggestion uuidPicker = firstOpenPicker(uuidKeyResp, "device_field::uuid");
    assertThat(uuidPicker.getLabel()).isEqualTo("Modify filter");
    assertThat(uuidPicker.getOpenPicker().hasViewExisting()).isTrue();
    assertThat(uuidPicker.getMainTextList())
        .containsExactly(TextSegment.newBuilder().setText("UUID").setEmphasized(false).build());
    assertThat(uuidKeyResp.getItemsList().stream().noneMatch(FleetSuggestion::hasApplyFilter))
        .isTrue();

    // A complex chip on a non-identifier key also conflicts with positive single-value, OR, and
    // emptiness merges, suppressing value conditions and falling back to the picker on emptiness.
    Filter modelPrefixChip =
        Filter.newBuilder()
            .setKey("dimension::model")
            .setComplex(
                ComplexMatch.newBuilder().setStartsWith(StartsWith.newBuilder().setValue("pix")))
            .build();
    assertThat(suggester.suggest(corpus, request("model is nexus", modelPrefixChip)).getItemsList())
        .isEmpty();
    assertThat(
            suggester
                .suggest(corpus, request("model is nexus, iphone", modelPrefixChip))
                .getItemsList())
        .isEmpty();
    Filter labPrefixChip =
        Filter.newBuilder()
            .setKey("dimension::lab_location")
            .setComplex(
                ComplexMatch.newBuilder().setStartsWith(StartsWith.newBuilder().setValue("m")))
            .build();
    FleetSuggestionResponse complexEmptyResp =
        suggester.suggest(corpus, request("no lab location", labPrefixChip));
    assertThat(
            firstOpenPicker(complexEmptyResp, "dimension::lab_location")
                .getOpenPicker()
                .hasViewExisting())
        .isTrue();
    assertThat(complexEmptyResp.getItemsList().stream().noneMatch(FleetSuggestion::hasApplyFilter))
        .isTrue();

    // Merging a concrete value onto an existing (no value) chip collapses pillCondition to "2".
    Filter emptyLabChip =
        Filter.newBuilder()
            .setKey("dimension::lab_location")
            .setSimple(
                SimpleMatch.newBuilder()
                    .addValues(FilterValue.newBuilder().setNoValue(NoValue.getDefaultInstance())))
            .build();
    FleetSuggestion addMtvToEmpty =
        firstApplyFilter(
            suggester.suggest(corpus, request("lab_location is mtv", emptyLabChip)),
            "dimension::lab_location");
    assertThat(addMtvToEmpty.getLabel()).isEqualTo("Modify filter");
    assertThat(addMtvToEmpty.getCountPrefix()).isEqualTo("+");
    assertThat(addMtvToEmpty.getCount()).isEqualTo(2);
    assertThat(addMtvToEmpty.getApplyFilter().getPillCondition()).isEqualTo("2");
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

    // A substring value match over the same 60-pool corpus caps the contains scan at 24 hits.
    DeviceCorpus manyPoolsCorpus =
        new DeviceCorpus(manyPools, manyPoolsPostings, new AtsCuration());
    assertThat(suggester.suggest(manyPoolsCorpus, request("pool is -")).getItemsCount())
        .isEqualTo(12);
  }

  @Test
  public void emptinessWithActiveChip_mergesCompatibleSimpleChipAndFallsBackToPickerOnConflict() {
    // 1. Conflicting polarity ("has lab location" requires negated=true, while lab_location=mtv is
    // negated=false): offers the bare-key picker opener ("Modify filter  Dimension lab_location").
    FleetSuggestionResponse conflictResp =
        suggester.suggest(
            corpus, request("has lab location", simple("dimension::lab_location", "mtv")));

    FleetSuggestion conflictPicker = firstOpenPicker(conflictResp, "dimension::lab_location");
    assertThat(conflictPicker.getLabel()).isEqualTo("Modify filter");
    assertThat(conflictPicker.getOpenPicker().hasViewExisting()).isTrue();
    assertThat(conflictPicker.hasCount()).isFalse();
    assertThat(conflictPicker.getMainTextList())
        .containsExactly(
            TextSegment.newBuilder()
                .setText("Dimension lab_location")
                .setEmphasized(false)
                .build());

    // 2. Compatible include ("no lab location" merges (no value) into lab_location=mtv, adding
    // device-3 as +1, both when lab_location=mtv is the sole chip and alongside status=IDLE; under
    // status=BUSY, device-3 is excluded by status so the delta is 0 and the row is omitted).
    FleetSuggestion mergeIncludeSingle =
        firstApplyFilter(
            suggester.suggest(
                corpus, request("no lab location", simple("dimension::lab_location", "mtv"))),
            "dimension::lab_location");
    assertThat(mergeIncludeSingle.getLabel()).isEqualTo("Modify filter");
    assertThat(mergeIncludeSingle.getCountPrefix()).isEqualTo("+");
    assertThat(mergeIncludeSingle.getCount()).isEqualTo(1);
    assertThat(mergeIncludeSingle.getMainTextList())
        .containsExactly(
            TextSegment.newBuilder()
                .setText("Dimension lab_location: add ")
                .setEmphasized(false)
                .build(),
            TextSegment.newBuilder().setText("(no value)").setEmphasized(true).build())
        .inOrder();
    assertThat(mergeIncludeSingle.getApplyFilter().getPillCondition()).isEqualTo("2");

    // When the active chip is the sole filter, modifyIncludeEmptyDelta computes the delta from
    // recordCount - globalPresenceCount without fetching postings.forKey(keyId).
    TrackingCorpus singleChipTracking = new TrackingCorpus(corpus);
    FleetSuggestion trackedEmptyMerge =
        firstApplyFilter(
            suggester.suggest(
                singleChipTracking,
                request("no lab location", simple("dimension::lab_location", "mtv"))),
            "dimension::lab_location");
    assertThat(trackedEmptyMerge.getCount()).isEqualTo(1);
    assertThat(singleChipTracking.postingsForKeyCalls).isEqualTo(0);

    FleetSuggestion mergeIncludeMultiChip =
        firstApplyFilter(
            suggester.suggest(
                corpus,
                request(
                    "no lab location",
                    simple("dimension::lab_location", "mtv"),
                    simple("device_field::status", "IDLE"))),
            "dimension::lab_location");
    assertThat(mergeIncludeMultiChip.getCountPrefix()).isEqualTo("+");
    assertThat(mergeIncludeMultiChip.getCount()).isEqualTo(1);

    assertThat(
            suggester
                .suggest(
                    corpus,
                    request(
                        "no lab location",
                        simple("dimension::lab_location", "mtv"),
                        simple("device_field::status", "BUSY")))
                .getItemsList())
        .isEmpty();
    assertThat(
            suggester
                .suggest(corpus, request("no lab location", simple("device_field::status", "BUSY")))
                .getItemsList())
        .isEmpty();

    // 3. Compatible exclude ("has lab location" merges (no value) into negated lab_location!=mtv,
    // excluding device-3 and leaving device-2 nyc with count 1).
    Filter excludeMtv =
        Filter.newBuilder()
            .setKey("dimension::lab_location")
            .setSimple(
                SimpleMatch.newBuilder()
                    .setNegated(true)
                    .addValues(FilterValue.newBuilder().setValue("mtv")))
            .build();
    FleetSuggestion mergeExclude =
        firstApplyFilter(
            suggester.suggest(corpus, request("has lab location", excludeMtv)),
            "dimension::lab_location");
    assertThat(mergeExclude.getLabel()).isEqualTo("Modify filter");
    assertThat(mergeExclude.getCountPrefix()).isEmpty();
    assertThat(mergeExclude.getCount()).isEqualTo(1);
    assertThat(mergeExclude.getMainTextList())
        .containsExactly(
            TextSegment.newBuilder()
                .setText("Dimension lab_location: exclude ")
                .setEmphasized(false)
                .build(),
            TextSegment.newBuilder().setText("(no value)").setEmphasized(true).build())
        .inOrder();
    assertThat(mergeExclude.getApplyFilter().getPillCondition()).isEqualTo("\u2260 2");

    // When every record in the current scope already carries lab_location (lab_location!=mtv plus
    // status=BUSY leaves only device-2 nyc), excluding (no value) covers the whole scope (1 == 1)
    // and still applies directly; when every present value is already excluded (lab_location!=mtv,
    // nyc leaves only device-3 without lab_location), present == 0 omits the row.
    FleetSuggestion mergeExcludeFullCoverage =
        firstApplyFilter(
            suggester.suggest(
                corpus,
                request("has lab location", excludeMtv, simple("device_field::status", "BUSY"))),
            "dimension::lab_location");
    assertThat(mergeExcludeFullCoverage.getCount()).isEqualTo(1);
    Filter excludeAllPresent =
        Filter.newBuilder()
            .setKey("dimension::lab_location")
            .setSimple(
                SimpleMatch.newBuilder()
                    .setNegated(true)
                    .addValues(FilterValue.newBuilder().setValue("mtv"))
                    .addValues(FilterValue.newBuilder().setValue("nyc")))
            .build();
    assertThat(
            suggester
                .suggest(corpus, request("has lab location", excludeAllPresent))
                .getItemsList())
        .isEmpty();

    // When the chip already contains (no value), repeating "no lab location" falls back to the
    // bare-key picker opener instead of appending a duplicate NoValue entry.
    Filter alreadyEmpty =
        Filter.newBuilder()
            .setKey("dimension::lab_location")
            .setSimple(
                SimpleMatch.newBuilder()
                    .addValues(FilterValue.newBuilder().setNoValue(NoValue.getDefaultInstance())))
            .build();
    FleetSuggestion duplicateNoValueFallback =
        firstOpenPicker(
            suggester.suggest(corpus, request("no lab location", alreadyEmpty)),
            "dimension::lab_location");
    assertThat(duplicateNoValueFallback.getOpenPicker().hasViewExisting()).isTrue();
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
  public void groupByPrefix_suggestsColdLongTailDimensionsAndDropsUnindexedBuiltIns() {
    DeviceCorpus withCatalog =
        corpusWithCatalog("lab_location_extra_b", "lab_location_extra_a", "Monsoon_Status");

    // Prefix match on "lab_location" finds the exact indexed dimension (matchRank 0, counted), the
    // indexed prefix match (matchRank 1, counted), and the two cold catalog prefix matches
    // (matchRank 1, uncounted, ordered alphabetically after the counted match).
    FleetSuggestionResponse response =
        suggester.suggest(withCatalog, request("group by lab_location"));
    assertThat(response.getItemsList().stream().map(item -> item.getAddGroupBy().getKey()).toList())
        .containsExactly(
            "dimension::lab_location",
            "dimension::lab_location_zone",
            "dimension::lab_location_extra_a",
            "dimension::lab_location_extra_b")
        .inOrder();
    assertThat(response.getItems(1).hasCount()).isTrue();
    assertThat(response.getItems(2).hasCount()).isFalse();
    assertThat(response.getItems(2).getMainText(0).getText())
        .isEqualTo("Dimension lab_location_extra_a");

    // Catalog-only mixed-case dimension resolves by prefix ("group by monsoon") and closes a
    // single-token query ("monsoon_status") with an uncounted Group by row.
    FleetSuggestion byPrefix =
        firstAddGroupBy(
            suggester.suggest(withCatalog, request("group by monsoon")),
            "dimension::Monsoon_Status");
    assertThat(byPrefix.getLabel()).isEqualTo("Group by");
    assertThat(byPrefix.hasCount()).isFalse();
    assertThat(byPrefix.getMainText(0).getText()).isEqualTo("Dimension Monsoon_Status");

    FleetSuggestionResponse singleToken = suggester.suggest(withCatalog, request("monsoon_status"));
    FleetSuggestion closingGroupBy = singleToken.getItems(singleToken.getItemsCount() - 1);
    assertThat(closingGroupBy.getAddGroupBy().getKey()).isEqualTo("dimension::Monsoon_Status");
    assertThat(closingGroupBy.hasCount()).isFalse();

    // An uncataloged cold long-tail dimension name mints an uncounted Group by row.
    FleetSuggestion minted =
        firstAddGroupBy(
            suggester.suggest(corpus, request("group by brand_new_dim")),
            "dimension::brand_new_dim");
    assertThat(minted.hasCount()).isFalse();
    assertThat(minted.getMainText(0).getText()).isEqualTo("Dimension brand_new_dim");

    // An unindexed built-in key (SDK_VERSION has 0 devices in this snapshot and isLongTail=false)
    // is not a cold overlay, so it is not offered for grouping.
    assertThat(suggester.suggest(corpus, request("group by sdk_version")).getItemsList()).isEmpty();
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
  public void groupBy_whenFilterNarrowsKeyToSingleGroup_stillSuggestsWithOneGroup() {
    // Under status=IDLE (devices 0, 1, 3), every matching device has status IDLE (1 group). The
    // group-by suggestion is still offered so the user can add or adjust group-bys freely while
    // editing filters.
    FleetSuggestionResponse response =
        suggester.suggest(
            corpus, request("group by status", simple("device_field::status", "IDLE")));

    FleetSuggestion status = firstAddGroupBy(response, "device_field::status");
    assertThat(status.getLabel()).isEqualTo("Group by");
    assertThat(status.getCount()).isEqualTo(1);
    assertThat(status.getCountUnit()).isEqualTo("group");
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
  public void emptyQuery_withFilters_returnsSuggestions() {
    FleetSuggestionResponse response =
        suggester.suggest(corpus, request("", simple("device_field::status", "idle")));
    assertThat(response.getItemsList()).isNotEmpty();
  }

  @Test
  public void emptyQuery_withGroupBys_returnsSuggestions() {
    FleetSuggestionResponse response =
        suggester.suggest(corpus, requestWithGroupBys("", "dimension::model"));
    assertThat(response.getItemsList()).isNotEmpty();
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

  @Test
  public void suggest_buildsPostingsOnlyForAppliedFilters() {
    FleetSnapshot dualFleet =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(dualKeyValueFleet(), BUILD_TIME);
    Set<String> postingKeysScanned = new HashSet<>();
    LazyPostings trackingPostings =
        new LazyPostings(
            new LazyPostings.RecordValues() {
              @Override
              public int recordCount() {
                return dualFleet.devices().size();
              }

              @Override
              public ImmutableSet<String> valuesForKey(int index, String keyId) {
                postingKeysScanned.add(keyId);
                return dualFleet.devices().get(index).normalizedValues(keyId);
              }
            });
    DeviceCorpus trackingCorpus = new DeviceCorpus(dualFleet, trackingPostings, new AtsCuration());

    // 1. Without filters, value counts and key statistics come from the index, so neither value
    // queries nor group-by / emptiness queries build posting lists.
    FleetSuggestionResponse unfiltered = suggester.suggest(trackingCorpus, request("zephyr"));
    assertThat(unfiltered.getItemsCount()).isEqualTo(2);
    assertThat(unfiltered.getItems(0).getCount()).isEqualTo(1);
    FleetSuggestionResponse unfilteredGroupBy =
        suggester.suggest(trackingCorpus, request("group by model"));
    assertThat(unfilteredGroupBy.getItemsCount()).isEqualTo(1);
    assertThat(unfilteredGroupBy.getItems(0).getCount()).isEqualTo(1);
    FleetSuggestionResponse unfilteredEmpty =
        suggester.suggest(trackingCorpus, request("custom_tag is empty"));
    assertThat(unfilteredEmpty.getItemsList()).isEmpty();
    // A comma list with one present value uses scopeCount (index lookup) rather than postings.
    FleetSuggestionResponse unfilteredSingleList =
        suggester.suggest(trackingCorpus, request("model is zephyr, nonexistent"));
    assertThat(unfilteredSingleList.getItemsCount()).isEqualTo(1);
    assertThat(unfilteredSingleList.getItems(0).getCount()).isEqualTo(1);
    assertThat(postingKeysScanned).isEmpty();

    // 2. With an active filter on device_field::status, only the applied filter key builds posting
    // lists; unapplied candidate keys (dimension::model and dimension::custom_tag) are counted over
    // the filtered scope without building posting lists, even when both rows are returned.
    FleetSuggestionResponse filtered =
        suggester.suggest(
            trackingCorpus, request("zephyr", simple("device_field::status", "IDLE")));
    assertThat(filtered.getItemsCount()).isEqualTo(2);
    assertThat(filtered.getItems(0).getApplyFilter().getResultingFilter().getKey())
        .isEqualTo("dimension::model");
    assertThat(filtered.getItems(1).getApplyFilter().getResultingFilter().getKey())
        .isEqualTo("dimension::custom_tag");
    assertThat(postingKeysScanned).containsExactly("device_field::status");
  }

  @Test
  public void suggest_filteredValueList_countsUnappliedKeyWithoutBuildingItsPostings() {
    Set<String> postingKeysScanned = new HashSet<>();
    LazyPostings trackingPostings =
        new LazyPostings(
            new LazyPostings.RecordValues() {
              @Override
              public int recordCount() {
                return snapshot.devices().size();
              }

              @Override
              public ImmutableSet<String> valuesForKey(int index, String keyId) {
                postingKeysScanned.add(keyId);
                return snapshot.devices().get(index).normalizedValues(keyId);
              }
            });
    DeviceCorpus trackingCorpus = new DeviceCorpus(snapshot, trackingPostings, new AtsCuration());

    // status=IDLE keeps device-0, device-1 (pixel), and device-3 (nexus). The two-value OR on the
    // unapplied dimension::model is counted in one pass over that scope, so only the applied
    // filter key builds a posting list.
    FleetSuggestionResponse response =
        suggester.suggest(
            trackingCorpus,
            request("model is pixel, nexus", simple("device_field::status", "IDLE")));

    assertThat(firstApplyFilter(response, "dimension::model").getCount()).isEqualTo(3);
    assertThat(postingKeysScanned).containsExactly("device_field::status");
  }

  @Test
  public void suggest_modifyIncludeUpperBound_isNotClampedToScopeSize() {
    // The chip model=tablet keeps only device-0 (scope size 1), which also carries pixel.
    //   pixel:     device-0..device-3, global 4, delta +3 (device-0 is already in scope)
    //   pixel_pro: device-4..device-7, global 4, delta +4
    // Both are prefix matches for "pix" with equal global counts, so pixel is visited first and
    // fills the single slot with +3. pixel_pro's delta can exceed the scope size, so its upper
    // bound
    // is its global count (4), not 1, and it is evaluated and wins with +4.
    List<DeviceInfo> devices = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      DeviceCompositeDimension.Builder dims = DeviceCompositeDimension.newBuilder();
      if (i == 0) {
        dims.addSupportedDimension(dimension("model", "tablet"));
      }
      dims.addSupportedDimension(dimension("model", i < 4 ? "pixel" : "pixel_pro"));
      devices.add(
          DeviceInfo.newBuilder()
              .setDeviceLocator(DeviceLocator.newBuilder().setId("device-" + i))
              .setDeviceStatus(DeviceStatus.IDLE)
              .setDeviceFeature(
                  DeviceFeature.newBuilder()
                      .addType("android_real_device")
                      .setCompositeDimension(dims))
              .build());
    }
    LabQueryResult lab =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(labData("lab-a", "1.1.1.1", devices.toArray(new DeviceInfo[0]))))
            .build();
    FleetSnapshot modelSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(lab, BUILD_TIME);
    DeviceCorpus modelCorpus =
        new DeviceCorpus(
            modelSnapshot, new LazyPostings(modelSnapshot.devices()), new AtsCuration());

    FleetSuggestionResponse response =
        suggester.suggest(
            modelCorpus,
            request("model is pix", simple("dimension::model", "tablet")).toBuilder()
                .setLimit(1)
                .build());

    assertThat(response.getItemsCount()).isEqualTo(1);
    FleetSuggestion item = response.getItems(0);
    assertThat(item.getLabel()).isEqualTo("Modify filter");
    assertThat(item.getApplyFilter().getResultingFilter().getKey()).isEqualTo("dimension::model");
    assertThat(item.getCountPrefix()).isEqualTo("+");
    assertThat(item.getCount()).isEqualTo(4);
  }

  @Test
  public void suggest_filteredEvaluation_ordersEqualPriorityKeysByGlobalCountAndRespectsCap() {
    // 6 devices, every candidate key a raw dimension of equal priority: device-0 is BUSY (scope
    // size
    // 1 under status=BUSY) and carries dim_low=zephyr; device-1..device-5 are IDLE and carry
    // dim_miss_0..dim_miss_4=zephyr, and device-1 also carries dim_low=zephyr.
    // Globally:
    //   - dim_miss_0..dim_miss_4 each have global count 5 for "zephyr" (on device-1..device-5).
    //   - dim_low has global count 2 for "zephyr" (on device-0 and device-1), even though its name
    //     sorts alphabetically before dim_miss_*.
    // Under filter status=BUSY (scopeSize = 1) with limit = 1 (distinct-key budget = 3):
    //   - All candidates have upperBoundCount clamped to scopeSize (1), so fullCorpusCount (5 > 2)
    //     must break the tie ahead of alphabetical mainText, evaluating the top 3 keys
    //     (dim_miss_0, dim_miss_1, dim_miss_2) and stopping at the budget of 3.
    DeviceList.Builder devices =
        DeviceList.newBuilder()
            .setDeviceTotalCount(6)
            .addDeviceInfo(
                DeviceInfo.newBuilder()
                    .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
                    .setDeviceStatus(DeviceStatus.BUSY)
                    .setDeviceFeature(
                        DeviceFeature.newBuilder()
                            .addType("android_real_device")
                            .setCompositeDimension(
                                DeviceCompositeDimension.newBuilder()
                                    .addSupportedDimension(dimension("dim_low", "zephyr")))));
    for (int i = 1; i <= 5; i++) {
      DeviceCompositeDimension.Builder dims = DeviceCompositeDimension.newBuilder();
      if (i == 1) {
        dims.addSupportedDimension(dimension("dim_low", "zephyr"));
      }
      for (int k = 0; k < 5; k++) {
        dims.addSupportedDimension(dimension("dim_miss_" + k, "zephyr"));
      }
      devices.addDeviceInfo(
          DeviceInfo.newBuilder()
              .setDeviceLocator(DeviceLocator.newBuilder().setId("device-" + i))
              .setDeviceStatus(DeviceStatus.IDLE)
              .setDeviceFeature(
                  DeviceFeature.newBuilder()
                      .addType("android_real_device")
                      .setCompositeDimension(dims)));
    }
    LabQueryResult labResult =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        LabData.newBuilder()
                            .setLabInfo(
                                LabInfo.newBuilder()
                                    .setLabLocator(
                                        LabLocator.newBuilder()
                                            .setHostName("lab-a")
                                            .setIp("1.1.1.1"))
                                    .setLabStatus(LabStatus.LAB_RUNNING))
                            .setDeviceList(devices)))
            .build();
    FleetSnapshot cappedSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(labResult, BUILD_TIME);
    DeviceCorpus cappedCorpus =
        new DeviceCorpus(
            cappedSnapshot, new LazyPostings(cappedSnapshot.devices()), new AtsCuration());
    TrackingCorpus cappedTracking = new TrackingCorpus(cappedCorpus);

    // With limit = 1, the filtered evaluation budget is 1 * 3 = 3 distinct keys. The 5 dim_miss_*
    // candidates (fullCorpusCount 5) rank ahead of dim_low (fullCorpusCount 2) despite dim_low
    // sorting first alphabetically, so the first 3 evaluated keys are dim_miss_0..2 (all count 0 on
    // the BUSY device), and dim_low is beyond the budget of 3.
    FleetSuggestionResponse cappedAtLimit1 =
        suggester.suggest(
            cappedTracking,
            request("zephyr", simple("device_field::status", "BUSY")).toBuilder()
                .setLimit(1)
                .build());
    assertThat(cappedAtLimit1.getItemsList()).isEmpty();
    assertThat(cappedTracking.keysScannedInScope)
        .containsExactly("dimension::dim_miss_0", "dimension::dim_miss_1", "dimension::dim_miss_2");

    // With limit = 2, the filtered evaluation budget is 2 * 3 = 6 distinct keys, which reaches
    // dim_low (the 6th key by fullCorpusCount) and returns it with count 1.
    FleetSuggestionResponse reachedAtLimit2 =
        suggester.suggest(
            cappedCorpus,
            request("zephyr", simple("device_field::status", "BUSY")).toBuilder()
                .setLimit(2)
                .build());
    assertThat(reachedAtLimit2.getItemsCount()).isEqualTo(1);
    assertThat(reachedAtLimit2.getItems(0).getApplyFilter().getResultingFilter().getKey())
        .isEqualTo("dimension::dim_low");
    assertThat(reachedAtLimit2.getItems(0).getCount()).isEqualTo(1);
  }

  @Test
  public void suggest_activeChipModifyAndExclude_usesScopedCountsAndFastPath() {
    // 1. Active negated chip on dimension::model (excludes iphone, leaving 3 devices: device-0,
    // device-1, device-3). Typing "model != pixel" counts "pixel" on the active key via postings
    // intersected with currentBits (2 devices), leaving 3 - 2 = 1 device.
    Filter excludeIphone =
        Filter.newBuilder()
            .setKey("dimension::model")
            .setSimple(
                SimpleMatch.newBuilder()
                    .setNegated(true)
                    .addValues(FilterValue.newBuilder().setValue("iphone")))
            .build();
    FleetSuggestionResponse excludeOnActive =
        suggester.suggest(corpus, request("model != pixel", excludeIphone));
    FleetSuggestion excludePixel = firstApplyFilter(excludeOnActive, "dimension::model");
    assertThat(excludePixel.getLabel()).isEqualTo("Modify filter");
    assertThat(excludePixel.getCount()).isEqualTo(1);
    assertThat(excludePixel.getCountPrefix()).isEmpty();
    assertThat(excludePixel.getMainTextList())
        .containsExactly(
            TextSegment.newBuilder().setText("Model: exclude ").setEmphasized(false).build(),
            TextSegment.newBuilder().setText("pixel").setEmphasized(true).build())
        .inOrder();
    assertThat(excludePixel.getApplyFilter().getPillCondition()).isEqualTo("\u2260 2");
    assertThat(suggester.suggest(corpus, request("model != iphone", excludeIphone)).getItemsList())
        .isEmpty();

    // 2. Modify-include with two active chips: model=pixel and pool=shared (matches device-0 and
    // device-1). Typing "model is nexus" checks matchesExceptChip (pool=shared matches device-0,
    // device-1, device-3) and reports +1 for device-3.
    FleetSuggestionResponse modifyWithOtherChip =
        suggester.suggest(
            corpus,
            request(
                "model is nexus",
                simple("dimension::model", "pixel"),
                simple("dimension::pool", "shared")));
    FleetSuggestion nexus = firstApplyFilter(modifyWithOtherChip, "dimension::model");
    assertThat(nexus.getCount()).isEqualTo(1);
    assertThat(nexus.getCountPrefix()).isEqualTo("+");

    // 3. Modify-include with a single active chip (model=tablet, matching device-0 which carries
    // both pixel and tablet). Typing "model is pixel" subtracts the 1 overlapping device in
    // currentBits from pixel's 2 total devices (+1) and skips re-running filterEngine.match on an
    // empty chip list.
    DeviceInfo multiModel0 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("model", "pixel"))
                            .addSupportedDimension(dimension("model", "tablet"))))
            .build();
    DeviceInfo multiModel1 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-1"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("model", "pixel"))))
            .build();
    LabQueryResult multiModelLab =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(labData("lab-a", "1.1.1.1", multiModel0, multiModel1)))
            .build();
    FleetSnapshot multiModelSnapshot =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(multiModelLab, BUILD_TIME);
    TrackingCorpus tracking =
        new TrackingCorpus(
            new DeviceCorpus(
                multiModelSnapshot,
                new LazyPostings(multiModelSnapshot.devices()),
                new AtsCuration()));
    // Baseline: the same chip with a value that matches nothing never reaches modifyIncludeDelta,
    // so its corpus reads are what building the request context costs.
    assertThat(
            suggester
                .suggest(
                    tracking,
                    request("model is no_such_model", simple("dimension::model", "tablet")))
                .getItemsList())
        .isEmpty();
    int contextRecordCountReads = tracking.recordCountCalls;
    tracking.recordCountCalls = 0;

    FleetSuggestionResponse modifySingleChip =
        suggester.suggest(
            tracking, request("model is pixel", simple("dimension::model", "tablet")));
    FleetSuggestion pixel = firstApplyFilter(modifySingleChip, "dimension::model");
    assertThat(pixel.getCount()).isEqualTo(1);
    assertThat(pixel.getCountPrefix()).isEqualTo("+");
    // With the chip being the only one, modifyIncludeDelta matches nothing again, so the request
    // reads the corpus size no more often than the baseline.
    assertThat(tracking.recordCountCalls).isEqualTo(contextRecordCountReads);
  }

  @Test
  public void suggest_hugeLimit_isCappedAndStillCountsFilteredCandidates() {
    // An unbounded limit would size the ranking heap to Integer.MAX_VALUE and overflow the filtered
    // evaluation budget (limit * 3) to a negative number that skips every counted candidate.
    FleetSuggestionResponse response =
        suggester.suggest(
            corpus,
            request("pixel", simple("device_field::status", "IDLE")).toBuilder()
                .setLimit(Integer.MAX_VALUE)
                .build());

    assertThat(response.getItemsCount()).isAtMost(50);
    // device-0 and device-1 are the IDLE pixels.
    assertThat(firstApplyFilter(response, "dimension::model").getCount()).isEqualTo(2);
  }

  @Test
  public void suggest_filteredGroupBy_countsMultiValuedRecordsAsSetsRegardlessOfOrder() {
    // Under status=IDLE, device-0 has model {pixel, tablet} and device-1 has {tablet, pixel}: the
    // same value set in a different order, so grouping by model makes one group.
    DeviceInfo idlePixelTablet =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("model", "pixel"))
                            .addSupportedDimension(dimension("model", "tablet"))))
            .build();
    DeviceInfo idleTabletPixel =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-1"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("model", "tablet"))
                            .addSupportedDimension(dimension("model", "pixel"))))
            .build();
    DeviceInfo busyIphone =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-2"))
            .setDeviceStatus(DeviceStatus.BUSY)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("ios_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("model", "iphone"))))
            .build();
    LabQueryResult lab =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData("lab-a", "1.1.1.1", idlePixelTablet, idleTabletPixel, busyIphone)))
            .build();
    FleetSnapshot setSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(lab, BUILD_TIME);
    DeviceCorpus setCorpus =
        new DeviceCorpus(setSnapshot, new LazyPostings(setSnapshot.devices()), new AtsCuration());

    FleetSuggestionResponse response =
        suggester.suggest(
            setCorpus, request("group by model", simple("device_field::status", "IDLE")));

    FleetSuggestion model = firstAddGroupBy(response, "dimension::model");
    assertThat(model.getCount()).isEqualTo(1);
    assertThat(model.getCountUnit()).isEqualTo("group");
  }

  @Test
  public void suggest_groupByRanking_putsUsableGroupingAheadOfHigherPrioritySingleGroup() {
    // "group by mo" prefix-matches the curated Model key and the raw mo_rack dimension. Under
    // status=IDLE both IDLE devices are pixels, so Model makes one group, while mo_rack (r1, r2)
    // splits the scope. Model's curated priority is higher, but a grouping that splits the scope
    // ranks first.
    DeviceCorpus rackCorpus =
        corpusOf(
            ImmutableList.of(
                deviceWith(
                    "device-0",
                    DeviceStatus.IDLE,
                    dimension("model", "pixel"),
                    dimension("mo_rack", "r1")),
                deviceWith(
                    "device-1",
                    DeviceStatus.IDLE,
                    dimension("model", "pixel"),
                    dimension("mo_rack", "r2")),
                deviceWith(
                    "device-2",
                    DeviceStatus.BUSY,
                    dimension("model", "iphone"),
                    dimension("mo_rack", "r3"))));

    FleetSuggestionResponse response =
        suggester.suggest(
            rackCorpus, request("group by mo", simple("device_field::status", "IDLE")));

    assertThat(response.getItemsCount()).isEqualTo(2);
    assertThat(response.getItems(0).getAddGroupBy().getKey()).isEqualTo("dimension::mo_rack");
    assertThat(response.getItems(0).getCount()).isEqualTo(2);
    assertThat(response.getItems(1).getAddGroupBy().getKey()).isEqualTo("dimension::model");
    assertThat(response.getItems(1).getCount()).isEqualTo(1);
  }

  @Test
  public void suggest_filteredEvaluationBudget_countsDistinctKeysNotValues() {
    // device-0 is the only BUSY device and carries model zep5. zep1..zep4 are each on two IDLE
    // devices, so under status=BUSY they rank first by fleet-wide count and all count 0. With
    // limit = 1 the budget is 3 keys; all five values belong to one key, so every value is still
    // evaluated and zep5 is found.
    List<DeviceInfo> devices = new ArrayList<>();
    devices.add(deviceWith("device-0", DeviceStatus.BUSY, dimension("model", "zep5")));
    for (int i = 1; i <= 8; i++) {
      devices.add(
          deviceWith("device-" + i, DeviceStatus.IDLE, dimension("model", "zep" + ((i + 1) / 2))));
    }
    DeviceCorpus zepCorpus = corpusOf(devices);

    FleetSuggestionResponse response =
        suggester.suggest(
            zepCorpus,
            request("zep", simple("device_field::status", "BUSY")).toBuilder().setLimit(1).build());

    assertThat(response.getItemsCount()).isEqualTo(1);
    FleetSuggestion item = firstApplyFilter(response, "dimension::model");
    assertThat(item.getApplyFilter().getResultingFilter().getSimple().getValues(0).getValue())
        .isEqualTo("zep5");
    assertThat(item.getCount()).isEqualTo(1);
  }

  @Test
  public void suggest_filteredGroupByTerm_countsOnlyTheBudgetedKeys() {
    // Eight raw dimensions grp_0..grp_7 match "group by grp". Under a filter, limit = 2 allows
    // 2 * 3 = 6 group counts; the other two keys are offered without a count and never scanned.
    DeviceCompositeDimension.Builder dims = DeviceCompositeDimension.newBuilder();
    for (int k = 0; k < 8; k++) {
      dims.addSupportedDimension(dimension("grp_" + k, "v"));
    }
    DeviceInfo device =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(dims))
            .build();
    TrackingCorpus tracking = new TrackingCorpus(corpusOf(ImmutableList.of(device)));

    FleetSuggestionResponse response =
        suggester.suggest(
            tracking,
            request("group by grp", simple("device_field::status", "IDLE")).toBuilder()
                .setLimit(2)
                .build());

    assertThat(response.getItemsCount()).isEqualTo(2);
    assertThat(response.getItemsList().stream().noneMatch(FleetSuggestion::hasCount)).isTrue();
    assertThat(
            tracking.keysScannedInScope.stream().filter(key -> key.startsWith("dimension::grp_")))
        .hasSize(6);
  }

  @Test
  public void suggest_filteredGroupByTerm_coldKeysDoNotConsumeTheCountBudget() {
    // "group by grp" prefix-matches six catalog-only (cold) dimensions grp_c0..grp_c5, which are
    // visited first, and substring-matches the indexed x_grp_0 and x_grp_1. Cold keys are offered
    // without a count and cost nothing, so with limit = 2 (budget 6) both indexed keys are counted.
    FleetSnapshot built =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(
                labOf(
                    ImmutableList.of(
                        deviceWith(
                            "device-0",
                            DeviceStatus.IDLE,
                            dimension("x_grp_0", "a"),
                            dimension("x_grp_1", "b")))),
                BUILD_TIME);
    TrackingCorpus tracking =
        new TrackingCorpus(
            new DeviceCorpus(
                built,
                new LazyPostings(built.devices()),
                new AtsCuration(),
                OverlayView.empty(),
                ImmutableSet.of("grp_c0", "grp_c1", "grp_c2", "grp_c3", "grp_c4", "grp_c5")));

    FleetSuggestionResponse response =
        suggester.suggest(
            tracking,
            request("group by grp", simple("device_field::status", "IDLE")).toBuilder()
                .setLimit(2)
                .build());

    assertThat(response.getItemsCount()).isEqualTo(2);
    assertThat(tracking.keysScannedInScope)
        .containsAtLeast("dimension::x_grp_0", "dimension::x_grp_1");
  }

  @Test
  public void
      suggest_filteredValueListOnChipKey_mergesNewValuesAndComputesIncludeAndExcludeCounts() {
    // 1. Active include chip (model=pixel) alone: "model is nexus, iphone" adds device-2 and
    // device-3 (+2) and merges both values into the chip.
    FleetSuggestion addTwo =
        firstApplyFilter(
            suggester.suggest(
                corpus, request("model is nexus, iphone", simple("dimension::model", "pixel"))),
            "dimension::model");
    assertThat(addTwo.getLabel()).isEqualTo("Modify filter");
    assertThat(addTwo.getCountPrefix()).isEqualTo("+");
    assertThat(addTwo.getCount()).isEqualTo(2);
    assertThat(addTwo.getMainTextList())
        .containsExactly(
            TextSegment.newBuilder().setText("Model: add ").setEmphasized(false).build(),
            TextSegment.newBuilder().setText("nexus").setEmphasized(true).build(),
            TextSegment.newBuilder().setText(" or ").setEmphasized(false).build(),
            TextSegment.newBuilder().setText("iphone").setEmphasized(true).build())
        .inOrder();
    assertThat(addTwo.getApplyFilter().getPillCondition()).isEqualTo("3");

    // Multi-valued key (pool=shared matches device-0, which also carries "presubmit"): adding
    // "presubmit" or "canary" excludes already-matched device-0 via union.andNot(currentBits) and
    // reports +2 (device-1, device-2), not +3.
    DeviceCorpus multiPoolCorpus =
        corpusOf(
            ImmutableList.of(
                deviceWith(
                    "device-0",
                    DeviceStatus.IDLE,
                    dimension("pool", "shared"),
                    dimension("pool", "presubmit")),
                deviceWith("device-1", DeviceStatus.IDLE, dimension("pool", "presubmit")),
                deviceWith("device-2", DeviceStatus.IDLE, dimension("pool", "canary"))));
    FleetSuggestion addOverlappingPools =
        firstApplyFilter(
            suggester.suggest(
                multiPoolCorpus,
                request("pool is presubmit, canary", simple("dimension::pool", "shared"))),
            "dimension::pool");
    assertThat(addOverlappingPools.getCountPrefix()).isEqualTo("+");
    assertThat(addOverlappingPools.getCount()).isEqualTo(2);

    // 2. With status=IDLE also active: "model is pixel, nexus, iphone" strips the already-present
    // "pixel", evaluates matchesExceptChip for {nexus, iphone}, and reports +1 (only device-3 nexus
    // is IDLE; device-2 iphone is BUSY). "model is pixel, iphone" adds +0 under status=IDLE and is
    // omitted, as is a conflicting polarity or all-already-present list.
    FleetSuggestion addUnderFilter =
        firstApplyFilter(
            suggester.suggest(
                corpus,
                request(
                    "model is pixel, nexus, iphone",
                    simple("dimension::model", "pixel"),
                    simple("device_field::status", "IDLE"))),
            "dimension::model");
    assertThat(addUnderFilter.getCountPrefix()).isEqualTo("+");
    assertThat(addUnderFilter.getCount()).isEqualTo(1);
    assertThat(addUnderFilter.getApplyFilter().getPillCondition()).isEqualTo("3");

    assertThat(
            suggester
                .suggest(
                    corpus,
                    request(
                        "model is pixel, iphone",
                        simple("dimension::model", "pixel"),
                        simple("device_field::status", "IDLE")))
                .getItemsList())
        .isEmpty();
    assertThat(
            suggester
                .suggest(
                    corpus, request("model is pixel, pixel", simple("dimension::model", "pixel")))
                .getItemsList())
        .isEmpty();
    assertThat(
            suggester
                .suggest(
                    corpus, request("model != nexus, iphone", simple("dimension::model", "pixel")))
                .getItemsList())
        .isEmpty();

    // 3. Active negated chip (model!=iphone, scope {device-0, device-1, device-3}): "model !=
    // nexus, iphone" strips "iphone" and excludes "nexus", leaving 2 devices; "model != pixel,
    // nexus" excludes all 3 remaining devices in scope (union over active key postings) and is
    // omitted; "model != pixel" with a second value on a 3-model fleet leaves > 0 when one remains.
    Filter excludeIphone =
        Filter.newBuilder()
            .setKey("dimension::model")
            .setSimple(
                SimpleMatch.newBuilder()
                    .setNegated(true)
                    .addValues(FilterValue.newBuilder().setValue("iphone")))
            .build();
    FleetSuggestion excludeMulti =
        firstApplyFilter(
            suggester.suggest(corpus, request("model != nexus, iphone", excludeIphone)),
            "dimension::model");
    assertThat(excludeMulti.getLabel()).isEqualTo("Modify filter");
    assertThat(excludeMulti.getCountPrefix()).isEmpty();
    assertThat(excludeMulti.getCount()).isEqualTo(2);
    assertThat(excludeMulti.getMainTextList())
        .containsExactly(
            TextSegment.newBuilder().setText("Model: exclude ").setEmphasized(false).build(),
            TextSegment.newBuilder().setText("nexus").setEmphasized(true).build())
        .inOrder();
    assertThat(excludeMulti.getApplyFilter().getPillCondition()).isEqualTo("\u2260 2");

    assertThat(
            suggester
                .suggest(corpus, request("model != pixel, nexus", excludeIphone))
                .getItemsList())
        .isEmpty();

    // Unapplied negated multi-value OR ("model != pixel, iphone" with no chip) leaves device-3 (1),
    // while an unapplied OR matching 0 records in scope ("model is pixel, nexus" under status=BUSY)
    // is omitted.
    FleetSuggestion unappliedNegatedOr =
        firstApplyFilter(
            suggester.suggest(corpus, request("model != pixel, iphone")), "dimension::model");
    assertThat(unappliedNegatedOr.getLabel()).isEqualTo("Add filter");
    assertThat(unappliedNegatedOr.getCount()).isEqualTo(1);
    assertThat(unappliedNegatedOr.getApplyFilter().getPillCondition()).isEqualTo("\u2260 2");
    assertThat(
            suggester
                .suggest(
                    corpus,
                    request("model is pixel, nexus", simple("device_field::status", "BUSY")))
                .getItemsList())
        .isEmpty();
  }

  @Test
  public void suggest_unfilteredValueList_countsWithoutBuildingPostings() {
    // pool is single-valued (shared, shared, dedicated), so "shared, dedicated" is the sum 2 + 1.
    // model is multi-valued (device-0 carries pixel and tablet), so "pixel, tablet" counts device-0
    // once: 2 records, not 3. Neither count builds a posting list.
    ImmutableList<DeviceInfo> devices =
        ImmutableList.of(
            deviceWith(
                "device-0",
                DeviceStatus.IDLE,
                dimension("pool", "shared"),
                dimension("model", "pixel"),
                dimension("model", "tablet")),
            deviceWith(
                "device-1",
                DeviceStatus.IDLE,
                dimension("pool", "shared"),
                dimension("model", "pixel")),
            deviceWith(
                "device-2",
                DeviceStatus.IDLE,
                dimension("pool", "dedicated"),
                dimension("model", "iphone")));
    FleetSnapshot listSnapshot =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(labOf(devices), BUILD_TIME);
    Set<String> postingKeysScanned = new HashSet<>();
    LazyPostings trackingPostings =
        new LazyPostings(
            new LazyPostings.RecordValues() {
              @Override
              public int recordCount() {
                return listSnapshot.devices().size();
              }

              @Override
              public ImmutableSet<String> valuesForKey(int index, String keyId) {
                postingKeysScanned.add(keyId);
                return listSnapshot.devices().get(index).normalizedValues(keyId);
              }
            });
    DeviceCorpus listCorpus = new DeviceCorpus(listSnapshot, trackingPostings, new AtsCuration());
    TrackingCorpus tracking = new TrackingCorpus(listCorpus);

    assertThat(
            firstApplyFilter(
                    suggester.suggest(tracking, request("pool is shared, dedicated")),
                    "dimension::pool")
                .getCount())
        .isEqualTo(3);
    assertThat(
            firstApplyFilter(
                    suggester.suggest(tracking, request("model is pixel, tablet")),
                    "dimension::model")
                .getCount())
        .isEqualTo(2);
    assertThat(postingKeysScanned).isEmpty();
    assertThat(tracking.keysScannedInScope).containsExactly("dimension::model");
  }

  @Test
  public void suggest_filteredEmptyScope_suppressesAllGroupBysIncludingColdKeys() {
    FleetSnapshot built =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(
                labOf(
                    ImmutableList.of(
                        deviceWith("device-0", DeviceStatus.IDLE, dimension("model", "pixel")))),
                BUILD_TIME);
    DeviceCorpus coldCorpus =
        new DeviceCorpus(
            built,
            new LazyPostings(built.devices()),
            new AtsCuration(),
            OverlayView.empty(),
            ImmutableSet.of("cold_dim"));

    Filter nonEmptyScopeFilter = simple("device_field::status", "IDLE");
    Filter emptyScopeFilter = simple("device_field::status", "BUSY");
    assertThat(suggester.suggest(coldCorpus, request("group by cold_dim")).getItemsList())
        .isNotEmpty();
    assertThat(
            suggester.suggest(coldCorpus, request("cold_dim")).getItemsList().stream()
                .anyMatch(FleetSuggestion::hasAddGroupBy))
        .isTrue();
    assertThat(
            suggester
                .suggest(coldCorpus, request("cold_dim", nonEmptyScopeFilter))
                .getItemsList()
                .stream()
                .anyMatch(FleetSuggestion::hasAddGroupBy))
        .isTrue();
    assertThat(
            suggester
                .suggest(coldCorpus, request("group by cold_dim", emptyScopeFilter))
                .getItemsList())
        .isEmpty();
    assertThat(
            suggester
                .suggest(coldCorpus, request("group by model", emptyScopeFilter))
                .getItemsList())
        .isEmpty();
    // Single-token "cold_dim" still offers the bare key picker row, without a closing Group by row.
    FleetSuggestionResponse singleToken =
        suggester.suggest(coldCorpus, request("cold_dim", emptyScopeFilter));
    assertThat(firstOpenPicker(singleToken, "dimension::cold_dim").getOpenPicker().getKey())
        .isEqualTo("dimension::cold_dim");
    assertThat(singleToken.getItemsList().stream().noneMatch(FleetSuggestion::hasAddGroupBy))
        .isTrue();
  }

  @Test
  public void suggest_valueList_ordersByPriorityAndBoundsRecordScanningKeys() {
    // Unfiltered "v1, v2": a_single_dim is single-valued (device-0 carries v1, device-1 carries
    // v2), while aa_multi_0..aa_multi_3 and curated Model are multi-valued. On device-0,
    // aa_multi_3..0 are added in descending k order, followed by a_single_dim and Model, so
    // first-seen insertion order differs from priority, upper-bound OR count, and key-id order.
    // aa_multi_3 also appears on device-1 so its upper-bound OR count (4) beats Model's (2) and
    // aa_multi_0..2's (2). With limit = 1 (scan budget 3), a_single_dim costs no record scan,
    // curated Model is scanned first by priority, aa_multi_3 is scanned second by upper-bound OR
    // count, and aa_multi_0 is scanned third by key id.
    DeviceCompositeDimension.Builder dims0 = DeviceCompositeDimension.newBuilder();
    for (int k = 3; k >= 0; k--) {
      dims0
          .addSupportedDimension(dimension("aa_multi_" + k, "v1"))
          .addSupportedDimension(dimension("aa_multi_" + k, "v2"));
    }
    dims0
        .addSupportedDimension(dimension("a_single_dim", "v1"))
        .addSupportedDimension(dimension("model", "v1"))
        .addSupportedDimension(dimension("model", "v2"));
    DeviceInfo d0 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(dims0))
            .build();
    DeviceInfo d1 =
        deviceWith(
            "device-1",
            DeviceStatus.IDLE,
            dimension("a_single_dim", "v2"),
            dimension("aa_multi_3", "v1"),
            dimension("aa_multi_3", "v2"));
    TrackingCorpus tracking = new TrackingCorpus(corpusOf(ImmutableList.of(d0, d1)));

    FleetSuggestionResponse response =
        suggester.suggest(tracking, request("v1, v2").toBuilder().setLimit(1).build());

    assertThat(response.getItemsCount()).isEqualTo(1);
    assertThat(firstApplyFilter(response, "dimension::model").getCount()).isEqualTo(1);
    assertThat(tracking.keysScannedInScope)
        .containsExactly("dimension::model", "dimension::aa_multi_3", "dimension::aa_multi_0");

    // Under an active filter, even single-valued keys scan the filtered scope and count toward the
    // budget (a_single_dim sorts ahead of aa_multi_0 by key id).
    tracking.keysScannedInScope.clear();
    FleetSuggestionResponse filteredResponse =
        suggester.suggest(
            tracking,
            request("v1, v2", simple("device_field::status", "IDLE")).toBuilder()
                .setLimit(1)
                .build());
    assertThat(filteredResponse.getItemsCount()).isEqualTo(1);
    assertThat(tracking.keysScannedInScope)
        .containsExactly("dimension::model", "dimension::aa_multi_3", "dimension::a_single_dim");
  }

  private static DeviceInfo deviceWith(String id, DeviceStatus status, DeviceDimension... dims) {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId(id))
        .setDeviceStatus(status)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("android_real_device")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addAllSupportedDimension(ImmutableList.copyOf(dims))))
        .build();
  }

  private static LabQueryResult labOf(List<DeviceInfo> devices) {
    return LabQueryResult.newBuilder()
        .setLabView(
            LabQueryResult.LabView.newBuilder()
                .setLabTotalCount(1)
                .addLabData(labData("lab-a", "1.1.1.1", devices.toArray(new DeviceInfo[0]))))
        .build();
  }

  private static DeviceCorpus corpusOf(List<DeviceInfo> devices) {
    FleetSnapshot built =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(labOf(devices), BUILD_TIME);
    return new DeviceCorpus(built, new LazyPostings(built.devices()), new AtsCuration());
  }

  @Test
  public void suggest_singleTokenAtLimitOne_reservesSlotForGroupByAndHandlesZeroRankLimit() {
    // When limit = 1 and the single token matches a groupable key, the group-by row takes the one
    // slot and ranker.rank is called with limit = 0.
    FleetSuggestionResponse response =
        suggester.suggest(corpus, request("status").toBuilder().setLimit(1).build());
    assertThat(response.getItemsCount()).isEqualTo(1);
    assertThat(response.getItems(0).getAddGroupBy().getKey()).isEqualTo("device_field::status");
  }

  @Test
  public void suggest_cannotBeatPruning_stopsBeforeLowerPriorityLowerTierAndLowerUpperBound() {
    // 1. Priority pruning: dimension::model (curated priority 930) fills limit = 1 under
    // status=IDLE, so uncurated dimension::custom_tag (priority 0) is never evaluated.
    FleetSnapshot dualFleet =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(dualKeyValueFleet(), BUILD_TIME);
    TrackingCorpus priorityTracking =
        new TrackingCorpus(
            new DeviceCorpus(dualFleet, new LazyPostings(dualFleet.devices()), new AtsCuration()));
    FleetSuggestionResponse priorityResponse =
        suggester.suggest(
            priorityTracking,
            request("zephyr", simple("device_field::status", "IDLE")).toBuilder()
                .setLimit(1)
                .build());
    assertThat(priorityResponse.getItemsCount()).isEqualTo(1);
    assertThat(priorityTracking.keysScannedInScope).containsExactly("dimension::model");

    // 2. Tier pruning: custom_a="zephyr" (exact match, tier 3) and custom_b="zephyr_plus" (prefix
    // match, tier 2) share priority 0. Under status=IDLE with limit = 1, custom_a fills top and
    // custom_b is pruned by cannotBeat (candidate.tier() < worstKept.tier()) without being
    // evaluated.
    DeviceInfo tierDevice =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("custom_a", "zephyr"))
                            .addSupportedDimension(dimension("custom_b", "zephyr_plus"))))
            .build();
    LabQueryResult tierLab =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(labData("lab-a", "1.1.1.1", tierDevice)))
            .build();
    FleetSnapshot tierSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(tierLab, BUILD_TIME);
    TrackingCorpus tierTracking =
        new TrackingCorpus(
            new DeviceCorpus(
                tierSnapshot, new LazyPostings(tierSnapshot.devices()), new AtsCuration()));
    FleetSuggestionResponse tierResponse =
        suggester.suggest(
            tierTracking,
            request("zephyr", simple("device_field::status", "IDLE")).toBuilder()
                .setLimit(1)
                .build());
    assertThat(tierResponse.getItemsCount()).isEqualTo(1);
    assertThat(tierTracking.keysScannedInScope).containsExactly("dimension::custom_a");

    // 3. Upper-bound pruning: custom_common="zephyr" (on 2 IDLE devices, fullCorpusCount=2) and
    // custom_rare="zephyr" (on 1 IDLE device, fullCorpusCount=1) share priority 0 and tier 3. Under
    // status=IDLE with limit = 1, custom_common fills top with filtered count 2, and custom_rare
    // (upperBoundCount = 1 < worstKept.rankCount() = 2) is pruned without being evaluated.
    DeviceInfo upperDevice0 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("custom_common", "zephyr"))
                            .addSupportedDimension(dimension("custom_rare", "zephyr"))))
            .build();
    DeviceInfo upperDevice1 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-1"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("custom_common", "zephyr"))))
            .build();
    LabQueryResult upperLab =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(labData("lab-a", "1.1.1.1", upperDevice0, upperDevice1)))
            .build();
    FleetSnapshot upperSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(upperLab, BUILD_TIME);
    TrackingCorpus upperTracking =
        new TrackingCorpus(
            new DeviceCorpus(
                upperSnapshot, new LazyPostings(upperSnapshot.devices()), new AtsCuration()));
    FleetSuggestionResponse upperResponse =
        suggester.suggest(
            upperTracking,
            request("zephyr", simple("device_field::status", "IDLE")).toBuilder()
                .setLimit(1)
                .build());
    assertThat(upperResponse.getItemsCount()).isEqualTo(1);
    assertThat(upperTracking.keysScannedInScope).containsExactly("dimension::custom_common");

    // 4. Tie on upper bound under active filters: when upperBoundCount == worstKept.rankCount(),
    // cannotBeat must NOT break on mainText order because fullCorpusCount precedes mainText in
    // EvaluationKey.
    // Scope: status=IDLE matches 2 IDLE devices (device-0, device-1) out of 4 total.
    //   mid_dim="zephyr": 2 IDLE + 2 BUSY (fullCorpusCount=4, upperBoundCount=2, filtered=2)
    //   zzz_dim="zephyr": 1 IDLE + 2 BUSY (fullCorpusCount=3, upperBoundCount=2, filtered=1)
    //   aaa_dim="zephyr": 2 IDLE + 0 BUSY (fullCorpusCount=2, upperBoundCount=2, filtered=2)
    // EvaluationKey visits mid_dim, then zzz_dim, then aaa_dim. At zzz_dim, upperBoundCount (2) ==
    // worstKept.rankCount() (2) and "zzz_dim..." >= "mid_dim...", yet aaa_dim comes later and wins
    // finalOrder.
    DeviceInfo tieIdle0 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("mid_dim", "zephyr"))
                            .addSupportedDimension(dimension("zzz_dim", "zephyr"))
                            .addSupportedDimension(dimension("aaa_dim", "zephyr"))))
            .build();
    DeviceInfo tieIdle1 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-1"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("mid_dim", "zephyr"))
                            .addSupportedDimension(dimension("aaa_dim", "zephyr"))))
            .build();
    DeviceInfo tieBusy2 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-2"))
            .setDeviceStatus(DeviceStatus.BUSY)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("mid_dim", "zephyr"))
                            .addSupportedDimension(dimension("zzz_dim", "zephyr"))))
            .build();
    DeviceInfo tieBusy3 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-3"))
            .setDeviceStatus(DeviceStatus.BUSY)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("mid_dim", "zephyr"))
                            .addSupportedDimension(dimension("zzz_dim", "zephyr"))))
            .build();
    LabQueryResult tieLab =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData("lab-a", "1.1.1.1", tieIdle0, tieIdle1, tieBusy2, tieBusy3)))
            .build();
    FleetSnapshot tieSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(tieLab, BUILD_TIME);
    DeviceCorpus tieCorpus =
        new DeviceCorpus(tieSnapshot, new LazyPostings(tieSnapshot.devices()), new AtsCuration());
    FleetSuggestionResponse tieResponse =
        suggester.suggest(
            tieCorpus,
            request("zephyr", simple("device_field::status", "IDLE")).toBuilder()
                .setLimit(1)
                .build());
    assertThat(tieResponse.getItemsCount()).isEqualTo(1);
    assertThat(firstApplyFilter(tieResponse, "dimension::aaa_dim").getCount()).isEqualTo(2);

    // 5. Exclude upper bound: for "not zephyr", a deferred exclude candidate's fullCorpusCount is
    // recordCount (4) - globalCount, and upperBoundCount is min(fullCorpusCount, scopeSize=3):
    //   rare_dim="zephyr":   0 IDLE + 1 BUSY (fullCorpusCount=4-1=3, upperBoundCount=3, exclude=3)
    //   common_dim="zephyr": 1 IDLE + 1 BUSY (fullCorpusCount=4-2=2, upperBoundCount=2, exclude=2)
    // EvaluationKey visits rare_dim first (upperBoundCount 3 > 2), putting count 3 in top, and
    // prunes common_dim via cannotBeat (upperBoundCount 2 < 3) without scanning it.
    DeviceInfo exclIdle0 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-0"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("common_dim", "zephyr"))))
            .build();
    DeviceInfo exclIdle1 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-1"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("android_real_device"))
            .build();
    DeviceInfo exclIdle2 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-2"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDeviceFeature(DeviceFeature.newBuilder().addType("android_real_device"))
            .build();
    DeviceInfo exclBusy3 =
        DeviceInfo.newBuilder()
            .setDeviceLocator(DeviceLocator.newBuilder().setId("device-3"))
            .setDeviceStatus(DeviceStatus.BUSY)
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .addType("android_real_device")
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder()
                            .addSupportedDimension(dimension("common_dim", "zephyr"))
                            .addSupportedDimension(dimension("rare_dim", "zephyr"))))
            .build();
    LabQueryResult exclLab =
        LabQueryResult.newBuilder()
            .setLabView(
                LabQueryResult.LabView.newBuilder()
                    .setLabTotalCount(1)
                    .addLabData(
                        labData("lab-a", "1.1.1.1", exclIdle0, exclIdle1, exclIdle2, exclBusy3)))
            .build();
    FleetSnapshot exclSnapshot =
        Guice.createInjector().getInstance(FleetIndexBuilder.class).build(exclLab, BUILD_TIME);
    TrackingCorpus exclTracking =
        new TrackingCorpus(
            new DeviceCorpus(
                exclSnapshot, new LazyPostings(exclSnapshot.devices()), new AtsCuration()));
    FleetSuggestionResponse exclResponse =
        suggester.suggest(
            exclTracking,
            request("not zephyr", simple("device_field::status", "IDLE")).toBuilder()
                .setLimit(1)
                .build());
    assertThat(exclResponse.getItemsCount()).isEqualTo(1);
    assertThat(firstApplyFilter(exclResponse, "dimension::rare_dim").getCount()).isEqualTo(3);
    assertThat(exclTracking.keysScannedInScope).containsExactly("dimension::rare_dim");
  }

  /**
   * Delegating {@link SearchCorpus} that records which keys are scanned via {@link #valuesForKey}
   * and how many times {@link #recordCount} and {@link Postings#forKey} (via {@link
   * #postingsForKeyCalls}) are called.
   */
  private static final class TrackingCorpus implements SearchCorpus {
    private final SearchCorpus delegate;
    final Set<String> keysScannedInScope = new HashSet<>();
    int recordCountCalls = 0;
    int postingsForKeyCalls = 0;

    TrackingCorpus(SearchCorpus delegate) {
      this.delegate = delegate;
    }

    @Override
    public FleetIndex index() {
      return delegate.index();
    }

    @Override
    public Postings postings() {
      Postings inner = delegate.postings();
      return new Postings() {
        @Override
        public int[] get(String keyId, String value) {
          return inner.get(keyId, value);
        }

        @Override
        public ImmutableMap<String, int[]> forKey(String keyId) {
          postingsForKeyCalls++;
          return inner.forKey(keyId);
        }
      };
    }

    @Override
    public int recordCount() {
      recordCountCalls++;
      return delegate.recordCount();
    }

    @Override
    public String recordId(int index) {
      return delegate.recordId(index);
    }

    @Override
    public SearchEntity entity() {
      return delegate.entity();
    }

    @Override
    public String identifierKey() {
      return delegate.identifierKey();
    }

    @Override
    public boolean isIdentifierKey(String keyId) {
      return delegate.isIdentifierKey(keyId);
    }

    @Override
    public ImmutableSet<String> valuesForKey(int index, String keyId) {
      keysScannedInScope.add(keyId);
      return delegate.valuesForKey(index, keyId);
    }

    @Override
    public ImmutableList<String> displayValues(int index, String keyId) {
      return delegate.displayValues(index, keyId);
    }

    @Override
    public Column column(String keyId) {
      return delegate.column(keyId);
    }

    @Override
    public Cell cell(int index, String keyId) {
      return delegate.cell(index, keyId);
    }

    @Override
    public Optional<FleetUtilization> utilization(List<Integer> memberIndices) {
      return delegate.utilization(memberIndices);
    }

    @Nullable
    @Override
    public ScenarioCuration curation() {
      return delegate.curation();
    }

    @Override
    public KeyVocabulary vocabulary() {
      return delegate.vocabulary();
    }
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
