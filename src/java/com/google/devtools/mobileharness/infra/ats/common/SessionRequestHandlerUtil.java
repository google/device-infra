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

package com.google.devtools.mobileharness.infra.ats.common;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.primitives.Ints.saturatedCast;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;
import static com.google.protobuf.TextFormat.shortDebugString;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfoFactory;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionGenDir;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionTempDir;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.xts.common.util.AbiUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ModuleConfigurationHelper;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Device;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper.DeviceInfo;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryArgs;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryGenerator;
import com.google.devtools.mobileharness.platform.android.xts.suite.retry.RetryType;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.jobconfig.JobInfoCreator;
import com.google.gson.Gson;
import com.google.inject.Provider;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Priority;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.DeviceList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.Driver;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Helper class for ATS applications to create job config. */
public class SessionRequestHandlerUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String ANDROID_REAL_DEVICE_TYPE = "AndroidRealDevice";
  private static final String ANDROID_DEVICE_TYPE = "AndroidDevice";
  private static final Pattern MODULE_PARAMETER_PATTERN =
      Pattern.compile(".*\\[(?<moduleParam>.*)]$");

  private final DeviceQuerier deviceQuerier;
  private final LocalFileUtil localFileUtil;
  private final ConfigurationUtil configurationUtil;
  private final ModuleConfigurationHelper moduleConfigurationHelper;
  private final CertificationSuiteInfoFactory certificationSuiteInfoFactory;
  private final RetryGenerator retryGenerator;
  private final Provider<AndroidAdbInternalUtil> androidAdbInternalUtilProvider;
  private final Provider<AndroidAdbUtil> androidAdbUtilProvider;
  private final Path sessionGenDir;
  private final Path sessionTempDir;
  private final Provider<ResUtil> resUtilProvider;

  @Inject
  SessionRequestHandlerUtil(
      DeviceQuerier deviceQuerier,
      LocalFileUtil localFileUtil,
      ConfigurationUtil configurationUtil,
      ModuleConfigurationHelper moduleConfigurationHelper,
      CertificationSuiteInfoFactory certificationSuiteInfoFactory,
      RetryGenerator retryGenerator,
      Provider<AndroidAdbInternalUtil> androidAdbInternalUtilProvider,
      Provider<AndroidAdbUtil> androidAdbUtilProvider,
      @SessionGenDir Path sessionGenDir,
      @SessionTempDir Path sessionTempDir,
      Provider<ResUtil> resUtilProvider) {
    this.deviceQuerier = deviceQuerier;
    this.localFileUtil = localFileUtil;
    this.configurationUtil = configurationUtil;
    this.moduleConfigurationHelper = moduleConfigurationHelper;
    this.certificationSuiteInfoFactory = certificationSuiteInfoFactory;
    this.retryGenerator = retryGenerator;
    this.androidAdbInternalUtilProvider = androidAdbInternalUtilProvider;
    this.androidAdbUtilProvider = androidAdbUtilProvider;
    this.sessionGenDir = sessionGenDir;
    this.sessionTempDir = sessionTempDir;
    this.resUtilProvider = resUtilProvider;
  }

  /** Information used to create the Tradefed job. */
  @AutoValue
  public abstract static class TradefedJobInfo {

    /** Creates a {@link TradefedJobInfo}. */
    public static TradefedJobInfo of(
        JobConfig jobConfig, ImmutableMap<XtsPropertyName, String> extraJobProperties) {
      return new AutoValue_SessionRequestHandlerUtil_TradefedJobInfo(jobConfig, extraJobProperties);
    }

    /** The job config. */
    public abstract JobConfig jobConfig();

    /** Extra properties to set in the created job. */
    public abstract ImmutableMap<XtsPropertyName, String> extraJobProperties();
  }

  /**
   * Gets a list of SubDeviceSpec for the job. One SubDeviceSpec maps to one sub device used for
   * running the job as the job may need multiple devices to run the test.
   */
  private ImmutableList<SubDeviceSpec> getSubDeviceSpecListForTradefed(
      List<String> passedInDeviceSerials, int shardCount)
      throws MobileHarnessException, InterruptedException {
    ImmutableSet<String> allAndroidDevices = getAllAndroidDevices();
    logger.atInfo().log("All android devices: %s", allAndroidDevices);
    if (passedInDeviceSerials.isEmpty()) {
      return pickAndroidOnlineDevices(allAndroidDevices, shardCount);
    }

    ArrayList<String> existingPassedInDeviceSerials = new ArrayList<>();
    passedInDeviceSerials.forEach(
        serial -> {
          if (allAndroidDevices.contains(serial)) {
            existingPassedInDeviceSerials.add(serial);
          } else {
            logger
                .atInfo()
                .with(IMPORTANCE, IMPORTANT)
                .log("Passed in device serial [%s] is not detected, skipped.", serial);
          }
        });
    if (existingPassedInDeviceSerials.isEmpty()) {
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log("None of passed in devices exist [%s], skipped.", passedInDeviceSerials);
      return ImmutableList.of();
    }
    return existingPassedInDeviceSerials.stream()
        .map(
            serial ->
                SubDeviceSpec.newBuilder()
                    .setType(getTradefedRequiredDeviceType())
                    .setDimensions(StringMap.newBuilder().putContent("id", serial))
                    .build())
        .collect(toImmutableList());
  }

  private ImmutableList<SubDeviceSpec> pickAndroidOnlineDevices(
      Set<String> allAndroidOnlineDevices, int shardCount) {
    if (shardCount <= 1 && !allAndroidOnlineDevices.isEmpty()) {
      return ImmutableList.of(
          SubDeviceSpec.newBuilder().setType(getTradefedRequiredDeviceType()).build());
    }
    int numOfNeededDevices = min(allAndroidOnlineDevices.size(), shardCount);
    ImmutableList.Builder<SubDeviceSpec> deviceSpecList = ImmutableList.builder();
    for (int i = 0; i < numOfNeededDevices; i++) {
      deviceSpecList.add(
          SubDeviceSpec.newBuilder().setType(getTradefedRequiredDeviceType()).build());
    }
    return deviceSpecList.build();
  }

  private static String getTradefedRequiredDeviceType() {
    return Flags.instance().atsRunTfOnAndroidRealDevice.getNonNull()
        ? ANDROID_REAL_DEVICE_TYPE
        : ANDROID_DEVICE_TYPE;
  }

  private ImmutableSet<String> getAllAndroidDevices()
      throws MobileHarnessException, InterruptedException {
    if (Flags.instance().enableAtsMode.getNonNull()) {
      return getAllAndroidDevicesFromMaster();
    } else {
      return getAllLocalAndroidDevices();
    }
  }

  private ImmutableSet<String> getAllLocalAndroidDevices()
      throws MobileHarnessException, InterruptedException {
    if (Flags.instance().detectAdbDevice.getNonNull()) {
      return ImmutableSet.copyOf(
          androidAdbInternalUtilProvider
              .get()
              .getDeviceSerialsByState(DeviceState.DEVICE, /* timeout= */ null));
    } else {
      return ImmutableSet.of();
    }
  }

  private ImmutableSet<String> getAllAndroidDevicesFromMaster()
      throws MobileHarnessException, InterruptedException {
    DeviceQueryResult queryResult;
    try {
      queryResult = deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_COMMAND_QUERY_DEVICE_ERROR, "Failed to query device", e);
    }
    return queryResult.getDeviceInfoList().stream()
        .filter(
            deviceInfo ->
                deviceInfo.getTypeList().stream()
                    .anyMatch(deviceType -> deviceType.startsWith("Android")))
        .map(com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo::getId)
        .collect(toImmutableSet());
  }

  public Optional<JobInfo> createXtsTradefedTestJob(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    String xtsRootDir = sessionRequestInfo.xtsRootDir();
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      logger.atInfo().log(
          "xTS root dir [%s] doesn't exist, skip creating tradefed jobs.", xtsRootDir);
      return Optional.empty();
    }

    String xtsType = sessionRequestInfo.xtsType();
    ImmutableMap<String, Configuration> configsMap =
        configurationUtil.getConfigsFromDirs(
            ImmutableList.of(XtsDirUtil.getXtsTestCasesDir(Path.of(xtsRootDir), xtsType).toFile()));

    ImmutableList<String> modules = sessionRequestInfo.moduleNames();
    String ctsListPath =
        resUtilProvider
            .get()
            .getResourceFile(
                getClass(),
                "/devtools/mobileharness/infra/controller/test/util/xtsdownloader/configs/cts_list.txt");
    ImmutableList<String> ctsLists =
        localFileUtil.readLineListFromFile(ctsListPath).stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .collect(toImmutableList());
    ImmutableSet<String> allTfModules =
        Stream.concat(
                configsMap.values().stream().map(config -> config.getMetadata().getXtsModule()),
                ctsLists.stream())
            .collect(toImmutableSet());
    ImmutableSet<String> givenMatchedTfModules =
        modules.isEmpty() ? allTfModules : matchModules(modules, allTfModules);

    // Filter modules by include/exclude filters.
    ImmutableList<SuiteTestFilter> includeFilters =
        sessionRequestInfo.includeFilters().stream()
            .map(SuiteTestFilter::create)
            .collect(toImmutableList());
    ImmutableList<SuiteTestFilter> excludeFilters =
        sessionRequestInfo.excludeFilters().stream()
            .map(SuiteTestFilter::create)
            .collect(toImmutableList());
    ImmutableList.Builder<String> filteredModulesBuilder = ImmutableList.builder();
    for (String module : givenMatchedTfModules) {
      if (excludeFilters.stream()
          .anyMatch(
              filter -> filter.testName().isEmpty() && filter.matchModule(module, null, null))) {
        continue;
      }
      if (!includeFilters.isEmpty()
          && includeFilters.stream().noneMatch(filter -> filter.matchModule(module, null, null))) {
        continue;
      }
      filteredModulesBuilder.add(module);
    }
    ImmutableList<String> filteredModules = filteredModulesBuilder.build();

    if (filteredModules.isEmpty()) {
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "Skip creating tradefed jobs as none of given modules is for tradefed module: %s",
              modules);
      return Optional.empty();
    }

    Optional<TradefedJobInfo> tradefedJobInfo =
        createXtsTradefedTestJobInfo(
            sessionRequestInfo, modules.isEmpty() ? ImmutableList.of() : filteredModules);
    if (tradefedJobInfo.isEmpty()) {
      return Optional.empty();
    }
    JobInfo jobInfo =
        JobInfoCreator.createJobInfo(
            tradefedJobInfo.get().jobConfig(),
            ImmutableList.of(),
            tradefedJobInfo.get().jobConfig().getGenFileDir(),
            createJobTmpDir(tradefedJobInfo.get().jobConfig().getName()).toString());
    addSessionClientIdToJobInfo(jobInfo, sessionRequestInfo);
    jobInfo.properties().add(Job.IS_XTS_TF_JOB, "true");
    tradefedJobInfo
        .get()
        .extraJobProperties()
        .forEach((key, value) -> jobInfo.properties().add(key, value));
    printCreatedJobInfo(jobInfo, /* isTf= */ true);
    return Optional.of(jobInfo);
  }

  @VisibleForTesting
  Optional<TradefedJobInfo> createXtsTradefedTestJobInfo(
      SessionRequestInfo sessionRequestInfo, ImmutableList<String> tfModules)
      throws MobileHarnessException, InterruptedException {
    String testPlan = sessionRequestInfo.testPlan();
    String xtsRootDir = sessionRequestInfo.xtsRootDir();
    String xtsType = sessionRequestInfo.xtsType();
    ImmutableList<String> deviceSerials = sessionRequestInfo.deviceSerials();
    int shardCount = sessionRequestInfo.shardCount().orElse(0);
    ImmutableList<String> extraArgs = sessionRequestInfo.extraArgs();
    ImmutableMap.Builder<XtsPropertyName, String> extraJobProperties = ImmutableMap.builder();

    // TODO: migrate multi-device tests to non-TF
    int minDeviceCount = testPlan.matches(xtsType + "-multi-?device") ? 2 : 1;
    ImmutableList<SubDeviceSpec> subDeviceSpecList =
        getSubDeviceSpecListForTradefed(deviceSerials, max(shardCount, minDeviceCount));
    if (subDeviceSpecList.size() < minDeviceCount) {
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log("Found no enough devices to create the job config.");
      return Optional.empty();
    }

    Duration jobTimeout =
        sessionRequestInfo.jobTimeout().isZero()
            ? Duration.ofDays(15L)
            : sessionRequestInfo.jobTimeout();
    // Temporarily reuse job timeout to be test timeout.
    @SuppressWarnings("UnnecessaryLocalVariable")
    Duration testTimeout = jobTimeout;
    Duration startTimeout =
        sessionRequestInfo.startTimeout().isZero()
            ? Duration.ofMinutes(5L)
            : sessionRequestInfo.startTimeout();

    JobConfig.Builder jobConfigBuilder =
        JobConfig.newBuilder()
            .setName("xts-tradefed-test-job")
            .setExecMode("local")
            .setJobTimeoutSec(saturatedCast(jobTimeout.toSeconds()))
            .setTestTimeoutSec(saturatedCast(testTimeout.toSeconds()))
            .setStartTimeoutSec(saturatedCast(startTimeout.toSeconds()))
            .setPriority(Priority.HIGH)
            .setTestAttempts(1)
            .setTests(
                StringList.newBuilder()
                    .addContent(String.format("xts-tradefed-test-%s", testPlan)));
    jobConfigBuilder.setDevice(DeviceList.newBuilder().addAllSubDeviceSpec(subDeviceSpecList));

    Map<String, String> driverParams = new HashMap<>();
    driverParams.put("xts_type", xtsType);

    // Use android xts zip file path if specified in request. Otherwise use root directory path.
    if (sessionRequestInfo.androidXtsZip().isPresent()) {
      driverParams.put("android_xts_zip", sessionRequestInfo.androidXtsZip().get());
    } else {
      driverParams.put("xts_root_dir", xtsRootDir);
    }
    driverParams.put("xts_test_plan", testPlan);
    if (isRunRetry(testPlan)) {
      extraJobProperties.put(Job.IS_RUN_RETRY, "true");
      Optional<SubPlan> runRetryTfSubPlan;
      final ImmutableSet<String> allNonTfModules =
          getNonTfModules(sessionRequestInfo.v2ConfigsMap());
      if (sessionRequestInfo.retrySessionIndex().isPresent()) {
        runRetryTfSubPlan =
            prepareRunRetrySubPlan(
                xtsRootDir,
                sessionRequestInfo.xtsType(),
                sessionRequestInfo.retrySessionIndex().orElseThrow(),
                sessionRequestInfo.retryType().orElse(null),
                sessionRequestInfo.includeFilters(),
                sessionRequestInfo.excludeFilters(),
                allNonTfModules,
                /* forTf= */ true,
                sessionRequestInfo.moduleNames());
      } else {
        runRetryTfSubPlan =
            prepareRunRetrySubPlan(
                sessionRequestInfo.retryResultDir().orElseThrow(),
                sessionRequestInfo.retrySessionId().orElseThrow(),
                sessionRequestInfo.retryType().orElse(null),
                sessionRequestInfo.includeFilters(),
                sessionRequestInfo.excludeFilters(),
                allNonTfModules,
                /* forTf= */ true,
                sessionRequestInfo.moduleNames());
      }
      if (runRetryTfSubPlan.isEmpty()) {
        return Optional.empty();
      }
      driverParams.put(
          "prev_session_xts_test_plan", runRetryTfSubPlan.get().getPreviousSessionXtsTestPlan());
      extraJobProperties.put(
          Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT,
          runRetryTfSubPlan.get().getPreviousSessionDeviceBuildFingerprint().orElse(""));
      Path runRetryTfSubPlanXmlFile;
      if (sessionRequestInfo.retrySessionIndex().isPresent()) {
        runRetryTfSubPlanXmlFile =
            prepareRunRetryTfSubPlanXmlFile(
                xtsRootDir,
                sessionRequestInfo.xtsType(),
                sessionRequestInfo.retrySessionIndex().orElseThrow(),
                runRetryTfSubPlan.get());
      } else {
        runRetryTfSubPlanXmlFile =
            prepareRunRetryTfSubPlanXmlFile(
                xtsRootDir,
                sessionRequestInfo.retrySessionId().orElseThrow(),
                runRetryTfSubPlan.get());
      }
      driverParams.put("subplan_xml", runRetryTfSubPlanXmlFile.toAbsolutePath().toString());
    } else if (sessionRequestInfo.subPlanName().isPresent()) {
      Optional<Path> tfSubPlan =
          prepareTfSubPlan(
              xtsRootDir, sessionRequestInfo.xtsType(), sessionRequestInfo.subPlanName().get());
      if (tfSubPlan.isEmpty()) {
        return Optional.empty();
      }
      driverParams.put("subplan_xml", tfSubPlan.get().toAbsolutePath().toString());
    }

    if (!sessionRequestInfo.envVars().isEmpty()) {
      driverParams.put("env_vars", new Gson().toJson(sessionRequestInfo.envVars()));
    }
    if (sessionRequestInfo.testPlanFile().isPresent()) {
      driverParams.put("xts_test_plan_file", sessionRequestInfo.testPlanFile().get());
    }
    ImmutableList<String> shardCountArg =
        shardCount > 0
            ? ImmutableList.of(String.format("--shard-count %s", shardCount))
            : ImmutableList.of();

    Optional<String> testNameArg =
        sessionRequestInfo.testName().map((String value) -> String.format("-t %s", value));

    String sessionRequestInfoArgs =
        Joiner.on(' ')
            .join(
                Streams.concat(
                        // For "run retry" command, the given modules have been processed when
                        // generating the subplan above, no need to pass these again to underneath
                        // TF
                        (isRunRetry(testPlan)
                            ? Stream.empty()
                            : tfModules.stream().map(module -> String.format("-m %s", module))),
                        testNameArg.stream(),
                        shardCountArg.stream(),
                        // For "run retry" command, the passed in include filters and exclude
                        // filters are set in generated subplan, no need to set in TF command again.
                        isRunRetry(testPlan)
                            ? Stream.empty()
                            : sessionRequestInfo.includeFilters().stream()
                                .map(
                                    includeFilter ->
                                        String.format("--include-filter \"%s\"", includeFilter)),
                        isRunRetry(testPlan)
                            ? Stream.empty()
                            : sessionRequestInfo.excludeFilters().stream()
                                .map(
                                    excludeFilter ->
                                        String.format("--exclude-filter \"%s\"", excludeFilter)),
                        extraArgs.stream()
                            .map(arg -> arg.contains(" ") ? String.format("\"%s\"", arg) : arg))
                    .collect(toImmutableList()));
    if (!sessionRequestInfoArgs.isEmpty()) {
      driverParams.put("run_command_args", sessionRequestInfoArgs);
    }

    jobConfigBuilder.setDriver(
        Driver.newBuilder().setName("XtsTradefedTest").setParam(new Gson().toJson(driverParams)));

    Path jobGenDir = createJobGenDir(jobConfigBuilder.getName());
    jobConfigBuilder.setGenFileDir(jobGenDir.toString());

    JobConfig jobConfig = jobConfigBuilder.build();
    logger.atInfo().log("XtsTradefedTest job config: %s", shortDebugString(jobConfig));

    return Optional.of(TradefedJobInfo.of(jobConfig, extraJobProperties.buildOrThrow()));
  }

  private Optional<Path> prepareTfSubPlan(String xtsRootDir, String xtsType, String subPlanName)
      throws MobileHarnessException, InterruptedException {
    Path subPlansDir = XtsDirUtil.getXtsSubPlansDir(Path.of(xtsRootDir), xtsType);
    Path subPlanPath = subPlansDir.resolve(subPlanName + ".xml");
    if (!localFileUtil.isFileExist(subPlanPath)) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_SUBPLAN_COMMAND_SUBPLAN_XML_NOT_FOUND,
          String.format("Subplan xml file %s doesn't exist", subPlanPath));
    }

    SubPlan subPlan = new SubPlan();
    try (InputStream inputStream = new FileInputStream(subPlanPath.toFile())) {
      subPlan.parse(inputStream);
    } catch (IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_SUBPLAN_COMMAND_PARSE_SUBPLAN_XML_ERROR,
          String.format("Failed to parse the subplan xml file at %s", subPlanPath),
          e);
    }

    if (subPlan.getIncludeFiltersMultimap().isEmpty()
        && subPlan.getExcludeFiltersMultimap().isEmpty()) {
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log("No include or exclude filters found for TF modules and tests ");
      return Optional.empty();
    }

    // If the subplan only includes TF modules and tests, use the subplan file directly
    if (subPlan.getNonTfIncludeFiltersMultimap().isEmpty()
        && subPlan.getNonTfExcludeFiltersMultimap().isEmpty()) {
      return Optional.of(subPlanPath);
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

    return Optional.of(tfOnlySubPlanPath);
  }

  // For ATS Console
  private Path prepareRunRetryTfSubPlanXmlFile(
      String xtsRootDir, String xtsType, int previousSessionIndex, SubPlan subPlan)
      throws MobileHarnessException {
    Path xtsSubPlansDir = XtsDirUtil.getXtsSubPlansDir(Path.of(xtsRootDir), xtsType);

    return serializeRetrySubPlan(
        xtsSubPlansDir, subPlan, String.format("#%s", previousSessionIndex));
  }

  // For ATS Server
  private Path prepareRunRetryTfSubPlanXmlFile(
      String xtsRootDir, String previousSessionId, SubPlan subPlan) throws MobileHarnessException {
    Path xtsSubPlansDir = Path.of(xtsRootDir).getParent();
    return serializeRetrySubPlan(xtsSubPlansDir, subPlan, previousSessionId);
  }

  private Path serializeRetrySubPlan(
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

  /**
   * Adds non-tradefed module info to the {@code SessionRequestInfo}. This is required step before
   * creating non-tradefed jobs or checking if it can do so.
   *
   * @return an updated {@code SessionRequestInfo}
   */
  public SessionRequestInfo addNonTradefedModuleInfo(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    SessionRequestInfo.Builder updatedSessionRequestInfo = sessionRequestInfo.toBuilder();

    String xtsRootDir = sessionRequestInfo.xtsRootDir();
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      logger.atInfo().log(
          "xTS root dir [%s] doesn't exist, skip creating non-tradefed jobs.", xtsRootDir);
      return updatedSessionRequestInfo.build();
    }

    String xtsType = sessionRequestInfo.xtsType();
    ImmutableMap<String, Configuration> configsMap =
        configurationUtil.getConfigsV2FromDirs(
            ImmutableList.of(XtsDirUtil.getXtsTestCasesDir(Path.of(xtsRootDir), xtsType).toFile()));
    updatedSessionRequestInfo.setV2ConfigsMap(configsMap);

    // Gets expanded modules with abi and module parameters (if any).
    TestSuiteHelper testSuiteHelper = getTestSuiteHelper(xtsRootDir, xtsType, sessionRequestInfo);
    updatedSessionRequestInfo.setExpandedModules(
        ImmutableMap.copyOf(
            testSuiteHelper.loadTests(getDeviceInfo(sessionRequestInfo).orElse(null))));

    ImmutableList<String> modules = sessionRequestInfo.moduleNames();
    ImmutableSet<String> allNonTfModules = getNonTfModules(configsMap);
    updatedSessionRequestInfo.setGivenMatchedNonTfModules(matchModules(modules, allNonTfModules));
    return updatedSessionRequestInfo.build();
  }

  private Optional<DeviceInfo> getDeviceInfo(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    Optional<DeviceInfo> deviceInfo =
        Flags.instance().enableAtsMode.getNonNull()
            ? getDeviceInfoFromMaster(sessionRequestInfo)
            : getDeviceInfoFromLocal(sessionRequestInfo);

    logger.atInfo().log("Obtained device info: %s", deviceInfo.orElse(null));
    return deviceInfo;
  }

  private Optional<DeviceInfo> getDeviceInfoFromMaster(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    DeviceQueryResult queryResult;
    try {
      queryResult = deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_COMMAND_QUERY_DEVICE_ERROR, "Failed to query device", e);
    }

    Optional<com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo>
        queryDeviceInfo =
            queryResult.getDeviceInfoList().stream()
                .filter(
                    deviceInfo -> {
                      if (sessionRequestInfo.deviceSerials().isEmpty()) {
                        return true;
                      } else {
                        return sessionRequestInfo.deviceSerials().contains(deviceInfo.getId())
                            && deviceInfo.getTypeList().stream()
                                .anyMatch(deviceType -> deviceType.startsWith("Android"));
                      }
                    })
                .findFirst();
    if (queryDeviceInfo.isEmpty()) {
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log("No match Android devices, return empty device info.");
      return Optional.empty();
    }

    return Optional.of(
        DeviceInfo.builder()
            .setDeviceId(queryDeviceInfo.get().getId())
            .setSupportedAbiList(
                queryDeviceInfo.get().getDimensionList().stream()
                    .filter(
                        dimension ->
                            dimension
                                .getName()
                                .equals(Ascii.toLowerCase(AndroidProperty.ABILIST.name())))
                    .map(DeviceQuery.Dimension::getValue)
                    .findFirst()
                    .orElse(""))
            .setSupportedAbi(
                queryDeviceInfo.get().getDimensionList().stream()
                    .filter(
                        dimension ->
                            dimension
                                .getName()
                                .equals(Ascii.toLowerCase(AndroidProperty.ABI.name())))
                    .map(DeviceQuery.Dimension::getValue)
                    .findFirst()
                    .orElse(""))
            .build());
  }

  private Optional<DeviceInfo> getDeviceInfoFromLocal(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    ImmutableSet<String> allLocalAndroidDevices = getAllLocalAndroidDevices();
    if (!allLocalAndroidDevices.isEmpty()) {
      Optional<String> deviceSerial;
      if (sessionRequestInfo.deviceSerials().isEmpty()) {
        deviceSerial = allLocalAndroidDevices.stream().findFirst();
      } else {
        deviceSerial =
            sessionRequestInfo.deviceSerials().stream()
                .filter(allLocalAndroidDevices::contains)
                .findFirst();
      }
      if (deviceSerial.isEmpty()) {
        logger
            .atInfo()
            .with(IMPORTANCE, IMPORTANT)
            .log(
                "No match local Android devices, return empty device info. Detected all local"
                    + " Android devices: %s",
                allLocalAndroidDevices);
        return Optional.empty();
      }

      String abiList =
          androidAdbUtilProvider.get().getProperty(deviceSerial.get(), AndroidProperty.ABILIST);
      String abi =
          androidAdbUtilProvider.get().getProperty(deviceSerial.get(), AndroidProperty.ABI);
      return Optional.of(
          DeviceInfo.builder()
              .setDeviceId(deviceSerial.get())
              .setSupportedAbiList(abiList)
              .setSupportedAbi(abi)
              .build());
    }
    logger
        .atInfo()
        .with(IMPORTANCE, IMPORTANT)
        .log("Detected no local Android devices, return empty device info.");
    return Optional.empty();
  }

  /**
   * Checks if non-tradefed jobs can be created based on the {@code SessionRequestInfo}.
   *
   * @return true if non-tradefed jobs can be created.
   */
  public boolean canCreateNonTradefedJobs(SessionRequestInfo sessionRequestInfo) {
    if (isRunRetry(sessionRequestInfo.testPlan())) {
      return true;
    }
    boolean noGivenModuleForNonTf =
        !sessionRequestInfo.moduleNames().isEmpty()
            && sessionRequestInfo.givenMatchedNonTfModules().isEmpty();
    return !noGivenModuleForNonTf;
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
        TestPlanLoader.parseFilters(
            Path.of(sessionRequestInfo.xtsRootDir()),
            sessionRequestInfo.xtsType(),
            sessionRequestInfo.testPlan()));
  }

  @VisibleForTesting
  ImmutableList<JobInfo> createXtsNonTradefedJobs(
      SessionRequestInfo sessionRequestInfo, TestPlanLoader.TestPlanFilter testPlanFilter)
      throws MobileHarnessException, InterruptedException {
    if (!canCreateNonTradefedJobs(sessionRequestInfo)) {
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "Skip creating non-tradefed jobs as none of given modules is for non-tradefed module:"
                  + " %s",
              sessionRequestInfo.moduleNames());
      return ImmutableList.of();
    }
    String testPlan = sessionRequestInfo.testPlan();
    String xtsRootDir = sessionRequestInfo.xtsRootDir();
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      logger.atInfo().log(
          "xTS root dir [%s] doesn't exist, skip creating non-tradefed jobs.", xtsRootDir);
      return ImmutableList.of();
    }

    Map<XtsPropertyName, String> extraJobProperties = new HashMap<>();
    String xtsType = sessionRequestInfo.xtsType();
    Optional<SubPlan> subPlanOpt = Optional.empty();
    if (isRunRetry(testPlan)) {
      extraJobProperties.put(Job.IS_RUN_RETRY, "true");
      if (sessionRequestInfo.retrySessionIndex().isPresent()) {
        subPlanOpt =
            prepareRunRetrySubPlan(
                xtsRootDir,
                sessionRequestInfo.xtsType(),
                sessionRequestInfo.retrySessionIndex().orElseThrow(),
                sessionRequestInfo.retryType().orElse(null),
                sessionRequestInfo.includeFilters(),
                sessionRequestInfo.excludeFilters(),
                getNonTfModules(sessionRequestInfo.v2ConfigsMap()),
                /* forTf= */ false,
                sessionRequestInfo.moduleNames());
      } else {
        subPlanOpt =
            prepareRunRetrySubPlan(
                sessionRequestInfo.retryResultDir().orElseThrow(),
                sessionRequestInfo.retrySessionId().orElseThrow(),
                sessionRequestInfo.retryType().orElse(null),
                sessionRequestInfo.includeFilters(),
                sessionRequestInfo.excludeFilters(),
                getNonTfModules(sessionRequestInfo.v2ConfigsMap()),
                /* forTf= */ false,
                sessionRequestInfo.moduleNames());
      }
      if (subPlanOpt.isEmpty()) {
        return ImmutableList.of();
      }
      extraJobProperties.put(
          Job.PREV_SESSION_DEVICE_BUILD_FINGERPRINT,
          subPlanOpt.get().getPreviousSessionDeviceBuildFingerprint().orElse(""));
    } else if (sessionRequestInfo.subPlanName().isPresent()) {
      subPlanOpt =
          prepareNonTfSubPlan(
              xtsRootDir, sessionRequestInfo.xtsType(), sessionRequestInfo.subPlanName().get());
      if (subPlanOpt.isEmpty()) {
        return ImmutableList.of();
      }
    }

    ImmutableSet<String> givenMatchedNonTfModules = sessionRequestInfo.givenMatchedNonTfModules();
    ImmutableList.Builder<JobInfo> jobInfos = ImmutableList.builder();

    ImmutableList<String> androidDeviceSerials = sessionRequestInfo.deviceSerials();

    ImmutableMap<String, String> moduleNameToConfigFilePathMap =
        sessionRequestInfo.v2ConfigsMap().entrySet().stream()
            .collect(toImmutableMap(e -> e.getValue().getMetadata().getXtsModule(), Entry::getKey));

    ImmutableList<SuiteTestFilter> includeFilters =
        Stream.concat(
                sessionRequestInfo.includeFilters().stream(),
                testPlanFilter.includeFilters().stream())
            .map(SuiteTestFilter::create)
            .collect(toImmutableList());
    if (subPlanOpt.isPresent()) {
      includeFilters =
          subPlanOpt.get().getNonTfIncludeFiltersMultimap().entries().stream()
              .map(
                  e ->
                      SuiteTestFilter.create(
                          e.getKey()
                              + (e.getValue().equals(SubPlan.ALL_TESTS_IN_MODULE)
                                  ? ""
                                  : " " + e.getValue())))
              .collect(toImmutableList());
      logger.atInfo().log("Include filters for Non-TF retry/subplan run: %s", includeFilters);
    }
    ImmutableList<SuiteTestFilter> excludeFilters =
        Stream.concat(
                sessionRequestInfo.excludeFilters().stream(),
                testPlanFilter.excludeFilters().stream())
            .map(SuiteTestFilter::create)
            .collect(toImmutableList());

    Duration jobTimeout =
        sessionRequestInfo.jobTimeout().isZero()
            ? Duration.ofDays(5L)
            : sessionRequestInfo.jobTimeout();
    // Temporarily reuse job timeout to be test timeout.
    @SuppressWarnings("UnnecessaryLocalVariable")
    Duration testTimeout = jobTimeout;
    Duration startTimeout =
        sessionRequestInfo.startTimeout().isZero()
            ? Duration.ofHours(1L)
            : sessionRequestInfo.startTimeout();

    for (Entry<String, Configuration> entry :
        sessionRequestInfo.expandedModules().entrySet().stream()
            .filter(e -> e.getValue().getMetadata().getIsConfigV2())
            .collect(toImmutableList())) {
      String originalModuleName = entry.getValue().getMetadata().getXtsModule();
      String expandedModuleName = entry.getKey();
      // If it has a subplan(either from the retry command or the subplan command), do a early check
      // for whether the module should be ran
      if (subPlanOpt.isPresent()
          && !subPlanOpt.get().getNonTfIncludeFiltersMultimap().containsKey(expandedModuleName)) {
        continue;
      }
      ImmutableList.Builder<String> matchedTestCasesBuilder = ImmutableList.builder();
      if (givenMatchedNonTfModules.isEmpty()
          || givenMatchedNonTfModules.contains(originalModuleName)) {
        // Gets module abi
        String moduleAbi = getModuleAbi(expandedModuleName).orElse(null);
        // Gets module parameter
        String moduleParameter = getModuleParameter(expandedModuleName).orElse(null);

        // Filters the module by include-filter and exclude-filter.
        if (excludeFilters.stream()
            .anyMatch(
                excludeFilter ->
                    excludeFilter.matchModule(originalModuleName, moduleAbi, moduleParameter))) {
          continue;
        }
        if (sessionRequestInfo.testName().isPresent()) {
          String parsedTestName = parseTestName(sessionRequestInfo.testName().orElse(null));
          if (!parsedTestName.isEmpty()) {
            matchedTestCasesBuilder.add(parsedTestName);
          } else {
            continue;
          }
        } else if (!includeFilters.isEmpty()) {
          boolean matched = false;
          for (SuiteTestFilter filter : includeFilters) {
            if (filter.matchModule(originalModuleName, moduleAbi, moduleParameter)) {
              matched = true;
              String parsedTestName = parseTestName(filter.testName().orElse(null));
              if (!parsedTestName.isEmpty()) {
                matchedTestCasesBuilder.add(parsedTestName);
              }
            }
          }
          if (!matched) {
            continue;
          }
        }

        Optional<JobInfo> jobInfoOpt =
            createXtsNonTradefedJob(
                Path.of(xtsRootDir),
                xtsType,
                testPlan,
                subPlanOpt
                    .map(subPlan -> emptyToNull(subPlan.getPreviousSessionXtsTestPlan()))
                    .orElse(null),
                Path.of(requireNonNull(moduleNameToConfigFilePathMap.get(originalModuleName))),
                entry.getValue(),
                expandedModuleName,
                moduleAbi,
                moduleParameter,
                matchedTestCasesBuilder.build(),
                jobTimeout,
                testTimeout,
                startTimeout);
        if (jobInfoOpt.isPresent()) {
          JobInfo jobInfo = jobInfoOpt.get();
          if (!androidDeviceSerials.isEmpty()) {
            String serialDimensionValue =
                String.format("regex:(%s)", Joiner.on('|').join(androidDeviceSerials));
            jobInfo
                .subDeviceSpecs()
                .getAllSubDevices()
                .forEach(
                    subDeviceSpec ->
                        subDeviceSpec
                            .deviceRequirement()
                            .dimensions()
                            .add("id", serialDimensionValue));
          }
          addSessionClientIdToJobInfo(jobInfo, sessionRequestInfo);
          extraJobProperties.forEach((key, value) -> jobInfo.properties().add(key, value));
          printCreatedJobInfo(jobInfo, /* isTf= */ false);
          jobInfos.add(jobInfo);
        }
      }
    }

    return jobInfos.build();
  }

  /**
   * TestName is set with pattern TestClassName#TestCaseName while Mobly needs the test case name
   * without the test class name.
   */
  private String parseTestName(@Nullable String testName) {
    if (testName == null) {
      return "";
    }
    List<String> list = Splitter.on('#').trimResults().omitEmptyStrings().splitToList(testName);
    if (list.size() == 2) {
      return list.get(1);
    }
    logger
        .atWarning()
        .with(IMPORTANCE, IMPORTANT)
        .log("Failed to parse test case name from [%s].", testName);
    return "";
  }

  private Optional<JobInfo> createXtsNonTradefedJob(
      Path xtsRootDir,
      String xtsType,
      String testPlan,
      @Nullable String previousSessionTestPlan,
      Path moduleConfigPath,
      Configuration moduleConfig,
      String expandedModuleName,
      @Nullable String moduleAbi,
      @Nullable String moduleParameter,
      ImmutableList<String> matchedTestCases,
      Duration jobTimeout,
      Duration testTimeout,
      Duration startTimeout)
      throws MobileHarnessException, InterruptedException {
    Optional<JobInfo> jobInfoOpt =
        createBaseXtsNonTradefedJob(
            moduleConfig, expandedModuleName, jobTimeout, testTimeout, startTimeout);
    if (jobInfoOpt.isEmpty()) {
      return Optional.empty();
    }

    ImmutableList<File> fileDepDirs =
        ImmutableList.of(
            moduleConfigPath.getParent().toFile(),
            XtsDirUtil.getXtsTestCasesDir(xtsRootDir, xtsType).toFile());

    JobInfo jobInfo = jobInfoOpt.get();
    moduleConfigurationHelper.updateJobInfo(jobInfo, moduleConfig, fileDepDirs);
    jobInfo.properties().add(Job.IS_XTS_NON_TF_JOB, "true");
    jobInfo
        .properties()
        .add(SessionHandlerHelper.XTS_MODULE_NAME_PROP, moduleConfig.getMetadata().getXtsModule());
    if (moduleAbi != null) {
      jobInfo.properties().add(SessionHandlerHelper.XTS_MODULE_ABI_PROP, moduleAbi);
    }
    if (moduleParameter != null) {
      jobInfo.properties().add(SessionHandlerHelper.XTS_MODULE_PARAMETER_PROP, moduleParameter);
    }
    if (!matchedTestCases.isEmpty()) {
      jobInfo.params().add("test_case_selector", Joiner.on(" ").join(matchedTestCases));
    }
    jobInfo.params().add("run_certification_test_suite", "true");
    jobInfo
        .params()
        .add(
            "xts_suite_info",
            generateXtsSuiteInfoMap(
                xtsRootDir.toAbsolutePath().toString(),
                xtsType,
                previousSessionTestPlan != null ? previousSessionTestPlan : testPlan));
    return Optional.of(jobInfo);
  }

  private String generateXtsSuiteInfoMap(String xtsRootDir, String xtsType, String testPlan) {
    Map<String, String> xtsSuiteInfoMap =
        certificationSuiteInfoFactory.generateSuiteInfoMap(xtsRootDir, xtsType, testPlan);
    return Joiner.on(",").withKeyValueSeparator("=").join(xtsSuiteInfoMap);
  }

  private Optional<JobInfo> createBaseXtsNonTradefedJob(
      Configuration moduleConfig,
      String expandedModuleName,
      Duration jobTimeout,
      Duration testTimeout,
      Duration startTimeout)
      throws MobileHarnessException, InterruptedException {
    List<Device> moduleDevices = moduleConfig.getDevicesList();
    if (moduleDevices.isEmpty()) {
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "Found no devices to create the job config for xts non-tradefed job with module"
                  + " '%s'.",
              expandedModuleName);
      return Optional.empty();
    }

    List<SubDeviceSpec> subDeviceSpecList = new ArrayList<>();
    for (Device device : moduleDevices) {
      if (device.getName().isEmpty()) {
        logger
            .atWarning()
            .with(IMPORTANCE, IMPORTANT)
            .log("Device name is missing in a <device> in module '%s'", expandedModuleName);
        return Optional.empty();
      } else {
        subDeviceSpecList.add(SubDeviceSpec.newBuilder().setType(device.getName()).build());
      }
    }

    JobConfig.Builder jobConfigBuilder =
        JobConfig.newBuilder()
            .setName(
                String.format(
                    "xts-mobly-aosp-package-job-%s", expandedModuleName.replace(' ', '_')))
            .setExecMode("local")
            .setJobTimeoutSec(saturatedCast(jobTimeout.toSeconds()))
            .setTestTimeoutSec(saturatedCast(testTimeout.toSeconds()))
            .setStartTimeoutSec(saturatedCast(startTimeout.toSeconds()))
            .setPriority(Priority.HIGH)
            .setTestAttempts(1)
            .setTests(
                StringList.newBuilder()
                    .addContent(
                        String.format(
                            "xts-mobly-aosp-package-test-%s",
                            expandedModuleName.replace(' ', '_'))));
    jobConfigBuilder.setDevice(DeviceList.newBuilder().addAllSubDeviceSpec(subDeviceSpecList));
    jobConfigBuilder.setDriver(Driver.newBuilder().setName("MoblyAospPackageTest"));

    Path jobGenDir = createJobGenDir(jobConfigBuilder.getName());
    Path jobTmpDir = createJobTmpDir(jobConfigBuilder.getName());
    jobConfigBuilder.setGenFileDir(jobGenDir.toString());

    JobConfig jobConfig = jobConfigBuilder.build();
    logger.atInfo().log(
        "Non-tradefed job base config for module '%s': %s",
        expandedModuleName, shortDebugString(jobConfig));

    return Optional.of(
        JobInfoCreator.createJobInfo(
            jobConfig, ImmutableList.of(), jobGenDir.toString(), jobTmpDir.toString()));
  }

  private Path createJobGenDir(String jobName) {
    return sessionGenDir.resolve(
        String.format("job_gen_%s_%s", encodeJobName(jobName), UUID.randomUUID()));
  }

  private Path createJobTmpDir(String jobName) {
    return sessionTempDir.resolve(
        String.format("job_tmp_%s_%s", encodeJobName(jobName), UUID.randomUUID()));
  }

  private static String encodeJobName(String jobName) {
    return URLEncoder.encode(jobName, UTF_8);
  }

  @VisibleForTesting
  TestSuiteHelper getTestSuiteHelper(
      String xtsRootDir, String xtsType, SessionRequestInfo sessionRequestInfo) {
    TestSuiteHelper testSuiteHelper = new TestSuiteHelper(xtsRootDir, xtsType);
    testSuiteHelper.setParameterizedModules(sessionRequestInfo.enableModuleParameter());
    testSuiteHelper.setOptionalParameterizedModules(
        sessionRequestInfo.enableModuleOptionalParameter());
    return testSuiteHelper;
  }

  private Optional<String> getModuleAbi(String expandedModuleName) {
    String abi = AbiUtil.parseAbi(expandedModuleName);
    return isNullOrEmpty(abi) ? Optional.empty() : Optional.of(abi);
  }

  private Optional<String> getModuleParameter(String expandedModuleName) {
    Matcher matcher = MODULE_PARAMETER_PATTERN.matcher(expandedModuleName);
    return matcher.find() ? Optional.of(matcher.group("moduleParam")) : Optional.empty();
  }

  private boolean isRunRetry(String testPlan) {
    return Ascii.equalsIgnoreCase(testPlan, "retry");
  }

  private Optional<SubPlan> prepareNonTfSubPlan(
      String xtsRootDir, String xtsType, String subPlanName) throws MobileHarnessException {
    Path subPlanPath =
        XtsDirUtil.getXtsSubPlansDir(Path.of(xtsRootDir), xtsType).resolve(subPlanName + ".xml");
    if (!localFileUtil.isFileExist(subPlanPath)) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_SUBPLAN_COMMAND_SUBPLAN_XML_NOT_FOUND,
          String.format("Subplan xml file %s doesn't exist", subPlanPath));
    }

    try (InputStream inputStream = new FileInputStream(subPlanPath.toFile())) {
      SubPlan subPlan = new SubPlan();
      subPlan.parse(inputStream);
      if (subPlan.getNonTfIncludeFiltersMultimap().isEmpty()
          && subPlan.getNonTfExcludeFiltersMultimap().isEmpty()) {
        logger
            .atInfo()
            .with(IMPORTANCE, IMPORTANT)
            .log("No include or exclude filters found for Non-TF modules and tests");
        return Optional.empty();
      }
      return Optional.of(subPlan);
    } catch (IOException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_SUBPLAN_COMMAND_PARSE_SUBPLAN_XML_ERROR,
          String.format("Failed to parse the subplan xml file at %s", subPlanPath),
          e);
    }
  }

  // For ATS Console
  private Optional<SubPlan> prepareRunRetrySubPlan(
      String xtsRootDir,
      String xtsType,
      int previousSessionIndex,
      @Nullable RetryType retryType,
      ImmutableList<String> passedInIncludeFilters,
      ImmutableList<String> passedInExcludeFilters,
      ImmutableSet<String> allNonTradefedModules,
      boolean forTf,
      ImmutableList<String> passedInModules)
      throws MobileHarnessException {
    Path xtsRootDirPath = Path.of(xtsRootDir);
    RetryArgs.Builder retryArgs =
        RetryArgs.builder()
            .setResultsDir(XtsDirUtil.getXtsResultsDir(xtsRootDirPath, xtsType))
            .setPreviousSessionIndex(previousSessionIndex)
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
    return generateRetrySubPlan(retryArgs.build(), forTf, String.valueOf(previousSessionIndex));
  }

  // For ATS Server
  private Optional<SubPlan> prepareRunRetrySubPlan(
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

  private Optional<SubPlan> generateRetrySubPlan(
      RetryArgs retryArgs, boolean forTf, String previousSessionIdOrIndex)
      throws MobileHarnessException {
    SubPlan subPlan = retryGenerator.generateRetrySubPlan(retryArgs);
    if ((forTf
            && subPlan.getIncludeFiltersMultimap().isEmpty()
            && subPlan.getExcludeFiltersMultimap().isEmpty())
        || (!forTf
            && subPlan.getNonTfIncludeFiltersMultimap().isEmpty()
            && subPlan.getNonTfExcludeFiltersMultimap().isEmpty())) {
      logger.atInfo().log(
          "No include or exclude filters found for %s retry session %s with retry type %s",
          forTf ? "TF" : "Non-TF",
          previousSessionIdOrIndex,
          retryArgs.retryType().isPresent() ? retryArgs.retryType().get() : "UNKNOWN");
      return Optional.empty();
    }
    return Optional.of(subPlan);
  }

  private static ImmutableSet<String> matchModules(List<String> filters, Set<String> allModules)
      throws MobileHarnessException {
    ImmutableSet.Builder<String> modules = ImmutableSet.builder();
    for (String filter : filters) {
      matchModule(filter, allModules).ifPresent(modules::add);
    }
    return modules.build();
  }

  private static Optional<String> matchModule(String filter, Set<String> allModules)
      throws MobileHarnessException {
    // Do exact match first
    if (allModules.contains(filter)) {
      return Optional.of(filter);
    }

    Pattern pattern = Pattern.compile(filter);
    ImmutableList<String> modules =
        allModules.stream()
            .filter(module -> pattern.matcher(module).find())
            .collect(toImmutableList());
    if (modules.isEmpty()) {
      return Optional.empty();
    } else if (modules.size() == 1) {
      return Optional.of(modules.get(0));
    } else {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_COMMAND_MULTIPLE_MODULES_FOUND_ERROR,
          String.format(
              "Multiple modules found matching %s:\n%s\nWhich one did you mean?\n",
              filter, String.join("\n", modules)));
    }
  }

  private static ImmutableSet<String> getNonTfModules(
      ImmutableMap<String, Configuration> configsMap) {
    return configsMap.values().stream()
        .map(config -> config.getMetadata().getXtsModule())
        .collect(toImmutableSet());
  }

  private static void addSessionClientIdToJobInfo(
      JobInfo jobInfo, SessionRequestInfo sessionRequestInfo) {
    sessionRequestInfo
        .sessionClientId()
        .ifPresent(
            sessionClientId -> jobInfo.params().add("olc_session_client_id", sessionClientId));
  }

  private static void printCreatedJobInfo(JobInfo jobInfo, boolean isTf) {
    logger.atInfo().log(
        "%s job info for %s:\n\"params\": %s\n\"subDeviceSpecs\": %s\n\"files\": %s",
        isTf ? "Tradefed" : "Non-tradefed",
        jobInfo.locator().getName(),
        jobInfo.params().getAll(),
        jobInfo.subDeviceSpecs(),
        jobInfo.files().getAll());
  }
}
