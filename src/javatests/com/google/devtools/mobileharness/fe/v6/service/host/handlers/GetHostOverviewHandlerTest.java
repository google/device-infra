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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostAuxiliaryInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostReleaseInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostOverview;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
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
public final class GetHostOverviewHandlerTest {

  private static final String HOST_NAME = "test.host";
  private static final GetHostOverviewRequest REQUEST =
      GetHostOverviewRequest.newBuilder().setHostName(HOST_NAME).build();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private HostAuxiliaryInfoProvider hostAuxiliaryInfoProvider;

  @Bind private ListeningExecutorService executorService = newDirectExecutorService();

  @Inject private GetHostOverviewHandler getHostOverviewHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    // Default empty responses
    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(immediateFuture(GetLabInfoResponse.getDefaultInstance()));
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(any()))
        .thenReturn(immediateFuture(Optional.empty()));
    when(hostAuxiliaryInfoProvider.getPassThroughFlags(any()))
        .thenReturn(immediateFuture(Optional.empty()));
  }

  @Test
  public void getHostOverview_noData_returnsUnknown() throws Exception {
    ListenableFuture<HostOverview> result = getHostOverviewHandler.getHostOverview(REQUEST);

    HostOverview overview = Futures.getDone(result);
    assertThat(overview.getLabTypeDisplayNamesList()).containsExactly("Unknown");
    assertThat(overview.getHostName()).isEqualTo(HOST_NAME);
  }

  @Test
  public void getHostOverview_labType_fusion() throws Exception {
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(eq(HOST_NAME)))
        .thenReturn(
            immediateFuture(
                Optional.of(
                    HostReleaseInfo.builder().setLabType(Optional.of("FUSION_LAB")).build())));

    HostOverview overview = Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST));
    assertThat(overview.getLabTypeDisplayNamesList()).containsExactly("Fusion Lab");
  }

  @Test
  public void getHostOverview_labType_core_fromEnum() throws Exception {
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(eq(HOST_NAME)))
        .thenReturn(
            immediateFuture(
                Optional.of(
                    HostReleaseInfo.builder().setLabType(Optional.of("SHARED_LAB")).build())));

    HostOverview overview = Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST));
    assertThat(overview.getLabTypeDisplayNamesList()).containsExactly("Core Lab");
  }

  @Test
  public void getHostOverview_labType_core_fromProp() throws Exception {
    mockLabInfoWithProperty("lab_type", "core");

    HostOverview overview = Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST));
    assertThat(overview.getLabTypeDisplayNamesList()).containsExactly("Core Lab");
  }

  @Test
  public void getHostOverview_labType_slaas() throws Exception {
    mockLabInfoWithProperty("lab_type", "slaas");

    HostOverview overview = Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST));
    assertThat(overview.getLabTypeDisplayNamesList()).containsExactly("Satellite Lab (SLaaS)");
  }

  @Test
  public void getHostOverview_labType_satellite_fromProp() throws Exception {
    mockLabInfoWithProperty("lab_type", "satellite");

    HostOverview overview = Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST));
    assertThat(overview.getLabTypeDisplayNamesList()).containsExactly("Satellite Lab");
  }

  @Test
  public void getHostOverview_labType_ate() throws Exception {
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(eq(HOST_NAME)))
        .thenReturn(
            immediateFuture(
                Optional.of(
                    HostReleaseInfo.builder().setLabType(Optional.of("MH_ATE_LAB")).build())));

    HostOverview overview = Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST));
    assertThat(overview.getLabTypeDisplayNamesList())
        .containsExactly("Satellite Lab", "ATE Lab")
        .inOrder();
  }

  @Test
  public void getHostOverview_labType_field() throws Exception {
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(eq(HOST_NAME)))
        .thenReturn(
            immediateFuture(
                Optional.of(
                    HostReleaseInfo.builder()
                        .setLabType(Optional.of("RIEMANN_FIELD_LAB"))
                        .build())));

    HostOverview overview = Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST));
    assertThat(overview.getLabTypeDisplayNamesList())
        .containsExactly("Satellite Lab", "Riemann Field Lab")
        .inOrder();
  }

  @Test
  public void getHostOverview_withLabInfoProperties() throws Exception {
    GetLabInfoResponse response =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setLabView(
                        LabView.newBuilder()
                            .addLabData(
                                LabData.newBuilder()
                                    .setLabInfo(
                                        LabInfo.newBuilder()
                                            .setLabLocator(LabLocator.newBuilder().setIp("1.2.3.4"))
                                            .setLabServerFeature(
                                                LabServerFeature.newBuilder()
                                                    .setHostProperties(
                                                        HostProperties.newBuilder()
                                                            .addHostProperty(
                                                                HostProperty.newBuilder()
                                                                    .setKey("host_os")
                                                                    .setValue("Linux"))
                                                            .addHostProperty(
                                                                HostProperty.newBuilder()
                                                                    .setKey("other_prop")
                                                                    .setValue("other_val"))))))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), any())).thenReturn(immediateFuture(response));

    HostOverview overview = Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST));

    assertThat(overview.getIp()).isEqualTo("1.2.3.4");
    assertThat(overview.getOs()).isEqualTo("Linux");
    assertThat(overview.getPropertiesMap())
        .containsExactly("host_os", "Linux", "other_prop", "other_val");
  }

  @Test
  public void getHostOverview_withPassThroughFlags() throws Exception {
    String passThroughFlags = "--some_flags";
    when(hostAuxiliaryInfoProvider.getPassThroughFlags(eq(HOST_NAME)))
        .thenReturn(immediateFuture(Optional.of(passThroughFlags)));

    HostOverview overview = Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST));

    assertThat(overview.getLabServer().getPassThroughFlags()).isEqualTo(passThroughFlags);
  }

  private void mockLabInfoWithProperty(String key, String value) {
    GetLabInfoResponse response =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setLabView(
                        LabView.newBuilder()
                            .addLabData(
                                LabData.newBuilder()
                                    .setLabInfo(
                                        LabInfo.newBuilder()
                                            .setLabServerFeature(
                                                LabServerFeature.newBuilder()
                                                    .setHostProperties(
                                                        HostProperties.newBuilder()
                                                            .addHostProperty(
                                                                HostProperty.newBuilder()
                                                                    .setKey(key)
                                                                    .setValue(value))))))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), any())).thenReturn(immediateFuture(response));
  }
}
