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

package com.google.devtools.mobileharness.infra.lab.controller;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.lab.common.dir.DirUtil;
import com.google.devtools.mobileharness.shared.util.concurrent.ServiceModule;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.nio.file.Path;

/** Module for monitoring GCS. */
public class GcsMonitorModule extends AbstractModule {
  @Override
  protected void configure() {
    install(ServiceModule.forService(GcsMonitor.class));
  }

  @Provides
  GcsMonitor provideGcsMonitor() throws MobileHarnessException, InterruptedException {
    return new GcsMonitor(Path.of(DirUtil.getCloudReceivedDir()));
  }
}
