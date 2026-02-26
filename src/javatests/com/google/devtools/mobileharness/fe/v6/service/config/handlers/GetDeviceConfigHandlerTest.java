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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.fe.v6.service.device.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetDeviceConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
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
public final class GetDeviceConfigHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private Environment environment;
  @Bind private ListeningExecutorService executorService = newDirectExecutorService();

  @Inject private GetDeviceConfigHandler getDeviceConfigHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    when(environment.isGoogleInternal()).thenReturn(true);
    when(labInfoProvider.getLabInfoAsync(any(), any()))
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
    when(configurationProvider.getLabConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.empty()));
    when(configurationProvider.getDeviceConfig(any(), any()))
        .thenReturn(immediateFuture(Optional.empty()));
  }

  @Test
  public void getDeviceConfig_success() throws Exception {
    GetDeviceConfigRequest request =
        GetDeviceConfigRequest.newBuilder().setDeviceId("test").setUniverse("google_1p").build();
    GetDeviceConfigResponse response = getDeviceConfigHandler.getDeviceConfig(request).get();
    assertThat(response.getIsHostManaged()).isFalse();
  }
}
