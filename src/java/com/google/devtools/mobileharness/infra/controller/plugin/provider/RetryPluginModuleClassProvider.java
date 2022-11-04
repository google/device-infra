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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * {@link PluginModuleClassProvider} that will retry on different {@link PluginModuleClassProvider}s
 * until one gives back a non empty list.
 */
public class RetryPluginModuleClassProvider implements PluginModuleClassProvider {

  private final Collection<PluginModuleClassProvider> providers;

  /**
   * @param providers The collection of providers to try in order.
   */
  public RetryPluginModuleClassProvider(Collection<PluginModuleClassProvider> providers) {
    this.providers = providers;
  }

  /**
   * @param providers The collection of providers to try in order.
   */
  public RetryPluginModuleClassProvider(PluginModuleClassProvider... providers) {
    this(Arrays.asList(providers));
  }

  @Override
  public Set<Class<? extends Module>> getPluginModuleClasses() throws MobileHarnessException {
    for (PluginModuleClassProvider provider : providers) {
      Set<Class<? extends Module>> classes = provider.getPluginModuleClasses();
      if (!classes.isEmpty()) {
        return classes;
      }
    }

    return Collections.emptySet();
  }
}
