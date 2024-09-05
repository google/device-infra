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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPersistenceData;

/** Util to persist/resume session. */
public interface SessionPersistenceUtil {

  /** Persists the session data. */
  void persistSession(SessionPersistenceData sessionPersistenceData);

  /** Removes the persisted session data. */
  void removePersistenceData(String sessionId);

  /** Gets the session list that should be resumed and its persistence data. */
  ImmutableList<SessionPersistenceData> getToBeResumedSessions();
}
