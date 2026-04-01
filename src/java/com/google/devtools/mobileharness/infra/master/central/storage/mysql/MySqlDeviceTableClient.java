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

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.inject.Inject;

/** Client for reading/writing device table in MySQL. */
public class MySqlDeviceTableClient {
  private static final String TABLE_NAME = "DeviceInfo";
  private static final String COL_LAB_ID = "LabId";

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
}
