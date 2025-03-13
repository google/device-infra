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
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.DEBUG;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.infra.ats.common.FlagsString;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ClientComponentName;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ClientId;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.DeviceInfraServiceFlags;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer.ServerEnvironment;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.OlcServerDirs;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.HeartbeatRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse.Failure;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.ControlServiceProto.KillServerResponse.ResultCase;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ControlStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.VersionStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.VersionProtoUtil;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
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
import com.google.devtools.mobileharness.shared.version.Version;
import io.grpc.Status.Code;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
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

  private static final String LOCK_FILE_PATH = "/tmp/olc_server_startup.lck";

  private static final String SH_COMMAND = "sh";
  private static final String NOHUP_COMMAND = "nohup";

  private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10L);
  private static final Duration CONNECT_SERVER_INTERVAL = Duration.ofSeconds(1L);

  private static final ImmutableList<String> UNFINISHED_SESSIONS_TABLE_HEADER =
      ImmutableList.of("Session ID", "Name", "Status", "Submitted Time");

  private static final String FORCIBLY_RESTART_SUGGESTION =
      "if necessary, run \"server restart -f\" command in the console or \"kill -9 %s\" in the"
          + " terminal to forcibly restart OLC server.\nIMPORTANT: This action will also forcibly"
          + " kill all running jobs submitted from ANY console on the same machine. Ensure all"
          + " critical jobs are finished before proceeding.";

  private final String clientComponentName;
  private final String clientId;
  private final CommandExecutor commandExecutor;
  private final Sleeper sleeper;
  private final SystemUtil systemUtil;
  private final LocalFileUtil localFileUtil;
  private final Provider<ControlStub> controlStub;
  private final Provider<VersionStub> versionStub;
  private final ServerEnvironmentPreparer serverEnvironmentPreparer;
  private final FlagsString deviceInfraServiceFlags;
  private final ListeningScheduledExecutorService scheduledThreadPool;
  private final ServerHeapDumpFileDetector serverHeapDumpFileDetector;

  private final Object prepareServerLock = new Object();

  @GuardedBy("prepareServerLock")
  private boolean hasPrepared;

  @Inject
  ServerPreparer(
      @ClientComponentName String clientComponentName,
      @ClientId String clientId,
      CommandExecutor commandExecutor,
      Sleeper sleeper,
      SystemUtil systemUtil,
      LocalFileUtil localFileUtil,
      @ServerStub(ServerStub.Type.CONTROL_SERVICE) Provider<ControlStub> controlStub,
      @ServerStub(ServerStub.Type.VERSION_SERVICE) Provider<VersionStub> versionStub,
      ServerEnvironmentPreparer serverEnvironmentPreparer,
      @DeviceInfraServiceFlags FlagsString deviceInfraServiceFlags,
      ListeningScheduledExecutorService scheduledThreadPool,
      ServerHeapDumpFileDetector serverHeapDumpFileDetector) {
    this.clientComponentName = clientComponentName;
    this.clientId = clientId;
    this.commandExecutor = commandExecutor;
    this.sleeper = sleeper;
    this.systemUtil = systemUtil;
    this.localFileUtil = localFileUtil;
    this.controlStub = controlStub;
    this.versionStub = versionStub;
    this.serverEnvironmentPreparer = serverEnvironmentPreparer;
    this.deviceInfraServiceFlags = deviceInfraServiceFlags;
    this.scheduledThreadPool = scheduledThreadPool;
    this.serverHeapDumpFileDetector = serverHeapDumpFileDetector;
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
                serverHeapDumpFileDetector.detectHeapDumpExistenceWithGrpcError(e);
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

  /**
   * Connects to an existing OLC server or creates a new one.
   *
   * @implNote the method will use a file lock to ensure that at most one invocation can happen on
   *     the machine at any given time (if no error occurs when acquiring the lock)
   */
  public void prepareOlcServer() throws MobileHarnessException, InterruptedException {
    synchronized (prepareServerLock) {
      boolean firstPreparation = !hasPrepared;
      hasPrepared = true;

      // Acquires file lock.
      Optional<NonThrowingAutoCloseable> fileUnlocker = lockFile();
      try {
        // Tries to get server version.
        GetVersionResponse existingServerVersion = tryConnectToOlcServer().orElse(null);
        if (existingServerVersion != null) {
          if (firstPreparation) {
            logger.atInfo().log(
                "Connected to existing OLC server, version=[%s]",
                shortDebugString(existingServerVersion));
          }

          if (needKillExistingServer(firstPreparation, existingServerVersion)) {
            killExistingServer(/* forcibly= */ false);
          } else {
            if (firstPreparation) {
              logger.atInfo().log("Using existing OLC server");
              checkAndPrintServerVersionWarning(existingServerVersion);
              Optional<String> processWorkingDir =
                  systemUtil.getProcessWorkingDirectory(existingServerVersion.getProcessId());
              // Records the server information.
              processWorkingDir.ifPresent(
                  dir ->
                      serverHeapDumpFileDetector.setOlcServerInfo(
                          existingServerVersion.getProcessId(), dir));
            }
            return;
          }
        }

        // Starts a new server.
        logger.atInfo().log("Starting new OLC server...");

        // Prepares server environment.
        ServerEnvironment serverEnvironment = serverEnvironmentPreparer.prepareServerEnvironment();
        logger
            .atInfo()
            .with(IMPORTANCE, DEBUG)
            .log("OLC server environment: %s", serverEnvironment);

        // Creates arguments.
        FlagsString serverFlags = deviceInfraServiceFlags.addToHead(BuiltinOlcServerFlags.get());
        ImmutableList<String> serverNativeArguments =
            ImmutableList.of(
                "-Xmx" + Flags.instance().atsConsoleOlcServerXmx.getNonNull(),
                "-XX:+HeapDumpOnOutOfMemoryError",
                "-XX:+ExitOnOutOfMemoryError");
        logger
            .atInfo()
            .with(IMPORTANCE, DEBUG)
            .log(
                "OLC server flags: %s, native arguments: %s",
                serverFlags.flags(), serverNativeArguments);

        // Creates the command to start the server.
        String serverOutputPath = Flags.instance().atsConsoleOlcServerOutputPath.getNonNull();
        ImmutableList.Builder<String> startOlcServerCommandBuilder = ImmutableList.builder();
        startOlcServerCommandBuilder.add(NOHUP_COMMAND);
        startOlcServerCommandBuilder.addAll(
            JavaCommandCreator.of(
                    /* useStandardInvocationForm= */ true,
                    wrapPath(serverEnvironment.javaBinary().toString()))
                .createJavaCommand(
                    wrapPath(serverEnvironment.serverBinary().toString()),
                    // Treats all flags as one argument to keep escape chars.
                    ImmutableList.of(serverFlags.flagsString()),
                    serverNativeArguments));
        startOlcServerCommandBuilder.add(">" + serverOutputPath).add("2>&1").add("&");

        // Starts the server process.
        CommandProcess serverProcess;
        StringBuilderLineCallback serverOutputLineCallback = new StringBuilderLineCallback();
        Command serverCommand =
            Command.of(
                    ImmutableList.of(
                        SH_COMMAND,
                        "-c",
                        Joiner.on(" ").join(startOlcServerCommandBuilder.build())))
                .workDir(serverEnvironment.serverWorkingDir())
                .timeout(ChronoUnit.YEARS.getDuration())
                .redirectStderr(false)
                .onStdout(serverOutputLineCallback)
                .onStderr(serverOutputLineCallback)
                .needStdoutInResult(false)
                .needStderrInResult(false);
        try {
          serverProcess = commandExecutor.start(serverCommand);
        } catch (CommandStartException e) {
          throw new MobileHarnessException(
              InfraErrorId.ATSC_SERVER_PREPARER_START_OLC_SERVER_ERROR,
              "Failed to start OLC server",
              e);
        }
        try {

          // Waits until the server starts.
          logger
              .atInfo()
              .with(IMPORTANCE, DEBUG)
              .log("Wait until OLC server starts, command=[%s]", serverProcess.command());
          GetVersionResponse serverVersion = connectWithRetry();

          // The server starts successfully.
          logger.atInfo().log(
              "OLC server started, port=%s, pid=%s",
              Flags.instance().olcServerPort.getNonNull(), serverVersion.getProcessId());
          // Records the server information.
          serverHeapDumpFileDetector.setOlcServerInfo(
              serverVersion.getProcessId(), serverEnvironment.serverWorkingDir().toString());
        } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
          // Kills the wrapper process.
          if (serverProcess.isAlive()) {
            logger.atInfo().log("Killing OLC server");
            serverProcess.kill();
          }

          try {
            printServerStartingFailureInfo(
                serverCommand, serverOutputPath, serverOutputLineCallback);
          } catch (MobileHarnessException | RuntimeException | Error e2) {
            logger.atWarning().withCause(e2).log(
                "Failed to print server starting log from the log file.");
          } catch (InterruptedException e2) {
            Thread.currentThread().interrupt();
          }
          throw e;
        } finally {
          serverProcess.stopReadingOutput();
        }

      } finally {
        // Releases file lock if any.
        fileUnlocker.ifPresent(NonThrowingAutoCloseable::close);
      }
    }
  }

  private void printServerStartingFailureInfo(
      Command serverCommand,
      String serverOutputPath,
      StringBuilderLineCallback serverOutputLineCallback)
      throws MobileHarnessException, InterruptedException {
    // Prints server command.
    logger.atInfo().log("olc_server_command=%s", serverCommand.getCommand());

    // Prints server starting log from stderr / redirected file / log files.
    String serverStartingLog;
    String serverOutput = serverOutputLineCallback.toString();
    if (!serverOutput.isEmpty()) {
      serverStartingLog = serverOutput;
    } else if (!serverOutputPath.equals("/dev/null")
        && localFileUtil.isFileExist(serverOutputPath)) {
      serverStartingLog = localFileUtil.readFile(serverOutputPath);
    } else {
      Path logFile = Path.of(OlcServerDirs.getLogDir()).resolve("log0.txt");
      if (!localFileUtil.isFileOrDirExist(logFile)) {
        return;
      }
      Instant lastModifiedTime = localFileUtil.getFileLastModifiedTime(logFile);
      Instant now = Instant.now();

      Duration duration = Duration.between(lastModifiedTime, now);
      if (duration.compareTo(
              Flags.instance().atsConsoleOlcServerStartingTimeout.getNonNull().plusSeconds(5L))
          > 0) {
        // Skip printing if the log file wasn't modified recently.
        return;
      }
      serverStartingLog = localFileUtil.readFile(logFile);
    }
    logger.atInfo().log("OLC server log:\n%s", serverStartingLog);

    // Prints command versions.
    logger.atInfo().log(
        "sh version:\n%s", commandExecutor.run(Command.of(SH_COMMAND, "--version")));
    logger.atInfo().log(
        "nohup version:\n%s", commandExecutor.run(Command.of(NOHUP_COMMAND, "--version")));
  }

  /**
   * Kills the existing OLC server.
   *
   * @param forcibly whether to kill the server forcibly if it cannot be killed normally.
   */
  public void killExistingServer(boolean forcibly)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Killing existing OLC server...%s", forcibly ? " (forcibly)" : "");
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
          logger.atInfo().log("Existing OLC server (pid=%s) killed", serverPid);
          return;
        }
      }
    }

    if (forcibly) {
      killServerProcess(serverPid);
      logger.atInfo().log("Existing OLC server (pid=%s) forcibly killed", serverPid);
    } else {
      if (killServerResponse.getResultCase() == ResultCase.FAILURE) {
        throw MobileHarnessExceptionFactory.createUserFacingException(
            InfraErrorId.ATSC_SERVER_PREPARER_CANNOT_KILL_EXISTING_OLC_SERVER_ERROR,
            String.format(
                "Existing OLC server (pid=%s) cannot be killed, reason=[%s], %s",
                serverPid,
                createKillServerFailureReasons(killServerResponse.getFailure()),
                String.format(FORCIBLY_RESTART_SUGGESTION, serverPid)),
            /* cause= */ null);
      } else {
        throw MobileHarnessExceptionFactory.createUserFacingException(
            InfraErrorId.ATSC_SERVER_PREPARER_EXISTING_OLC_SERVER_STILL_RUNNING_ERROR,
            String.format(
                "Existing OLC server (pid=%s) is still running for 10s after it was killed, %s",
                serverPid, String.format(FORCIBLY_RESTART_SUGGESTION, serverPid)),
            /* cause= */ null);
      }
    }
  }

  private void killServerProcess(long serverPid)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Killing OLC server process (pid=%s)", serverPid);
    // Send SIGKILL to kill the OLC server.
    systemUtil.killProcess((int) serverPid);
  }

  private GetVersionResponse connectWithRetry()
      throws MobileHarnessException, InterruptedException {
    int count = 0;
    long maxAttempts =
        Flags.instance()
                .atsConsoleOlcServerStartingTimeout
                .getNonNull()
                .dividedBy(CONNECT_SERVER_INTERVAL)
            + 1L;
    while (true) {
      try {
        return requireNonNull(versionStub.get()).getVersion();
      } catch (GrpcExceptionWithErrorId e) {
        count++;
        if (count > maxAttempts) {
          throw new MobileHarnessException(
              InfraErrorId.ATSC_SERVER_PREPARER_CONNECT_NEW_OLC_SERVER_ERROR,
              "Failed to connect new OLC server",
              e);
        } else {
          sleeper.sleep(CONNECT_SERVER_INTERVAL);
        }
      }
    }
  }

  /**
   * Whether the preparer needs to "kill an existing OLC server (if any) and restart a new one" when
   * preparing server, or just reuse the existing server (if any).
   */
  private static boolean needKillExistingServer(
      boolean firstPreparation, GetVersionResponse getVersionResponse)
      throws MobileHarnessException {
    // Checks flag.
    if (firstPreparation) {
      if (Flags.instance().atsConsoleAlwaysRestartOlcServer.getNonNull()) {
        logger.atInfo().log(
            "Need to kill existing OLC server because"
                + " --ats_console_always_restart_olc_server=true");
        return true;
      }
      String olcLabVersionString = getVersionResponse.getLabVersion();
      Version olcLabVersion =
          olcLabVersionString.isEmpty() ? new Version(0, 0, 0) : new Version(olcLabVersionString);
      String minOlcLabVersionString =
          Flags.instance().atsConsoleOlcServerMinLabVersion.getNonNull();
      Version minOlcLabVersion = new Version(minOlcLabVersionString);
      if (olcLabVersion.compareTo(minOlcLabVersion) < 0) {
        logger.atInfo().log(
            "Need to kill existing OLC server because the current OLC lab version [%s] is older"
                + " than the minimum version [%s]",
            olcLabVersionString, minOlcLabVersionString);
        return true;
      }
    }

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
    serverVersion = serverVersion.toBuilder().clearProcessId().build();
    GetVersionResponse clientVersion =
        VersionProtoUtil.createGetVersionResponse().toBuilder().clearProcessId().build();
    if (!clientVersion.equals(serverVersion)) {
      logger.atWarning().log(
          "Using existing OLC server in a different version, version of OLC server: [%s], version"
              + " of %s: [%s]\n"
              + "If necessary, run \"server restart\" command to restart a new OLC server"
              + " instance using the same version of the current console",
          shortDebugString(serverVersion), clientComponentName, shortDebugString(clientVersion));
    }
  }

  /** Wraps a path in a command just in case that it contains special characters. */
  private static String wrapPath(String path) {
    return "'" + path + "'";
  }

  /**
   * Locks the given file exclusively.
   *
   * @return a closeable to unlock the file, or empty if an error occurs when acquiring the lock
   *     (not including waiting for the lock)
   */
  private Optional<NonThrowingAutoCloseable> lockFile() {
    RandomAccessFile file = null;
    try {
      // Opens file.
      file = new RandomAccessFile(LOCK_FILE_PATH, "rw");
      RandomAccessFile finalFile = file;
      localFileUtil.grantFileOrDirFullAccess(LOCK_FILE_PATH);

      // Locks file.
      logger.atInfo().with(IMPORTANCE, DEBUG).log("Locking file [%s]", LOCK_FILE_PATH);
      FileChannel channel = file.getChannel();
      FileLock lock = channel.tryLock();
      if (lock == null) {
        logger.atInfo().log("Acquiring file lock [%s]...", LOCK_FILE_PATH);
        channel.lock();
      }
      logger.atInfo().with(IMPORTANCE, DEBUG).log("Locked file [%s]", LOCK_FILE_PATH);

      return Optional.of(
          () -> {
            closeFile(finalFile);
            logger.atInfo().with(IMPORTANCE, DEBUG).log("Released file lock [%s]", LOCK_FILE_PATH);
          });
    } catch (MobileHarnessException | IOException e) {
      logger.atWarning().withCause(e).log("Failed to lock file [%s]", LOCK_FILE_PATH);
      if (file != null) {
        closeFile(file);
      }
      return Optional.empty();
    }
  }

  private static void closeFile(RandomAccessFile file) {
    try {
      file.close();
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to close file [%s]", LOCK_FILE_PATH);
    }
  }

  private static class StringBuilderLineCallback implements LineCallback {

    @GuardedBy("itself")
    private final StringBuilder stringBuilder = new StringBuilder();

    @Override
    public Response onLine(String line) {
      synchronized (stringBuilder) {
        stringBuilder.append(line).append('\n');
      }
      return Response.empty();
    }

    @Override
    public String toString() {
      synchronized (stringBuilder) {
        return stringBuilder.toString();
      }
    }
  }
}
