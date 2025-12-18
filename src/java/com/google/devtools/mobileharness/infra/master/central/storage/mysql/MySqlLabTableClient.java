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

package com.google.devtools.mobileharness.infra.master.central.storage.mysql;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.master.central.proto.Lab.LabServerCondition;
import com.google.devtools.mobileharness.shared.util.base.ProtoExtensionRegistry;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.InvalidProtocolBufferException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Optional;
import javax.inject.Inject;

/** Client for reading/writing lab table in MySQL. */
public class MySqlLabTableClient {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TABLE_NAME = "LabInfo";
  private static final String COL_LAB_ID = "LabId";
  private static final String COL_LAB_SERVER_CONDITION = "LabServerCondition";

  private final Clock clock;

  @Inject
  MySqlLabTableClient(Clock clock) {
    this.clock = clock;
  }

  /** Gets the lab server condition. Empty if the column value is null. */
  public Optional<LabServerCondition> getLabServerCondition(
      LabLocator labLocator, Connection connection) throws MobileHarnessException {
    String sql =
        String.format(
            "SELECT %s FROM %s WHERE %s = ?", COL_LAB_SERVER_CONDITION, TABLE_NAME, COL_LAB_ID);
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
      preparedStatement.setString(1, labLocator.getId());
      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        if (resultSet.next()) {
          byte[] conditionBytes = resultSet.getBytes(COL_LAB_SERVER_CONDITION);
          if (conditionBytes == null) {
            return Optional.empty();
          }
          return Optional.of(
              LabServerCondition.parseFrom(
                  conditionBytes, ProtoExtensionRegistry.getGeneratedRegistry()));
        } else {
          return Optional.empty();
        }
      }
    } catch (SQLException | InvalidProtocolBufferException e) {
      throw new MobileHarnessException(
          BasicErrorId.DATABASE_TABLE_READ_ERROR,
          "Failed to get LabServerCondition for " + labLocator.getId(),
          e);
    }
  }

  /** Updates the lab server condition of an existing lab. */
  @CanIgnoreReturnValue
  public MySqlLabTableClient updateLabServerCondition(
      LabLocator labLocator, boolean isMissing, Connection connection)
      throws MobileHarnessException {
    LabServerCondition newCondition =
        LabServerCondition.newBuilder()
            .setTimestampMs(clock.millis())
            .setIsMissing(isMissing)
            .build();

    String sql =
        String.format(
            "UPDATE %s SET %s = ? WHERE %s = ?", TABLE_NAME, COL_LAB_SERVER_CONDITION, COL_LAB_ID);
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
      preparedStatement.setBytes(1, newCondition.toByteArray());
      preparedStatement.setString(2, labLocator.getId());
      int rowsAffected = preparedStatement.executeUpdate();
      if (rowsAffected == 0) {
        logger.atWarning().log(
            "LabServerCondition not updated, LabId %s not found.", labLocator.getId());
      }
    } catch (SQLException e) {
      throw new MobileHarnessException(
          BasicErrorId.DATABASE_TABLE_UPDATE_ERROR,
          "Failed to update LabServerCondition for " + labLocator.getId(),
          e);
    }
    return this;
  }
}
