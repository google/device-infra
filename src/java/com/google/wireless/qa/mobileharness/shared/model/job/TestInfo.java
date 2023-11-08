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

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionUnit;
import com.google.devtools.mobileharness.shared.util.dir.TestDirUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Errors;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.RemoteFiles;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Status;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** A thread-safe data model containing all information of a single test. */
public class TestInfo extends TestScheduleUnit implements Cloneable {

  /** The job that this test belongs to. */
  private final JobInfo jobInfo;

  /** The parent test. Null when this is a root test. */
  @Nullable private final TestInfo parentTest;

  /** The direct sub tests. */
  private final TestInfos subTests;

  /** Test specific files. */
  private final Files files;

  /** Remote generated files for the test. */
  private final RemoteFiles remoteGenFiles;

  /** Test status. */
  private final Status status;

  /** Test result. */
  private final Result result;

  /** Test log. */
  private final Log log;

  /** Test properties. */
  private final Properties properties;

  /** Test warnings. */
  private final Errors errors;

  /** Utilities for local file operations. */
  private final LocalFileUtil fileUtil;

  private final CountDownTimer timer = new TestTimer();

  private final Supplier<TestExecutionUnit> testExecutionUnitSupplier;

  /**
   * Creates a TestInfo with all the required final fields. Note: please don't make this public at
   * any time.
   */
  TestInfo(
      TestLocator testLocator,
      Timing timing,
      JobInfo jobInfo,
      TestInfo parentTest,
      RemoteFiles remoteGenFiles,
      Status status,
      Result result,
      Log log,
      Properties properties,
      Errors errors) {
    super(testLocator, timing);
    this.jobInfo = jobInfo;
    this.parentTest = parentTest;
    this.subTests = new TestInfos(jobInfo, this);
    this.fileUtil = new LocalFileUtil();
    this.files = new Files(timing(), this.fileUtil);
    this.remoteGenFiles = remoteGenFiles;
    this.status = status;
    this.result = result;
    this.log = log;
    this.properties = properties;
    this.errors = errors;
    testExecutionUnitSupplier =
        Suppliers.memoize(
            () ->
                new TestExecutionUnit(
                    locator().toNewTestLocator(),
                    timing().toNewTiming(),
                    jobInfo().toJobExecutionUnit()));
  }

  /** Creates a TestInfo. */
  private TestInfo(Builder builder) {
    super(
        new TestLocator(
            builder.id == null ? UUID.randomUUID().toString() : builder.id,
            Preconditions.checkNotNull(builder.name, "Test name is not specified"),
            Preconditions.checkNotNull(builder.jobInfo, "JobInfo is not specified").locator()),
        builder.timing == null ? new Timing() : builder.timing);

    jobInfo = builder.jobInfo;
    parentTest = builder.parentTest;
    subTests = new TestInfos(jobInfo, this);

    fileUtil = builder.fileUtil == null ? new LocalFileUtil() : builder.fileUtil;

    log = new Log(timing());
    files = new Files(timing(), fileUtil);
    remoteGenFiles =
        new RemoteFiles(
            timing(),
            jobInfo
                .setting()
                .getRemoteFileDir()
                .map(
                    remoteFileDir ->
                        PathUtil.join(
                            remoteFileDir,
                            "j_" + jobInfo.locator().getId(),
                            "t_" + locator().getId())));
    errors = new Errors(log, timing());
    properties = new Properties(timing());
    result = new Result(timing(), jobInfo.params());
    status = new Status(timing());
    testExecutionUnitSupplier =
        Suppliers.memoize(
            () ->
                new TestExecutionUnit(
                    locator().toNewTestLocator(),
                    timing().toNewTiming(),
                    jobInfo().toJobExecutionUnit()));
  }

  /**
   * Create a builder for creating {@link TestInfo} instances.
   *
   * <p>Only visible for this package to prevent directly creating a {@link TestInfo} outside of
   * this package. Users should {@code JobInfo.tests().add(...)} to create new {@link TestInfo}
   * instances.
   */
  static Builder newBuilder() {
    return new Builder();
  }

