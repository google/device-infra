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

package com.google.devtools.mobileharness.shared.util.database;

import static com.google.common.base.Preconditions.checkState;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.mchange.v2.c3p0.DataSources;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import javax.annotation.concurrent.GuardedBy;
import javax.sql.DataSource;

/** Database connection pool. */
public class DatabaseConnections {

  private final Object lock = new Object();

  @GuardedBy("lock")
  private DataSource dataSource;

  @GuardedBy("lock")
  private String jdbcUrl;

  public void initialize(String jdbcUrl, Properties properties, int statementCacheSize)
      throws MobileHarnessException {
    synchronized (lock) {
      checkState(
          dataSource == null,
          "DatabaseConnections has been initialized, data_source=%s",
          dataSource);
      try {
        this.dataSource =
            DataSources.pooledDataSource(
                DataSources.unpooledDataSource(jdbcUrl, properties), statementCacheSize);
        this.jdbcUrl = jdbcUrl;
      } catch (SQLException e) {
        throw new MobileHarnessException(
            BasicErrorId.DATABASE_CONNECTIONS_INITIALIZE_ERROR,
            String.format("Failed to initialize database connections, jdbc_url=[%s]", jdbcUrl),
            e);
      }
    }
  }

  public Connection getConnection() throws MobileHarnessException {
    synchronized (lock) {
      checkState(dataSource != null, "DatabaseConnections has not been initialized");
      try {
        return dataSource.getConnection();
      } catch (SQLException e) {
        throw new MobileHarnessException(
            BasicErrorId.DATABASE_CONNECTIONS_GET_CONNECTION_ERROR,
            String.format("Failed to get connection, jdbc_url=[%s]", jdbcUrl),
            e);
      }
    }
  }
}
