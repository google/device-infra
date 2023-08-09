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

package com.google.devtools.mobileharness.infra.lab.controller;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunnerSetting;
import com.google.devtools.mobileharness.infra.controller.test.TestRunnerLauncher;
import com.google.devtools.mobileharness.infra.controller.test.exception.TestRunnerLauncherConnectedException;
import com.google.devtools.mobileharness.infra.controller.test.local.LocalDirectTestRunner;
import com.google.devtools.mobileharness.infra.lab.controller.util.LabFileNotifier;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.List;

/** {@link LocalDirectTestRunner} which runs in lab server or test engine. */
public class LabLocalDirectTestRunner extends LocalDirectTestRunner {

  private final LabFileNotifier labFileNotifier;

  public LabLocalDirectTestRunner(
      TestRunnerLauncher<? super LocalDirectTestRunner> launcher,
      DirectTestRunnerSetting setting,
      List<Device> devices,
      ListeningExecutorService threadPool,
      LabFileNotifier labFileNotifier)
      throws TestRunnerLauncherConnectedException {
    super(
        launcher,
        setting,
        devices,
        threadPool,
        new com.google.devtools.mobileharness.infra.controller.test.local.utp.controller
            .NoOpTestFlowConverter(
            com.google.devtools.mobileharness.infra.controller.test.local.utp.proto
                .IncompatibleReasonProto.InfraIncompatibleReason.ATS2,
            "ATS2 uses classic mode"));
    this.labFileNotifier = labFileNotifier;
  }

  @Override
  protected String getComponentName() {
    return "lab";
  }

  /**
   * Handles cached job/test files.
   *
   * <p>{@inheritDoc}
   */
  @Override
  protected void initialize(TestInfo testInfo, Allocation allocation)
      throws MobileHarnessException {
    // Handles cached job/test files.
    labFileNotifier.onTestStarting(testInfo, allocation);

    super.initialize(testInfo, allocation);
  }
}
