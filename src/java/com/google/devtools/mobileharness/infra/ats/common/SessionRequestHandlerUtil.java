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

import static com.google.common.base.Ascii.toLowerCase;
import static com.google.common.base.Enums.getIfPresent;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.primitives.Ints.saturatedCast;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.realdevice.AndroidRealDeviceConstants;
import com.google.devtools.deviceinfra.shared.util.file.remote.constant.RemoteFileType;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.api.model.proto.Job.AllocationExitStrategy;
import com.google.devtools.mobileharness.infra.ats.common.plan.TestPlanParser;
import com.google.devtools.mobileharness.infra.ats.common.plan.TestPlanParser.TestPlanFilter;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.ShardingMode;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfoFactory;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionGenDir;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.SessionTempDir;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.xts.common.util.AbiUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.MoblyTestLoader;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ModuleConfigurationHelper;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Device;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.DeviceConfigurationProto.DeviceConfigurations;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.DeviceConfigurationProto.ModuleDeviceConfiguration;
import com.google.devtools.mobileharness.platform.android.xts.constant.NonTradefedReportGeneratorConstants;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.suite.ModuleArg;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper.DeviceInfo;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.jobconfig.JobInfoCreator;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.gson.Gson;
import com.google.inject.Provider;
import com.google.protobuf.TextFormat.ParseException;
import com.google.wireless.qa.mobileharness.shared.api.spec.MoblyAospTestSpec;
import com.google.wireless.qa.mobileharness.shared.api.spec.MoblyTestSpec;
import com.google.wireless.qa.mobileharness.shared.api.step.android.InstallApkStepConstants;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Priority;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.DeviceList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.Driver;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.FileConfigList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.FileConfigList.FileConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.io.File;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

  public static final String ANDROID_REAL_DEVICE_TYPE = "AndroidRealDevice";
  public static final String ANDROID_LOCAL_EMULATOR_TYPE = "AndroidLocalEmulator";
  public static final String ANDROID_DEVICE_TYPE = "AndroidDevice";

  private static final String COMPATIBILITY_TEST_SUITE_CLASS_NAME =
      "com.android.compatibility.common.tradefed.testtype.suite.CompatibilityTestSuite";

  private static final Pattern MODULE_PARAMETER_PATTERN =
      Pattern.compile(".*\\[(?<moduleParam>.*)]$");

  private static final Duration JOB_TEST_TIMEOUT_DIFF = Duration.ofMinutes(1L);
  private static final Duration DEFAULT_TRADEFED_JOB_TIMEOUT = Duration.ofDays(15L);
  private static final Duration DEFAULT_TRADEFED_START_TIMEOUT = Duration.ofDays(14L);
  private static final Duration DEFAULT_NON_TRADEFED_JOB_TIMEOUT = Duration.ofDays(5L);
  private static final Duration DEFAULT_NON_TRADEFED_START_TIMEOUT = Duration.ofDays(4L);

  // Max waiting time for device to be ready is 30s * 40 = 20 minutes.
  private static final Duration GET_DEVICE_FOR_ATS_SERVER_RETRY_INTERVAL = Duration.ofSeconds(30);
  private static final int GET_DEVICE_FOR_ATS_SERVER_MAX_RETRY_ATTEMPTS = 40;

  private static final ImmutableList<String> XTS_TYPE_THAT_NEED_TEST_HARNESS_PROPERTY_FALSE =
      ImmutableList.of("cts", "mcts");

  private final DeviceQuerier deviceQuerier;
  private final LocalFileUtil localFileUtil;
  private final ConfigurationUtil configurationUtil;
  private final ModuleConfigurationHelper moduleConfigurationHelper;
  private final CertificationSuiteInfoFactory certificationSuiteInfoFactory;
  private final Provider<AndroidAdbUtil> androidAdbUtilProvider;
  private final Path sessionGenDir;
  private final Path sessionTempDir;
  private final Provider<ResUtil> resUtilProvider;
  private final DeviceDetailsRetriever deviceDetailsRetriever;
  private final MoblyTestLoader moblyTestLoader;
  private final TestPlanParser testPlanParser;
  private final Sleeper sleeper;

  @Inject
  SessionRequestHandlerUtil(
      DeviceQuerier deviceQuerier,
      LocalFileUtil localFileUtil,
      ConfigurationUtil configurationUtil,
      ModuleConfigurationHelper moduleConfigurationHelper,
      CertificationSuiteInfoFactory certificationSuiteInfoFactory,
      Provider<AndroidAdbUtil> androidAdbUtilProvider,
      @SessionGenDir Path sessionGenDir,
      @SessionTempDir Path sessionTempDir,
      Provider<ResUtil> resUtilProvider,
      DeviceDetailsRetriever deviceDetailsRetriever,
      MoblyTestLoader moblyTestLoader,
      TestPlanParser testPlanParser,
      Sleeper sleeper) {
    this.deviceQuerier = deviceQuerier;
    this.localFileUtil = localFileUtil;
    this.configurationUtil = configurationUtil;
    this.moduleConfigurationHelper = moduleConfigurationHelper;
    this.certificationSuiteInfoFactory = certificationSuiteInfoFactory;
    this.androidAdbUtilProvider = androidAdbUtilProvider;
    this.sessionGenDir = sessionGenDir;
    this.sessionTempDir = sessionTempDir;
    this.resUtilProvider = resUtilProvider;
    this.deviceDetailsRetriever = deviceDetailsRetriever;
    this.moblyTestLoader = moblyTestLoader;
    this.testPlanParser = testPlanParser;
    this.sleeper = sleeper;
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
  public ImmutableList<SubDeviceSpec> getSubDeviceSpecListForTradefed(
      SessionRequestInfo sessionRequestInfo) throws MobileHarnessException, InterruptedException {
    String testPlan = sessionRequestInfo.testPlan();
    String xtsType = sessionRequestInfo.xtsType();
    int requestedShardCount = sessionRequestInfo.shardCount().orElse(0);
    int minDeviceCount = testPlan.matches(xtsType + "-multi-?device") ? 2 : 1;
    int shardCount = max(requestedShardCount, minDeviceCount);
    ImmutableSet<DeviceDetails> availableDevices = getAvailableDevices(sessionRequestInfo);
    Map<String, String> extraDimensions = new HashMap<>();
    // TODO: b/444562857 - Remove enableTestHarnessCheckForRequiredTests flag once test harness
    // dimension allocation issue is fixed.
    if (Flags.instance().isOmniMode.getNonNull()
        && Flags.instance().enableTestHarnessCheckForRequiredTests.getNonNull()
        && needTestHarnessPropertyFalse(sessionRequestInfo)) {
      extraDimensions.put(toLowerCase(AndroidProperty.PERSIST_TEST_HARNESS.name()), Value.FALSE);
    }
    if (sessionRequestInfo.isAtsServerRequest() && !sessionRequestInfo.deviceSerials().isEmpty()) {
      if (shouldEnableModuleSharding(sessionRequestInfo)) {
        StringMap dimensions =
            StringMap.newBuilder()
                .putContent(
                    Name.UUID.lowerCaseName(),
                    String.format(
                        "%s(%s)",
                        Value.PREFIX_REGEX,
                        Joiner.on('|').join(sessionRequestInfo.deviceSerials())))
                .putAllContent(extraDimensions)
                .build();
        return ImmutableList.of(
            SubDeviceSpec.newBuilder()
                .setType(getTradefedRequiredDeviceType(sessionRequestInfo))
                .setDimensions(dimensions)
                .build());
      } else {
        // Use UUID instead of id because the id is not unique for virtual devices on different
        // hosts, but UUID is for them. Example: the id is 0.0.0.0:6520 and is same for those from
        // different hosts, and UUID is hostname:6520 which is unique.
        ImmutableSet<String> availableDeviceUuids =
            availableDevices.stream()
                .map(device -> device.uuid().orElse(""))
                .collect(toImmutableSet());
        Stream<String> deviceSerials = sessionRequestInfo.deviceSerials().stream();
        if (sessionRequestInfo.allowPartialDeviceMatch()) {
          // TODO: The available devices should online and not busy. Currently only
          // check for online devices.
          deviceSerials = deviceSerials.filter(availableDeviceUuids::contains);
        }
        return deviceSerials
            .map(
                deviceSerial ->
                    SubDeviceSpec.newBuilder()
                        .setType(getTradefedRequiredDeviceType(sessionRequestInfo))
                        .setDimensions(
                            StringMap.newBuilder()
                                .putContent(Name.UUID.lowerCaseName(), deviceSerial)
                                .putAllContent(extraDimensions))
                        .build())
            .collect(toImmutableList());
      }
    }

    if (sessionRequestInfo.deviceSerials().isEmpty()) {
      return pickAndroidOnlineDevices(
          sessionRequestInfo,
          availableDevices.stream().map(DeviceDetails::id).collect(toImmutableSet()),
          shardCount);
    }

    return availableDevices.stream()
        .map(
            deviceDetails ->
                SubDeviceSpec.newBuilder()
                    .setType(getTradefedRequiredDeviceType(sessionRequestInfo))
                    .setDimensions(
                        StringMap.newBuilder()
                            .putContent(Name.ID.lowerCaseName(), deviceDetails.id())
                            .putAllContent(extraDimensions))
                    .build())
        .collect(toImmutableList());
  }

  private ImmutableSet<DeviceDetails> getAvailableDevices(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    ImmutableMap<String, DeviceDetails> allAndroidDevices;
    // Wait for devices to be ready for ATS server requests.
    if (sessionRequestInfo.isAtsServerRequest() && !sessionRequestInfo.deviceSerials().isEmpty()) {
      waitForRequestedDevicesToBeReady(sessionRequestInfo);
      // Fetch the devices again after waiting to ensure we have the latest information.
      allAndroidDevices =
          deviceDetailsRetriever.getAllAndroidDevicesWithNeededDetails(sessionRequestInfo);
    } else {
      allAndroidDevices =
          deviceDetailsRetriever.getAllAndroidDevicesWithNeededDetails(sessionRequestInfo);
    }
    logger.atInfo().log("All android devices: %s", allAndroidDevices.keySet());

    DeviceSelectionOptions.Builder optionsBuilder =
        DeviceSelectionOptions.builder()
            .setSerials(sessionRequestInfo.deviceSerials())
            .setExcludeSerials(sessionRequestInfo.excludeDeviceSerials())
            .setProductTypes(sessionRequestInfo.productTypes())
            .setDeviceProperties(sessionRequestInfo.deviceProperties());
    sessionRequestInfo.maxBatteryLevel().ifPresent(optionsBuilder::setMaxBatteryLevel);
    sessionRequestInfo.minBatteryLevel().ifPresent(optionsBuilder::setMinBatteryLevel);
    sessionRequestInfo.maxBatteryTemperature().ifPresent(optionsBuilder::setMaxBatteryTemperature);
    sessionRequestInfo.minSdkLevel().ifPresent(optionsBuilder::setMinSdkLevel);
    sessionRequestInfo.maxSdkLevel().ifPresent(optionsBuilder::setMaxSdkLevel);
    DeviceSelectionOptions deviceSelectionOptions = optionsBuilder.build();

    ImmutableSet<DeviceDetails> availableDevices =
        allAndroidDevices.values().stream()
            .filter(deviceDetails -> DeviceSelection.matches(deviceDetails, deviceSelectionOptions))
            .collect(toImmutableSet());

    if (availableDevices.isEmpty()) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.OLCS_NO_AVAILABLE_DEVICE,
          "No available device is found.",
          /* cause= */ null);
    }
    return availableDevices;
  }

  /**
   * Waits for all devices specified in the {@code sessionRequestInfo} to become available.
   *
   * <p>This method repeatedly queries for device details until all requested device serials are
   * present. It will retry up to {@link #GET_DEVICE_FOR_ATS_SERVER_MAX_RETRY_ATTEMPTS} times,
   * waiting for {@link #GET_DEVICE_FOR_ATS_SERVER_RETRY_INTERVAL} between each attempt.
   *
   * <p>If any of the requested devices are still not found after all retry attempts, this method
   * will log a warning but will not throw an exception. The caller is responsible for handling the
   * case where some devices might be missing.
   *
   * @param sessionRequestInfo Information about the session request, including the list of
   *     requested device serials.
   * @throws MobileHarnessException if there's an error while retrieving device details.
   * @throws InterruptedException if the thread is interrupted while sleeping between retries.
   */
  private void waitForRequestedDevicesToBeReady(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    ImmutableSet<String> requestedSerials = ImmutableSet.copyOf(sessionRequestInfo.deviceSerials());
    Set<String> missingSerials = new HashSet<>(requestedSerials);
    int attempt = 0;

    while (!missingSerials.isEmpty() && attempt < GET_DEVICE_FOR_ATS_SERVER_MAX_RETRY_ATTEMPTS) {
      ImmutableMap<String, DeviceDetails> currentDevices =
          deviceDetailsRetriever.getAllAndroidDevicesWithNeededDetails(sessionRequestInfo);
      missingSerials.clear();
      missingSerials.addAll(requestedSerials);
      missingSerials.removeAll(currentDevices.keySet());

      if (!missingSerials.isEmpty()) {
        attempt++;
        if (attempt < GET_DEVICE_FOR_ATS_SERVER_MAX_RETRY_ATTEMPTS) {
          logger.atWarning().log(
              "Devices %s not found, retry attempt %d/%d in %s...",
              missingSerials,
              attempt,
              GET_DEVICE_FOR_ATS_SERVER_MAX_RETRY_ATTEMPTS,
              GET_DEVICE_FOR_ATS_SERVER_RETRY_INTERVAL);
          sleeper.sleep(GET_DEVICE_FOR_ATS_SERVER_RETRY_INTERVAL);
        }
      }
    }

    if (!missingSerials.isEmpty()) {
      logger.atWarning().log(
          "Devices %s are still missing after %d attempts.",
          missingSerials, GET_DEVICE_FOR_ATS_SERVER_MAX_RETRY_ATTEMPTS);
    }
  }

  private ImmutableList<SubDeviceSpec> pickAndroidOnlineDevices(
      SessionRequestInfo sessionRequestInfo,
      Set<String> allMatchAndroidOnlineDevices,
      int shardCount) {
    StringMap dimensions =
        StringMap.newBuilder()
            .putContent(
                Name.ID.lowerCaseName(),
                String.format(
                    "%s(%s)",
                    Value.PREFIX_REGEX, Joiner.on('|').join(allMatchAndroidOnlineDevices)))
            .build();
    SubDeviceSpec subDeviceSpec =
        SubDeviceSpec.newBuilder()
            .setType(getTradefedRequiredDeviceType(sessionRequestInfo))
            .setDimensions(dimensions)
            .build();
    if (shardCount <= 1 && !allMatchAndroidOnlineDevices.isEmpty()) {
      return ImmutableList.of(subDeviceSpec);
    }
    int numOfNeededDevices = min(allMatchAndroidOnlineDevices.size(), shardCount);
    return ImmutableList.copyOf(Collections.nCopies(numOfNeededDevices, subDeviceSpec));
  }

  private static String getTradefedRequiredDeviceType(SessionRequestInfo info) {
    if (Flags.instance().atsRunTfOnAndroidRealDevice.getNonNull()) {
      return ANDROID_REAL_DEVICE_TYPE;
    }
    return info.deviceType().orElse(ANDROID_DEVICE_TYPE);
  }

  public JobInfo createXtsTradefedTestJob(
      SessionRequestInfo sessionRequestInfo, TradefedJobInfo tradefedJobInfo)
      throws MobileHarnessException, InterruptedException {
    JobInfo jobInfo =
        JobInfoCreator.createJobInfo(
            tradefedJobInfo.jobConfig(),
            ImmutableList.of(),
            tradefedJobInfo.jobConfig().getGenFileDir(),
            createJobTmpDir(tradefedJobInfo.jobConfig().getName()).toString());
    addSessionClientIdToJobInfo(jobInfo, sessionRequestInfo);
    jobInfo.properties().add(Job.IS_XTS_TF_JOB, "true");
    injectCommonParams(jobInfo);
    if (Flags.instance().enablePersistentCache.getNonNull()) {
      urlForWorkerResolve(sessionRequestInfo)
          .ifPresent(url -> addUrlToPersistentCacheList(jobInfo, url));
    }
    tradefedJobInfo
        .extraJobProperties()
        .forEach((key, value) -> jobInfo.properties().add(key, value));
    printCreatedJobInfo(jobInfo, /* isTf= */ true);
    return jobInfo;
  }

  public static Optional<String> urlForWorkerResolve(SessionRequestInfo sessionRequestInfo) {
    if (Flags.instance().transferResourcesFromController.getNonNull()) {
      return sessionRequestInfo.androidXtsZip(); // Local url in controller.
    } else {
      return sessionRequestInfo
          .androidXtsZipDownloadUrl() // Remote download url.
          .filter(url -> isDownloadUrlSupported(url))
          .or(() -> sessionRequestInfo.androidXtsZip());
    }
  }

  private static boolean isDownloadUrlSupported(String url) {
    return url.startsWith(RemoteFileType.GCS.prefix());
  }

  private void addUrlToPersistentCacheList(JobInfo jobInfo, String url) {
    List<String> urls =
        new ArrayList<>(
            jobInfo.params().getList(JobInfo.PARAM_PERISTENT_CACHE_FILE_LIST, ImmutableList.of()));
    urls.add(url);
    jobInfo.params().addList(JobInfo.PARAM_PERISTENT_CACHE_FILE_LIST, urls);
  }

  /** Gets all local tradefed modules which doesn't include the mcts modules. */
  public ImmutableSet<String> getAllLocalTradefedModules(SessionRequestInfo sessionRequestInfo) {
    Path xtsRootDir = Path.of(sessionRequestInfo.xtsRootDir());
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      logger.atInfo().log(
          "xTS root dir [%s] doesn't exist, skip getting all TF modules.", xtsRootDir);
      return ImmutableSet.of();
    }
    String xtsType = sessionRequestInfo.xtsType();
    ImmutableMap<String, Configuration> configsMap =
        configurationUtil.getConfigsFromDirs(
            ImmutableList.of(XtsDirUtil.getXtsTestCasesDir(xtsRootDir, xtsType).toFile()));

    return configsMap.values().stream()
        .map(config -> config.getMetadata().getXtsModule())
        .collect(toImmutableSet());
  }

  private ImmutableSet<String> getStringSetFromResourceFile(String resPathInJar)
      throws MobileHarnessException {
    String filePath = resUtilProvider.get().getResourceFile(getClass(), resPathInJar);
    return localFileUtil.readLineListFromFile(filePath).stream()
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .collect(toImmutableSet());
  }

  /** Gets all local tradefed modules including the mcts modules from the static list. */
  public ImmutableSet<String> getStaticFullTradefedModules() throws MobileHarnessException {
    return getStringSetFromResourceFile(
        "/devtools/mobileharness/infra/controller/test/util/xtsdownloader/configs/cts_list.txt");
  }

  /** Gets all mcts modules from the static list. */
  public ImmutableSet<String> getStaticMctsModules() throws MobileHarnessException {
    return getStringSetFromResourceFile(
        "/devtools/mobileharness/infra/controller/test/util/xtsdownloader/configs/mcts_list.txt");
  }

  /**
   * Gets a list of filtered tradefed modules.
   *
   * <p>The list of modules is filtered by include/exclude filters and the given module names.
   *
   * @return a list of filtered tradefed modules.
   * @throws MobileHarnessException if no tradefed modules could satisfy the given filters.
   */
  public ImmutableList<String> getFilteredTradefedModules(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException {
    Path xtsRootDir = Path.of(sessionRequestInfo.xtsRootDir());
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.OLCS_INEXISTENT_XTS_ROOT_DIR,
          String.format("Inexistent xTS root dir: %s", xtsRootDir),
          /* cause= */ null);
    }

    ImmutableSet<String> localTfModules = getAllLocalTradefedModules(sessionRequestInfo);
    ImmutableSet<String> staticTfModules = getStaticMctsModules();
    ImmutableSet<String> allTfModules =
        Stream.concat(localTfModules.stream(), staticTfModules.stream()).collect(toImmutableSet());

    // For "run retry" command handled by TF, consider the module filter as include filter.
    boolean isTfRetryWithModules =
        SessionRequestHandlerUtil.isRunRetry(sessionRequestInfo.testPlan())
            && SessionHandlerHelper.useTfRetry(
                sessionRequestInfo.isAtsServerRequest(),
                sessionRequestInfo.xtsType(),
                sessionRequestInfo
                    .testSuiteInfo()
                    .map(testSuiteInfo -> testSuiteInfo.getTestSuiteVersion().orElse(null))
                    .orElse(null))
            && !sessionRequestInfo.moduleNames().isEmpty();

    ImmutableList<String> modules =
        isTfRetryWithModules ? ImmutableList.of() : sessionRequestInfo.moduleNames();
    ImmutableSet<String> givenMatchedTfModules =
        modules.isEmpty() ? allTfModules : matchModules(modules, allTfModules);

    // Filter modules by include/exclude filters.
    ImmutableList<SuiteTestFilter> includeFilters =
        Stream.concat(
                sessionRequestInfo.includeFilters().stream(),
                isTfRetryWithModules ? sessionRequestInfo.moduleNames().stream() : Stream.empty())
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
          && includeFilters.stream().noneMatch(filter -> filter.matchModuleName(module))) {
        continue;
      }
      filteredModulesBuilder.add(module);
    }
    ImmutableList<String> filteredModules = filteredModulesBuilder.build();

    if (filteredModules.isEmpty()) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.XTS_NO_MATCHED_TRADEFED_MODULES,
          String.format("No matched tradefed modules from the given modules: %s", modules),
          /* cause= */ null);
    }

    return filteredModules;
  }

  /** Initializes a {@link JobConfig} for a tradefed job. */
  public JobConfig initializeJobConfig(
      SessionRequestInfo sessionRequestInfo,
      Map<String, String> driverParams,
      ImmutableList<SubDeviceSpec> subDeviceSpecList,
      ImmutableMultimap<String, String> jobFiles)
      throws InterruptedException, MobileHarnessException {
    String testPlan = sessionRequestInfo.testPlan();
    String xtsType = sessionRequestInfo.xtsType();

    // TODO: migrate multi-device tests to non-TF
    int minDeviceCount = testPlan.matches(xtsType + "-multi-?device") ? 2 : 1;
    if (subDeviceSpecList.size() < minDeviceCount) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.OLCS_NO_ENOUGH_MATCHED_DEVICES,
          "Found no enough devices to create the job config.",
          /* cause= */ null);
    }

    Duration jobTimeout =
        sessionRequestInfo.jobTimeout().isZero()
            ? DEFAULT_TRADEFED_JOB_TIMEOUT
            : sessionRequestInfo.jobTimeout();
    Duration testTimeout = calculateTestTimeout(jobTimeout);
    Duration startTimeout =
        sessionRequestInfo.startTimeout().isZero()
            ? DEFAULT_TRADEFED_START_TIMEOUT
            : sessionRequestInfo.startTimeout();

    String name = "xts-tradefed-test-job";
    Path jobGenDir = createJobGenDir(name);
    JobConfig.Builder jobConfigBuilder =
        JobConfig.newBuilder()
            .setName(name)
            .setExecMode("local")
            .setJobTimeoutSec(saturatedCast(jobTimeout.toSeconds()))
            .setTestTimeoutSec(saturatedCast(testTimeout.toSeconds()))
            .setStartTimeoutSec(startTimeout.toSeconds())
            .setPriority(Priority.HIGH)
            .setTestAttempts(1)
            .setTests(
                StringList.newBuilder().addContent(String.format("xts-tradefed-test-%s", testPlan)))
            .setDevice(DeviceList.newBuilder().addAllSubDeviceSpec(subDeviceSpecList))
            .setDriver(
                Driver.newBuilder()
                    .setName("TradefedTest")
                    .setParam(new Gson().toJson(driverParams)))
            .setGenFileDir(jobGenDir.toString());
    if (!jobFiles.isEmpty()) {
      jobConfigBuilder.setFiles(
          FileConfigList.newBuilder()
              .addAllContent(
                  jobFiles.asMap().entrySet().stream()
                      .map(
                          entry ->
                              FileConfig.newBuilder()
                                  .setTag(entry.getKey())
                                  .addAllPath(entry.getValue())
                                  .build())
                      .collect(toImmutableList())));
    }
    JobConfig jobConfig = jobConfigBuilder.build();
    logger.atInfo().log("TradefedTest job config: %s", shortDebugString(jobConfig));
    return jobConfig;
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

    Path xtsRootDir = Path.of(sessionRequestInfo.xtsRootDir());
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      logger.atInfo().log(
          "xTS root dir [%s] doesn't exist, skip creating non-tradefed jobs.", xtsRootDir);
      return updatedSessionRequestInfo.build();
    }

    String xtsType = sessionRequestInfo.xtsType();
    ImmutableMap<String, Configuration> configsMap =
        configurationUtil.getConfigsV2FromDirs(
            ImmutableList.of(XtsDirUtil.getXtsTestCasesDir(xtsRootDir, xtsType).toFile()));
    updatedSessionRequestInfo.setV2ConfigsMap(configsMap);

    // Gets expanded modules with abi and module parameters (if any).
    TestSuiteHelper testSuiteHelper =
        getTestSuiteHelper(xtsRootDir.toString(), xtsType, sessionRequestInfo);
    updatedSessionRequestInfo.setExpandedModules(
        ImmutableMap.copyOf(
            testSuiteHelper.loadTests(sessionRequestInfo.deviceInfo().orElse(null))));

    ImmutableList<String> modules = sessionRequestInfo.moduleNames();
    ImmutableSet<String> allNonTfModules = getNonTfModules(configsMap);
    updatedSessionRequestInfo.setGivenMatchedNonTfModules(matchModules(modules, allNonTfModules));
    return updatedSessionRequestInfo.build();
  }

  public static boolean shouldEnableModuleSharding(SessionRequestInfo sessionRequestInfo) {
    return sessionRequestInfo.shardingMode().equals(ShardingMode.MODULE)
        && sessionRequestInfo.testName().isEmpty()
        && !SessionRequestHandlerUtil.isRunRetry(sessionRequestInfo.testPlan());
  }

  public Optional<DeviceInfo> getDeviceInfo(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    // Wait for devices to be ready for ATS server requests.
    if (sessionRequestInfo.isAtsServerRequest() && !sessionRequestInfo.deviceSerials().isEmpty()) {
      waitForRequestedDevicesToBeReady(sessionRequestInfo);
    }
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
    } catch (MobileHarnessException e) {
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
    ImmutableSet<String> allLocalAndroidDevices =
        deviceDetailsRetriever.getAllAndroidDevicesWithNeededDetails(sessionRequestInfo).keySet();
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
   * <p>Non-tradefed jobs can be created if:
   *
   * <ul>
   *   <li>The test plan is a retry.
   *   <li>Or, no specific modules were requested.
   *   <li>Or, at least one of the requested modules is a non-tradefed module.
   * </ul>
   *
   * @return true if non-tradefed jobs can be created.
   */
  public boolean canCreateNonTradefedJobs(SessionRequestInfo sessionRequestInfo) {
    if (isRunRetry(sessionRequestInfo.testPlan())) {
      return true;
    }
    return sessionRequestInfo.moduleNames().isEmpty()
        || !sessionRequestInfo.givenMatchedNonTfModules().isEmpty();
  }

  /**
   * Creates non-tradefed jobs based on the {@code SessionRequestInfo}.
   *
   * @return a list of added non-tradefed jobInfos.
   */
  public ImmutableList<JobInfo> createXtsNonTradefedJobs(
      SessionRequestInfo sessionRequestInfo,
      @Nullable SubPlan subPlan,
      Map<XtsPropertyName, String> extraJobProperties)
      throws MobileHarnessException, InterruptedException {
    ImmutableSet<String> givenMatchedNonTfModules = sessionRequestInfo.givenMatchedNonTfModules();
    ImmutableList.Builder<JobInfo> jobInfos = ImmutableList.builder();

    ImmutableSet<String> availableDeviceSerials;
    if (sessionRequestInfo.isAtsServerRequest() && !sessionRequestInfo.deviceSerials().isEmpty()) {
      availableDeviceSerials = ImmutableSet.copyOf(sessionRequestInfo.deviceSerials());
    } else {
      availableDeviceSerials =
          getAvailableDevices(sessionRequestInfo).stream()
              .map(DeviceDetails::id)
              .collect(toImmutableSet());
    }
    final String deviceSerialsDimensionValue =
        availableDeviceSerials.isEmpty()
            ? null
            : String.format(
                "%s(%s)", Value.PREFIX_REGEX, Joiner.on('|').join(availableDeviceSerials));

    ImmutableMap<String, String> moduleNameToConfigFilePathMap =
        sessionRequestInfo.v2ConfigsMap().entrySet().stream()
            .collect(toImmutableMap(e -> e.getValue().getMetadata().getXtsModule(), Entry::getKey));

    String testPlan =
        (SessionRequestHandlerUtil.isRunRetry(sessionRequestInfo.testPlan()) && subPlan != null)
            ? subPlan.getPreviousSessionXtsTestPlan()
            : sessionRequestInfo.testPlan();
    TestPlanFilter testPlanFilter =
        testPlanParser.parseFilters(
            Path.of(sessionRequestInfo.xtsRootDir()), sessionRequestInfo.xtsType(), testPlan);

    if (!testPlanFilter.tests().contains(COMPATIBILITY_TEST_SUITE_CLASS_NAME)) {
      logger
          .atInfo()
          .with(IMPORTANCE, IMPORTANT)
          .log(
              "The test plan %s is not for compatibility test suite. Skip creating non-tradefed"
                  + " jobs. Plan includes tests: %s.",
              testPlan, testPlanFilter.tests());
      return ImmutableList.of();
    }

    ImmutableList<SuiteTestFilter> includeFilters;
    if (subPlan == null) {
      includeFilters =
          Stream.concat(
                  sessionRequestInfo.includeFilters().stream(),
                  testPlanFilter.includeFilters().stream())
              .map(SuiteTestFilter::create)
              .collect(toImmutableList());
    } else {
      includeFilters =
          subPlan.getNonTfIncludeFiltersMultimap().entries().stream()
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
    if (subPlan != null) {
      ImmutableList<SuiteTestFilter> subplanExcludeFilters =
          subPlan.getNonTfExcludeFiltersMultimap().entries().stream()
              .map(
                  e ->
                      e.getKey()
                          + (e.getValue().equals(SubPlan.ALL_TESTS_IN_MODULE)
                              ? ""
                              : " " + e.getValue()))
              .map(SuiteTestFilter::create)
              .collect(toImmutableList());
      excludeFilters =
          Stream.concat(excludeFilters.stream(), subplanExcludeFilters.stream())
              .collect(toImmutableList());
    }
    logger.atInfo().log("Exclude filters for Non-TF run: %s", excludeFilters);

    ImmutableMultimap<String, String> moduleMetadataIncludeFilters =
        ImmutableMultimap.<String, String>builder()
            .putAll(sessionRequestInfo.moduleMetadataIncludeFilters())
            .putAll(testPlanFilter.moduleMetadataIncludeFilters())
            .build();
    ImmutableMultimap<String, String> moduleMetadataExcludeFilters =
        ImmutableMultimap.<String, String>builder()
            .putAll(sessionRequestInfo.moduleMetadataExcludeFilters())
            .putAll(testPlanFilter.moduleMetadataExcludeFilters())
            .build();

    Duration jobTimeout =
        sessionRequestInfo.jobTimeout().isZero()
            ? DEFAULT_NON_TRADEFED_JOB_TIMEOUT
            : sessionRequestInfo.jobTimeout();
    Duration testTimeout = calculateTestTimeout(jobTimeout);
    Duration startTimeout =
        sessionRequestInfo.startTimeout().isZero()
            ? DEFAULT_NON_TRADEFED_START_TIMEOUT
            : sessionRequestInfo.startTimeout();

    Path xtsRootDir = Path.of(sessionRequestInfo.xtsRootDir());
    String xtsType = sessionRequestInfo.xtsType();
    // Reads DeviceConfigurations text proto.
    Path xtsDeviceConfigFile = getXtsDeviceConfigFilePath(xtsRootDir, xtsType);
    DeviceConfigurations xtsDeviceConfig = readXtsDeviceConfigFile(xtsDeviceConfigFile);
    ImmutableMap<String, ModuleDeviceConfiguration> moduleDeviceConfigurations =
        groupXtsDeviceConfig(xtsDeviceConfig);
    ImmutableListMultimap.Builder<String, ModuleArg> moduleArgMapBuilder =
        ImmutableListMultimap.builder();
    for (String moduleArgStr : sessionRequestInfo.moduleArgs()) {
      ModuleArg moduleArg = ModuleArg.create(moduleArgStr);
      moduleArgMapBuilder.put(moduleArg.moduleName(), moduleArg);
    }
    ImmutableListMultimap<String, ModuleArg> moduleArgMap = moduleArgMapBuilder.build();
    ImmutableSet<String> simpleExcludeRunners =
        sessionRequestInfo.excludeRunners().stream()
            .map(ConfigurationUtil::getSimpleClassName)
            .collect(toImmutableSet());

    for (Entry<String, Configuration> entry :
        sessionRequestInfo.expandedModules().entrySet().stream()
            .filter(e -> e.getValue().getMetadata().getIsConfigV2())
            .collect(toImmutableList())) {
      String originalModuleName = entry.getValue().getMetadata().getXtsModule();
      String expandedModuleName = entry.getKey();

      String runner = entry.getValue().getTest().getClazz();
      // Skip the module if the runner is excluded
      if (simpleExcludeRunners.contains(ConfigurationUtil.getSimpleClassName(runner))) {
        logger.atInfo().log(
            "Skipping module %s because the runner %s is excluded", expandedModuleName, runner);
        continue;
      }

      // If it has a subplan(either from the retry command or the subplan command), do a early check
      // for whether the module should be run
      if (subPlan != null
          && !subPlan.getNonTfIncludeFiltersMultimap().containsKey(expandedModuleName)) {
        continue;
      }
      if (givenMatchedNonTfModules.isEmpty()
          || givenMatchedNonTfModules.contains(originalModuleName)) {
        // Gets module abi
        String moduleAbi = getModuleAbi(expandedModuleName).orElse(null);
        // Gets module parameter
        String moduleParameter = getModuleParameter(expandedModuleName).orElse(null);

        // Filters the module by metadata include-filter and exclude-filter.
        ImmutableListMultimap<String, String> moduleMetadata =
            entry.getValue().getConfigDescriptor().getMetadataMap().values().stream()
                .flatMap(
                    metadata ->
                        metadata.getValueList().stream()
                            .map(value -> Maps.immutableEntry(metadata.getKey(), value)))
                .collect(toImmutableListMultimap(Entry::getKey, Entry::getValue));
        if (!filterModuleByConfigMetadata(
            moduleMetadata, moduleMetadataIncludeFilters, moduleMetadataExcludeFilters)) {
          continue;
        }

        // Handles exclude-filter.
        Map<String, Collection<String>> excludeTestCaseMap = new HashMap<>();
        if (excludeFilters.stream()
            .filter(
                excludeFilter ->
                    excludeFilter.matchModule(originalModuleName, moduleAbi, moduleParameter))
            .anyMatch(
                excludeFilter -> {
                  if (excludeFilter.testName().isPresent()) {
                    parseExcludeTestNames(excludeFilter.testName().get(), excludeTestCaseMap);
                    return false;
                  }
                  return true;
                })) {
          // Excludes the whole module.
          continue;
        }

        // Handles include-filter.
        Map<String, Collection<String>> includeTestCaseMap = new HashMap<>();
        if (sessionRequestInfo.testName().isPresent()) {
          parseIncludeTestNames(sessionRequestInfo.testName().get(), includeTestCaseMap);
        } else if (!includeFilters.isEmpty()) {
          boolean matched = false;
          for (SuiteTestFilter filter : includeFilters) {
            if (filter.matchModule(originalModuleName, moduleAbi, moduleParameter)) {
              matched = true;
              if (filter.testName().isPresent()) {
                parseIncludeTestNames(filter.testName().get(), includeTestCaseMap);
              }
            }
          }
          if (!matched) {
            continue;
          }
        }

        ImmutableList.Builder<String> matchedTestCases = ImmutableList.builder();
        if (entry.getValue().getOptionsList().stream()
            .anyMatch(option -> option.getName().equals("test_path"))) {
          // TODO: remove once exclude filter is supported.
          if (!excludeTestCaseMap.isEmpty()) {
            logger.atWarning().log(
                "Ignore exclude filter as it is not supported for modules with \"test_path\""
                    + " option.");
          }
          for (Entry<String, Collection<String>> testCaseEntry : includeTestCaseMap.entrySet()) {
            if (testCaseEntry.getValue().isEmpty()) {
              matchedTestCases.add(testCaseEntry.getKey());
            } else {
              matchedTestCases.addAll(testCaseEntry.getValue());
            }
          }
        } else if (!includeTestCaseMap.isEmpty() || !excludeTestCaseMap.isEmpty()) {
          try {
            ImmutableSetMultimap<String, String> allTestCases =
                moblyTestLoader.getTestNamesInModule(
                    Path.of(requireNonNull(moduleNameToConfigFilePathMap.get(originalModuleName))),
                    entry.getValue());
            if (includeTestCaseMap.isEmpty()) {
              includeTestCaseMap = allTestCases.asMap();
            }
            for (Entry<String, Collection<String>> testCaseEntry : includeTestCaseMap.entrySet()) {
              String testClass = testCaseEntry.getKey();
              if (!allTestCases.containsKey(testClass)) {
                logger
                    .atWarning()
                    .with(IMPORTANCE, IMPORTANT)
                    .log("Test class %s not found in module %s", testClass, originalModuleName);
                continue;
              }
              Collection<String> includeTestCases =
                  testCaseEntry.getValue().isEmpty()
                      ? allTestCases.get(testClass)
                      : testCaseEntry.getValue();
              if (excludeTestCaseMap.containsKey(testClass)) {
                Collection<String> excludeTestCases = excludeTestCaseMap.get(testClass);
                // Exclude all test cases in the test class
                if (excludeTestCases.isEmpty()) {
                  continue;
                }
                matchedTestCases.addAll(
                    includeTestCases.stream()
                        .filter(testName -> !excludeTestCases.contains(testName))
                        .collect(toImmutableList()));
              } else {
                matchedTestCases.addAll(includeTestCases);
              }
            }
            if (matchedTestCases.build().isEmpty()) {
              logger
                  .atWarning()
                  .with(IMPORTANCE, IMPORTANT)
                  .log(
                      "No test cases left after filtering.\nIncludes: %s.\nExcludes: %s\n"
                          + "No job created for this module: %s.",
                      includeTestCaseMap, excludeTestCaseMap, expandedModuleName);
              continue;
            }
          } catch (MobileHarnessException e) {
            logger
                .atWarning()
                .with(IMPORTANCE, IMPORTANT)
                .withCause(e)
                .log(
                    "Failed to get all test names from module %s. Will run all test cases in the"
                        + " module",
                    originalModuleName);
          }
        }

        JobInfo jobInfo =
            createXtsNonTradefedJob(
                xtsRootDir,
                xtsType,
                sessionRequestInfo.testPlan(),
                subPlan == null ? null : subPlan.getPreviousSessionXtsTestPlan(),
                Path.of(requireNonNull(moduleNameToConfigFilePathMap.get(originalModuleName))),
                entry.getValue(),
                moduleDeviceConfigurations.getOrDefault(
                    originalModuleName, /* defaultValue= */ null),
                originalModuleName,
                expandedModuleName,
                moduleAbi,
                moduleParameter,
                moduleArgMap,
                matchedTestCases.build(),
                jobTimeout,
                testTimeout,
                startTimeout,
                isSkipDeviceInfo(sessionRequestInfo, subPlan),
                sessionRequestInfo.xtsSuiteInfo(),
                sessionRequestInfo.isAtsServerRequest());

        getSimCardTypeDimensionValue(moduleMetadata)
            .ifPresent(
                simCardTypeDimensionValue ->
                    jobInfo.subDeviceSpecs().getAllSubDevices().stream()
                        // Only add sim card type dimension to Android real devices.
                        .filter(
                            subDeviceSpec -> subDeviceSpec.type().equals(ANDROID_REAL_DEVICE_TYPE))
                        .forEach(
                            subDeviceSpec ->
                                subDeviceSpec
                                    .deviceRequirement()
                                    .dimensions()
                                    .add(Name.SIM_CARD_TYPE, simCardTypeDimensionValue)));

        if (deviceSerialsDimensionValue != null) {
          jobInfo
              .subDeviceSpecs()
              .getAllSubDevices()
              .forEach(
                  subDeviceSpec ->
                      subDeviceSpec
                          .deviceRequirement()
                          .dimensions()
                          .add(Name.ID, deviceSerialsDimensionValue));
        }
        addSessionClientIdToJobInfo(jobInfo, sessionRequestInfo);
        extraJobProperties.forEach((key, value) -> jobInfo.properties().add(key, value));
        if (sessionRequestInfo.isMoblyResultstoreUploadEnabled().orElse(false)) {
          jobInfo.properties().add(XtsConstants.IS_MOBLY_RESULTSTORE_UPLOAD_ENABLED, "true");
        }
        printCreatedJobInfo(jobInfo, /* isTf= */ false);
        jobInfos.add(jobInfo);
      }
    }

    return jobInfos.build();
  }

  /**
   * Applies the metadata filter to see if the module should run.
   *
   * @param includeFilters the metadata include filter
   * @param excludeFilters the metadata exclude filter
   * @return True if the module should run, false otherwise.
   */
  @VisibleForTesting
  static boolean filterModuleByConfigMetadata(
      ImmutableListMultimap<String, String> moduleMetadata,
      ImmutableMultimap<String, String> includeFilters,
      ImmutableMultimap<String, String> excludeFilters) {
    if (!includeFilters.isEmpty()
        && !includeFilters.entries().stream()
            .allMatch(filter -> moduleMetadata.containsEntry(filter.getKey(), filter.getValue()))) {
      return false;
    }
    if (!excludeFilters.isEmpty()
        && excludeFilters.entries().stream()
            .anyMatch(filter -> moduleMetadata.containsEntry(filter.getKey(), filter.getValue()))) {
      return false;
    }
    return true;
  }

  /**
   * Gets the sim card type dimension value from the module's configuration descriptor metadata.
   *
   * @param moduleMetadata the module metadata
   * @return the sim card type dimension value, or empty if not found
   */
  @VisibleForTesting
  static Optional<String> getSimCardTypeDimensionValue(
      ImmutableListMultimap<String, String> moduleMetadata) {
    return moduleMetadata.get("token").stream()
        .filter(
            tokenValue -> getIfPresent(Dimension.SimCardTypeValue.class, tokenValue).isPresent())
        .findFirst();
  }

  private DeviceConfigurations readXtsDeviceConfigFile(Path xtsDeviceConfigFile)
      throws MobileHarnessException {
    if (!localFileUtil.isFileExist(xtsDeviceConfigFile)) {
      logger.atWarning()
          // TODO: Enables with(IMPORTANCE, IMPORTANT) after the feature is ready
          .log("Device config file [%s] not found", xtsDeviceConfigFile);
      return DeviceConfigurations.getDefaultInstance();
    }
    try {
      String fileContent = localFileUtil.readFile(xtsDeviceConfigFile);
      return ProtoTextFormat.parse(fileContent, DeviceConfigurations.class);
    } catch (MobileHarnessException | ParseException e) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.XTS_DEVICE_CONFIG_FILE_PARSE_ERROR,
          String.format("Failed to read device config file [%s]", xtsDeviceConfigFile),
          e);
    }
  }

  /** Key is original module name. Value is configured but unvalidated module device config. */
  private static ImmutableMap<String, ModuleDeviceConfiguration> groupXtsDeviceConfig(
      DeviceConfigurations xtsDeviceConfig) throws MobileHarnessException {
    ImmutableMap.Builder<String, ModuleDeviceConfiguration> result = ImmutableMap.builder();
    for (ModuleDeviceConfiguration moduleDeviceConfiguration : xtsDeviceConfig.getModuleList()) {
      result.put(moduleDeviceConfiguration.getName(), moduleDeviceConfiguration);
    }
    try {
      return result.buildOrThrow();
    } catch (IllegalArgumentException e) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.XTS_DEVICE_CONFIG_FILE_VALIDATE_ERROR, "Invalid device config", e);
    }
  }

  private static Path getXtsDeviceConfigFilePath(Path xtsRootDir, String xtsType) {
    // TODO: Support specifying path from ATS console.
    return XtsDirUtil.getXtsToolsDir(xtsRootDir, xtsType)
        .resolve("device_configurations.textproto");
  }

  private void parseIncludeTestNames(String testName, Map<String, Collection<String>> filterMap) {
    parseTestName(testName, filterMap, /* isExcluded= */ false);
  }

  private void parseExcludeTestNames(String testName, Map<String, Collection<String>> filterMap) {
    parseTestName(testName, filterMap, /* isExcluded= */ true);
  }

  /**
   * Parses the test name and adds it to the filter map.
   *
   * <p>The key of the filter map is the test class name. The value is a collection of specific test
   * case names in the format "TestClassName.TestCaseName". An empty collection indicates that the
   * entire test class is included or excluded.
   *
   * <p>For include filter, includes specified test cases if test class and test case are both
   * specified. For exclude filter, excludes the whole test class if test class and test case are
   * both specified.
   *
   * <p>TestName is set with pattern "TestClassName#TestCaseName" while Mobly needs the pattern
   * "TestClassName.TestCaseName".
   */
  private void parseTestName(
      String testName, Map<String, Collection<String>> filterMap, boolean isExcluded) {
    List<String> list =
        Splitter.on('#').limit(2).trimResults().omitEmptyStrings().splitToList(testName);
    String testClass = list.get(0);
    if (list.size() == 1) {
      if (isExcluded) {
        filterMap.put(testClass, new HashSet<>());
      } else {
        filterMap.putIfAbsent(testClass, new HashSet<>());
      }
    } else if (list.size() == 2) {
      if (isExcluded && filterMap.containsKey(testClass) && filterMap.get(testClass).isEmpty()) {
        return;
      }
      filterMap.computeIfAbsent(testClass, k -> new HashSet<>()).add(Joiner.on('.').join(list));
    }
  }

  private JobInfo createXtsNonTradefedJob(
      Path xtsRootDir,
      String xtsType,
      String testPlan,
      @Nullable String previousSessionTestPlan,
      Path moduleConfigPath,
      Configuration moduleConfig,
      @Nullable ModuleDeviceConfiguration moduleDeviceConfig,
      String originalModuleName,
      String expandedModuleName,
      @Nullable String moduleAbi,
      @Nullable String moduleParameter,
      ImmutableMultimap<String, ModuleArg> moduleArgMap,
      ImmutableList<String> matchedTestCases,
      Duration jobTimeout,
      Duration testTimeout,
      Duration startTimeout,
      boolean skipDeviceInfo,
      ImmutableMap<String, String> xtsSuiteInfo,
      boolean isAtsServerRequest)
      throws MobileHarnessException, InterruptedException {
    JobInfo jobInfo =
        createBaseXtsNonTradefedJob(
            moduleConfig, expandedModuleName, jobTimeout, testTimeout, startTimeout);

    Path moduleDir = moduleConfigPath.getParent();
    ImmutableList<File> fileDepDirs =
        ImmutableList.of(
            moduleDir.toFile(), XtsDirUtil.getXtsTestCasesDir(xtsRootDir, xtsType).toFile());

    moduleConfigurationHelper.updateJobInfo(jobInfo, moduleConfig, moduleDeviceConfig, fileDepDirs);

    // Add job params and files from module args. Support module name with abi and parameter.
    Map<String, String> params = new HashMap<>();
    ListMultimap<String, String> files = ArrayListMultimap.create();
    getModuleArgs(originalModuleName, moduleArgMap, params, files);
    if (!isNullOrEmpty(moduleParameter)) {
      getModuleArgs(
          String.format("%s[%s]", originalModuleName, moduleParameter),
          moduleArgMap,
          params,
          files);
    }
    if (!isNullOrEmpty(moduleAbi)) {
      getModuleArgs(
          String.format("%s %s", moduleAbi, originalModuleName), moduleArgMap, params, files);
    }
    if (!isNullOrEmpty(moduleAbi) && !isNullOrEmpty(moduleParameter)) {
      getModuleArgs(expandedModuleName, moduleArgMap, params, files);
    }
    jobInfo.params().addAll(params);
    for (Entry<String, Collection<String>> file : files.asMap().entrySet()) {
      jobInfo.files().replaceAll(file.getKey(), file.getValue());
    }

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
    jobInfo.properties().add(Job.SKIP_COLLECTING_DEVICE_INFO, Boolean.toString(skipDeviceInfo));
    if (!matchedTestCases.isEmpty()) {
      jobInfo.params().add(MoblyTestSpec.TEST_SELECTOR_KEY, Joiner.on(" ").join(matchedTestCases));
    }
    if (!isAtsServerRequest
        && jobInfo.params().getOptional(MoblyAospTestSpec.PARAM_VENV_PATH).isEmpty()) {
      // Use the venv in the module directory if it's not specified by users. The venv will be
      // created by the test driver if it doesn't exist.
      jobInfo.params().add(MoblyAospTestSpec.PARAM_VENV_PATH, moduleDir.resolve("venv").toString());
    }
    jobInfo
        .params()
        .add(NonTradefedReportGeneratorConstants.PARAM_RUN_CERTIFICATION_TEST_SUITE, "true");
    jobInfo
        .params()
        .add(
            NonTradefedReportGeneratorConstants.PARAM_XTS_SUITE_INFO,
            generateXtsSuiteInfoMap(
                xtsRootDir.toAbsolutePath().toString(),
                xtsType,
                previousSessionTestPlan != null ? previousSessionTestPlan : testPlan,
                isRunRetry(testPlan),
                xtsSuiteInfo));

    // TODO: Add multi hosts mode support.
    jobInfo
        .params()
        .add("xts_test_dir", XtsDirUtil.getXtsTestCasesDir(xtsRootDir, xtsType).toString());
    injectCommonParams(jobInfo);
    return jobInfo;
  }

  /** Injects common params to the job info for both tradefed and non-tradefed jobs. */
  private void injectCommonParams(JobInfo jobInfo) {
    // Skip to clear gservice flag overrides.
    jobInfo.params().add(AndroidRealDeviceConstants.PARAM_CLEAR_GSERVICES_OVERRIDES, "false");
    // Skip to check installed gms core version.
    jobInfo.params().add(InstallApkStepConstants.PARAM_CHECK_INSTALLED_GMS_CORE_VERSION, "false");
  }

  private String generateXtsSuiteInfoMap(
      String xtsRootDir,
      String xtsType,
      String testPlan,
      boolean isRunRetry,
      ImmutableMap<String, String> xtsSuiteInfoFromClient) {
    Map<String, String> xtsSuiteInfoMap = new HashMap<>();
    if (xtsSuiteInfoFromClient.isEmpty()) {
      logger.atInfo().log("No xTS suite info from client, regenerating xTS suite info.");
      xtsSuiteInfoMap =
          certificationSuiteInfoFactory.generateSuiteInfoMap(xtsRootDir, xtsType, testPlan);
    } else {
      xtsSuiteInfoMap.putAll(xtsSuiteInfoFromClient);
      if (isRunRetry) {
        // Overwrite the suite plan and suite variant if it's run retry
        xtsSuiteInfoMap.put(SuiteCommon.SUITE_PLAN, testPlan);
        xtsSuiteInfoMap.put(
            SuiteCommon.SUITE_VARIANT,
            certificationSuiteInfoFactory.getSuiteVariant(
                testPlan, xtsSuiteInfoFromClient.getOrDefault(SuiteCommon.SUITE_NAME, "")));
      }
    }
    return Joiner.on(",").withKeyValueSeparator("=").join(xtsSuiteInfoMap);
  }

  private JobInfo createBaseXtsNonTradefedJob(
      Configuration moduleConfig,
      String expandedModuleName,
      Duration jobTimeout,
      Duration testTimeout,
      Duration startTimeout)
      throws MobileHarnessException, InterruptedException {
    List<Device> moduleDevices = moduleConfig.getDevicesList();
    if (moduleDevices.isEmpty()) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.XTS_NO_DEVICE_SPEC_DEFINED,
          String.format(
              "Found no devices to create the job config for xts non-tradefed job with module"
                  + " '%s'.",
              expandedModuleName),
          /* cause= */ null);
    }

    List<SubDeviceSpec> subDeviceSpecList = new ArrayList<>();
    for (Device device : moduleDevices) {
      if (device.getName().isEmpty()) {
        throw MobileHarnessExceptionFactory.createUserFacingException(
            InfraErrorId.XTS_ILLEGAL_DEVICE_SPEC,
            String.format(
                "Device name is missing in a <device> in module '%s'", expandedModuleName),
            /* cause= */ null);
      } else {
        subDeviceSpecList.add(SubDeviceSpec.newBuilder().setType(device.getName()).build());
      }
    }

    String driverName = moduleConfig.getTest().getClazz();
    if (isNullOrEmpty(driverName)) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.XTS_MODULE_CONFIG_MISSING_DRIVER_NAME,
          String.format("Found no driver name in <test> in module '%s'.", expandedModuleName),
          /* cause= */ null);
    }
    String name = String.format("xts-mobly-aosp-job-%s", expandedModuleName.replace(' ', '_'));
    Path jobGenDir = createJobGenDir(name);
    Path jobTmpDir = createJobTmpDir(name);
    JobConfig jobConfig =
        JobConfig.newBuilder()
            .setName(name)
            .setExecMode("local")
            .setJobTimeoutSec(saturatedCast(jobTimeout.toSeconds()))
            .setTestTimeoutSec(saturatedCast(testTimeout.toSeconds()))
            .setStartTimeoutSec(startTimeout.toSeconds())
            .setPriority(Priority.HIGH)
            .setTestAttempts(1)
            .setTests(
                StringList.newBuilder()
                    .addContent(
                        String.format(
                            "xts-mobly-aosp-test-%s", expandedModuleName.replace(' ', '_'))))
            .setDevice(DeviceList.newBuilder().addAllSubDeviceSpec(subDeviceSpecList))
            .setDriver(Driver.newBuilder().setName(driverName))
            .setGenFileDir(jobGenDir.toString())
            .setAllocationExitStrategy(AllocationExitStrategy.FAIL_FAST_NO_MATCH)
            .build();
    logger.atInfo().log(
        "Non-tradefed job base config for module '%s': %s",
        expandedModuleName, shortDebugString(jobConfig));

    return JobInfoCreator.createJobInfo(
        jobConfig, ImmutableList.of(), jobGenDir.toString(), jobTmpDir.toString());
  }

  public String getHostIp(String deviceSerial) throws MobileHarnessException, InterruptedException {
    DeviceQueryResult queryResult;
    try {
      queryResult = deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_COMMAND_QUERY_DEVICE_ERROR, "Failed to query device", e);
    }
    Optional<com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo>
        queryDeviceInfo =
            queryResult.getDeviceInfoList().stream()
                .filter(deviceInfo -> deviceInfo.getId().equals(deviceSerial))
                .findFirst();
    if (queryDeviceInfo.isEmpty()) {
      logger.atWarning().log(
          "Cannot find device info from master for device serial: %s", deviceSerial);
      return "";
    }
    Optional<String> hostIp =
        queryDeviceInfo.get().getDimensionList().stream()
            .filter(dimension -> dimension.getName().equals(Ascii.toLowerCase(Name.HOST_IP.name())))
            .findFirst()
            .map(DeviceQuery.Dimension::getValue);
    if (hostIp.isEmpty() || hostIp.get().isEmpty()) {
      logger.atWarning().log("Cannot find host ip from master for device serial: %s", deviceSerial);
      return "";
    }
    return hostIp.get();
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

  public static boolean isRunRetry(String testPlan) {
    return Ascii.equalsIgnoreCase(testPlan, "retry");
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

  /** Gets all the non-tradefed modules from the config map. */
  public static ImmutableSet<String> getNonTfModules(
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

  private static Duration calculateTestTimeout(Duration jobTimeout) {
    return jobTimeout.compareTo(JOB_TEST_TIMEOUT_DIFF.multipliedBy(2L)) < 0
        ? jobTimeout.dividedBy(2L)
        : jobTimeout.minus(JOB_TEST_TIMEOUT_DIFF);
  }

  private static boolean isSkipDeviceInfo(
      SessionRequestInfo sessionRequestInfo, @Nullable SubPlan subPlan) {
    return sessionRequestInfo.skipDeviceInfo().orElse(false)
        || (isRunRetry(sessionRequestInfo.testPlan())
            && subPlan != null
            && subPlan.getPreviousSessionDeviceBuildFingerprint().orElse("").isEmpty());
  }

  private static void getModuleArgs(
      String moduleIdentifier,
      ImmutableMultimap<String, ModuleArg> moduleArgMap,
      Map<String, String> params,
      ListMultimap<String, String> files) {
    for (ModuleArg moduleArg : moduleArgMap.get(moduleIdentifier)) {
      if (moduleArg.isFile()) {
        files.put(moduleArg.argName(), moduleArg.argValue());
      } else {
        params.put(moduleArg.argName(), moduleArg.argValue());
      }
    }
  }

  @VisibleForTesting
  static boolean needTestHarnessPropertyFalse(SessionRequestInfo sessionRequestInfo) {
    return XTS_TYPE_THAT_NEED_TEST_HARNESS_PROPERTY_FALSE.stream()
        .anyMatch(xtsType -> sessionRequestInfo.xtsType().startsWith(xtsType));
  }
}
