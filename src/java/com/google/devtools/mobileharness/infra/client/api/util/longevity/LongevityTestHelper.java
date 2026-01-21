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

package com.google.devtools.mobileharness.infra.client.api.util.longevity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Striped;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator;
import com.google.devtools.mobileharness.service.moss.proto.Slg.JobInfoProto;
import com.google.devtools.mobileharness.service.moss.util.slg.JobInfoConverter;
import com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Job;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecHelper;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecWalker;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import javax.annotation.Nullable;

/**
 * A helper class for longevity test.
 *
 * <p>
 */
public class LongevityTestHelper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The remote file path to stored the job info. */
  public static final String PARAM_JOB_PERSISTENT_PATH = "job_persistent_path";

  private final StorageBackend storageBackend;

  /** The object is static, so it can synchronize concurrent operations from different callers. */
  private static final Striped<Lock> PERSIST_JOB_INFO_STRIPED_LOCKS = Striped.lock(128);

  public LongevityTestHelper() {
    this(new CompositeStorageBackend());
  }

  @VisibleForTesting
  LongevityTestHelper(StorageBackend storageBackend) {
    this.storageBackend = storageBackend;
  }

  /** Resumes the TestEngineLocator from tesInfo. */
  public static Optional<TestEngineLocator> resumeTestEngineLocator(TestInfo testInfo)
      throws MobileHarnessException {
    if (!testInfo.properties().has(PropertyName.Test._TEST_ENGINE_LOCATOR)) {
      return Optional.empty();
    }

    String textProto = testInfo.properties().get(PropertyName.Test._TEST_ENGINE_LOCATOR);
    try {
      return Optional.of(TextFormat.parse(textProto, TestEngineLocator.class));
    } catch (ParseException e) {
      throw new MobileHarnessException(
          InfraErrorId.CLIENT_LONGEVITY_TEST_ENGINE_LOCATOR_RECOVER_ERROR,
          String.format(
              "Failed to parse TestEngineLocator from text %s: %s", textProto, e.getMessage()));
    }
  }

  public Optional<String> resumeJobId(String jobPersistentPath) throws MobileHarnessException {
    return getPersistentJobInfoProto(jobPersistentPath)
        .map(jobInfo -> jobInfo.getJobScheduleUnit().getJobLocator().getId());
  }

  /** Resumes JobInfo. */
  public Optional<JobInfo> resumeJobInfo(
      String jobPersistentPath,
      boolean resumeFiles,
      @Nullable String rootDir,
      @Nullable JobInfo newJobInfo)
      throws MobileHarnessException, InterruptedException {
    Optional<JobInfoProto> jobInfoProto = getPersistentJobInfoProto(jobPersistentPath);
    if (jobInfoProto.isPresent()) {
      try {
        JobInfo jobInfo = JobInfoConverter.fromProto(resumeFiles, rootDir, jobInfoProto.get());
        jobInfo.properties().add(Job._IS_RESUMED_JOB, "true");

        if (!resumeFiles && newJobInfo != null) {
          jobInfo.files().addAll(newJobInfo.files().getAll());
          jobInfo
              .protoSpec()
              .setProto(
                  getResumedJobSpecWithRawFiles(
                      jobInfo.protoSpec().getProto(), newJobInfo.protoSpec().getProto()));
          jobInfo
              .scopedSpecs()
              .addAll(
                  getResumedJobSpecWithRawFiles(
                      jobInfo.scopedSpecs().toJobSpec(JobSpecHelper.getDefaultHelper()),
                      newJobInfo.scopedSpecs().toJobSpec(JobSpecHelper.getDefaultHelper())));
        }
        logger.atInfo().log(
            "Successfully resumed JobInfo:\n%s from %s", jobInfo, jobPersistentPath);
        return Optional.of(jobInfo);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            InfraErrorId.CLIENT_LONGEVITY_JOB_INFO_RECOVER_ERROR,
            String.format(
                "Failed to resume JobInfo from file %s: %s", jobPersistentPath, e.getMessage()),
            e);
      }
    }
    return Optional.empty();
  }

  private Optional<JobInfoProto> getPersistentJobInfoProto(String jobPersistentPath)
      throws MobileHarnessException {
    if (!storageBackend.exists(jobPersistentPath)) {
      logger.atInfo().log(
          "Job persistent file does not exist, it could happen if it is the first run of the job.");
      return Optional.empty();
    }
    try {
      String jobInfoProtoStr = storageBackend.read(jobPersistentPath);

      if (jobInfoProtoStr.isEmpty()) {
        logger.atWarning().log(
            "Job persistent file exists but the size is 0. This can happen when the previous job"
                + " failed to persist the job.");
        return Optional.empty();
      }
      JobInfoProto jobInfoProto =
          ProtoTextFormat.parse(
              jobInfoProtoStr,
              JobSpecHelper.getDefaultHelper().getExtensionRegistry(),
              JobInfoProto.class);
      return Optional.of(jobInfoProto);
    } catch (MobileHarnessException | ParseException e) {
      throw new MobileHarnessException(
          InfraErrorId.CLIENT_LONGEVITY_JOB_INFO_RECOVER_ERROR,
          String.format(
              "Failed to resume JobInfo from file %s: %s", jobPersistentPath, e.getMessage()),
          e);
    }
  }

  /** Saves JobInfo if the param "job_persistent_path" is specified. */
  public void persistentJobInfoIfNeeded(JobInfo jobInfo) {
    String jobPersistentPath = jobInfo.params().get(PARAM_JOB_PERSISTENT_PATH);
    if (jobPersistentPath == null) {
      logger.atInfo().log("No persistent path specified, won't save JobInfo");
      return;
    }

    Lock lock = PERSIST_JOB_INFO_STRIPED_LOCKS.get(jobPersistentPath);
    logger.atInfo().log("Acquiring lock to persist job info to %s", jobPersistentPath);
    lock.lock();
    try {
      logger.atInfo().log("Start to persist job info to %s", jobPersistentPath);
      Stopwatch stopwatch = Stopwatch.createStarted();
      JobInfoProto jobInfoProto = JobInfoConverter.toProto(jobInfo);
      storageBackend.write(jobPersistentPath, TextFormat.printer().printToString(jobInfoProto));
      stopwatch.stop();
      logger.atInfo().log("Stop persisting job info after %s", stopwatch.elapsed());
    } catch (MobileHarnessException e) {
      logger.atInfo().withCause(e).log(
          "Failed to write JobInfo into remote file %s", jobPersistentPath);
      jobInfo
          .warnings()
          .add(
              new MobileHarnessException(
                  InfraErrorId.CLIENT_LONGEVITY_JOB_INFO_PERSISTENT_ERROR,
                  String.format("Failed to write JobInfo into remote file %s", jobPersistentPath),
                  e));
    } finally {
      lock.unlock();
    }
  }

  /** Removes persistent file for one JobInfo if the file exists. */
  public void removeJobPersistentFileIfExist(JobInfo jobInfo) {
    String jobPersistentPath = jobInfo.params().get(PARAM_JOB_PERSISTENT_PATH);
    if (jobPersistentPath == null) {
      logger.atInfo().log("No persistent path specified, won't save JobInfo into remote file");
      return;
    }
    if (!storageBackend.exists(jobPersistentPath)) {
      logger.atInfo().log("Job persistent file %s does not exist.", jobPersistentPath);
      return;
    }
    try {
      storageBackend.remove(jobPersistentPath);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to remove job persistent file %s", jobPersistentPath);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private JobSpec getResumedJobSpecWithRawFiles(JobSpec resumedJobSpec, JobSpec rawJobSpec)
      throws MobileHarnessException, InterruptedException {
    List<List<String>> rawFiles = new ArrayList<>();

    JobSpecWalker.resolve(
        rawJobSpec,
        new JobSpecWalker.Visitor() {
          @Override
          public void visitPrimitiveFileField(Message.Builder builder, FieldDescriptor field) {
            if (field.getType() != FieldDescriptor.Type.STRING) {
              return;
            }
            List<String> currentResolvedFiles = new ArrayList<>();
            if (field.isRepeated()) {
              int size = builder.getRepeatedFieldCount(field);
              for (int i = 0; i < size; i++) {
                currentResolvedFiles.add((String) builder.getRepeatedField(field, i));
              }
            } else {
              currentResolvedFiles.add((String) builder.getField(field));
            }
            rawFiles.add(currentResolvedFiles);
          }
        });
    // Assumes the raw files and resolved files have the same count and order.
    Iterator<List<String>> rawFilesIterator = rawFiles.iterator();
    return JobSpecWalker.resolve(
        resumedJobSpec,
        new JobSpecWalker.Visitor() {
          @Override
          public void visitPrimitiveFileField(Message.Builder builder, FieldDescriptor field) {
            if (field.getType() != FieldDescriptor.Type.STRING) {
              return;
            }
            if (rawFilesIterator.hasNext()) {
              List<String> files = rawFilesIterator.next();
              if (field.isRepeated()) {
                builder.setField(field, files);
              } else {
                builder.setField(field, files.get(0));
              }
            }
          }
        });
  }
}
