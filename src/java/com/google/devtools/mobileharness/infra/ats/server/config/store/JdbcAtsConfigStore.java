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

package com.google.devtools.mobileharness.infra.ats.server.config.store;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.OlcDatabaseConnections;
import com.google.devtools.mobileharness.shared.util.database.DatabaseConnections;
import com.google.protobuf.ByteString;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link AtsConfigStore} backed by the {@code ats_config_entity} table of the OLC database.
 *
 * <p>Creates the table via {@code CREATE TABLE IF NOT EXISTS} on first use; if the table already
 * exists, it is reused as-is.
 */
@Singleton
public class JdbcAtsConfigStore implements AtsConfigStore {

  @VisibleForTesting
  static final String CREATE_TABLE_SQL =
      "CREATE TABLE IF NOT EXISTS ats_config_entity ("
          + "kind VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, "
          + "id VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL, "
          + "data MEDIUMBLOB NOT NULL, "
          + "update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) "
          + "ON UPDATE CURRENT_TIMESTAMP(3), "
          + "PRIMARY KEY (kind, id))";

  @VisibleForTesting
  static final String GET_SQL = "SELECT data FROM ats_config_entity WHERE kind = ? AND id = ?";

  @VisibleForTesting
  static final String PUT_SQL =
      "INSERT INTO ats_config_entity (kind, id, data) VALUES (?, ?, ?) "
          + "ON DUPLICATE KEY UPDATE data = VALUES(data)";

  @VisibleForTesting
  static final String DELETE_SQL = "DELETE FROM ats_config_entity WHERE kind = ? AND id = ?";

  private final DatabaseConnections databaseConnections;

  @GuardedBy("this")
  private boolean tableCreated;

  @Inject
  JdbcAtsConfigStore(@OlcDatabaseConnections DatabaseConnections databaseConnections) {
    this.databaseConnections = databaseConnections;
  }

  @Override
  public Optional<ByteString> get(String kind, String id) throws MobileHarnessException {
    createTableIfNeeded();
    try (Connection connection = databaseConnections.getConnection();
        PreparedStatement statement = connection.prepareStatement(GET_SQL)) {
      statement.setString(1, kind);
      statement.setString(2, id);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next()
            ? Optional.of(ByteString.copyFrom(resultSet.getBytes(1)))
            : Optional.empty();
      }
    } catch (SQLException e) {
      throw new MobileHarnessException(
          BasicErrorId.DATABASE_TABLE_READ_ERROR,
          String.format("Failed to read ATS config entity, kind=[%s], id=[%s]", kind, id),
          e);
    }
  }

  @Override
  public void put(String kind, String id, ByteString data) throws MobileHarnessException {
    createTableIfNeeded();
    try (Connection connection = databaseConnections.getConnection();
        PreparedStatement statement = connection.prepareStatement(PUT_SQL)) {
      statement.setString(1, kind);
      statement.setString(2, id);
      statement.setBytes(3, data.toByteArray());
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new MobileHarnessException(
          BasicErrorId.DATABASE_TABLE_UPDATE_ERROR,
          String.format("Failed to update ATS config entity, kind=[%s], id=[%s]", kind, id),
          e);
    }
  }

  @Override
  public void delete(String kind, String id) throws MobileHarnessException {
    createTableIfNeeded();
    try (Connection connection = databaseConnections.getConnection();
        PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
      statement.setString(1, kind);
      statement.setString(2, id);
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new MobileHarnessException(
          BasicErrorId.DATABASE_TABLE_DELETE_ERROR,
          String.format("Failed to delete ATS config entity, kind=[%s], id=[%s]", kind, id),
          e);
    }
  }

  /**
   * Ensures the table exists before the first operation in this process.
   *
   * <p>Runs on first use rather than in the constructor because the database connections are
   * initialized after the store is created.
   */
  private synchronized void createTableIfNeeded() throws MobileHarnessException {
    if (tableCreated) {
      return;
    }
    try (Connection connection = databaseConnections.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(CREATE_TABLE_SQL);
    } catch (SQLException e) {
      throw new MobileHarnessException(
          BasicErrorId.DATABASE_TABLE_CREATE_ERROR,
          "Failed to create database table [ats_config_entity]",
          e);
    }
    tableCreated = true;
  }
}
