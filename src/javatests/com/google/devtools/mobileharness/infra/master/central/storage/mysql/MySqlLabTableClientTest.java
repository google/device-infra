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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.master.central.proto.Lab.LabServerCondition;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class MySqlLabTableClientTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Connection mockConnection;
  @Mock private PreparedStatement mockPreparedStatement;
  @Mock private ResultSet mockResultSet;
  @Mock private Clock mockClock;

  private static final String LAB_ID = "test_lab_id";
  private static final LabLocator LAB_LOCATOR =
      LabLocator.of(/* ip= */ "test_lab_ip", /* hostName= */ LAB_ID);
  private static final long CURRENT_MILLIS = 1234567890L;

  private MySqlLabTableClient mySqlLabTableClient;

  @Before
  public void setUp() throws Exception {
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockClock.millis()).thenReturn(CURRENT_MILLIS);

    mySqlLabTableClient = new MySqlLabTableClient(mockClock);
  }

  @Test
  public void getLabServerCondition_exists() throws Exception {
    LabServerCondition expectedCondition =
        LabServerCondition.newBuilder().setTimestampMs(CURRENT_MILLIS).setIsMissing(false).build();
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getBytes(anyString())).thenReturn(expectedCondition.toByteArray());

    Optional<LabServerCondition> actualCondition =
        mySqlLabTableClient.getLabServerCondition(LAB_LOCATOR, mockConnection);

    assertThat(actualCondition).hasValue(expectedCondition);
    verify(mockPreparedStatement).setString(1, LAB_ID);
  }

  @Test
  public void getLabServerCondition_notExists() throws Exception {
    when(mockResultSet.next()).thenReturn(false);

    Optional<LabServerCondition> actualCondition =
        mySqlLabTableClient.getLabServerCondition(LAB_LOCATOR, mockConnection);

    assertThat(actualCondition).isEmpty();
    verify(mockPreparedStatement).setString(1, LAB_ID);
  }

  @Test
  public void getLabServerCondition_nullCondition() throws Exception {
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getBytes(anyString())).thenReturn(null);

    Optional<LabServerCondition> actualCondition =
        mySqlLabTableClient.getLabServerCondition(LAB_LOCATOR, mockConnection);

    assertThat(actualCondition).isEmpty();
    verify(mockPreparedStatement).setString(1, LAB_ID);
  }

  @Test
  public void getLabServerCondition_sqlException() throws Exception {
    when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("DB error"));

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> mySqlLabTableClient.getLabServerCondition(LAB_LOCATOR, mockConnection));
    assertThat(exception).hasMessageThat().contains("Failed to get LabServerCondition");
  }

  @Test
  public void getLabServerCondition_invalidProtocolBufferException() throws Exception {
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getBytes(anyString())).thenReturn(new byte[] {0x01}); // Invalid proto

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> mySqlLabTableClient.getLabServerCondition(LAB_LOCATOR, mockConnection));
    assertThat(exception).hasMessageThat().contains("Failed to get LabServerCondition");
  }

  @Test
  public void updateLabServerCondition_success() throws Exception {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);

    mySqlLabTableClient.updateLabServerCondition(LAB_LOCATOR, true, mockConnection);

    LabServerCondition expectedCondition =
        LabServerCondition.newBuilder().setTimestampMs(CURRENT_MILLIS).setIsMissing(true).build();
    verify(mockPreparedStatement).setBytes(1, expectedCondition.toByteArray());
    verify(mockPreparedStatement).setString(2, LAB_ID);
  }

  @Test
  public void updateLabServerCondition_notFound() throws Exception {
    when(mockPreparedStatement.executeUpdate()).thenReturn(0);

    mySqlLabTableClient.updateLabServerCondition(LAB_LOCATOR, false, mockConnection);

    LabServerCondition expectedCondition =
        LabServerCondition.newBuilder().setTimestampMs(CURRENT_MILLIS).setIsMissing(false).build();
    verify(mockPreparedStatement).setBytes(1, expectedCondition.toByteArray());
    verify(mockPreparedStatement).setString(2, LAB_ID);
    // Warning should be logged, but we don't have an easy way to assert logs here.
  }

  @Test
  public void updateLabServerCondition_sqlException() throws Exception {
    when(mockPreparedStatement.executeUpdate()).thenThrow(new SQLException("DB error"));

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> mySqlLabTableClient.updateLabServerCondition(LAB_LOCATOR, true, mockConnection));
    assertThat(exception).hasMessageThat().contains("Failed to update LabServerCondition");
  }
}
