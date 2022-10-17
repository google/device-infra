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
import com.google.protobuf.Message;

/**
 * An empty {@link JobSpecWrapper}. It always returns return the default instance for any valid
 * JobSpec. It's useful for caller who wants to use a method with JobSpecWrapper as a param but just
 * wants an empty one, such as used in unit tests.
 */
public class EmptyJobSpec implements JobSpecWrapper {

  /** Returns empty protobuf message for any valid {@code specClass}. */
  @Override
  public <T extends Message> T getSpec(Class<T> specClass) throws MobileHarnessException {
    try {
      return JobSpecHelper.getDefaultInstance(specClass);
    } catch (IllegalArgumentException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR, "Invalid Message class: " + specClass, e);
    }
  }
}
