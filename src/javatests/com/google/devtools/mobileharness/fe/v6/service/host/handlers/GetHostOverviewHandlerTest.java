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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
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
import com.google.devtools.mobileharness.fe.v6.service.proto.common.Universe;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DiagnosticLink;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DiagnosticLink.Category;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostOverviewRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostOverview;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostOverviewPageData;
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
public final class GetHostOverviewHandlerTest {

  private static final String HOST_NAME = "test.host";
  private static final GetHostOverviewRequest REQUEST =
      GetHostOverviewRequest.newBuilder().setHostName(HOST_NAME).build();
  private static final Universe UNIVERSE = Universe.getDefaultInstance();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind @Mock private HostAuxiliaryInfoProvider hostAuxiliaryInfoProvider;
  @Bind @Mock private Environment environment;

  @Bind private ListeningExecutorService executorService = newDirectExecutorService();

  @Inject private GetHostOverviewHandler getHostOverviewHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    // Default empty responses
    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(immediateFuture(GetLabInfoResponse.getDefaultInstance()));
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(any(), any()))
        .thenReturn(immediateFuture(Optional.empty()));
    when(hostAuxiliaryInfoProvider.getPassThroughFlags(any(), any()))
        .thenReturn(immediateFuture(Optional.empty()));
    when(hostAuxiliaryInfoProvider.getDiagnosticLinks(anyString(), any(), any()))
        .thenReturn(immediateFuture(ImmutableList.of()));
    when(environment.isGoogleInternal()).thenReturn(true);
  }

  @Test
  public void getHostOverview_noData_returnsUnknown() throws Exception {
    ListenableFuture<HostOverviewPageData> result =
        getHostOverviewHandler.getHostOverview(REQUEST, UNIVERSE);

    HostOverview overview = Futures.getDone(result).getOverviewContent();
    assertThat(overview.getLabTypeDisplayNamesList()).containsExactly("Unknown");
    assertThat(overview.getHostName()).isEqualTo(HOST_NAME);
  }

  @Test
  public void getHostOverview_labType_fusion() throws Exception {
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(eq(HOST_NAME), any()))
        .thenReturn(
            immediateFuture(
                Optional.of(
                    HostReleaseInfo.builder().setLabType(Optional.of("FUSION_LAB")).build())));

    HostOverview overview =
        Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST, UNIVERSE))
            .getOverviewContent();
    assertThat(overview.getLabTypeDisplayNamesList()).containsExactly("Fusion Lab");
  }

  @Test
  public void getHostOverview_labType_core_fromEnum() throws Exception {
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(eq(HOST_NAME), any()))
        .thenReturn(
            immediateFuture(
                Optional.of(
                    HostReleaseInfo.builder().setLabType(Optional.of("SHARED_LAB")).build())));

    HostOverview overview =
        Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST, UNIVERSE))
            .getOverviewContent();
    assertThat(overview.getLabTypeDisplayNamesList()).containsExactly("Core Lab");
  }

  @Test
  public void getHostOverview_labType_core_fromProp() throws Exception {
    mockLabInfoWithProperty("lab_type", "core");

    HostOverview overview =
        Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST, UNIVERSE))
            .getOverviewContent();
    assertThat(overview.getLabTypeDisplayNamesList()).containsExactly("Core Lab");
  }

  @Test
  public void getHostOverview_labType_slaas() throws Exception {
    mockLabInfoWithProperty("lab_type", "slaas");

    HostOverview overview =
        Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST, UNIVERSE))
            .getOverviewContent();
    assertThat(overview.getLabTypeDisplayNamesList()).containsExactly("Satellite Lab (SLaaS)");
  }

  @Test
  public void getHostOverview_labType_satellite_fromProp() throws Exception {
    mockLabInfoWithProperty("lab_type", "satellite");

    HostOverview overview =
        Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST, UNIVERSE))
            .getOverviewContent();
    assertThat(overview.getLabTypeDisplayNamesList()).containsExactly("Satellite Lab");
  }

  @Test
  public void getHostOverview_labType_ate() throws Exception {
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(eq(HOST_NAME), any()))
        .thenReturn(
            immediateFuture(
                Optional.of(
                    HostReleaseInfo.builder().setLabType(Optional.of("MH_ATE_LAB")).build())));

    HostOverview overview =
        Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST, UNIVERSE))
            .getOverviewContent();
    assertThat(overview.getLabTypeDisplayNamesList())
        .containsExactly("Satellite Lab", "ATE Lab")
        .inOrder();
  }

  @Test
  public void getHostOverview_labType_field() throws Exception {
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(eq(HOST_NAME), any()))
        .thenReturn(
            immediateFuture(
                Optional.of(
                    HostReleaseInfo.builder()
                        .setLabType(Optional.of("RIEMANN_FIELD_LAB"))
                        .build())));

    HostOverview overview =
        Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST, UNIVERSE))
            .getOverviewContent();
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

    HostOverview overview =
        Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST, UNIVERSE))
            .getOverviewContent();

    assertThat(overview.getIp()).isEqualTo("1.2.3.4");
    assertThat(overview.getOs()).isEqualTo("Linux");
    assertThat(overview.getPropertiesMap())
        .containsExactly("host_os", "Linux", "other_prop", "other_val");
  }

  @Test
  public void getHostOverview_withPassThroughFlags() throws Exception {
    String passThroughFlags = "--some_flags";
    when(hostAuxiliaryInfoProvider.getPassThroughFlags(eq(HOST_NAME), any()))
        .thenReturn(immediateFuture(Optional.of(passThroughFlags)));

    HostOverview overview =
        Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST, UNIVERSE))
            .getOverviewContent();

    assertThat(overview.getLabServer().getPassThroughFlags()).isEqualTo(passThroughFlags);
  }

  @Test
  public void getHostOverview_diagnosticLinks() throws Exception {
    String mtaasHost = "test.mtaas.google.com";
    GetHostOverviewRequest mtaasRequest =
        GetHostOverviewRequest.newBuilder().setHostName(mtaasHost).build();
    mockLabInfoWithIp("1.2.3.4");
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(eq(mtaasHost), any()))
        .thenReturn(
            immediateFuture(
                Optional.of(
                    HostReleaseInfo.builder().setLabType(Optional.of("FUSION_LAB")).build())));

    when(hostAuxiliaryInfoProvider.getDiagnosticLinks(eq(mtaasHost), any(), any()))
        .thenReturn(
            immediateFuture(
                ImmutableList.of(
                    DiagnosticLink.newBuilder()
                        .setLabel("View Log")
                        .setCategory(Category.LAB_SERVER)
                        .setUrl("logs/viewer?advancedFilter=encoded%20filter%3Dval")
                        .build(),
                    DiagnosticLink.newBuilder()
                        .setLabel("View Log")
                        .setCategory(Category.DAEMON_SERVER)
                        .build(),
                    DiagnosticLink.newBuilder()
                        .setLabel("View STATUSZ")
                        .setUrl("mtaas.omnilab.goog/test/statusz")
                        .setCategory(Category.OVERVIEW)
                        .build())));

    HostOverview overview =
        Futures.getDone(getHostOverviewHandler.getHostOverview(mtaasRequest, UNIVERSE))
            .getOverviewContent();

    assertThat(overview.getDiagnosticLinksList()).hasSize(3);

    // Lab Server Log
    assertThat(overview.getDiagnosticLinks(0).getLabel()).isEqualTo("View Log");
    assertThat(overview.getDiagnosticLinks(0).getCategory()).isEqualTo(Category.LAB_SERVER);
    assertThat(overview.getDiagnosticLinks(0).getUrl()).contains("logs/viewer?advancedFilter=");

    // Daemon Server Log
    assertThat(overview.getDiagnosticLinks(1).getLabel()).isEqualTo("View Log");
    assertThat(overview.getDiagnosticLinks(1).getCategory()).isEqualTo(Category.DAEMON_SERVER);

    // statusz
    assertThat(overview.getDiagnosticLinks(2).getLabel()).isEqualTo("View STATUSZ");
    assertThat(overview.getDiagnosticLinks(2).getUrl()).contains("mtaas.omnilab.goog/test/statusz");
    assertThat(overview.getDiagnosticLinks(2).getCategory()).isEqualTo(Category.OVERVIEW);
  }

  @Test
  public void getHostOverview_statuszLink_visibility() throws Exception {
    mockLabInfoWithIp("1.2.3.4");

    // Case 1: mtaas.google.com + FUSION_LAB -> Included
    String mtaasHost = "host.mtaas.google.com";
    when(hostAuxiliaryInfoProvider.getDiagnosticLinks(eq(mtaasHost), any(), any()))
        .thenReturn(
            immediateFuture(
                ImmutableList.of(DiagnosticLink.newBuilder().setLabel("View STATUSZ").build())));
    HostOverview overview1 =
        Futures.getDone(
                getHostOverviewHandler.getHostOverview(
                    GetHostOverviewRequest.newBuilder().setHostName(mtaasHost).build(), UNIVERSE))
            .getOverviewContent();
    assertThat(
            overview1.getDiagnosticLinksList().stream()
                .anyMatch(link -> link.getLabel().equals("View STATUSZ")))
        .isTrue();

    // Case 2: NOT mtaas.google.com + FUSION_LAB -> Excluded (Mock Provider to return empty)
    String otherHost = "host.other.google.com";
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(eq(otherHost), any()))
        .thenReturn(
            immediateFuture(
                Optional.of(
                    HostReleaseInfo.builder().setLabType(Optional.of("FUSION_LAB")).build())));
    when(hostAuxiliaryInfoProvider.getDiagnosticLinks(eq(otherHost), any(), any()))
        .thenReturn(immediateFuture(ImmutableList.of()));
    HostOverview overview2 =
        Futures.getDone(
                getHostOverviewHandler.getHostOverview(
                    GetHostOverviewRequest.newBuilder().setHostName(otherHost).build(), UNIVERSE))
            .getOverviewContent();
    assertThat(
            overview2.getDiagnosticLinksList().stream()
                .anyMatch(link -> link.getLabel().equals("View STATUSZ")))
        .isFalse();

    // Case 3: mtaas.google.com + NOT FUSION_LAB -> Excluded (Mock Provider to return empty)
    when(hostAuxiliaryInfoProvider.getHostReleaseInfo(eq(mtaasHost), any()))
        .thenReturn(
            immediateFuture(
                Optional.of(
                    HostReleaseInfo.builder().setLabType(Optional.of("MH_ATE_LAB")).build())));
    when(hostAuxiliaryInfoProvider.getDiagnosticLinks(eq(mtaasHost), any(), any()))
        .thenReturn(immediateFuture(ImmutableList.of()));
    HostOverview overview3 =
        Futures.getDone(
                getHostOverviewHandler.getHostOverview(
                    GetHostOverviewRequest.newBuilder().setHostName(mtaasHost).build(), UNIVERSE))
            .getOverviewContent();
    assertThat(
            overview3.getDiagnosticLinksList().stream()
                .anyMatch(link -> link.getLabel().equals("View STATUSZ")))
        .isFalse();
  }

  @Test
  public void getHostOverview_externalEnvironment_noDiagnosticLinks() throws Exception {
    when(environment.isGoogleInternal()).thenReturn(false);
    mockLabInfoWithIp("1.2.3.4");

    HostOverview overview =
        Futures.getDone(getHostOverviewHandler.getHostOverview(REQUEST, UNIVERSE))
            .getOverviewContent();

    assertThat(overview.getDiagnosticLinksList()).isEmpty();
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

  private void mockLabInfoWithIp(String ip) {
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
                                            .setLabLocator(LabLocator.newBuilder().setIp(ip))))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), any())).thenReturn(immediateFuture(response));
  }
}
