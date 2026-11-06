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

package com.google.devtools.mobileharness.fe.v6.service.config.handlers;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.BatchDeviceConfigHelper.DeviceEvaluation;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigPusherHelper;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceTarget;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigSection;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.IamPermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class BatchDeviceConfigHelperTest {

  private static final UniverseScope SELF_UNIVERSE = UniverseScope.SELF;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private ConfigPusherHelper configPusherHelper;
  @Bind @Mock private IamPermissionChecker iamPermissionChecker;
  @Bind @Mock private Environment environment;
  @Bind private final ListeningExecutorService executor = newDirectExecutorService();

  @Inject private BatchDeviceConfigHelper helper;

  private FakeBatchFleet fleet;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(environment.isGoogleInternal()).thenReturn(true);
    fleet =
        new FakeBatchFleet(
                labInfoProvider, configurationProvider, iamPermissionChecker, SELF_UNIVERSE)
            .install();
  }

  @Test
  public void evaluateDevices_checksPermissionsInBoundedWavesAndKeepsRequestOrder()
      throws Exception {
    int deviceCount = BatchDeviceConfigHelper.MAX_CONCURRENT_CALLS + 4;
    ImmutableList<DeviceTarget> targets =
        IntStream.range(0, deviceCount)
            .mapToObj(
                i ->
                    DeviceTarget.newBuilder()
                        .setDeviceId("dev" + i)
                        .setHostName("host" + i)
                        .build())
            .collect(toImmutableList());
    targets.forEach(
        target ->
            fleet.addDevice(
                target.getDeviceId(),
                target.getHostName(),
                Optional.of(DeviceConfig.newBuilder().setUuid(target.getDeviceId()).build())));
    Map<String, SettableFuture<ConfigResult<LabConfig>>> pendingLabConfigs = new LinkedHashMap<>();
    when(configurationProvider.getLabConfig(anyString(), eq(SELF_UNIVERSE)))
        .thenAnswer(
            invocation -> {
              SettableFuture<ConfigResult<LabConfig>> future = SettableFuture.create();
              pendingLabConfigs.put(invocation.getArgument(0), future);
              return future;
            });
    Map<String, SettableFuture<Boolean>> pendingChecks = new LinkedHashMap<>();
    when(iamPermissionChecker.canConfigDevice(
            anyString(), any(DeviceConfig.class), eq(SELF_UNIVERSE)))
        .thenAnswer(
            invocation -> {
              SettableFuture<Boolean> check = SettableFuture.create();
              pendingChecks.put(invocation.getArgument(0), check);
              return check;
            });

    ListenableFuture<ImmutableList<DeviceEvaluation>> result =
        helper.evaluateDevices(targets, Optional.of("user1"));

    assertThat(pendingLabConfigs).hasSize(BatchDeviceConfigHelper.MAX_CONCURRENT_CALLS);
    assertThat(pendingChecks).isEmpty();
    ImmutableList.copyOf(pendingLabConfigs.values())
        .forEach(future -> future.set(ConfigResult.available(Optional.empty())));
    assertThat(pendingLabConfigs).hasSize(deviceCount);
    assertThat(pendingChecks).isEmpty();
    ImmutableList.copyOf(pendingLabConfigs.values())
        .forEach(future -> future.set(ConfigResult.available(Optional.empty())));

    assertThat(pendingChecks).hasSize(BatchDeviceConfigHelper.MAX_CONCURRENT_CALLS);
    ImmutableList.copyOf(pendingChecks.values()).forEach(check -> check.set(true));
    assertThat(pendingChecks).hasSize(deviceCount);
    assertThat(result.isDone()).isFalse();
    ImmutableList.copyOf(pendingChecks.values()).forEach(check -> check.set(true));

    ImmutableList<DeviceEvaluation> evaluations = result.get();
    assertThat(evaluations.stream().map(DeviceEvaluation::deviceId))
        .containsExactlyElementsIn(targets.stream().map(DeviceTarget::getDeviceId).toList())
        .inOrder();
    assertThat(evaluations.stream().allMatch(DeviceEvaluation::writable)).isTrue();
    ArgumentCaptor<GetLabInfoRequest> labInfoReqCaptor =
        ArgumentCaptor.forClass(GetLabInfoRequest.class);
    verify(labInfoProvider).getLabInfoAsync(labInfoReqCaptor.capture(), eq(SELF_UNIVERSE));
    assertThat(
            labInfoReqCaptor
                .getValue()
                .getLabQuery()
                .getMask()
                .getDeviceInfoMask()
                .getFieldMask()
                .getPathsList())
        .containsExactly("device_locator.id", "device_locator.lab_locator.host_name");
  }

  @Test
  public void evaluateDevices_disambiguatesCollidingDeviceIdAcrossHostsAndIsolatesInvalidUuids()
      throws Exception {
    LabConfig sharedLab =
        LabConfig.newBuilder()
            .setHostName("host_shared")
            .setHostProperties(
                HostProperties.newBuilder()
                    .addHostProperty(
                        HostProperty.newBuilder().setKey("device_config_mode").setValue("host")))
            .setDefaultDeviceConfig(
                BasicDeviceConfig.newBuilder()
                    .setDefaultWifi(WifiConfig.newBuilder().setSsid("SharedWifi")))
            .build();
    fleet
        .addDevice("emulator-5554", "host_shared", Optional.empty())
        .addDevice(
            "emulator-5554",
            "host_standalone",
            Optional.of(DeviceConfig.newBuilder().setUuid("emulator-5554").build()))
        .addDevice(
            "dup-writable",
            "host_a",
            Optional.of(DeviceConfig.newBuilder().setUuid("dup-writable").build()))
        .addDevice(
            "dup-writable",
            "host_b",
            Optional.of(DeviceConfig.newBuilder().setUuid("dup-writable").build()))
        .addDevice("bad id with spaces", "host_standalone", Optional.empty())
        .setLabConfig("host_shared", sharedLab)
        .setLabConfig("host_a", LabConfig.newBuilder().setHostName("host_a").build())
        .setLabConfig("host_b", LabConfig.newBuilder().setHostName("host_b").build())
        .allowConfig("emulator-5554", true)
        .allowConfig("dup-writable", true);

    ImmutableList<DeviceEvaluation> evaluations =
        helper
            .evaluateDevices(
                ImmutableList.of(
                    DeviceTarget.newBuilder()
                        .setDeviceId("emulator-5554")
                        .setHostName("host_shared")
                        .build(),
                    DeviceTarget.newBuilder()
                        .setDeviceId("emulator-5554")
                        .setHostName("host_standalone")
                        .build(),
                    DeviceTarget.newBuilder()
                        .setDeviceId("emulator-5554")
                        .setHostName("host_missing")
                        .build(),
                    DeviceTarget.newBuilder()
                        .setDeviceId("bad id with spaces")
                        .setHostName("host_standalone")
                        .build(),
                    DeviceTarget.newBuilder()
                        .setDeviceId("dup-writable")
                        .setHostName("host_a")
                        .build()),
                Optional.of("user1"))
            .get();

    assertThat(evaluations).hasSize(5);
    assertThat(evaluations.get(0).hostName()).isEqualTo("host_shared");
    assertThat(evaluations.get(0).writable()).isFalse();
    assertThat(evaluations.get(0).readOnlyReason()).isEqualTo("Managed by host shared config");
    assertThat(evaluations.get(1).hostName()).isEqualTo("host_standalone");
    assertThat(evaluations.get(1).writable()).isFalse();
    assertThat(evaluations.get(1).readOnlyReason())
        .isEqualTo("Managed by host shared config on colliding host host_shared");
    assertThat(evaluations.get(2).hostName()).isEqualTo("host_missing");
    assertThat(evaluations.get(2).readOnlyReason()).isEqualTo("Device not found");
    assertThat(evaluations.get(3).hostName()).isEqualTo("host_standalone");
    assertThat(evaluations.get(3).readOnlyReason()).isEqualTo("Invalid device ID");
    assertThat(evaluations.get(4).hostName()).isEqualTo("host_a");
    assertThat(evaluations.get(4).writable()).isTrue();

    // Malformed/unrecognizable IDs are filtered out before querying DeviceConfigService.
    verify(configurationProvider)
        .getDeviceConfigs(ImmutableList.of("emulator-5554", "dup-writable"), SELF_UNIVERSE);

    // Even when Step 3 sends only (emulator-5554, host_standalone), the sibling host_shared
    // restriction is still enforced so the shared DeviceConfig is never overwritten.
    ImmutableList<DeviceEvaluation> singleTargetEval =
        helper
            .evaluateDevices(
                ImmutableList.of(
                    DeviceTarget.newBuilder()
                        .setDeviceId("emulator-5554")
                        .setHostName("host_standalone")
                        .build()),
                Optional.of("user1"))
            .get();
    assertThat(singleTargetEval).hasSize(1);
    assertThat(singleTargetEval.get(0).writable()).isFalse();
    assertThat(singleTargetEval.get(0).readOnlyReason())
        .isEqualTo("Managed by host shared config on colliding host host_shared");
    assertThat(singleTargetEval.get(0).errorCode()).isEqualTo(UpdateError.Code.VALIDATION_ERROR);

    // Sibling host whose LabConfig read fails or is restricted by Config Pusher also marks the
    // standalone target read-only with the colliding host name and corresponding error code.
    LabConfig pusherLab = LabConfig.newBuilder().setHostName("host_pusher").build();
    when(configPusherHelper.isSectionRestricted(HostConfigSection.DEVICE_CONFIG, pusherLab))
        .thenReturn(true);
    fleet
        .addDevice(
            "dup-unreadable",
            "host_standalone",
            Optional.of(DeviceConfig.newBuilder().setUuid("dup-unreadable").build()))
        .addDevice("dup-unreadable", "host_broken", Optional.empty())
        .failLabConfig("host_broken")
        .allowConfig("dup-unreadable", true)
        .addDevice(
            "dup-pusher",
            "host_standalone",
            Optional.of(DeviceConfig.newBuilder().setUuid("dup-pusher").build()))
        .addDevice("dup-pusher", "host_pusher", Optional.empty())
        .setLabConfig("host_pusher", pusherLab)
        .allowConfig("dup-pusher", true);

    ImmutableList<DeviceEvaluation> otherSiblingBranches =
        helper
            .evaluateDevices(
                ImmutableList.of(
                    DeviceTarget.newBuilder()
                        .setDeviceId("dup-unreadable")
                        .setHostName("host_standalone")
                        .build(),
                    // Empty host_name resolves to the first matching LabDevice (host_standalone)
                    // and still enforces the colliding sibling restriction (host_pusher).
                    DeviceTarget.newBuilder().setDeviceId("dup-pusher").build()),
                Optional.of("user1"))
            .get();
    assertThat(otherSiblingBranches).hasSize(2);
    assertThat(otherSiblingBranches.get(0).writable()).isFalse();
    assertThat(otherSiblingBranches.get(0).readOnlyReason())
        .isEqualTo("Failed to load host configuration on colliding host host_broken");
    assertThat(otherSiblingBranches.get(0).errorCode()).isEqualTo(UpdateError.Code.UNKNOWN);
    assertThat(otherSiblingBranches.get(1).hostName()).isEqualTo("host_standalone");
    assertThat(otherSiblingBranches.get(1).writable()).isFalse();
    assertThat(otherSiblingBranches.get(1).readOnlyReason())
        .isEqualTo("Managed by Config Pusher on colliding host host_pusher");
    assertThat(otherSiblingBranches.get(1).errorCode())
        .isEqualTo(UpdateError.Code.VALIDATION_ERROR);
  }

  @Test
  public void evaluateDevices_emptyRequest_readsNothing() throws Exception {
    assertThat(helper.evaluateDevices(ImmutableList.of(), Optional.of("u")).get()).isEmpty();
  }

  @Test
  public void checkDeviceTargetCount_countsDistinctNormalizedTargets() {
    ImmutableList<DeviceTarget> maxTargets =
        IntStream.range(0, BatchDeviceConfigHelper.MAX_BATCH_DEVICE_IDS)
            .mapToObj(
                i -> DeviceTarget.newBuilder().setDeviceId("dev" + i).setHostName("host1").build())
            .collect(toImmutableList());

    assertThat(
            BatchDeviceConfigHelper.checkDeviceTargetCount(
                ImmutableList.<DeviceTarget>builder()
                    .addAll(maxTargets)
                    .add(
                        DeviceTarget.newBuilder()
                            .setDeviceId("dev0")
                            .setHostName("  host1  ")
                            .build())
                    .build()))
        .isEmpty();
    assertThat(
            BatchDeviceConfigHelper.checkDeviceTargetCount(
                ImmutableList.<DeviceTarget>builder()
                    .addAll(maxTargets)
                    .add(DeviceTarget.newBuilder().setDeviceId("dev0").setHostName("host2").build())
                    .build()))
        .hasValue("A batch request accepts at most 1000 devices, got 1001.");
  }

  @Test
  public void defaultCanConfigDeviceWithLoadedConfig_delegatesToTwoArgMethod() throws Exception {
    IamPermissionChecker delegatingChecker =
        new IamPermissionChecker() {
          @Override
          public ListenableFuture<Boolean> canConfigDevice(
              String deviceId, UniverseScope universe) {
            return immediateFuture(
                deviceId.equals("allowed-device") && universe.equals(SELF_UNIVERSE));
          }

          @Override
          public ListenableFuture<Boolean> canConfigHost(String hostName, UniverseScope universe) {
            return immediateFuture(false);
          }

          @Override
          public ListenableFuture<Boolean> canExecHost(String hostName, UniverseScope universe) {
            return immediateFuture(false);
          }
        };

    assertThat(
            delegatingChecker
                .canConfigDevice("allowed-device", DeviceConfig.getDefaultInstance(), SELF_UNIVERSE)
                .get())
        .isTrue();
  }

  @Test
  public void commitDeviceConfigUpdates_deduplicatesWritableAndBlocksMixedReadOnlySameDeviceId()
      throws Exception {
    when(configurationProvider.updateDeviceConfigs(any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateFuture(ImmutableMap.of()));
    ImmutableList<DeviceEvaluation> evaluations =
        ImmutableList.of(
            // (1) Writable followed by read-only for the same deviceId -> removed from commit map.
            DeviceEvaluation.createWritable("dup-writable-then-ro", "host1", Optional.empty()),
            DeviceEvaluation.createReadOnly(
                "dup-writable-then-ro",
                "host_missing",
                DeviceConfig.getDefaultInstance(),
                BatchDeviceConfigHelper.REASON_DEVICE_NOT_FOUND,
                UpdateError.Code.VALIDATION_ERROR),
            // (2) Read-only followed by writable for the same deviceId -> skipped by errors check.
            DeviceEvaluation.createReadOnly(
                "dup-ro-then-writable",
                "host_missing",
                DeviceConfig.getDefaultInstance(),
                BatchDeviceConfigHelper.REASON_DEVICE_NOT_FOUND,
                UpdateError.Code.VALIDATION_ERROR),
            DeviceEvaluation.createWritable("dup-ro-then-writable", "host1", Optional.empty()),
            // (3) Two writable evaluations on different hosts for the same deviceId -> single write
            DeviceEvaluation.createWritable("dup-both-writable", "host1", Optional.empty()),
            DeviceEvaluation.createWritable("dup-both-writable", "host2", Optional.empty()));

    ImmutableMap<String, UpdateError> errors =
        helper
            .commitDeviceConfigUpdates(
                evaluations,
                config ->
                    config.toBuilder()
                        .setBasicConfig(
                            BasicDeviceConfig.newBuilder()
                                .setDefaultWifi(WifiConfig.newBuilder().setSsid("CorpWifi")))
                        .build(),
                "Default error")
            .get();

    assertThat(errors.keySet()).containsExactly("dup-writable-then-ro", "dup-ro-then-writable");
    assertThat(errors.get("dup-writable-then-ro").getCode())
        .isEqualTo(UpdateError.Code.VALIDATION_ERROR);
    assertThat(errors.get("dup-ro-then-writable").getCode())
        .isEqualTo(UpdateError.Code.VALIDATION_ERROR);
    DeviceConfig expectedUpdatedConfig =
        DeviceConfig.newBuilder()
            .setUuid("dup-both-writable")
            .setBasicConfig(
                BasicDeviceConfig.newBuilder()
                    .setDefaultWifi(WifiConfig.newBuilder().setSsid("CorpWifi")))
            .build();
    verify(configurationProvider)
        .updateDeviceConfigs(
            ImmutableMap.of("dup-both-writable", expectedUpdatedConfig), SELF_UNIVERSE);
  }
}
