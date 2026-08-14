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

/*
 * Copyright 2026 Google LLC
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

package com.google.devtools.mobileharness.infra.controller.plugin.subprocess;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.util.Collection;
import javax.annotation.Nullable;

/** Loader that manages the subprocess plugin launcher and provides the event subscriber proxy. */
public final class SubprocessPluginLoader implements AutoCloseable {

  private final SubprocessPluginLauncher launcher;
  private final SubprocessPluginSubscriber subscriber;

  public SubprocessPluginLoader(
      Collection<String> pluginJarPaths,
      @Nullable Collection<String> pluginClasses,
      @Nullable String customWorkerRunnerJar) {
    this(pluginJarPaths, pluginClasses, customWorkerRunnerJar, /* hermeticWorkerBinary= */ null);
  }

  public SubprocessPluginLoader(
      Collection<String> pluginJarPaths,
      @Nullable Collection<String> pluginClasses,
      @Nullable String customWorkerRunnerJar,
      @Nullable String hermeticWorkerBinary) {
    this.launcher =
        new SubprocessPluginLauncher(
            pluginJarPaths, pluginClasses, customWorkerRunnerJar, hermeticWorkerBinary);
    this.subscriber = new SubprocessPluginSubscriber(launcher);
  }

  /** Starts the subprocess worker. */
  public void load() throws MobileHarnessException {
    launcher.start();
  }

  /** Returns the subscriber object to register with the host's EventBus. */
  public Object getSubscriber() {
    return subscriber;
  }

  @Override
  public void close() {
    launcher.close();
  }
}
