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

package com.google.devtools.mobileharness.shared.logging;

import com.google.devtools.mobileharness.shared.logging.controller.LogEntryUploadManager;
import com.google.devtools.mobileharness.shared.logging.controller.handler.Annotations.LoggerHandler;
import com.google.devtools.mobileharness.shared.logging.controller.handler.MobileHarnessLogHandler;
import com.google.devtools.mobileharness.shared.util.concurrent.ServiceModule;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import java.util.logging.Handler;

/** Module for starting the Mobile Harness host log service. */
public final class MobileHarnessHostLogServiceModule extends AbstractModule {
  @Override
  protected void configure() {
    install(ServiceModule.forService(LogEntryUploadManager.class));

    Multibinder.newSetBinder(binder(), Handler.class, LoggerHandler.class)
        .addBinding()
        .to(MobileHarnessLogHandler.class);
  }
}
