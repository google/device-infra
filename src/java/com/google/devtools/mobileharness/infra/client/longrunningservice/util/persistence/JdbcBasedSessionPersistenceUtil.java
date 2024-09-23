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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.OlcDatabaseConnections;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPersistenceData;
import com.google.devtools.mobileharness.shared.util.database.DatabaseConnections;
import com.google.devtools.mobileharness.shared.util.database.TableUtil.StringBinaryProtoOrError;
import com.google.devtools.mobileharness.shared.util.database.TableUtil.StringBinaryProtoTable;
import javax.inject.Inject;

/** The {@link SessionPersistenceUtil} implementation based on JDBC. */
public final class JdbcBasedSessionPersistenceUtil implements SessionPersistenceUtil {

  private final StringBinaryProtoTable<SessionPersistenceData> table;

  @Inject
  JdbcBasedSessionPersistenceUtil(
      @OlcDatabaseConnections DatabaseConnections olcDatabaseConnections) {
    this.table =
        new StringBinaryProtoTable<>(
            olcDatabaseConnections,
            SessionPersistenceData.parser(),
            /* tableName= */ "unfinished_sessions",
            /* keyColumnName= */ "session_id",
            /* valueColumnName= */ "session_data");
  }

  @Override
  public void persistSession(SessionPersistenceData sessionPersistenceData)
      throws MobileHarnessException {
    table.update(
        sessionPersistenceData.getSessionDetail().getSessionId().getId(), sessionPersistenceData);
  }

  @Override
  public void removePersistenceData(String sessionId) throws MobileHarnessException {
    table.delete(sessionId);
  }

  @Override
  public ImmutableList<SessionPersistenceDataOrError> getToBeResumedSessions()
      throws MobileHarnessException {
    ImmutableList<StringBinaryProtoOrError> result = table.read();

    return result.stream()
        .map(
            element -> {
              if (element.error().isPresent()) {
                return SessionPersistenceDataOrError.of(/* data= */ null, element.error().get());
              } else {
                return SessionPersistenceDataOrError.of(
                    /* data= */ (SessionPersistenceData) element.value().orElseThrow(),
                    /* error= */ null);
              }
            })
        .collect(toImmutableList());
  }
}
