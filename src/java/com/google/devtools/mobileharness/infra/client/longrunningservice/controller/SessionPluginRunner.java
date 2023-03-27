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

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.converter.ErrorModelConverter;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionDetailHolder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionPlugin;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginError;
import com.google.devtools.mobileharness.shared.util.event.EventBusBackend.SubscriberMethod;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Runner for running session plugins. */
public class SessionPluginRunner {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Set after {@link #initialize} is called. */
  private volatile SessionDetailHolder sessionDetailHolder;

  /** Set after {@link #initialize} is called. */
  private volatile ImmutableList<SessionPlugin> sessionPlugins;

  /** Initializes the plugin runner and scans all subscriber methods from session plugins. */
  public void initialize(
      SessionDetailHolder sessionDetailHolder, List<SessionPlugin> sessionPlugins) {
    this.sessionDetailHolder = sessionDetailHolder;
    this.sessionPlugins = ImmutableList.copyOf(sessionPlugins);
  }

  /** Posts {@link SessionStartingEvent} to session plugins. */
  public void onSessionStarting() {
    postEvent(SessionStartingEvent::new, SessionStartingEvent.class);
  }

  /** Posts {@link SessionEndedEvent} to session plugins. */
  public void onSessionEnded(@Nullable Throwable error) {
    postEvent(sessionInfo -> new SessionEndedEvent(sessionInfo, error), SessionEndedEvent.class);
  }

  private <T> void postEvent(Function<SessionInfo, T> eventGenerator, Class<T> eventClass) {
    logger.atInfo().log("Posting %s", eventClass.getSimpleName());
    // TODO: Supports skipping session.
    for (SessionPlugin sessionPlugin : sessionPlugins) {
      T event = eventGenerator.apply(sessionPlugin.sessionInfo());
      for (SubscriberMethod subscriberMethod :
          sessionPlugin.subscriberMethodSearchResult().subscriberMethods()) {
        if (subscriberMethod.canReceiveEvent(eventClass)) {
          logger.atInfo().log("Posting %s to subscriber [%s]", event, subscriberMethod);
          try {
            subscriberMethod.receiveEvent(event);
            logger.atInfo().log("Subscriber [%s] handled %s", subscriberMethod, event);
          } catch (Throwable e) {
            logger.atWarning().withCause(e).log(
                "Error from subscriber [%s] during %s", subscriberMethod, event);

            sessionDetailHolder.addSessionPluginError(
                SessionPluginError.newBuilder()
                    .setPluginLabel(sessionPlugin.sessionInfo().getSessionPluginLabel())
                    .setPluginClassName(subscriberMethod.clazz().getName())
                    .setMethodName(subscriberMethod.method().getName())
                    .setEventClassName(event.getClass().getName())
                    .setPluginIdentityHashCode(
                        System.identityHashCode(subscriberMethod.subscriberObject()))
                    .setError(ErrorModelConverter.toExceptionDetail(e))
                    .build());

            if (e instanceof InterruptedException) {
              Thread.currentThread().interrupt();
            }
          }
        }
      }
    }
  }
}
