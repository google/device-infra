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

package com.google.devtools.deviceinfra.host.daemon.health;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.AbstractIdleService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.inject.Inject;

/** Service that blocks shutdown until the server has finished draining, if it started draining. */
class DrainService extends AbstractIdleService {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final HealthStatusManager healthStatusManager;

  @Inject
  DrainService(HealthStatusManager healthStatusManager) {
    this.healthStatusManager = healthStatusManager;
  }

  @Override
  protected void startUp() {}

  @Override
  protected void shutDown() throws InterruptedException {
    Future<?> drainFuture = healthStatusManager.getDrainFuture();

    if (drainFuture == null) {
      logger.atWarning().log("Host not draining before it shut down! This may interrupt tests.");
      return;
    }

    logger.atInfo().log("Waiting for server to drain.");
    try {
      drainFuture.get();
      logger.atInfo().log("Server drained.");
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Error while waiting for server to drain.");
    }
  }
}
