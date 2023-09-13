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
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionStatus;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SessionFilter;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.FieldMask;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
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
  private static final int RUNNING_SESSION_CAPACITY = 30;

  /** Capacity for archived sessions. */
  private static final int ARCHIVED_SESSION_CAPACITY = 500;

  private final SessionDetailCreator sessionDetailCreator;
  private final SessionRunner.Factory sessionRunnerFactory;
  private final ListeningExecutorService threadPool;

  private final Object lock = new Object();

  /** Submitted and non-started sessions. */
  @GuardedBy("lock")
  private final LinkedHashMap<String, SessionDetailAndFinalResultFuture> sessionQueue =
      new LinkedHashMap<>();

  /** Running sessions. */
  @GuardedBy("lock")
  private final Map<String, SessionRunnerAndFinalResultFuture> sessionRunners = new HashMap<>();

  /** Archived sessions. */
  @GuardedBy("lock")
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
      ListeningExecutorService threadPool) {
    this.sessionDetailCreator = sessionDetailCreator;
    this.sessionRunnerFactory = sessionRunnerFactory;
    this.threadPool = threadPool;
  }

  /**
   * Adds a new session to the session queue.
   *
   * @throws MobileHarnessException if the queue is full
   */
  @CanIgnoreReturnValue
  public SessionAddingResult addSession(SessionConfig sessionConfig) throws MobileHarnessException {
    SessionDetail sessionDetail = sessionDetailCreator.create(sessionConfig);
    logger.atInfo().log("Create session: %s", shortDebugString(sessionDetail));
    SessionDetailAndFinalResultFuture sessionDetailAndFinalResultFuture =
        SessionDetailAndFinalResultFuture.of(sessionDetail, SettableFuture.create());
    synchronized (lock) {
      MobileHarnessExceptions.check(
          sessionQueue.size() <= SESSION_QUEUE_CAPACITY,
          InfraErrorId.OLCS_CREATE_SESSION_ERROR_SESSION_QUEUE_FULL,
          () ->
              String.format(
                  "Session queue is full(%s), failed to add session [%s]",
                  SESSION_QUEUE_CAPACITY, shortDebugString(sessionDetail)));
      sessionQueue.put(sessionDetail.getSessionId().getId(), sessionDetailAndFinalResultFuture);

      // Tries to start new sessions.
      startSessions();
    }
    return SessionAddingResult.of(sessionDetailAndFinalResultFuture);
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
    // Checks the session from 3 places.
    synchronized (lock) {
      sessionDetail = archivedSessions.get(sessionId);
      if (sessionDetail == null) {
        SessionRunnerAndFinalResultFuture runningSession = sessionRunners.get(sessionId);
        if (runningSession != null) {
          sessionDetail = runningSession.sessionRunner().getSession(fieldMask);
        }
      }
      if (sessionDetail == null) {
        SessionDetailAndFinalResultFuture pendingSession = sessionQueue.get(sessionId);
        if (pendingSession != null) {
          sessionDetail = pendingSession.sessionDetail();
        }
      }
    }
    MobileHarnessExceptions.check(
        sessionDetail != null,
        InfraErrorId.OLCS_GET_SESSION_SESSION_NOT_FOUND,
        () -> String.format("Session not found, id=[%s]", sessionId));
    logger.atFine().log("Get session: %s", shortDebugString(sessionDetail));
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
    Predicate<SessionConfig> sessionConfigFilter = getSessionConfigFilter(sessionFilter);
    synchronized (lock) {
      return Streams.concat(
              sessionStatusFilter.test(SessionStatus.SESSION_SUBMITTED)
                  ? sessionQueue.values().stream()
                      .map(SessionDetailAndFinalResultFuture::sessionDetail)
                      .filter(
                          sessionDetail ->
                              sessionConfigFilter.test(sessionDetail.getSessionConfig()))
                  : Stream.empty(),
              sessionStatusFilter.test(SessionStatus.SESSION_RUNNING)
                  ? sessionRunners.values().stream()
                      .map(SessionRunnerAndFinalResultFuture::sessionRunner)
                      .filter(
                          sessionRunner ->
                              sessionConfigFilter.test(sessionRunner.getSessionConfig()))
                      .map(sessionRunner -> sessionRunner.getSession(fieldMask))
                  : Stream.empty(),
              sessionStatusFilter.test(SessionStatus.SESSION_FINISHED)
                  ? archivedSessions.values().stream()
                      .filter(
                          sessionDetail ->
                              sessionConfigFilter.test(sessionDetail.getSessionConfig()))
                  : Stream.empty())
          .collect(toImmutableList());
    }
  }

  public boolean hasUnarchivedSessions() {
    synchronized (lock) {
      return !sessionRunners.isEmpty() || !sessionQueue.isEmpty();
    }
  }

  /** Tries to poll as many as sessions from the session queue and starts them. */
  @GuardedBy("lock")
  private void startSessions() {
    // Poll sessions from the session queue.
    ImmutableList<SessionDetailAndFinalResultFuture> newSessions = pollSessions();

    // Creates session runners.
    ImmutableList<SessionRunnerAndFinalResultFuture> newSessionRunners =
        newSessions.stream()
            .map(
                session ->
                    SessionRunnerAndFinalResultFuture.of(
                        sessionRunnerFactory.create(
                            session.sessionDetail().toBuilder()
                                .setSessionStatus(SessionStatus.SESSION_RUNNING)
                                .build()),
                        session.finalResultFuture()))
            .collect(toImmutableList());

    // Starts session runners.
    for (SessionRunnerAndFinalResultFuture sessionRunner : newSessionRunners) {
      SessionDetail sessionDetail = sessionRunner.sessionRunner().getSession(/* fieldMask= */ null);
      logger.atInfo().log("Start session: %s", shortDebugString(sessionDetail));
      String sessionId = sessionDetail.getSessionId().getId();
      sessionRunners.put(sessionId, sessionRunner);
      addCallback(
          threadPool.submit(
              threadRenaming(sessionRunner.sessionRunner(), () -> "session-runner-" + sessionId)),
          new SessionRunnerCallback(sessionRunner),
          directExecutor());
    }
  }

  /** Polls as many as sessions from the session queue. */
  @GuardedBy("lock")
  private ImmutableList<SessionDetailAndFinalResultFuture> pollSessions() {
    int count = sessionQueue.size();
    if (count > 0) {
      count = min(count, RUNNING_SESSION_CAPACITY - sessionRunners.size());
    }
    if (count <= 0) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<SessionDetailAndFinalResultFuture> result =
        ImmutableList.builderWithExpectedSize(count);
    Iterator<SessionDetailAndFinalResultFuture> iterator = sessionQueue.values().iterator();
    for (int i = 0; i < count; i++) {
      result.add(iterator.next());
      iterator.remove();
    }
    return result.build();
  }

  /** Callback for {@link SessionRunner#call()}. */
  private class SessionRunnerCallback implements FutureCallback<Void> {

    private final SessionRunnerAndFinalResultFuture sessionRunner;

    private SessionRunnerCallback(SessionRunnerAndFinalResultFuture sessionRunner) {
      this.sessionRunner = sessionRunner;
    }

    @Override
    public void onSuccess(@Nullable Void result) {
      afterSession(/* error= */ null);
    }

    @Override
    public void onFailure(Throwable error) {
      afterSession(error);
    }

    private void afterSession(@Nullable Throwable error) {
      SessionDetail sessionDetail = sessionRunner.sessionRunner().getSession(/* fieldMask= */ null);
      SessionDetail.Builder sessionDetailBuilder = sessionDetail.toBuilder();
      sessionDetailBuilder.setSessionStatus(SessionStatus.SESSION_FINISHED);

      logger.atInfo().withCause(error).log("Session finished: %s", shortDebugString(sessionDetail));

      // Adds the error thrown from the session runner to SessionDetail, if any.
      if (error != null) {
        sessionDetailBuilder.setSessionRunnerError(ErrorModelConverter.toExceptionDetail(error));
      }
      SessionDetail finalSessionDetail = sessionDetailBuilder.build();

      // Archives the session.
      String sessionId = finalSessionDetail.getSessionId().getId();
      synchronized (lock) {
        sessionRunners.remove(sessionId);
        if (!finalSessionDetail.getSessionConfig().getRemoveAfterFinish()) {
          archivedSessions.put(sessionId, finalSessionDetail);
        }

        // Tries to start new sessions if any.
        startSessions();
      }

      // Completes the final result future.
      sessionRunner.finalResultFuture().set(finalSessionDetail);
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

  private static Predicate<SessionConfig> getSessionConfigFilter(
      @Nullable SessionFilter sessionFilter) {
    if (sessionFilter != null) {
      String sessionNameRegexString = sessionFilter.getSessionNameRegex();
      if (!sessionNameRegexString.isEmpty()) {
        try {
          Pattern sessionNameRegex = Pattern.compile(sessionNameRegexString);
          return sessionConfig ->
              sessionNameRegex.matcher(sessionConfig.getSessionName()).matches();
        } catch (PatternSyntaxException e) {
          logger.atWarning().withCause(e).log(
              "Invalid session name regex [%s]", sessionNameRegexString);
        }
      }
    }
    return sessionConfig -> true;
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

    private static SessionAddingResult of(
        SessionDetailAndFinalResultFuture sessionDetailAndFinalResultFuture) {
      return new AutoValue_SessionManager_SessionAddingResult(
          sessionDetailAndFinalResultFuture.sessionDetail(),
          sessionDetailAndFinalResultFuture.finalResultFuture());
    }
  }

  @AutoValue
  abstract static class SessionDetailAndFinalResultFuture {
    abstract SessionDetail sessionDetail();

    abstract SettableFuture<SessionDetail> finalResultFuture();

    private static SessionDetailAndFinalResultFuture of(
        SessionDetail sessionDetail, SettableFuture<SessionDetail> finalResultFuture) {
      return new AutoValue_SessionManager_SessionDetailAndFinalResultFuture(
          sessionDetail, finalResultFuture);
    }
  }

  @AutoValue
  abstract static class SessionRunnerAndFinalResultFuture {
    abstract SessionRunner sessionRunner();

    abstract SettableFuture<SessionDetail> finalResultFuture();

    private static SessionRunnerAndFinalResultFuture of(
        SessionRunner sessionRunner, SettableFuture<SessionDetail> finalResultFuture) {
      return new AutoValue_SessionManager_SessionRunnerAndFinalResultFuture(
          sessionRunner, finalResultFuture);
    }
  }
}
