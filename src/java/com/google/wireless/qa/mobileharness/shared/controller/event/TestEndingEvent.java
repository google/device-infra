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

package com.google.wireless.qa.mobileharness.shared.controller.event;

import com.google.common.annotations.Beta;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import javax.annotation.Nullable;

/**
 * Event to signal that a test is ending. Note the device resource hasn't been released at this
 * moment. In REMOTE Mode, the client will wait for lab server to finish handling this event.
 */
public class TestEndingEvent extends TestEvent {

  @Nullable private final Throwable testError;

  /**
   * NOTE: Do NOT instantiate it in tests of your plugin and use mocked object instead.
   *
   * <p>TODO: Uses factory to prevent instantiating out of MH infrastructure.
   */
  @Beta
  public TestEndingEvent(
      TestInfo testInfo,
      Allocation allocation,
      @Nullable DeviceInfo deviceInfo,
      @Nullable Throwable testError) {
    super(testInfo, allocation, deviceInfo);
    this.testError = testError;
  }

  public boolean hasTestError() {
    return testError != null;
  }

  @Nullable
  public Throwable getTestError() {
    return testError;
  }
}
