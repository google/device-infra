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
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.SessionEnvironmentPreparer.SessionEnvironment;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionDetailHolder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionPlugin;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionNotification;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.devtools.mobileharness.shared.util.event.EventBusBackend.Subscriber;
import com.google.inject.assistedinject.Assisted;
import com.google.protobuf.FieldMask;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

/** Session runner for running a session. */
public class SessionRunner implements Callable<Void> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Factory for creating {@link SessionRunner}. */
  public interface Factory {

    SessionRunner create(
        SessionDetail sessionDetail,
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

  @GuardedBy("itself")
  private final List<ListenableFuture<?>> sessionNotifyingFutures = new ArrayList<>();

  @GuardedBy("sessionNotifyingFutures")
  private boolean receiveSessionNotification = true;

  private volatile SessionEnvironment sessionEnvironment;

  @Inject
  SessionRunner(
      @Assisted SessionDetail sessionDetail,
      @Assisted Runnable sessionDetailListener,
      @Assisted ImmutableList<SessionNotification> cachedSessionNotifications,
      SessionEnvironmentPreparer sessionEnvironmentPreparer,
      SessionJobCreator sessionJobCreator,
      SessionJobRunner sessionJobRunner,
      SessionPluginLoader sessionPluginLoader,
      SessionPluginRunner sessionPluginRunner,
      ListeningExecutorService threadPool) {
    this.sessionDetailHolder = new SessionDetailHolder(sessionDetail, sessionDetailListener);
    this.cachedSessionNotifications = cachedSessionNotifications;
    this.sessionEnvironmentPreparer = sessionEnvironmentPreparer;
    this.sessionJobCreator = sessionJobCreator;
    this.sessionJobRunner = sessionJobRunner;
    this.sessionPluginLoader = sessionPluginLoader;
    this.sessionPluginRunner = sessionPluginRunner;
    this.threadPool = threadPool;
  }

  @Override
  public Void call() throws MobileHarnessException, InterruptedException {
    // Prepares environment.
    sessionEnvironment = sessionEnvironmentPreparer.prepareEnvironment(sessionDetailHolder);

    logger.atInfo().log(
        "Starting session runner %s, session_detail:\n%s",
        sessionDetailHolder.getSessionId(),
        sessionDetailHolder.buildSessionDetail(/* fieldMask= */ null));

    // Creates OmniLab jobs.
    sessionJobCreator.createAndAddJobs(sessionDetailHolder);

    // Loads session plugins.
    ImmutableList<SessionPlugin> sessionPlugins =
        sessionPluginLoader.loadSessionPlugins(sessionDetailHolder, sessionEnvironment);
    sessionPluginRunner.initialize(sessionDetailHolder, sessionPlugins);

    // Sends cached session notifications synchronously.
    cachedSessionNotifications.forEach(sessionPluginRunner::onSessionNotification);

    // Calls sessionPlugin.onStarting().
    sessionPluginRunner.onSessionStarting();

    Throwable sessionError = null;
    try {
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
      // Calls sessionPlugin.onEnded().
      sessionPluginRunner.onSessionEnded(sessionError);

      // Waits until all session notifications have been sent.
      waitSessionNotifying();

      // Closes session plugin resources.
      sessionPlugins.stream()
          .map(SessionPlugin::closeableResource)
          .forEach(NonThrowingAutoCloseable::close);
    }
    return null;
  }

  public Optional<SessionEnvironment> getSessionEnvironment() {
    return Optional.ofNullable(sessionEnvironment);
  }

  /**
   * Gets {@link SessionDetail} of the session.
   *
   * @param fieldMask a field mask relative to SessionDetail. {@code null} means all fields are
   *     required. It is acceptable that the implementation outputs more fields than the field mask
   *     requires.
   */
  public SessionDetail getSession(@Nullable FieldMask fieldMask) {
    return sessionDetailHolder.buildSessionDetail(fieldMask);
  }

  public SessionConfig getSessionConfig() {
    return sessionDetailHolder.getSessionConfig();
  }

  public boolean notifySession(SessionNotification sessionNotification) {
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

  public void abortSession() {
    sessionDetailHolder.putSessionProperty(
        SessionProperties.PROPERTY_KEY_SESSION_ABORTED_WHEN_RUNNING, "true");

    sessionJobRunner.abort();
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
