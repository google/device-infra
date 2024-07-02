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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.protobuf.Message;
import java.util.List;
import java.util.function.Consumer;

/** Pushes data to a sink. */
public abstract class DataPusher {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Sets up resources. */
  public abstract void setUp() throws MobileHarnessException;

  /** Cleans up resources. */
  public abstract void tearDown();

  /**
   * Pushes data to a sink.
   *
   * @param messageData, data to push.
   * @param successCallback, callback to handle success.
   * @param failureCallback, callback to handle failure.
   */
  public abstract <T extends Message> void push(
      List<T> messageData, Consumer<String> successCallback, Consumer<Throwable> failureCallback)
      throws MobileHarnessException;

  /** Pushes data and logs success and errors. */
  public <T extends Message> void push(List<T> messageData) throws MobileHarnessException {
    push(
        messageData,
        str -> logger.atInfo().log("Published with message id %s.", str),
        t -> logger.atWarning().withCause(t).log("Failed to publish."));
  }
}
