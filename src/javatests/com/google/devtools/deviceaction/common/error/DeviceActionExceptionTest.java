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

package com.google.devtools.deviceaction.common.error;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.api.model.error.BasicErrorId.SYSTEM_INVALID_PROCESS_ID;

import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceinfra.api.error.DeviceInfraException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceActionExceptionTest {

  @Test
  public void construct_getMessage() {
    DeviceActionException e =
        new DeviceActionException("FAKE", ErrorType.DEPENDENCY_ISSUE, "message");

    assertThat(e).hasMessageThat().isEqualTo("[DA|DEPENDENCY_ISSUE|FAKE|2150229] message");
  }

  @Test
  public void construct_withCause() {
    Exception cause = new RuntimeException();

    DeviceActionException e =
        new DeviceActionException("FAKE", ErrorType.DEPENDENCY_ISSUE, "message", cause);

    assertThat(e).hasMessageThat().isEqualTo("[DA|DEPENDENCY_ISSUE|FAKE|2150229] message");
    assertThat(e).hasCauseThat().isEqualTo(cause);
  }

  @Test
  public void construct_nullCause() {
    DeviceActionException e =
        new DeviceActionException("FAKE", ErrorType.DEPENDENCY_ISSUE, "message", null);

    assertThat(e).hasMessageThat().isEqualTo("[DA|DEPENDENCY_ISSUE|FAKE|2150229] message");
    assertThat(e).hasCauseThat().isNull();
  }

  @Test
  public void construct_convertMobileHarness() {
    MobileHarnessException cause = new MobileHarnessException(SYSTEM_INVALID_PROCESS_ID, "message");

    DeviceActionException e = new DeviceActionException(cause, "format %s %d.", "arg1", 2);

    assertThat(e)
        .hasMessageThat()
        .isEqualTo("[DA|UNDETERMINED|SYSTEM_INVALID_PROCESS_ID|31655] format arg1 2.");
    assertThat(e).hasCauseThat().isEqualTo(cause);
  }

  @Test
  public void construct_convertDeviceInfraHarness() {
    DeviceInfraException cause = new DeviceInfraException(SYSTEM_INVALID_PROCESS_ID, "message");

    DeviceActionException e = new DeviceActionException(cause, "format %s %d.", "arg1", 2);

    assertThat(e)
        .hasMessageThat()
        .isEqualTo("[DA|UNDETERMINED|SYSTEM_INVALID_PROCESS_ID|31655] format arg1 2.");
    assertThat(e).hasCauseThat().isEqualTo(cause);
  }
}
