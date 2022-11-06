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

import com.google.inject.Module;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import java.util.Set;

/** Provides a list of classes to use as {@link Module}s for plugins. */
public interface PluginModuleClassProvider {
  /** Gets the list of plugin module classes. */
  Set<Class<? extends Module>> getPluginModuleClasses() throws MobileHarnessException;
}
