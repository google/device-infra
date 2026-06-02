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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.host.builder.RemoteControlUrlBuilder;
import com.google.devtools.mobileharness.fe.v6.service.host.handlers.PreflightLabServerReleaseActionHelper;
import com.google.devtools.mobileharness.fe.v6.service.host.handlers.ReleaseLabServerActionHelper;
import com.google.devtools.mobileharness.fe.v6.service.host.handlers.UpdatePassThroughFlagsActionHelper;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostAuxiliaryInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostLatestVersionProvider;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionHostRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DecommissionHostResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostHeaderInfoRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.PreflightLabServerReleaseRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.PreflightLabServerReleaseResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.SubDeviceInfoListFactory;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityChecker;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.master.rpc.proto.LabSyncServiceProto.RemoveMissingHostResponse;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabSyncStub;
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
  @Bind @Mock private HostLatestVersionProvider hostLatestVersionProvider;
  @Bind @Mock private SubDeviceInfoListFactory subDeviceInfoListFactory;
  @Bind @Mock private RemoteControlEligibilityChecker remoteControlEligibilityChecker;
  @Bind @Mock private RemoteControlUrlBuilder remoteControlUrlBuilder;
  @Bind private final ListeningExecutorService executor = newDirectExecutorService();
  @Bind @Mock private UniverseFactory universeFactory;
  @Bind @Mock private InstantSource instantSource;
  @Bind @Mock private FeatureManagerFactory featureManagerFactory;
  @Bind @Mock private Environment environment;
  @Bind @Mock private LabSyncStub labSyncStub;
  @Bind @Mock private PreflightLabServerReleaseActionHelper preflightLabServerReleaseActionHelper;
  @Bind @Mock private ReleaseLabServerActionHelper releaseLabServerActionHelper;
  @Bind @Mock private UpdatePassThroughFlagsActionHelper updatePassThroughFlagsActionHelper;
  @Mock private FeatureManager featureManager;

  private HostServiceLogicImpl hostServiceLogicImpl;

  @Before
  public void setUp() {
    when(universeFactory.create(anyString())).thenReturn(new UniverseScope.SelfUniverse());
    when(featureManagerFactory.create(any())).thenReturn(featureManager);
    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(immediateFuture(GetLabInfoResponse.getDefaultInstance()));
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(anyString(), any()))
        .thenReturn(immediateFuture(Optional.empty()));
    when(environment.isAts()).thenReturn(false);
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

  @Test
  public void preflightLabServerRelease_success() throws Exception {
    PreflightLabServerReleaseRequest request =
        PreflightLabServerReleaseRequest.newBuilder().setUniverse("universe").build();
    PreflightLabServerReleaseResponse response =
        PreflightLabServerReleaseResponse.getDefaultInstance();

    when(preflightLabServerReleaseActionHelper.preflightLabServerRelease(any(), any(), any()))
        .thenReturn(immediateFuture(response));

    PreflightLabServerReleaseResponse actualResponse =
        hostServiceLogicImpl.preflightLabServerRelease(request, Optional.of("user")).get();

    assertThat(actualResponse).isEqualTo(response);
  }

  @Test
  public void preflightLabServerRelease_invalidUniverse_fails() throws Exception {
    PreflightLabServerReleaseRequest request =
        PreflightLabServerReleaseRequest.newBuilder().setUniverse("invalid").build();

    when(universeFactory.create("invalid")).thenThrow(new IllegalArgumentException("invalid"));

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () ->
                hostServiceLogicImpl.preflightLabServerRelease(request, Optional.of("user")).get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void updatePassThroughFlags_success() throws Exception {
    UpdatePassThroughFlagsRequest request =
        UpdatePassThroughFlagsRequest.newBuilder().setUniverse("universe").build();
    UpdatePassThroughFlagsResponse response = UpdatePassThroughFlagsResponse.getDefaultInstance();

    when(updatePassThroughFlagsActionHelper.updatePassThroughFlags(any(), any()))
        .thenReturn(immediateFuture(response));

    UpdatePassThroughFlagsResponse actualResponse =
        hostServiceLogicImpl.updatePassThroughFlags(request).get();

    assertThat(actualResponse).isEqualTo(response);
  }

  @Test
  public void updatePassThroughFlags_invalidUniverse_fails() throws Exception {
    UpdatePassThroughFlagsRequest request =
        UpdatePassThroughFlagsRequest.newBuilder().setUniverse("invalid").build();

    when(universeFactory.create("invalid")).thenThrow(new IllegalArgumentException("invalid"));

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> hostServiceLogicImpl.updatePassThroughFlags(request).get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void decommissionHost_success() throws Exception {
    DecommissionHostRequest request =
        DecommissionHostRequest.newBuilder().setHostName("host").setUniverse("universe").build();

    when(labSyncStub.removeMissingHost(any(), eq(true)))
        .thenReturn(immediateFuture(RemoveMissingHostResponse.getDefaultInstance()));

    DecommissionHostResponse actualResponse = hostServiceLogicImpl.decommissionHost(request).get();

    assertThat(actualResponse).isEqualTo(DecommissionHostResponse.getDefaultInstance());
  }

  @Test
  public void decommissionHost_invalidUniverse_fails() throws Exception {
    DecommissionHostRequest request =
        DecommissionHostRequest.newBuilder().setUniverse("invalid").build();

    when(universeFactory.create("invalid")).thenThrow(new IllegalArgumentException("invalid"));

    ExecutionException e =
        assertThrows(
            ExecutionException.class, () -> hostServiceLogicImpl.decommissionHost(request).get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }
}
