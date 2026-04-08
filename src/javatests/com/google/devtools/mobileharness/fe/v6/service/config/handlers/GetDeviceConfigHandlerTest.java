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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapability;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigUiStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
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
public final class GetDeviceConfigHandlerTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Mock private ConfigServiceCapability configServiceCapability;
  @Bind @Mock private ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  @Bind private ListeningExecutorService executorService = newDirectExecutorService();
  @Inject private GetDeviceConfigHandler getDeviceConfigHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(configServiceCapabilityFactory.create(anyString())).thenReturn(configServiceCapability);
    when(configServiceCapabilityFactory.create(any(UniverseScope.class)))
        .thenReturn(configServiceCapability);
    when(configServiceCapability.calculateDeviceUiStatus())
        .thenReturn(DeviceConfigUiStatus.getDefaultInstance());
    when(labInfoProvider.getLabInfoAsync(any(), any(UniverseScope.class)))
        .thenReturn(
            immediateFuture(
                GetLabInfoResponse.newBuilder()
                    .setLabQueryResult(
                        LabQueryResult.newBuilder()
                            .setDeviceView(
                                DeviceView.newBuilder()
                                    .setGroupedDevices(
                                        GroupedDevices.newBuilder()
                                            .setDeviceList(
                                                DeviceList.newBuilder()
                                                    .addDeviceInfo(
                                                        DeviceInfo.getDefaultInstance())))))
                    .build()));
    when(configurationProvider.getLabConfig(anyString(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(Optional.empty()));
    when(configurationProvider.getDeviceConfig(anyString(), any(UniverseScope.class)))
        .thenReturn(immediateFuture(Optional.empty()));
  }

  @Test
  public void getDeviceConfig_success() throws Exception {
    GetDeviceConfigRequest request = GetDeviceConfigRequest.newBuilder().setId("test").build();
    GetDeviceConfigResponse response = getDeviceConfigHandler.getDeviceConfig(request).get();
    assertThat(response.getIsHostManaged()).isFalse();
  }

  @Test
  public void getDeviceConfig_unsupportedUniverse_throwsException() throws Exception {
    doThrow(new UnsupportedOperationException("Unsupported"))
        .when(configServiceCapability)
        .checkConfigServiceAvailability();
    GetDeviceConfigRequest request =
        GetDeviceConfigRequest.newBuilder().setId("test").setUniverse("unsupported").build();
    assertThrows(
        ExecutionException.class, () -> getDeviceConfigHandler.getDeviceConfig(request).get());
  }
}
