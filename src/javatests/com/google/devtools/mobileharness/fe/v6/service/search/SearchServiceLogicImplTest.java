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

package com.google.devtools.mobileharness.fe.v6.service.search;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFlatView;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupExpandView;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupHeaderView;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedKeysRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPromotedKeysResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSuggestionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetValueListRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetValueListResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.index.CoreFleetRawData;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndexBuilder;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.DimensionOverlayRaw;
import com.google.devtools.mobileharness.fe.v6.service.search.pull.FleetDataSource;
import com.google.devtools.mobileharness.fe.v6.service.search.query.ScenarioCurationModule;
import com.google.devtools.mobileharness.fe.v6.service.search.refresh.FleetSnapshotStore;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.summary.GlobalSummaryProvider;
import com.google.devtools.mobileharness.fe.v6.service.search.summary.NoOpGlobalSummaryProvider;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.multibindings.MapBinder;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Wiring tests for {@link SearchServiceLogicImpl}.
 *
 * <p>These validate that the logic reads the published snapshot for the request's fleet, resolves
 * the ats-one curation, and dispatches to the right query class. The per-query behavior is covered
 * by each query class's own test, so these assert only that a representative subset of RPCs return
 * sane results end to end through the injector.
 */
@RunWith(JUnit4.class)
public final class SearchServiceLogicImplTest {

  private static final Instant BUILD_TIME = Instant.ofEpochSecond(1_700_000_000L);

  private SearchServiceLogicImpl logic;

