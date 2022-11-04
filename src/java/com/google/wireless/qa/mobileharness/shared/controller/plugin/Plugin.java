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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Flag annotation for marking a Mobile Harness plugin class. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Plugin {

  /** Types of a Mobile Harness plugin. */
  enum PluginType {

    /** A plugin whose type is not specified. */
    UNSPECIFIED,

    /** Lab plugin. */
    LAB,

    /** Client plugin. */
    CLIENT,

    /**
     * Gateway plugin.
     *
     * @see <a href="http://go/mh-gateway">Mobile Harness Stubby API</a>
     */
    GATEWAY,

    /**
     * Forge-on-Mac plugin.
     *
     * @see <a href="http://go/fom-plugin-v2">Forge-on-Mac Plugin</a>
     */
    FOM,
  }

  /**
   * The type of the plugin to control where the plugin will be loaded.
   *
   * <p>The default value is {@linkplain PluginType#UNSPECIFIED UNSPECIFIED} but <b>you should
   * always specify another value</b>.
   */
  PluginType type() default PluginType.UNSPECIFIED;
}
