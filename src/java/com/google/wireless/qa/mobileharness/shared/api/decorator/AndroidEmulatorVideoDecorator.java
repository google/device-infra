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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidEmulatorVideoDecoratorSpec;

/** Decorator for recording video on Android Emulators using the console recording */
public class AndroidEmulatorVideoDecorator extends BaseDecorator
    implements SpecConfigable<AndroidEmulatorVideoDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public AndroidEmulatorVideoDecorator(Driver decorated, TestInfo testInfo) {
    super(decorated, testInfo);
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    AndroidEmulatorVideoDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this);

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("------- Starting Android Emulator Video Decorator --------\n Spec: %s", spec);

    getDecorated().run(testInfo);

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("------- Stopping Android Emulator Video Decorator --------");
  }
}
