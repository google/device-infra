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

package com.google.wireless.qa.mobileharness.shared.model.job.in.spec;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;
import java.util.function.Supplier;

/**
 * Gets spec with class {@code specClass} from {@code jobSpec}. If a field is not set in {@code
 * jobSpec} but is specified in {@code defaultValues}, it will be assigned with the default value.
 * for example:
 *
 * <p>Notice that the settings in JobSpec always win if there are conflicts.
 */
public class ProtoJobSpec implements JobSpecWrapper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** JobSpec to wrapper. */
  private JobSpec jobSpec;

  /** Helper of handling {@link #jobSpec}. */
  private final Supplier<JobSpecHelper> jobSpecHelper;

  /** Creates an empty wrapper. */
  public ProtoJobSpec() {
    this(JobSpec.getDefaultInstance());
  }

  /**
   * Creates a wrapper of {@code jobSpec} with default value {@code defaultValues}.
   *
   * @param jobSpec wrapped {@code JobSpec}
   */
  public ProtoJobSpec(JobSpec jobSpec) {
    this(
        jobSpec,
        new Supplier<JobSpecHelper>() {
          @Override
          public JobSpecHelper get() {
            return JobSpecHelper.getDefaultHelper();
          }
        });
  }

  @VisibleForTesting
  ProtoJobSpec(JobSpec jobSpec, Supplier<JobSpecHelper> jobSpecHelper) {
    this.jobSpecHelper = jobSpecHelper;
    this.jobSpec = jobSpec;
  }

  /**
   * Tries to resolve unknown spec extensions in {@code jobSpec}, If failed to resolve any unknown
   * field, or any exception occurs during the resolving, the {@link #jobSpec} keeps unchanged.
   */
  @CanIgnoreReturnValue
  public ProtoJobSpec tryResolveUnknownExtension() throws InterruptedException {
    try {
      JobSpec resolvedJobSpec = jobSpecHelper.get().resolveUnknownSpecExtension(jobSpec);
      jobSpec = resolvedJobSpec;
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to solve unknown extension in job spec: %s; error: %s", jobSpec, e);
    }
    return this;
  }

  /**
   * Gets spec data of class {@code specClass} from wrapped data.
   *
   * @throws MobileHarnessException if {@code specClass} is not a valid extension of {@link JobSpec}
   */
  @Override
  public <T extends Message> T getSpec(Class<T> specClass) throws MobileHarnessException {
    return jobSpecHelper.get().getSpec(jobSpec, specClass);
  }

  /** Gets wrapped protobuf. */
  public JobSpec getProto() {
    return jobSpec;
  }

  /** Sets wrapped protobuf. */
  @CanIgnoreReturnValue
  public ProtoJobSpec setProto(JobSpec jobSpec) {
    this.jobSpec = jobSpec;
    return this;
  }
}
