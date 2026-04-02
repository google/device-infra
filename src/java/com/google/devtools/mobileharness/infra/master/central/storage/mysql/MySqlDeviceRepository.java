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
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.master.central.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.infra.master.central.storage.lab.DeviceRepository;
import com.google.devtools.mobileharness.shared.storage.transaction.TransactionContext;
import com.google.devtools.mobileharness.shared.storage.transaction.mysql.MySqlTransactionContext;
import java.util.Optional;
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

  @Override
  public Optional<DeviceCondition> getDeviceCondition(
      DeviceLocator deviceLocator, TransactionContext transactionContext)
      throws MobileHarnessException {
    MySqlTransactionContext context = castContext(transactionContext);
    return mySqlDeviceTableClient.getDeviceCondition(deviceLocator, context.getConnection());
  }

  @Override
  public void updateDeviceCondition(
      DeviceLocator deviceLocator, DeviceCondition condition, TransactionContext transactionContext)
      throws MobileHarnessException {
    MySqlTransactionContext context = castContext(transactionContext);
    mySqlDeviceTableClient.updateDeviceCondition(deviceLocator, condition, context.getConnection());
  }

  @Override
  public void removeDevice(DeviceLocator deviceLocator, TransactionContext transactionContext)
      throws MobileHarnessException {
    MySqlTransactionContext context = castContext(transactionContext);
    mySqlDeviceTableClient.removeDevice(deviceLocator, context.getConnection());
  }

  private MySqlTransactionContext castContext(TransactionContext context) {
    if (!(context instanceof MySqlTransactionContext mysqlTransactionContext)) {
      throw new IllegalArgumentException(
          "Expected " + MySqlTransactionContext.class.getSimpleName());
    }
    return mysqlTransactionContext;
  }
}
