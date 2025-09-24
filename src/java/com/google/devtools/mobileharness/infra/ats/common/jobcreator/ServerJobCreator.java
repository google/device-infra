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
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName;
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.TradefedConfigGenerator;
import com.google.devtools.mobileharness.infra.ats.server.util.AtsServerSessionUtil;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryArgs;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryGenerator;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.xmlpull.v1.XmlPullParserException;

/** A creator to create XTS jobs for ATS server only. */
public class ServerJobCreator extends XtsJobCreator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PreviousResultLoader previousResultLoader;
  private final AtsServerSessionUtil atsServerSessionUtil;

  @Inject
  ServerJobCreator(
      SessionRequestHandlerUtil sessionRequestHandlerUtil,
      LocalFileUtil localFileUtil,
      PreviousResultLoader previousResultLoader,
      RetryGenerator retryGenerator,
      ModuleShardingArgsGenerator moduleShardingArgsGenerator,
      AtsServerSessionUtil atsServerSessionUtil) {
    super(sessionRequestHandlerUtil, localFileUtil, retryGenerator, moduleShardingArgsGenerator);

    this.previousResultLoader = previousResultLoader;
    this.atsServerSessionUtil = atsServerSessionUtil;
  }

  @Override
  protected void injectEnvSpecificProperties(
      SessionRequestInfo sessionRequestInfo, Map<String, String> driverParams, int jobDeviceCount)
      throws InterruptedException, MobileHarnessException {
    if (atsServerSessionUtil.isLocalMode()) {
      driverParams.put("xts_root_dir", sessionRequestInfo.xtsRootDir());
    } else {
      Optional<String> findAndroidXtsZip =
          Flags.instance().enablePersistentCache.getNonNull()
              ? sessionRequestInfo.androidXtsZipDownloadUrl()
              : Optional.empty();
      findAndroidXtsZip = findAndroidXtsZip.or(() -> sessionRequestInfo.androidXtsZip());
      findAndroidXtsZip.ifPresent(url -> driverParams.put("android_xts_zip", url));
    }
    if (sessionRequestInfo.remoteRunnerFilePathPrefix().isPresent()
        && driverParams.containsKey("subplan_xml")) {
      driverParams.put(
          "subplan_xml",
          PathUtil.join(
              sessionRequestInfo.remoteRunnerFilePathPrefix().get(),
              PathUtil.makeRelative(
                  Flags.instance().atsStoragePath.getNonNull(), driverParams.get("subplan_xml"))));
    }
    if (sessionRequestInfo.atsServerTestEnvironment().isPresent()) {
      generateTradefedTestConfigFile(sessionRequestInfo, driverParams, jobDeviceCount);
    } else {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.ATS_SERVER_MISSING_TEST_ENVIRONMENT_ERROR,
          "Test environment is missing in request. ATS server needs test environment to generate TF"
              + " test config.",
          /* cause= */ null);
    }
  }

  private void generateTradefedTestConfigFile(
      SessionRequestInfo sessionRequestInfo, Map<String, String> driverParams, int jobDeviceCount)
      throws MobileHarnessException {
    // Generate XML test config template for ClusterCommandLauncher.
    Path commandPath = Path.of(sessionRequestInfo.xtsRootDir()).resolveSibling("command.xml");
    try (OutputStream outputStream = new FileOutputStream(commandPath.toFile())) {
      TradefedConfigGenerator.generateXml(
          outputStream,
          sessionRequestInfo.atsServerTestEnvironment().get(),
          sessionRequestInfo.atsServerTestResources(),
          SessionRequestHandlerUtil.shouldEnableModuleSharding(sessionRequestInfo)
              ? 1
              : jobDeviceCount);
    } catch (IOException | XmlPullParserException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATS_SERVER_FAILED_TO_GENERATE_XML_TEST_CONFIG,
          "Failed to create XML test config for session",
          e);
    }
    logger.atInfo().log("Generate TF config:\n%s", localFileUtil.readFile(commandPath));
    if (sessionRequestInfo.remoteRunnerFilePathPrefix().isPresent()) {
      driverParams.put(
          "xts_test_plan_file",
          PathUtil.join(
              sessionRequestInfo.remoteRunnerFilePathPrefix().get(),
              PathUtil.makeRelative(
                  Flags.instance().atsStoragePath.getNonNull(), commandPath.toString())));
    } else {
      driverParams.put("xts_test_plan_file", commandPath.toString());
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
    return generateRetrySubPlan(
        retryArgs.build(), forTf, previousSessionId, /* previousSessionResultDirName= */ null);
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
