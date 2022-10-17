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

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * A union {@code JobSpecWrapper}. Use method {@link #addWrapper} to add more sub wrappers. {@link
 * #getSpec} returns a merged the result of all added sub wrappers.
 *
 * <p>The order of sub wrappers matters. The value in the first added wrapper will be adopted first.
 */
public class UnionJobSpec implements JobSpecWrapper {

  private final List<JobSpecWrapper> wrappers = new ArrayList<>();

  /** Adds a sub wrappers. The The value in the first added wrapper will be adopted first. */
  @CanIgnoreReturnValue
  public UnionJobSpec addWrapper(JobSpecWrapper wrapper) {
    // We use the default protobuf merge method {@link Message.Builder#mergeFrom} in implementation.
    // That method uses the last value as the merged value, so we add wrappers reversely, to make
    // the merge result as expectation.
    wrappers.add(0, wrapper);
    return this;
  }

  /**
   * Gets spec data of class {@code specClass} from {@code jobSpec}.
   *
   * @throws MobileHarnessException if {@code specClass} is not a valid extension of {@link JobSpec}
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T extends Message> T getSpec(Class<T> specClass) throws MobileHarnessException {
    Message.Builder builder = null;
    try {
      builder = JobSpecHelper.getDefaultInstance(specClass).newBuilderForType();
    } catch (IllegalArgumentException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR, "Invalid Message class: " + specClass, e);
    }

    for (JobSpecWrapper wrapper : wrappers) {
      builder.mergeFrom(wrapper.getSpec(specClass));
    }
    return (T) builder.build();
  }
}
