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
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.DEBUG;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static com.google.protobuf.TextFormat.shortDebugString;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ClientComponentName;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ClientId;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.DeviceInfraServiceFlags;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.OlcServerJavaPath;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerBinary;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.HeartbeatRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse.Failure;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse.ResultCase;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ControlStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.VersionStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.VersionProtoUtil;
import com.google.devtools.mobileharness.shared.util.base.TableFormatter;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.command.java.JavaCommandCreator;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.errorprone.annotations.FormatMethod;
import io.grpc.Status.Code;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/** Server preparer for preparing an OLC server instance. */
@Singleton
public class ServerPreparer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10L);

  private static final ImmutableList<String> UNFINISHED_SESSIONS_TABLE_HEADER =
      ImmutableList.of("Session ID", "Name", "Status", "Submitted Time");

  private static final String SERVER_STARTED_SIGNAL = "OLC server started";

  /** Logger for printing logs of OLC server until it starts successfully. */
  @FunctionalInterface
  public interface ServerStartingLogger {
    @FormatMethod
    void log(String format, Object... args);
  }

  private final String clientComponentName;
  private final String clientId;
  private final ServerStartingLogger serverStartingLogger;
  private final CommandExecutor commandExecutor;
  private final Sleeper sleeper;
  private final SystemUtil systemUtil;
  private final LocalFileUtil localFileUtil;
  private final Provider<ControlStub> controlStub;
  private final Provider<VersionStub> versionStub;
  private final Provider<Path> serverBinary;
  private final Provider<Path> javaPath;
  private final ImmutableList<String> deviceInfraServiceFlags;
  private final ListeningScheduledExecutorService scheduledThreadPool;

  private final Object prepareServerLock = new Object();

  @GuardedBy("prepareServerLock")
  private boolean hasPrepared;

  @Inject
  ServerPreparer(
      @ClientComponentName String clientComponentName,
      @ClientId String clientId,
      ServerStartingLogger serverStartingLogger,
      CommandExecutor commandExecutor,
      Sleeper sleeper,
      SystemUtil systemUtil,
      LocalFileUtil localFileUtil,
      @ServerStub(ServerStub.Type.CONTROL_SERVICE) Provider<ControlStub> controlStub,
      @ServerStub(ServerStub.Type.VERSION_SERVICE) Provider<VersionStub> versionStub,
      @ServerBinary Provider<Path> serverBinary,
      @OlcServerJavaPath Provider<Path> javaPath,
      @DeviceInfraServiceFlags ImmutableList<String> deviceInfraServiceFlags,
      ListeningScheduledExecutorService scheduledThreadPool) {
    this.clientComponentName = clientComponentName;
    this.clientId = clientId;
    this.serverStartingLogger = serverStartingLogger;
    this.commandExecutor = commandExecutor;
    this.sleeper = sleeper;
    this.systemUtil = systemUtil;
    this.localFileUtil = localFileUtil;
    this.controlStub = controlStub;
    this.versionStub = versionStub;
    this.serverBinary = serverBinary;
    this.javaPath = javaPath;
    this.deviceInfraServiceFlags = deviceInfraServiceFlags;
    this.scheduledThreadPool = scheduledThreadPool;
  }

  public void startSendingHeartbeats() {
    HeartbeatRequest request = HeartbeatRequest.newBuilder().setClientId(clientId).build();
    logFailure(
        scheduledThreadPool.scheduleWithFixedDelay(
            () -> {
              try {
                requireNonNull(controlStub.get()).heartbeat(request);
              } catch (GrpcExceptionWithErrorId e) {
                logger
                    .atInfo()
                    .atMostEvery(5, MINUTES)
                    .with(IMPORTANCE, DEBUG)
                    .log("Error when sending heartbeat to OLC server");
              }
            },
            Duration.ZERO,
            HEARTBEAT_INTERVAL),
        Level.SEVERE,
        "Fatal error when sending heartbeat to OLC server");
  }

  public Optional<GetVersionResponse> tryConnectToOlcServer() throws MobileHarnessException {
    try {
      return Optional.of(requireNonNull(versionStub.get()).getVersion());
    } catch (GrpcExceptionWithErrorId e) {
      if (!e.getUnderlyingRpcException().getStatus().getCode().equals(Code.UNAVAILABLE)) {
        throw new MobileHarnessException(
            InfraErrorId.ATSC_SERVER_PREPARER_CONNECT_EXISTING_OLC_SERVER_ERROR,
            "Failed to connect to existing OLC server",
            e);
      }
      return Optional.empty();
    }
  }

  public void prepareOlcServer() throws MobileHarnessException, InterruptedException {
    synchronized (prepareServerLock) {
      boolean firstPreparation = !hasPrepared;
      hasPrepared = true;

      // Tries to get server version.
      GetVersionResponse version = tryConnectToOlcServer().orElse(null);
      if (version != null) {
        if (firstPreparation) {
          serverStartingLogger.log(
              "Connected to existing OLC server, version=[%s]", shortDebugString(version));
        }

        if (needKillExistingServer(firstPreparation)) {
          killExistingServer(/* forcibly= */ false);
        } else {
          if (firstPreparation) {
            serverStartingLogger.log("Using existing OLC server");
            checkAndPrintServerVersionWarning(version);
          }
          return;
        }
      }

      // Starts a new server if not exists.
      serverStartingLogger.log("Starting new OLC server...");
      String serverBinaryPath = requireNonNull(serverBinary.get()).toString();
      localFileUtil.checkFile(serverBinaryPath);
      ImmutableList<String> serverFlags =
          ImmutableList.<String>builder()
              .addAll(BuiltinOlcServerFlags.get())
              .addAll(deviceInfraServiceFlags)
              .build();
      ImmutableList<String> serverNativeArguments =
          ImmutableList.of(
              "-Xmx" + Flags.instance().atsConsoleOlcServerXmx.getNonNull(),
              "-XX:+HeapDumpOnOutOfMemoryError");
      logger
          .atInfo()
          .with(IMPORTANCE, DEBUG)
          .log("OLC server flags: %s, native arguments: %s", serverFlags, serverNativeArguments);

      CommandProcess serverProcess = null;
      ServerStderrLineCallback serverStderrLineCallback = new ServerStderrLineCallback();
      try {
        try {
          serverProcess =
              commandExecutor.start(
                  Command.of(
                          JavaCommandCreator.of(
                                  /* useStandardInvocationForm= */ true,
                                  requireNonNull(javaPath.get()).toString())
                              .createJavaCommand(
                                  serverBinaryPath, serverFlags, serverNativeArguments))
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
        logger
            .atInfo()
            .with(IMPORTANCE, DEBUG)
            .log("Wait until OLC server starts, command=[%s]", serverProcess.command());
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
            Flags.instance().olcServerPort.getNonNull(), serverProcess.getPid());
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
  }

  /**
   * Kills the existing OLC server.
   *
   * @param forcibly whether to kill the server forcibly if it cannot be killed normally.
   */
  public void killExistingServer(boolean forcibly)
      throws MobileHarnessException, InterruptedException {
    serverStartingLogger.log("Killing existing OLC server...");
    KillServerResponse killServerResponse;
    try {
      killServerResponse =
          requireNonNull(controlStub.get())
              .killServer(KillServerRequest.newBuilder().setClientId(clientId).build());
    } catch (GrpcExceptionWithErrorId e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_SERVER_PREPARER_KILL_EXISTING_OLC_SERVER_RPC_ERROR,
          "Failed to call KillServer of OLC server",
          e);
    }
    long serverPid = killServerResponse.getServerPid();

    if (killServerResponse.getResultCase() == ResultCase.SUCCESS) {
      // Waits until the existing server is killed.
      for (int i = 0; i < 10; i++) {
        sleeper.sleep(Duration.ofSeconds(1L));
        try {
          requireNonNull(versionStub.get()).getVersion();
        } catch (GrpcExceptionWithErrorId e) {
          serverStartingLogger.log("Existing OLC server (pid=%s) killed", serverPid);
          return;
        }
      }
    }

    if (forcibly) {
      serverStartingLogger.log("Existing OLC server (pid=%s) forcibly killed", serverPid);
      killServerProcess(serverPid);
    } else {
      if (killServerResponse.getResultCase() == ResultCase.FAILURE) {
        throw MobileHarnessExceptionFactory.create(
            InfraErrorId.ATSC_SERVER_PREPARER_CANNOT_KILL_EXISTING_OLC_SERVER_ERROR,
            String.format(
                "Existing OLC server (pid=%s) cannot be killed because: \n%s",
                serverPid, createKillServerFailureReasons(killServerResponse.getFailure())),
            /* cause= */ null,
            /* addErrorIdToMessage= */ false,
            /* clearStackTrace= */ true);
      } else {
        throw new MobileHarnessException(
            InfraErrorId.ATSC_SERVER_PREPARER_EXISTING_OLC_SERVER_STILL_RUNNING_ERROR,
            String.format(
                "Existing OLC server (pid=%s) is still running for 10s after it was killed",
                serverPid));
      }
    }
  }

  private void killServerProcess(long serverPid)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Killing OLC server process (pid=%s)", serverPid);
    // Send SIGKILL to kill the OLC server.
    systemUtil.killProcess((int) serverPid);
  }

  private void connectWithRetry() throws MobileHarnessException, InterruptedException {
    int count = 0;
    while (true) {
      try {
        requireNonNull(versionStub.get()).getVersion();
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
  private static boolean needKillExistingServer(boolean firstPreparation) {
    // Checks flag.
    if (firstPreparation && Flags.instance().atsConsoleAlwaysRestartOlcServer.getNonNull()) {
      logger.atInfo().log(
          "Need to kill existing OLC server because --ats_console_always_restart_olc_server=true");
      return true;
    }

    // Always reuse the existing server no matter what its version is.
    return false;
  }

  private static String createKillServerFailureReasons(Failure killServerFailure) {
    StringBuilder result = new StringBuilder();
    if (killServerFailure.getUnfinishedSessionsCount() > 0) {
      result.append("it has running sessions:\n");
      result.append(
          TableFormatter.displayTable(
              Stream.concat(
                      Stream.of(UNFINISHED_SESSIONS_TABLE_HEADER),
                      killServerFailure.getUnfinishedSessionsList().stream()
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
                  .collect(toImmutableList())));
    }
    if (killServerFailure.getAliveClientsCount() > 0) {
      result.append("it has alive clients:\n");
      result.append(String.join("\n", killServerFailure.getAliveClientsList()));
    }
    return result.toString();
  }

  private void checkAndPrintServerVersionWarning(GetVersionResponse serverVersion) {
    GetVersionResponse clientVersion = VersionProtoUtil.createGetVersionResponse();
    if (!clientVersion.equals(serverVersion)) {
      logger.atWarning().log(
          "Using existing OLC server in a different version, "
              + "version of OLC server: [%s], version of %s: [%s]",
          shortDebugString(serverVersion), clientComponentName, shortDebugString(clientVersion));
    }
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
