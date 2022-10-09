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

package com.google.devtools.mobileharness.api.model.error;

import com.google.common.base.Throwables;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Utilities for {@link MobileHarnessException}. */
public class MobileHarnessExceptions {

  /**
   * @see com.google.common.base.Preconditions#checkArgument
   * @see com.google.common.base.Preconditions#checkState
   */
  public static void check(boolean expression, ErrorId errorId, Supplier<String> errorMessage)
      throws MobileHarnessException {
    if (!expression) {
      throw new MobileHarnessException(errorId, errorMessage.get());
    }
  }

  /**
   * Rethrows the given throwable as a more specific Mobile Harness exception.
   *
   * <p>This method will never return so its type parameter is unused.
   *
   * @param throwable a throwable which is actually a {@link MobileHarnessException}/{@link
   *     InterruptedException}/{@link RuntimeException}/{@link Error}
   * @param defaultError the default error to throw if the throwable is an unexpected non-MH checked
   *     exception or {@code null}
   * @throws MobileHarnessException if the throwable is actually a {@link MobileHarnessException} or
   *     the throwable is an unexpected non-MH checked exception or {@code null}
   * @throws InterruptedException if the throwable is actually an {@link InterruptedException}
   * @throws RuntimeException if the throwable is actually a {@link RuntimeException}
   * @throws Error if the throwable is actually an {@link Error}
   */
  @SuppressWarnings("TypeParameterUnusedInFormals")
  public static <T> T rethrow(@Nullable Throwable throwable, ErrorId defaultError)
      throws MobileHarnessException, InterruptedException {
    Throwables.propagateIfPossible(
        throwable, MobileHarnessException.class, InterruptedException.class);
    throw new MobileHarnessException(
        defaultError,
        String.format(
            "Unexpected non-MH checked exception [%s]",
            throwable == null ? null : throwable.getClass().getName()),
        throwable);
  }

  private MobileHarnessExceptions() {}
}
