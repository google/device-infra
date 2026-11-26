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

package com.google.devtools.mobileharness.infra.controller.plugin.provider;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Provides plugin classes from a list of canonical class names provided. */
public class NamedPluginClassProvider implements PluginClassProvider {

  private final ImmutableList<String> classNames;
  private final ClassLoader classLoader;
  private final ImmutableSet<PluginType> allowedPluginTypes;

  /**
   * @param classNames The canonical class names of the plugin classes.
   * @param classLoader What to use to load the classes.
   * @param allowedPluginTypes Allowed {@link PluginType} values on {@link Plugin}; if empty, any
   *     class is accepted without annotation checking.
   */
  public NamedPluginClassProvider(
      Collection<String> classNames,
      ClassLoader classLoader,
      Collection<PluginType> allowedPluginTypes) {
    this.classNames = ImmutableList.copyOf(classNames);
    this.classLoader = classLoader;
    this.allowedPluginTypes = ImmutableSet.copyOf(allowedPluginTypes);
  }

  @Override
  public Set<Class<?>> getPluginClasses() throws MobileHarnessException {
    Set<Class<?>> pluginClasses = new HashSet<>();

    for (String className : classNames) {
      Class<?> pluginClass =
          AnnotatedPluginClassProvider.loadClass(
              className, classLoader, BasicErrorId.PLUGIN_LOADER_FAILED_TO_CREATE_PLUGIN_INSTANCE);
      if (!allowedPluginTypes.isEmpty()) {
        Plugin plugin = pluginClass.getDeclaredAnnotation(Plugin.class);
        if (plugin == null) {
          throw new MobileHarnessException(
              BasicErrorId.PLUGIN_LOADER_FAILED_TO_CREATE_PLUGIN_INSTANCE,
              String.format("Class [%s] is not annotated with @Plugin.", className));
        }
        if (!allowedPluginTypes.contains(plugin.type())) {
          throw new MobileHarnessException(
              BasicErrorId.PLUGIN_LOADER_FAILED_TO_CREATE_PLUGIN_INSTANCE,
              String.format(
                  "Class [%s] has @Plugin(type = %s), which is not allowed (expected one of %s).",
                  className, plugin.type(), allowedPluginTypes));
        }
      }
      pluginClasses.add(pluginClass);
    }

    return pluginClasses;
  }
}
