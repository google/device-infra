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

package com.google.wireless.qa.mobileharness.shared.api.job;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.TestBuilderAdapter;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Deprecated. Please use {@link com.google.wireless.qa.mobileharness.shared.model.job.TestInfo}
 * instead.
 */
@Deprecated
public class TestInfo implements Cloneable {

  private final com.google.wireless.qa.mobileharness.shared.model.job.TestInfo newTestInfo;
  private final JobInfo oldJobInfo;

  @Deprecated
  public TestInfo(
      com.google.wireless.qa.mobileharness.shared.model.job.TestInfo newTestInfo,
      JobInfo oldJobInfo) {
    this.newTestInfo = newTestInfo;
    this.oldJobInfo = oldJobInfo;
  }

  @Deprecated
  public TestInfo(com.google.wireless.qa.mobileharness.shared.model.job.TestInfo newTestInfo) {
    this.newTestInfo = newTestInfo;
    this.oldJobInfo = new JobInfo(newTestInfo.jobInfo());
  }

  /**
   * Creates a test when test ID is provided.
   *
   * @param testId Id of the test
   * @param testName name of the test
   * @param jobInfo the job that this test belongs to
   */
  public TestInfo(String testId, String testName, JobInfo jobInfo) {
    this(
        TestBuilderAdapter.newTestInfoBuilder()
            .setId(testId)
            .setName(testName)
            .setJobInfo(jobInfo.toNewJobInfo())
            .build(),
        jobInfo);
  }

  public com.google.wireless.qa.mobileharness.shared.model.job.TestInfo toNewTestInfo() {
    return newTestInfo;
  }

  /** Gets the locator of this test. */
  public TestLocator getLocator() {
    return newTestInfo.locator();
  }

  /** Returns the id of this test. */
  public String getId() {
    return getLocator().getId();
  }

  /** Returns the name of this test. */
  public String getName() {
    return getLocator().getName();
  }

  /** Returns the job that this test belongs to. */
  public JobInfo getJobInfo() {
    return oldJobInfo;
  }

  /** Updates the status of this test using the given value. */
  public void setStatus(TestStatus status) {
    newTestInfo.status().set(status);
  }

  /** Returns the current status of the test. */
  public TestStatus getStatus() {
    return newTestInfo.status().get();
  }

  /** Updates the result of this test using the given value. */
  public void setResult(TestResult result) {
    newTestInfo.result().set(result);
  }

  /** Returns the current result of the test. */
  public TestResult getResult() {
    return newTestInfo.result().get();
  }

  /** Appends run-time logs to the current test. Will append '\n' at the end of the message. */
  public void logLn(@CompileTimeConstant String message) {
    newTestInfo.log().ln(message + "\n");
  }

  /**
   * Appends run-time logs to the current test. Will append '\n' at the end of the message. Also log
   * with the given logger.
   */
  public void logLn(@CompileTimeConstant String message, Logger logger) {
    newTestInfo.log().atInfo().log(message);
  }

  /**
   * Appends run-time logs to the current test. Will append '\n' at the end of the message. Also log
   * with the given logger.
   */
  public void logLn(@CompileTimeConstant String message, FluentLogger logger) {
    newTestInfo.log().ln(message, logger);
  }

  /** Appends run-time logs to the current test. Also log with the given logger. */
  public void log(@CompileTimeConstant String message, FluentLogger logger) {
    newTestInfo.log().append(message, logger);
  }

  /**
   * Maps the specified key to the specified value in test properties. Neither the key nor the value
   * can be null.
   *
   * @return the previous value associated with <tt>key</tt>, or <tt>null</tt> if there was no
   *     mapping for <tt>key</tt>
   * @throws NullPointerException if the specified key or value is null
   */
  @Nullable
  public String setProperty(@Nonnull String key, @Nonnull String value) {
    return newTestInfo.properties().add(key, value);
  }

  /**
   * Returns the value to which the specified key is mapped in test properties, or {@code null} if
   * the properties contains no mapping for the key.
   *
   * @throws NullPointerException if the specified key is null
   */
  @Nullable
  public String getProperty(@Nonnull String key) {
    return newTestInfo.properties().get(key);
  }

  /** Returns the test properties. */
  public ImmutableMap<String, String> getProperties() {
    return newTestInfo.properties().getAll();
  }

  /** Returns the time in milliseconds when this test is created in lab server. */
  public long getCreateTimeMs() {
    return newTestInfo.timing().getCreateTime().toEpochMilli();
  }

  /**
   * Returns the remaining time in milliseconds before the test is timeout. The return value is
   * positive.
   *
   * @throws MobileHarnessException if the remaining time <= 0
   */
  public long getRemainingTimeMs() throws MobileHarnessException {
    if (newTestInfo.timing().getStartTime() == null) {
      return oldJobInfo.getSetting().getTimeout().getTestTimeoutMs();
    }
    return newTestInfo.timer().remainingTimeJava().toMillis();
  }

  /**
   * Gets the path of the directory that contains all and only the generated files of this test,
   * which are expected to be uploaded to Sponge. Will create this directory if it doesn't exist.
   *
   * @throws MobileHarnessException fails to create the directory
   */
  public String getGenFileDir() throws MobileHarnessException {
    return newTestInfo.getGenFileDir();
  }

  /**
   * Gets the path of the directory that contains all and only the tmp files of this test, which are
   * expected to be discarded after the test is done. Will create this directory if it doesn't
   * exist.
   *
   * @throws MobileHarnessException fails to create the directory
   */
  public String getTmpFileDir() throws MobileHarnessException {
    return newTestInfo.getTmpFileDir();
  }

  /** Saves the exception of this job. */
  @CanIgnoreReturnValue
  public TestInfo addError(MobileHarnessException e) {
    newTestInfo.errors().add(e);
    return this;
  }

  /** Saves the error of this test. Also logs the error to test log. */
  @CanIgnoreReturnValue
  public TestInfo addAndLogError(MobileHarnessException e, @Nullable FluentLogger logger) {
    newTestInfo.errors().addAndLog(e, logger);
    return this;
  }
}
