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

package com.google.devtools.mobileharness.infra.client.longrunningservice.util;

import static com.google.devtools.mobileharness.shared.util.message.FieldMaskUtils.createFieldMaskPath;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionStatus;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.GetSessionResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SessionFilter;
import com.google.protobuf.FieldMask;

/** A utility class for common session queries. */
public final class SessionQueryUtil {

  /** GetSessionResponse mask for session status. */
  public static final FieldMask GET_SESSION_STATUS_FIELD_MASK =
      FieldMask.newBuilder()
          .addPaths(
              createFieldMaskPath(
                  GetSessionResponse.getDescriptor()
                      .findFieldByNumber(GetSessionResponse.SESSION_DETAIL_FIELD_NUMBER),
                  SessionDetail.getDescriptor()
                      .findFieldByNumber(SessionDetail.SESSION_STATUS_FIELD_NUMBER)))
          .build();

  /** SessionDetail mask for session id. */
  public static final FieldMask SESSION_ID_FIELD_MASK =
      FieldMask.newBuilder()
          .addPaths(
              createFieldMaskPath(
                  SessionDetail.getDescriptor()
                      .findFieldByNumber(SessionDetail.SESSION_ID_FIELD_NUMBER)))
          .build();

  /** SessionDetail mask for session summary. */
  public static final FieldMask SESSION_SUMMARY_FIELD_MASK =
      FieldMask.newBuilder()
          .addPaths(
              createFieldMaskPath(
                  SessionDetail.getDescriptor()
                      .findFieldByNumber(SessionDetail.SESSION_ID_FIELD_NUMBER)))
          .addPaths(
              createFieldMaskPath(
                  SessionDetail.getDescriptor()
                      .findFieldByNumber(SessionDetail.SESSION_STATUS_FIELD_NUMBER)))
          .addPaths(
              createFieldMaskPath(
                  SessionDetail.getDescriptor()
                      .findFieldByNumber(SessionDetail.SESSION_CONFIG_FIELD_NUMBER),
                  SessionConfig.getDescriptor()
                      .findFieldByNumber(SessionConfig.SESSION_NAME_FIELD_NUMBER)))
          .addPaths(
              createFieldMaskPath(
                  SessionDetail.getDescriptor()
                      .findFieldByNumber(SessionDetail.SESSION_OUTPUT_FIELD_NUMBER),
                  SessionOutput.getDescriptor()
                      .findFieldByNumber(SessionOutput.SESSION_PROPERTY_FIELD_NUMBER)))
          .addPaths(
              createFieldMaskPath(
                  SessionDetail.getDescriptor()
                      .findFieldByNumber(SessionDetail.SESSION_OUTPUT_FIELD_NUMBER),
                  SessionOutput.getDescriptor()
                      .findFieldByNumber(SessionOutput.SESSION_TIMING_INFO_FIELD_NUMBER)))
          .build();

  public static final String UNFINISHED_SESSION_STATUS_NAME_REGEX =
      ImmutableList.of(SessionStatus.SESSION_SUBMITTED, SessionStatus.SESSION_RUNNING).stream()
          .map(SessionStatus::name)
          .collect(joining("|"));
  public static final SessionFilter UNFINISHED_NOT_ABORTED_SESSION_FILTER =
      SessionFilter.newBuilder()
          .setSessionStatusNameRegex(UNFINISHED_SESSION_STATUS_NAME_REGEX)
          .addExcludedSessionPropertyKey(
              SessionProperties.PROPERTY_KEY_SESSION_ABORTED_WHEN_RUNNING)
          .build();

  public static SessionFilter getUnfinishedAndNotAbortedSessionFromClientFilter(String clientId) {
    return SessionFilter.newBuilder()
        .setSessionStatusNameRegex(UNFINISHED_SESSION_STATUS_NAME_REGEX)
        .putIncludedSessionConfigProperty(
            SessionProperties.PROPERTY_KEY_SESSION_CLIENT_ID, clientId)
        .build();
  }

  private SessionQueryUtil() {}
}
