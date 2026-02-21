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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostDefaultDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostDefaultDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Optional;
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
public final class GetHostDefaultDeviceConfigHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private ConfigServiceCapability configServiceCapability;
  @Bind private ListeningExecutorService executorService = newDirectExecutorService();

  @Inject private GetHostDefaultDeviceConfigHandler getHostDefaultDeviceConfigHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(configServiceCapability.isUniverseSupported(any())).thenReturn(true);
  }

  @Test
  public void getHostDefaultDeviceConfig_empty() throws Exception {
    when(configurationProvider.getLabConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.empty()));

    GetHostDefaultDeviceConfigRequest request =
        GetHostDefaultDeviceConfigRequest.newBuilder().setHostName("host").build();
    assertThat(getHostDefaultDeviceConfigHandler.getHostDefaultDeviceConfig(request).get())
        .isEqualTo(GetHostDefaultDeviceConfigResponse.getDefaultInstance());
  }

  @Test
  public void getHostDefaultDeviceConfig_unsupportedUniverse_throwsException() {
    when(configServiceCapability.isUniverseSupported("unsupported")).thenReturn(false);

    GetHostDefaultDeviceConfigRequest request =
        GetHostDefaultDeviceConfigRequest.newBuilder()
            .setHostName("host")
            .setUniverse("unsupported")
            .build();

    assertThrows(
        UnsupportedOperationException.class,
        () -> getHostDefaultDeviceConfigHandler.getHostDefaultDeviceConfig(request));
  }

  @Test
  public void getHostDefaultDeviceConfig_success() throws Exception {
    LabConfig labConfig =
        LabConfig.newBuilder()
            .setHostName("host")
            .setDefaultDeviceConfig(BasicDeviceConfig.newBuilder().addOwner("owner").build())
            .build();
    when(configurationProvider.getLabConfig("host", "universe"))
        .thenReturn(immediateFuture(Optional.of(labConfig)));

    GetHostDefaultDeviceConfigRequest request =
        GetHostDefaultDeviceConfigRequest.newBuilder()
            .setHostName("host")
            .setUniverse("universe")
            .build();

    GetHostDefaultDeviceConfigResponse response =
        getHostDefaultDeviceConfigHandler.getHostDefaultDeviceConfig(request).get();

    assertThat(response.getDeviceConfig().getPermissions().getOwnersList())
        .containsExactly("owner");
  }

  @Test
  public void getHostDefaultDeviceConfig_success_noDefault() throws Exception {
    LabConfig labConfig = LabConfig.newBuilder().setHostName("host").build();
    when(configurationProvider.getLabConfig("host", "universe"))
        .thenReturn(immediateFuture(Optional.of(labConfig)));

    GetHostDefaultDeviceConfigRequest request =
        GetHostDefaultDeviceConfigRequest.newBuilder()
            .setHostName("host")
            .setUniverse("universe")
            .build();

    GetHostDefaultDeviceConfigResponse response =
        getHostDefaultDeviceConfigHandler.getHostDefaultDeviceConfig(request).get();

    assertThat(response.getDeviceConfig())
        .isEqualTo(
            com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig
                .getDefaultInstance());
  }
}
