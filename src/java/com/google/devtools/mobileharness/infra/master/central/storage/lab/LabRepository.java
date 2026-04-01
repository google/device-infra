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

package com.google.devtools.mobileharness.infra.master.central.storage.lab;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.master.central.model.lab.LabDao;
import com.google.devtools.mobileharness.infra.master.central.proto.Lab.LabServerCondition;
import com.google.devtools.mobileharness.shared.storage.transaction.TransactionContext;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Optional;

/** Interface for lab repository. */
public interface LabRepository {

  /**
   * Gets the lab server condition.
   *
   * @throws MobileHarnessException if the lab doesn't exist
   */
  public Optional<LabServerCondition> getLabServerCondition(
      LabLocator labLocator, TransactionContext context) throws MobileHarnessException;

  /** Updates the lab server condition of an existing lab. */
  @CanIgnoreReturnValue
  public LabRepository updateLabServerCondition(
      LabLocator labLocator, boolean isMissing, TransactionContext context)
      throws MobileHarnessException;

  /**
   * Reads the information of one lab from the lab table.
   *
   * @param readDevices if true, will also read the device info from device repository.
   * @throws MobileHarnessException if lab not found
   * @throws NullPointerException if the lab IP/hostname or device column value is null
   * @throws IllegalArgumentException if the column name is ambiguous or the column does not exist
   * @throws IllegalStateException if parsing failed state check. Currently does not check
   *     out.isInitialized.
   */
  public LabDao getLab(LabLocator labLocator, TransactionContext context, boolean readDevices)
      throws MobileHarnessException;

  /** Removes the lab from the repository. */
  void removeLab(LabLocator labLocator, TransactionContext context) throws MobileHarnessException;

  /** Clears the lab server condition (sets to null/empty). */
  void clearLabServer(LabLocator labLocator, TransactionContext context)
      throws MobileHarnessException;
}
