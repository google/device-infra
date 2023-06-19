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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Deprecated. Please use {@link com.google.wireless.qa.mobileharness.shared.model.job.JobInfo}
 * instead.
 */
@Deprecated
public class JobInfo implements Cloneable {

  private final com.google.wireless.qa.mobileharness.shared.model.job.JobInfo newJobInfo;

  @Deprecated
  public JobInfo(com.google.wireless.qa.mobileharness.shared.model.job.JobInfo newJobInfo) {
    this.newJobInfo = newJobInfo;
    newJobInfo.timing().start();
  }

  public JobInfo(String id, String name, JobUser jobUser, JobType type, JobSetting setting) {
    this(id, name, jobUser, type, setting, JobSpec.getDefaultInstance());
  }

  /**
   * @deprecated use {@link JobInfo(String, String, JobUser, JobType, JobSetting)} instead.
   */
  @Deprecated
  public JobInfo(String id, String name, String runAs, JobType type, JobSetting setting) {
    this(id, name, JobUser.newBuilder().setRunAs(runAs).build(), type, setting);
  }

  public JobInfo(
      String id, String name, JobUser jobUser, JobType type, JobSetting setting, JobSpec spec) {
    Clock clock = Clock.systemUTC();
    Timing timing = new Timing(clock);
    newJobInfo =
        com.google.wireless.qa.mobileharness.shared.model.job.JobInfo.newBuilder()
            .setLocator(new JobLocator(id, name))
            .setJobUser(jobUser)
            .setType(type == null ? JobType.getDefaultInstance() : type)
            .setSetting(setting)
            .setTiming(timing)
            .build();
    newJobInfo.protoSpec().setProto(spec);

    // TODO: Note when migrating users to the new JobInfo, the following behavior is no
    // longer default. Need to pay attention to it when migrating.
    timing.start();
  }

  public JobInfo(String name, JobUser jobUser, JobType type, JobSetting setting) {
    this(UUID.randomUUID().toString(), name, jobUser, type, setting);
  }

  /**
   * @deprecated use {@link JobInfo(String, JobUser, JobType, JobSetting)} instead.
   */
  @Deprecated
  public JobInfo(String name, String runAs, JobType type, JobSetting setting) {
    this(name, JobUser.newBuilder().setRunAs(runAs).build(), type, setting);
  }

  /** Returns the id of the job. */
  public String getId() {
    return newJobInfo.locator().getId();
  }

  /** Returns the name of the test. */
  public String getName() {
    return newJobInfo.locator().getName();
  }

  /** Returns the user of the test. */
  public String getUser() {
    return newJobInfo.jobUser().getRunAs();
  }

  /**
   * Returns the job type, which contains the required device, driver and decorators information.
   */
  public JobType getType() {
    return newJobInfo.type();
  }

  /**
   * Creates a new {@code TestInfo} with the given name to the job.
   *
   * @throws MobileHarnessException if adds a test with exist id in this job
   */
  @CanIgnoreReturnValue
  public JobInfo addTest(String testName) throws MobileHarnessException {
    newJobInfo.tests().add(testName);
    return this;
  }

  /** Returns a list of {@link TestInfo} with the given test name. */
  public List<TestInfo> getTestByName(String testName) {
    return toOldTestInfos(newJobInfo.tests().getByName(testName));
  }

  /** Returns all the {@link TestInfo} of the current job. */
  public ListMultimap<String, TestInfo> getTests() {
    return toOldTestInfos(newJobInfo.tests().getAll());
  }

  /** Adds the given parameter to the job. */
  @CanIgnoreReturnValue
  public JobInfo addParam(String name, String value) {
    newJobInfo.params().add(name, value);
    return this;
  }

  /** Whether the job has non-empty value of the given parameter name. */
  public boolean hasParam(String name) {
    String value = newJobInfo.params().get(name);
    return value != null && !value.isEmpty();
  }

  /**
   * Returns the parameter value of the given parameter name, or null if the parameter not exists.
   */
  @Nullable
  public String getParam(String name) {
    return newJobInfo.params().get(name);
  }

  /**
   * Returns the parameter value of the given parameter name, or return the default value if the
   * parameter not exists.
   */
  public String getParam(String paramName, String defaultValue) {
    return newJobInfo.params().get(paramName, defaultValue);
  }

  /**
   * Gets a boolean from the parameter.
   *
   * @param paramName parameter name/key
   * @param defaultValue the default boolean value if the param does not exist
   */
  public boolean getBoolParam(String paramName, boolean defaultValue) {
    return newJobInfo.params().getBool(paramName, defaultValue);
  }

  /**
   * Gets an integer from the parameter.
   *
   * @param paramName parameter name/key
   * @param defaultValue the default int value if the param not exists, or the param value is not
   *     valid
   */
  public int getIntParam(String paramName, int defaultValue) {
    return newJobInfo.params().getInt(paramName, defaultValue);
  }

