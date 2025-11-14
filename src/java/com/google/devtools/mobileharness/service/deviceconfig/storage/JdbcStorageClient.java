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

package com.google.devtools.mobileharness.service.deviceconfig.storage;

import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.database.DatabaseConnections;
import com.google.devtools.mobileharness.shared.util.database.TableUtil.StringBinaryProtoTable;
import java.util.Optional;
import javax.inject.Inject;

/** The client to talk to the config tables in the database using JDBC. */
public final class JdbcStorageClient implements StorageClient {

  private static final String DEVICE_CONFIG_TABLE_NAME = "device_config_table";
  private static final String DEVICE_CONFIG_KEY_COLUMN_NAME = "device_uuid";
  private static final String DEVICE_CONFIG_VALUE_COLUMN_NAME = "device_config";

  private static final String LAB_CONFIG_TABLE_NAME = "lab_config_table";
  private static final String LAB_CONFIG_KEY_COLUMN_NAME = "lab_host";
  private static final String LAB_CONFIG_VALUE_COLUMN_NAME = "lab_config";

  private final StringBinaryProtoTable<DeviceConfig> deviceConfigTable;
  private final StringBinaryProtoTable<LabConfig> labConfigTable;

  @Inject
  JdbcStorageClient(DatabaseConnections databaseConnections) {
    this.deviceConfigTable =
        new StringBinaryProtoTable<>(
            databaseConnections,
            DeviceConfig.parser(),
            DEVICE_CONFIG_TABLE_NAME,
            DEVICE_CONFIG_KEY_COLUMN_NAME,
            DEVICE_CONFIG_VALUE_COLUMN_NAME);
    this.labConfigTable =
        new StringBinaryProtoTable<>(
            databaseConnections,
            LabConfig.parser(),
            LAB_CONFIG_TABLE_NAME,
            LAB_CONFIG_KEY_COLUMN_NAME,
            LAB_CONFIG_VALUE_COLUMN_NAME);
  }

  @Override
  public Optional<DeviceConfig> getDeviceConfig(String deviceUuid) throws MobileHarnessException {
    // TODO: b/460296020 - Be more efficient by adding a new interface to StringBinaryProtoTable so
    // that we can directly read by key.
    return deviceConfigTable.read().stream()
        .filter(deviceConfig -> deviceConfig.key().filter(deviceUuid::equals).isPresent())
        .findFirst()
        .flatMap(deviceConfig -> deviceConfig.value().map(m -> (DeviceConfig) m));
  }

  @Override
  public Optional<LabConfig> getLabConfig(String labHost) throws MobileHarnessException {
    // TODO: b/460296020 - Be more efficient by adding a new interface to StringBinaryProtoTable so
    // that we can directly read by key.
    return labConfigTable.read().stream()
        .filter(labConfig -> labConfig.key().filter(labHost::equals).isPresent())
        .findFirst()
        .flatMap(labConfig -> labConfig.value().map(m -> (LabConfig) m));
  }

  @Override
  public void upsertDeviceConfig(DeviceConfig deviceConfig) throws MobileHarnessException {
    deviceConfigTable.update(deviceConfig.getUuid(), deviceConfig);
  }

  @Override
  public void upsertLabConfig(LabConfig labConfig) throws MobileHarnessException {
    labConfigTable.update(labConfig.getHostName(), labConfig);
  }

  @Override
  public void deleteDeviceConfig(String deviceUuid) throws MobileHarnessException {
    deviceConfigTable.delete(deviceUuid);
  }

  @Override
  public void deleteLabConfig(String labHost) throws MobileHarnessException {
    labConfigTable.delete(labHost);
  }
}
