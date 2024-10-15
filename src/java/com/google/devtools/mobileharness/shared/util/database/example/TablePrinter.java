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

package com.google.devtools.mobileharness.shared.util.database.example;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.database.DatabaseConnections;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

/**
 * A binary to print the row count of a table in a MySQL database.
 *
 * <p>Please see the BUILD file for the manual.
 */
public class TablePrinter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static void main(String[] args) throws MobileHarnessException, SQLException {
    String jdbcUrl = args[0];
    logger.atInfo().log("JDBC URL: [%s]", jdbcUrl);

    String tableName = args[1];
    logger.atInfo().log("Table name: [%s]", tableName);

    DatabaseConnections databaseConnections = new DatabaseConnections();
    databaseConnections.initialize(jdbcUrl, new Properties(), /* statementCacheSize= */ 100);

    try (Connection connection = databaseConnections.getConnection();
        ResultSet resultSet =
            connection
                .createStatement()
                .executeQuery(String.format("SELECT COUNT(1) FROM (%s)", tableName))) {
      int count = resultSet.next() ? resultSet.getInt(1) : 0;
      System.out.println(count);
    }
  }

  private TablePrinter() {}
}