  /**
   * Gets a long from the parameter.
   *
   * @param paramName parameter name/key
   * @param defaultValue the default long value if the param not exists, or the param value is not
   *     valid
   */
  public long getLongParam(String paramName, long defaultValue) {
    return newJobInfo.params().getLong(paramName, defaultValue);
  }

  /**
   * Gets a double from the parameter.
   *
   * @param paramName parameter name/key
   * @param defaultValue the default int value if the param not exists, or the param value is not
   *     valid
   */
  public double getDoubleParam(String paramName, double defaultValue) {
    return newJobInfo.params().getDouble(paramName, defaultValue);
  }

  /**
   * Gets the comma separated value list from the parameter.
   *
   * @param paramName parameter name/key
   * @param defaultValue the default if the param does not exist, or the param value is not valid
   */
  public List<String> getListParam(String paramName, List<String> defaultValue) {
    return newJobInfo.params().getList(paramName, defaultValue);
  }

  /**
   * Returns the parameter {name, value} mapping. Never return null. At least an empty map is
   * returned.
   */
  public Map<String, String> getParams() {
    return newJobInfo.params().getAll();
  }

  /**
   * Returns true only if the given parameter has value and the value equals(ignore case) to "true".
   */
  public boolean isParamTrue(String name) {
    return newJobInfo.params().isTrue(name);
  }

  /**
   * Checks whether the parameters exist in the job.
   *
   * @param paramNames parameter names/keys
   * @return a list of error messages, or an <b>empty</b> list if no error found
   */
  public List<String> checkParams(String... paramNames) {
    try {
      newJobInfo.params().checkExist(paramNames);
      return ImmutableList.<String>of();
    } catch (MobileHarnessException e) {
      return ImmutableList.of(e.getMessage());
    }
  }

  /**
   * Checks whether the parameters is a valid integer.
   *
   * @param paramName parameter name/key
   * @return a list of error messages, or an <b>empty</b> list if no error found.
   */
  public List<String> checkIntParam(String paramName, int min, int max) {
    try {
      newJobInfo.params().checkInt(paramName, min, max);
      return ImmutableList.<String>of();
    } catch (MobileHarnessException e) {
      return ImmutableList.of(e.getMessage());
    }
  }

  /**
   * Checks whether the parameters is a valid boolean.
   *
   * <p>Valid boolean values are empty or "true" or "false", case-insensitive.
   *
   * @param paramName parameter name/key
   * @param optional true if parameter is optional, in which case it can also be an empty string.
   * @return error message, or <b>null</b> if no error found.
   * @see #getBoolParam(String, boolean)
   */
  @Nullable
  public String checkBoolParam(String paramName, boolean optional) {
    try {
      newJobInfo.params().checkBool(paramName, optional);
      return null;
    } catch (MobileHarnessException e) {
      return e.getMessage();
    }
  }

  /**
   * Adds a file/folder to this job, marks the file with a tag. If you add the same file with the
   * same tag for more than once, the call leaves it unchanged.
   *
   * <p>DOES NOT add new files in a lab plugin/driver/decorator. when the test is running in the
   * lab, because the {@link JobInfo} instance is shared with all the tests running in the same lab.
   *
   * @param tag tag of the file/folder
   * @param fileOrDirPath file/folder path
   */
  @CanIgnoreReturnValue
  public JobInfo addFile(String tag, String fileOrDirPath) throws MobileHarnessException {
    newJobInfo.files().add(tag, fileOrDirPath);
    return this;
  }

  /**
   * Replaces the file/dir with a new file/dir.
   *
   * <p>DOES NOT replace files in a lab plugin/driver/decorator. when the test is running in the
   * lab, because the {@link JobInfo} instance is shared with all the tests running in the same lab.
   *
   * @throws MobileHarnessException if the original file/dir doesn't exists in the job
   * @throws InterruptedException
   */
  @CanIgnoreReturnValue
  public JobInfo replaceFile(String tag, String originalFileOrDirPath, String newFileOrDirPath)
      throws MobileHarnessException, InterruptedException {
    newJobInfo.files().replace(tag, originalFileOrDirPath, ImmutableList.of(newFileOrDirPath));
    return this;
  }

  /**
   * Finds a single file/folder marked with the given tag.
   *
   * @param tag file tag
   * @return the full path with name of the file with the given tag
   * @throws MobileHarnessException when tag did not match exactly one file
   */
  public String getFile(String tag) throws MobileHarnessException {
    return newJobInfo.files().getSingle(tag);
  }

  /**
   * Finds the files/folders marked with the given tag.
   *
   * @param tag file tag
   * @return a copy {@code HashSet} of the file full path with name, or <b>an empty set</b> if there
   *     is no files with the given tag
   */
  public Set<String> getFiles(String tag) {
    return newJobInfo.files().get(tag);
  }

