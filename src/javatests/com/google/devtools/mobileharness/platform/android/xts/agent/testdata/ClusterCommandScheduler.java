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

package com.android.tradefed.cluster;

import static com.google.devtools.mobileharness.platform.android.xts.agent.testdata.FakeTradefed.DEVICE_ID_TO_TRIGGER_CHECKED_INVOCATION_EXCEPTION;
import static com.google.devtools.mobileharness.platform.android.xts.agent.testdata.FakeTradefed.DEVICE_ID_TO_TRIGGER_UNCHECKED_INVOCATION_EXCEPTION;

import com.google.devtools.mobileharness.platform.android.xts.agent.testdata.FakeInvocationContext;
import java.util.List;

public class ClusterCommandScheduler {

  @SuppressWarnings("ClassCanBeStatic")
  class InvocationEventHandler {

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private String mError;

    public void invocationComplete(FakeInvocationContext metadata, Object devicesStates) {
      List<String> serials = metadata.getSerials();
      if (serials.contains(DEVICE_ID_TO_TRIGGER_CHECKED_INVOCATION_EXCEPTION)
          || serials.contains(DEVICE_ID_TO_TRIGGER_UNCHECKED_INVOCATION_EXCEPTION)) {
        return;
      }

      mError = "Fake error message";
    }
  }
}
