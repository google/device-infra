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

package com.google.devtools.mobileharness.shared.util.jobconfig;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.Files;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.api.proto.Device.DeviceSpec;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.sharedpool.SharedPoolJobUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.job.JobTypeUtil;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ScopedSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecWalker;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.DeviceList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.Driver;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.FileConfigList.FileConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;
import com.google.wireless.qa.mobileharness.shared.util.FlagUtil;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import javax.annotation.Nullable;

/** A utility to create JobInfo from different JobConfigs. */
public final class JobInfoCreator {
  // Do not use the constant defined in InstallApkStep.java, it will include lots of unnecessary
  // libraries.
  private static final String TAG_BUILD_APK = "build_apk";

  /** File tag name of DeviceSpec textproto. */
  private static final String TAG_DEVICE_SPEC = "device_spec_textproto";

  @VisibleForTesting
  static final String PERFORMANCE_LOCK_DECORATOR = "AndroidPerformanceLockDecorator";

  @VisibleForTesting static final String ANDROID_REAL_DEVICE = "AndroidRealDevice";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Prefix of a file in scoped spec. It should be the same as in
   * http://cs/com/google/wireless/qa/mobileharness/client/builddefs/to_json.bzl.
   */
  private static final String SCOPED_SPEC_FILE_PREFIX = "file::";

  private static final String PARAM_SESSION_ID = "session_id";

  private static final ImmutableSet<String> IOS_DRIVERS_NEED_SYSLOG =
      ImmutableSet.of("IosXcTest", "IosXcuiTest");

  private static final ImmutableSet<String> IOS_PERFORMANCE_DECORATORS =
      ImmutableSet.of(
          "IosMonsoonDecorator", "IosPowerMonitorMonsoonDecorator", "IosPerformanceDecorator");

  private static final String SYSLOG_DECORATOR_SUFFIX = "SysLogDecorator";

  private static final ImmutableSet<String> LINKABLE_FILE_SUFFIX =
      ImmutableSet.of("apk", "gz", "img", "jar", "par", "tar", "zip");

  /** Creates JobInfo from MH's JobConfig. */
  public static JobInfo createJobInfo(
      com.google.wireless.qa.mobileharness.shared.proto.JobConfig jobConfig,
      List<String> nonstandardFlags,
      @Nullable String genDirPath)
      throws MobileHarnessException, InterruptedException {
    // Generates the job id.
    String jobId = UUID.randomUUID().toString();
    return createJobInfo(
        jobId,
        jobConfig,
        nonstandardFlags,
        JobSettingsCreator.createJobSetting(jobId, jobConfig),
        JobConfigHelper.finalizeUser(jobConfig),
        null,
        genDirPath,
        true);
  }

