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

package com.google.devtools.mobileharness.infra.client.rbe;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.jobconfig.JobInfoCreator;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.inject.Guice;
import com.google.wireless.qa.mobileharness.client.bin.JobInitializer;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ExitCode;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfigs;
import com.google.wireless.qa.mobileharness.shared.util.FlagUtil;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/** OmniLab client entry point for RBE. */
public final class Client {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Env name for TEST_SRCDIR. */
  private static final String TEST_SRCDIR = System.getenv("TEST_SRCDIR");

  private ClientRunner clientRunner;

  @Inject
  Client(ClientRunner clientRunner) {
    this.clientRunner = clientRunner;
  }

  private Client() {}

  /** The entry point of OmniLab for RBE to run local tests. */
  public static void main(String[] args) throws InterruptedException {
    // Splits standard arguments and nonstandard flags from args.
    List<String> standardArgs = new ArrayList<>();
    List<String> nonstandardFlags = new ArrayList<>();
    FlagUtil.splitArgs(args, standardArgs, nonstandardFlags);
    // Parses flags.
    Flags.parse(args);

    Client client = Guice.createInjector(new ClientModule()).getInstance(Client.class);

    // MobileHarnessLogger.init();
    logger.atInfo().log(
        "Pesto Client started with the following args:\n%s", Joiner.on('\n').join(args));

    JobConfigs jobConfigs = null;
    try {
      JobConfigs.Builder builder = JobConfigs.newBuilder();
      JobInitializer.initializeJobConfigs()
          .getJobConfigList()
          .forEach(
              jobConfig ->
                  builder.addJobConfig(
                      jobConfig.toBuilder()
                          .setTestTimeoutSec(500)
                          .setStartTimeoutSec(500)
                          .setJobTimeoutSec(3600)));
      jobConfigs = builder.build();
    } catch (MobileHarnessException e) {
      new SystemUtil().exit(ExitCode.Client.JOB_INFO_ERROR, e);
    }
    List<JobInfo> jobInfos =
        JobInitializer.getJobInfos(
            jobConfigs.getJobConfigList(),
            nonstandardFlags,
            (jobConfig, nonstandardFlagsForMethod, genDirPath) ->
                JobInfoCreator.createJobInfo(jobConfig, nonstandardFlagsForMethod, TEST_SRCDIR),
            ImmutableMap.of(),
            ImmutableMap.of(),
            "pesto");
    new SystemUtil().exit(client.clientRunner.run(jobInfos));
  }
}