  @Before
  public void setUp() {
    // The OSS curation module binds the ats-one curation under FLEET_SELF, which the suggester,
    // promoted-keys provider, and config method resolve. The query classes have @Inject
    // constructors, so the injector builds the whole logic graph.
    // Bind the logic's executor to a direct executor so its submitted scans run inline, letting the
    // ListenableFuture that each RPC returns resolve synchronously for the assertions below.
    Injector injector =
        Guice.createInjector(
            new ScenarioCurationModule(),
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(ListeningExecutorService.class).toInstance(newDirectExecutorService());
                bind(GlobalSummaryProvider.class).to(NoOpGlobalSummaryProvider.class);
                MapBinder.newMapBinder(binder(), Fleet.class, FleetDataSource.class)
                    .addBinding(Fleet.FLEET_SELF)
                    .toInstance(
                        new FleetDataSource() {
                          @Override
                          public Fleet fleet() {
                            return Fleet.FLEET_SELF;
                          }

                          @Override
                          public ListenableFuture<CoreFleetRawData> pull() {
                            throw new UnsupportedOperationException();
                          }

                          @Override
                          public ListenableFuture<DimensionOverlayRaw> pullDimension(
                              DeviceKeyDescriptor dimensionKey) {
                            return immediateFuture(
                                DimensionOverlayRaw.create(
                                    dimensionKey.id(),
                                    ImmutableMap.of(
                                        "device-0", ImmutableList.of("mh"),
                                        "device-1", ImmutableList.of("mh"),
                                        "device-2", ImmutableList.of("mh"))));
                          }
                        });
              }
            });
    FleetSnapshotStore store = injector.getInstance(FleetSnapshotStore.class);
    FleetSnapshot snapshot =
        injector.getInstance(FleetIndexBuilder.class).build(fleet(), BUILD_TIME);
    store.publish(Fleet.FLEET_SELF, snapshot);
    logic = injector.getInstance(SearchServiceLogicImpl.class);
  }

  @Test
  public void getFleetSearchConfig_returnsCuratedColumnsAndDeviceCount() throws Exception {
    FleetSearchConfig config =
        logic
            .getFleetSearchConfig(
                FleetSearchConfigRequest.newBuilder()
                    .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
                    .setFleet(Fleet.FLEET_SELF)
                    .build())
            .get();

    // The ats-one curation's default columns lead with the device identifier, which is locked.
    assertThat(config.getColumns().getDefaultsList()).isNotEmpty();
    FleetColumnDescriptor first = config.getColumns().getDefaults(0);
    assertThat(first.getKey()).isEqualTo("device_field::uuid");
    assertThat(first.getLocked()).isTrue();
    assertThat(config.getColumns().getRecommendedList()).isNotEmpty();
    // The synthetic fleet has three devices, and the ats-one build browses directly (no landing).
    assertThat(config.getLanding().getBrowseAllCount()).isEqualTo(3);
    assertThat(config.getLanding().getEnabled()).isFalse();
  }

  @Test
  public void searchFleet_flat_returnsRowsForEveryDevice() throws Exception {
    FleetSearchResults results =
        logic
            .searchFleet(
                FleetSearchRequest.newBuilder()
                    .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
                    .setFleet(Fleet.FLEET_SELF)
                    .setFlat(
                        FleetFlatView.newBuilder()
                            .addColumns("device_field::uuid")
                            .addColumns("device_field::status"))
                    .build())
            .get();

    assertThat(results.getModeCase()).isEqualTo(FleetSearchResults.ModeCase.FLAT);
    assertThat(results.getFlat().getColumnsList()).hasSize(2);
    assertThat(results.getFlat().getRowsList()).hasSize(3);
    assertThat(results.getFlat().getTotal()).isEqualTo(3);
  }

  @Test
  public void getFleetPromotedKeys_returnsFilterRows() throws Exception {
    FleetPromotedKeysResponse response =
        logic
            .getFleetPromotedKeys(
                FleetPromotedKeysRequest.newBuilder()
                    .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
                    .setFleet(Fleet.FLEET_SELF)
                    .build())
            .get();

    // The ats-one curation promotes filter keys, and the synthetic fleet carries several of them
    // with more than one distinct value, so the "Filter by:" row is not empty.
    assertThat(response.getFilterKeysList()).isNotEmpty();
  }

  @Test
  public void unspecifiedFleet_readsSelfSnapshot() throws Exception {
    // FLEET_UNSPECIFIED normalizes to FLEET_SELF, so a request that leaves the fleet unset reads
    // the same published snapshot.
    FleetSearchResults results =
        logic
            .searchFleet(
                FleetSearchRequest.newBuilder()
                    .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
                    .setFlat(FleetFlatView.newBuilder().addColumns("device_field::uuid"))
                    .build())
            .get();

    assertThat(results.getFlat().getTotal()).isEqualTo(3);
  }

  @Test
  public void longTailOverlay_valueListPromotedSuggestionsAndGroupExpand_allBindOverlay()
      throws Exception {
    Filter overlayFilter =
        Filter.newBuilder()
            .setKey("dimension::provisioned_by")
            .setSimple(SimpleMatch.newBuilder().addValues(FilterValue.newBuilder().setValue("mh")))
            .build();

    // 1. Value list with same long-tail key in both request.key and request.filters succeeds.
    FleetValueListResponse valueList =
        logic
            .getFleetValueList(
                FleetValueListRequest.newBuilder()
                    .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
                    .setFleet(Fleet.FLEET_SELF)
                    .setKey("dimension::provisioned_by")
                    .addFilters(overlayFilter)
                    .build())
            .get();
    assertThat(valueList.getCounted().getValuesList()).hasSize(1);
    assertThat(valueList.getCounted().getValues(0).getValue()).isEqualTo("mh");
    assertThat(valueList.getCounted().getValues(0).getTotal()).isEqualTo(3);

    // 2. Promoted keys with active long-tail filter binds overlay and retains non-empty filter
    // keys.
    FleetPromotedKeysResponse promoted =
        logic
            .getFleetPromotedKeys(
                FleetPromotedKeysRequest.newBuilder()
                    .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
                    .setFleet(Fleet.FLEET_SELF)
                    .addFilters(overlayFilter)
                    .build())
            .get();
    assertThat(promoted.getFilterKeysList()).isNotEmpty();

    // 3. Suggestions with active long-tail filter binds overlay and reports non-zero counts.
    FleetSuggestionResponse suggestions =
        logic
            .getFleetSuggestions(
                FleetSuggestionRequest.newBuilder()
                    .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
                    .setFleet(Fleet.FLEET_SELF)
                    .setInput("IDLE")
                    .addFilters(overlayFilter)
                    .build())
            .get();
    assertThat(suggestions.getItemsList()).isNotEmpty();
    assertThat(suggestions.getItems(0).getCount()).isEqualTo(2);

    // 4. Grouped search by long-tail dimension followed by GROUP_EXPAND binds overlay from groupId.
    FleetSearchResults grouped =
        logic
            .searchFleet(
                FleetSearchRequest.newBuilder()
                    .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
                    .setFleet(Fleet.FLEET_SELF)
                    .setGroupHeader(
                        FleetGroupHeaderView.newBuilder().addGroupBy("dimension::provisioned_by"))
                    .build())
            .get();
    assertThat(grouped.getGrouped().getGroupsList()).hasSize(1);
    String groupId = grouped.getGrouped().getGroups(0).getGroupId();

    FleetSearchResults expanded =
        logic
            .searchFleet(
                FleetSearchRequest.newBuilder()
                    .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
                    .setFleet(Fleet.FLEET_SELF)
                    .setGroupExpand(
                        FleetGroupExpandView.newBuilder()
                            .setGroupId(groupId)
                            .addColumns("device_field::uuid"))
                    .build())
            .get();
    assertThat(expanded.getFlat().getRowsList()).hasSize(3);
  }

  // --- Synthetic fleet: three devices across two hosts. ---

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
