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
  public void doAbortSessions_success() {
    SessionFilter filter =
        SessionFilter.newBuilder().setSessionNameRegex("session_name_regex").build();
    when(sessionManager.getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), eq(null)))
        .thenReturn(createSessionDetails("a", "b", "c", "d", "e", "f"));
    when(sessionManager.getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), eq(filter)))
        .thenReturn(createSessionDetails("b", "c", "e"));

    AbortSessionsResponse response =
        sessionService.doAbortSessions(
            AbortSessionsRequest.newBuilder()
                .addAllSessionId(createSessionIds("a", "b", "c"))
                .setSessionFilter(filter)
                .build());

    verify(sessionManager, times(2))
        .getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), any());
    verify(sessionManager).abortSessions(eq(ImmutableList.of("b", "c")));
    assertThat(response.getSessionIdList()).isEqualTo(createSessionIds("b", "c"));
  }

  @Test
  public void doAbortSessions_emptyRequest_success() {
    AbortSessionsResponse response =
        sessionService.doAbortSessions(AbortSessionsRequest.getDefaultInstance());

    verify(sessionManager, never()).abortSessions(any());
    assertThat(response.getSessionIdList()).isEmpty();
  }

  @Test
  public void doAbortSessions_emptySessionIds_success() {
    SessionFilter filter =
        SessionFilter.newBuilder().setSessionNameRegex("session_name_regex").build();
    when(sessionManager.getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), eq(null)))
        .thenReturn(createSessionDetails("a", "b", "c", "d", "e"));
    when(sessionManager.getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), eq(filter)))
        .thenReturn(createSessionDetails("a", "b", "c"));

    AbortSessionsResponse response =
        sessionService.doAbortSessions(
            AbortSessionsRequest.newBuilder().setSessionFilter(filter).build());

    verify(sessionManager, times(2))
        .getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), any());
    verify(sessionManager).abortSessions(eq(ImmutableList.of("a", "b", "c")));
    assertThat(response.getSessionIdList()).isEqualTo(createSessionIds("a", "b", "c"));
  }

  @Test
  public void doAbortSessions_emptyFilter_success() {
    when(sessionManager.getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), eq(null)))
        .thenReturn(createSessionDetails("a", "b", "c", "d", "e"));

    AbortSessionsResponse response =
        sessionService.doAbortSessions(
            AbortSessionsRequest.newBuilder().addAllSessionId(createSessionIds("c")).build());

    verify(sessionManager).getAllSessions(eq(SessionQueryUtil.SESSION_ID_FIELD_MASK), any());
    verify(sessionManager).abortSessions(eq(ImmutableList.of("c")));
    assertThat(response.getSessionIdList()).isEqualTo(createSessionIds("c"));
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
