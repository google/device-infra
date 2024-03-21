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

package com.google.devtools.mobileharness.infra.ats.dda.stub;

import com.google.auto.value.AutoValue;
import com.google.devtools.common.metrics.stability.converter.DeserializedException;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceRequirement;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.infra.ats.dda.proto.SessionPluginProto.AtsDdaSessionNotification;
import com.google.devtools.mobileharness.infra.ats.dda.proto.SessionPluginProto.AtsDdaSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.dda.proto.SessionPluginProto.AtsDdaSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.dda.proto.SessionPluginProto.CancelSession;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionNotification;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginConfigs;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLabel;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLoadingConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionStatus;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.CreateSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.NotifySessionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.NotifySessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.SessionStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionErrorUtil;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import io.grpc.Channel;
import java.util.Optional;
import javax.annotation.Nullable;

/** Stub for creating ATS DDA sessions. */
public class AtsDdaStub {

  /** Information of a session. */
  @AutoValue
  public abstract static class SessionInfo {

    public abstract String sessionId();

    public abstract SessionStatus sessionStatus();

    /** Becomes present when a device is allocated. */
    public abstract Optional<DeviceInfo> allocatedDevice();

    public abstract Optional<Throwable> sessionError();

    public static SessionInfo of(
        String sessionId,
        SessionStatus sessionStatus,
        @Nullable DeviceInfo allocatedDevice,
        @Nullable Throwable sessionError) {
      return new AutoValue_AtsDdaStub_SessionInfo(
          sessionId,
          sessionStatus,
          Optional.ofNullable(allocatedDevice),
          Optional.ofNullable(sessionError));
    }
  }

  private static final String SESSION_PLUGIN_LABEL = "AtsDdaSessionPlugin";
  private static final String SESSION_PLUGIN_CLASS_NAME =
      "com.google.devtools.mobileharness.infra.ats.dda.sessionplugin.AtsDdaSessionPlugin";

  private final SessionStub sessionStub;

  public AtsDdaStub(Channel olcServerChannel) {
    this.sessionStub = new SessionStub(olcServerChannel);
  }

  /** Creates a session and returns the session ID. */
  public String createSession(String sessionName, String model, int version)
      throws GrpcExceptionWithErrorId {
    AtsDdaSessionPluginConfig pluginConfig =
        AtsDdaSessionPluginConfig.newBuilder()
            .setDeviceRequirement(
                DeviceRequirement.newBuilder()
                    .setDeviceType("AndroidRealDevice")
                    .putDimensions(Name.MODEL.lowerCaseName(), model)
                    .putDimensions(Name.SDK_VERSION.lowerCaseName(), Integer.toString(version)))
            .build();
    CreateSessionResponse createSessionResponse =
        sessionStub.createSession(
            CreateSessionRequest.newBuilder()
                .setSessionConfig(
                    SessionConfig.newBuilder()
                        .setSessionName(sessionName)
                        .setSessionPluginConfigs(
                            SessionPluginConfigs.newBuilder()
                                .addSessionPluginConfig(
                                    SessionPluginConfig.newBuilder()
                                        .setExplicitLabel(
                                            SessionPluginLabel.newBuilder()
                                                .setLabel(SESSION_PLUGIN_LABEL))
                                        .setLoadingConfig(
                                            SessionPluginLoadingConfig.newBuilder()
                                                .setPluginClassName(SESSION_PLUGIN_CLASS_NAME))
                                        .setExecutionConfig(
                                            SessionPluginExecutionConfig.newBuilder()
                                                .setConfig(Any.pack(pluginConfig))))))
                .build());
    return createSessionResponse.getSessionId().getId();
  }

  /**
   * Gets information of a session.
   *
   * @throws GrpcExceptionWithErrorId if the session is not found or network error
   */
  public SessionInfo getSession(String sessionId) throws GrpcExceptionWithErrorId {
    GetSessionResponse getSessionResponse =
        sessionStub.getSession(
            GetSessionRequest.newBuilder()
                .setSessionId(SessionId.newBuilder().setId(sessionId))
                .build());
    SessionDetail sessionDetail = getSessionResponse.getSessionDetail();
    Optional<DeviceInfo> deviceInfo =
        getPluginOutput(sessionDetail).map(AtsDdaSessionPluginOutput::getAllocatedDevice);
    Optional<DeserializedException> sessionError =
        SessionErrorUtil.getSessionError(sessionDetail, SESSION_PLUGIN_LABEL);
    return SessionInfo.of(
        sessionId,
        sessionDetail.getSessionStatus(),
        deviceInfo.orElse(null),
        sessionError.orElse(null));
  }

  /**
   * Cancels a session.
   *
   * @return true if a pending/running session is found and cancelled, false if no session is found
   *     or the session has finished
   * @throws GrpcExceptionWithErrorId if there is network error
   */
  public boolean cancelSession(String sessionId) throws GrpcExceptionWithErrorId {
    NotifySessionResponse notifySessionResponse =
        sessionStub.notifySession(
            NotifySessionRequest.newBuilder()
                .setSessionId(SessionId.newBuilder().setId(sessionId))
                .setSessionNotification(
                    SessionNotification.newBuilder()
                        .setPluginLabel(
                            SessionPluginLabel.newBuilder().setLabel(SESSION_PLUGIN_LABEL))
                        .setNotification(
                            Any.pack(
                                AtsDdaSessionNotification.newBuilder()
                                    .setCancelSession(CancelSession.getDefaultInstance())
                                    .build())))
                .build());
    return notifySessionResponse.getSuccessful();
  }

  private static Optional<AtsDdaSessionPluginOutput> getPluginOutput(SessionDetail sessionDetail) {
    SessionPluginOutput pluginOutput =
        sessionDetail
            .getSessionOutput()
            .getSessionPluginOutputMap()
            .getOrDefault(SESSION_PLUGIN_LABEL, SessionPluginOutput.getDefaultInstance());
    if (!pluginOutput.hasOutput()) {
      return Optional.empty();
    }
    try {
      return Optional.of(pluginOutput.getOutput().unpack(AtsDdaSessionPluginOutput.class));
    } catch (InvalidProtocolBufferException e) {
      throw new AssertionError(e);
    }
  }
}
