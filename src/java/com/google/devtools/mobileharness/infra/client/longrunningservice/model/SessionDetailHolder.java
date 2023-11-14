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

package com.google.devtools.mobileharness.infra.client.longrunningservice.model;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginError;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLabel;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginOutput;
import com.google.devtools.mobileharness.shared.util.message.FieldMaskUtils;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.FieldMask;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/** Internal data model for managing states of a running session. */
public class SessionDetailHolder {

  private final Object sessionDetailLock = new Object();

  @GuardedBy("itself")
  private final List<JobInfo> jobs;

  @GuardedBy("jobs")
  private int lastPollJobsIndex;

  /**
   * {@link SessionDetail} without {@link SessionOutput#getSessionPropertyMap()}, {@link
   * SessionOutput#getSessionPluginErrorList()} and {@link
   * SessionOutput#getSessionPluginOutputMap()}.
   */
  @GuardedBy("sessionDetailLock")
  private final SessionDetail.Builder sessionDetailBuilder;

  /** {@link SessionOutput#getSessionPropertyMap()}. */
  @GuardedBy("sessionDetailLock")
  private final Map<String, String> sessionProperties;

  /** {@link SessionOutput#getSessionPluginErrorList()}. */
  @GuardedBy("sessionDetailLock")
  private final List<SessionPluginError> sessionPluginErrors = new ArrayList<>();

  /** {@link SessionOutput#getSessionPluginOutputMap()}. */
  @GuardedBy("sessionDetailLock")
  private final Map<String, SessionPluginOutput> sessionPluginOutputs = new HashMap<>();

  /**
   * Constructor with an initial {@link SessionDetail}. {@link
   * SessionOutput#getSessionPropertyMap()} will be copied from {@link
   * SessionConfig#getSessionPropertyMap()}.
   */
  public SessionDetailHolder(SessionDetail sessionDetail) {
    this.jobs = new ArrayList<>();
    this.sessionDetailBuilder = sessionDetail.toBuilder();
    this.sessionProperties =
        new HashMap<>(sessionDetail.getSessionConfig().getSessionPropertyMap());
  }

  public void addJob(JobInfo jobInfo) {
    synchronized (jobs) {
      jobs.add(jobInfo);
    }
  }

  public ImmutableList<JobInfo> getAllJobs() {
    synchronized (jobs) {
      return ImmutableList.copyOf(jobs);
    }
  }

  /** Gets all new added jobs since last poll. */
  public ImmutableList<JobInfo> pollJobs() {
    synchronized (jobs) {
      int size = jobs.size();
      ImmutableList<JobInfo> result = ImmutableList.copyOf(jobs.subList(lastPollJobsIndex, size));
      lastPollJobsIndex = size;
      return result;
    }
  }

  /**
   * Builds and returns a latest {@link SessionDetail}.
   *
   * @param fieldMask a field mask relative to SessionDetail. {@code null} means all fields are
   *     required. It is acceptable that the implementation outputs more fields than the field mask
   *     requires.
   */
  public SessionDetail buildSessionDetail(@Nullable FieldMask fieldMask) {
    Set<Integer> sessionOutputFieldNumbers = getSessionOutputFieldNumbers(fieldMask).orElse(null);
    synchronized (sessionDetailLock) {
      if (sessionOutputFieldNumbers == null || !sessionOutputFieldNumbers.isEmpty()) {
        SessionOutput.Builder sessionOutputBuilder = sessionDetailBuilder.getSessionOutputBuilder();

        sessionOutputBuilder.clearSessionProperty();
        if (sessionOutputFieldNumbers == null
            || sessionOutputFieldNumbers.contains(SessionOutput.SESSION_PROPERTY_FIELD_NUMBER)) {
          sessionOutputBuilder.putAllSessionProperty(sessionProperties);
        }

        sessionOutputBuilder.clearSessionPluginError();
        if (sessionOutputFieldNumbers == null
            || sessionOutputFieldNumbers.contains(
                SessionOutput.SESSION_PLUGIN_ERROR_FIELD_NUMBER)) {
          sessionOutputBuilder.addAllSessionPluginError(sessionPluginErrors);
        }

        sessionOutputBuilder.clearSessionPluginOutput();
        if (sessionOutputFieldNumbers == null
            || sessionOutputFieldNumbers.contains(
                SessionOutput.SESSION_PLUGIN_OUTPUT_FIELD_NUMBER)) {
          sessionOutputBuilder.putAllSessionPluginOutput(sessionPluginOutputs);
        }
      }
      return sessionDetailBuilder.build();
    }
  }

  public String getSessionId() {
    synchronized (sessionDetailLock) {
      return sessionDetailBuilder.getSessionId().getId();
    }
  }

  public SessionConfig getSessionConfig() {
    synchronized (sessionDetailLock) {
      return sessionDetailBuilder.getSessionConfig();
    }
  }

  public Map<String, String> getAllSessionProperties() {
    synchronized (sessionDetailLock) {
      return ImmutableMap.copyOf(sessionProperties);
    }
  }

  public Optional<String> getSessionProperty(String key) {
    synchronized (sessionDetailLock) {
      return Optional.ofNullable(sessionProperties.get(key));
    }
  }

  public Optional<String> putSessionProperty(String key, String value) {
    synchronized (sessionDetailLock) {
      return Optional.ofNullable(sessionProperties.put(key, value));
    }
  }

  public void addSessionPluginError(SessionPluginError sessionPluginError) {
    synchronized (sessionDetailLock) {
      sessionPluginErrors.add(sessionPluginError);
    }
  }

  public void setSessionPluginOutput(
      SessionPluginLabel sessionPluginLabel,
      UnaryOperator<SessionPluginOutput> outputComputingFunction) {
    synchronized (sessionDetailLock) {
      sessionPluginOutputs.compute(
          sessionPluginLabel.getLabel(),
          (key, oldValue) -> outputComputingFunction.apply(oldValue));
    }
  }

  public Optional<SessionPluginOutput> getSessionPluginOutput(
      SessionPluginLabel sessionPluginLabel) {
    synchronized (sessionDetailLock) {
      return Optional.ofNullable(sessionPluginOutputs.get(sessionPluginLabel.getLabel()));
    }
  }

  /**
   * Returns field numbers of required fields in {@link SessionOutput}, based on the given field
   * mask relative to {@link SessionDetail}. An empty optional means all fields are required.
   */
  private static Optional<Set<Integer>> getSessionOutputFieldNumbers(
      @Nullable FieldMask sessionDetailFieldMask) {
    if (sessionDetailFieldMask == null) {
      return Optional.empty();
    }
    Optional<FieldMask> sessionOutputFieldMask =
        FieldMaskUtils.subFieldMask(
            sessionDetailFieldMask,
            SessionDetail.getDescriptor()
                .findFieldByNumber(SessionDetail.SESSION_OUTPUT_FIELD_NUMBER));
    if (sessionOutputFieldMask.isEmpty()) {
      return Optional.empty();
    }

    ImmutableSet.Builder<Integer> result = ImmutableSet.builder();
    Splitter splitter = Splitter.on('.');
    for (String path : sessionOutputFieldMask.get().getPathsList()) {
      splitter
          .splitToStream(path)
          .findFirst()
          .ifPresent(
              sessionOutputFieldName -> {
                FieldDescriptor field =
                    SessionOutput.getDescriptor().findFieldByName(sessionOutputFieldName);
                if (field != null) {
                  result.add(field.getNumber());
                }
              });
    }
    return Optional.of(result.build());
  }
}
