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

import static com.google.protobuf.TextFormat.shortDebugString;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.DeviceInfraServiceFlags;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.Annotations.ServerBinary;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ControlStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.VersionStub;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import io.grpc.Status.Code;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/** Server preparer for preparing an OLC server instance. */
@Singleton
public class ServerPreparer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ConsoleUtil consoleUtil;
  private final CommandExecutor commandExecutor;
  private final Sleeper sleeper;
  private final SystemUtil systemUtil;
  private final LocalFileUtil localFileUtil;
  private final ControlStub controlStub;
  private final VersionStub versionStub;
  private final Provider<Path> serverBinary;
  private final ImmutableList<String> deviceInfraServiceFlags;

  private final AtomicBoolean serverPrepared = new AtomicBoolean();

  @Inject
  ServerPreparer(
      ConsoleUtil consoleUtil,
      CommandExecutor commandExecutor,
      Sleeper sleeper,
      SystemUtil systemUtil,
      LocalFileUtil localFileUtil,
      @ServerStub(ServerStub.Type.CONTROL_SERVICE) ControlStub controlStub,
      @ServerStub(ServerStub.Type.VERSION_SERVICE) VersionStub versionStub,
      @ServerBinary Provider<Path> serverBinary,
      @DeviceInfraServiceFlags ImmutableList<String> deviceInfraServiceFlags) {
    this.consoleUtil = consoleUtil;
    this.commandExecutor = commandExecutor;
    this.sleeper = sleeper;
    this.systemUtil = systemUtil;
    this.localFileUtil = localFileUtil;
    this.controlStub = controlStub;
    this.versionStub = versionStub;
    this.serverBinary = serverBinary;
    this.deviceInfraServiceFlags = deviceInfraServiceFlags;
  }

  public void prepareOlcServer() throws MobileHarnessException, InterruptedException {
    if (!serverPrepared.compareAndSet(false, true)) {
      return;
    }

    // Tries to get server version.
    GetVersionResponse version = null;
    try {
      version = versionStub.getVersion();
    } catch (GrpcExceptionWithErrorId e) {
      if (!e.getUnderlyingRpcException().getStatus().getCode().equals(Code.UNAVAILABLE)) {
        throw new MobileHarnessException(
            InfraErrorId.ATSC_SERVER_PREPARER_CONNECT_EXISTING_OLC_SERVER_ERROR,
            "Failed to connect to existing OLC server",
            e);
      }
    }
    if (version != null) {
      consoleUtil.printlnStderr(
          "Connected to existing OLC server, version=[%s]", shortDebugString(version));

      if (!needKillExistingServer(version) || !killExistingServer()) {
        consoleUtil.printlnStderr("Using existing OLC server");
        return;
      }
    }

    // Starts a new server if not exists.
    consoleUtil.printlnStderr("Starting new OLC server...");
    String serverBinaryPath = requireNonNull(serverBinary.get()).toString();
    localFileUtil.checkFile(serverBinaryPath);

    CommandProcess serverProcess = null;
    CountDownLatch serverStartedLatch = new CountDownLatch(1);
    AtomicBoolean serverStartedSuccessfully = new AtomicBoolean();
    try {
      try {
        serverProcess =
            commandExecutor.start(
                Command.of(
                        systemUtil
                            .getJavaCommandCreator()
                            .createJavaCommand(
                                serverBinaryPath,
                                deviceInfraServiceFlags,
                                /* nativeArguments= */ ImmutableList.of()))
                    .onStderr(
                        new ServerStderrLineCallback(
                            consoleUtil, serverStartedLatch, serverStartedSuccessfully))
                    .onExit(result -> serverStartedLatch.countDown())
                    .timeout(ChronoUnit.YEARS.getDuration())
                    .redirectStderr(false)
                    .needStdoutInResult(false)
                    .needStderrInResult(false));
      } catch (CommandStartException e) {
        throw new MobileHarnessException(
            InfraErrorId.ATSC_SERVER_PREPARER_START_OLC_SERVER_ERROR,
            "Failed to start OLC server",
            e);
      }

      // Waits until the server starts.
      logger.atFine().log("Wait until OLC server starts, command=[%s]", serverProcess.command());
      if (!serverStartedLatch.await(40L, SECONDS)) {
        throw new MobileHarnessException(
            InfraErrorId.ATSC_SERVER_PREPARER_OLC_SERVER_INITIALIZE_ERROR,
            "OLC server didn't start in 30 seconds");
      }
      if (!serverStartedSuccessfully.get()) {
        throw new MobileHarnessException(
            InfraErrorId.ATSC_SERVER_PREPARER_OLC_SERVER_ABNORMAL_EXIT_WHILE_INITIALIZATION,
            "OLC server exited abnormally while initialization");
      }
      connectWithRetry();
      consoleUtil.printlnStderr(
          "OLC server started, port=%s, pid=%s",
          Flags.instance().olcServerPort.getNonNull(), serverProcess.getUnixPidIfAny().orElse(0));
    } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
      if (serverProcess != null && serverProcess.isAlive()) {
        consoleUtil.printlnStderr("Killing OLC server");
        serverProcess.kill();
      }
      throw e;
    }
  }

  private boolean killExistingServer() throws InterruptedException {
    consoleUtil.printlnStderr("Killing existing OLC server...");
    KillServerResponse killServerResponse = null;
    try {
      killServerResponse = controlStub.killServer();
    } catch (GrpcExceptionWithErrorId e) {
      logger.atInfo().withCause(e).log("RPC exception when sending kill server request");
    }
    if (killServerResponse != null && !killServerResponse.getSuccessful()) {
      return false;
    }

    // Waits until the existing server is killed.
    for (int i = 0; i < 10; i++) {
      sleeper.sleep(Duration.ofSeconds(1L));
      try {
        versionStub.getVersion();
      } catch (GrpcExceptionWithErrorId e) {
        consoleUtil.printlnStderr("Existing OLC server killed");
        return true;
      }
    }
    return false;
  }

  private void connectWithRetry() throws MobileHarnessException, InterruptedException {
    int count = 0;
    while (true) {
      try {
        versionStub.getVersion();
        return;
      } catch (GrpcExceptionWithErrorId e) {
        count++;
        if (count == 15) {
          throw new MobileHarnessException(
              InfraErrorId.ATSC_SERVER_PREPARER_CONNECT_NEW_OLC_SERVER_ERROR,
              "Failed to connect new OLC server",
              e);
        } else {
          sleeper.sleep(Duration.ofSeconds(1L));
        }
      }
    }
  }

  /**
   * Whether the preparer needs to "kill an existing OLC server (if any) and restart a new one" when
   * preparing server, or just reuse the existing server (if any).
   */
  private static boolean needKillExistingServer(
      @SuppressWarnings("unused") GetVersionResponse version) {
    // TODO: Checks server version. If too old, kills it and starts a new one.

    // If the flag is specified, always kills the existing server.
    return Flags.instance().atsConsoleAlwaysRestartOlcServer.getNonNull();
  }

  private static class ServerStderrLineCallback implements LineCallback {

    private static final String SERVER_STARTED_SIGNAL = "OLC server started";

    private final ConsoleUtil consoleUtil;
    private final CountDownLatch serverStartedLatch;
    private final AtomicBoolean serverStartedSuccessfully;

    private ServerStderrLineCallback(
        ConsoleUtil consoleUtil,
        CountDownLatch serverStartedLatch,
        AtomicBoolean serverStartedSuccessfully) {
      this.consoleUtil = consoleUtil;
      this.serverStartedLatch = serverStartedLatch;
      this.serverStartedSuccessfully = serverStartedSuccessfully;
    }

    @Override
    public Response onLine(String stderrLine) {
      consoleUtil.printlnStderr("olc_server_stderr: %s", stderrLine);

      if (stderrLine.contains(SERVER_STARTED_SIGNAL)) {
        serverStartedSuccessfully.set(true);
        serverStartedLatch.countDown();
        return Response.stopReadingOutput();
      } else {
        return Response.empty();
      }
    }
  }
}
