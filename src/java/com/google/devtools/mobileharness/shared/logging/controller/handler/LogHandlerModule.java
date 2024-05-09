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

package com.google.devtools.mobileharness.shared.logging.controller.handler;

import com.google.devtools.mobileharness.shared.logging.controller.queue.LogEntryQueue;
import com.google.devtools.mobileharness.shared.logging.util.LogEntryUtil;
import com.google.inject.AbstractModule;
import javax.annotation.Nullable;

/** Module for providing all related log {@link java.util.logging.Handler}s. */
public class LogHandlerModule extends AbstractModule {

  @Nullable private final String logFileDir;

  public LogHandlerModule(@Nullable String logFileDir) {
    this.logFileDir = logFileDir;
  }

  @Override
  public void configure() {
    requireBinding(LogEntryUtil.class);
    requireBinding(LogEntryQueue.class);
    install(new LocalFileHandlerModule(logFileDir));
  }
}
