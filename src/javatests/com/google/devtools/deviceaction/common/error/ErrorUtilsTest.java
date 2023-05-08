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

import static com.google.common.truth.Truth8.assertThat;
import static com.google.devtools.mobileharness.api.model.error.BasicErrorId.SYSTEM_INVALID_PROCESS_ID;

import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ErrorUtilsTest {

  @Test
  public void findCause_expectedResult() {
    MobileHarnessException cause = new MobileHarnessException(SYSTEM_INVALID_PROCESS_ID, "message");

    assertThat(
            ErrorUtils.findCause(
                new DeviceActionException(cause, "caused by MH"), MobileHarnessException.class))
        .hasValue(cause);
    assertThat(
            ErrorUtils.findCause(
                new DeviceActionException("FAKE", ErrorType.UNDETERMINED, "No cause."),
                MobileHarnessException.class))
        .isEmpty();
    assertThat(
            ErrorUtils.findCause(
                new DeviceActionException(cause, "caused by MH"), IOException.class))
        .isEmpty();
  }
}
