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
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetConfigurableDimensionKeysRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceDimensionConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchGetDeviceWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceDimensionConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.BatchUpdateDeviceWifiConfigsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceConfigPermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckDeviceConfigPermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostConfigPermissionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.CheckHostConfigPermissionResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetConfigurableDimensionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDimensionValueSuggestionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetWifiSuggestionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UnlockHostPropertiesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UnlockHostPropertiesResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.DeviceDataLoader;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.IamPermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.WifiCredentialsStore;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import io.grpc.Status;
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
  @Bind @Mock private IamPermissionChecker iamPermissionChecker;
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
  public void checkDeviceConfigPermission_delegatesToHandler() throws Exception {
    CheckDeviceConfigPermissionRequest request =
        CheckDeviceConfigPermissionRequest.newBuilder().setId("device").setUniverse("self").build();
    when(iamPermissionChecker.canConfigDevice(eq("device"), any(UniverseScope.class)))
        .thenReturn(immediateFuture(true));

    CheckDeviceConfigPermissionResponse response =
        configServiceLogicImpl.checkDeviceConfigPermission(request, Optional.of("user")).get();

    assertThat(response.getHasPermission()).isTrue();
    assertThat(response.getUserName()).isEqualTo("user");
  }

  @Test
  public void checkHostConfigPermission_delegatesToHandler() throws Exception {
    CheckHostConfigPermissionRequest request =
        CheckHostConfigPermissionRequest.newBuilder()
            .setHostName("host")
            .setUniverse("self")
            .build();
    when(iamPermissionChecker.canConfigHost(eq("host"), any(UniverseScope.class)))
        .thenReturn(immediateFuture(true));

    CheckHostConfigPermissionResponse response =
        configServiceLogicImpl.checkHostConfigPermission(request, Optional.of("user")).get();

    assertThat(response.getHasPermission()).isTrue();
    assertThat(response.getUserName()).isEqualTo("user");
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

  @Test
  public void batchConfigRpcs_returnUnimplemented() {
    ExecutionException ex1 =
        assertThrows(
            ExecutionException.class,
            () ->
                configServiceLogicImpl
                    .getConfigurableDimensions(
                        GetConfigurableDimensionsRequest.getDefaultInstance())
                    .get());
    assertThat(ex1).hasCauseThat().isInstanceOf(FeServiceException.class);
    assertThat(((FeServiceException) ex1.getCause()).getCode())
        .isEqualTo(Status.Code.UNIMPLEMENTED);

    ExecutionException ex2 =
        assertThrows(
            ExecutionException.class,
            () ->
                configServiceLogicImpl
                    .batchGetConfigurableDimensionKeys(
                        BatchGetConfigurableDimensionKeysRequest.getDefaultInstance())
                    .get());
    assertThat(((FeServiceException) ex2.getCause()).getCode())
        .isEqualTo(Status.Code.UNIMPLEMENTED);

    ExecutionException ex3 =
        assertThrows(
            ExecutionException.class,
            () ->
                configServiceLogicImpl
                    .getDimensionValueSuggestions(
                        GetDimensionValueSuggestionsRequest.getDefaultInstance())
                    .get());
    assertThat(((FeServiceException) ex3.getCause()).getCode())
        .isEqualTo(Status.Code.UNIMPLEMENTED);

    ExecutionException ex4 =
        assertThrows(
            ExecutionException.class,
            () ->
                configServiceLogicImpl
                    .batchGetDeviceDimensionConfigs(
                        BatchGetDeviceDimensionConfigsRequest.getDefaultInstance())
                    .get());
    assertThat(((FeServiceException) ex4.getCause()).getCode())
        .isEqualTo(Status.Code.UNIMPLEMENTED);

    ExecutionException ex5 =
        assertThrows(
            ExecutionException.class,
            () ->
                configServiceLogicImpl
                    .batchUpdateDeviceDimensionConfigs(
                        BatchUpdateDeviceDimensionConfigsRequest.getDefaultInstance())
                    .get());
    assertThat(((FeServiceException) ex5.getCause()).getCode())
        .isEqualTo(Status.Code.UNIMPLEMENTED);

    ExecutionException ex6 =
        assertThrows(
            ExecutionException.class,
            () ->
                configServiceLogicImpl
                    .getWifiSuggestions(GetWifiSuggestionsRequest.getDefaultInstance())
                    .get());
    assertThat(((FeServiceException) ex6.getCause()).getCode())
        .isEqualTo(Status.Code.UNIMPLEMENTED);

    ExecutionException ex7 =
        assertThrows(
            ExecutionException.class,
            () ->
                configServiceLogicImpl
                    .batchGetDeviceWifiConfigs(
                        BatchGetDeviceWifiConfigsRequest.getDefaultInstance())
                    .get());
    assertThat(((FeServiceException) ex7.getCause()).getCode())
        .isEqualTo(Status.Code.UNIMPLEMENTED);

    ExecutionException ex8 =
        assertThrows(
            ExecutionException.class,
            () ->
                configServiceLogicImpl
                    .batchUpdateDeviceWifiConfigs(
                        BatchUpdateDeviceWifiConfigsRequest.getDefaultInstance())
                    .get());
    assertThat(((FeServiceException) ex8.getCause()).getCode())
        .isEqualTo(Status.Code.UNIMPLEMENTED);
  }
}
