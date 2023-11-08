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

package com.google.wireless.qa.mobileharness.shared.model.job;

import com.google.common.base.Joiner;
import com.google.common.base.Joiner.MapJoiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceRequirement;
import com.google.devtools.mobileharness.api.model.proto.Job.DeviceRequirements;
import com.google.devtools.mobileharness.api.model.proto.Job.JobFeature;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.infra.controller.test.model.JobExecutionUnit;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.job.JobTypeUtil;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Job;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.FilesJobSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ParamsJobSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ScopedSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.ProtoJobSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.UnionJobSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Errors;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.RemoteFiles;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Status;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * A thread-safe data model of a Mobile Harness job, including the information of the tests belong
 * to it.
 */
public class JobInfo extends JobScheduleUnit {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * File tag name of the plugin jar with the handler for job/test events. The handler is executed
   * on client side.
   */
  public static final String TAG_CLIENT_PLUGIN = "client_plugin_jar";

  /**
   * File tag name of Plugin jar with the handler for test events. The handler is executed on lab
   * server side.
   */
  public static final String TAG_LAB_PLUGIN = "lab_plugin_jar";

  /**
   * Param name of the canonical class names (split by ",") of the plugin handlers for job/test
   * start/end events. The handlers are executed on client side.
   */
  public static final String PARAM_CLIENT_PLUGIN = "client_plugin_class";

  /** Param name of the canonical class names of the client plugin module. */
  public static final String PARAM_CLIENT_PLUGIN_MODULES = "client_plugin_module_classes";

  /**
   * Param name of the regex of class names that we force to be loaded from the client plugin's
   * classloader.
   *
   * <p>If specified, this can be used to resolve problems arising from a plugin's unintended use of
   * classes from Mobile Harness, rather than its own classes (since parent classes are always used,
   * if they are present).
   */
  public static final String PARAM_CLIENT_PLUGIN_FORCE_LOAD_FROM_JAR_CLASS_REGEX =
      "client_plugin_force_load_from_jar_class_regex";

  /**
   * Param name of the canonical class names (split by ",") of the plugin handlers for test
   * start/end events. The handlers are executed on lab server side.
   */
  public static final String PARAM_LAB_PLUGIN = "lab_plugin_class";

  /** Param name of the canonical class names of the client plugin module. */
  public static final String PARAM_LAB_PLUGIN_MODULES = "lab_plugin_module_classes";

  /**
   * Param name of the regex of class names that we force to be loaded from the lab plugin's
   * classloader.
   *
   * <p>If specified, this can be used to resolve problems arising from a plugin's unintended use of
   * classes from Mobile Harness, rather than its own classes (since parent classes are always used,
   * if they are present).
   */
  public static final String PARAM_LAB_PLUGIN_FORCE_LOAD_FROM_JAR_CLASS_REGEX =
      "lab_plugin_force_load_from_jar_class_regex";

  @ParamAnnotation(
      required = false,
      help =
          "Container mode preference for testing/debugging/backward-compatibility."
              + " See the enum ContainerModePreference. Case is ignored. By default, it is"
              + "ContainerModePreference.NON_CONTAINER.\n")
  public static final String PARAM_CONTAINER_MODE_PREFERENCE = "container_mode_preference";

  @ParamAnnotation(
      required = false,
      help =
          "Sandbox mode preference for testing/debugging/backward-compatibility."
              + " See the enum SandboxModePreference. Case is ignored. By default, it is"
              + "SandboxModePreference.NON_SANDBOX.\n")
  public static final String PARAM_SANDBOX_MODE_PREFERENCE = "sandbox_mode_preference";

  @ParamAnnotation(
      required = false,
      help =
          "If this flag is true, the not assigned tests will still show in sponge \"Test Method\" "
              + "but won't effect job result (target result).")
  public static final String PARAM_IGNORE_NOT_ASSIGNED_TESTS = "ignore_not_assigned_tests";