  /** Builder for creating {@link JobInfo} instances. */
  public static class Builder {
    @Nullable private String id;
    private String name;
    private JobInfo jobInfo;
    @Nullable private TestInfo parentTest;
    @Nullable private Timing timing;
    @Nullable private LocalFileUtil fileUtil;

    private Builder() {}

    public TestInfo build() {
      return new TestInfo(this);
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setId(String id) {
      this.id = id;
      return this;
    }

    /** Required. */
    @CanIgnoreReturnValue
    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    /** Required. */
    @CanIgnoreReturnValue
    public Builder setJobInfo(JobInfo jobInfo) {
      this.jobInfo = jobInfo;
      return this;
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setParentTest(@Nullable TestInfo parentTest) {
      this.parentTest = parentTest;
      return this;
    }

    /** Optional. */
    @CanIgnoreReturnValue
    public Builder setTiming(@Nullable Timing timing) {
      this.timing = timing;
      return this;
    }

    /** Optional. Mainly for mocking out the file operation for testing. */
    @CanIgnoreReturnValue
    public Builder setFileUtil(@Nullable LocalFileUtil fileUtil) {
      this.fileUtil = fileUtil;
      return this;
    }
  }

  /** Returns the job that this test belongs to. */
  public JobInfo jobInfo() {
    return jobInfo;
  }

  /** The parent test. Null when this is a root test. */
  @Nullable
  public TestInfo parentTest() {
    return parentTest;
  }

  /** The direct sub tests. */
  public TestInfos subTests() {
    return subTests;
  }

  /** Input files. */
  public Files files() {
    return files;
  }

  /** Remote generated files. */
  public RemoteFiles remoteGenFiles() {
    return remoteGenFiles;
  }

  /** Execution status. */
  public Status status() {
    return status;
  }

  /**
   * Please use {@link #resultWithCause()} instead because it requires result cause for a
   * non-passing result, which helps debugging.
   *
   * <p>Execution result.
   */
  public Result result() {
    return result;
  }

  /** Execution result with cause. */
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
   * Please use {@link #warnings()} instead, which is the new name and API of the legacy errors.
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

  /**
   * Timer of the test which starts when the test starts and expires when the test expires. If this
   * is a sub-test, it is decided by its root test.
   */
  public CountDownTimer timer() {
    return timer;
  }

  /** Returns true if the generated files' directory of the test has been created. */
  public boolean hasGenFileDir() {
    JobSetting jobSetting = jobInfo.setting();
    if (!jobSetting.hasGenFileDir()) {
      return false;
    }
    try {
      String jobDir = jobSetting.getGenFileDir();
      fileUtil.checkDir(jobSetting.hasTestSubdirs() ? genTestSubdirPath(jobDir) : jobDir);
      return true;
    } catch (MobileHarnessException e) {
      return false;
    }
  }

  /**
   * Gets the path of the directory that contains all and only the generated files of this test,
   * which are expected to be uploaded to Sponge. Will create this directory if it doesn't exist.
   *
   * @throws MobileHarnessException fails to create the directory
   */
  public String getGenFileDir() throws MobileHarnessException {
    JobSetting jobSetting = jobInfo.setting();
    String jobDir = jobSetting.getGenFileDir();
    if (jobSetting.hasTestSubdirs()) {
      String testDir = genTestSubdirPath(jobDir);
      fileUtil.prepareDir(testDir);
      fileUtil.grantFileOrDirFullAccess(jobDir);
      fileUtil.grantFileOrDirFullAccess(testDir);
      return testDir;
    } else {
      fileUtil.prepareDir(jobDir);
      fileUtil.grantFileOrDirFullAccess(jobDir);
      return jobDir;
    }
  }

  /**
   * Gets the path of the directory that contains all and only the tmp files of this test, which are
   * expected to be discarded after the test is done. Will create this directory if it doesn't
   * exist.
   *
   * @throws MobileHarnessException fails to create the directory
   */
  public String getTmpFileDir() throws MobileHarnessException {
    JobSetting jobSetting = jobInfo.setting();
    String jobDir = jobSetting.getTmpFileDir();
    if (jobSetting.hasTestSubdirs()) {
      String testDir = genTestSubdirPath(jobDir);
      fileUtil.prepareDir(testDir);
      fileUtil.grantFileOrDirFullAccess(jobDir);
      fileUtil.grantFileOrDirFullAccess(testDir);
      return testDir;
    } else {
      fileUtil.prepareDir(jobDir);
      fileUtil.grantFileOrDirFullAccess(jobDir);
      return jobDir;
    }
  }

  /**
   * <b>NOTE</b>: For internal use only.
   *
   * @return the execution unit of the test
   */
  @Beta
  public TestExecutionUnit toTestExecutionUnit() {
    return testExecutionUnitSupplier.get();
  }

  /**
   * @return the root test of the test.
   */
  public TestInfo getRootTest() {
    TestInfo iter = this;
    while (iter.parentTest != null) {
      iter = iter.parentTest;
    }
    return iter;
  }

  /**
   * @return whether the test is the root test (<tt>parentTest() == null</tt>)
   */
  public boolean isRootTest() {
    return parentTest() == null;
  }

  /** Gets the path(to job GEN/TMP file dir) of the gen/tmp file dir of the test. */
  private String genTestSubdirPath(String jobDirPath) {
    List<String> testIds = new ArrayList<>();
    TestInfo test = this;
    while (true) {
      testIds.add(test.locator().getId());
      TestInfo parent = test.parentTest();
      if (parent == null) {
        break;
      }
      test = parent;
    }
    testIds = Lists.reverse(testIds);
    return TestDirUtil.getTestDirPath(
        jobDirPath, testIds.get(0), testIds.stream().skip(1L).toArray(String[]::new));
  }

  private class TestTimer implements CountDownTimer {

    @Override
    public Instant expireTime() throws MobileHarnessException {
      TestInfo rootTest = getRootTest();
      Instant startTime = rootTest.timing().getStartTime();
      if (startTime == null) {
        throw new MobileHarnessException(
            BasicErrorId.TEST_GET_EXPIRE_TIME_ERROR_BEFORE_START,
            "Failed to calculate the test expire time because the "
                + (parentTest == null ? "current test" : "root test " + rootTest.locator())
                + " is not started. Please set its status from NEW to any other status.");
      }
      Instant jobExpireTime = jobInfo.timer().expireTime();
      Instant testExpireTime =
          startTime.plus(Duration.ofMillis(jobInfo.setting().getTimeout().getTestTimeoutMs()));
      if (jobExpireTime.isBefore(testExpireTime)) {
        return jobExpireTime;
      } else {
        return testExpireTime;
      }
    }

    @Override
    public boolean isExpired() {
      try {
        return expireTime().isBefore(timing().getClock().instant());
      } catch (MobileHarnessException e) {
        // Job/Test not started. Considered as not expired.
        return false;
      }
    }

    @Override
    public Duration remainingTimeJava() throws MobileHarnessException {
      Instant jobExpireTime = jobInfo.timer().expireTime();
      TestInfo rootTest = getRootTest();
      Instant now = rootTest.timing().getClock().instant();
      if (jobExpireTime.isBefore(now)) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_TIMEOUT, "Job expired. No time to run remaining test steps.");
      }
      Instant testExpireTime = expireTime();
      if (testExpireTime.isBefore(now)) {
        throw new MobileHarnessException(
            BasicErrorId.TEST_TIMEOUT,
            (parentTest == null ? "Test" : "Top level test " + rootTest.locator())
                + " expired. No time to run remaining test steps.");
      }
      if (jobExpireTime.isBefore(testExpireTime)) {
        return Duration.between(now, jobExpireTime);
      } else {
        return Duration.between(now, testExpireTime);
      }
    }
  }
}
