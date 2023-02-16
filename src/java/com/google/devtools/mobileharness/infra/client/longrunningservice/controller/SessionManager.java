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
import static com.google.common.util.concurrent.Callables.threadRenaming;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.protobuf.TextFormat.shortDebugString;
import static java.lang.Math.min;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionStatus;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
  private final ListeningScheduledExecutorService threadPool;

  private final Object lock = new Object();

  /** Submitted and non-started sessions. */
  @GuardedBy("lock")
  private final LinkedHashMap<String, SessionDetail> sessionQueue = new LinkedHashMap<>();

  /** Running sessions. */
  @GuardedBy("lock")
  private final Map<String, SessionRunner> sessionRunners = new HashMap<>();

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
      ListeningScheduledExecutorService threadPool) {
    this.sessionDetailCreator = sessionDetailCreator;
    this.sessionRunnerFactory = sessionRunnerFactory;
    this.threadPool = threadPool;
  }

  /**
   * Adds a new session to the session queue.
   *
   * @throws MobileHarnessException if the queue is full
   */
  public SessionDetail addSession(SessionConfig sessionConfig) throws MobileHarnessException {
    SessionDetail sessionDetail = sessionDetailCreator.create(sessionConfig);
    logger.atInfo().log("Create session: %s", shortDebugString(sessionDetail));
    synchronized (lock) {
      MobileHarnessExceptions.check(
          sessionQueue.size() <= SESSION_QUEUE_CAPACITY,
          InfraErrorId.OLCS_CREATE_SESSION_ERROR_SESSION_QUEUE_FULL,
          () ->
              String.format(
                  "Session queue is full(%s), failed to add session [%s]",
                  SESSION_QUEUE_CAPACITY, shortDebugString(sessionDetail)));
      sessionQueue.put(sessionDetail.getSessionId().getId(), sessionDetail);

      // Tries to start new sessions.
      startSessions();
    }
    return sessionDetail;
  }

  /**
   * Gets {@link SessionDetail} of a session by its ID.
   *
   * @throws MobileHarnessException if the session is not found (un-submitted or has been removed
   *     from archived sessions)
   */
  public SessionDetail getSession(String sessionId) throws MobileHarnessException {
    SessionDetail sessionDetail;
    // Checks the session from 3 places.
    synchronized (lock) {
      sessionDetail = archivedSessions.get(sessionId);
      if (sessionDetail == null) {
        SessionRunner sessionRunner = sessionRunners.get(sessionId);
        if (sessionRunner != null) {
          sessionDetail = sessionRunner.getSession();
        }
      }
      if (sessionDetail == null) {
        sessionDetail = sessionQueue.get(sessionId);
      }
    }
    MobileHarnessExceptions.check(
        sessionDetail != null,
        InfraErrorId.OLCS_GET_SESSION_SESSION_NOT_FOUND,
        () -> String.format("Session not found, id=[%s]", sessionId));
    logger.atInfo().log("Get session: %s", shortDebugString(sessionDetail));
    return sessionDetail;
  }

  /**
   * Gets {@link SessionDetail} of all sessions which have been submitted and have not been removed
   * from archived sessions.
   */
  public List<SessionDetail> getAllSessions() {
    synchronized (lock) {
      return Streams.concat(
              sessionQueue.values().stream(),
              sessionRunners.values().stream().map(SessionRunner::getSession),
              archivedSessions.values().stream())
          .collect(toImmutableList());
    }
  }

  /** Tries to poll as many as sessions from the session queue and starts them. */
  @GuardedBy("lock")
  private void startSessions() {
    // Poll sessions from the session queue.
    ImmutableList<SessionDetail> newSessions = pollSessions();

    // Creates session runners.
    ImmutableList<SessionRunner> newSessionRunners =
        newSessions.stream()
            .map(
                session ->
                    session.toBuilder().setSessionStatus(SessionStatus.SESSION_RUNNING).build())
            .map(sessionRunnerFactory::create)
            .collect(toImmutableList());

    // Starts session runners.
    for (SessionRunner sessionRunner : newSessionRunners) {
      logger.atInfo().log("Start session: %s", shortDebugString(sessionRunner.getSession()));
      String sessionId = sessionRunner.getSession().getSessionId().getId();
      sessionRunners.put(sessionId, sessionRunner);
      addCallback(
          threadPool.submit(threadRenaming(sessionRunner, () -> "session-runner-" + sessionId)),
          new SessionRunnerCallback(sessionRunner),
          directExecutor());
    }
  }

  /** Polls as many as sessions from the session queue. */
  @GuardedBy("lock")
  private ImmutableList<SessionDetail> pollSessions() {
    int count = sessionQueue.size();
    if (count > 0) {
      count = min(count, RUNNING_SESSION_CAPACITY - sessionRunners.size());
    }
    if (count <= 0) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<SessionDetail> result = ImmutableList.builderWithExpectedSize(count);
    Iterator<SessionDetail> iterator = sessionQueue.values().iterator();
    for (int i = 0; i < count; i++) {
      result.add(iterator.next());
      iterator.remove();
    }
    return result.build();
  }

  /** Callback for {@link SessionRunner#call()}. */
  private class SessionRunnerCallback implements FutureCallback<Void> {

    private final SessionRunner sessionRunner;

    private SessionRunnerCallback(SessionRunner sessionRunner) {
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
      SessionDetail sessionDetail = sessionRunner.getSession();
      SessionDetail.Builder sessionDetailBuilder = sessionDetail.toBuilder();
      sessionDetailBuilder.setSessionStatus(SessionStatus.SESSION_FINISHED);

      logger.atInfo().withCause(error).log("Session finished: %s", sessionDetail);

      // Adds the error thrown from the session runner to SessionDetail, if any.
      if (error != null) {
        sessionDetailBuilder.setSessionRunnerError(ErrorModelConverter.toExceptionDetail(error));
      }
      sessionDetail = sessionDetailBuilder.build();

      // Archives the session.
      String sessionId = sessionDetail.getSessionId().getId();
      synchronized (lock) {
        sessionRunners.remove(sessionId);
        archivedSessions.put(sessionId, sessionDetail);

        // Tries to start new sessions if any.
        startSessions();
      }
    }
  }
}