  @ParamAnnotation(
      required = false,
      help =
          "Sandbox memory (MB). If it is 0 or not specified, default value from lab server will be"
              + " used.\n")
  public static final String PARAM_SANDBOX_MEMORY_MB = "sandbox_memory_mb";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to report test error as TOOL_FAILED. Default value is not set. Currently it only"
              + " supports tests triggered by gateway.")
  public static final String PARAM_REPORT_ERROR_AS_TOOL_FAILED = "report_error_as_tool_failed";

  /** Input files of the job. */
  private final Files files;

  /** Remote generated files of the job. */
  private final RemoteFiles remoteGenFiles;

  private final RemoteFiles remoteRunFiles;

  /** Job status. */
  private final Status status;

  /** Job result. */
  private final Result result;

  /** Job log. */
  private final Log log;

  /** Job properties. */
  private final Properties properties;

  /** Job warnings. */
  private final Errors errors;

  /** Job spec which contains the structured parameters and files. */
  private final ProtoJobSpec spec;

  /** All the top-level tests in this job. */
  private final TestInfos tests;

  private final Supplier<JobExecutionUnit> jobExecutionUnitSupplier;

  private final CountDownTimer timer = new JobTimer();

  /** Whether the failure in {@link #toString()} is logged for this instance. */
  private boolean loggedToStringFailure = false;

  /**
   * Creates a Mobile Harness job by the given required final fields. Note: please don't make this
   * public at any time.
   */
  JobInfo(
      JobLocator locator,
      JobUser jobUser,
      JobType type,
      JobSetting setting,
      Timing timing,
      Params params,
      ScopedSpecs scopedSpecs,
      SubDeviceSpecs subDeviceSpecs,
      LocalFileUtil localFileUtil,
      RemoteFiles remoteGenFiles,
      RemoteFiles remoteRunFiles,
      Status status,
      Result result,
      Log log,
      Properties properties,
      Errors errors,
      JobSpec jobSpec) {
    super(locator, jobUser, type, setting, timing, params, scopedSpecs, subDeviceSpecs);
    this.files = new Files(timing(), localFileUtil == null ? new LocalFileUtil() : localFileUtil);
    this.remoteGenFiles = remoteGenFiles;
    this.remoteRunFiles = remoteRunFiles;
    this.status = status;
    this.result = result;
    this.log = log;
    this.properties = properties;
    this.errors = errors;
    this.spec = new ProtoJobSpec(jobSpec);
    this.tests = new TestInfos(this);
    this.jobExecutionUnitSupplier =
        Suppliers.memoize(
            () ->
                JobExecutionUnit.create(
                    locator().toNewJobLocator(),
                    type().getDriver(),
                    setting().getNewTimeout(),
                    timing().toNewTiming(),
                    setting().dirs()));
  }

  /** Creates a Mobile Harness job, which contains a set of tests. */
  private JobInfo(Builder builder) {
    super(
        Preconditions.checkNotNull(builder.locator, "Job locator is not specified"),
        builder.jobUser == null
            ? JobUser.newBuilder()
                .setActualUser(System.getenv("USER"))
                .setRunAs(System.getenv("USER"))
                .build()
            : builder.jobUser,
        Preconditions.checkNotNull(builder.type, "Job type is not specified"),
        builder.setting == null
            ? JobSetting.newBuilder().setLocalFileUtil(builder.fileUtil).build()
            : builder.setting,
        builder.timing == null ? new Timing() : builder.timing);
    files = new Files(timing(), builder.fileUtil == null ? new LocalFileUtil() : builder.fileUtil);
    remoteGenFiles =
        new RemoteFiles(
            timing(),
            setting()
                .getRemoteFileDir()
                .map(
                    remoteFileDir ->
                        PathUtil.join(remoteFileDir, "j_" + locator().getId(), "genfiles")));
    remoteRunFiles =
        new RemoteFiles(
            timing(),
            setting()
                .getRemoteFileDir()
                .map(
                    remoteFileDir ->
                        PathUtil.join(remoteFileDir, "j_" + locator().getId(), "runfiles")));
    status = new Status(timing());
    result = new Result(timing(), params());
    log = new Log(timing());
    properties = new Properties(timing());
    errors = new Errors(log, timing());
    spec = new ProtoJobSpec();
    tests = new TestInfos(this);

    this.jobExecutionUnitSupplier =
        Suppliers.memoize(
            () ->
                JobExecutionUnit.create(
                    locator().toNewJobLocator(),
                    type().getDriver(),
                    setting().getNewTimeout(),
                    timing().toNewTiming(),
                    setting().dirs()));
  }

  /** Create a builder for creating {@link JobInfo} instances. */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** Builder for creating {@link JobInfo} instances. */
  public static class Builder {
    private JobLocator locator;
    private JobType type;
    @Nullable private JobUser jobUser;
    @Nullable private JobSetting setting;
    @Nullable private Timing timing;
    @Nullable private LocalFileUtil fileUtil;

    private Builder() {}

    public JobInfo build() {
      return new JobInfo(this);
    }

    /** Required. */
    @CanIgnoreReturnValue
    public Builder setLocator(JobLocator locator) {
      this.locator = locator;
      return this;
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setJobUser(JobUser jobUser) {
      this.jobUser = jobUser;
      return this;
    }

    /** Required. */
    @CanIgnoreReturnValue
    public Builder setType(JobType type) {
      this.type = type;
      return this;
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setSetting(JobSetting setting) {
      this.setting = setting;
      return this;
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setTiming(Timing timing) {
      this.timing = timing;
      return this;
    }

    /** Optional. Mainly for mocking out the file operation for testing. */
    @CanIgnoreReturnValue
    public Builder setFileUtil(LocalFileUtil fileUtil) {
      this.fileUtil = fileUtil;
      return this;
    }
  }

  /** Input files. */
  public Files files() {
    return files;
  }

  /** Get the path of gen files stored in the remote file system like cns. */
  public RemoteFiles remoteGenFiles() {
    return remoteGenFiles;
  }

  /** Get the path of run files stored in the remote file system like cns. */
  public RemoteFiles remoteRunFiles() {
    return remoteRunFiles;
  }

  /** Execution status. */
  public Status status() {
    return status;
  }

  /**
   * Please use {@link #resultWithCause()} instead.
   *
   * <p>Execution result.
   */
  public Result result() {
    return result;
  }

  /** Execution result. */
  public com.google.devtools.mobileharness.api.model.job.out.Result resultWithCause() {
    return result.toNewResult();
  }

  /** Log generated during execution. */
  public Log log() {
    return log;
  }

  /** Output properties generated during execution. */
  public Properties properties() {
    return properties;
  }

  /**
   * Please use {@link #warnings()} instead.
   *
   * <p>Warnings that occur during execution.
   */
  public Errors errors() {
    return errors;
  }

  /** Warnings that occur during execution. */
  public Warnings warnings() {
    return errors.toWarnings();
  }

  /** Timer of the job which starts when the job starts and expires when the job expires. */
  public CountDownTimer timer() {
    return timer;
  }

  /**
   * Gets spec of {@link SpecConfigable} for the class of object {@code configable}.
   *
   * @return a spec that combines all relevant values in {@link #params()}, {@link #files} and
   *     {@link #protoSpec}
   */
  public <T extends Message> T combinedSpec(SpecConfigable<T> configable)
      throws InterruptedException, MobileHarnessException {
    @SuppressWarnings("unchecked")
    Class<T> specClass = (Class<T>) JobSpecHelper.getSpecClass(configable.getClass());
    return combinedSpecOfClass(specClass, null);
  }

  /**
   * Same as {@link #combinedSpec(SpecConfigable)} but overrides decorators settings with device
   * specific configurations.
   */
  public <T extends Message> T combinedSpec(SpecConfigable<T> configable, @Nullable String deviceId)
      throws InterruptedException,
          com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    @SuppressWarnings("unchecked")
    Class<T> specClass = (Class<T>) JobSpecHelper.getSpecClass(configable.getClass());
    return combinedSpec(specClass, deviceId);
  }

  /** Same as {@link #combinedSpec(SpecConfigable, String)} but takes in a class instead. */
  public <T extends Message> T combinedSpec(Class<T> specClass, @Nullable String deviceId)
      throws InterruptedException,
          com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    return combinedSpecOfClass(specClass, subDeviceSpecs().getSubDeviceById(deviceId).orElse(null));
  }

  /**
   * Retrieves each spec for the configable for each subdevice in the job that satisfies the given
   * predicate. This is meant for use in any validation that only cares about the specs associated
   * with devices of a particular type, dimension, etc.
   */
  public <T extends Message> List<T> combinedSpecForDevices(
      SpecConfigable<T> configable, Predicate<SubDeviceSpec> deviceFilter)
      throws InterruptedException,
          com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    @SuppressWarnings("unchecked")
    Class<T> specClass = (Class<T>) JobSpecHelper.getSpecClass(configable.getClass());
    List<T> specs = new ArrayList<>();
    for (SubDeviceSpec subDeviceSpec : subDeviceSpecs().getAllSubDevices()) {
      if (deviceFilter.test(subDeviceSpec)) {
        specs.add(combinedSpecOfClass(specClass, subDeviceSpec));
      }
    }
    return specs;
  }

  /** Same as {@link #combinedSpecForDevices} but returns the spec for all devices. */
  public <T extends Message> List<T> combinedSpecForAllDevices(SpecConfigable<T> configable)
      throws InterruptedException,
          com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    return combinedSpecForDevices(configable, subDeviceSpec -> true);
  }

  /**
   * Gets spec with class {@code specClass}.
   *
   * @return a spec that combines all relevant values in {@link #params()}, {@link #files} and
   *     {@link #protoSpec}
   */
  public <T extends Message> T combinedSpecOfClass(Class<T> specClass)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    return combinedSpecOfClass(specClass, null);
  }

  private <T extends Message> T combinedSpecOfClass(
      Class<T> specClass, @Nullable SubDeviceSpec subDeviceSpec)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    return getUnionJobSpec(subDeviceSpec).getSpec(specClass);
  }

  /**
   * Gets a union of all specs of the job.
   *
   * @return a spec union that combines all relevant values in {@link #params()}, {@link #files} and
   *     {@link #protoSpec}
   */
  public UnionJobSpec getUnionJobSpec(@Nullable SubDeviceSpec subDeviceSpec) {
    UnionJobSpec unionJobSpec = new UnionJobSpec();
    if (subDeviceSpec != null) {
      unionJobSpec = unionJobSpec.addWrapper(subDeviceSpec.scopedSpecs());
    } else {
      logger.atWarning().log(
          "The sub device spec should exist and the global scoped spec should not be used.");
      unionJobSpec = unionJobSpec.addWrapper(scopedSpecs());
    }
    return unionJobSpec
        .addWrapper(new ParamsJobSpec(params()))
        .addWrapper(new FilesJobSpec(files()))
        .addWrapper(protoSpec());
  }

  /** Returns job spec, which contains the structured parameters and files. */
  public ProtoJobSpec protoSpec() {
    return spec;
  }

  /** All tests of this job. */
  public TestInfos tests() {
    return tests;
  }

  /**
   * <b>NOTE</b>: For internal use only.
   *
   * @return the execution unit of the job
   */
  public JobExecutionUnit toJobExecutionUnit() {
    return jobExecutionUnitSupplier.get();
  }

  /** <b>NOTE</b>: For internal use only. */
  public JobFeature toFeature() {
    JobFeature.Builder jobFeatureBuilder =
        JobFeature.newBuilder().setUser(jobUser()).setDriver(type().getDriver());
    DeviceRequirements.Builder deviceRequirements =
        DeviceRequirements.newBuilder()
            .addAllSharedDimension(subDeviceSpecs().getSharedDimensionNames());
    for (SubDeviceSpec deviceSpec : subDeviceSpecs().getAllSubDevices()) {
      DeviceRequirement deviceRequirement = deviceSpec.deviceRequirement().toProto();
      deviceRequirements.addDeviceRequirement(deviceRequirement);
    }
    jobFeatureBuilder.setDeviceRequirements(deviceRequirements);
    return jobFeatureBuilder.build();
  }

  /** Uses {@link TestInfos#add(TestInfo)} instead. */
  @CanIgnoreReturnValue
  @Deprecated
  public JobInfo addTest(TestInfo testInfo) throws MobileHarnessException {
    tests.add(testInfo);
    return this;
  }

  /** Uses {@link TestInfos#getAll()} instead. */
  @Deprecated
  public ListMultimap<String, TestInfo> getAllTests() {
    return tests.getAll();
  }

  @Override
  public String toString() {
    // Logs the job info.
    StringBuilder buf = new StringBuilder("Job basic information: \n");

    String execModeName = properties().get(Job.EXEC_MODE);
    buf.append(String.format("EXEC MODE:\t%s\n", execModeName != null ? execModeName : "Unset"));

    // Basic info.
    buf.append(String.format("ID:\t%s\n", locator().getId()));
    buf.append(String.format("NAME:\t%s\n", locator().getName()));
    buf.append(String.format("USER&RUN_AS:\t%s\n", jobUser().getRunAs()));
    buf.append(String.format("ACTUAL_USER:\t%s\n", jobUser().getActualUser()));
    buf.append(String.format("TYPE:\t%s\n", JobTypeUtil.toString(type())));
    if (!dimensions().isEmpty()) {
      MapJoiner mapJoiner = Joiner.on("\n- ").withKeyValueSeparator("=");
      buf.append(String.format("\nDIMENSIONS:\n- %s\n", mapJoiner.join(dimensions().getAll())));
    }

    JobSetting jobSetting = setting();
    buf.append(
        String.format(
            "JOB TIMEOUT:\t%s\n",
            (int) Duration.ofMillis(jobSetting.getTimeout().getJobTimeoutMs()).getSeconds()
                + " sec"));
    buf.append(
        String.format(
            "TEST_TIMEOUT:\t%s\n",
            (int) Duration.ofMillis(jobSetting.getTimeout().getTestTimeoutMs()).getSeconds()
                + " sec"));
    buf.append(
        String.format(
            "START_TIMEOUT:\t%s\n",
            (int) Duration.ofMillis(jobSetting.getTimeout().getStartTimeoutMs()).getSeconds()
                + " sec"));
    buf.append(String.format("TEST_ATTEMPTS:\t%d\n", jobSetting.getRetry().getTestAttempts()));
    buf.append(String.format("RETRY_LEVEL:\t%s\n", jobSetting.getRetry().getRetryLevel()));
    buf.append(String.format("PRIORITY:\t%s\n", jobSetting.getPriority().name()));

    String genFileDir = "Unknown";
    try {
      genFileDir = setting().getGenFileDir();
    } catch (MobileHarnessException e) {
      if (!loggedToStringFailure) {
        log().atWarning().withCause(e).log("Failed to get gen file dir when printing the job.");
        loggedToStringFailure = true;
      }
    }
    buf.append(String.format("\nGEN_FILE DIR:\n- %s\n", genFileDir));
    buf.append(
        String.format(
            "JOB_LINK_IN_MHFE:\n- %s\n",
            String.format("http://mhfe/jobdetailview/%s", locator().getId())));
    buf.append(params());
    buf.append(files());
    return buf.toString();
  }

  private class JobTimer implements CountDownTimer {

    @Override
    public Instant expireTime()
        throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
      Instant startTime = timing().getStartTime();
      if (startTime == null) {
        throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
            BasicErrorId.JOB_GET_EXPIRE_TIME_ERROR_BEFORE_START,
            "Failed to calculate the job expire time because job is not started. "
                + "Please set the job status from NEW to any other status.");
      } else {
        return startTime.plus(Duration.ofMillis(setting().getTimeout().getJobTimeoutMs()));
      }
    }

    @Override
    public boolean isExpired() {
      try {
        return timer().expireTime().isBefore(timing().getClock().instant());
      } catch (MobileHarnessException e) {
        // Job not started. Considered as not expired.
        return false;
      }
    }

    @Override
    public Duration remainingTimeJava()
        throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
      Instant expireTime = timer().expireTime();
      Instant now = timing().getClock().instant();
      if (expireTime.isBefore(now)) {
        throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
            BasicErrorId.JOB_TIMEOUT, "Job expired");
      }
      return Duration.between(now, expireTime);
    }
  }
}
