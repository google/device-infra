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

import com.google.auto.value.AutoValue;
import com.google.common.eventbus.EventBus;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Settings for a direct test runner. */
@AutoValue
public abstract class DirectTestRunnerSetting {
  public static DirectTestRunnerSetting create(
      TestInfo testInfo,
      Allocation allocation,
      @Nullable EventBus globalInternalBus,
      @Nullable List<Object> internalPluginSubscribers,
      @Nullable List<Object> apiPluginSubscribers,
      @Nullable List<Object> jarPluginSubscribers) {
    return new AutoValue_DirectTestRunnerSetting(
        testInfo,
        allocation,
        Optional.ofNullable(globalInternalBus),
        Optional.ofNullable(internalPluginSubscribers),
        Optional.ofNullable(apiPluginSubscribers),
        Optional.ofNullable(jarPluginSubscribers));
  }

  /** Test info of the current runner. */
  public abstract TestInfo testInfo();

  /** Device allocation info of the current test. */
  public abstract Allocation allocation();

  /** EventBus for global Mobile Harness framework events. */
  public abstract Optional<EventBus> globalInternalBus();

  /** EventBus subscribers of the internal plugins. */
  public abstract Optional<List<Object>> internalPluginSubscribers();

  /** EventBus subscribers of the job plugins added via Client API. */
  public abstract Optional<List<Object>> apiPluginSubscribers();

  /** Event subscribers of "xxx_plugin_jar". */
  public abstract Optional<List<Object>> jarPluginSubscribers();
}
