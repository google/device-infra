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

package com.google.devtools.deviceinfra.shared.logging;

import com.google.devtools.deviceinfra.shared.logging.controller.LogEntryUploadManagerModule;
import com.google.devtools.deviceinfra.shared.logging.controller.handler.LogHandlerModule;
import com.google.devtools.deviceinfra.shared.logging.controller.queue.LogEntryQueueModule;
import com.google.devtools.deviceinfra.shared.logging.parameter.LogEnvironment;
import com.google.devtools.deviceinfra.shared.logging.parameter.LogManagerParameters;
import com.google.devtools.deviceinfra.shared.logging.parameter.LogProject;
import com.google.inject.AbstractModule;

/** Module for {@link UdclusterHostLogManager}. */
public class UdclusterHostLogManagerModule extends AbstractModule {
  private final LogManagerParameters logManagerParameters;

  public UdclusterHostLogManagerModule(LogManagerParameters logManagerParameters) {
    this.logManagerParameters = logManagerParameters;
  }

  @Override
  public void configure() {
    bind(LogProject.class).toInstance(logManagerParameters.logProject());
    bind(LogEnvironment.class).toInstance(logManagerParameters.logEnvironment());
    bind(LogManagerParameters.class).toInstance(logManagerParameters);
    install(new LogEntryQueueModule());
    install(new LogEntryUploadManagerModule(logManagerParameters.logUploaderParameters()));
    install(new LogHandlerModule(logManagerParameters.fileLogger().orElse(null)));
  }
}
