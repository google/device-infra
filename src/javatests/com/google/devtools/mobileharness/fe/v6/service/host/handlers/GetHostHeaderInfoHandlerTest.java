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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostAuxiliaryInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostHeaderInfoRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GetHostHeaderInfoHandlerTest {

  private GetHostHeaderInfoHandler handler;
  private LabInfoProvider mockLabInfoProvider;
  private HostAuxiliaryInfoProvider mockHostAuxiliaryInfoProvider;
  private HostHeaderInfoBuilder mockHostHeaderInfoBuilder;

  @Before
  public void setUp() {
    mockLabInfoProvider = mock(LabInfoProvider.class);
    mockHostAuxiliaryInfoProvider = mock(HostAuxiliaryInfoProvider.class);
    mockHostHeaderInfoBuilder = mock(HostHeaderInfoBuilder.class);
    handler =
        new GetHostHeaderInfoHandler(
            mockLabInfoProvider,
            mockHostAuxiliaryInfoProvider,
            MoreExecutors.newDirectExecutorService(),
            mockHostHeaderInfoBuilder);

    when(mockLabInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(Futures.immediateFuture(GetLabInfoResponse.getDefaultInstance()));
    when(mockHostAuxiliaryInfoProvider.getHostReleaseInfo(any(), any()))
        .thenReturn(Futures.immediateFuture(Optional.empty()));
    when(mockHostHeaderInfoBuilder.build(any(), any(), any(), any(), any()))
        .thenReturn(HostHeaderInfo.newBuilder().setHostName("my_host").build());
  }

  @Test
  public void getHostHeaderInfo_routedUniverse_returnsEmptyActions() throws Exception {
    when(mockLabInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(
            Futures.immediateFuture(
                GetLabInfoResponse.newBuilder()
                    .setLabQueryResult(
                        LabQueryResult.newBuilder()
                            .setLabView(
                                LabView.newBuilder()
                                    .addLabData(
                                        LabData.newBuilder()
                                            .setLabInfo(LabInfo.getDefaultInstance()))))
                    .build()));
    GetHostHeaderInfoRequest request =
        GetHostHeaderInfoRequest.newBuilder().setHostName("my_host").build();
    UniverseScope universe = new UniverseScope.RoutedUniverse("");

    HostHeaderInfo info = handler.getHostHeaderInfo(request, universe).get();

    assertThat(info.getHostName()).isEqualTo("my_host");
    assertThat(info.getActions()).isEqualToDefaultInstance();
  }

  @Test
  public void getHostHeaderInfo_selfUniverse_returnsEmptyActions() throws Exception {
    when(mockLabInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(
            Futures.immediateFuture(
                GetLabInfoResponse.newBuilder()
                    .setLabQueryResult(
                        LabQueryResult.newBuilder()
                            .setLabView(
                                LabView.newBuilder()
                                    .addLabData(
                                        LabData.newBuilder()
                                            .setLabInfo(LabInfo.getDefaultInstance()))))
                    .build()));
    GetHostHeaderInfoRequest request =
        GetHostHeaderInfoRequest.newBuilder().setHostName("my_host").build();
    UniverseScope universe = new UniverseScope.SelfUniverse();

    HostHeaderInfo info = handler.getHostHeaderInfo(request, universe).get();

    assertThat(info.getHostName()).isEqualTo("my_host");
    assertThat(info.getActions()).isEqualToDefaultInstance();
  }

  @Test
  public void getHostHeaderInfo_noData_throwsException() throws Exception {
    GetHostHeaderInfoRequest request =
        GetHostHeaderInfoRequest.newBuilder().setHostName("my_host").build();
    UniverseScope universe = new UniverseScope.SelfUniverse();

    ExecutionException exception =
        Assert.assertThrows(
            ExecutionException.class, () -> handler.getHostHeaderInfo(request, universe).get());
    assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(exception)
        .hasCauseThat()
        .hasMessageThat()
        .contains(GetHostOverviewHandler.HOST_NOT_FOUND);
  }
}
