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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.infra.ats.common.SessionHandlerHelper;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestHandlerUtil.TradefedJobInfo;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.infra.ats.common.plan.TestPlanParser;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryArgs;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryGenerator;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.gson.Gson;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

/** A creator to create XTS tradefed jobs and non tradefed jobs. */
public abstract class XtsJobCreator {

  private final SessionRequestHandlerUtil sessionRequestHandlerUtil;
  private final LocalFileUtil localFileUtil;
  private final TestPlanParser testPlanParser;
  private final RetryGenerator retryGenerator;
  private final ModuleShardingArgsGenerator moduleShardingArgsGenerator;

  protected XtsJobCreator(
      SessionRequestHandlerUtil sessionRequestHandlerUtil,
      LocalFileUtil localFileUtil,
      TestPlanParser testPlanParser,
      RetryGenerator retryGenerator,
      ModuleShardingArgsGenerator moduleShardingArgsGenerator) {
    this.sessionRequestHandlerUtil = sessionRequestHandlerUtil;
    this.localFileUtil = localFileUtil;
    this.testPlanParser = testPlanParser;
    this.retryGenerator = retryGenerator;
    this.moduleShardingArgsGenerator = moduleShardingArgsGenerator;
  }

  public static boolean isSkippableException(MobileHarnessException e) {
    return e.getErrorId() == InfraErrorId.XTS_NO_MATCHED_TRADEFED_MODULES
        || e.getErrorId() == InfraErrorId.XTS_NO_MATCHED_NON_TRADEFED_MODULES;
  }

  /**
   * Creates a tradefed job based on the {@code SessionRequestInfo}.
   *
   * @return a tradefed jobInfo.
   */
  public ImmutableList<JobInfo> createXtsTradefedTestJob(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    ImmutableList<String> tfModules =
        sessionRequestHandlerUtil.getFilteredTradefedModules(sessionRequestInfo);

    ImmutableList<TradefedJobInfo> tradefedJobInfoList =
        createXtsTradefedTestJobInfo(sessionRequestInfo, tfModules);

    ImmutableList.Builder<JobInfo> jobInfos = ImmutableList.builder();
    for (TradefedJobInfo tradefedJobInfo : tradefedJobInfoList) {
      JobInfo jobInfo =
          sessionRequestHandlerUtil.createXtsTradefedTestJob(sessionRequestInfo, tradefedJobInfo);
      jobInfos.add(jobInfo);
    }

    return jobInfos.build();
  }

