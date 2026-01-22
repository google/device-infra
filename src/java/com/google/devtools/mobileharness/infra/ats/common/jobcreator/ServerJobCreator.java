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
import static com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil.urlForWorkerResolve;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.DeviceActionConfigObject;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestEnvironment;
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.TradefedConfigGenerator;
import com.google.devtools.mobileharness.infra.ats.server.util.AtsServerSessionUtil;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.PreviousResultLoader.TradefedResultFilesBundle;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryArgs;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryGenerator;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.spec.TradefedTestSpec;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
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
              ? urlForWorkerResolve(sessionRequestInfo)
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
      ImmutableMap.Builder<XtsPropertyName, String> extraJobProperties,
      ListMultimap<String, String> jobFiles)
      throws MobileHarnessException {
    Optional<Path> testReportPropertiesFile =
        getPrevSessionTestReportProperties(sessionRequestInfo);
    if (testReportPropertiesFile.isPresent()) {
      Properties testReportProperties = loadTestReportProperties(testReportPropertiesFile.get());
      // If previous session doesn't have TF module, skip running TF retry.
      if (!Boolean.parseBoolean(
          testReportProperties.getProperty(SuiteCommon.TEST_REPORT_PROPERTY_HAS_TF_MODULE))) {
        throw MobileHarnessExceptionFactory.createUserFacingException(
            InfraErrorId.ATS_SERVER_TF_RETRY_WITHOUT_TF_MODULE,
            "Previous session doesn't have tradefed module",
            /* cause= */ null);
      }
    }
    TradefedResultFilesBundle tfRunRetryFilesBundle =
        findTfRunRetryFilesBundle(Path.of(sessionRequestInfo.retryResultDir().orElseThrow()));
    driverParams.put(
        "prev_session_test_result_xml",
        toAtsServerPaths(
                ImmutableList.of(tfRunRetryFilesBundle.testResultXml()),
                sessionRequestInfo.remoteRunnerFilePathPrefix().orElse(null))
            .get(0));
    // It uses jobFiles here instead of driverParams as driverParams doesn't work well with list of
    // files.
    jobFiles.putAll(
        TradefedTestSpec.TAG_PREV_SESSION_TEST_RECORD_PB_FILES,
        toAtsServerPaths(
            tfRunRetryFilesBundle.testRecordProtoFiles(),
            sessionRequestInfo.remoteRunnerFilePathPrefix().orElse(null)));
    if (sessionRequestInfo.retryType().isPresent()) {
      driverParams.put("retry_type", sessionRequestInfo.retryType().get().toString());
    }
  }

  private TradefedResultFilesBundle findTfRunRetryFilesBundle(Path retryResultDir)
      throws MobileHarnessException {
    return previousResultLoader
        .getPrevSessionResultFilesBundle(retryResultDir)
        .orElseThrow(
            () ->
                new MobileHarnessException(
                    InfraErrorId.ATS_SERVER_RUN_RETRY_COMMAND_PREV_SESSION_MISS_RESULT_FILES,
                    "Previous session misses test-record proto files and the test_result.xml file,"
                        + " not able to retry it"));
  }

  private ImmutableList<String> toAtsServerPaths(
      ImmutableList<Path> paths, @Nullable String remoteRunnerFilePathPrefix) {
    if (remoteRunnerFilePathPrefix == null) {
      return paths.stream()
          .map(Path::toAbsolutePath)
          .map(Path::toString)
          .collect(toImmutableList());
    }
    return paths.stream()
        .map(Path::toAbsolutePath)
        .map(Path::toString)
        .map(
            path ->
                PathUtil.join(
                    remoteRunnerFilePathPrefix,
                    PathUtil.makeRelative(Flags.instance().atsStoragePath.getNonNull(), path)))
        .collect(toImmutableList());
  }

  @Override
  protected Path prepareRunRetryTfSubPlanXmlFile(
      SessionRequestInfo sessionRequestInfo, SubPlan subPlan) throws MobileHarnessException {
    Path xtsSubPlansDir = Path.of(sessionRequestInfo.xtsRootDir()).getParent();
    return serializeRetrySubPlan(
        xtsSubPlansDir, subPlan, sessionRequestInfo.retrySessionId().orElseThrow());
  }

  /**
   * Revises {@link SessionRequestInfo} for dynamic job.
   *
   * <p>The dynamic download job's device action should be cleared, since those actions should be
   * executed in the static xts job that is triggered first.
   */
  @Override
  protected SessionRequestInfo reviseRequestInfoForDynamicJob(
      SessionRequestInfo sessionRequestInfo) {
    if (sessionRequestInfo.atsServerTestEnvironment().isPresent()) {
      // The dynamic download job's device action should be cleared, since those actions should
      // be executed in the static xts job that is triggered first.
      TestEnvironment testEnvironment = sessionRequestInfo.atsServerTestEnvironment().get();
      ImmutableList<DeviceActionConfigObject> resultReporters =
          testEnvironment.getDeviceActionConfigObjectsList().stream()
              .filter(
                  deviceActionConfigObject ->
                      deviceActionConfigObject.getType()
                          == DeviceActionConfigObject.DeviceActionConfigObjectType.RESULT_REPORTER)
              .collect(toImmutableList());
      TestEnvironment updatedTestEnvironment =
          testEnvironment.toBuilder()
              .clearDeviceActionConfigObjects()
              .addAllDeviceActionConfigObjects(resultReporters)
              .build();
      return sessionRequestInfo.toBuilder()
          .setAtsServerTestEnvironment(updatedTestEnvironment)
          .build();
    }
    return sessionRequestInfo;
  }
}
