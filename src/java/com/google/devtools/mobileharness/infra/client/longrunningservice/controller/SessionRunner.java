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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionDetailHolder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionPlugin;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.inject.assistedinject.Assisted;
import com.google.protobuf.FieldMask;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Session runner for running a session. */
public class SessionRunner implements Callable<Void> {

  /** Factory for creating {@link SessionRunner}. */
  public interface Factory {

    SessionRunner create(SessionDetail sessionDetail);
  }

  private final SessionDetailHolder sessionDetailHolder;
  private final SessionJobCreator sessionJobCreator;
  private final SessionJobRunner sessionJobRunner;
  private final SessionPluginLoader sessionPluginLoader;
  private final SessionPluginRunner sessionPluginRunner;

  @Inject
  SessionRunner(
      @Assisted SessionDetail sessionDetail,
      SessionJobCreator sessionJobCreator,
      SessionJobRunner sessionJobRunner,
      SessionPluginLoader sessionPluginLoader,
      SessionPluginRunner sessionPluginRunner) {
    this.sessionDetailHolder = new SessionDetailHolder(sessionDetail);
    this.sessionJobCreator = sessionJobCreator;
    this.sessionJobRunner = sessionJobRunner;
    this.sessionPluginLoader = sessionPluginLoader;
    this.sessionPluginRunner = sessionPluginRunner;
  }

  @Override
  public Void call() throws MobileHarnessException, InterruptedException {
    // Creates OmniLab jobs.
    sessionJobCreator.createAndAddJobs(sessionDetailHolder);

    // Loads session plugins.
    ImmutableList<SessionPlugin> sessionPlugins =
        sessionPluginLoader.loadSessionPlugins(sessionDetailHolder);
    sessionPluginRunner.initialize(sessionDetailHolder, sessionPlugins);

    // Calls sessionPlugin.onStarting().
    sessionPluginRunner.onSessionStarting();

    Throwable sessionError = null;
    try {
      // Starts all jobs and wait until they finish.
      sessionJobRunner.runJobs(
          sessionDetailHolder,
          sessionPlugins.stream().map(SessionPlugin::plugin).collect(toImmutableList()));
    } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
      sessionError = e;
      throw e;
    } finally {
      // Calls sessionPlugin.onEnded().
      sessionPluginRunner.onSessionEnded(sessionError);
    }

    return null;
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
}
