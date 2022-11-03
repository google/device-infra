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

package com.google.devtools.mobileharness.infra.controller.test;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.controller.test.DirectTestRunner.EventScope;
import java.util.List;

/** Plugin loading result of the test */
@AutoValue
public abstract class PluginLoadingResult {

  public abstract ImmutableList<PluginItem<?>> pluginItems();

  public static PluginLoadingResult create(List<PluginItem<?>> pluginItems) {
    return new AutoValue_PluginLoadingResult(ImmutableList.copyOf(pluginItems));
  }

  /** A plugin item used for the plugin loading result */
  @AutoValue
  public abstract static class PluginItem<T> {

    public abstract T plugin();

    public abstract EventScope scope();

    public static <T> PluginItem<T> create(T plugin, EventScope scope) {
      return new AutoValue_PluginLoadingResult_PluginItem<>(plugin, scope);
    }
  }

  public List<PluginItem<?>> getAll() {
    return pluginItems();
  }

  @SuppressWarnings("unchecked")
  public <T> List<PluginItem<T>> searchByClass(Class<T> pluginClass) {
    return (List<PluginItem<T>>)
        (List<? extends PluginItem<?>>)
            pluginItems().stream()
                .filter(pluginItem -> pluginClass.isInstance(pluginItem.plugin()))
                .collect(toImmutableList());
  }
}
