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

package com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.SessionManager;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.AbortSessionsRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.AbortSessionsResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.NotifyAllSessionsRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.NotifyAllSessionsResponse;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionServiceProto.SessionFilter;
import com.google.devtools.mobileharness.infra.client.longrunningservice.util.SessionQueryUtil;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class SessionServiceTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private SessionManager sessionManager;

  private SessionService sessionService;

  @Before
  public void setup() {
    sessionService = new SessionService(sessionManager);
  }

  @Test
  public void getSessionIds() {
    SessionFilter filter =
        SessionFilter.newBuilder().setSessionNameRegex("session_name_regex").build();
    when(sessionManager.getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), eq(null)))
        .thenReturn(createSessionDetails("a", "b", "c", "d", "e", "f"));
    when(sessionManager.getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), eq(filter)))
        .thenReturn(createSessionDetails("b", "c", "e"));

    ImmutableList<String> sessionIds =
        sessionService.getSessionIds(createSessionIds("a", "b", "c"), filter);

    verify(sessionManager, times(2))
        .getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), any());
    assertThat(sessionIds).containsExactly("b", "c");
  }

  @Test
  public void getSessionIds_emptySessionIds() {
    SessionFilter filter =
        SessionFilter.newBuilder().setSessionNameRegex("session_name_regex").build();
    when(sessionManager.getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), eq(null)))
        .thenReturn(createSessionDetails("a", "b", "c", "d", "e"));
    when(sessionManager.getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), eq(filter)))
        .thenReturn(createSessionDetails("a", "b", "c"));

    ImmutableList<String> sessionIds = sessionService.getSessionIds(ImmutableList.of(), filter);

    verify(sessionManager, times(2))
        .getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), any());
    assertThat(sessionIds).containsExactly("a", "b", "c");
  }

  @Test
  public void getSessionIds_emptyFilter() {
    when(sessionManager.getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), eq(null)))
        .thenReturn(createSessionDetails("a", "b", "c", "d", "e"));

    ImmutableList<String> sessionIds =
        sessionService.getSessionIds(createSessionIds("c"), SessionFilter.getDefaultInstance());

    verify(sessionManager).getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), any());
    assertThat(sessionIds).containsExactly("c");
  }

  @Test
  public void getSessionIds_noSessionMatched() {
    SessionFilter filter =
        SessionFilter.newBuilder().setSessionNameRegex("session_name_regex").build();
    when(sessionManager.getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), eq(null)))
        .thenReturn(createSessionDetails("a", "b", "c", "d", "e"));
    when(sessionManager.getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), eq(filter)))
        .thenReturn(ImmutableList.of());

    ImmutableList<String> sessionIds = sessionService.getSessionIds(ImmutableList.of(), filter);

    verify(sessionManager, times(2))
        .getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), any());
    assertThat(sessionIds).isEmpty();
  }

  @Test
  public void doAbortSessions_emptyRequest_success() {
    AbortSessionsResponse response =
        sessionService.doAbortSessions(AbortSessionsRequest.getDefaultInstance());

    verify(sessionManager, never()).abortSessions(any());
    assertThat(response.getSessionIdList()).isEmpty();
  }

  @Test
  public void doNotifiySessions_emptyRequest_success() {
    NotifyAllSessionsResponse response =
        sessionService.doNotifyAllSessions(NotifyAllSessionsRequest.getDefaultInstance());

    verify(sessionManager, never()).notifySessions(any(), any());
    assertThat(response.getSessionIdList()).isEmpty();
  }

  private ImmutableList<SessionId> createSessionIds(String... sessionIds) {
    return Arrays.stream(sessionIds)
        .map(id -> SessionId.newBuilder().setId(id).build())
        .collect(toImmutableList());
  }

  private ImmutableList<SessionDetail> createSessionDetails(String... sessionIds) {
    return Arrays.stream(sessionIds)
        .map(
            id -> SessionDetail.newBuilder().setSessionId(SessionId.newBuilder().setId(id)).build())
        .collect(toImmutableList());
  }
}
