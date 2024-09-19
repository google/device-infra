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

package com.google.devtools.mobileharness.infra.client.longrunningservice.util.persistence;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPersistenceData;
import java.util.Optional;
import javax.annotation.Nullable;

/** Util to persist/resume session. */
public interface SessionPersistenceUtil {

  /** Persists the session data. */
  void persistSession(SessionPersistenceData sessionPersistenceData) throws MobileHarnessException;

  /** Removes the persisted session data. */
  void removePersistenceData(String sessionId) throws MobileHarnessException;

  /** Gets the session list that should be resumed and its persistence data. */
  ImmutableList<SessionPersistenceDataOrError> getToBeResumedSessions()
      throws MobileHarnessException;

  /** Data or error. One and only one is present. */
  @AutoValue
  abstract class SessionPersistenceDataOrError {

    public abstract Optional<SessionPersistenceData> data();

    public abstract Optional<Throwable> error();

    public static SessionPersistenceDataOrError of(
        @Nullable SessionPersistenceData data, @Nullable Throwable error) {
      checkArgument((data != null && error == null) || (data == null && error != null));
      return new AutoValue_SessionPersistenceUtil_SessionPersistenceDataOrError(
          Optional.ofNullable(data), Optional.ofNullable(error));
    }
  }

  /** A no-op implementation. */
  final class NoOpSessionPersistenceUtil implements SessionPersistenceUtil {

    @Override
    public void persistSession(SessionPersistenceData sessionPersistenceData) {}

    @Override
    public void removePersistenceData(String sessionId) {}

    @Override
    public ImmutableList<SessionPersistenceDataOrError> getToBeResumedSessions() {
      return ImmutableList.of();
    }
  }
}
