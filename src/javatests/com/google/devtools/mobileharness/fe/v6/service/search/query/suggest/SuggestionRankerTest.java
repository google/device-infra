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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestion;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TextSegment;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import com.google.devtools.mobileharness.fe.v6.service.search.query.AtsCuration;
import com.google.devtools.mobileharness.fe.v6.service.search.query.DeviceCorpus;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetFilterEngine;
import com.google.inject.Guice;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SuggestionRanker}. */
@RunWith(JUnit4.class)
public final class SuggestionRankerTest {

  private static final String MODEL = "dimension::model";

  @Test
  public void rank_filteredTieOnCount_evaluatesTheTiedRowAndKeepsTheEarlierMainText() {
    // Under status=IDLE (device-0..2, scope size 3):
    //   add-include row "zzz" for model=pixel: upper bound min(2 fleet-wide, 3) = 2, final count 1
    //       (only device-0 is an IDLE pixel; device-3 is a BUSY pixel);
    //   preset row "aaa": upper bound 1, final count 1.
    // The "zzz" row is visited first and fills the single slot with 1. The "aaa" row's upper bound
    // ties that count, so it must still be evaluated, and it wins because its main text sorts
    // first.
    FleetSnapshot snapshot =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(fleet(), Instant.ofEpochSecond(1_700_000_000L));
    DeviceCorpus corpus =
        new DeviceCorpus(snapshot, new LazyPostings(snapshot.devices()), new AtsCuration());
    Filter idle =
        Filter.newBuilder()
            .setKey("device_field::status")
            .setSimple(
                SimpleMatch.newBuilder().addValues(FilterValue.newBuilder().setValue("IDLE")))
            .build();
    SuggestionContext context =
        SuggestionContext.create(
            corpus,
            ImmutableList.of(idle),
            Guice.createInjector().getInstance(FleetFilterEngine.class));

    SuggestionCandidate addPixel =
        SuggestionCandidate.valueCondition(
            corpus.vocabulary().describe(MODEL).get(),
            MatchTier.EXACT,
            row("zzz"),
            "pixel",
            /* exclude= */ false,
            /* inChip= */ false,
            /* globalCount= */ 2);
    SuggestionCandidate tiedPreset =
        SuggestionCandidate.presetCondition(
            corpus.vocabulary().describe(MODEL).get(),
            MatchTier.EXACT,
            row("aaa"),
            /* count= */ 1,
            /* rankCount= */ 1);

    FleetSuggestionResponse response =
        new SuggestionRanker()
            .rank(context, ImmutableList.of(addPixel, tiedPreset), /* limit= */ 1);

    assertThat(response.getItemsCount()).isEqualTo(1);
    assertThat(response.getItems(0).getMainText(0).getText()).isEqualTo("aaa");
    assertThat(response.getItems(0).getCount()).isEqualTo(1);
  }

  @Test
  public void rank_samePriority_ordersStrongerMatchTierAheadOfHigherCountInWeakerTier() {
    FleetSnapshot snapshot =
        Guice.createInjector()
            .getInstance(FleetIndexBuilder.class)
            .build(fleet(), Instant.ofEpochSecond(1_700_000_000L));
    DeviceCorpus corpus =
        new DeviceCorpus(snapshot, new LazyPostings(snapshot.devices()), new AtsCuration());
    SuggestionContext context =
        SuggestionContext.create(
            corpus,
            ImmutableList.of(),
            Guice.createInjector().getInstance(FleetFilterEngine.class));

    var modelKey = corpus.vocabulary().describe(MODEL).get();
    SuggestionCandidate exactLowCount =
        SuggestionCandidate.presetCondition(
            modelKey, MatchTier.EXACT, row("zzz"), /* count= */ 1, /* rankCount= */ 1);
    SuggestionCandidate prefixMidCount =
        SuggestionCandidate.presetCondition(
            modelKey, MatchTier.PREFIX, row("mmm"), /* count= */ 2, /* rankCount= */ 2);
    SuggestionCandidate weakHighCount =
        SuggestionCandidate.presetCondition(
            modelKey, MatchTier.WEAK, row("aaa"), /* count= */ 3, /* rankCount= */ 3);

    FleetSuggestionResponse response =
        new SuggestionRanker()
            .rank(
                context,
                ImmutableList.of(weakHighCount, prefixMidCount, exactLowCount),
                /* limit= */ 3);

    assertThat(response.getItemsList().stream().map(item -> item.getMainText(0).getText()))
        .containsExactly("zzz", "mmm", "aaa")
        .inOrder();
    assertThat(response.getItemsList().stream().map(FleetSuggestion::getCount))
        .containsExactly(1, 2, 3)
        .inOrder();
  }

  @Test
  public void demoted_returnsNextTierDownOrStaysAtWeak() {
    assertThat(MatchTier.EXACT.demoted()).isEqualTo(MatchTier.PREFIX);
    assertThat(MatchTier.PREFIX.demoted()).isEqualTo(MatchTier.WEAK);
    assertThat(MatchTier.WEAK.demoted()).isEqualTo(MatchTier.WEAK);
  }

  private static FleetSuggestion.Builder row(String mainText) {
    return FleetSuggestion.newBuilder()
        .setLabel("Add filter")
        .addMainText(TextSegment.newBuilder().setText(mainText));
  }

  /** device-0 IDLE pixel, device-1 and device-2 IDLE iphone, device-3 BUSY pixel. */
  private static LabQueryResult fleet() {
    DeviceList devices =
        DeviceList.newBuilder()
            .setDeviceTotalCount(4)
            .addDeviceInfo(device(0, DeviceStatus.IDLE, "pixel"))
            .addDeviceInfo(device(1, DeviceStatus.IDLE, "iphone"))
            .addDeviceInfo(device(2, DeviceStatus.IDLE, "iphone"))
            .addDeviceInfo(device(3, DeviceStatus.BUSY, "pixel"))
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
                                .setLabStatus(LabStatus.LAB_RUNNING))
                        .setDeviceList(devices)))
        .build();
  }

  private static DeviceInfo device(int index, DeviceStatus status, String model) {
    return DeviceInfo.newBuilder()
        .setDeviceLocator(DeviceLocator.newBuilder().setId("device-" + index))
        .setDeviceStatus(status)
        .setDeviceFeature(
            DeviceFeature.newBuilder()
                .addType("android_real_device")
                .setCompositeDimension(
                    DeviceCompositeDimension.newBuilder()
                        .addSupportedDimension(
                            DeviceDimension.newBuilder().setName("model").setValue(model))))
        .build();
  }
}
