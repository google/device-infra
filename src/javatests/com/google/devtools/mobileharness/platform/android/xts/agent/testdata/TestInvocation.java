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

package com.android.tradefed.invoker;

import static com.google.devtools.mobileharness.platform.android.xts.agent.testdata.FakeTradefed.DEVICE_ID_TO_TRIGGER_CHECKED_INVOCATION_EXCEPTION;
import static com.google.devtools.mobileharness.platform.android.xts.agent.testdata.FakeTradefed.DEVICE_ID_TO_TRIGGER_UNCHECKED_INVOCATION_EXCEPTION;
import static com.google.devtools.mobileharness.platform.android.xts.agent.testdata.FakeTradefed.INVOCATION_CHECKED_EXCEPTION_MESSAGE;
import static com.google.devtools.mobileharness.platform.android.xts.agent.testdata.FakeTradefed.INVOCATION_UNCHECKED_EXCEPTION_MESSAGE;

import com.android.tradefed.result.CollectingTestListener;
import com.google.devtools.mobileharness.platform.android.xts.agent.testdata.FakeInvocationContext;

public class TestInvocation {

  private volatile String status = "init";

  @SuppressWarnings("unused")
  public void invoke(FakeInvocationContext context, int param2, int param3, int param4)
      throws InterruptedException {
    if (context.getSerials().contains(DEVICE_ID_TO_TRIGGER_UNCHECKED_INVOCATION_EXCEPTION)) {
      throw new RuntimeException(INVOCATION_UNCHECKED_EXCEPTION_MESSAGE);
    }

    if (context.getSerials().contains(DEVICE_ID_TO_TRIGGER_CHECKED_INVOCATION_EXCEPTION)) {
      new CollectingTestListener()
          .invocationFailed(new InterruptedException(INVOCATION_CHECKED_EXCEPTION_MESSAGE));
    }

    // Briefly sleeps to make sure the monitoring agent will be reading (and writing to file) the
    // "init" status to avoid flaky tests.
    Thread.sleep(1_000L);
    status = "running";
    status = "finished";
  }

  @Override
  public String toString() {
    return status;
  }
}
