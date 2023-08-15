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
import com.google.devtools.deviceinfra.host.daemon.health.handler.DrainHandler;
import com.google.devtools.mobileharness.infra.lab.controller.JobManager;
import com.google.inject.Inject;

/** Drain handler for JobManager. */
public class JobManagerDrainHandler implements DrainHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final JobManager jobManager;

  @Inject
  public JobManagerDrainHandler(JobManager jobManager) {
    this.jobManager = jobManager;
  }

  @Override
  public boolean hasDrained() {
    logger.atInfo().log("Check if TestManager has drained.");
    return jobManager.isAllTestClientPostRunDone();
  }
}
