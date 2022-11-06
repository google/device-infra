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

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.PluginModule;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.reflections.Reflections;

/** Provides plugin module classes that have been annotated with the given {@link PluginType}s. */
public class AnnotatedPluginModuleClassProvider implements PluginModuleClassProvider {

  private final Reflections reflections;
  @Nullable private final LogCollector<?> log;
  private final boolean warnUnmatchedTypes;
  private final ImmutableSet<PluginType> pluginTypes;

  /**
   * @param reflections The reflection object ot use when finding the classes.
   * @param pluginTypes The types of plugin to allow.
   */
  public AnnotatedPluginModuleClassProvider(
      Reflections reflections,
      @Nullable LogCollector<?> log,
      boolean warnUnmatchedTypes,
      PluginType... pluginTypes) {
    this.reflections = reflections;
    this.log = log;
    this.warnUnmatchedTypes = warnUnmatchedTypes;
    this.pluginTypes = ImmutableSet.copyOf(pluginTypes);
  }

  @Override
  public Set<Class<? extends Module>> getPluginModuleClasses() throws MobileHarnessException {
    try {
      return reflections.getTypesAnnotatedWith(PluginModule.class).stream()
          .filter(
              aClass ->
                  AnnotatedPluginClassProvider.checkPluginType(
                      aClass,
                      clazz -> clazz.getDeclaredAnnotation(PluginModule.class).type(),
                      log,
                      warnUnmatchedTypes,
                      pluginTypes))
          .<Class<? extends Module>>map(aClass -> aClass.asSubclass(Module.class))
          .collect(Collectors.toSet());
    } catch (ClassCastException e) {
      throw new MobileHarnessException(
          ErrorCode.PLUGIN_ERROR, "Found class that is not a module marked with PluginModule.", e);
    }
  }
}
