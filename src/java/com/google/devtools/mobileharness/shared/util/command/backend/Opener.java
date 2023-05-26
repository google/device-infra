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

package com.google.devtools.mobileharness.shared.util.command.backend;

import static com.google.common.base.Preconditions.checkState;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opens a resource at most once. */
public final class Opener<R extends Closeable> {
  interface StreamSupplier<R extends Closeable> {
    R get() throws IOException;
  }

  private final AtomicBoolean opened = new AtomicBoolean(false);
  private final StreamSupplier<R> supplier;

  Opener(StreamSupplier<R> supplier) {
    this.supplier = supplier;
  }

  /**
   * Opens the resource.
   *
   * @throws IOException - if an I/O error occurs
   * @throws IllegalStateException - if the stream has already been opened
   */
  public R open() throws IOException {
    checkState(!opened.getAndSet(true), "already opened");
    return supplier.get();
  }

  /** Returns whether the stream was ever opened. */
  boolean opened() {
    return opened.get();
  }
}
