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

package com.google.devtools.mobileharness.infra.ats.common.jobcreator;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName;
import com.google.devtools.mobileharness.infra.ats.common.plan.TestPlanParser;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryArgs;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryGenerator;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** A creator to create XTS jobs for ATS server only. */
public class ServerJobCreator extends XtsJobCreator {

  private final PreviousResultLoader previousResultLoader;

  @Inject
  ServerJobCreator(
      SessionRequestHandlerUtil sessionRequestHandlerUtil,
      LocalFileUtil localFileUtil,
      TestPlanParser testPlanParser,
      PreviousResultLoader previousResultLoader,
      RetryGenerator retryGenerator,
      ModuleShardingArgsGenerator moduleShardingArgsGenerator) {
    super(
        sessionRequestHandlerUtil,
        localFileUtil,
        testPlanParser,
        retryGenerator,
        moduleShardingArgsGenerator);

    this.previousResultLoader = previousResultLoader;
  }

  @Override
  protected void injectEnvSpecificProperties(
      SessionRequestInfo sessionRequestInfo, Map<String, String> driverParams) {
    driverParams.put("android_xts_zip", sessionRequestInfo.androidXtsZip().get());
    if (sessionRequestInfo.remoteRunnerFilePathPrefix().isPresent()
        && driverParams.containsKey("subplan_xml")) {
      driverParams.put(
          "subplan_xml",
          PathUtil.join(
              sessionRequestInfo.remoteRunnerFilePathPrefix().get(),
              PathUtil.makeRelative(
                  Flags.instance().atsStoragePath.getNonNull(), driverParams.get("subplan_xml"))));
    }
  }

  @Override
  protected Optional<Path> getPrevSessionTestReportProperties(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException {
    return previousResultLoader.getPrevSessionTestReportProperties(
        Path.of(sessionRequestInfo.retryResultDir().orElseThrow()));
  }

  @Override
  protected SubPlan prepareRunRetrySubPlan(SessionRequestInfo sessionRequestInfo, boolean forTf)
      throws MobileHarnessException {
    return prepareRunRetrySubPlan(
        sessionRequestInfo.retryResultDir().orElseThrow(),
        sessionRequestInfo.retrySessionId().orElseThrow(),
        sessionRequestInfo.retryType().orElse(null),
        sessionRequestInfo.includeFilters(),
        sessionRequestInfo.excludeFilters(),
        getNonTfModules(sessionRequestInfo.v2ConfigsMap()),
        forTf,
        sessionRequestInfo.moduleNames());
  }

  private SubPlan prepareRunRetrySubPlan(
      String retryResultDir,
      String previousSessionId,
      @Nullable RetryType retryType,
      ImmutableList<String> passedInIncludeFilters,
      ImmutableList<String> passedInExcludeFilters,
      ImmutableSet<String> allNonTradefedModules,
      boolean forTf,
      ImmutableList<String> passedInModules)
      throws MobileHarnessException {
    RetryArgs.Builder retryArgs =
        RetryArgs.builder()
            .setResultsDir(Path.of(retryResultDir))
            .setPreviousSessionId(previousSessionId)
            .setPassedInExcludeFilters(
                passedInExcludeFilters.stream()
                    .map(SuiteTestFilter::create)
                    .collect(toImmutableSet()))
            .setPassedInIncludeFilters(
                passedInIncludeFilters.stream()
                    .map(SuiteTestFilter::create)
                    .collect(toImmutableSet()))
            .setAllNonTfModules(allNonTradefedModules);

    if (retryType != null) {
      retryArgs.setRetryType(retryType);
    }
    if (!passedInModules.isEmpty()) {
      retryArgs.setPassedInModules(ImmutableSet.copyOf(passedInModules));
    }
    return generateRetrySubPlan(retryArgs.build(), forTf, previousSessionId);
  }

  @Override
  protected void prepareTfRetry(
      SessionRequestInfo sessionRequestInfo,
      Map<String, String> driverParams,
      ImmutableMap.Builder<XtsPropertyName, String> extraJobProperties)
      throws MobileHarnessException {
    throw MobileHarnessExceptionFactory.createUserFacingException(
        InfraErrorId.ATS_SERVER_USE_TF_RETRY_ERROR,
        "ATS server doesn't support TF retry.",
        /* cause= */ null);
  }

  @Override
  protected Path prepareRunRetryTfSubPlanXmlFile(
      SessionRequestInfo sessionRequestInfo, SubPlan subPlan) throws MobileHarnessException {
    Path xtsSubPlansDir = Path.of(sessionRequestInfo.xtsRootDir()).getParent();
    return serializeRetrySubPlan(
        xtsSubPlansDir, subPlan, sessionRequestInfo.retrySessionId().orElseThrow());
  }
}
