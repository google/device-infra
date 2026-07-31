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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionFileRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionFileResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionLogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionLogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.session.GetSessionResponse;

/** Logic interface for the Session Detail service. */
public interface SessionServiceLogic {

  /** Gets the full session detail (overview, config, jobs). */
  ListenableFuture<GetSessionResponse> getSession(GetSessionRequest request);

  /** Gets the whole session log. */
  ListenableFuture<GetSessionLogResponse> getSessionLog(GetSessionLogRequest request);

  /** Gets the content of a session file. */
  ListenableFuture<GetSessionFileResponse> getSessionFile(GetSessionFileRequest request);
}
