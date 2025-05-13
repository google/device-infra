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

import static com.google.common.collect.ImmutableList.toImmutableList;
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
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader.TradefedResultFilesBundle;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryArgs;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryGenerator;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** A creator to create XTS jobs for XTS console only. */
public class ConsoleJobCreator extends XtsJobCreator {

  private final PreviousResultLoader previousResultLoader;

  @Inject
  ConsoleJobCreator(
      SessionRequestHandlerUtil sessionRequestHandlerUtil,
      LocalFileUtil localFileUtil,
      PreviousResultLoader previousResultLoader,
      RetryGenerator retryGenerator,
      ModuleShardingArgsGenerator moduleShardingArgsGenerator) {
    super(sessionRequestHandlerUtil, localFileUtil, retryGenerator, moduleShardingArgsGenerator);

    this.previousResultLoader = previousResultLoader;
  }

  @Override
  protected void injectEnvSpecificProperties(
      SessionRequestInfo sessionRequestInfo, Map<String, String> driverParams, int jobDeviceCount) {
    driverParams.put("xts_root_dir", sessionRequestInfo.xtsRootDir());
  }

  @Override
  protected Optional<Path> getPrevSessionTestReportProperties(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException {
    return previousResultLoader.getPrevSessionTestReportProperties(
        XtsDirUtil.getXtsResultsDir(
            Path.of(sessionRequestInfo.xtsRootDir()), sessionRequestInfo.xtsType()),
        sessionRequestInfo.retrySessionIndex().orElse(null),
        sessionRequestInfo.retrySessionResultDirName().orElse(null));
  }

  @Override
  protected SubPlan prepareRunRetrySubPlan(SessionRequestInfo sessionRequestInfo, boolean forTf)
      throws MobileHarnessException {
    return prepareRunRetrySubPlan(
        Path.of(sessionRequestInfo.xtsRootDir()),
        sessionRequestInfo.xtsType(),
        sessionRequestInfo.retrySessionIndex().orElse(null),
        sessionRequestInfo.retrySessionResultDirName().orElse(null),
        sessionRequestInfo.retryType().orElse(null),
        sessionRequestInfo.includeFilters(),
        sessionRequestInfo.excludeFilters(),
        getNonTfModules(sessionRequestInfo.v2ConfigsMap()),
        forTf,
        sessionRequestInfo.moduleNames());
  }

  private SubPlan prepareRunRetrySubPlan(
      Path xtsRootDir,
      String xtsType,
      @Nullable Integer previousSessionIndex,
      @Nullable String previousSessionResultDirName,
      @Nullable RetryType retryType,
      ImmutableList<String> passedInIncludeFilters,
      ImmutableList<String> passedInExcludeFilters,
      ImmutableSet<String> allNonTradefedModules,
      boolean forTf,
      ImmutableList<String> passedInModules)
      throws MobileHarnessException {
    RetryArgs.Builder retryArgs =
        RetryArgs.builder()
            .setResultsDir(XtsDirUtil.getXtsResultsDir(xtsRootDir, xtsType))
            .setPassedInExcludeFilters(
                passedInExcludeFilters.stream()
                    .map(SuiteTestFilter::create)
                    .collect(toImmutableSet()))
            .setPassedInIncludeFilters(
                passedInIncludeFilters.stream()
                    .map(SuiteTestFilter::create)
                    .collect(toImmutableSet()))
            .setAllNonTfModules(allNonTradefedModules);
    if (previousSessionIndex != null) {
      retryArgs.setPreviousSessionIndex(previousSessionIndex);
    }
    if (previousSessionResultDirName != null) {
      retryArgs.setPreviousSessionResultDirName(previousSessionResultDirName);
    }
    if (retryType != null) {
      retryArgs.setRetryType(retryType);
    }
    if (!passedInModules.isEmpty()) {
      retryArgs.setPassedInModules(ImmutableSet.copyOf(passedInModules));
    }
    return generateRetrySubPlan(
        retryArgs.build(),
        forTf,
        previousSessionIndex != null ? String.valueOf(previousSessionIndex) : null,
        previousSessionResultDirName);
  }

  @Override
  protected void prepareTfRetry(
      SessionRequestInfo sessionRequestInfo,
      Map<String, String> driverParams,
      ImmutableMap.Builder<XtsPropertyName, String> extraJobProperties)
      throws MobileHarnessException {
    Optional<Path> testReportPropertiesFile =
        getPrevSessionTestReportProperties(sessionRequestInfo);
    if (testReportPropertiesFile.isPresent()) {
      Properties testReportProperties = loadTestReportProperties(testReportPropertiesFile.get());
      // If previous session doesn't have TF module, skip running TF retry.
      if (!Boolean.parseBoolean(
          testReportProperties.getProperty(SuiteCommon.TEST_REPORT_PROPERTY_HAS_TF_MODULE))) {
        throw MobileHarnessExceptionFactory.createUserFacingException(
            InfraErrorId.ATSC_TF_RETRY_WITHOUT_TF_MODULE,
            "Previous session doesn't have tradefed module",
            /* cause= */ null);
      }
      extraJobProperties
          .put(
              Job.PREV_SESSION_HAS_TF_MODULE,
              String.valueOf(
                  Boolean.parseBoolean(
                      testReportProperties.getProperty(
                          SuiteCommon.TEST_REPORT_PROPERTY_HAS_TF_MODULE))))
          .put(
              Job.PREV_SESSION_HAS_NON_TF_MODULE,
              String.valueOf(
                  Boolean.parseBoolean(
                      testReportProperties.getProperty(
                          SuiteCommon.TEST_REPORT_PROPERTY_HAS_NON_TF_MODULE))));
    }
    TradefedResultFilesBundle tfRunRetryFilesBundle =
        findTfRunRetryFilesBundle(
            Path.of(sessionRequestInfo.xtsRootDir()),
            sessionRequestInfo.xtsType(),
            sessionRequestInfo.retrySessionIndex().orElse(null),
            sessionRequestInfo.retrySessionResultDirName().orElse(null));
    driverParams.put(
        "prev_session_test_result_xml",
        tfRunRetryFilesBundle.testResultXml().toAbsolutePath().toString());
    driverParams.put(
        "prev_session_test_record_files",
        new Gson()
            .toJson(
                tfRunRetryFilesBundle.testRecordProtoFiles().stream()
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .collect(toImmutableList())));
    if (sessionRequestInfo.retryType().isPresent()) {
      driverParams.put("retry_type", sessionRequestInfo.retryType().get().toString());
    }
  }

  private TradefedResultFilesBundle findTfRunRetryFilesBundle(
      Path xtsRootDir,
      String xtsType,
      @Nullable Integer previousSessionIndex,
      @Nullable String previousSessionResultDirName)
      throws MobileHarnessException {
    return previousResultLoader
        .getPrevSessionResultFilesBundle(
            XtsDirUtil.getXtsResultsDir(xtsRootDir, xtsType),
            previousSessionIndex,
            previousSessionResultDirName)
        .orElseThrow(
            () ->
                new MobileHarnessException(
                    InfraErrorId.ATSC_RUN_RETRY_COMMAND_PREV_SESSION_MISS_RESULT_FILES,
                    String.format(
                        "Session %s misses test-record proto files and the test_result.xml file,"
                            + " not able to retry it",
                        previousSessionIndex)));
  }

  @Override
  protected Path prepareRunRetryTfSubPlanXmlFile(
      SessionRequestInfo sessionRequestInfo, SubPlan subPlan) throws MobileHarnessException {
    Path xtsSubPlansDir =
        XtsDirUtil.getXtsSubPlansDir(
            Path.of(sessionRequestInfo.xtsRootDir()), sessionRequestInfo.xtsType());

    return serializeRetrySubPlan(
        xtsSubPlansDir,
        subPlan,
        String.format(
            "#%s",
            sessionRequestInfo
                .retrySessionResultDirName()
                .orElseGet(
                    () -> String.valueOf(sessionRequestInfo.retrySessionIndex().orElseThrow()))));
  }
}
