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
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.infra.master.central.proto.Device.DeviceCondition;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
public final class MySqlDeviceTableClientTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Connection mockConnection;
  @Mock private PreparedStatement mockPreparedStatement;
  @Mock private ResultSet mockResultSet;

  private static final String LAB_ID = "test_lab_id";
  private static final LabLocator LAB_LOCATOR =
      LabLocator.of(/* ip= */ "test_lab_ip", /* hostName= */ LAB_ID);
  private static final String DEVICE_ID = "test_device_id";
  private static final DeviceLocator DEVICE_LOCATOR = DeviceLocator.of(DEVICE_ID, LAB_LOCATOR);
  private static final DeviceCondition DEVICE_CONDITION =
      DeviceCondition.newBuilder().setStatusFromLab(DeviceStatus.MISSING).build();

  private MySqlDeviceTableClient mySqlDeviceTableClient;

  @Before
  public void setUp() throws Exception {
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

    mySqlDeviceTableClient = new MySqlDeviceTableClient();
  }

  @Test
  public void hasDevice_true() throws Exception {
    when(mockResultSet.next()).thenReturn(true);

    boolean hasDevice = mySqlDeviceTableClient.hasDevice(LAB_LOCATOR, mockConnection);

    assertThat(hasDevice).isTrue();
    verify(mockPreparedStatement).setString(1, LAB_ID);
  }

  @Test
  public void hasDevice_false() throws Exception {
    when(mockResultSet.next()).thenReturn(false);

    boolean hasDevice = mySqlDeviceTableClient.hasDevice(LAB_LOCATOR, mockConnection);

    assertThat(hasDevice).isFalse();
    verify(mockPreparedStatement).setString(1, LAB_ID);
  }

  @Test
  public void hasDevice_sqlException() throws Exception {
    when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("DB error"));

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> mySqlDeviceTableClient.hasDevice(LAB_LOCATOR, mockConnection));
    assertThat(exception).hasMessageThat().contains("Failed to check devices for lab");
  }

  @Test
  public void getDeviceCondition_success() throws Exception {
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getBytes("DeviceCondition")).thenReturn(DEVICE_CONDITION.toByteArray());

    Optional<DeviceCondition> result =
        mySqlDeviceTableClient.getDeviceCondition(DEVICE_LOCATOR, mockConnection);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(DEVICE_CONDITION);
    verify(mockPreparedStatement).setString(1, LAB_ID);
    verify(mockPreparedStatement).setString(2, DEVICE_ID);
  }

  @Test
  public void getDeviceCondition_empty() throws Exception {
    when(mockResultSet.next()).thenReturn(false);

    Optional<DeviceCondition> result =
        mySqlDeviceTableClient.getDeviceCondition(DEVICE_LOCATOR, mockConnection);

    assertThat(result).isEmpty();
  }

  @Test
  public void getDeviceCondition_nullValue() throws Exception {
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getBytes("DeviceCondition")).thenReturn(null);

    Optional<DeviceCondition> result =
        mySqlDeviceTableClient.getDeviceCondition(DEVICE_LOCATOR, mockConnection);

    assertThat(result).isEmpty();
  }

  @Test
  public void getDeviceCondition_exception() throws Exception {
    when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("DB error"));

    assertThrows(
        MobileHarnessException.class,
        () -> mySqlDeviceTableClient.getDeviceCondition(DEVICE_LOCATOR, mockConnection));
  }

  @Test
  public void updateDeviceCondition_success() throws Exception {
    mySqlDeviceTableClient.updateDeviceCondition(DEVICE_LOCATOR, DEVICE_CONDITION, mockConnection);

    verify(mockPreparedStatement).setBytes(1, DEVICE_CONDITION.toByteArray());
    verify(mockPreparedStatement).setString(2, LAB_ID);
    verify(mockPreparedStatement).setString(3, DEVICE_ID);
    verify(mockPreparedStatement).executeUpdate();
  }

  @Test
  public void updateDeviceCondition_exception() throws Exception {
    when(mockPreparedStatement.executeUpdate()).thenThrow(new SQLException("DB error"));

    assertThrows(
        MobileHarnessException.class,
        () ->
            mySqlDeviceTableClient.updateDeviceCondition(
                DEVICE_LOCATOR, DEVICE_CONDITION, mockConnection));
  }
}
