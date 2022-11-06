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

package com.google.devtools.mobileharness.infra.controller.test.local.utp.controller;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.proto.BasicFlowProto.BasicFlow;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Controller for converting a MH test to hybrid UTP mode if possible. */
public interface TestFlowConverter {

  Result convert(TestInfo testInfo, List<Device> devices, Allocation allocation)
      throws MobileHarnessException, InterruptedException;

  /** A conversion result. */
  @AutoValue
  abstract class Result {

    public abstract BasicFlow flow();

    /**
     * Returns a {@linkplain
     * com.google.devtools.mobileharness.infra.controller.test.local.utp.driver.UtpDriver UTP
     * driver} if {@link #flow()} specifies a MH UTP driver proto, or returns empty if {@link
     * #flow()} specifies an original MH driver.
     */
    public abstract Optional<Driver> utpDriver();

    static Result of(BasicFlow basicFlow, @Nullable Driver utpDriver) {
      return new AutoValue_TestFlowConverter_Result(basicFlow, Optional.ofNullable(utpDriver));
    }
  }
}
