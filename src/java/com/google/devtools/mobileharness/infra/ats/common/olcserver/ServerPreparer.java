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

package com.google.devtools.mobileharness.infra.ats.common.olcserver;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.protobuf.TextFormat.shortDebugString;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.DeviceInfraServiceFlags;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerBinary;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse.ResultCase;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ControlStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.VersionStub;
import com.google.devtools.mobileharness.shared.util.base.TableFormatter;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.errorprone.annotations.FormatMethod;
import io.grpc.Status.Code;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/** Server preparer for preparing an OLC server instance. */
@Singleton
public class ServerPreparer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableList<String> UNFINISHED_SESSIONS_TABLE_HEADER =
      ImmutableList.of("Session ID", "Name", "Status", "Submitted Time");

  private static final String SERVER_STARTED_SIGNAL = "OLC server started";

  /** Logger for printing logs of OLC server until it starts successfully. */
  @FunctionalInterface
  public interface ServerStartingLogger {
    @FormatMethod
    void log(String format, Object... args);
  }

  private final ServerStartingLogger serverStartingLogger;
  private final CommandExecutor commandExecutor;
  private final Sleeper sleeper;
  private final SystemUtil systemUtil;
  private final LocalFileUtil localFileUtil;
  private final Provider<ControlStub> controlStub;
  private final Provider<VersionStub> versionStub;
  private final Provider<Path> serverBinary;
  private final ImmutableList<String> deviceInfraServiceFlags;

  private final AtomicBoolean serverPrepared = new AtomicBoolean();

  @Inject
  ServerPreparer(
      ServerStartingLogger serverStartingLogger,
      CommandExecutor commandExecutor,
      Sleeper sleeper,
      SystemUtil systemUtil,
      LocalFileUtil localFileUtil,
      @ServerStub(ServerStub.Type.CONTROL_SERVICE) Provider<ControlStub> controlStub,
      @ServerStub(ServerStub.Type.VERSION_SERVICE) Provider<VersionStub> versionStub,
      @ServerBinary Provider<Path> serverBinary,
      @DeviceInfraServiceFlags ImmutableList<String> deviceInfraServiceFlags) {
    this.serverStartingLogger = serverStartingLogger;
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
      version = versionStub.get().getVersion();
    } catch (GrpcExceptionWithErrorId e) {
      if (!e.getUnderlyingRpcException().getStatus().getCode().equals(Code.UNAVAILABLE)) {
        throw new MobileHarnessException(
            InfraErrorId.ATSC_SERVER_PREPARER_CONNECT_EXISTING_OLC_SERVER_ERROR,
            "Failed to connect to existing OLC server",
            e);
      }
    }
    if (version != null) {
      serverStartingLogger.log(
          "Connected to existing OLC server, version=[%s]", shortDebugString(version));

      if (needKillExistingServer(version)) {
        killExistingServer();
      } else {
        serverStartingLogger.log("Using existing OLC server");
        return;
      }
    }

    // Starts a new server if not exists.
    serverStartingLogger.log("Starting new OLC server...");
    String serverBinaryPath = requireNonNull(serverBinary.get()).toString();
    localFileUtil.checkFile(serverBinaryPath);

    CommandProcess serverProcess = null;
    ServerStderrLineCallback serverStderrLineCallback = new ServerStderrLineCallback();
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
                    .onStderr(serverStderrLineCallback)
                    .successfulStartCondition(line -> line.contains(SERVER_STARTED_SIGNAL))
                    .timeout(ChronoUnit.YEARS.getDuration())
                    .redirectStderr(false)
                    .needStdoutInResult(false)
                    .needStderrInResult(false));
        addCallback(
            serverProcess.successfulStartFuture(),
            new ServerSuccessfulStartCallback(serverProcess),
            directExecutor());
      } catch (CommandStartException e) {
        throw new MobileHarnessException(
            InfraErrorId.ATSC_SERVER_PREPARER_START_OLC_SERVER_ERROR,
            "Failed to start OLC server",
            e);
      }

      // Waits until the server starts.
      logger.atFine().log("Wait until OLC server starts, command=[%s]", serverProcess.command());
      try {
        if (!serverProcess.successfulStartFuture().get(40L, SECONDS)) {
          throw new MobileHarnessException(
              InfraErrorId.ATSC_SERVER_PREPARER_OLC_SERVER_ABNORMAL_EXIT_WHILE_INITIALIZATION,
              "OLC server exited abnormally while initialization");
        }
      } catch (ExecutionException e) {
        throw new AssertionError(e);
      } catch (TimeoutException unused) {
        throw new MobileHarnessException(
            InfraErrorId.ATSC_SERVER_PREPARER_OLC_SERVER_INITIALIZE_ERROR,
            "OLC server didn't start in 40 seconds");
      }
      connectWithRetry();
      serverStartingLogger.log(
          "OLC server started, port=%s, pid=%s",
          Flags.instance().olcServerPort.getNonNull(), serverProcess.getUnixPidIfAny().orElse(0));
    } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
      // Kills the server.
      if (serverProcess != null && serverProcess.isAlive()) {
        serverStartingLogger.log("Killing OLC server");
        serverProcess.kill();
      }

      // Prints stderr of the server.
      String serverStartingLog = serverStderrLineCallback.getServerStartingLog();
      if (!serverStartingLog.isEmpty()) {
        serverStartingLogger.log("olc_server_stderr=[%s]", serverStartingLog);
      }
      throw e;
    }
  }

  public void killExistingServer() throws MobileHarnessException, InterruptedException {
    serverStartingLogger.log("Killing existing OLC server...");
    KillServerResponse killServerResponse;
    try {
      killServerResponse = controlStub.get().killServer(KillServerRequest.getDefaultInstance());
    } catch (GrpcExceptionWithErrorId e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_SERVER_PREPARER_KILL_EXISTING_OLC_SERVER_RPC_ERROR,
          "Failed to call KillServer of OLC server",
          e);
    }
    long serverPid = killServerResponse.getServerPid();
    if (killServerResponse.getResultCase() == ResultCase.FAILURE) {
      throw MobileHarnessExceptionFactory.create(
          InfraErrorId.ATSC_SERVER_PREPARER_CANNOT_KILL_EXISTING_OLC_SERVER_ERROR,
          String.format(
              "Existing OLC server (pid=%s) cannot be killed since it has running sessions:\n%s",
              serverPid, createUnfinishedSessionsTable(killServerResponse)),
          /* cause= */ null,
          /* addErrorIdToMessage= */ false,
          /* clearStackTrace= */ true);
    }

    // Waits until the existing server is killed.
    for (int i = 0; i < 10; i++) {
      sleeper.sleep(Duration.ofSeconds(1L));
      try {
        versionStub.get().getVersion();
      } catch (GrpcExceptionWithErrorId e) {
        serverStartingLogger.log("Existing OLC server (pid=%s) killed", serverPid);
        return;
      }
    }
    throw new MobileHarnessException(
        InfraErrorId.ATSC_SERVER_PREPARER_EXISTING_OLC_SERVER_STILL_RUNNING_ERROR,
        String.format(
            "Existing OLC server (pid=%s) is still running for 10s after it was killed",
            serverPid));
  }

  private void connectWithRetry() throws MobileHarnessException, InterruptedException {
    int count = 0;
    while (true) {
      try {
        versionStub.get().getVersion();
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
  private boolean needKillExistingServer(GetVersionResponse version) {
    // Checks flag.
    if (Flags.instance().atsConsoleAlwaysRestartOlcServer.getNonNull()) {
      logger.atInfo().log(
          "Need to kill existing OLC server because --ats_console_always_restart_olc_server=true");
      return true;
    }

    // Checks server version.
    String serverVersionString = version.getLabVersion();
    try {
      Version serverVersion = new Version(serverVersionString);
      Version requiredServerVersion = Version.LAB_VERSION;
      if (serverVersion.compareTo(requiredServerVersion) < 0) {
        serverStartingLogger.log(
            "Existing OLC server version [%s] is older than [%s]",
            serverVersion, requiredServerVersion);
        return true;
      } else {
        return false;
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().log("Invalid OLC server version [%s]", serverVersionString);
      return true;
    }
  }

  private static String createUnfinishedSessionsTable(KillServerResponse killServerResponse) {
    return TableFormatter.displayTable(
        Stream.concat(
                Stream.of(UNFINISHED_SESSIONS_TABLE_HEADER),
                killServerResponse.getFailure().getUnfinishedSessionsList().stream()
                    .map(
                        sessionDetail ->
                            ImmutableList.of(
                                sessionDetail.getSessionId().getId(),
                                sessionDetail.getSessionConfig().getSessionName(),
                                sessionDetail.getSessionStatus().name(),
                                TimeUtils.toJavaInstant(
                                        sessionDetail
                                            .getSessionOutput()
                                            .getSessionTimingInfo()
                                            .getSessionSubmittedTime())
                                    .toString())))
            .collect(toImmutableList()));
  }

  private static class ServerSuccessfulStartCallback implements FutureCallback<Boolean> {

    private final CommandProcess commandProcess;

    private ServerSuccessfulStartCallback(CommandProcess commandProcess) {
      this.commandProcess = commandProcess;
    }

    @Override
    public void onSuccess(Boolean result) {
      if (Objects.equals(result, Boolean.TRUE)) {
        commandProcess.stopReadingOutput();
      }
    }

    @Override
    public void onFailure(Throwable t) {}
  }

  private static class ServerStderrLineCallback implements LineCallback {

    @GuardedBy("itself")
    private final StringBuilder serverStartingLog = new StringBuilder();

    @Override
    public Response onLine(String stderrLine) {
      synchronized (serverStartingLog) {
        serverStartingLog.append(stderrLine).append('\n');
      }
      return Response.empty();
    }

    private String getServerStartingLog() {
      synchronized (serverStartingLog) {
        return serverStartingLog.toString();
      }
    }
  }
}
