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

package com.google.devtools.mobileharness.shared.constant.closeable;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** A {@link NonThrowingAutoCloseable} that records when {@link #close()} is invoked. */
public abstract class CountingCloseable implements NonThrowingAutoCloseable {

  private final AtomicReference<Instant> closeTime = new AtomicReference<>();

  /** Returns when {@link #close()} is invoked. */
  public final Optional<Instant> closeTime() {
    return Optional.ofNullable(closeTime.get());
  }

  @Override
  public final void close() {
    closeTime.compareAndSet(/* expectedValue= */ null, /* newValue= */ Instant.now());
    doClose();
  }

  /** Sub-classes can override this method to implement their own closing logic. */
  protected void doClose() {}
}
