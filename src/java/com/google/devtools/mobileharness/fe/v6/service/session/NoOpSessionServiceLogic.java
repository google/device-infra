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

package com.google.devtools.mobileharness.fe.v6.service.session;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionFileRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionFileResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionLogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionLogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionResponse;

/** No-op implementation of {@link SessionServiceLogic} used when no MOSS backend is available. */
public final class NoOpSessionServiceLogic implements SessionServiceLogic {

  @Override
  public ListenableFuture<GetSessionResponse> getSession(GetSessionRequest request) {
    return immediateFailedFuture(
        new UnsupportedOperationException("SessionService.GetSession is not available."));
  }

  @Override
  public ListenableFuture<GetSessionLogResponse> getSessionLog(GetSessionLogRequest request) {
    return immediateFailedFuture(
        new UnsupportedOperationException("SessionService.GetSessionLog is not available."));
  }

  @Override
  public ListenableFuture<GetSessionFileResponse> getSessionFile(GetSessionFileRequest request) {
    return immediateFailedFuture(
        new UnsupportedOperationException("SessionService.GetSessionFile is not available."));
  }
}
