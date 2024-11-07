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

package com.google.devtools.mobileharness.infra.client.api.mode.ats;

import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.Annotations.AtsModeAbstractScheduler;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.Annotations.AtsModeDeviceQuerier;
import com.google.devtools.mobileharness.infra.client.api.mode.ats.Annotations.JobSyncServiceVersionChecker;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.controller.scheduler.simple.SimpleScheduler;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoProvider;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.checker.ServiceSideVersionChecker;
import com.google.inject.AbstractModule;
import javax.inject.Singleton;

/** Module for {@code AtsMode}. */
public class AtsModeModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(LabInfoProvider.class).to(RemoteDeviceManager.class);
    bind(DeviceQuerier.class).annotatedWith(AtsModeDeviceQuerier.class).to(DeviceQuerierImpl.class);
    bind(AbstractScheduler.class)
        .annotatedWith(AtsModeAbstractScheduler.class)
        .to(SimpleScheduler.class)
        .in(Singleton.class);
    bind(ServiceSideVersionChecker.class)
        .annotatedWith(JobSyncServiceVersionChecker.class)
        .toInstance(
            new ServiceSideVersionChecker(Version.MASTER_V5_VERSION, Version.MIN_CLIENT_VERSION));
  }
}
