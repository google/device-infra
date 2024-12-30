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

package com.google.devtools.mobileharness.shared.util.junit.rule.util;

import javax.annotation.Nullable;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * A {@link TestWatcher} which handles {@link #finished(Description)} with the optional test
 * failure.
 */
public abstract class FinishWithFailureTestWatcher extends TestWatcher {

  private Throwable testFailure;

  protected abstract void onFinished(@Nullable Throwable testFailure, Description description);

  @Override
  protected final void failed(Throwable testFailure, Description description) {
    this.testFailure = testFailure;
  }

  @Override
  protected final void finished(Description description) {
    onFinished(testFailure, description);
  }
}
