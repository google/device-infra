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

package com.google.devtools.mobileharness.infra.master.central.storage;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

/** Runner for transactions. */
public interface TransactionRunner {

  /**
   * Runs the given transaction worker using the given configuration.
   *
   * @param config the transaction configuration
   * @param worker the transaction worker
   * @return the result of the transaction
   * @throws MobileHarnessException if fails to run the transaction
   */
  public abstract <R> R run(TransactionConfig config, TransactionWorker<R> worker)
      throws MobileHarnessException;
}
