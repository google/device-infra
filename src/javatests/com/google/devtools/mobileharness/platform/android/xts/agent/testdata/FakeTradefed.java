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

package com.google.devtools.mobileharness.platform.android.xts.agent.testdata;

import static java.util.Arrays.asList;

import com.android.tradefed.cluster.FakeClusterCommandSchedulerUtil;
import com.android.tradefed.invoker.TestInvocation;
import java.util.UUID;

public class FakeTradefed {

  public static final String DEVICE_ID_TO_TRIGGER_INVOCATION_EXCEPTION =
      "device_id_to_trigger_invocation_exception";
  public static final String INVOCATION_EXCEPTION_MESSAGE = "Fake tradefed invocation exception.";

  public static void main(String[] args) throws InterruptedException {
    FakeInvocationContext context =
        new FakeInvocationContext(UUID.randomUUID().toString(), asList(args));
    try {
      new TestInvocation().invoke(context, 0, 0, 0);
    } catch (RuntimeException e) {
      if (!e.getMessage().equals(INVOCATION_EXCEPTION_MESSAGE)) {
        throw e;
      }
    }
    // Briefly sleeps to make sure the test will be able to read the row generated by the monitoring
    // agent after exiting the invocation.
    Thread.sleep(1_000L);
    FakeClusterCommandSchedulerUtil.invocationComplete(context);
    // Briefly sleeps to make sure the test will be able to read the row generated by the monitoring
    // agent after the `invocationComplete` method invocation.
    Thread.sleep(1_000L);
  }

  private FakeTradefed() {}
}
