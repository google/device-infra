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

package com.google.devtools.mobileharness.infra.client.api.controller.job;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import java.util.concurrent.ExecutorService;

/** Factory for creating {@link JobManagerCore}. */
public interface JobManagerCoreFactory {

  JobManagerCore create(
      ExecutorService jobThreadPool,
      EventBus globalInternalBus,
      ImmutableList<Object> jobInternalPlugins);

  /** Default factory for creating {@link JobManagerCore}. */
  class DefaultJobManagerCoreFactory implements JobManagerCoreFactory {

    @Override
    public JobManagerCore create(
        ExecutorService jobThreadPool,
        EventBus globalInternalBus,
        ImmutableList<Object> jobInternalPlugins) {
      return new JobManagerCore(jobThreadPool, globalInternalBus, jobInternalPlugins);
    }
  }
}
