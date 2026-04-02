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
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.master.central.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.shared.util.base.ProtoExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.inject.Inject;

/** Client for reading/writing device table in MySQL. */
public class MySqlDeviceTableClient {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TABLE_NAME = "DeviceInfo";
  private static final String COL_LAB_ID = "LabId";
  private static final String COL_DEVICE_ID = "DeviceId";
  private static final String COL_DEVICE_CONDITION = "DeviceCondition";

  @Inject
  MySqlDeviceTableClient() {}

  /** Checks whether there is any device in the lab. */
  public boolean hasDevice(LabLocator labLocator, Connection connection)
      throws MobileHarnessException {
    String sql = String.format("SELECT 1 FROM %s WHERE %s = ? LIMIT 1", TABLE_NAME, COL_LAB_ID);
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
      preparedStatement.setString(1, labLocator.getId());
      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        return resultSet.next();
      }
    } catch (SQLException e) {
      throw new MobileHarnessException(
          BasicErrorId.DATABASE_TABLE_READ_ERROR,
          "Failed to check devices for lab " + labLocator.getId(),
          e);
    }
  }

  /** Gets the condition of a device. Empty if the device or column value is null. */
  public Optional<DeviceCondition> getDeviceCondition(
      DeviceLocator deviceLocator, Connection connection) throws MobileHarnessException {
    String sql =
        String.format(
            "SELECT %s FROM %s WHERE %s = ? AND %s = ?",
            COL_DEVICE_CONDITION, TABLE_NAME, COL_LAB_ID, COL_DEVICE_ID);
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
      preparedStatement.setString(1, deviceLocator.labLocator().getId());
      preparedStatement.setString(2, deviceLocator.id());
      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        if (resultSet.next()) {
          byte[] conditionBytes = resultSet.getBytes(COL_DEVICE_CONDITION);
          if (conditionBytes == null) {
            return Optional.empty();
          }
          return Optional.of(
              DeviceCondition.parseFrom(
                  conditionBytes, ProtoExtensionRegistry.getGeneratedRegistry()));
        } else {
          return Optional.empty();
        }
      }
    } catch (SQLException | InvalidProtocolBufferException e) {
      throw new MobileHarnessException(
          BasicErrorId.DATABASE_TABLE_READ_ERROR,
          "Failed to get DeviceCondition for " + deviceLocator,
          e);
    }
  }

  /** Updates the condition of a device. */
  public void updateDeviceCondition(
      DeviceLocator deviceLocator, DeviceCondition condition, Connection connection)
      throws MobileHarnessException {
    String sql =
        String.format(
            "UPDATE %s SET %s = ? WHERE %s = ? AND %s = ?",
            TABLE_NAME, COL_DEVICE_CONDITION, COL_LAB_ID, COL_DEVICE_ID);
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
      preparedStatement.setBytes(1, condition.toByteArray());
      preparedStatement.setString(2, deviceLocator.labLocator().getId());
      preparedStatement.setString(3, deviceLocator.id());
      int rowsAffected = preparedStatement.executeUpdate();
      if (rowsAffected == 0) {
        logger.atWarning().log(
            "DeviceCondition not updated, DeviceId %s not found.", deviceLocator.id());
      }
    } catch (SQLException e) {
      throw new MobileHarnessException(
          BasicErrorId.DATABASE_TABLE_UPDATE_ERROR,
          "Failed to update DeviceCondition for " + deviceLocator,
          e);
    }
  }
}
