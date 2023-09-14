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

package com.google.devtools.mobileharness.shared.util.concurrent.retry;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;

/**
 * Exception thrown when a retry operation fails. This exception reports the number of attempts made
 * before failure.
 */
public class RetryException extends Exception {
  private final int tries;

  RetryException(int tries, Exception wrapped) {
    super(checkNotNull(wrapped));
    this.tries = tries;
  }

  @Override
  public synchronized Exception getCause() {
    return (Exception) requireNonNull(super.getCause());
  }

  /** Returns the number of attempts made before failure. */
  public int getTries() {
    return tries;
  }

  private static final long serialVersionUID = 0L;
}
