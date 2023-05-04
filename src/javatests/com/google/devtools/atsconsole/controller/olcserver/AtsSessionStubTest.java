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

package com.google.devtools.atsconsole.controller.olcserver;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.atsconsole.Annotations.DeviceInfraServiceFlags;
import com.google.devtools.atsconsole.controller.olcserver.Annotations.ServerStub;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Failure;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.deviceinfra.shared.util.concurrent.ThreadFactoryUtil;
import com.google.devtools.deviceinfra.shared.util.flags.Flags;
import com.google.devtools.deviceinfra.shared.util.port.PortProber;
import com.google.devtools.deviceinfra.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.SessionStub;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AtsSessionStubTest {

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  @Bind private ListeningScheduledExecutorService threadPool;
  @Bind private Sleeper sleeper;
  @Bind @DeviceInfraServiceFlags private ImmutableList<String> deviceInfraServiceFlags;

  @Inject private ServerPreparer serverPreparer;

  @Inject
  @ServerStub(ServerStub.Type.SESSION_SERVICE)
  private SessionStub sessionStub;

  @Inject private AtsSessionStub atsSessionStub;

  @Before
  public void setUp() throws Exception {
    int serverPort = PortProber.pickUnusedPort();
    String publicDirPath = tmpFolder.newFolder("public_dir").toString();

    ImmutableMap<String, String> flagMap =
        ImmutableMap.of(
            "olc_server_port",
            Integer.toString(serverPort),
            "public_dir",
            publicDirPath,
            "detect_adb_device",
            "false");
    deviceInfraServiceFlags =
        flagMap.entrySet().stream()
            .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
            .collect(toImmutableList());
    Flags.parse(deviceInfraServiceFlags.toArray(new String[0]));

    sleeper = Sleeper.defaultSleeper();
    threadPool =
        MoreExecutors.listeningDecorator(
            Executors.newScheduledThreadPool(
                /* corePoolSize= */ 5, ThreadFactoryUtil.createThreadFactory("main-thread")));

    Path serverBinary =
        Path.of(
            RunfilesUtil.getRunfilesLocation(
                "java/com/google/devtools/atsconsole/controller/olcserver/AtsOlcServer_deploy.jar"));

    Guice.createInjector(new OlcServerModule(() -> serverBinary), BoundFieldModule.of(this))
        .injectMembers(this);
  }

  @After
  public void tearDown() {
    Flags.resetToDefault();
  }

  @Test
  public void prepareOlcServer() throws Exception {
    serverPreparer.prepareOlcServer();

    assertThat(
            assertThrows(
                    GrpcExceptionWithErrorId.class,
                    () -> sessionStub.getSession(GetSessionRequest.getDefaultInstance()))
                .getApplicationError()
                .orElseThrow()
                .getErrorId()
                .name())
        .isEqualTo(InfraErrorId.OLCS_GET_SESSION_SESSION_NOT_FOUND.name());
  }

  @Test
  public void runSession() throws Exception {
    serverPreparer.prepareOlcServer();

    ListenableFuture<AtsSessionPluginOutput> outputFuture =
        atsSessionStub.runSession("fake_session", AtsSessionPluginConfig.getDefaultInstance());
    AtsSessionPluginOutput atsSessionPluginOutput = outputFuture.get(1L, MINUTES);

    assertThat(atsSessionPluginOutput)
        .isEqualTo(
            AtsSessionPluginOutput.newBuilder()
                .setFailure(Failure.newBuilder().setErrorMessage("Unimplemented"))
                .build());
  }
}