  /**
   * Checks whether all tags are associated with at least one file/folder in the job. Used in
   * validator only.
   *
   * @param tags file/folder tags
   * @return a empty list if no error found; or a list of error messages
   */
  public List<String> checkFiles(String... tags) {
    try {
      newJobInfo.files().checkNotEmpty(tags);
      return ImmutableList.<String>of();
    } catch (MobileHarnessException e) {
      return ImmutableList.of(e.getMessage());
    }
  }

  /**
   * Checks whether all tags are associated with a unique file/folder in the job.
   *
   * @param tags file/folder tags
   * @return a empty list if no error found; or a list of error messages
   */
  public List<String> checkUniqueFiles(String... tags) {
    try {
      newJobInfo.files().checkUnique(tags);
      return ImmutableList.<String>of();
    } catch (MobileHarnessException e) {
      return ImmutableList.of(e.getMessage());
    }
  }

  /**
   * Adds the given dimension to the job. If there is another dimension with the same name exists in
   * the job, it will be overwritten.
   */
  @CanIgnoreReturnValue
  public JobInfo addDimension(String name, String value) {
    newJobInfo.dimensions().add(name, value);
    return this;
  }

  /** Adds the given dimension to the job if it doesn't already exist. */
  @CanIgnoreReturnValue
  public JobInfo addDimensionIfAbsent(String name, String value) {
    newJobInfo.dimensions().addIfAbsent(name, value);
    return this;
  }

  /** Adds the given dimensions to the job. */
  @CanIgnoreReturnValue
  public JobInfo addDimensions(Map<String, String> dimensions) {
    newJobInfo.dimensions().addAll(dimensions);
    return this;
  }

  /** Return the {name, value} mapping of the dimensions of the job. */
  public Map<String, String> getDimensions() {
    return newJobInfo.dimensions().getAll();
  }

  /**
   * Maps the specified key to the specified value in job properties. Neither the key nor the value
   * can be null.
   *
   * @return the previous value associated with <tt>key</tt>, or <tt>null</tt> if there was no
   *     mapping for <tt>key</tt>
   * @throws NullPointerException if the specified key or value is null
   */
  @Nullable
  public String setProperty(@Nonnull String key, @Nonnull String value) {
    return newJobInfo.properties().add(key, value);
  }

  /**
   * Returns the value to which the specified key is mapped in job properties, or {@code null} if
   * the properties contains no mapping for the key.
   *
   * @throws NullPointerException if the specified key is null
   */
  @Nullable
  public String getProperty(@Nonnull String key) {
    return newJobInfo.properties().get(key);
  }

  /** Returns the job properties. */
  public ImmutableMap<String, String> getProperties() {
    return newJobInfo.properties().getAll();
  }

  /** Gets the immutable job setting. */
  public JobSetting getSetting() {
    return newJobInfo.setting();
  }

  /** Returns the current result. */
  public TestResult getResult() {
    return newJobInfo.result().get();
  }

  /**
   * This method is for temporally used during the migration period to the new {@link
   * com.google.wireless.qa.mobileharness.shared.model.job.JobInfo} data mode. Please avoid using
   * this, migrates to new JobInfo instead.
   */
  @Deprecated
  public com.google.wireless.qa.mobileharness.shared.model.job.JobInfo toNewJobInfo() {
    return newJobInfo;
  }

  @Nullable
  private TestInfo toOldTestInfo(
      @Nullable com.google.wireless.qa.mobileharness.shared.model.job.TestInfo newTestInfo) {
    if (newTestInfo == null) {
      return null;
    } else {
      return new TestInfo(newTestInfo, this);
    }
  }

  @Nullable
  private List<TestInfo> toOldTestInfos(
      @Nullable List<com.google.wireless.qa.mobileharness.shared.model.job.TestInfo> newTestInfos) {
    if (newTestInfos == null) {
      return null;
    }
    List<TestInfo> oldTestInfos = new ArrayList<>(newTestInfos.size());
    for (com.google.wireless.qa.mobileharness.shared.model.job.TestInfo newTestInfo :
        newTestInfos) {
      oldTestInfos.add(toOldTestInfo(newTestInfo));
    }
    return oldTestInfos;
  }

  @Nullable
  private ListMultimap<String, TestInfo> toOldTestInfos(
      @Nullable
          ListMultimap<String, com.google.wireless.qa.mobileharness.shared.model.job.TestInfo>
              newTestInfos) {
    if (newTestInfos == null) {
      return null;
    }
    ListMultimap<String, TestInfo> oldTestInfos = LinkedListMultimap.create();
    for (Entry<String, com.google.wireless.qa.mobileharness.shared.model.job.TestInfo> newEntry :
        newTestInfos.entries()) {
      oldTestInfos.put(newEntry.getKey(), toOldTestInfo(newEntry.getValue()));
    }
    return oldTestInfos;
  }

  @Override
  public String toString() {
    return toNewJobInfo().toString();
  }
}
