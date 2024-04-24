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

package com.google.devtools.mobileharness.infra.client.longrunningservice.controller;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.protobuf.TextFormat.shortDebugString;
import static java.lang.Math.min;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.SessionEnvironmentPreparer.SessionEnvironment;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionNotification;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionStatus;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SessionFilter;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SubscribeSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SubscribeSessionResponse;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.message.FieldMaskUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.FieldMask;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.Printer;
import com.google.protobuf.util.FieldMaskUtil;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Session manager for managing sessions. */
@Singleton
public class SessionManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Queue capacity for submitted and non-started sessions. */
  private static final int SESSION_QUEUE_CAPACITY = 5000;

  /** Capacity for concurrently running sessions. */
  private static final int RUNNING_SESSION_CAPACITY = 100;

  /** Capacity for archived sessions. */
  private static final int ARCHIVED_SESSION_CAPACITY = 500;

  private final SessionDetailCreator sessionDetailCreator;
  private final SessionRunner.Factory sessionRunnerFactory;
  private final LocalFileUtil localFileUtil;
  private final ListeningExecutorService threadPool;

  private final Object sessionsLock = new Object();

  /** Submitted and non-started sessions. */
  @GuardedBy("sessionsLock")
  private final LinkedHashMap<String, PendingSession> pendingSessions = new LinkedHashMap<>();

  /** Running sessions. */
  @GuardedBy("sessionsLock")
  private final Map<String, RunningSession> runningSessions = new HashMap<>();

  /** Archived sessions. */
  @GuardedBy("sessionsLock")
  private final LinkedHashMap<String, SessionDetail> archivedSessions =
      new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Entry<String, SessionDetail> eldest) {
          return size() > ARCHIVED_SESSION_CAPACITY;
        }
      };

  @Inject
  SessionManager(
      SessionDetailCreator sessionDetailCreator,
      SessionRunner.Factory sessionRunnerFactory,
      LocalFileUtil localFileUtil,
      ListeningExecutorService threadPool) {
    this.sessionDetailCreator = sessionDetailCreator;
    this.sessionRunnerFactory = sessionRunnerFactory;
    this.localFileUtil = localFileUtil;
    this.threadPool = threadPool;
  }

  /**
   * Adds a new session to the session queue.
   *
   * @throws MobileHarnessException if the queue is full
   */
  @CanIgnoreReturnValue
  public SessionAddingResult addSession(SessionConfig sessionConfig) throws MobileHarnessException {
    SessionDetail pendingSessionDetail = sessionDetailCreator.create(sessionConfig);
    logger.atInfo().log("Create session: %s", shortDebugString(pendingSessionDetail));
    SessionSubscribers subscribers = new SessionSubscribers();
    subscribers.setSessionDetailSupplier(fieldMask -> pendingSessionDetail);
    PendingSession pendingSession =
        PendingSession.of(
            pendingSessionDetail,
            SettableFuture.create(),
            subscribers,
            /* cachedSessionNotifications= */ new ArrayList<>());
    synchronized (sessionsLock) {
      MobileHarnessExceptions.check(
          pendingSessions.size() <= SESSION_QUEUE_CAPACITY,
          InfraErrorId.OLCS_CREATE_SESSION_ERROR_SESSION_QUEUE_FULL,
          () ->
              String.format(
                  "Session queue is full(%s), failed to add session [%s]",
                  SESSION_QUEUE_CAPACITY, shortDebugString(pendingSessionDetail)));
      pendingSessions.put(pendingSessionDetail.getSessionId().getId(), pendingSession);

      // Tries to start new sessions.
      startSessions();
    }
    return SessionAddingResult.of(pendingSession);
  }

  /**
   * Gets {@link SessionDetail} of a session by its ID.
   *
   * @param fieldMask a field mask relative to SessionDetail. {@code null} means all fields are
   *     required. It is acceptable that the implementation outputs more fields than the field mask
   *     requires, e.g., for an archived session.
   * @throws MobileHarnessException if the session is not found (un-submitted or has been removed
   *     from archived sessions)
   */
  public SessionDetail getSession(String sessionId, @Nullable FieldMask fieldMask)
      throws MobileHarnessException {
    SessionDetail sessionDetail;
    Printer protoPrinter = TextFormat.printer();
    // Checks the session from 3 places.
    synchronized (sessionsLock) {
      sessionDetail = archivedSessions.get(sessionId);
      if (sessionDetail == null) {
        RunningSession runningSession = runningSessions.get(sessionId);
        if (runningSession != null) {
          SessionRunner sessionRunner = runningSession.sessionRunner();
          sessionDetail = sessionRunner.getSession(fieldMask);
          protoPrinter = sessionRunner.getProtoPrinter();
        }
      }
      if (sessionDetail == null) {
        PendingSession pendingSession = pendingSessions.get(sessionId);
        if (pendingSession != null) {
          sessionDetail = pendingSession.sessionDetail();
        }
      }
    }
    MobileHarnessExceptions.check(
        sessionDetail != null,
        InfraErrorId.OLCS_GET_SESSION_SESSION_NOT_FOUND,
        () -> String.format("Session not found, id=[%s]", sessionId));
    logger.atFine().log("Get session: %s", protoPrinter.shortDebugString(sessionDetail));
    return sessionDetail;
  }

  /**
   * Gets {@link SessionDetail} of all sessions which have been submitted and have not been removed
   * from archived sessions.
   *
   * @param fieldMask a field mask relative to SessionDetail. {@code null} means all fields are
   *     required. It is acceptable that the implementation outputs more fields than the field mask
   *     requires, e.g., for an archived session.
   */
  public ImmutableList<SessionDetail> getAllSessions(
      @Nullable FieldMask fieldMask, @Nullable SessionFilter sessionFilter) {
    Predicate<SessionStatus> sessionStatusFilter = getSessionStatusFilter(sessionFilter);
    Optional<Predicate<SessionConfig>> sessionConfigFilter = getSessionConfigFilter(sessionFilter);
    Optional<Predicate<SessionOutput>> sessionOutputFilter = getSessionOutputFilter(sessionFilter);
    ImmutableList<SessionDetail> sessions;
    synchronized (sessionsLock) {
      sessions =
          Streams.concat(
                  sessionStatusFilter.test(SessionStatus.SESSION_SUBMITTED)
                      ? pendingSessions.values().stream()
                          .map(PendingSession::sessionDetail)
                          .filter(
                              sessionDetail ->
                                  testSession(
                                      sessionConfigFilter.orElse(null),
                                      sessionOutputFilter.orElse(null),
                                      sessionDetail::getSessionConfig,
                                      sessionDetail::getSessionOutput))
                      : Stream.empty(),
                  sessionStatusFilter.test(SessionStatus.SESSION_RUNNING)
                      ? runningSessions.values().stream()
                          .map(RunningSession::sessionRunner)
                          .filter(
                              sessionRunner ->
                                  testSession(
                                      sessionConfigFilter.orElse(null),
                                      sessionOutputFilter.orElse(null),
                                      sessionRunner::getSessionConfig,
                                      () ->
                                          sessionRunner
                                              .getSession(/* fieldMask= */ null)
                                              .getSessionOutput()))
                          .map(sessionRunner -> sessionRunner.getSession(fieldMask))
                      : Stream.empty(),
                  sessionStatusFilter.test(SessionStatus.SESSION_FINISHED)
                      ? archivedSessions.values().stream()
                          .filter(
                              sessionDetail ->
                                  testSession(
                                      sessionConfigFilter.orElse(null),
                                      sessionOutputFilter.orElse(null),
                                      sessionDetail::getSessionConfig,
                                      sessionDetail::getSessionOutput))
                      : Stream.empty())
              .collect(toImmutableList());
    }
    logger.atInfo().log(
        "Get sessions, filter=[%s], sessions=%s",
        sessionFilter == null ? null : shortDebugString(sessionFilter),
        sessions.stream()
            .map(SessionDetail::getSessionId)
            .map(SessionId::getId)
            .collect(toImmutableList()));
    return sessions;
  }

  private static boolean testSession(
      @Nullable Predicate<SessionConfig> sessionConfigFilter,
      @Nullable Predicate<SessionOutput> sessionOutputFilter,
      Supplier<SessionConfig> sessionConfigSupplier,
      Supplier<SessionOutput> sessionOutputSupplier) {
    return (sessionConfigFilter == null || sessionConfigFilter.test(sessionConfigSupplier.get()))
        && (sessionOutputFilter == null || sessionOutputFilter.test(sessionOutputSupplier.get()));
  }

  public StreamObserver<SubscribeSessionRequest> subscribeSession(
      StreamObserver<SubscribeSessionResponse> responseObserver) {
    return new SessionSubscriber(responseObserver).getRequestObserver();
  }

  public boolean notifySession(String sessionId, SessionNotification sessionNotification) {
    synchronized (sessionsLock) {
      RunningSession runningSession = runningSessions.get(sessionId);
      if (runningSession != null) {
        SessionRunner sessionRunner = runningSession.sessionRunner();
        logger.atInfo().log(
            "Notify running session [%s]: [%s]",
            sessionId, sessionRunner.getProtoPrinter().shortDebugString(sessionNotification));
        return sessionRunner.notifySession(sessionNotification);
      }
      PendingSession pendingSession = pendingSessions.get(sessionId);
      if (pendingSession != null) {
        logger.atInfo().log(
            "Notify pending session [%s]: [%s]", sessionId, shortDebugString(sessionNotification));
        pendingSession.cachedSessionNotifications().add(sessionNotification);
        return true;
      }
      logger.atInfo().log(
          "Discard notification to session [%s]: [%s]",
          sessionId, shortDebugString(sessionNotification));
      return false;
    }
  }

  public void abortSessions(List<String> sessionIds) {
    List<PendingSession> abortedPendingSessions = new ArrayList<>();
    for (String sessionId : sessionIds) {
      synchronized (sessionsLock) {
        if (archivedSessions.containsKey(sessionId)) {
          // If the session has ended, does nothing.
          logger.atInfo().log("Abort an archived session [%s]", sessionId);
        } else if (pendingSessions.containsKey(sessionId)) {
          // If the session has not started, archives its directly.
          logger.atInfo().log("Abort a pending session [%s], archive it", sessionId);
          PendingSession pendingSession = pendingSessions.remove(sessionId);

          // Marks the session as FINISHED.
          SessionDetail finalSessionDetail =
              createFinalSessionDetail(
                  pendingSession.sessionDetail(),
                  /* sessionRunnerError= */ null,
                  TextFormat.printer());

          // Archives the session.
          archiveSession(finalSessionDetail);

          // Triggers subscribers after session status becomes SESSION_FINISHED.
          pendingSession.sessionSubscribers().close(finalSessionDetail);

          abortedPendingSessions.add(
              PendingSession.of(
                  finalSessionDetail,
                  pendingSession.finalResultFuture(),
                  pendingSession.sessionSubscribers(),
                  pendingSession.cachedSessionNotifications()));
        } else if (runningSessions.containsKey(sessionId)) {
          // If the session is running, stops job polling and kills all running jobs.
          logger.atInfo().log("Abort a running session [%s]", sessionId);
          RunningSession runningSession = runningSessions.get(sessionId);
          runningSession.sessionRunner().abortSession();
        } else {
          logger.atInfo().log("Session to abort is not found, id=[%s]", sessionId);
        }
      }
    }

    // Sets the future out of the lock.
    for (PendingSession abortedPendingSession : abortedPendingSessions) {
      abortedPendingSession.finalResultFuture().set(abortedPendingSession.sessionDetail());
    }
  }

  /** Tries to poll as many as sessions from the session queue and starts them. */
  @GuardedBy("sessionsLock")
  private void startSessions() {
    // Poll sessions from the session queue.
    ImmutableList<PendingSession> newSessions = pollSessions();

    // Creates session runners.
    ImmutableList<RunningSession> newRunningSessions =
        newSessions.stream()
            .map(
                pendingSession ->
                    RunningSession.of(
                        sessionRunnerFactory.create(
                            pendingSession.sessionDetail().toBuilder()
                                .setSessionStatus(SessionStatus.SESSION_RUNNING)
                                .build(),
                            pendingSession.sessionSubscribers().getSessionDetailListener(),
                            ImmutableList.copyOf(pendingSession.cachedSessionNotifications())),
                        pendingSession.finalResultFuture(),
                        pendingSession.sessionSubscribers()))
            .collect(toImmutableList());

    // Starts session runners.
    for (RunningSession runningSession : newRunningSessions) {
      SessionRunner sessionRunner = runningSession.sessionRunner();

      // Triggers subscribers after session status becomes SESSION_RUNNING.
      SessionSubscribers subscribers = runningSession.sessionSubscribers();
      subscribers.receiveSessionDetail(/* finalSessionDetail= */ false);
      subscribers.setSessionDetailSupplier(sessionRunner::getSession);

      String sessionId = sessionRunner.getSessionId();
      logger.atInfo().log("Starting session [%s]", sessionId);

      runningSessions.put(sessionId, runningSession);
      addCallback(
          threadPool.submit(threadRenaming(sessionRunner, () -> "session-runner-" + sessionId)),
          threadRenaming(
              new SessionRunnerCallback(runningSession),
              () -> "session-runner-post-run" + sessionId),
          directExecutor());
    }
  }

  /** Polls as many as sessions from the session queue. */
  @GuardedBy("sessionsLock")
  private ImmutableList<PendingSession> pollSessions() {
    int count = pendingSessions.size();
    if (count > 0) {
      count = min(count, RUNNING_SESSION_CAPACITY - runningSessions.size());
    }
    if (count <= 0) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<PendingSession> result = ImmutableList.builderWithExpectedSize(count);
    Iterator<PendingSession> iterator = pendingSessions.values().iterator();
    for (int i = 0; i < count; i++) {
      result.add(iterator.next());
      iterator.remove();
    }
    return result.build();
  }

  /** Callback for {@link SessionRunner#call()}. */
  private class SessionRunnerCallback implements FutureCallback<Void> {

    private final RunningSession runningSession;

    private SessionRunnerCallback(RunningSession runningSession) {
      this.runningSession = runningSession;
    }

    @Override
    public void onSuccess(@Nullable Void result) {
      afterSession(/* sessionRunnerError= */ null);
    }

    @Override
    public void onFailure(Throwable sessionRunnerError) {
      afterSession(sessionRunnerError);
    }

    private void afterSession(@Nullable Throwable sessionRunnerError) {
      SessionRunner sessionRunner = runningSession.sessionRunner();
      SessionDetail sessionDetail = sessionRunner.getSession(/* fieldMask= */ null);
      SessionDetail finalSessionDetail =
          createFinalSessionDetail(
              sessionDetail, sessionRunnerError, sessionRunner.getProtoPrinter());

      Optional<SessionEnvironment> sessionEnvironment = sessionRunner.getSessionEnvironment();
      if (sessionEnvironment.isPresent()) {
        // Copies session logs.
        try {
          copySessionLog(sessionEnvironment.get(), finalSessionDetail);
        } catch (RuntimeException | Error e) {
          logger.atWarning().withCause(e).log("Failed to copy session logs");
        }

        // Cleans up session environment.
        sessionEnvironment.get().close();
      }

      // Archives the session.
      synchronized (sessionsLock) {
        runningSessions.remove(finalSessionDetail.getSessionId().getId());
        archiveSession(finalSessionDetail);

        // Tries to start new sessions if any.
        startSessions();

        // Triggers subscribers after session status becomes SESSION_FINISHED.
        runningSession.sessionSubscribers().close(finalSessionDetail);
      }

      // Completes the final result future.
      runningSession.finalResultFuture().set(finalSessionDetail);
    }
  }

  private static SessionDetail createFinalSessionDetail(
      SessionDetail sessionDetail, @Nullable Throwable sessionRunnerError, Printer protoPrinter) {
    SessionDetail.Builder sessionDetailBuilder =
        sessionDetail.toBuilder().setSessionStatus(SessionStatus.SESSION_FINISHED);

    logger.atInfo().withCause(sessionRunnerError).log(
        "Session finished, session_id=%s, final_session_detail=[%s]",
        sessionDetailBuilder.getSessionId().getId(),
        protoPrinter.shortDebugString(sessionDetailBuilder));

    // Adds the error thrown from the session runner to SessionDetail, if any.
    if (sessionRunnerError != null) {
      sessionDetailBuilder.setSessionRunnerError(
          ErrorModelConverter.toExceptionDetail(sessionRunnerError));
    }
    return sessionDetailBuilder.build();
  }

  @GuardedBy("sessionsLock")
  private void archiveSession(SessionDetail finalSessionDetail) {
    if (!finalSessionDetail.getSessionConfig().getRemoveAfterFinish()) {
      archivedSessions.put(finalSessionDetail.getSessionId().getId(), finalSessionDetail);
    }
  }

  private void copySessionLog(SessionEnvironment sessionEnvironment, SessionDetail sessionDetail) {
    String sessionLogFileDestinationPath =
        sessionDetail
            .getSessionOutput()
            .getSessionPropertyOrDefault(
                SessionProperties.PROPERTY_KEY_SERVER_SESSION_LOG_PATH, "");
    if (sessionLogFileDestinationPath.isEmpty()) {
      return;
    }
    String sessionLogFileSourcePath = sessionEnvironment.sessionLogFile().toString();
    logger.atInfo().log(
        "Copying server session log file from %s to %s",
        sessionLogFileSourcePath, sessionLogFileDestinationPath);
    try {
      localFileUtil.copyFileOrDir(sessionLogFileSourcePath, sessionLogFileDestinationPath);
    } catch (MobileHarnessException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        logger.atWarning().withCause(e).log(
            "Failed to copy server session log file from %s to %s",
            sessionLogFileSourcePath, sessionLogFileDestinationPath);
        Thread.currentThread().interrupt();
      }
    }
  }

  private static Predicate<SessionStatus> getSessionStatusFilter(
      @Nullable SessionFilter sessionFilter) {
    if (sessionFilter != null) {
      String sessionStatusNameRegexString = sessionFilter.getSessionStatusNameRegex();
      if (!sessionStatusNameRegexString.isEmpty()) {
        try {
          Pattern sessionStatusNameRegex = Pattern.compile(sessionStatusNameRegexString);
          return sessionStatus -> sessionStatusNameRegex.matcher(sessionStatus.name()).matches();
        } catch (PatternSyntaxException e) {
          logger.atWarning().withCause(e).log(
              "Invalid session status name regex [%s]", sessionStatusNameRegexString);
        }
      }
    }
    return sessionStatus -> true;
  }

  private static Optional<Predicate<SessionConfig>> getSessionConfigFilter(
      @Nullable SessionFilter sessionFilter) {
    if (sessionFilter != null) {
      // Session name filter.
      Predicate<SessionConfig> sessionNameFilter;
      String sessionNameRegexString = sessionFilter.getSessionNameRegex();
      if (sessionNameRegexString.isEmpty()) {
        sessionNameFilter = null;
      } else {
        try {
          Pattern sessionNameRegex = Pattern.compile(sessionNameRegexString);
          sessionNameFilter =
              sessionConfig -> sessionNameRegex.matcher(sessionConfig.getSessionName()).matches();
        } catch (PatternSyntaxException e) {
          logger.atWarning().withCause(e).log(
              "Invalid session name regex [%s]", sessionNameRegexString);
          sessionNameFilter = null;
        }
      }

      // Session config property filter.
      Predicate<SessionConfig> sessionConfigPropertyFilter;
      Map<String, String> includedSessionConfigProperties =
          sessionFilter.getIncludedSessionConfigPropertyMap();
      if (includedSessionConfigProperties.isEmpty()) {
        sessionConfigPropertyFilter = null;
      } else {
        sessionConfigPropertyFilter =
            sessionConfig -> {
              Map<String, String> sessionConfigProperties = sessionConfig.getSessionPropertyMap();
              return includedSessionConfigProperties.entrySet().stream()
                  .allMatch(
                      includedSessionConfigProperty ->
                          Objects.equals(
                              sessionConfigProperties.get(includedSessionConfigProperty.getKey()),
                              includedSessionConfigProperty.getValue()));
            };
      }

      if (sessionNameFilter != null) {
        if (sessionConfigPropertyFilter != null) {
          return Optional.of(sessionNameFilter.and(sessionConfigPropertyFilter));
        } else {
          return Optional.of(sessionNameFilter);
        }
      } else if (sessionConfigPropertyFilter != null) {
        return Optional.of(sessionConfigPropertyFilter);
      }
    }
    return Optional.empty();
  }

  private static Optional<Predicate<SessionOutput>> getSessionOutputFilter(
      @Nullable SessionFilter sessionFilter) {
    if (sessionFilter != null) {
      ImmutableSet<String> excludedSessionPropertyKeys =
          ImmutableSet.copyOf(sessionFilter.getExcludedSessionPropertyKeyList());
      if (!excludedSessionPropertyKeys.isEmpty()) {
        return Optional.of(
            sessionOutput ->
                sessionOutput.getSessionPropertyMap().keySet().stream()
                    .noneMatch(excludedSessionPropertyKeys::contains));
      }
    }
    return Optional.empty();
  }

  /** {@link SessionDetail} and the future of the final result of the session. */
  @AutoValue
  public abstract static class SessionAddingResult {
    /** The initial {@link SessionDetail}. */
    public abstract SessionDetail sessionDetail();

    /**
     * The final {@link SessionDetail} when the session runner finishes.
     *
     * <p>Note that if the session runner throws an exception, a {@link SessionDetail} will be
     * returned and the exception will be added to {@link SessionDetail#getSessionRunnerError()}.
     */
    public abstract ListenableFuture<SessionDetail> finalResultFuture();

    private static SessionAddingResult of(PendingSession pendingSession) {
      return new AutoValue_SessionManager_SessionAddingResult(
          pendingSession.sessionDetail(), pendingSession.finalResultFuture());
    }
  }

  @AutoValue
  abstract static class PendingSession {
    abstract SessionDetail sessionDetail();

    abstract SettableFuture<SessionDetail> finalResultFuture();

    abstract SessionSubscribers sessionSubscribers();

    /** Must be guarded by {@link #sessionsLock}. */
    @SuppressWarnings("AutoValueImmutableFields")
    abstract List<SessionNotification> cachedSessionNotifications();

    private static PendingSession of(
        SessionDetail sessionDetail,
        SettableFuture<SessionDetail> finalResultFuture,
        SessionSubscribers sessionSubscribers,
        List<SessionNotification> cachedSessionNotifications) {
      return new AutoValue_SessionManager_PendingSession(
          sessionDetail, finalResultFuture, sessionSubscribers, cachedSessionNotifications);
    }
  }

  @AutoValue
  abstract static class RunningSession {
    abstract SessionRunner sessionRunner();

    abstract SettableFuture<SessionDetail> finalResultFuture();

    abstract SessionSubscribers sessionSubscribers();

    private static RunningSession of(
        SessionRunner sessionRunner,
        SettableFuture<SessionDetail> finalResultFuture,
        SessionSubscribers sessionSubscribers) {
      return new AutoValue_SessionManager_RunningSession(
          sessionRunner, finalResultFuture, sessionSubscribers);
    }
  }

  /** Subscriber of a session. */
  private class SessionSubscriber {

    private final StreamObserver<SubscribeSessionRequest> requestObserver =
        new SubscribeSessionRequestObserver();
    private final StreamObserver<SubscribeSessionResponse> responseObserver;

    private final String subscriberId = UUID.randomUUID().toString();
    private final AtomicReference<SubscribeSessionRequest> request = new AtomicReference<>();

    /** Set in {@link #start(SubscribeSessionRequest)}. */
    @Nullable private volatile FieldMask getSessionResponseFieldMask;

    /** Set in {@link #start(SubscribeSessionRequest)}. */
    @Nullable private volatile FieldMask sessionDetailFieldMask;

    /** {@link SessionSubscribers} which contains this subscriber. */
    @GuardedBy("sessionsLock")
    private SessionSubscribers subscribers;

    @GuardedBy("sessionsLock")
    private boolean closed;

    private SessionSubscriber(StreamObserver<SubscribeSessionResponse> responseObserver) {
      this.responseObserver = responseObserver;
    }

    private StreamObserver<SubscribeSessionRequest> getRequestObserver() {
      return requestObserver;
    }

    private void start(SubscribeSessionRequest request) {
      // Checks if it already started.
      if (!this.request.compareAndSet(/* expectedValue= */ null, request)) {
        logger.atWarning().log(
            "SubscribeSessionRequest [%s] is ignored since session subscriber [%s] has already"
                + " received a request",
            shortDebugString(request), subscriberId);
        return;
      }
      logger.atInfo().log(
          "Session subscriber [%s] received SubscribeSessionRequest [%s]",
          subscriberId, shortDebugString(request));

      // Sets field masks.
      GetSessionRequest getSessionRequest = request.getGetSessionRequest();
      getSessionResponseFieldMask =
          getSessionRequest.hasFieldMask() ? getSessionRequest.getFieldMask() : null;
      sessionDetailFieldMask = getSessionDetailFieldMask(getSessionRequest).orElse(null);

      synchronized (sessionsLock) {
        // Checks if it already closed.
        if (closed) {
          logger.atWarning().log("Session subscriber [%s] has been closed", subscriberId);
          return;
        }
        String sessionId = getSessionRequest.getSessionId().getId();

        // Finds an archived session if any.
        if (archivedSessions.containsKey(sessionId)) {
          logger.atInfo().log("Sending SessionDetail of the archived session [%s]", sessionId);
          SessionDetail finalSessionDetail = archivedSessions.get(sessionId);
          receiveSessionDetail(fieldMask -> finalSessionDetail, /* finalSessionDetail= */ true);
          return;
        }

        // Finds a running/pending session if any.
        if (runningSessions.containsKey(sessionId)) {
          subscribers = runningSessions.get(sessionId).sessionSubscribers();
        } else if (pendingSessions.containsKey(sessionId)) {
          subscribers = pendingSessions.get(sessionId).sessionSubscribers();
        } else {
          logger.atWarning().log("Session [%s] is not found", sessionId);
          responseObserver.onError(
              new MobileHarnessException(
                  InfraErrorId.OLCS_SUBSCRIBE_SESSION_SESSION_NOT_FOUND,
                  String.format("Session not found, id=[%s]", sessionId)));
          return;
        }

        // Subscribes the session.
        logger.atInfo().log(
            "Session subscriber [%s] starting subscribing session [%s]", subscriberId, sessionId);
        subscribers.addSubscriber(this);

        // Sends the first SessionDetail.
        receiveSessionDetail(subscribers.sessionDetailSupplier, /* finalSessionDetail= */ false);
      }
    }

    private void end() {
      logger.atInfo().log("Closing session subscriber [%s]", subscriberId);
      responseObserver.onCompleted();
      synchronized (sessionsLock) {
        closed = true;
        if (subscribers != null) {
          subscribers.removeSubscriber(this);
          subscribers = null;
        }
      }
    }

    private void receiveSessionDetail(
        Function<FieldMask, SessionDetail> sessionDetailSupplier, boolean finalSessionDetail) {
      // Creates SubscribeSessionResponse.
      SessionDetail sessionDetail = sessionDetailSupplier.apply(sessionDetailFieldMask);
      GetSessionResponse getSessionResponse =
          GetSessionResponse.newBuilder().setSessionDetail(sessionDetail).build();
      if (getSessionResponseFieldMask != null) {
        getSessionResponse = FieldMaskUtil.trim(getSessionResponseFieldMask, getSessionResponse);
      }
      SubscribeSessionResponse subscribeSessionResponse =
          SubscribeSessionResponse.newBuilder().setGetSessionResponse(getSessionResponse).build();

      // Sends SubscribeSessionResponse.
      responseObserver.onNext(subscribeSessionResponse);
      logger.atInfo().log(
          "Session subscriber [%s] sent SubscribeSessionResponse [%s]",
          subscriberId, shortDebugString(subscribeSessionResponse));

      // Closes the stream if it is the last SessionDetail.
      if (finalSessionDetail) {
        responseObserver.onCompleted();
        logger.atInfo().log(
            "Session subscriber [%s] has sent the last SessionDetail", subscriberId);
      }
    }

    private class SubscribeSessionRequestObserver
        implements StreamObserver<SubscribeSessionRequest> {

      @Override
      public void onNext(SubscribeSessionRequest request) {
        start(request);
      }

      @Override
      public void onError(Throwable error) {
        logger.atWarning().withCause(error).log(
            "Received an error from request stream of session subscriber [%s]", subscriberId);
        end();
      }

      @Override
      public void onCompleted() {
        end();
      }
    }
  }

  /** All {@link SessionSubscriber}s of a session. */
  static class SessionSubscribers {

    private final Runnable sessionDetailListener = new SessionDetailListener();
    private final Set<SessionSubscriber> subscribers = ConcurrentHashMap.newKeySet();
    private volatile Function<FieldMask, SessionDetail> sessionDetailSupplier;

    /** Returns a listener which should be invoked when the {@link SessionDetail} is modified. */
    private Runnable getSessionDetailListener() {
      return sessionDetailListener;
    }

    private void addSubscriber(SessionSubscriber subscriber) {
      subscribers.add(subscriber);
    }

    private void removeSubscriber(SessionSubscriber subscriber) {
      subscribers.remove(subscriber);
    }

    private void setSessionDetailSupplier(
        Function<FieldMask, SessionDetail> sessionDetailSupplier) {
      this.sessionDetailSupplier = sessionDetailSupplier;
    }

    private void receiveSessionDetail(boolean finalSessionDetail) {
      Function<FieldMask, SessionDetail> sessionDetailSupplier =
          SessionSubscribers.this.sessionDetailSupplier;
      for (SessionSubscriber subscriber : subscribers) {
        subscriber.receiveSessionDetail(sessionDetailSupplier, finalSessionDetail);
      }
    }

    private void close(SessionDetail finalSessionDetail) {
      setSessionDetailSupplier(fieldMask -> finalSessionDetail);
      receiveSessionDetail(/* finalSessionDetail= */ true);
    }

    private class SessionDetailListener implements Runnable {

      @Override
      public void run() {
        receiveSessionDetail(/* finalSessionDetail= */ false);
      }
    }
  }

  public static Optional<FieldMask> getSessionDetailFieldMask(GetSessionRequest request) {
    return Optional.ofNullable(request.hasFieldMask() ? request.getFieldMask() : null)
        .flatMap(
            getSessionResponseFieldMask ->
                FieldMaskUtils.subFieldMask(
                    getSessionResponseFieldMask,
                    GetSessionResponse.getDescriptor()
                        .findFieldByNumber(GetSessionResponse.SESSION_DETAIL_FIELD_NUMBER)));
  }
}
