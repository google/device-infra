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
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static com.google.common.util.concurrent.Futures.whenAllComplete;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugStringWithPrinter;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.infra.client.api.util.longevity.LongevityTestHelper;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.SessionEnvironmentPreparer.SessionEnvironment;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionDetailHolder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionPlugin;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionNotification;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPersistenceData;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPersistenceStatus;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.VersionProtoUtil;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.persistence.SessionPersistenceUtil;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.devtools.mobileharness.shared.util.concurrent.Callables;
import com.google.devtools.mobileharness.shared.util.event.EventBusBackend.Subscriber;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.inject.assistedinject.Assisted;
import com.google.protobuf.FieldMask;
import com.google.protobuf.TextFormat.Printer;
import com.google.protobuf.TypeRegistry;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

/** Session runner for running a session. */
public class SessionRunner implements Callable<Void> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * IDs of sessions which have posted {@code SessionStartingEvent} but have not posted {@code
   * SessionStartedEvent}.
   */
  @GuardedBy("itself")
  private static final Set<String> startedRunningSessionIds = new HashSet<>();

  /** Factory for creating {@link SessionRunner}. */
  public interface Factory {

    SessionRunner create(
        SessionDetail sessionDetail,
        SessionPersistenceStatus initialSessionPersistenceStatus,
        ImmutableList<String> toBeResumedJobIds,
        Runnable sessionDetailListener,
        ImmutableList<SessionNotification> cachedSessionNotifications);
  }

  private final SessionDetailHolder sessionDetailHolder;
  private final ImmutableList<SessionNotification> cachedSessionNotifications;
  private final SessionEnvironmentPreparer sessionEnvironmentPreparer;
  private final SessionJobCreator sessionJobCreator;
  private final SessionJobRunner sessionJobRunner;
  private final SessionPluginLoader sessionPluginLoader;
  private final SessionPluginRunner sessionPluginRunner;
  private final ListeningExecutorService threadPool;
  private final SystemUtil systemUtil;
  private final SessionPersistenceUtil sessionPersistenceUtil;
  private final LongevityTestHelper longevityTestHelper;
  private final SessionPersistenceStatus initialSessionPersistenceStatus;
  private final ImmutableList<String> toBeResumedJobIds;

  @GuardedBy("itself")
  private final List<ListenableFuture<?>> sessionNotifyingFutures = new ArrayList<>();

  @GuardedBy("sessionNotifyingFutures")
  private boolean receiveSessionNotification = true;

  @GuardedBy("startedRunningSessionIds")
  private boolean sessionAborted;

  private volatile SessionEnvironment sessionEnvironment;

  @Inject
  SessionRunner(
      @Assisted SessionDetail sessionDetail,
      @Assisted SessionPersistenceStatus initialSessionPersistenceStatus,
      @Assisted ImmutableList<String> toBeResumedJobIds,
      @Assisted Runnable sessionDetailListener,
      @Assisted ImmutableList<SessionNotification> cachedSessionNotifications,
      SessionEnvironmentPreparer sessionEnvironmentPreparer,
      SessionJobCreator sessionJobCreator,
      SessionJobRunner sessionJobRunner,
      SessionPluginLoader sessionPluginLoader,
      SessionPluginRunner sessionPluginRunner,
      ListeningExecutorService threadPool,
      SystemUtil systemUtil,
      SessionPersistenceUtil sessionPersistenceUtil,
      LongevityTestHelper longevityTestHelper) {
    this.sessionDetailHolder = new SessionDetailHolder(sessionDetail, sessionDetailListener);
    this.initialSessionPersistenceStatus = initialSessionPersistenceStatus;
    this.toBeResumedJobIds = toBeResumedJobIds;
    this.cachedSessionNotifications = cachedSessionNotifications;
    this.sessionEnvironmentPreparer = sessionEnvironmentPreparer;
    this.sessionJobCreator = sessionJobCreator;
    this.sessionJobRunner = sessionJobRunner;
    this.sessionPluginLoader = sessionPluginLoader;
    this.sessionPluginRunner = sessionPluginRunner;
    this.threadPool = threadPool;
    this.systemUtil = systemUtil;
    this.sessionPersistenceUtil = sessionPersistenceUtil;
    this.longevityTestHelper = longevityTestHelper;
  }

  @Override
  public Void call() throws MobileHarnessException, InterruptedException {
    // Prepares environment.
    sessionEnvironment = sessionEnvironmentPreparer.prepareEnvironment(sessionDetailHolder);

    String sessionId = sessionDetailHolder.getSessionId();

    logger.atInfo().log(
        "Starting session runner %s, server_version=[%s], memory_info=[%s]",
        sessionId,
        shortDebugString(VersionProtoUtil.createGetVersionResponse()),
        systemUtil.getMemoryInfo());

    // Loads session plugins.
    ImmutableList<SessionPlugin> sessionPlugins =
        sessionPluginLoader.loadSessionPlugins(sessionDetailHolder, sessionEnvironment);
    sessionPluginRunner.initialize(sessionDetailHolder, sessionPlugins);

    // Creates type registry.
    sessionDetailHolder.setTypeRegistry(
        TypeRegistry.newBuilder()
            .add(
                sessionPlugins.stream()
                    .map(SessionPlugin::descriptors)
                    .flatMap(Collection::stream)
                    .collect(toImmutableList()))
            .build());

    logger.atInfo().log(
        "Session detail:\n%s",
        shortDebugStringWithPrinter(
            sessionDetailHolder.buildSessionDetail(/* fieldMask= */ null),
            sessionDetailHolder.getProtoPrinter()));

    // Creates OmniLab jobs.
    sessionJobCreator.createAndAddJobs(sessionDetailHolder);

    // Sends cached session notifications synchronously.
    cachedSessionNotifications.forEach(sessionPluginRunner::onSessionNotification);

    if (initialSessionPersistenceStatus.compareTo(SessionPersistenceStatus.SESSION_STARTED) < 0) {
      // Calls sessionPlugin.onStarting().
      sessionPluginRunner.onSessionStarting();
    }

    Throwable sessionError = null;
    try {
      // Checks started and running session number.
      int maxStartedRunningSessionNum =
          Flags.instance().olcServerMaxStartedRunningSessionNum.getNonNull();
      synchronized (startedRunningSessionIds) {
        while (true) {
          if (sessionAborted) {
            throw MobileHarnessExceptionFactory.createUserFacingException(
                InfraErrorId.OLCS_SESSION_ABORTED_WHEN_QUEUEING,
                "Session aborted when waiting to become started",
                /* cause= */ null);
          }
          if (startedRunningSessionIds.size() < maxStartedRunningSessionNum) {
            startedRunningSessionIds.add(sessionId);
            break;
          }
          startedRunningSessionIds.wait();
        }
      }

      if (initialSessionPersistenceStatus.compareTo(SessionPersistenceStatus.SESSION_STARTED) < 0) {
        // Calls sessionPlugin.onStarted().
        sessionPluginRunner.onSessionStarted();
        persistSession(SessionPersistenceStatus.SESSION_STARTED);
        for (JobInfo jobInfo : sessionDetailHolder.getAllJobs()) {
          jobInfo
              .params()
              .add(
                  LongevityTestHelper.PARAM_JOB_PERSISTENT_PATH,
                  getJobPersistencePath(jobInfo.locator().getId()));
          longevityTestHelper.persistentJobInfoIfNeeded(jobInfo);
        }
      } else {
        for (String jobId : toBeResumedJobIds) {
          try {
            Optional<JobInfo> jobInfo =
                longevityTestHelper.resumeJobInfo(getJobPersistencePath(jobId), true, null, null);
            jobInfo.ifPresent(sessionDetailHolder::addJob);
          } catch (MobileHarnessException e) {
            logger.atWarning().withCause(e).log("Failed to resume job [%s]", jobId);
          }
        }
      }
      // Starts all jobs and wait until they finish.
      sessionJobRunner.runJobs(
          sessionDetailHolder,
          sessionPlugins.stream()
              .map(SessionPlugin::subscriber)
              .map(Subscriber::subscriberObject)
              .collect(toImmutableList()));
    } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
      sessionError = e;
      throw e;
    } finally {
      Throwable finalSessionError = sessionError;
      Callables.callAll(
          () -> {
            if (initialSessionPersistenceStatus.compareTo(SessionPersistenceStatus.SESSION_ENDED)
                < 0) {
              // Calls sessionPlugin.onEnded().
              sessionPluginRunner.onSessionEnded(finalSessionError);
              persistSession(SessionPersistenceStatus.SESSION_ENDED);
            }
            return null;
          },
          () -> {
            // Waits until all session notifications have been sent.
            waitSessionNotifying();
            return null;
          },
          () -> {
            // Closes session plugin resources.
            sessionPlugins.stream()
                .map(SessionPlugin::closeableResource)
                .forEach(NonThrowingAutoCloseable::close);
            return null;
          },
          () -> {
            synchronized (startedRunningSessionIds) {
              startedRunningSessionIds.remove(sessionId);
              startedRunningSessionIds.notifyAll();
            }
            return null;
          });
    }
    return null;
  }

  private void persistSession(SessionPersistenceStatus sessionPersistenceStatus) {
    try {
      sessionPersistenceUtil.persistSession(
          SessionPersistenceData.newBuilder()
              .setSessionDetail(sessionDetailHolder.buildSessionDetail(null))
              .setSessionPersistenceStatus(sessionPersistenceStatus)
              .addAllJobId(
                  sessionDetailHolder.getAllJobs().stream()
                      .map(jobInfo -> jobInfo.locator().getId())
                      .collect(toImmutableList()))
              .build());
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to persist session [%s]", sessionDetailHolder.getSessionId());
    }
  }

  private String getJobPersistencePath(String jobId) {
    return PathUtil.join(
        sessionEnvironment.sessionTempDir().toString(), "persistence", jobId + ".textproto");
  }

  Optional<SessionEnvironment> getSessionEnvironment() {
    return Optional.ofNullable(sessionEnvironment);
  }

  /**
   * Gets {@link SessionDetail} of the session.
   *
   * @param fieldMask a field mask relative to SessionDetail. {@code null} means all fields are
   *     required. It is acceptable that the implementation outputs more fields than the field mask
   *     requires.
   */
  SessionDetail getSession(@Nullable FieldMask fieldMask) {
    return sessionDetailHolder.buildSessionDetail(fieldMask);
  }

  SessionConfig getSessionConfig() {
    return sessionDetailHolder.getSessionConfig();
  }

  String getSessionId() {
    return sessionDetailHolder.getSessionId();
  }

  boolean notifySession(SessionNotification sessionNotification) {
    synchronized (sessionNotifyingFutures) {
      if (!receiveSessionNotification) {
        return false;
      }
      ListenableFuture<?> sessionNotifyingFuture =
          threadPool.submit(
              threadRenaming(
                  () -> sessionPluginRunner.onSessionNotification(sessionNotification),
                  () -> "session-notifier-" + sessionDetailHolder.getSessionId()));
      sessionNotifyingFutures.add(sessionNotifyingFuture);
      return true;
    }
  }

  void abortSession() {
    synchronized (startedRunningSessionIds) {
      sessionAborted = true;
      startedRunningSessionIds.notifyAll();
    }

    sessionDetailHolder.putSessionProperty(
        SessionProperties.PROPERTY_KEY_SESSION_ABORTED_WHEN_RUNNING, "true");

    sessionJobRunner.abort();
  }

  Printer getProtoPrinter() {
    return sessionDetailHolder.getProtoPrinter();
  }

  private void waitSessionNotifying() {
    ImmutableList<ListenableFuture<?>> sessionNotifyingFutures;
    synchronized (this.sessionNotifyingFutures) {
      sessionNotifyingFutures = ImmutableList.copyOf(this.sessionNotifyingFutures);
      receiveSessionNotification = false;
    }
    getUnchecked(whenAllComplete(sessionNotifyingFutures).run(() -> {}, threadPool));
  }
}
