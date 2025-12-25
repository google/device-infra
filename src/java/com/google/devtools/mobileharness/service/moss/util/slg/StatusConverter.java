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

package com.google.devtools.mobileharness.service.moss.util.slg;

import com.google.devtools.mobileharness.api.model.proto.Test.TestStatus;
import com.google.devtools.mobileharness.service.moss.util.converter.TestStatusConverter;
import com.google.wireless.qa.mobileharness.shared.model.job.out.JobOutInternalFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Status;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;

/**
 * Utility class to help convert the {@link Status} class to {@link TestStatus} proto in forward and
 * backward.
 */
final class StatusConverter {

  private StatusConverter() {}

  /** Gets a {@link Status} by the given {@link Timing} and {@link TestStatus}. */
  static Status fromProto(Timing timing, TestStatus testStatus) {
    return JobOutInternalFactory.createStatus(
        timing, TestStatusConverter.convertDevtoolsProtoToQaProto(testStatus));
  }

  /** Gets a {@link TestStatus} to represent the given {@link Status}. */
  static TestStatus toProto(Status status) {
    return TestStatusConverter.convertQaProtoToDevtoolsProto(status.get());
  }
}
