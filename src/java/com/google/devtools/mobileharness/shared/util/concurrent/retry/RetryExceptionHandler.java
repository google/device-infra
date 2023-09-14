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

import java.time.Duration;

/**
 * Handler for exceptions that are swallowed for retry purpose.
 *
 * @see RetryExceptionHandlers
 */
public interface RetryExceptionHandler<X extends Throwable> {

  /**
   * Called when an exception is caught and the operation is to be retried. In general, it should
   * not throw any exception. Note that if the maximum number of retries is reached, or if the
   * current thread is interrupted, the last exception will be immediately propagated and this
   * method won't be called.
   *
   * @param e the caught exception
   * @param attemptsSoFar failed attempts so far
   * @param timeWaited time waited before the retry
   */
  void aboutToRetry(X e, int attemptsSoFar, Duration timeWaited);

  /**
   * Called if interrupted while waiting to retry. The latest exception will be thrown. It's okay to
   * swallow the exception. The framework already propagates the 'interrupted' bit of the thread.
   */
  void interrupted(InterruptedException e, int attemptsSoFar);
}
