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

package com.google.devtools.mobileharness.shared.storage.transaction;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

/**
 * Configuration for a transaction. It contains data passed to {@link TransactionRunner} from the
 * caller.
 */
@AutoValue
public abstract class TransactionConfig {
  public abstract String description();

  public abstract ErrorId errorId();

  public static TransactionConfig.Builder newBuilder() {
    return new AutoValue_TransactionConfig.Builder();
  }

  /** Builder for creating a {@link TransactionConfig} instance. */
  @AutoValue.Builder
  public abstract static class Builder {

    /**
     * Required: The description of the transaction. Should start with a lower case verb, like "do
     * something".
     */
    public abstract Builder setDescription(String description);

    /** Required: ErrorId of the {@link MobileHarnessException} if any error occurs. */
    public abstract Builder setErrorId(ErrorId errorId);

    abstract TransactionConfig autoBuild();

    public TransactionConfig build() {
      return autoBuild();
    }
  }
}
