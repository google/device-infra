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

package com.google.devtools.mobileharness.infra.ats.common.sessionorchestrator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.Optional;

/**
 * Delegate providing environment-specific job provisioning and sharding configuration for {@code
 * AtsSessionOrchestrator}.
 *
 * <p>Implemented directly by entry session plugins (e.g. {@code AtsConsoleSessionPlugin} and {@code
 * AtsServerSessionPlugin}).
 */
public interface SessionOrchestratorDelegate {

  /**
   * Creates the setup job if required by the command.
   *
   * @return the setup {@link JobInfo}, or {@link Optional#empty()} if no setup is needed
   */
  Optional<JobInfo> createSetupJob() throws MobileHarnessException, InterruptedException;

  /**
   * Creates the main Tradefed jobs.
   *
   * @param dynamicMctsModules canonical list of dynamic MCTS modules downloaded in setup job
   * @param skipDynamicMctsJob true if dynamic MCTS should be skipped (e.g. no preloaded mainline)
   * @return list of main Tradefed {@link JobInfo} instances
   */
  ImmutableList<JobInfo> createTradefedJobs(
      ImmutableSet<String> dynamicMctsModules, boolean skipDynamicMctsJob)
      throws MobileHarnessException, InterruptedException;

  /**
   * Creates the main non-Tradefed (e.g., Mobly) jobs.
   *
   * @return list of main non-Tradefed {@link JobInfo} instances
   */
  ImmutableList<JobInfo> createNonTradefedJobs()
      throws MobileHarnessException, InterruptedException;

  /**
   * Creates the teardown job if required by the command.
   *
   * @return the teardown {@link JobInfo}, or {@link Optional#empty()} if no teardown is needed
   */
  Optional<JobInfo> createTeardownJob() throws MobileHarnessException, InterruptedException;

  /**
   * Returns the effective {@link ShardingMode} for this session ({@link ShardingMode#MODULE} or
   * {@link ShardingMode#RUNNER}).
   *
   * <p>When {@link ShardingMode#MODULE}, Tradefed jobs are scheduled concurrently across available
   * devices. When {@link ShardingMode#RUNNER}, jobs are executed sequentially with device pinning.
   */
  ShardingMode getEffectiveShardingMode();
}
