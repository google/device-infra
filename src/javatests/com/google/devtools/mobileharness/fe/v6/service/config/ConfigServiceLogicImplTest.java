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

package com.google.devtools.mobileharness.fe.v6.service.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigPusherHelper;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UnlockHostPropertiesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UnlockHostPropertiesResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.WifiCredentialsStore;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ConfigServiceLogicImplTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private DeviceDataLoader deviceDataLoader;
  @Bind @Mock private ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  @Bind private final ListeningExecutorService executor = newDirectExecutorService();
  @Bind @Mock private UniverseFactory universeFactory;
  @Bind @Mock private GroupMembershipProvider groupMembershipProvider;
  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private WifiCredentialsStore wifiCredentialsStore;
  @Bind @Mock private ConfigPusherHelper configPusherHelper;

  private ConfigServiceLogicImpl configServiceLogicImpl;

  @Before
  public void setUp() {
    when(universeFactory.create(anyString())).thenReturn(new UniverseScope.SelfUniverse());
    configServiceLogicImpl =
        Guice.createInjector(BoundFieldModule.of(this)).getInstance(ConfigServiceLogicImpl.class);
  }

  @Test
  public void getDeviceConfig_invalidUniverse_fails() throws Exception {
    GetDeviceConfigRequest request =
        GetDeviceConfigRequest.newBuilder().setId("device").setUniverse("invalid").build();

    when(universeFactory.create("invalid")).thenThrow(new IllegalArgumentException("invalid"));

    ExecutionException e =
        assertThrows(
            ExecutionException.class, () -> configServiceLogicImpl.getDeviceConfig(request).get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void unlockHostProperties_success() throws Exception {
    UnlockHostPropertiesRequest request =
        UnlockHostPropertiesRequest.newBuilder().setHostName("host").setUniverse("self").build();
    LabConfig existingConfig = LabConfig.newBuilder().setHostName("host").build();

    when(configurationProvider.getLabConfig(eq("host"), any(UniverseScope.class)))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(existingConfig))));
    when(configPusherHelper.unlockHostProperties(any(LabConfig.Builder.class)))
        .thenAnswer(
            invocation -> {
              LabConfig.Builder builder = invocation.getArgument(0);
              builder.setHostName("host-modified"); // simulate modification
              return true;
            });
    when(configurationProvider.updateLabConfig(
            eq("host"), any(LabConfig.class), any(UniverseScope.class)))
        .thenReturn(immediateVoidFuture());

    UnlockHostPropertiesResponse response =
        configServiceLogicImpl.unlockHostProperties(request).get();

    assertThat(response.getSuccess()).isTrue();
    verify(configurationProvider)
        .updateLabConfig(eq("host"), any(LabConfig.class), any(UniverseScope.class));
  }

  @Test
  public void unlockHostProperties_invalidUniverse_fails() {
    UnlockHostPropertiesRequest request =
        UnlockHostPropertiesRequest.newBuilder().setHostName("host").setUniverse("invalid").build();

    when(universeFactory.create("invalid")).thenThrow(new IllegalArgumentException("invalid"));

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> configServiceLogicImpl.unlockHostProperties(request).get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }
}
