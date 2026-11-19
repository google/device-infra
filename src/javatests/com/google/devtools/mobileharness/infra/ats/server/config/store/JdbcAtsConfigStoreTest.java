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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.database.DatabaseConnections;
import com.google.protobuf.ByteString;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class JdbcAtsConfigStoreTest {

  private static final String KIND = "kind";
  private static final String ID = "id";
  private static final ByteString DATA = ByteString.copyFromUtf8("data");

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private DatabaseConnections databaseConnections;
  @Mock private Connection connection;
  @Mock private Statement createTableStatement;
  @Mock private PreparedStatement statement;
  @Mock private ResultSet resultSet;

  private JdbcAtsConfigStore store;

  @Before
  public void setUp() throws Exception {
    when(databaseConnections.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(createTableStatement);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);

    store = new JdbcAtsConfigStore(databaseConnections);
  }

  @Test
  public void get_entityExists_returnsData() throws Exception {
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getBytes(1)).thenReturn(DATA.toByteArray());

    assertThat(store.get(KIND, ID)).hasValue(DATA);

    verify(connection).prepareStatement(JdbcAtsConfigStore.GET_SQL);
    verify(statement).setString(1, KIND);
    verify(statement).setString(2, ID);
  }

  @Test
  public void get_entityDoesNotExist_returnsEmpty() throws Exception {
    when(resultSet.next()).thenReturn(false);

    assertThat(store.get(KIND, ID)).isEmpty();
  }

  @Test
  public void get_sqlError_throwsException() throws Exception {
    when(statement.executeQuery()).thenThrow(new SQLException("error"));

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> store.get(KIND, ID));

    assertThat(e.getErrorId()).isEqualTo(BasicErrorId.DATABASE_TABLE_READ_ERROR);
  }

  @Test
  public void put_writesEntity() throws Exception {
    store.put(KIND, ID, DATA);

    verify(connection).prepareStatement(JdbcAtsConfigStore.PUT_SQL);
    verify(statement).setString(1, KIND);
    verify(statement).setString(2, ID);
    verify(statement).setBytes(3, DATA.toByteArray());
    verify(statement).executeUpdate();
  }

  @Test
  public void delete_deletesEntity() throws Exception {
    store.delete(KIND, ID);

    verify(connection).prepareStatement(JdbcAtsConfigStore.DELETE_SQL);
    verify(statement).setString(1, KIND);
    verify(statement).setString(2, ID);
    verify(statement).executeUpdate();
  }

  @Test
  public void createsTableOnFirstUseOnly() throws Exception {
    store.put(KIND, ID, DATA);
    store.delete(KIND, ID);

    verify(createTableStatement).execute(JdbcAtsConfigStore.CREATE_TABLE_SQL);
  }

  @Test
  public void createTableFails_throwsExceptionAndRetriesOnNextUse() throws Exception {
    when(createTableStatement.execute(JdbcAtsConfigStore.CREATE_TABLE_SQL))
        .thenThrow(new SQLException("error"))
        .thenReturn(false);

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> store.put(KIND, ID, DATA));
    assertThat(e.getErrorId()).isEqualTo(BasicErrorId.DATABASE_TABLE_CREATE_ERROR);

    store.put(KIND, ID, DATA);
    verify(statement).executeUpdate();
  }
}
