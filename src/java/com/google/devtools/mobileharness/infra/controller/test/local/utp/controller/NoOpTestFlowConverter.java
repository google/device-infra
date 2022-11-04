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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.common.IncompatibleReasons;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.common.TestFlows;
import com.google.devtools.mobileharness.infra.controller.test.local.utp.proto.IncompatibleReasonProto.InfraIncompatibleReason;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.List;

/** A default implementation of {@link TestFlowConverter} which does not convert tests. */
public class NoOpTestFlowConverter implements TestFlowConverter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final InfraIncompatibleReason reason;
  private final String detail;

  public NoOpTestFlowConverter(InfraIncompatibleReason reason, String detail) {
    this.reason = reason;
    this.detail = detail;
  }

  @Override
  public Result convert(TestInfo testInfo, List<Device> devices, Allocation allocation) {
    testInfo.log().atInfo().alsoTo(logger).log("Run test in MH classic mode because %s", detail);

    IncompatibleReasons incompatibleReasons = new IncompatibleReasons();
    incompatibleReasons.addReason(reason);
    incompatibleReasons.addToTestProperties(testInfo.properties());

    return Result.of(TestFlows.getOriginalFlow(testInfo), null /* utpDriver */);
  }
}
