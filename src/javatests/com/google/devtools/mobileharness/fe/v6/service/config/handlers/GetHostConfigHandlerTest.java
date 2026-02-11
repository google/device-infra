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
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigMode;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetHostConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigUiStatus;
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
public final class GetHostConfigHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private ConfigServiceCapability configServiceCapability;
  @Bind private ListeningExecutorService executorService = newDirectExecutorService();

  @Inject private GetHostConfigHandler getHostConfigHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(configServiceCapability.isUniverseSupported(any())).thenReturn(true);
    when(configServiceCapability.calculateHostUiStatus())
        .thenReturn(HostConfigUiStatus.getDefaultInstance());
  }

  @Test
  public void getHostConfig_empty() throws Exception {
    when(configurationProvider.getLabConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.empty()));

    GetHostConfigRequest request = GetHostConfigRequest.newBuilder().setHostName("host").build();
    assertThat(getHostConfigHandler.getHostConfig(request).get())
        .isEqualTo(GetHostConfigResponse.getDefaultInstance());
  }

  @Test
  public void getHostConfig_unsupportedUniverse_throwsException() {
    when(configServiceCapability.isUniverseSupported("unsupported")).thenReturn(false);

    GetHostConfigRequest request =
        GetHostConfigRequest.newBuilder().setHostName("host").setUniverse("unsupported").build();

    assertThrows(
        UnsupportedOperationException.class, () -> getHostConfigHandler.getHostConfig(request));
  }

  @Test
  public void getHostConfig_success() throws Exception {
    LabConfig labConfig = LabConfig.newBuilder().setHostName("host").build();
    when(configurationProvider.getLabConfig("host", "universe"))
        .thenReturn(immediateFuture(Optional.of(labConfig)));

    GetHostConfigRequest request =
        GetHostConfigRequest.newBuilder().setHostName("host").setUniverse("universe").build();

    GetHostConfigResponse response = getHostConfigHandler.getHostConfig(request).get();

    assertThat(response.getHostConfig().getDeviceConfigMode())
        .isEqualTo(DeviceConfigMode.PER_DEVICE);
    assertThat(response.getUiStatus()).isEqualTo(HostConfigUiStatus.getDefaultInstance());
  }
}
