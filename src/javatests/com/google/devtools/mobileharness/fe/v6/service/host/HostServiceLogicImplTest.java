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

package com.google.devtools.mobileharness.fe.v6.service.host;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.host.builder.RemoteControlUrlBuilder;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostAuxiliaryInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostHeaderInfoRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.shared.SubDeviceInfoListFactory;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityChecker;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.time.InstantSource;
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
public final class HostServiceLogicImplTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private HostAuxiliaryInfoProvider hostAuxiliaryInfoProvider;
  @Bind @Mock private SubDeviceInfoListFactory subDeviceInfoListFactory;
  @Bind @Mock private RemoteControlEligibilityChecker remoteControlEligibilityChecker;
  @Bind @Mock private RemoteControlUrlBuilder remoteControlUrlBuilder;
  @Bind private final ListeningExecutorService executor = newDirectExecutorService();
  @Bind @Mock private UniverseFactory universeFactory;
  @Bind @Mock private InstantSource instantSource;
  @Bind @Mock private FeatureManagerFactory featureManagerFactory;
  @Mock private FeatureManager featureManager;

  private HostServiceLogicImpl hostServiceLogicImpl;

  @Before
  public void setUp() {
    when(universeFactory.create(anyString())).thenReturn(new UniverseScope.SelfUniverse());
    when(featureManagerFactory.create(any())).thenReturn(featureManager);
    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(Futures.immediateFuture(GetLabInfoResponse.getDefaultInstance()));
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(anyString(), any()))
        .thenReturn(Futures.immediateFuture(Optional.empty()));
    hostServiceLogicImpl =
        Guice.createInjector(BoundFieldModule.of(this)).getInstance(HostServiceLogicImpl.class);
  }

  @Test
  public void getHostHeaderInfo_success() throws Exception {
    GetHostHeaderInfoRequest request =
        GetHostHeaderInfoRequest.newBuilder().setHostName("host").build();

    HostHeaderInfo response = hostServiceLogicImpl.getHostHeaderInfo(request).get();

    assertThat(response.getHostName()).isEqualTo("host");
    assertThat(response.getActions().hasConfiguration()).isTrue();
    assertThat(response.getActions().hasDebug()).isTrue();
    assertThat(response.getActions().hasDecommission()).isTrue();
  }

  @Test
  public void getHostHeaderInfo_invalidUniverse_fails() throws Exception {
    GetHostHeaderInfoRequest request =
        GetHostHeaderInfoRequest.newBuilder().setHostName("host").setUniverse("invalid").build();

    when(universeFactory.create("invalid")).thenThrow(new IllegalArgumentException("invalid"));

    ExecutionException e =
        assertThrows(
            ExecutionException.class, () -> hostServiceLogicImpl.getHostHeaderInfo(request).get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }
}
