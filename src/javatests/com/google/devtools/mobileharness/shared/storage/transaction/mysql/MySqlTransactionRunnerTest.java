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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.storage.transaction.TransactionConfig;
import com.google.devtools.mobileharness.shared.storage.transaction.TransactionWorker;
import com.google.devtools.mobileharness.shared.util.database.DatabaseConnections;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class MySqlTransactionRunnerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private DatabaseConnections databaseConnections;
  @Mock private Connection connection;
  @Mock private TransactionWorker<String> worker;

  private static final ErrorId ERROR_ID = BasicErrorId.DATABASE_TABLE_UPDATE_ERROR;
  private static final TransactionConfig TRANSACTION_CONFIG =
      TransactionConfig.newBuilder()
          .setErrorId(ERROR_ID)
          .setDescription("test description")
          .build();

  private MySqlTransactionRunner transactionRunner;

  @Before
  public void setUp() throws Exception {
    when(databaseConnections.getConnection()).thenReturn(connection);
    transactionRunner = new MySqlTransactionRunner(databaseConnections);
  }

  @Test
  public void run_successfulTransaction() throws Exception {
    when(worker.doWork(any())).thenReturn("success");

    String result = transactionRunner.run(TRANSACTION_CONFIG, worker);

    assertThat(result).isEqualTo("success");
    InOrder inOrder = inOrder(connection, worker);
    inOrder.verify(connection).setAutoCommit(false);
    inOrder.verify(worker).doWork(any(MySqlTransactionContext.class));
    inOrder.verify(connection).commit();
    inOrder.verify(connection).setAutoCommit(true);
    inOrder.verify(connection).close();
  }

  @Test
  public void run_workerThrowsMobileHarnessException_rollsBack() throws Exception {
    MobileHarnessException exception =
        new MobileHarnessException(BasicErrorId.DATABASE_TABLE_READ_ERROR, "Worker failed");
    when(worker.doWork(any())).thenThrow(exception);

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> transactionRunner.run(TRANSACTION_CONFIG, worker));

    assertThat(thrown.getErrorId()).isEqualTo(ERROR_ID);
    assertThat(thrown).hasCauseThat().isEqualTo(exception);
    InOrder inOrder = inOrder(connection, worker);
    inOrder.verify(connection).setAutoCommit(false);
    inOrder.verify(worker).doWork(any(MySqlTransactionContext.class));
    inOrder.verify(connection).rollback();
    inOrder.verify(connection).setAutoCommit(true);
    inOrder.verify(connection).close();
  }

  @Test
  public void run_workerThrowsSQLException_rollsBack() throws Exception {
    SQLException causeException = new SQLException("Worker failed");
    when(worker.doWork(any()))
        .thenThrow(
            new MobileHarnessException(
                BasicErrorId.DATABASE_TABLE_READ_ERROR, "Worker failed", causeException));
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> transactionRunner.run(TRANSACTION_CONFIG, worker));

    assertThat(thrown.getErrorId()).isEqualTo(ERROR_ID);
    assertThat(((MobileHarnessException) thrown.getCause()).getErrorId())
        .isEqualTo(BasicErrorId.DATABASE_TABLE_READ_ERROR);
    assertThat(thrown.getCause()).hasCauseThat().isEqualTo(causeException);
    InOrder inOrder = inOrder(connection, worker);
    inOrder.verify(connection).setAutoCommit(false);
    inOrder.verify(worker).doWork(any(MySqlTransactionContext.class));
    inOrder.verify(connection).rollback();
    inOrder.verify(connection).setAutoCommit(true);
    inOrder.verify(connection).close();
  }

  @Test
  public void run_commitThrowsSQLException_rollsBackAndThrows() throws Exception {
    when(worker.doWork(any())).thenReturn("success");
    SQLException commitException = new SQLException("Commit failed");
    doThrow(commitException).when(connection).commit();

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> transactionRunner.run(TRANSACTION_CONFIG, worker));

    assertThat(thrown.getErrorId()).isEqualTo(ERROR_ID);
    assertThat(thrown).hasCauseThat().isEqualTo(commitException);
    InOrder inOrder = inOrder(connection, worker);
    inOrder.verify(connection).setAutoCommit(false);
    inOrder.verify(worker).doWork(any(MySqlTransactionContext.class));
    inOrder.verify(connection).commit();
    inOrder.verify(connection).rollback();
    inOrder.verify(connection).setAutoCommit(true);
    inOrder.verify(connection).close();
  }

  @Test
  public void run_rollbackThrowsSQLException_suppressesException() throws Exception {
    MobileHarnessException workerException =
        new MobileHarnessException(
            BasicErrorId.JOB_INFO_CREATE_INVALID_GEN_DIR_ERROR, "Worker failed");
    when(worker.doWork(any())).thenThrow(workerException);
    SQLException rollbackException = new SQLException("Rollback failed");
    doThrow(rollbackException).when(connection).rollback();

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> transactionRunner.run(TRANSACTION_CONFIG, worker));

    assertThat(thrown.getErrorId()).isEqualTo(ERROR_ID);
    assertThat(thrown).hasCauseThat().isEqualTo(workerException);
    assertThat(thrown.getCause().getSuppressed()).hasLength(1);
    assertThat(thrown.getCause().getSuppressed()[0]).isEqualTo(rollbackException);
    InOrder inOrder = inOrder(connection, worker);
    inOrder.verify(connection).setAutoCommit(false);
    inOrder.verify(worker).doWork(any(MySqlTransactionContext.class));
    inOrder.verify(connection).rollback();
    inOrder.verify(connection).setAutoCommit(true);
    inOrder.verify(connection).close();
  }

  @Test
  public void run_setAutoCommitTrueThrowsSQLException_returnsSuccess() throws Exception {
    when(worker.doWork(any())).thenReturn("success");
    doThrow(new SQLException("Failed to reset autoCommit")).when(connection).setAutoCommit(true);

    String result = transactionRunner.run(TRANSACTION_CONFIG, worker);

    assertThat(result).isEqualTo("success");
    InOrder inOrder = inOrder(connection, worker);
    inOrder.verify(connection).setAutoCommit(false);
    inOrder.verify(worker).doWork(any(MySqlTransactionContext.class));
    inOrder.verify(connection).commit();
    inOrder.verify(connection).setAutoCommit(true);
    inOrder.verify(connection).close();
  }

  @Test
  public void run_closeThrowsSQLException_returnsSuccess() throws Exception {
    when(worker.doWork(any())).thenReturn("success");
    doThrow(new SQLException("Close failed")).when(connection).close();

    String result = transactionRunner.run(TRANSACTION_CONFIG, worker);

    assertThat(result).isEqualTo("success");
    InOrder inOrder = inOrder(connection, worker);
    inOrder.verify(connection).setAutoCommit(false);
    inOrder.verify(worker).doWork(any(MySqlTransactionContext.class));
    inOrder.verify(connection).commit();
    inOrder.verify(connection).setAutoCommit(true);
    inOrder.verify(connection).close();
  }

  @Test
  public void run_setAutoCommitFalseThrowsSQLException_rollsBackAndThrows() throws Exception {
    SQLException exception = new SQLException("setAutoCommit(false) failed");
    doThrow(exception).when(connection).setAutoCommit(false);

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> transactionRunner.run(TRANSACTION_CONFIG, worker));

    assertThat(thrown.getErrorId()).isEqualTo(ERROR_ID);
    assertThat(thrown).hasCauseThat().isEqualTo(exception);
    InOrder inOrder = inOrder(connection);
    inOrder.verify(connection).setAutoCommit(false);
    inOrder.verify(connection).rollback();
    inOrder.verify(connection).setAutoCommit(true);
    inOrder.verify(connection).close();
  }

  @Test
  public void run_getConnectionThrowsSQLException() throws Exception {
    MobileHarnessException causeException =
        new MobileHarnessException(
            BasicErrorId.DATABASE_CONNECTIONS_GET_CONNECTION_ERROR,
            "Get connection failed",
            new SQLException("Get connection failed"));
    when(databaseConnections.getConnection()).thenThrow(causeException);

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> transactionRunner.run(TRANSACTION_CONFIG, worker));

    assertThat(thrown.getErrorId()).isEqualTo(ERROR_ID);
    assertThat(thrown).hasCauseThat().isEqualTo(causeException);
    verify(connection, never()).setAutoCommit(anyBoolean());
    verify(connection, never()).rollback();
    verify(connection, never()).commit();
    verify(connection, never()).close();
  }
}
