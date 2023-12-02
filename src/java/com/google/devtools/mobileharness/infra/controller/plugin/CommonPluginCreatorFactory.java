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
import com.google.inject.Module;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.EventInjectionScope;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.RunnerEventScoped;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import java.util.Collection;
import javax.annotation.Nullable;
import org.apache.commons.lang3.ArrayUtils;

/** {@link PluginCreator.Factory} that will load in common resources for plugins on load. */
public class CommonPluginCreatorFactory implements PluginCreator.Factory {

  @Override
  public PluginCreator create(
      Collection<String> jarPaths,
      @Nullable Collection<String> classNames,
      @Nullable Collection<String> moduleClassNames,
      @Nullable String forceLoadFromJarClassRegex,
      PluginType pluginType,
      @Nullable LogCollector<?> log,
      Module... systemModules) {
    return new PluginCreator(
        jarPaths,
        classNames,
        moduleClassNames,
        forceLoadFromJarClassRegex,
        pluginType,
        log,
        ArrayUtils.add(systemModules, new CommonSetupModule()));
  }

  static class CommonSetupModule extends AbstractModule {

    @Override
    protected void configure() {
      super.configure();

      bindScope(RunnerEventScoped.class, EventInjectionScope.instance);

      bind(TestInfo.class).toProvider(() -> null).in(RunnerEventScoped.class);
      bind(com.google.wireless.qa.mobileharness.shared.api.job.TestInfo.class)
          .toProvider(() -> null)
          .in(RunnerEventScoped.class);

      bind(DeviceInfo.class).toProvider(() -> null).in(RunnerEventScoped.class);
      bind(DeviceLocator.class).toProvider(() -> null).in(RunnerEventScoped.class);

      bind(JobInfo.class).toProvider(() -> null).in(RunnerEventScoped.class);
      bind(com.google.wireless.qa.mobileharness.shared.api.job.JobInfo.class)
          .toProvider(() -> null)
          .in(RunnerEventScoped.class);

      bind(Device.class).toProvider(() -> null).in(RunnerEventScoped.class);
    }
  }
}
