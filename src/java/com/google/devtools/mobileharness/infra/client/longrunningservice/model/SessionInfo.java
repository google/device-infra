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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLabel;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginOutput;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Information of a running session for a session plugin. */
public class SessionInfo {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SessionDetailHolder sessionDetailHolder;
  private final SessionPluginLabel sessionPluginLabel;
  private final SessionPluginExecutionConfig sessionPluginExecutionConfig;

  public SessionInfo(
      SessionDetailHolder sessionDetailHolder,
      SessionPluginLabel sessionPluginLabel,
      SessionPluginExecutionConfig sessionPluginExecutionConfig) {
    this.sessionDetailHolder = sessionDetailHolder;
    this.sessionPluginLabel = sessionPluginLabel;
    this.sessionPluginExecutionConfig = sessionPluginExecutionConfig;
  }

  public String getSessionId() {
    return sessionDetailHolder.getSessionId();
  }

  public Map<String, String> getAllSessionProperties() {
    return sessionDetailHolder.getAllSessionProperties();
  }

  public Optional<String> getSessionProperty(String key) {
    return sessionDetailHolder.getSessionProperty(key);
  }

  @CanIgnoreReturnValue
  public Optional<String> putSessionProperty(String key, String value) {
    return sessionDetailHolder.putSessionProperty(key, value);
  }

  /**
   * Adds a job to the session.
   *
   * <p>Note that the job will be started only if this method is called in {@code
   * SessionStartingEvent}, or this method is called when at least another job of the session is
   * running.
   */
  public void addJob(JobInfo jobInfo) {
    sessionDetailHolder.addJob(jobInfo);
  }

  public List<JobInfo> getAllJobs() {
    return sessionDetailHolder.getAllJobs();
  }

  /**
   * Sets the output of the session plugin in {@link SessionPluginOutput#getOutput()} of {@link
   * SessionOutput#getSessionPluginOutputMap()} whose key is {@link SessionPluginLabel}.
   *
   * @param outputComputingFunction the session plugin output computing function, whose parameter is
   *     the old output (if any) or null (if no old output or the type of the old output is not
   *     {@code clazz}), and whose return value is the new output (or null to remove the old output)
   */
  public <T extends Message> void setSessionPluginOutput(
      UnaryOperator<T> outputComputingFunction, Class<T> clazz) {
    sessionDetailHolder.setSessionPluginOutput(
        sessionPluginLabel,
        oldOutput -> {
          // Unpacks the old output if any.
          T oldPluginOutput;
          if (oldOutput == null || !oldOutput.hasOutput()) {
            oldPluginOutput = null;
          } else {
            try {
              oldPluginOutput = oldOutput.getOutput().unpack(clazz);
            } catch (InvalidProtocolBufferException e) {
              logger.atWarning().withCause(e).log(
                  "Failed to parse session plugin output old value, plugin_label=[%s],"
                      + " target_class=[%s]",
                  sessionPluginLabel, clazz);
              oldPluginOutput = null;
            }
          }

          // Calls the given computing function.
          T newPluginOutput = outputComputingFunction.apply(oldPluginOutput);

          // Packs the new output.
          SessionPluginOutput newOutput;
          if (newPluginOutput == null) {
            newOutput = null;
          } else {
            newOutput =
                SessionPluginOutput.newBuilder().setOutput(Any.pack(newPluginOutput)).build();
          }
          return newOutput;
        });
  }

  /**
   * Gets the output of the session plugin (if any) in {@link SessionPluginOutput#getOutput()} of
   * {@link SessionOutput#getSessionPluginOutputMap()} whose key is {@link SessionPluginLabel}.
   *
   * <p>If the type of the output is not {@code clazz}, returns empty.
   */
  public <T extends Message> Optional<T> getSessionPluginOutput(Class<T> clazz) {
    return sessionDetailHolder
        .getSessionPluginOutput(sessionPluginLabel)
        .flatMap(
            output -> {
              if (!output.hasOutput()) {
                return Optional.empty();
              }
              try {
                return Optional.of(output.getOutput().unpack(clazz));
              } catch (InvalidProtocolBufferException e) {
                logger.atWarning().withCause(e).log(
                    "Failed to parse session plugin output value, plugin_label=[%s],"
                        + " target_class=[%s]",
                    sessionPluginLabel, clazz);
                return Optional.empty();
              }
            });
  }

  public SessionPluginLabel getSessionPluginLabel() {
    return sessionPluginLabel;
  }

  public SessionPluginExecutionConfig getSessionPluginExecutionConfig() {
    return sessionPluginExecutionConfig;
  }
}
