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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ArsenalFilterParam;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
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
import java.util.Optional;
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
    DeviceKeyDescriptor executorKey =
        DeviceKeyDescriptor.builder()
            .setId("device_field::executor")
            .setDisplay(KeyDisplay.plural("Executors"))
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
    HostKeyDescriptor releaseTypeKey =
        HostKeyDescriptor.builder()
            .setId("host_field::release_type")
            .setDisplay(KeyDisplay.of("Release Type"))
            .build();

    DeviceKeyRegistry selfDeviceRegistry =
        new DeviceKeyRegistry(ImmutableList.of(ownerKey, executorKey)) {};
    HostKeyRegistry selfHostRegistry =
        new HostKeyRegistry(ImmutableList.of(releaseStatusKey, releaseTypeKey)) {};
    DeviceKeyRegistry atsDeviceRegistry =
        new DeviceKeyRegistry(ImmutableList.of(projectedAtsController, projectedAtsLabName)) {};
    HostKeyRegistry atsHostRegistry =
        new HostKeyRegistry(ImmutableList.of(atsControllerKey, atsLabNameKey)) {};

    ScenarioCuration selfCuration =
        new FakeCuration(
            selfDeviceRegistry,
            selfHostRegistry,
            ImmutableMap.of(
                "owner", ownerKey.id(),
                "executor", executorKey.id()),
            ImmutableMap.of(
                "release_status", releaseStatusKey.id(),
                "lab_server_release_status", releaseStatusKey.id(),
                "release_type", releaseTypeKey.id()),
            Optional.empty());
    ScenarioCuration atsCuration =
        new FakeCuration(
            atsDeviceRegistry,
            atsHostRegistry,
            ImmutableMap.of("universe", projectedAtsLabName.id()),
            ImmutableMap.of("universe", atsLabNameKey.id()),
            Optional.of(atsControllerKey.id()));
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
            .build();

    TranslateArsenalSearchResponse response = translator.translate(request);

    assertThat(response.getFleet()).isEqualTo(Fleet.FLEET_SELF);
    assertThat(response.getFiltersList().stream().map(Filter::getKey))
        .containsExactly("dimension::pool", "device_field::status", "dimension::quarantined")
        .inOrder();
    assertThat(response.getFilters(0).getSimple().getNegated()).isFalse();
    assertThat(response.getFilters(1).getSimple().getNegated()).isTrue();
    assertThat(response.getFilterChipsCount()).isEqualTo(3);
    assertThat(response.getFilterChips(0).hasValid()).isTrue();

    assertThat(response.getGroupByKeysList()).containsExactly("dimension::model");
    assertThat(response.getGroupByChipsCount()).isEqualTo(1);
    assertThat(response.getGroupByChips(0).getValid().getPillKey()).isEqualTo("Model");

    assertThat(response.getColumnsList().stream().map(FleetColumnDescriptor::getKey))
        .containsExactly(
            "device_field::uuid", "device_field::sdk_or_software_version", "dimension::pool")
        .inOrder();
    assertThat(response.getColumns(0).getLocked()).isTrue();
    assertThat(response.getColumns(1).getDisplayName()).isEqualTo("SDK or Software Version");
  }

  @Test
  public void translateVersion_mapsOnlyInColumnsNotInFilterOrGroupBy() {
    TranslateArsenalSearchRequest request =
        TranslateArsenalSearchRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("version")
                    .setMatchType("match")
                    .setValueType("values")
                    .addValues("34"))
            .addGroupByKeys("version")
            .addColumns("version")
            .build();

    TranslateArsenalSearchResponse response = translator.translate(request);

    assertThat(response.getColumnsList().stream().map(FleetColumnDescriptor::getKey))
        .containsExactly("device_field::uuid", "device_field::sdk_or_software_version")
        .inOrder();
    assertThat(response.getFilters(0).getKey()).isEqualTo("version");
    assertThat(response.getFilterChips(0).hasInvalid()).isTrue();
    assertThat(response.getGroupByKeysList()).containsExactly("version");
    assertThat(response.getGroupByChips(0).hasInvalid()).isTrue();
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
  public void translateUniverse_excludesGoogle1pAndExtraAtsControllers_emitsNegatedControllerId() {
    TranslateArsenalSearchRequest request =
        TranslateArsenalSearchRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("universe")
                    .setMatchType("none_match")
                    .setValueType("values")
                    .addValues("google_1p")
                    .addValues("ats_ctrl_a")
                    .addValues("ats_ctrl_b"))
            .build();

    TranslateArsenalSearchResponse response = translator.translate(request);

    assertThat(response.getFleet()).isEqualTo(Fleet.FLEET_ATS);
    assertThat(response.getFiltersCount()).isEqualTo(1);
    assertThat(response.getFilters(0).getKey()).isEqualTo("host_field::ats_controller_id");
    assertThat(response.getFilters(0).getSimple().getNegated()).isTrue();
    assertThat(response.getFilters(0).getSimple().getValuesList())
        .containsExactly(
            FilterValue.newBuilder().setValue("ats_ctrl_a").build(),
            FilterValue.newBuilder().setValue("ats_ctrl_b").build())
        .inOrder();
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

  @Test
  public void translateCondition_supportsExactMatchAtLeastRegexNonEmptyAndNoneMatch() {
    TranslateArsenalSearchRequest request =
        TranslateArsenalSearchRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("driver")
                    .setMatchType("exact_match")
                    .setValueType("values")
                    .addValues("AndroidInstrumentation")
                    .addValues("MoblyTest"))
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("decorator")
                    .setMatchType("match_at_least")
                    .setValueType("values")
                    .addValues("AndroidLogCatDecorator"))
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("model")
                    .setMatchType("not_match")
                    .setValueType("regex")
                    .addValues(" ^pixel-[67] "))
            .addFilters(ArsenalFilterParam.newBuilder().setKey("owner").setMatchType("non_empty"))
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("executor")
                    .setMatchType("none_match")
                    .setValueType("values")
                    .addValues("bot-a"))
            .build();

    TranslateArsenalSearchResponse response = translator.translate(request);

    assertThat(response.getFiltersCount()).isEqualTo(5);
    assertThat(response.getFilters(0).getComplex().getMatchesExactly().getValuesList())
        .containsExactly("AndroidInstrumentation", "MoblyTest")
        .inOrder();
    assertThat(response.getFilters(1).getComplex().getMatchesAtLeast().getValuesList())
        .containsExactly("AndroidLogCatDecorator");
    assertThat(response.getFilters(2).getComplex().getMatchesRegex().getValue())
        .isEqualTo(" ^pixel-[67] ");
    assertThat(response.getFilters(2).getComplex().getMatchesRegex().getNegated()).isTrue();
    assertThat(response.getFilters(3).getSimple().getValues(0).hasNoValue()).isTrue();
    assertThat(response.getFilters(3).getSimple().getNegated()).isTrue();
    assertThat(response.getFilters(4).getSimple().getNegated()).isTrue();
    assertThat(response.getFilters(4).getSimple().getValues(0).getValue()).isEqualTo("bot-a");
  }

  @Test
  public void translateFilters_unknownKeyProducesInvalidChipAndDuplicateKeyLastWins() {
    TranslateArsenalSearchRequest request =
        TranslateArsenalSearchRequest.newBuilder()
            .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("status")
                    .setMatchType("match")
                    .setValueType("values")
                    .addValues("IDLE"))
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("status")
                    .setMatchType("match")
                    .setValueType("values")
                    .addValues("BUSY"))
            .addFilters(
                ArsenalFilterParam.newBuilder()
                    .setKey("no_such_arsenal_key")
                    .setMatchType("match")
                    .setValueType("values")
                    .addValues("val"))
            .build();

    TranslateArsenalSearchResponse response = translator.translate(request);

    assertThat(response.getFiltersCount()).isEqualTo(2);
    assertThat(response.getFilters(0).getKey()).isEqualTo("device_field::status");
    assertThat(response.getFilters(0).getSimple().getValues(0).getValue()).isEqualTo("BUSY");
    assertThat(response.getFilterChips(0).hasValid()).isTrue();

    assertThat(response.getFilters(1).getKey()).isEqualTo("no_such_arsenal_key");
    assertThat(response.getFilterChips(1).hasInvalid()).isTrue();
  }

  @Test
  public void translateColumns_emptyRequestColumns_usesArsenalDefaultColumns() {
    TranslateArsenalSearchResponse deviceResponse =
        translator.translate(
            TranslateArsenalSearchRequest.newBuilder()
                .setEntity(SearchEntity.SEARCH_ENTITY_DEVICE)
                .build());
    assertThat(deviceResponse.getColumnsList().stream().map(FleetColumnDescriptor::getKey))
        .containsExactly(
            "device_field::uuid",
            "host_field::host_name",
            "device_field::type",
            "dimension::label",
            "dimension::model",
            "device_field::sdk_or_software_version",
            "device_field::status",
            "device_field::owner")
        .inOrder();
    assertThat(deviceResponse.getColumns(0).getLocked()).isTrue();

    TranslateArsenalSearchResponse hostResponse =
        translator.translate(
            TranslateArsenalSearchRequest.newBuilder()
                .setEntity(SearchEntity.SEARCH_ENTITY_HOST)
                .build());
    assertThat(hostResponse.getColumnsList().stream().map(FleetColumnDescriptor::getKey))
        .containsExactly(
            "host_field::host_name",
            "host_field::host_ip",
            "host_property::host_os",
            "host_field::release_status",
            "host_field::release_type")
        .inOrder();
    assertThat(hostResponse.getColumns(0).getLocked()).isTrue();
  }

  @Test
  public void translate_unspecifiedEntity_throwsInvalidArgument() {
    TranslateArsenalSearchRequest request = TranslateArsenalSearchRequest.getDefaultInstance();

    assertThrows(FeServiceException.class, () -> translator.translate(request));
  }

  private static final class FakeCuration implements ScenarioCuration {
    private final DeviceKeyRegistry deviceRegistry;
    private final HostKeyRegistry hostRegistry;
    private final ImmutableMap<String, String> deviceAliases;
    private final ImmutableMap<String, String> hostAliases;
    private final Optional<String> atsControllerIdKey;

    FakeCuration(
        DeviceKeyRegistry deviceRegistry,
        HostKeyRegistry hostRegistry,
        ImmutableMap<String, String> deviceAliases,
        ImmutableMap<String, String> hostAliases,
        Optional<String> atsControllerIdKey) {
      this.deviceRegistry = deviceRegistry;
      this.hostRegistry = hostRegistry;
      this.deviceAliases = deviceAliases;
      this.hostAliases = hostAliases;
      this.atsControllerIdKey = atsControllerIdKey;
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

    @Override
    public ImmutableMap<String, String> arsenalDeviceKeyAliases() {
      return deviceAliases;
    }

    @Override
    public ImmutableMap<String, String> arsenalHostKeyAliases() {
      return hostAliases;
    }

    @Override
    public Optional<String> atsControllerIdKey() {
      return atsControllerIdKey;
    }
  }
}
