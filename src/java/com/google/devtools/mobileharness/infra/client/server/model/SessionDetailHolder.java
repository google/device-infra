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

package com.google.devtools.mobileharness.infra.client.server.model;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.client.server.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.server.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.server.proto.SessionProto.SessionOutput;
import com.google.devtools.mobileharness.infra.client.server.proto.SessionProto.SessionPluginError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.concurrent.GuardedBy;

/** Internal data model for managing {@link SessionDetail} of a running session. */
public class SessionDetailHolder {

  private final Object lock = new Object();

  /**
   * {@link SessionDetail} without {@link SessionOutput#getSessionPropertyMap()} and {@link
   * SessionOutput#getSessionPluginErrorList()}.
   */
  @GuardedBy("lock")
  private final SessionDetail.Builder sessionDetailBuilder;

  /** {@link SessionOutput#getSessionPropertyMap()}. */
  @GuardedBy("lock")
  private final Map<String, String> sessionProperties;

  /** {@link SessionOutput#getSessionPluginErrorList()}. */
  @GuardedBy("lock")
  private final List<SessionPluginError> sessionPluginErrors = new ArrayList<>();

  /**
   * Constructor with an initial {@link SessionDetail}. {@link
   * SessionOutput#getSessionPropertyMap()} will be copied from {@link
   * SessionConfig#getSessionPropertyMap()}.
   */
  public SessionDetailHolder(SessionDetail sessionDetail) {
    this.sessionDetailBuilder = sessionDetail.toBuilder();
    this.sessionProperties =
        new HashMap<>(sessionDetail.getSessionConfig().getSessionPropertyMap());
  }

  /** Builds and returns a latest {@link SessionDetail}. */
  public SessionDetail buildSessionDetail() {
    synchronized (lock) {
      SessionOutput.Builder sessionOutputBuilder = sessionDetailBuilder.getSessionOutputBuilder();
      sessionOutputBuilder.clearSessionProperty();
      sessionOutputBuilder.putAllSessionProperty(sessionProperties);
      sessionOutputBuilder.clearSessionPluginError();
      sessionOutputBuilder.addAllSessionPluginError(sessionPluginErrors);
      return sessionDetailBuilder.build();
    }
  }

  public String getSessionId() {
    synchronized (lock) {
      return sessionDetailBuilder.getSessionId().getId();
    }
  }

  public SessionConfig getSessionConfig() {
    synchronized (lock) {
      return sessionDetailBuilder.getSessionConfig();
    }
  }

  public Map<String, String> getAllSessionProperties() {
    synchronized (lock) {
      return ImmutableMap.copyOf(sessionProperties);
    }
  }

  public Optional<String> getSessionProperty(String key) {
    synchronized (lock) {
      return Optional.ofNullable(sessionProperties.get(key));
    }
  }

  public Optional<String> putSessionProperty(String key, String value) {
    synchronized (lock) {
      return Optional.ofNullable(sessionProperties.put(key, value));
    }
  }

  public void addSessionPluginError(SessionPluginError sessionPluginError) {
    synchronized (lock) {
      sessionPluginErrors.add(sessionPluginError);
    }
  }
}
