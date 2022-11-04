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

package com.google.wireless.qa.mobileharness.shared.controller.plugin;

import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for specifying a classes is a {@link com.google.inject.Module} to be used with {@link
 * Plugin} creation. These classes will be loaded first to allow specifying bindings to be injected
 * into the {@link Plugin} object creation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginModule {
  /**
   * The type of the plugin to control where the plugin will be loaded.
   *
   * <p>The default value is {@linkplain PluginType#UNSPECIFIED UNSPECIFIED} but <b>you should
   * always specify another value</b>.
   */
  PluginType type() default PluginType.UNSPECIFIED;
}
