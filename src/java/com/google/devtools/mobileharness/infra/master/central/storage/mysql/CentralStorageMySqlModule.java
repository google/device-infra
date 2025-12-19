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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.master.central.CentralAnnotation.CentralDatabaseConnections;
import com.google.devtools.mobileharness.infra.master.central.storage.lab.LabRepository;
import com.google.devtools.mobileharness.shared.storage.transaction.TransactionRunner;
import com.google.devtools.mobileharness.shared.util.database.DatabaseConnections;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.util.Properties;
import javax.inject.Singleton;

/** The module for the MySQL storage of Master Central. */
public class CentralStorageMySqlModule extends AbstractModule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  protected void configure() {
    bind(LabRepository.class).to(MySqlLabRepository.class);
    bind(TransactionRunner.class).to(MySqlTransactionRunner.class);
    logger.atInfo().log("Master Central storage MySql module configured");
  }

  @Provides
  @Singleton
  @CentralDatabaseConnections
  DatabaseConnections provideDatabaseConnections() throws MobileHarnessException {
    String jdbcUrl = Flags.instance().masterCentralDatabaseJdbcUrl.getNonNull();
    Properties properties = new Properties();
    Flags.instance()
        .masterCentralDatabaseJdbcProperty
        .getNonNull()
        .forEach(properties::setProperty);
    DatabaseConnections databaseConnections = new DatabaseConnections();
    databaseConnections.initialize(jdbcUrl, properties, /* statementCacheSize= */ 100);
    logger.atInfo().log(
        "Master Central storage MySql database connections initialized: %s, %s.",
        jdbcUrl, properties);
    return databaseConnections;
  }
}
