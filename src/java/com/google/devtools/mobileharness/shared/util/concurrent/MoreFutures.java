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

import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.FormatMethod;
import java.util.logging.Level;

/** More utility futures. */
public class MoreFutures {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private MoreFutures() {}

  /** Adds a callback to a future that will log a message if the future fails. */
  @FormatMethod
  @CanIgnoreReturnValue
  public static <V> ListenableFuture<V> logFailure(
      ListenableFuture<V> future, Level level, String message, Object... params) {
    addCallback(
        future,
        new FutureCallback<V>() {
          @Override
          public void onSuccess(V result) {}

          @Override
          public void onFailure(Throwable t) {
            logger.at(level).withCause(t).logVarargs(message, params);
          }
        },
        directExecutor());
    return future;
  }
}
