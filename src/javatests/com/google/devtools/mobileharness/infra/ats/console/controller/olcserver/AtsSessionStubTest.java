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

package com.google.devtools.mobileharness.infra.ats.console.controller.olcserver;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.ats.common.FlagsString;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.OlcServerConnectorModule;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer.NoOpServerEnvironmentPreparer;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer.ServerEnvironment;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionCancellation;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginNotification;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Failure;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.SessionStub;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.devtools.mobileharness.shared.util.port.PortProber;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.io.PrintStream;
import java.nio.file.Path;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AtsSessionStubTest {

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  private final SystemUtil systemUtil = new SystemUtil();

  @Bind private ListeningExecutorService threadPool;
  @Bind private ListeningScheduledExecutorService scheduledThreadPool;
  @Bind private Sleeper sleeper;
  @Bind private FlagsString deviceInfraServiceFlags;
  @Bind private ServerEnvironmentPreparer serverEnvironmentPreparer;

  @Bind
  @ConsoleOutput(ConsoleOutput.Type.OUT_STREAM)
  private PrintStream outPrintStream;

  @Bind
  @ConsoleOutput(ConsoleOutput.Type.ERR_STREAM)
  private PrintStream errPrintStream;

  @Inject private ServerPreparer serverPreparer;

  @Inject
  @ServerStub(ServerStub.Type.SESSION_SERVICE)
  private SessionStub sessionStub;

  @Inject private AtsSessionStub atsSessionStub;

  @Before
  public void setUp() throws Exception {
    int serverPort = PortProber.pickUnusedPort();
    String publicDirPath = tmpFolder.newFolder("public_dir").toString();
    String tmpDirPath = tmpFolder.newFolder("tmp_dir").toString();

    ImmutableMap<String, String> flagMap =
        ImmutableMap.of(
            "olc_server_port",
            Integer.toString(serverPort),
            "public_dir",
            publicDirPath,
            "detect_adb_device",
            "false",
            "tmp_dir_root",
            tmpDirPath);
    flags.setAllFlags(flagMap);
    ImmutableList<String> flagList =
        flagMap.entrySet().stream()
            .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
            .collect(toImmutableList());
    deviceInfraServiceFlags = FlagsString.of(String.join(" ", flagList), flagList);

    sleeper = Sleeper.defaultSleeper();
    threadPool = ThreadPools.createStandardThreadPool("main-thread");
    scheduledThreadPool =
        ThreadPools.createStandardScheduledThreadPool("main-scheduled-thread", 10);

    outPrintStream = System.out;
    errPrintStream = System.err;

    serverEnvironmentPreparer =
        new NoOpServerEnvironmentPreparer(
            ServerEnvironment.of(
                Path.of(
                    RunfilesUtil.getRunfilesLocation(
                        "java/com/google/devtools/mobileharness/infra/ats/common/"
                            + "olcserver/ats_olc_server_local_mode_deploy.jar")),
                Path.of(systemUtil.getJavaBin()),
                Path.of(System.getProperty("user.dir"))));

    Guice.createInjector(
            new OlcServerConnectorModule(deviceInfraServiceFlags, "ATS console", "fake_client_id"),
            BoundFieldModule.of(this))
        .injectMembers(this);
  }

  @Test
  public void getSession() throws Exception {
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
  public void abortUnstartedSessions() throws Exception {
    serverPreparer.prepareOlcServer();

    atsSessionStub.abortUnstartedSessions();
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
                .setFailure(Failure.newBuilder().setErrorMessage("Unimplemented []"))
                .build());
  }

  @Test
  public void cancelUnfinishedNotAbortedSessions() throws Exception {
    serverPreparer.prepareOlcServer();

    atsSessionStub.cancelUnfinishedNotAbortedSessions(
        /* fromCurrentClient= */ true,
        AtsSessionPluginNotification.newBuilder()
            .setSessionCancellation(AtsSessionCancellation.newBuilder().setReason("Exit command."))
            .build());
  }
}
