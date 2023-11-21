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

package com.google.devtools.mobileharness.infra.lab.controller.handler;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.daemon.health.handler.DrainHandler;
import com.google.devtools.mobileharness.infra.lab.rpc.service.ExecTestServiceImpl;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.Inject;
import java.time.Duration;

/** Drain handler for ExecTestServiceImpl. */
public class ExecTestServiceDrainHandler implements DrainHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final ExecTestServiceImpl execTestServiceImpl;

  @Inject
  public ExecTestServiceDrainHandler(ExecTestServiceImpl execTestServiceImpl) {
    this.execTestServiceImpl = execTestServiceImpl;
  }

  @Override
  public void forceCleanUp() {
    execTestServiceImpl.enableForceCleanUpForDrainTimeout();
    logger.atWarning().log(
        "Enabled forcibly clean up running tests on ExecTestServiceImpl for the drain timeout.");

    // Sleep for 1 minutes to make sure all interrupted results have been updated to the client.
    try {
      Sleeper.defaultSleeper().sleep(Duration.ofMinutes(1L));
    } catch (InterruptedException e) {
      logger.atWarning().log("Interrupted when wait for the clean up of ExecTestServiceImpl.");
      Thread.currentThread().interrupt();
    }
  }
}
