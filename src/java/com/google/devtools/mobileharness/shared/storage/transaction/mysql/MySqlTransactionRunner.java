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

package com.google.devtools.mobileharness.shared.storage.transaction.mysql;

import com.google.common.base.Stopwatch;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.storage.Annotations;
import com.google.devtools.mobileharness.shared.storage.transaction.TransactionConfig;
import com.google.devtools.mobileharness.shared.storage.transaction.TransactionRunner;
import com.google.devtools.mobileharness.shared.storage.transaction.TransactionWorker;
import com.google.devtools.mobileharness.shared.util.database.DatabaseConnections;
import java.sql.Connection;
import java.sql.SQLException;
import javax.inject.Inject;

/** MySQL implementation of {@link TransactionRunner} for committing MySQL transactions. */
public final class MySqlTransactionRunner implements TransactionRunner {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DatabaseConnections databaseConnections;

  @Inject
  MySqlTransactionRunner(@Annotations.DatabaseConnections DatabaseConnections databaseConnections) {
    this.databaseConnections = databaseConnections;
  }

  @Override
  public <R> R run(TransactionConfig config, TransactionWorker<R> worker)
      throws MobileHarnessException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    Connection connection = null;
    try {
      connection = databaseConnections.getConnection();
      // Disable auto-commit to manually control transaction boundaries.
      connection.setAutoCommit(false);
      MySqlTransactionContext context = new MySqlTransactionContext(connection);
      R result = worker.doWork(context);
      connection.commit();
      logger.atInfo().log("Successfully %s (elapsed=%s)", config.description(), stopwatch.stop());
      return result;
    } catch (SQLException | MobileHarnessException e) {
      if (connection != null) {
        try {
          connection.rollback();
        } catch (SQLException sqlException) {
          e.addSuppressed(sqlException);
        }
      }
      throw new MobileHarnessException(
          config.errorId(),
          String.format("Failed to %s (elapsed=%s)", config.description(), stopwatch.stop()),
          e);
    } finally {
      if (connection != null) {
        try {
          // Reset auto-commit to true to return the connection to the pool in a clean state.
          connection.setAutoCommit(true);
        } catch (SQLException e) {
          logger.atWarning().withCause(e).log("Failed to reset autoCommit to true");
        }
        try {
          // Close the connection to return it to the pool.
          connection.close();
        } catch (SQLException e) {
          logger.atWarning().withCause(e).log("Failed to close connection");
        }
      }
    }
  }
}
