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
import com.google.devtools.mobileharness.infra.lab.controller.MasterSyncerForDevice;
import com.google.inject.Inject;

/** {@link DrainHandler} for {@link MasterSyncerForDevice}. */
public class MasterSyncerForDeviceDrainHandler implements DrainHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final MasterSyncerForDevice masterSyncerForDevice;

  @Inject
  public MasterSyncerForDeviceDrainHandler(MasterSyncerForDevice masterSyncerForDevice) {
    this.masterSyncerForDevice = masterSyncerForDevice;
  }

  @Override
  public void drain() {
    logger.atInfo().log("MasterSyncerForDevice Drain Started!");
    masterSyncerForDevice.enableDrainingMode();
    return;
  }
}
