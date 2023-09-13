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

package com.google.devtools.mobileharness.shared.util.concurrent;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Branch of {@code com.google.common.util.concurrent.Callables} since the GitHub version does not
 * have some methods.
 */
public final class Callables {

  /**
   * Wraps the given callable such that for the duration of {@link Callable#call} the thread that is
   * running will have the given name.
   *
   * @param callable the callable to wrap
   * @param nameSupplier the supplier of thread names, {@link Supplier#get get} will be called once
   *     for each invocation of the wrapped callable.
   */
  public static <T> Callable<T> threadRenaming(
      Callable<T> callable, Supplier<String> nameSupplier) {
    checkNotNull(nameSupplier);
    checkNotNull(callable);
    return () -> {
      Thread currentThread = Thread.currentThread();
      String oldName = currentThread.getName();
      boolean restoreName = trySetName(nameSupplier.get(), currentThread);
      try {
        return callable.call();
      } finally {
        if (restoreName) {
          trySetName(oldName, currentThread);
        }
      }
    };
  }

  /**
   * Wraps the given runnable such that for the duration of {@link Runnable#run} the thread that is
   * running will have the given name.
   *
   * @param runnable the runnable to wrap
   * @param nameSupplier the supplier of thread names, {@link Supplier#get get} will be called once
   *     for each invocation of the wrapped runnable.
   */
  public static Runnable threadRenaming(Runnable runnable, Supplier<String> nameSupplier) {
    checkNotNull(nameSupplier);
    checkNotNull(runnable);
    return () -> {
      Thread currentThread = Thread.currentThread();
      String oldName = currentThread.getName();
      boolean restoreName = trySetName(nameSupplier.get(), currentThread);
      try {
        runnable.run();
      } finally {
        if (restoreName) {
          trySetName(oldName, currentThread);
        }
      }
    };
  }

  @CanIgnoreReturnValue
  private static boolean trySetName(String threadName, Thread currentThread) {
    try {
      currentThread.setName(threadName);
      return true;
    } catch (SecurityException e) {
      return false;
    }
  }

  private Callables() {}
}
