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
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.DEBUG;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.protobuf.TextFormat.shortDebugString;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.partitioningBy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ClientId;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin.AtsSessionPluginConfigOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfigs;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginError;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLabel;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLoadingConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionStatus;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.AbortSessionsRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetAllSessionsRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetAllSessionsResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.RunSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.RunSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SessionFilter;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.SessionStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionQueryUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TypeRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;

/** Stub for running ATS sessions in OmniLab long-running client. */
public class AtsSessionStub {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String SESSION_PLUGIN_LABEL = "AtsSessionPlugin";
  private static final String SESSION_PLUGIN_CLASS_NAME =
      "com.google.devtools.mobileharness.infra.ats.console."
          + "controller.sessionplugin.AtsSessionPlugin";
  private static final String SESSION_PLUGIN_MODULE_CLASS_NAME =
      "com.google.devtools.mobileharness.infra.ats.console."
          + "controller.sessionplugin.AtsSessionPluginModule";
  private static final Duration GET_SESSION_STATUS_SHORT_INTERVAL = Duration.ofMillis(400L);
  private static final Duration GET_SESSION_STATUS_MEDIUM_INTERVAL = Duration.ofSeconds(5L);
  private static final Duration GET_SESSION_STATUS_LONG_INTERVAL = Duration.ofSeconds(30L);

  private static final TextFormat.Printer PRINTER =
      TextFormat.printer()
          .usingTypeRegistry(
              TypeRegistry.newBuilder()
                  .add(
                      ImmutableList.of(
                          AtsSessionPluginConfig.getDescriptor(),
                          AtsSessionPluginOutput.getDescriptor()))
                  .build());

  private final Provider<SessionStub> sessionStubProvider;
  private final String clientId;
  private final ListeningExecutorService threadPool;
  private final Sleeper sleeper;

  @Inject
  AtsSessionStub(
      @ServerStub(ServerStub.Type.SESSION_SERVICE) Provider<SessionStub> sessionStubProvider,
      @ClientId String clientId,
      ListeningExecutorService threadPool,
      Sleeper sleeper) {
    this.sessionStubProvider = sessionStubProvider;
    this.clientId = clientId;
    this.threadPool = threadPool;
    this.sleeper = sleeper;
  }

  /** Runs a session in OmniLab long-running client. */
  public ListenableFuture<AtsSessionPluginOutput> runSession(
      String sessionName, AtsSessionPluginConfig config) {
    // Creates a session.
    CreateSessionRequest createSessionRequest =
        CreateSessionRequest.newBuilder()
            .setSessionConfig(createSessionConfig(sessionName, config))
            .build();
    logger
        .atInfo()
        .with(IMPORTANCE, DEBUG)
        .log(
            "Creating session, plugin_config=[%s], request=[%s]",
            shortDebugString(config), PRINTER.shortDebugString(createSessionRequest));
    CreateSessionResponse createSessionResponse;
    try {
      createSessionResponse =
          requireNonNull(sessionStubProvider.get()).createSession(createSessionRequest);
    } catch (GrpcExceptionWithErrorId e) {
      return immediateFailedFuture(
          new MobileHarnessException(
              InfraErrorId.ATSC_SESSION_STUB_CREATE_SESSION_ERROR,
              String.format(
                  "Failed to create session, request=[%s]",
                  PRINTER.shortDebugString(createSessionRequest)),
              e));
    }
    logger
        .atInfo()
        .with(IMPORTANCE, DEBUG)
        .log("Session created, response=[%s]", PRINTER.shortDebugString(createSessionResponse));
    SessionId sessionId = createSessionResponse.getSessionId();

    // Asynchronously gets the session result.
    return threadPool.submit(
        threadRenaming(
            new GetAtsSessionTask(sessionId), () -> "get-ats-session-task-" + sessionId.getId()));
  }

