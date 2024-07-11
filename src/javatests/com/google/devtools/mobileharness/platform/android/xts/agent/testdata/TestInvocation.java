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

import com.google.devtools.mobileharness.platform.android.xts.agent.testdata.FakeInvocationContext;

public class TestInvocation {

  private volatile String status = "init";

  @SuppressWarnings("unused")
  public void invoke(FakeInvocationContext context, int param2, int param3, int param4)
      throws InterruptedException {
    status = "running";
    Thread.sleep(6_000L);
    status = "finished";
  }

  @Override
  public String toString() {
    return status;
  }
}
