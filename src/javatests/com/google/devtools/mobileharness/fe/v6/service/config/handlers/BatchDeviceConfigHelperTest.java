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
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.handlers.BatchDeviceConfigHelper.DeviceEvaluation;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigPusherHelper;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.IamPermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class BatchDeviceConfigHelperTest {

  private static final UniverseScope SELF_UNIVERSE = new UniverseScope.SelfUniverse();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private ConfigPusherHelper configPusherHelper;
  @Bind @Mock private IamPermissionChecker iamPermissionChecker;
  @Mock private ConfigServiceCapability configServiceCapability;
  @Bind @Mock private ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  @Bind @Mock private Environment environment;
  @Bind private final ListeningExecutorService executor = newDirectExecutorService();

  @Inject private BatchDeviceConfigHelper helper;

  private FakeBatchFleet fleet;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(configServiceCapabilityFactory.create(any(UniverseScope.class)))
        .thenReturn(configServiceCapability);
    when(configServiceCapability.isConfigServiceAvailable()).thenReturn(true);
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
    ImmutableList<String> ids =
        IntStream.range(0, deviceCount).mapToObj(i -> "dev" + i).collect(toImmutableList());
    ids.forEach(
        id ->
            fleet.addDevice(
                id,
                "host" + (ids.indexOf(id) % 3),
                Optional.of(DeviceConfig.newBuilder().setUuid(id).build())));
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
        helper.evaluateDevices(ids, SELF_UNIVERSE, Optional.of("user1"));

    assertThat(pendingChecks).hasSize(BatchDeviceConfigHelper.MAX_CONCURRENT_CALLS);
    ImmutableList.copyOf(pendingChecks.values()).forEach(check -> check.set(true));
    assertThat(pendingChecks).hasSize(deviceCount);
    assertThat(result.isDone()).isFalse();
    ImmutableList.copyOf(pendingChecks.values()).forEach(check -> check.set(true));

    ImmutableList<DeviceEvaluation> evaluations = result.get();
    assertThat(evaluations.stream().map(DeviceEvaluation::deviceId))
        .containsExactlyElementsIn(ids)
        .inOrder();
    assertThat(evaluations.stream().allMatch(DeviceEvaluation::writable)).isTrue();
  }

  @Test
  public void evaluateDevices_emptyRequest_readsNothing() throws Exception {
    assertThat(helper.evaluateDevices(ImmutableList.of(), SELF_UNIVERSE, Optional.of("u")).get())
        .isEmpty();
  }

  @Test
  public void checkDeviceIdCount_countsDistinctIds() {
    ImmutableList<String> maxIds =
        IntStream.range(0, BatchDeviceConfigHelper.MAX_BATCH_DEVICE_IDS)
            .mapToObj(i -> "dev" + i)
            .collect(toImmutableList());

    assertThat(
            BatchDeviceConfigHelper.checkDeviceIdCount(
                ImmutableList.<String>builder().addAll(maxIds).add("dev0").build()))
        .isEmpty();
    assertThat(
            BatchDeviceConfigHelper.checkDeviceIdCount(
                ImmutableList.<String>builder().addAll(maxIds).add("one_more").build()))
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
}
