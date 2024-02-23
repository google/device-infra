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

package com.google.devtools.mobileharness.infra.controller.plugin.loader;

import static com.google.common.collect.Iterables.concat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.ProvisionException;

/** Instantiator for instantiating plugins. */
public class PluginInstantiator {

  /** Instantiates a plugin instance. */
  public static Object instantiatePlugin(
      Class<?> pluginClass,
      Iterable<Class<? extends Module>> moduleClasses,
      Iterable<Module> modules)
      throws MobileHarnessException {
    // Instantiates plugin module instances.
    ImmutableList.Builder<Module> newModules = ImmutableList.builder();
    for (Class<? extends Module> moduleClass : moduleClasses) {
      try {
        newModules.add(Guice.createInjector(modules).getInstance(moduleClass));
      } catch (CreationException | ProvisionException e) {
        throw new MobileHarnessException(
            BasicErrorId.PLUGIN_LOADER_FAILED_TO_CREATE_PLUGIN_MODULE_INSTANCE,
            String.format("Failed to create plugin module instance [%s]", moduleClass.getName()),
            e);
      }
    }

    // Instantiates the plugin.
    try {
      return Guice.createInjector(concat(modules, newModules.build())).getInstance(pluginClass);
    } catch (CreationException | ProvisionException e) {
      throw new MobileHarnessException(
          BasicErrorId.PLUGIN_LOADER_FAILED_TO_CREATE_PLUGIN_INSTANCE,
          String.format("Failed to create plugin instance [%s]", pluginClass.getName()),
          e);
    }
  }

  private PluginInstantiator() {}
}
