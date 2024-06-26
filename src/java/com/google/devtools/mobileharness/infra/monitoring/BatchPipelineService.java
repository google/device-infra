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

package com.google.devtools.mobileharness.infra.monitoring;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.protobuf.Message;
import java.time.Duration;

/** Periodically pipes data from a source to a sink. */
public class BatchPipelineService<T extends Message> extends AbstractScheduledService {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final DataPuller<T> puller;
  private final DataPusher pusher;

  // The period duration between two publish actions.
  private static final Duration PUBLISH_INTERVAL = Duration.ofMinutes(1);
  // Initial delay of publish actions.
  private static final Duration INITIAL_DELAY = Duration.ofMinutes(5);

  public BatchPipelineService(DataPuller<T> puller, DataPusher pusher) {
    this.puller = puller;
    this.pusher = pusher;
  }

  @Override
  protected void startUp() throws MobileHarnessException {
    puller.setUp();
    pusher.setUp();
  }

  @Override
  protected void runOneIteration() {
    try {
      pusher.push(puller.pull());
    } catch (MobileHarnessException | RuntimeException e) {
      logger.atWarning().withCause(e).log("Failed to send the data.");
    }
  }

  @Override
  protected void shutDown() throws MobileHarnessException {
    puller.tearDown();
    pusher.tearDown();
  }

  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedRateSchedule(
        INITIAL_DELAY.toSeconds(), PUBLISH_INTERVAL.toSeconds(), SECONDS);
  }
}