  /** Runs a short session in OmniLab long-running client and returns when the session finished. */
  public AtsSessionPluginOutput runShortSession(String sessionName, AtsSessionPluginConfig config)
      throws MobileHarnessException {
    RunSessionRequest runSessionRequest =
        RunSessionRequest.newBuilder()
            .setSessionConfig(createSessionConfig(sessionName, config))
            .build();
    logger
        .atInfo()
        .with(IMPORTANCE, DEBUG)
        .log(
            "Running session, plugin_config=[%s], request=[%s]",
            shortDebugString(config), PRINTER.shortDebugString(runSessionRequest));
    RunSessionResponse runSessionResponse;
    try {
      runSessionResponse = requireNonNull(sessionStubProvider.get()).runSession(runSessionRequest);
    } catch (GrpcExceptionWithErrorId e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_SESSION_STUB_RUN_SESSION_ERROR,
          String.format(
              "Failed to run session, request=[%s]", PRINTER.shortDebugString(runSessionRequest)),
          e);
    }
    logger
        .atInfo()
        .with(IMPORTANCE, DEBUG)
        .log("Session finished, response=[%s]", PRINTER.shortDebugString(runSessionResponse));
    return getSessionPluginOutput(runSessionResponse.getSessionDetail());
  }

  /** Returns all sessions which are not finished and not aborted. */
  public ImmutableList<String> getAllUnfinishedNotAbortedSessions(boolean fromCurrentClient)
      throws MobileHarnessException {
    SessionFilter sessionFilter =
        fromCurrentClient
            ? SessionQueryUtil.getUnfinishedAndNotAbortedSessionFromClientFilter(clientId)
            : SessionQueryUtil.UNFINISHED_NOT_ABORTED_SESSION_FILTER;
    GetAllSessionsRequest getAllSessionsRequest =
        GetAllSessionsRequest.newBuilder()
            .setSessionDetailFieldMask(SessionQueryUtil.SESSION_ID_FIELD_MASK)
            .setSessionFilter(sessionFilter)
            .build();

    GetAllSessionsResponse getAllSessionsResponse = getAllSessionsByRequest(getAllSessionsRequest);
    return getAllSessionsResponse.getSessionDetailList().stream()
        .map(SessionDetail::getSessionId)
        .map(SessionId::getId)
        .collect(toImmutableList());
  }

  /** Returns all sessions. */
  public ImmutableList<AtsSessionPluginConfigOutput> getAllSessions(
      String sessionNameRegex, String sessionStatusNameRegex) throws MobileHarnessException {
    GetAllSessionsRequest getAllSessionsRequest =
        GetAllSessionsRequest.newBuilder()
            .setSessionFilter(
                SessionFilter.newBuilder()
                    .setSessionNameRegex(sessionNameRegex)
                    .setSessionStatusNameRegex(sessionStatusNameRegex))
            .build();
    GetAllSessionsResponse getAllSessionsResponse = getAllSessionsByRequest(getAllSessionsRequest);
    return getAllSessionsResponse.getSessionDetailList().stream()
        .flatMap(
            sessionDetail -> {
              try {
                AtsSessionPluginConfig sessionPluginConfig = getSessionPluginConfig(sessionDetail);
                Optional<AtsSessionPluginOutput> sessionPluginOutput =
                    getSessionPluginOutputIfAny(sessionDetail);
                return Stream.of(
                    AtsSessionPluginConfigOutput.of(
                        sessionPluginConfig, sessionPluginOutput.orElse(null)));
              } catch (MobileHarnessException | RuntimeException e) {
                logger.atWarning().withCause(e).log(
                    "Failed to get session plugin config/output, session=[%s]",
                    PRINTER.shortDebugString(sessionDetail));
                return Stream.empty();
              }
            })
        .collect(toImmutableList());
  }

  private GetAllSessionsResponse getAllSessionsByRequest(
      GetAllSessionsRequest getAllSessionsRequest) throws MobileHarnessException {
    try {
      return requireNonNull(sessionStubProvider.get()).getAllSessions(getAllSessionsRequest);
    } catch (GrpcExceptionWithErrorId e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_SESSION_STUB_GET_ALL_SESSIONS_ERROR,
          String.format(
              "Failed to get all sessions, request=[%s]", shortDebugString(getAllSessionsRequest)),
          e);
    }
  }

  /** Aborts all unstarted sessions triggered by the current client. */
  public void abortUnstartedSessions() throws MobileHarnessException {
    SessionFilter sessionFilter =
        SessionQueryUtil.getUnfinishedSessionWithoutStartedTestFromClientFilter(clientId);
    abortSessions(AbortSessionsRequest.newBuilder().setSessionFilter(sessionFilter).build());
  }

  private void abortSessions(AbortSessionsRequest request) throws MobileHarnessException {
    try {
      sessionStubProvider.get().abortSessions(request);
    } catch (GrpcExceptionWithErrorId e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_SESSION_STUB_ABORT_SESSION_ERROR,
          String.format("Failed to abort sessions, request=[%s]", shortDebugString(request)),
          e);
    }
  }

  private class GetAtsSessionTask implements Callable<AtsSessionPluginOutput> {

    private final SessionId sessionId;

    private GetAtsSessionTask(SessionId sessionId) {
      this.sessionId = sessionId;
    }

    @Override
    public AtsSessionPluginOutput call() throws MobileHarnessException, InterruptedException {
      SessionStatus sessionStatus = SessionStatus.SESSION_STATUS_UNSPECIFIED;
      try {
        int count = 0;
        do {
          count++;
          sleeper.sleep(calculateGetSessionStatusInterval(count));
          SessionStatus newSessionStatus =
              requireNonNull(sessionStubProvider.get())
                  .getSession(
                      GetSessionRequest.newBuilder()
                          .setSessionId(sessionId)
                          .setFieldMask(SessionQueryUtil.GET_SESSION_STATUS_FIELD_MASK)
                          .build())
                  .getSessionDetail()
                  .getSessionStatus();
          if (!newSessionStatus.equals(sessionStatus)) {
            sessionStatus = newSessionStatus;
            logger.atFine().log(
                "Session status: [%s], session_id=[%s]",
                sessionStatus, shortDebugString(sessionId));
          }
        } while (!sessionStatus.equals(SessionStatus.SESSION_FINISHED));
      } catch (GrpcExceptionWithErrorId e) {
        throw new MobileHarnessException(
            InfraErrorId.ATSC_SESSION_STUB_GET_SESSION_STATUS_ERROR,
            String.format(
                "Failed to get session status, session_id=[%s]", shortDebugString(sessionId)),
            e);
      }

      // Gets session detail.
      SessionDetail sessionDetail;
      try {
        sessionDetail =
            requireNonNull(sessionStubProvider.get())
                .getSession(GetSessionRequest.newBuilder().setSessionId(sessionId).build())
                .getSessionDetail();
      } catch (GrpcExceptionWithErrorId e) {
        throw new MobileHarnessException(
            InfraErrorId.ATSC_SESSION_STUB_GET_SESSION_RESULT_ERROR,
            String.format(
                "Failed to get session result, session_id=[%s]", shortDebugString(sessionId)),
            e);
      }
      logger
          .atInfo()
          .with(IMPORTANCE, DEBUG)
          .log("Session result: [%s]", PRINTER.shortDebugString(sessionDetail));

      // Gets session plugin output.
      return getSessionPluginOutput(sessionDetail);
    }
  }

  private SessionConfig createSessionConfig(String sessionName, AtsSessionPluginConfig config) {
    return SessionConfig.newBuilder()
        .setSessionName(sessionName)
        .putSessionProperty(SessionProperties.PROPERTY_KEY_SESSION_CLIENT_ID, clientId)
        .setSessionPluginConfigs(
            SessionPluginConfigs.newBuilder()
                .addSessionPluginConfig(
                    SessionPluginConfig.newBuilder()
                        .setLoadingConfig(
                            SessionPluginLoadingConfig.newBuilder()
                                .setPluginClassName(SESSION_PLUGIN_CLASS_NAME)
                                .setPluginModuleClassName(SESSION_PLUGIN_MODULE_CLASS_NAME))
                        .setExecutionConfig(
                            SessionPluginExecutionConfig.newBuilder().setConfig(Any.pack(config)))
                        .setExplicitLabel(
                            SessionPluginLabel.newBuilder().setLabel(SESSION_PLUGIN_LABEL))))
        .build();
  }

  private static Duration calculateGetSessionStatusInterval(int count) {
    if (count <= 100) {
      return GET_SESSION_STATUS_SHORT_INTERVAL;
    } else if (count <= 300) {
      return GET_SESSION_STATUS_MEDIUM_INTERVAL;
    } else {
      return GET_SESSION_STATUS_LONG_INTERVAL;
    }
  }

  private static AtsSessionPluginConfig getSessionPluginConfig(SessionDetail sessionDetail) {
    return sessionDetail
        .getSessionConfig()
        .getSessionPluginConfigs()
        .getSessionPluginConfigList()
        .stream()
        .filter(
            sessionPluginConfig ->
                sessionPluginConfig.getExplicitLabel().getLabel().equals(SESSION_PLUGIN_LABEL))
        .map(
            sessionPluginConfig -> {
              try {
                return sessionPluginConfig
                    .getExecutionConfig()
                    .getConfig()
                    .unpack(AtsSessionPluginConfig.class);
              } catch (InvalidProtocolBufferException e) {
                throw new IllegalStateException(e);
              }
            })
        .findFirst()
        .orElseThrow();
  }

  private static AtsSessionPluginOutput getSessionPluginOutput(SessionDetail sessionDetail)
      throws MobileHarnessException {
    Optional<AtsSessionPluginOutput> result = getSessionPluginOutputIfAny(sessionDetail);
    Optional<MobileHarnessException> sessionError = getSessionError(sessionDetail);
    if (result.isPresent()) {
      sessionError.ifPresent(
          e ->
              logger.atWarning().withCause(e).log(
                  "Warning of session %s:", sessionDetail.getSessionId().getId()));
      return result.get();
    } else {
      throw sessionError.orElseGet(
          () ->
              new MobileHarnessException(
                  InfraErrorId.ATSC_SESSION_STUB_ATS_SESSION_PLUGIN_NO_OUTPUT_ERROR,
                  "ATS session plugin didn't set output"));
    }
  }

  private static Optional<AtsSessionPluginOutput> getSessionPluginOutputIfAny(
      SessionDetail sessionDetail) throws MobileHarnessException {
    SessionPluginOutput sessionPluginOutput =
        sessionDetail
            .getSessionOutput()
            .getSessionPluginOutputMap()
            .getOrDefault(SESSION_PLUGIN_LABEL, SessionPluginOutput.getDefaultInstance());
    if (sessionPluginOutput.hasOutput()) {
      try {
        return Optional.of(sessionPluginOutput.getOutput().unpack(AtsSessionPluginOutput.class));
      } catch (InvalidProtocolBufferException e) {
        throw new MobileHarnessException(
            InfraErrorId.ATSC_SESSION_STUB_UNPACK_SESSION_PLUGIN_OUTPUT_ERROR,
            "Failed to unpack AtsSessionPluginOutput",
            e);
      }
    } else {
      return Optional.empty();
    }
  }

  /**
   * Returns the highest priority exception as the session error, and other exceptions as its
   * suppressed exceptions.
   *
   * <p>Priority: exceptions from AtsSessionPlugin -> exception from session runner -> exception
   * from other session plugins.
   */
  private static Optional<MobileHarnessException> getSessionError(SessionDetail sessionDetail) {
    Optional<MobileHarnessException> sessionRunnerError =
        sessionDetail.hasSessionRunnerError()
            ? Optional.of(
                new MobileHarnessException(
                    InfraErrorId.ATSC_SESSION_STUB_SESSION_RUNNER_ERROR,
                    "Session runner error",
                    ErrorModelConverter.toDeserializedException(
                        sessionDetail.getSessionRunnerError())))
            : Optional.empty();

    // Selects exceptions from AtsSessionPlugin.
    List<SessionPluginError> sessionPluginErrors =
        sessionDetail.getSessionOutput().getSessionPluginErrorList();
    Map<Boolean, List<SessionPluginError>> sessionPluginErrorsPartitionedByLabel =
        sessionPluginErrors.stream()
            .collect(
                partitioningBy(
                    error -> error.getPluginLabel().getLabel().equals(SESSION_PLUGIN_LABEL)));
    ImmutableList<MobileHarnessException> atsSessionPluginErrors =
        sessionPluginErrorsPartitionedByLabel.get(true).stream()
            .map(
                atsSessionPluginError ->
                    new MobileHarnessException(
                        InfraErrorId.ATSC_SESSION_STUB_ATS_SESSION_PLUGIN_ERROR,
                        String.format(
                            "ATS session plugin error, method=[%s]",
                            atsSessionPluginError.getMethodName()),
                        ErrorModelConverter.toDeserializedException(
                            atsSessionPluginError.getError())))
            .collect(toImmutableList());
    ImmutableList<MobileHarnessException> otherSessionPluginErrors =
        sessionPluginErrorsPartitionedByLabel.get(false).stream()
            .map(
                otherSessionPluginError ->
                    new MobileHarnessException(
                        InfraErrorId.ATSC_SESSION_STUB_OTHER_SESSION_PLUGIN_ERROR,
                        String.format(
                            "Session plugin error, class=[%s], method=[%s]",
                            otherSessionPluginError.getPluginClassName(),
                            otherSessionPluginError.getMethodName()),
                        ErrorModelConverter.toDeserializedException(
                            otherSessionPluginError.getError())))
            .collect(toImmutableList());

    // Sorts session errors.
    ImmutableList<MobileHarnessException> sortedSessionErrors =
        Streams.concat(
                atsSessionPluginErrors.stream(),
                sessionRunnerError.stream(),
                otherSessionPluginErrors.stream())
            .collect(toImmutableList());
    if (sortedSessionErrors.isEmpty()) {
      return Optional.empty();
    }

    // Adds other errors as suppressed errors of the first one.
    MobileHarnessException result = sortedSessionErrors.get(0);
    sortedSessionErrors.stream().skip(1L).forEach(result::addSuppressed);

    return Optional.of(result);
  }
}
