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
import com.google.common.flogger.FluentLogger;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.reflections.Reflections;

/** Provides plugin classes that have been annotated with the given {@link PluginType}s. */
public class AnnotatedPluginClassProvider implements PluginClassProvider {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Reflections reflections;
  @Nullable private final LogCollector<?> log;
  private final boolean warnUnmatchedTypes;
  private final ImmutableSet<PluginType> pluginTypes;

  /**
   * @param reflections The reflection object ot use when finding the classes.
   * @param pluginTypes The types of plugin to allow.
   */
  public AnnotatedPluginClassProvider(
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
  public Set<Class<?>> getPluginClasses() {
    return reflections.getTypesAnnotatedWith(Plugin.class).stream()
        .filter(
            aClass ->
                checkPluginType(
                    aClass,
                    clazz -> clazz.getDeclaredAnnotation(Plugin.class).type(),
                    log,
                    warnUnmatchedTypes,
                    pluginTypes))
        .collect(Collectors.toSet());
  }

  static boolean checkPluginType(
      Class<?> aClass,
      Function<Class<?>, PluginType> pluginTypeGetter,
      @Nullable LogCollector<?> log,
      boolean warnUnmatchedTypes,
      ImmutableSet<PluginType> pluginTypes) {
    PluginType pluginType = pluginTypeGetter.apply(aClass);
    if (pluginTypes.contains(pluginType)) {
      return true;
    } else {
      if (log != null && warnUnmatchedTypes) {
        log.atWarning()
            .alsoTo(logger)
            .log(
                "Plugin class [%s] is found when loading plugins whose type is in %s"
                    + " but its type is %s. Do you %s",
                aClass.getName(),
                pluginTypes,
                pluginType,
                PluginType.UNSPECIFIED.equals(pluginType)
                    ? "forget to add PluginType to the @Plugin annotation like"
                        + " @Plugin(type = CLIENT) or @Plugin(type = LAB)?"
                    : "specify a wrong plugin jar? If the class is depended by other"
                        + " plugins, you can ignore this warning.");
      }
      return false;
    }
  }
}
