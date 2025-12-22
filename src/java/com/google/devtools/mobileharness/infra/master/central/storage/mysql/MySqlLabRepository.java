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
import com.google.devtools.mobileharness.infra.master.central.proto.Lab.LabServerCondition;
import com.google.devtools.mobileharness.infra.master.central.storage.lab.LabRepository;
import com.google.devtools.mobileharness.shared.storage.transaction.TransactionContext;
import com.google.devtools.mobileharness.shared.storage.transaction.mysql.MySqlTransactionContext;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Optional;
import javax.inject.Inject;

/** MySQL implementation of {@link LabRepository}. */
public final class MySqlLabRepository implements LabRepository {

  private final MySqlLabTableClient mySqlLabTableClient;

  @Inject
  MySqlLabRepository(MySqlLabTableClient mySqlLabTableClient) {
    this.mySqlLabTableClient = mySqlLabTableClient;
  }

  @Override
  public Optional<LabServerCondition> getLabServerCondition(
      LabLocator labLocator, TransactionContext transactionContext) throws MobileHarnessException {
    MySqlTransactionContext context = castContext(transactionContext);
    return mySqlLabTableClient.getLabServerCondition(labLocator, context.getConnection());
  }

  @Override
  @CanIgnoreReturnValue
  public MySqlLabRepository updateLabServerCondition(
      LabLocator labLocator, boolean isMissing, TransactionContext transactionContext)
      throws MobileHarnessException {
    MySqlTransactionContext context = castContext(transactionContext);
    mySqlLabTableClient.updateLabServerCondition(labLocator, isMissing, context.getConnection());
    return this;
  }

  private MySqlTransactionContext castContext(TransactionContext context) {
    if (!(context instanceof MySqlTransactionContext mysqlTransactionContext)) {
      throw new IllegalArgumentException(
          "Expected " + MySqlTransactionContext.class.getSimpleName());
    }
    return mysqlTransactionContext;
  }
}
