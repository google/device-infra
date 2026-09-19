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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ArsenalFilterParam;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TranslateArsenalSearchRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.TranslateArsenalSearchResponse;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.KeyDisplay;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ArsenalSearchTranslator}. */
@RunWith(JUnit4.class)
public final class ArsenalSearchTranslatorTest {

  private ArsenalSearchTranslator translator;

  @Before
  public void setUp() {
    DeviceKeyDescriptor ownerKey =
        DeviceKeyDescriptor.builder()
            .setId("device_field::owner")
            .setDisplay(KeyDisplay.plural("Owners"))
            .build();
    DeviceKeyDescriptor quarantineKey =
        DeviceKeyDescriptor.builder()
            .setId("device_field::quarantined")
            .setDisplay(KeyDisplay.of("Quarantine"))
            .build();
    HostKeyDescriptor atsControllerKey =
        HostKeyDescriptor.builder()
            .setId("host_field::ats_controller_id")
            .setDisplay(KeyDisplay.of("ATS Controller ID"))
            .build();
    HostKeyDescriptor atsLabNameKey =
        HostKeyDescriptor.builder()
            .setId("host_field::ats_lab_display_name")
            .setDisplay(KeyDisplay.of("ATS Lab"))
            .build();
    DeviceKeyDescriptor projectedAtsController =
        DeviceKeyDescriptor.builder()
            .setId("host_field::ats_controller_id")
            .setDisplay(KeyDisplay.of("ATS Controller ID"))
            .build();
    DeviceKeyDescriptor projectedAtsLabName =
        DeviceKeyDescriptor.builder()
            .setId("host_field::ats_lab_display_name")
            .setDisplay(KeyDisplay.of("ATS Lab"))
            .build();
    HostKeyDescriptor releaseStatusKey =
        HostKeyDescriptor.builder()
            .setId("host_field::release_status")
            .setDisplay(KeyDisplay.of("Release Status"))
            .build();

    DeviceKeyRegistry selfDeviceRegistry =
        new DeviceKeyRegistry(ImmutableList.of(ownerKey, quarantineKey)) {};
    HostKeyRegistry selfHostRegistry = new HostKeyRegistry(ImmutableList.of(releaseStatusKey)) {};
    DeviceKeyRegistry atsDeviceRegistry =
        new DeviceKeyRegistry(ImmutableList.of(projectedAtsController, projectedAtsLabName)) {};
    HostKeyRegistry atsHostRegistry =
        new HostKeyRegistry(ImmutableList.of(atsControllerKey, atsLabNameKey)) {};

    ScenarioCuration selfCuration = new FakeCuration(selfDeviceRegistry, selfHostRegistry);
    ScenarioCuration atsCuration = new FakeCuration(atsDeviceRegistry, atsHostRegistry);
    ImmutableMap<Fleet, ScenarioCuration> curations =
        ImmutableMap.of(Fleet.FLEET_SELF, selfCuration, Fleet.FLEET_ATS, atsCuration);

    translator = new ArsenalSearchTranslator(curations, new FleetChipResolver(curations));
  }