  /** Creates JobInfo from MH's JobConfig. */
  @VisibleForTesting
  static JobInfo createJobInfo(
      String jobId,
      com.google.wireless.qa.mobileharness.shared.proto.JobConfig jobConfig,
      List<String> nonstandardFlags,
      JobSetting setting,
      JobUser jobUser,
      @Nullable String tmpRunDirPath,
      @Nullable String genDirPath,
      boolean removeGenFileDirBeforePrepare)
      throws MobileHarnessException, InterruptedException {
    // Finalizes the job name.
    String jobName = jobConfig.getName();
    if (Strings.isNullOrEmpty(jobName)) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_CONFIG_NO_JOB_NAME_ERROR,
          "Can not find the job name from job config file, BUILD target, or flag");
    }

    jobConfig = updateDeviceList(jobConfig, genDirPath);

    // Finalizes the job type.
    JobType type = finalizeJobType(jobConfig);

    // Finalizes the GEN_FILE dir.
    LocalFileUtil fileUtil = new LocalFileUtil();
    String genFileDir;
    try {
      genFileDir = setting.getGenFileDir();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_INFO_CREATE_INVALID_GEN_DIR_ERROR,
          "Failed to get GNE_FILE dir from JobSetting",
          e);
    }
    if (removeGenFileDirBeforePrepare) {
      try {
        fileUtil.removeFileOrDir(genFileDir);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_INFO_CREATE_INVALID_GEN_DIR_ERROR,
            "Failed to clean up GEN_FILE dir: " + genFileDir,
            e);
      }
    }
    fileUtil.prepareDir(genFileDir);
    logger.atInfo().log("Gen file dir: %s", genFileDir);

    // Finalize job spec.
    JobSpecHelper jobSpecHelper = JobSpecHelper.getDefaultHelper();
    List<JobSpec> jobSpecs = new ArrayList<>();
    for (String specFile : jobConfig.getSpecFiles().getContentList()) {
      String path =
          getFileOrDirPath("", specFile, genDirPath, jobConfig.getTargetLocations().getContentMap())
              .get(0);
      String fileName = Path.of(path).getFileName().toString();
      fileUtil.copyFileOrDir(path, PathUtil.join(genFileDir, "specfile_" + fileName));
      jobSpecs.add(jobSpecHelper.parseText(fileUtil.readFile(path)));
    }
    JobSpec jobSpec = jobSpecHelper.mergeSpec(jobSpecs);

    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator(jobId, jobName))
            .setJobUser(jobUser)
            .setType(type)
            .setSetting(setting)
            .build();
    jobInfo.protoSpec().setProto(jobSpec);

    // Loads the overriding info.
    Map<String, String> overridingDimensions = new HashMap<>();
    Map<String, List<String>> overridingFiles = new HashMap<>();
    Map<String, String> overridingParams = new HashMap<>();
    try {
      FlagUtil.loadOverridingInfo(
          nonstandardFlags, overridingDimensions, overridingFiles, overridingParams);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_INFO_CREATE_OVERRIDE_INFO_ERROR, "Failed to loads overridden info", e);
    }

    // Finalizes the parameters. This must be done before finalizing subDeviceSpecs or scopedSpecs.
    jobInfo.params().addAll(jobConfig.getParams().getContentMap());
    jobInfo.params().addAll(overridingParams);
    jobInfo.params().add("tags", String.join(", ", jobConfig.getTags().getContentList()));

    // Finalizes sub-devices and their dimensions.
    if (jobConfig.hasDevice()) {
      finalizeSubDeviceSpecs(
          jobConfig.getDevice().getSubDeviceSpecList(),
          jobConfig.hasSharedDimensionNames()
              ? jobConfig.getSharedDimensionNames().getContentList()
              : ImmutableList.of(),
          jobInfo);
    }

    // Sets overriding dimensions.
    jobInfo.subDeviceSpecs().getAllSubDevices().stream()
        .map(com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec::dimensions)
        .forEach(dimensions -> dimensions.addAll(overridingDimensions));

    // Finalizes the scoped specs.
    finalizeJobScopedSpecs(jobConfig, jobInfo, genDirPath);

    // Finalizes the files.
    finalizeFiles(jobConfig, jobInfo, setting, overridingFiles, tmpRunDirPath, genDirPath);

    // Finalizes the tests.
    for (String test : jobConfig.getTests().getContentList()) {
      jobInfo.tests().add(test);
    }

    // Sets session id.
    String sessionId = jobInfo.params().get(PARAM_SESSION_ID);
    if (sessionId != null) {
      jobInfo.properties().add(PropertyName.Job.SESSION_ID, sessionId);
    }
    return jobInfo;
  }

  @VisibleForTesting
  static void finalizeFiles(
      com.google.wireless.qa.mobileharness.shared.proto.JobConfig jobConfig,
      JobInfo jobInfo,
      @Nullable JobSetting jobSetting,
      Map<String, List<String>> overridingFiles,
      @Nullable String tmpRunDirPath,
      String genDirPath)
      throws MobileHarnessException, InterruptedException {
    finalizeFiles(
        jobConfig,
        jobInfo,
        jobSetting,
        overridingFiles,
        tmpRunDirPath,
        genDirPath,
        new LocalFileUtil(),
        new SystemUtil());
  }

  @VisibleForTesting
  static void finalizeFiles(
      com.google.wireless.qa.mobileharness.shared.proto.JobConfig jobConfig,
      JobInfo jobInfo,
      @Nullable JobSetting jobSetting,
      Map<String, List<String>> overridingFiles,
      @Nullable String tmpRunDirPath,
      String genDirPath,
      LocalFileUtil localFileUtil,
      SystemUtil systemUtil)
      throws MobileHarnessException, InterruptedException {
    // Implementation of the MapSplitter is backed up by a LinkedMultimap, so the order of
    // file keeps the same as defined.
    Map<String, String> targetLocations = jobConfig.getTargetLocations().getContentMap();
    boolean needToCheckBuiltFiles = jobConfig.getNeedCheckBuiltFiles();
    List<String> apksUnderTest = new ArrayList<>();
    for (FileConfig fileConfig : jobConfig.getFiles().getContentList()) {
      String tag = fileConfig.getTag();
      if (tag.equals(TAG_DEVICE_SPEC)) {
        continue;
      }
      if (!overridingFiles.containsKey(tag)) {
        List<String> files = new ArrayList<>(fileConfig.getPathList());
        if (tag.equals(TAG_BUILD_APK)) {
          apksUnderTest = putApksUnderTestInFront(files);
        }
        for (String file : files) {
          if (needToCheckBuiltFiles) {
            for (String fileOrDirPath :
                getFileOrDirPath(tag, file, genDirPath, targetLocations, localFileUtil)) {
              if (tmpRunDirPath != null
                  && jobSetting != null
                  && localFileUtil.isLocalFileOrDir(fileOrDirPath)
                  // The local file must be an absolute path.
                  && fileOrDirPath.startsWith("/")) {
                // Copies file from tmp run dir to job run dir.
                String fileValue =
                    PathUtil.join(
                        jobSetting.getRunFileDir(), fileOrDirPath.replace(tmpRunDirPath, ""));
                try {
                  localFileUtil.prepareDir(PathUtil.dirname(fileValue));
                  if (LINKABLE_FILE_SUFFIX.contains(
                      Ascii.toLowerCase(Files.getFileExtension(fileValue)))) {
                    localFileUtil.linkFileOrDir(fileOrDirPath, fileValue);
                  } else {
                    localFileUtil.copyFileOrDir(fileOrDirPath, fileValue);
                  }
                  jobInfo.files().add(tag, fileValue);
                } catch (MobileHarnessException e) {
                  // It's acceptable to ignore failed run file copy which is not necessary for
                  // session running.
                  logger.atWarning().withCause(e).log(
                      "Failed to copy file from %s to %s.", fileOrDirPath, fileValue);
                }
              } else {
                jobInfo.files().add(tag, fileOrDirPath);
              }
            }
          } else {
            jobInfo.files().add(tag, file);
          }
        }
      }
    }
    for (Entry<String, List<String>> entry : overridingFiles.entrySet()) {
      String tag = entry.getKey();
      List<String> files = new ArrayList<>(entry.getValue());
      if (TAG_BUILD_APK.equals(tag)) {
        apksUnderTest = putApksUnderTestInFront(files);
      }

      for (String fileOrDirPath : files) {
        jobInfo.files().add(tag, fileOrDirPath);
      }
    }
  }

  /**
   * Find apks under test, and puts them before extra apks.
   *
   * @return list of apks under test
   */
  static List<String> putApksUnderTestInFront(List<String> files) {
    List<String> buildApks = new ArrayList<>();
    List<String> extraApks = new ArrayList<>();
    files.forEach(
        file -> {
          if (isExtraApk(file)) {
            extraApks.add(file);
          } else {
            buildApks.add(file);
          }
        });

    // Puts build apks in front of extra apks.
    files.clear();
    files.addAll(buildApks);
    files.addAll(extraApks);

    return buildApks;
  }

  /** Checks whether a file in build_apk should be placed into extra_apk. */
  @VisibleForTesting
  static boolean isExtraApk(String file) {
    boolean isExtraApk =
        file.startsWith("//javatests/")
            || file.startsWith("//java/com/google/android/apps/common/testing/")
            || file.startsWith("//java/com/google/android/testing/");
    if (isExtraApk) {
      logger.atWarning().log(
          "File %s should be set in extra_apk. The build_apk is only for the app under test.",
          file);
    }
    return isExtraApk;
  }

  @VisibleForTesting
  static JobType finalizeJobType(
      com.google.wireless.qa.mobileharness.shared.proto.JobConfig jobConfig)
      throws MobileHarnessException {
    JobType type;
    /**
     * It is possible for jobConfig to have both a non-empty type and device fields. We should check
     * for the deprecated type field first because device may have been populated while rewriting
     * the job config json to accommodate moving the dimensions proto field into part of
     * SubDeviceSpecs.
     */
    if (!jobConfig.getType().isEmpty()) {
      try {
        type = JobTypeUtil.parseString(jobConfig.getType());
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_CONFIG_INVALID_JOB_TYPE_ERROR, "Failed to parse job type", e);
      }
      // TODO: Remove this block together with JobType.type.
      logger.atWarning().log(
          "%s",
          StrUtil.addFrame(
              String.format(
                  "mobile_test.type is deprecated. \n"
                      + "Please replace\n"
                      + "    type = \"%s\", \n"
                      + "With \n"
                      + "    device = \"%s\"\n"
                      + "    driver = \"%s\"\n"
                      + "    decorators = [\n"
                      + "        %s,\n"
                      + "    ]\n"
                      + "http://go/mh-job-config-drivers for more details.",
                  jobConfig.getType(),
                  type.getDevice(),
                  type.getDriver(),
                  Lists.reverse(type.getDecoratorList()).stream()
                      .map(decorator -> String.format("\"%s\"", decorator))
                      .collect(joining(",\n        ")))));
    } else if (jobConfig.hasDevice() || jobConfig.hasDriver()) {
      logger.atInfo().log("Job type is empty, use the first sub device to set job type");
      JobType.Builder typeBuilder = JobType.newBuilder();
      typeBuilder.setDevice(JobTypeUtil.getDeviceTypeName(jobConfig.getDevice()));
      typeBuilder.setDriver(jobConfig.getDriver().getName());
      typeBuilder.addAllDecorator(
          Lists.reverse(jobConfig.getDevice().getSubDeviceSpec(0).getDecorators().getContentList())
              .stream()
              .map(Driver::getName)
              .collect(toImmutableList()));
      type = typeBuilder.build();
    } else {
      throw new MobileHarnessException(
          BasicErrorId.JOB_CONFIG_INVALID_JOB_TYPE_ERROR,
          "Failed to load the driver from job config file, BUILD target, or flag.");
    }

    if (type.getDevice().isEmpty()) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_CONFIG_INVALID_JOB_TYPE_ERROR,
          "Can not find the device from job config file, BUILD target, or flag");
    }
    return mayAppendDecorator(type, jobConfig);
  }

  private static JobType mayAppendDecorator(
      JobType type, com.google.wireless.qa.mobileharness.shared.proto.JobConfig jobConfig) {
    JobType newType =
        SharedPoolJobUtil.isUsingSharedDefaultPerformancePool(jobConfig)
            ? mayAppendPerformanceLockDecorator(type)
            : type;
    return mayAddSysLogDecoratorForIosTest(newType);
  }

  /**
   * Adds SysLogDecorator to the first decorator in {@code type} if needed. If the test has
   * SysLogDecorator, move SysLogDecorator to the first of the decorator list {@code type}.
   */
  private static JobType mayAddSysLogDecoratorForIosTest(JobType type) {
    if (!needSysLogDecorator(type)) {
      return type;
    }

    List<String> updatedDecorators = new ArrayList<>();
    String sysLogDecoratorName = getSysLogDecoratorName(type.getDevice());
    logger.atInfo().log("Add %s to tests", sysLogDecoratorName);
    updatedDecorators.add(sysLogDecoratorName);
    type.getDecoratorList()
        .forEach(
            decorator -> {
              if (!decorator.equals(sysLogDecoratorName)) {
                updatedDecorators.add(decorator);
              }
            });
    return type.toBuilder().clearDecorator().addAllDecorator(updatedDecorators).build();
  }

  /**
   * Returns whether the test needs SysLogDecorator. By default, all iOS tests without performance
   * related decorators:{@code IosMonsoonDecorator}, {@code * IosPowerMonitorMonsoonDecorator} and
   * {@code IosPerformanceDecorator} need SysLogDecorator.
   */
  private static boolean needSysLogDecorator(JobType type) {
    if (!IOS_DRIVERS_NEED_SYSLOG.contains(type.getDriver())) {
      return false;
    }
    return Collections.disjoint(type.getDecoratorList(), IOS_PERFORMANCE_DECORATORS);
  }

  /**
   * Appends AndroidPerformanceLockDecorator to the last decorator in {@code type} if it is not
   * specified in tests against {@link #ANDROID_REAL_DEVICE}.
   *
   * <p>Note: The last decorator in {@code type} is actually the first to run.
   *
   * <p>Reference doc: http://shortn/_CdkebOGNSz
   */
  private static JobType mayAppendPerformanceLockDecorator(JobType type) {
    List<String> originalDecorators = type.getDecoratorList();
    if (!ANDROID_REAL_DEVICE.equals(type.getDevice())
        || originalDecorators.contains(PERFORMANCE_LOCK_DECORATOR)) {
      return type;
    }
    logger.atInfo().log(
        "Add AndroidPerformanceLockDecorator to lock CPUs/GPUs for tests in shared lab"
            + " performance pool");
    return type.toBuilder().addDecorator(PERFORMANCE_LOCK_DECORATOR).build();
  }

  /**
   * Updates the DeviceList according to the {@code TAG_DEVICE_SPEC} file in the files and returns
   * the updated JobConfig.
   */
  private static com.google.wireless.qa.mobileharness.shared.proto.JobConfig updateDeviceList(
      com.google.wireless.qa.mobileharness.shared.proto.JobConfig jobConfig, String genDirPath)
      throws MobileHarnessException, InterruptedException {
    for (FileConfig fileConfig : jobConfig.getFiles().getContentList()) {
      if (TAG_DEVICE_SPEC.equals(fileConfig.getTag())) {
        DeviceSpec deviceSpecFromFile =
            loadDeviceSpecFromFile(
                fileConfig.getPath(0), genDirPath, jobConfig.getTargetLocations().getContentMap());
        if (!deviceSpecFromFile.equals(DeviceSpec.getDefaultInstance())) {
          DeviceList.Builder deviceListBuilder = DeviceList.newBuilder();
          for (SubDeviceSpec subDeviceSpec : jobConfig.getDevice().getSubDeviceSpecList()) {
            deviceListBuilder.addSubDeviceSpec(
                subDeviceSpec.toBuilder()
                    .setType(deviceSpecFromFile.getType())
                    .setDimensions(
                        // subDeviceSpec.hasDimensions() only catches cases when flag
                        // --dimensions='' is passed to test. Besides this,
                        // subDeviceSpec.hasDimensions() behaves equally as
                        // subDeviceSpec.getDimensions().getContentCount() > 0
                        subDeviceSpec.hasDimensions()
                            ? subDeviceSpec.getDimensions()
                            : getDeviceDimensions(deviceSpecFromFile)));
          }
          logger.atInfo().log(
              "Device list update from target_device as: %s", deviceListBuilder.build());
          return jobConfig.toBuilder().setDevice(deviceListBuilder).build();
        }
      }
    }
    return jobConfig;
  }

  /** Finalizes {@code SubDeviceSpecs} in {@code JobInfo}. */
  @VisibleForTesting
  static void finalizeSubDeviceSpecs(
      List<SubDeviceSpec> subDeviceSpecConfigs, List<String> sharedDimensionNames, JobInfo jobInfo)
      throws MobileHarnessException {
    SubDeviceSpecs subDeviceSpecs = jobInfo.subDeviceSpecs();
    for (int i = 0; i < subDeviceSpecConfigs.size(); i++) {
      SubDeviceSpec deviceSpec = subDeviceSpecConfigs.get(i);
      /*
       * Note that JobConfig.SubDeviceSpec is not the same class as contained in
       * JobInfo.subDeviceSpecs(), so the following maps the proto's device type and dimensions to
       * the JobInfo object.
       */
      List<String> deviceDecorators = new ArrayList<>();
      Map<String, JsonObject> decoratorSpecs = new HashMap<>();
      boolean needsSysLocDecorator = needSysLogDecorator(jobInfo.type());
      for (Driver decorator : deviceSpec.getDecorators().getContentList()) {
        // Skip adding SysLogDecorator here and add it later to make it the last in the list.
        if (needsSysLocDecorator && decorator.getName().contains(SYSLOG_DECORATOR_SUFFIX)) {
          continue;
        }
        deviceDecorators.add(decorator.getName());
        if (decorator.hasParam()) {
          try {
            Map.Entry<String, JsonObject> scopedSpec =
                getNamespaceAndScopedSpecs(decorator, /* isDecorator= */ true);
            decoratorSpecs.put(scopedSpec.getKey(), scopedSpec.getValue());
          } catch (MobileHarnessException e) {
            throw new MobileHarnessException(
                BasicErrorId.JOB_SPEC_INVALID_JOB_TYPE_ERROR,
                String.format(
                    "Failed to get namespace and scopedspecs for decorator [%s] with params: %s\n"
                        + " Error: %s",
                    decorator.getName(), decorator.getParam(), e.getMessage()));
          }
        }
      }
      com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec subDeviceSpec;
      if (i == 0) {
        subDeviceSpec = subDeviceSpecs.getSubDevice(0);
        MobileHarnessExceptions.check(
            subDeviceSpec.type().equals(deviceSpec.getType()),
            BasicErrorId.JOB_SPEC_INVALID_JOB_TYPE_ERROR,
            () ->
                String.format(
                    "Device type in job type is [%s] however device type of the first"
                        + " device is [%s]",
                    subDeviceSpec.type(), deviceSpec.getType()));
        // Updates decorators. see {@link #mayAppendDecorator}.
        List<String> subDeviceSpecDecorators = subDeviceSpec.decorators().getAll();
        if (deviceDecorators.size() == subDeviceSpecDecorators.size() - 1) {
          if (subDeviceSpecDecorators.get(0).equals(PERFORMANCE_LOCK_DECORATOR)) {
            deviceDecorators.add(0, PERFORMANCE_LOCK_DECORATOR);
          } else if (Iterables.getLast(subDeviceSpecDecorators).contains(SYSLOG_DECORATOR_SUFFIX)) {
            deviceDecorators.add(Iterables.getLast(subDeviceSpecDecorators));
          }
        }
        MobileHarnessExceptions.check(
            subDeviceSpecDecorators.equals(deviceDecorators),
            BasicErrorId.JOB_SPEC_INVALID_JOB_TYPE_ERROR,
            () ->
                String.format(
                    "Device decorators in job type are %s however device decorators of the first"
                        + " device are %s",
                    subDeviceSpec.decorators().getAll(), deviceDecorators));
      } else {
        subDeviceSpec =
            subDeviceSpecs.addSubDevice(deviceSpec.getType(), ImmutableMap.of(), deviceDecorators);
      }
      subDeviceSpec.dimensions().addAll(deviceSpec.getDimensions().getContentMap());
      subDeviceSpec.scopedSpecs().addAll(decoratorSpecs);
    }
    subDeviceSpecs.addSharedDimensionNames(sharedDimensionNames);
  }

  /** Takes a {@code Driver} with non-null params, and returns the namespace and scoped specs. */
  @VisibleForTesting
  static Map.Entry<String, JsonObject> getNamespaceAndScopedSpecs(
      Driver driver, boolean isDecorator) throws MobileHarnessException {
    JsonElement jsonParams = JsonParser.parseString(driver.getParam());
    if (!jsonParams.isJsonObject()) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_INVALID_JOB_TYPE_ERROR,
          String.format(
              "Params of driver [%s] is not a valid JsonObject: %s",
              driver.getName(), driver.getParam()));
    }

    // For a {@link SpecConfigable} class, we use its spec class name as the namespace.
    String driverName = driver.getName();
    String namespace = driverName;
    Class<?> clazz;
    if (isDecorator) {
      clazz = ClassUtil.getDecoratorClass(driverName);
    } else {
      clazz = ClassUtil.getDriverClass(driverName);
    }
    if (JobSpecHelper.isSpecConfigable(clazz)) {
      namespace = JobSpecHelper.getSpecClass(clazz).getSimpleName();
    }
    return Map.entry(namespace, jsonParams.getAsJsonObject());
  }

  /** Finalizes scoped specs in {@code jobInfo}. */
  private static void finalizeJobScopedSpecs(
      com.google.wireless.qa.mobileharness.shared.proto.JobConfig jobConfig,
      JobInfo jobInfo,
      @Nullable String genDirPath)
      throws MobileHarnessException, InterruptedException {
    // TODO: Decorator ScopedSpecs should moved from JobInfo-level to individual
    // SubDeviceSpec entries of the job and only Driver scoped specs should remain at the job level
    // once the testrunner supports it. Currently decorator scoped specs are duped.

    // Adds the specs of {@code driver} into {@code jobInfo} if it is available. Exits the process
    // immediately if there is any exception.
    ScopedSpecs scopedSpecs = jobInfo.scopedSpecs();

    // Multiple device jobs should not dupe any decorator related information to the JobInfo.
    if (jobConfig.getDevice().getSubDeviceSpecCount() == 1) {
      for (Driver decorator :
          jobConfig.getDevice().getSubDeviceSpec(0).getDecorators().getContentList()) {
        addSpecsOfDriver(scopedSpecs, decorator, true);
      }
    }
    addSpecsOfDriver(scopedSpecs, jobConfig.getDriver(), false);

    // Try to resolve the files in the scoped specs of SpecConfigable driver/decorators.
    Map<String, String> targetLocations = jobConfig.getTargetLocations().getContentMap();
    scopedSpecs.addAll(
        JobSpecWalker.resolve(
            scopedSpecs.toJobSpec(JobSpecHelper.getDefaultHelper()),
            new JobSpecWalker.Visitor() {
              @Override
              public void visitPrimitiveFileField(Message.Builder builder, FieldDescriptor field)
                  throws MobileHarnessException, InterruptedException {
                if (field.isRepeated() && builder.getRepeatedFieldCount(field) <= 0) {
                  return;
                }
                List<Object> originalPaths = new ArrayList<>();
                if (field.isRepeated()) {
                  int size = builder.getRepeatedFieldCount(field);
                  for (int i = 0; i < size; i++) {
                    originalPaths.add(builder.getRepeatedField(field, i));
                  }
                } else {
                  originalPaths.add(builder.getField(field));
                }

                List<Object> resolvedPaths = new ArrayList<>();
                String fieldName = field.getName();
                for (Object rawPath : originalPaths) {
                  if (!(rawPath instanceof String)) {
                    throw new MobileHarnessException(
                        BasicErrorId.JOB_SPEC_INVALID_FILE_PATH_ERROR,
                        String.format(
                            "Failed to resolve files in JobConfigable scoped specs: Invalid "
                                + "field definition: %s is neither a String nor a list of "
                                + "String.",
                            field.getFullName()));
                  }
                  String rawPathString = (String) rawPath;
                  if (rawPathString.startsWith(SCOPED_SPEC_FILE_PREFIX)) {
                    rawPathString = rawPathString.substring(SCOPED_SPEC_FILE_PREFIX.length());
                    resolvedPaths.addAll(
                        getFileOrDirPath(fieldName, rawPathString, genDirPath, targetLocations));
                  } else {
                    resolvedPaths.add(rawPathString);
                  }
                }
                if (field.isRepeated()) {
                  builder.setField(field, resolvedPaths);
                } else {
                  builder.setField(field, resolvedPaths.get(0));
                }
              }
            }));
  }

  private static void addSpecsOfDriver(ScopedSpecs scopedSpecs, Driver driver, boolean isDecorator)
      throws MobileHarnessException {
    if (!driver.hasParam()) {
      return;
    }
    try {
      Map.Entry<String, JsonObject> scopedSpec = getNamespaceAndScopedSpecs(driver, isDecorator);
      scopedSpecs.add(scopedSpec.getKey(), scopedSpec.getValue());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_INVALID_JOB_TYPE_ERROR,
          String.format(
              "Failed to get namespace and scopedspecs for decorator [%s] with params: %s\n"
                  + " Error: %s",
              driver.getName(), driver.getParam(), e.getMessage()));
    }
  }

  /**
   * Gets the full paths of the given file target.
   *
   * @param targetLocations a map which key is a build target, value is the locations of build
   *     results (separated by #VALUE_SEPARATOR#). It will be used as additional information if
   *     failed to get file or dir path from target name (such as fields defined by filegroup,
   *     genrule, etc)
   */
  public static List<String> getFileOrDirPath(
      String fileOrDirTag,
      String fileOrDirTarget,
      @Nullable String genDirPath,
      Map<String, String> targetLocations)
      throws MobileHarnessException, InterruptedException {
    return getFileOrDirPath(
        fileOrDirTag, fileOrDirTarget, genDirPath, targetLocations, new LocalFileUtil());
  }

  /**
   * Gets the full paths of the given file target.
   *
   * @param targetLocations a map which key is a build target, value is the locations of build
   *     results (separated by #VALUE_SEPARATOR#). It will be used as additional information if
   *     failed to get file or dir path from target name (such as fields defined by filegroup,
   *     genrule, etc)
   */
  @VisibleForTesting
  static List<String> getFileOrDirPath(
      String fileOrDirTag,
      String fileOrDirTarget,
      @Nullable String genDirPath,
      Map<String, String> targetLocations,
      LocalFileUtil localFileUtil)
      throws MobileHarnessException, InterruptedException {
    return ImmutableList.of(fileOrDirTarget);
  }

  /**
   * Loads {@code DeviceSpec} from file. Returns a default instance of {@code DeviceSpec} if {@code
   * path} is empty.
   */
  private static DeviceSpec loadDeviceSpecFromFile(
      String path, String genDirPath, Map<String, String> targetLocations)
      throws InterruptedException, MobileHarnessException {
    if (!Strings.isNullOrEmpty(path)) {
      String deviceSpecFilePath = getFileOrDirPath("", path, genDirPath, targetLocations).get(0);

      try {
        String content = new LocalFileUtil().readFile(deviceSpecFilePath);
        return TextFormat.parse(content, DeviceSpec.class);
      } catch (ParseException e) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_CONFIG_DEVICE_TARGET_PARSE_ERROR,
            String.format(
                "Failed to parse DeviceSpec from target device file [%s]", deviceSpecFilePath),
            e);
      }
    }
    return DeviceSpec.getDefaultInstance();
  }

  /** Gets device dimensions from {@code DeviceSpec}. */
  static StringMap getDeviceDimensions(DeviceSpec deviceSpec) {
    StringMap.Builder dimensions =
        StringMap.newBuilder().putAllContent(deviceSpec.getDimensionsMap());

    if (!deviceSpec.getModel().isEmpty()) {
      dimensions.putContent(Ascii.toLowerCase(Name.MODEL.name()), deviceSpec.getModel());
    }
    if (!deviceSpec.getVersion().isEmpty()) {
      if (Ascii.equalsIgnoreCase(deviceSpec.getType(), "AndroidRealDevice")) {
        dimensions.putContent(Ascii.toLowerCase(Name.SDK_VERSION.name()), deviceSpec.getVersion());
      } else if (Ascii.equalsIgnoreCase(deviceSpec.getType(), "iOSRealDevice")) {
        dimensions.putContent(
            Ascii.toLowerCase(Name.SOFTWARE_VERSION.name()), deviceSpec.getVersion());
      } else {
        logger.atWarning().log(
            "Ignoring version: [%s] for incompatible device type: [%s] in device info.",
            deviceSpec.getVersion(), deviceSpec.getType());
      }
    }
    return dimensions.build();
  }

  private static String getSysLogDecoratorName(String device) {
    return device + SYSLOG_DECORATOR_SUFFIX;
  }

  private JobInfoCreator() {}
}
