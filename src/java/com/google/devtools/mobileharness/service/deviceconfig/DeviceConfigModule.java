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

package com.google.devtools.mobileharness.service.deviceconfig;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.service.deviceconfig.storage.JdbcStorageClient;
import com.google.devtools.mobileharness.service.deviceconfig.storage.LocalFileStorageClient;
import com.google.devtools.mobileharness.service.deviceconfig.storage.StorageClient;
import com.google.devtools.mobileharness.shared.util.database.DatabaseConnections;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.util.Properties;
import javax.inject.Singleton;

/** The Guice module for device config service. */
public class DeviceConfigModule extends AbstractModule {

  @Override
  protected void configure() {
    switch (Flags.instance().configServiceStorageType.getNonNull()) {
      case LOCAL_FILE -> bind(StorageClient.class).to(LocalFileStorageClient.class);
      case JDBC_CONNECTOR -> bind(StorageClient.class).to(JdbcStorageClient.class);
    }
  }

  @Provides
  @Singleton
  DatabaseConnections provideDatabaseConnections() throws MobileHarnessException {
    Properties properties = new Properties();
    properties.put("user", "root");
    properties.put("password", "");

    DatabaseConnections databaseConnections = new DatabaseConnections();
    // TODO: b/460296020 - Add a flag to control the database connection string.
    databaseConnections.initialize(
        "jdbc:mysql:///ats_db", properties, /* statementCacheSize= */ 100);
    return databaseConnections;
  }

  @Provides
  @Singleton
  @Annotations.ServerPort
  int provideServerPort() {
    return Flags.instance().configServiceGrpcPort.getNonNull();
  }
}