  @Test
  public void translateDeviceSearch_mapsKeysConditionsGroupByAndVersionToSdkOrSoftwareVersion() {
    TranslateArsenalSearchRequest request =
        TranslateArsenalSearchRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("universe")
                    .setMatchType("match")
                    .setValueType("values")
                    .addValues("google_1p"))
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("dimension_pool")
                    .setMatchType("any_match")
                    .setValueType("values")
                    .addValues("shared"))
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("status")
                    .setMatchType("not_match")
                    .setValueType("values")
                    .addValues("MISSING"))
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("dimension_quarantined")
                    .setMatchType("match")
                    .setValueType("values")
                    .addValues("true"))
            .addColumns("uuid")
            .addColumns("version")
            .addColumns("dimension_pool")
            .addColumns("required_dimensions")
            .addColumns("universe")
            .addGroupByKeys("model")
            .addGroupByKeys("version")
            .build();

    TranslateArsenalSearchResponse response = translator.translate(request);

    assertThat(response.getFleet()).isEqualTo(Fleet.FLEET_SELF);
    assertThat(response.getFiltersList().stream().map(Filter::getKey))
        .containsExactly("dimension::pool", "device_field::status", "device_field::quarantined")
        .inOrder();
    assertThat(response.getFilters(0).getSimple().getNegated()).isFalse();
    assertThat(response.getFilters(1).getSimple().getNegated()).isTrue();
    assertThat(response.getFilterChipsCount()).isEqualTo(3);
    assertThat(response.getFilterChips(0).hasValid()).isTrue();

    assertThat(response.getGroupByKeysList())
        .containsExactly("dimension::model", "device_field::sdk_or_software_version")
        .inOrder();
    assertThat(response.getGroupByChipsCount()).isEqualTo(2);
    assertThat(response.getGroupByChips(1).getValid().getPillKey())
        .isEqualTo("SDK or Software Version");

    assertThat(response.getColumnsList().stream().map(FleetColumnDescriptor::getKey))
        .containsExactly(
            "device_field::uuid", "device_field::sdk_or_software_version", "dimension::pool")
        .inOrder();
    assertThat(response.getColumns(0).getLocked()).isTrue();
    assertThat(response.getColumns(1).getDisplayName()).isEqualTo("SDK or Software Version");
  }

  @Test
  public void translateUniverse_positiveIncludesGoogle1pAndAts_routesToFleetSelf() {
    TranslateArsenalSearchRequest request =
        TranslateArsenalSearchRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("universe")
                    .setMatchType("any_match")
                    .setValueType("values")
                    .addValues("android_ci_ats")
                    .addValues("google_1p"))
            .build();

    TranslateArsenalSearchResponse response = translator.translate(request);

    assertThat(response.getFleet()).isEqualTo(Fleet.FLEET_SELF);
    assertThat(response.getFiltersList()).isEmpty();
  }

  @Test
  public void translateUniverse_onlyAtsController_routesToFleetAtsAndFiltersControllerId() {
    TranslateArsenalSearchRequest request =
        TranslateArsenalSearchRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("universe")
                    .setMatchType("match")
                    .setValueType("values")
                    .addValues("android_ci_ats"))
            .addColumns("uuid")
            .addColumns("universe")
            .build();

    TranslateArsenalSearchResponse response = translator.translate(request);

    assertThat(response.getFleet()).isEqualTo(Fleet.FLEET_ATS);
    assertThat(response.getFiltersCount()).isEqualTo(1);
    assertThat(response.getFilters(0).getKey()).isEqualTo("host_field::ats_controller_id");
    assertThat(response.getFilters(0).getSimple().getValues(0).getValue())
        .isEqualTo("android_ci_ats");
    assertThat(response.getColumnsList().stream().map(FleetColumnDescriptor::getKey))
        .containsExactly("device_field::uuid", "host_field::ats_lab_display_name")
        .inOrder();
  }

  @Test
  public void translateUniverse_excludesGoogle1p_routesToFleetAts() {
    TranslateArsenalSearchRequest request =
        TranslateArsenalSearchRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("universe")
                    .setMatchType("not_match")
                    .setValueType("values")
                    .addValues("google_1p"))
            .build();

    TranslateArsenalSearchResponse response = translator.translate(request);

    assertThat(response.getFleet()).isEqualTo(Fleet.FLEET_ATS);
    assertThat(response.getFiltersList()).isEmpty();
  }

  @Test
  public void translateHostSearch_mapsLabServerReleaseStatusAndComplexConditions() {
    TranslateArsenalSearchRequest request =
        TranslateArsenalSearchRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("host_name")
                    .setMatchType("match")
                    .setValueType("substring")
                    .addValues("mtv"))
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("host_property_host_group")
                    .setMatchType("empty"))
            .addColumns("lab_server_release_status")
            .addColumns("host_os")
            .build();

    TranslateArsenalSearchResponse response = translator.translate(request);

    assertThat(response.getFleet()).isEqualTo(Fleet.FLEET_SELF);
    assertThat(response.getFiltersCount()).isEqualTo(2);
    assertThat(response.getFilters(0).getKey()).isEqualTo("host_field::host_name");
    assertThat(response.getFilters(0).getComplex().getContainsSubstring().getValue())
        .isEqualTo("mtv");
    assertThat(response.getFilters(1).getKey()).isEqualTo("host_property::host_group");
    assertThat(response.getFilters(1).getSimple().getValues(0).hasNoValue()).isTrue();
    assertThat(response.getColumnsList().stream().map(FleetColumnDescriptor::getKey))
        .containsExactly(
            "host_field::host_name", "host_field::release_status", "host_property::host_os")
        .inOrder();
    assertThat(response.getColumns(0).getLocked()).isTrue();
  }

  private static final class FakeCuration implements ScenarioCuration {
    private final DeviceKeyRegistry deviceRegistry;
    private final HostKeyRegistry hostRegistry;

    FakeCuration(DeviceKeyRegistry deviceRegistry, HostKeyRegistry hostRegistry) {
      this.deviceRegistry = deviceRegistry;
      this.hostRegistry = hostRegistry;
    }

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
      return ImmutableList.of(DeviceKeys.UUID);
    }

    @Override
    public ImmutableList<DeviceKeyDescriptor> deviceRecommendedColumns() {
      return ImmutableList.of(DeviceKeys.UUID);
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
      return ImmutableList.of(HostKeys.HOST_NAME);
    }

    @Override
    public ImmutableList<HostKeyDescriptor> hostRecommendedColumns() {
      return ImmutableList.of(HostKeys.HOST_NAME);
    }

    @Override
    public KeyPriority keyPriority() {
      return FleetKeyPriority.INSTANCE;
    }

    @Override
    public DeviceKeyRegistry deviceKeyRegistry() {
      return deviceRegistry;
    }

    @Override
    public HostKeyRegistry hostKeyRegistry() {
      return hostRegistry;
    }

    @Override
    public boolean landingEnabled() {
      return true;
    }
  }
}