  @VisibleForTesting
  ImmutableList<TradefedJobInfo> createXtsTradefedTestJobInfo(
      SessionRequestInfo sessionRequestInfo, ImmutableList<String> tfModules)
      throws MobileHarnessException, InterruptedException {
    String testPlan = sessionRequestInfo.testPlan();
    Path xtsRootDir = Path.of(sessionRequestInfo.xtsRootDir());
    String xtsType = sessionRequestInfo.xtsType();
    int shardCount = sessionRequestInfo.shardCount().orElse(0);
    ImmutableMap.Builder<XtsPropertyName, String> extraJobProperties = ImmutableMap.builder();

    Map<String, String> driverParams = new HashMap<>();
    driverParams.put("xts_type", xtsType);
    driverParams.put("xts_test_plan", testPlan);
    boolean prevSessionSkipDeviceInfo = false;
    if (SessionRequestHandlerUtil.isRunRetry(testPlan)) {
      extraJobProperties.put(Job.IS_RUN_RETRY, "true");
      if (SessionHandlerHelper.useTfRetry()) {
        prepareTfRetry(sessionRequestInfo, driverParams, extraJobProperties);
      } else {
        SubPlan runRetryTfSubPlan = prepareRunRetrySubPlan(sessionRequestInfo, /* forTf= */ true);
        driverParams.put(
            "prev_session_xts_test_plan", runRetryTfSubPlan.getPreviousSessionXtsTestPlan());
        injectBuildFingerprint(extraJobProperties, runRetryTfSubPlan);
        Path runRetryTfSubPlanXmlFile =
            prepareRunRetryTfSubPlanXmlFile(sessionRequestInfo, runRetryTfSubPlan);
        driverParams.put("subplan_xml", runRetryTfSubPlanXmlFile.toAbsolutePath().toString());
        prevSessionSkipDeviceInfo =
            runRetryTfSubPlan.getPreviousSessionDeviceBuildFingerprint().orElse("").isEmpty();
      }
    } else if (sessionRequestInfo.subPlanName().isPresent()) {
      Path tfSubPlan =
          prepareTfSubPlan(
              xtsRootDir, sessionRequestInfo.xtsType(), sessionRequestInfo.subPlanName().get());
      driverParams.put("subplan_xml", tfSubPlan.toAbsolutePath().toString());
    }

    if (!sessionRequestInfo.envVars().isEmpty()) {
      driverParams.put("env_vars", new Gson().toJson(sessionRequestInfo.envVars()));
    }
    if (sessionRequestInfo.testPlanFile().isPresent()) {
      driverParams.put("xts_test_plan_file", sessionRequestInfo.testPlanFile().get());
    }

    injectEnvSpecificProperties(sessionRequestInfo, driverParams);

    ImmutableList<String> shardCountArg =
        shardCount > 0
            ? ImmutableList.of(String.format("--shard-count %s", shardCount))
            : ImmutableList.of();

    Optional<String> testNameArg =
        sessionRequestInfo.testName().map((String value) -> String.format("-t %s", value));
    Optional<String> reportSystemCheckersArg =
        sessionRequestInfo.reportSystemCheckers()
            ? Optional.of("--report-system-checkers")
            : Optional.empty();
    Optional<String> skipDeviceInfoArg =
        prevSessionSkipDeviceInfo ? Optional.of("--skip-device-info true") : Optional.empty();
    if (sessionRequestInfo.skipDeviceInfo().isPresent()) {
      skipDeviceInfoArg =
          Optional.of(
              String.format("--skip-device-info %s", sessionRequestInfo.skipDeviceInfo().get()));
    }

    ImmutableSet<String> runCommandArgsSet;
    if (SessionRequestHandlerUtil.shouldEnableModuleSharding(sessionRequestInfo)) {
      runCommandArgsSet =
          moduleShardingArgsGenerator.generateShardingArgs(sessionRequestInfo, tfModules);
    } else {
      ImmutableList<String> extraArgs = sessionRequestInfo.extraArgs();
      String sessionRequestInfoArgs =
          Joiner.on(' ')
              .join(
                  Streams.concat(
                          // For "run retry" command, the given modules have been processed when
                          // generating the subplan above, no need to pass these again to underneath
                          // TF
                          (!SessionHandlerHelper.useTfRetry()
                                  && SessionRequestHandlerUtil.isRunRetry(testPlan)
                              ? Stream.empty()
                              : tfModules.stream().map(module -> String.format("-m %s", module))),
                          testNameArg.stream(),
                          shardCountArg.stream(),
                          // For "run retry" command, the passed in include filters and exclude
                          // filters are set in generated subplan, no need to set in TF command
                          // again.
                          !SessionHandlerHelper.useTfRetry()
                                  && SessionRequestHandlerUtil.isRunRetry(testPlan)
                              ? Stream.empty()
                              : sessionRequestInfo.includeFilters().stream()
                                  .map(
                                      includeFilter ->
                                          String.format("--include-filter \"%s\"", includeFilter)),
                          !SessionHandlerHelper.useTfRetry()
                                  && SessionRequestHandlerUtil.isRunRetry(testPlan)
                              ? Stream.empty()
                              : sessionRequestInfo.excludeFilters().stream()
                                  .map(
                                      excludeFilter ->
                                          String.format("--exclude-filter \"%s\"", excludeFilter)),
                          reportSystemCheckersArg.stream(),
                          skipDeviceInfoArg.stream(),
                          extraArgs.stream()
                              .map(arg -> arg.contains(" ") ? String.format("\"%s\"", arg) : arg))
                      .collect(toImmutableList()));
      runCommandArgsSet = ImmutableSet.of(sessionRequestInfoArgs);
    }

    if (runCommandArgsSet.isEmpty()) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.XTS_EMPTY_RUN_COMMAND_ARGS,
          "Failed to generate run command args to create jobs",
          /* cause= */ null);
    }

    ImmutableList.Builder<TradefedJobInfo> tradefedJobInfos = ImmutableList.builder();
    for (String runCommandArgs : runCommandArgsSet) {
      Map<String, String> driverParamsCopy = new HashMap<>(driverParams);
      if (!runCommandArgs.isEmpty()) {
        driverParamsCopy.put("run_command_args", runCommandArgs);
      }
      JobConfig jobConfig =
          sessionRequestHandlerUtil.initializeJobConfig(sessionRequestInfo, driverParamsCopy);
      tradefedJobInfos.add(TradefedJobInfo.of(jobConfig, extraJobProperties.buildOrThrow()));
    }

    return tradefedJobInfos.build();
  }

  /** Prepares a sub plan file for a tradefed job. */
  private Path prepareTfSubPlan(Path xtsRootDir, String xtsType, String subPlanName)
      throws MobileHarnessException, InterruptedException {
    Path subPlansDir = XtsDirUtil.getXtsSubPlansDir(xtsRootDir, xtsType);
    Path subPlanPath = subPlansDir.resolve(subPlanName + ".xml");
    SubPlan subPlan = SessionHandlerHelper.loadSubPlan(subPlanPath.toFile());

    if (subPlan.getIncludeFiltersMultimap().isEmpty()
        && subPlan.getExcludeFiltersMultimap().isEmpty()) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.OLCS_NO_CORRESPONDING_FILTER_FOUND_IN_SUBPLAN,
          "No include or exclude filters found for TF modules and tests",
          /* cause= */ null);
    }

    // If the subplan only includes TF modules and tests, use the subplan file directly
    if (subPlan.getNonTfIncludeFiltersMultimap().isEmpty()
        && subPlan.getNonTfExcludeFiltersMultimap().isEmpty()) {
      return subPlanPath;
    }

    Path tfOnlySubPlanPath = subPlansDir.resolve(String.format("%s_tf_auto_gen.xml", subPlanName));
    if (localFileUtil.isFileExist(tfOnlySubPlanPath)) {
      localFileUtil.removeFileOrDir(tfOnlySubPlanPath);
    }
    try (OutputStream outputStream = new FileOutputStream(tfOnlySubPlanPath.toFile())) {
      subPlan.serialize(outputStream, /* tfFiltersOnly= */ true);
    } catch (IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_SUBPLAN_COMMAND_WRITE_SUBPLAN_XML_ERROR,
          String.format("Failed to write the TF subplan xml file at %s", tfOnlySubPlanPath),
          e);
    }

    return tfOnlySubPlanPath;
  }

  /**
   * Creates non-tradefed jobs based on the {@code SessionRequestInfo}.
   *
   * @return a list of added non-tradefed jobInfos.
   */
  public ImmutableList<JobInfo> createXtsNonTradefedJobs(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    return createXtsNonTradefedJobs(
        sessionRequestInfo,
        testPlanParser.parseFilters(
            Path.of(sessionRequestInfo.xtsRootDir()),
            sessionRequestInfo.xtsType(),
            sessionRequestInfo.testPlan()));
  }

  @VisibleForTesting
  ImmutableList<JobInfo> createXtsNonTradefedJobs(
      SessionRequestInfo sessionRequestInfo, TestPlanParser.TestPlanFilter testPlanFilter)
      throws MobileHarnessException, InterruptedException {
    if (!sessionRequestHandlerUtil.canCreateNonTradefedJobs(sessionRequestInfo)) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.XTS_NO_MATCHED_NON_TRADEFED_MODULES,
          "No matched non-tradefed modules to create jobs",
          /* cause= */ null);
    }
    String testPlan = sessionRequestInfo.testPlan();
    Path xtsRootDir = Path.of(sessionRequestInfo.xtsRootDir());
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.OLCS_INEXISTENT_XTS_ROOT_DIR,
          String.format("xTS root dir [%s] doesn't exist", xtsRootDir),
          /* cause= */ null);
    }

    ImmutableMap.Builder<XtsPropertyName, String> extraJobProperties = ImmutableMap.builder();
    SubPlan subPlan = null;
    if (SessionRequestHandlerUtil.isRunRetry(testPlan)) {
      extraJobProperties.put(Job.IS_RUN_RETRY, "true");
      Optional<Path> testReportPropertiesFile =
          getPrevSessionTestReportProperties(sessionRequestInfo);
      if (testReportPropertiesFile.isPresent()) {
        Properties testReportProperties = loadTestReportProperties(testReportPropertiesFile.get());
        // If previous session doesn't have Non-TF module, skip the retry.
        if (!Boolean.parseBoolean(
            testReportProperties.getProperty(SuiteCommon.TEST_REPORT_PROPERTY_HAS_NON_TF_MODULE))) {
          throw MobileHarnessExceptionFactory.createUserFacingException(
              InfraErrorId.XTS_NO_MATCHED_NON_TF_MODULES_TO_RETRY,
              "Previous session doesn't have non-tradefed module",
              /* cause= */ null);
        }
        extraJobProperties.put(
            Job.PREV_SESSION_HAS_TF_MODULE,
            String.valueOf(
                Boolean.parseBoolean(
                    testReportProperties.getProperty(
                        SuiteCommon.TEST_REPORT_PROPERTY_HAS_TF_MODULE))));
        extraJobProperties.put(
            Job.PREV_SESSION_HAS_NON_TF_MODULE,
            String.valueOf(
                Boolean.parseBoolean(
                    testReportProperties.getProperty(
                        SuiteCommon.TEST_REPORT_PROPERTY_HAS_NON_TF_MODULE))));
      }
      subPlan = prepareRunRetrySubPlan(sessionRequestInfo, /* forTf= */ false);
      injectBuildFingerprint(extraJobProperties, subPlan);
    } else if (sessionRequestInfo.subPlanName().isPresent()) {
      subPlan =
          prepareNonTfSubPlan(
              xtsRootDir, sessionRequestInfo.xtsType(), sessionRequestInfo.subPlanName().get());
    }

    return sessionRequestHandlerUtil.createXtsNonTradefedJobs(
        sessionRequestInfo, testPlanFilter, subPlan, extraJobProperties.buildOrThrow());
  }

  /** Prepares a sub plan file for a non-tradefed job. */
  private SubPlan prepareNonTfSubPlan(Path xtsRootDir, String xtsType, String subPlanName)
      throws MobileHarnessException {
    SubPlan subPlan = SessionHandlerHelper.loadSubPlan(xtsRootDir, xtsType, subPlanName);
    if (subPlan.getNonTfIncludeFiltersMultimap().isEmpty()
        && subPlan.getNonTfExcludeFiltersMultimap().isEmpty()) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.OLCS_NO_CORRESPONDING_FILTER_FOUND_IN_SUBPLAN,
          "No include or exclude filters found for non-TF modules and tests",
          /* cause= */ null);
    }
    return subPlan;
  }

  private void injectBuildFingerprint(
      ImmutableMap.Builder<XtsPropertyName, String> extraJobPropertiesBuilder,
      SubPlan runRetrySubPlan) {
    String prevSessionDeviceBuildFingerprint =
        runRetrySubPlan.getPreviousSessionDeviceBuildFingerprint().orElse("");
    extraJobPropertiesBuilder.put(
        Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT, prevSessionDeviceBuildFingerprint);
    String prevSessionDeviceBuildFingerprintUnaltered =
        runRetrySubPlan.getPreviousSessionDeviceBuildFingerprintUnaltered().orElse("");
    extraJobPropertiesBuilder.put(
        Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT_UNALTERED,
        prevSessionDeviceBuildFingerprintUnaltered);
    String prevSessionDeviceVendorBuildFingerprint =
        runRetrySubPlan.getPreviousSessionDeviceVendorBuildFingerprint().orElse("");
    extraJobPropertiesBuilder.put(
        Job.PREV_SESSION_DEVICE_VENDOR_BUILD_FINGERPRINT, prevSessionDeviceVendorBuildFingerprint);
  }

  protected static Properties loadTestReportProperties(Path testReportPropertiesFile)
      throws MobileHarnessException {
    Properties properties = new Properties();
    try (InputStream inputStream = new FileInputStream(testReportPropertiesFile.toFile())) {
      properties.load(inputStream);
    } catch (IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_RETRY_COMMAND_TEST_REPORT_PROPERTIES_FILE_READ_ERROR,
          String.format("Failed to read test report properties file %s", testReportPropertiesFile),
          e);
    }
    return properties;
  }

  protected Path serializeRetrySubPlan(
      Path xtsSubPlansDir, SubPlan subPlan, String formattedPreviousSessionIdentifier)
      throws MobileHarnessException {
    localFileUtil.prepareDir(xtsSubPlansDir);
    Path subPlanPath =
        xtsSubPlansDir.resolve(
            String.format(
                "tf_retry_session_%s_%d.xml",
                formattedPreviousSessionIdentifier, Clock.systemUTC().millis()));
    try (OutputStream outputStream = new FileOutputStream(subPlanPath.toFile())) {
      subPlan.serialize(outputStream, /* tfFiltersOnly= */ true);
    } catch (IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_RETRY_COMMAND_PREPARE_SUBPLAN_ERROR,
          String.format(
              "Failed to write the subplan xml file when retrying session %s",
              formattedPreviousSessionIdentifier),
          e);
    }
    return subPlanPath;
  }

  protected SubPlan generateRetrySubPlan(
      RetryArgs retryArgs, boolean forTf, String previousSessionIdOrIndex)
      throws MobileHarnessException {
    SubPlan subPlan = retryGenerator.generateRetrySubPlan(retryArgs);
    if ((forTf
            && subPlan.getIncludeFiltersMultimap().isEmpty()
            && subPlan.getExcludeFiltersMultimap().isEmpty())
        || (!forTf
            && subPlan.getNonTfIncludeFiltersMultimap().isEmpty()
            && subPlan.getNonTfExcludeFiltersMultimap().isEmpty())) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.OLCS_NO_FILTER_FOUND_IN_RETRY_SUBPLAN,
          String.format(
              "No include or exclude filters found for %s retry session %s with retry type %s",
              forTf ? "TF" : "Non-TF",
              previousSessionIdOrIndex,
              retryArgs.retryType().isPresent() ? retryArgs.retryType().get() : "UNKNOWN"),
          /* cause= */ null);
    }
    return subPlan;
  }

  protected static ImmutableSet<String> getNonTfModules(
      ImmutableMap<String, Configuration> configsMap) {
    return configsMap.values().stream()
        .map(config -> config.getMetadata().getXtsModule())
        .collect(toImmutableSet());
  }

  /**
   * Prepares retry parameters/properties for Tradefed.
   *
   * @return true if params/properties are prepared successfully.
   */
  protected abstract void prepareTfRetry(
      SessionRequestInfo sessionRequestInfo,
      Map<String, String> driverParams,
      ImmutableMap.Builder<XtsPropertyName, String> extraJobProperties)
      throws MobileHarnessException;

  protected abstract Optional<Path> getPrevSessionTestReportProperties(
      SessionRequestInfo sessionRequestInfo) throws MobileHarnessException;

  protected abstract void injectEnvSpecificProperties(
      SessionRequestInfo sessionRequestInfo, Map<String, String> driverParams);

  protected abstract Path prepareRunRetryTfSubPlanXmlFile(
      SessionRequestInfo sessionRequestInfo, SubPlan subPlan) throws MobileHarnessException;

  protected abstract SubPlan prepareRunRetrySubPlan(
      SessionRequestInfo sessionRequestInfo, boolean forTf) throws MobileHarnessException;
}
