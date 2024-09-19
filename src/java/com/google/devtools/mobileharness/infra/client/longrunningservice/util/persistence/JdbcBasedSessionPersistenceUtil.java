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
import static com.google.common.base.Preconditions.checkState;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.OlcDatabaseConnections;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPersistenceData;
import com.google.devtools.mobileharness.shared.util.base.ProtoExtensionRegistry;
import com.google.devtools.mobileharness.shared.util.database.DatabaseConnections;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.inject.Inject;

/** The {@link SessionPersistenceUtil} implementation based on JDBC. */
public final class JdbcBasedSessionPersistenceUtil implements SessionPersistenceUtil {

  private final DatabaseConnections olcDatabaseConnections;

  @Inject
  JdbcBasedSessionPersistenceUtil(
      @OlcDatabaseConnections DatabaseConnections olcDatabaseConnections) {
    this.olcDatabaseConnections = olcDatabaseConnections;
  }

  @Override
  public void persistSession(SessionPersistenceData sessionPersistenceData)
      throws MobileHarnessException {
    checkArgument(
        !sessionPersistenceData.getSessionDetail().getSessionId().getId().isEmpty(),
        "Invalid session data: [%s]",
        shortDebugString(sessionPersistenceData));

    // Creates prepared statement.
    try (Connection connection = olcDatabaseConnections.getConnection();
        PreparedStatement preparedStatement =
            connection.prepareStatement(
                "INSERT INTO unfinished_sessions (session_id, session_data) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE session_data = VALUES(session_data)")) {

      // Sets parameters.
      preparedStatement.setString(
          1, sessionPersistenceData.getSessionDetail().getSessionId().getId());
      preparedStatement.setBytes(2, sessionPersistenceData.toByteArray());

      // Executes update.
      preparedStatement.executeUpdate();
    } catch (MobileHarnessException | SQLException e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_SESSION_DATABASE_ERROR,
          String.format(
              "Failed to persist session to database, session=[%s]",
              shortDebugString(sessionPersistenceData)),
          e);
    }
  }

  @Override
  public void removePersistenceData(String sessionId) throws MobileHarnessException {
    try (Connection connection = olcDatabaseConnections.getConnection();
        PreparedStatement preparedStatement =
            connection.prepareStatement("DELETE FROM unfinished_sessions WHERE session_id = ?")) {
      preparedStatement.setString(1, sessionId);
      preparedStatement.executeUpdate();
    } catch (MobileHarnessException | SQLException e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_SESSION_DATABASE_ERROR,
          String.format(
              "Failed to remove persistence data from database, session_id=[%s]", sessionId),
          e);
    }
  }

  @Override
  public ImmutableList<SessionPersistenceDataOrError> getToBeResumedSessions()
      throws MobileHarnessException {
    // Queries from table.
    try (Connection connection = olcDatabaseConnections.getConnection();
        ResultSet resultSet =
            connection
                .prepareStatement("SELECT session_id, session_data FROM unfinished_sessions")
                .executeQuery()) {
      ImmutableList.Builder<SessionPersistenceDataOrError> result = ImmutableList.builder();
      while (resultSet.next()) {
        try {
          // Reads a row.
          String sessionId = resultSet.getString("session_id");
          InputStream sessionDataStream = resultSet.getBinaryStream("session_data");

          // Parses and validates proto.
          SessionPersistenceData sessionPersistenceData =
              SessionPersistenceData.parseFrom(
                  sessionDataStream, ProtoExtensionRegistry.getGeneratedRegistry());
          checkState(
              sessionId.equals(sessionPersistenceData.getSessionDetail().getSessionId().getId()),
              "session_id and session_data are mismatched, session_id=[%s], session_data=[%s]",
              sessionId,
              shortDebugString(sessionPersistenceData));

          // Adds to result.
          result.add(SessionPersistenceDataOrError.of(sessionPersistenceData, /* error= */ null));
        } catch (SQLException | IOException | IllegalStateException e) {
          result.add(SessionPersistenceDataOrError.of(/* data= */ null, e));
        }
      }
      return result.build();
    } catch (MobileHarnessException | SQLException e) {
      throw new MobileHarnessException(
          InfraErrorId.OLCS_SESSION_DATABASE_ERROR,
          "Failed to get sessions to resume from database",
          e);
    }
  }
}
