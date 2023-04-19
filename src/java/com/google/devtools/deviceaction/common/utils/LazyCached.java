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

package com.google.devtools.deviceaction.common.utils;

import com.google.devtools.deviceaction.common.error.DeviceActionException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * A utility class to cache and provide objects.
 *
 * <p>It gets the item by calling the {@code provide()} method until success. Then it will cache the
 * item until reset.
 */
public abstract class LazyCached<T> implements Callable<T>, Supplier<Optional<T>> {
  private T item;

  public LazyCached() {}

  /**
   * @see Callable#call()
   */
  @Override
  public final T call() throws DeviceActionException, InterruptedException {
    if (item == null) {
      item = provide();
    }
    return item;
  }

  /**
   * @see java.util.function.Supplier#get()
   */
  @Override
  public final Optional<T> get() {
    try {
      return Optional.ofNullable(call());
    } catch (DeviceActionException | InterruptedException e) {
      return Optional.empty();
    }
  }

  /** Clears up the cached item. */
  public void reset() {
    item = null;
  }

  protected abstract T provide() throws DeviceActionException, InterruptedException;
}
