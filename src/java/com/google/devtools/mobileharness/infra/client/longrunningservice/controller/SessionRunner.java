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

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionDetailHolder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLabel;
import com.google.inject.assistedinject.Assisted;
import java.util.concurrent.Callable;
import javax.inject.Inject;

/** Session runner for running a session. */
public class SessionRunner implements Callable<Void> {

  /** Factory for creating {@link SessionRunner}. */
  public interface Factory {

    SessionRunner create(SessionDetail sessionDetail);
  }

  private final SessionDetailHolder sessionDetailHolder;
  private final SessionInfoCreator sessionInfoCreator;
  private final SessionJobRunner sessionJobRunner;
  private final SessionPluginLoader sessionPluginLoader;
  private final SessionPluginRunner sessionPluginRunner;

  @Inject
  SessionRunner(
      @Assisted SessionDetail sessionDetail,
      SessionInfoCreator sessionInfoCreator,
      SessionJobRunner sessionJobRunner,
      SessionPluginLoader sessionPluginLoader,
      SessionPluginRunner sessionPluginRunner) {
    this.sessionDetailHolder = new SessionDetailHolder(sessionDetail);
    this.sessionInfoCreator = sessionInfoCreator;
    this.sessionJobRunner = sessionJobRunner;
    this.sessionPluginLoader = sessionPluginLoader;
    this.sessionPluginRunner = sessionPluginRunner;
  }

  @Override
  public Void call() throws MobileHarnessException, InterruptedException {
    // Creates SessionInfo and JobInfo.
    SessionInfo sessionInfo = sessionInfoCreator.create(sessionDetailHolder);

    // Loads session plugins.
    ImmutableMap<SessionPluginLabel, Object> sessionPlugins =
        sessionPluginLoader.loadSessionPlugins(
            sessionDetailHolder.getSessionConfig().getSessionPluginConfigs(), sessionInfo);
    sessionPluginRunner.initialize(sessionDetailHolder, sessionInfo, sessionPlugins);

    // Calls sessionPlugin.onStarting().
    sessionPluginRunner.onSessionStarting();

    Throwable sessionError = null;
    try {
      // Starts all jobs and wait until they finish.
      sessionJobRunner.runJobs(
          sessionDetailHolder, sessionInfo.getAllJobs(), sessionPlugins.values());
    } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
      sessionError = e;
      throw e;
    } finally {
      // Calls sessionPlugin.onEnded().
      sessionPluginRunner.onSessionEnded(sessionError);
    }

    return null;
  }

  public SessionDetail getSession() {
    return sessionDetailHolder.buildSessionDetail();
  }
}
