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

package com.google.devtools.mobileharness.infra.controller.plugin;

import com.google.inject.AbstractModule;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.EventInjectionScope;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.RunnerEventScoped;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;

/** Module for common setup in {@code PluginCreator}. */
public class CommonSetupModule extends AbstractModule {

  @Override
  protected void configure() {
    bindScope(RunnerEventScoped.class, EventInjectionScope.instance);

    bind(TestInfo.class).toProvider(() -> null).in(RunnerEventScoped.class);

    bind(DeviceInfo.class).toProvider(() -> null).in(RunnerEventScoped.class);
    bind(DeviceLocator.class).toProvider(() -> null).in(RunnerEventScoped.class);

    bind(JobInfo.class).toProvider(() -> null).in(RunnerEventScoped.class);
  }
}
