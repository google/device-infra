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

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.master.central.storage.lab.DeviceRepository;
import com.google.devtools.mobileharness.shared.storage.transaction.TransactionContext;
import com.google.devtools.mobileharness.shared.storage.transaction.mysql.MySqlTransactionContext;
import javax.inject.Inject;

/** MySQL implementation of {@link DeviceRepository}. */
public final class MySqlDeviceRepository implements DeviceRepository {

  private final MySqlDeviceTableClient mySqlDeviceTableClient;

  @Inject
  MySqlDeviceRepository(MySqlDeviceTableClient mySqlDeviceTableClient) {
    this.mySqlDeviceTableClient = mySqlDeviceTableClient;
  }

  @Override
  public boolean hasDevice(LabLocator labLocator, TransactionContext transactionContext)
      throws MobileHarnessException {
    MySqlTransactionContext context = castContext(transactionContext);
    return mySqlDeviceTableClient.hasDevice(labLocator, context.getConnection());
  }

  private MySqlTransactionContext castContext(TransactionContext context) {
    if (!(context instanceof MySqlTransactionContext mysqlTransactionContext)) {
      throw new IllegalArgumentException(
          "Expected " + MySqlTransactionContext.class.getSimpleName());
    }
    return mysqlTransactionContext;
  }
}
