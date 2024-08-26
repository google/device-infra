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

package com.google.devtools.mobileharness.service.moss.util.converter;

import com.google.devtools.mobileharness.api.model.proto.Test.TestStatus;
import com.google.wireless.qa.mobileharness.shared.proto.Job;

/**
 * The utility to help converting models between {@link TestStatus}s and {@link Job.TestStatus}s.
 */
public final class TestStatusConverter {

  private TestStatusConverter() {}

  /** Converts the given {@link Job.TestStatus} to the {@link TestStatus}. */
  public static TestStatus doForward(Job.TestStatus testStatus) {
    switch (testStatus) {
      case NEW:
        return TestStatus.NEW;
      case ASSIGNED:
        return TestStatus.ASSIGNED;
      case RUNNING:
        return TestStatus.RUNNING;
      case SUSPENDED:
        return TestStatus.SUSPENDED;
      case DONE:
        return TestStatus.DONE;
    }
    return TestStatus.UNRECOGNIZED;
  }

  /** Converts the given {@link TestStatus} to the {@link Job.TestStatus}. */
  public static Job.TestStatus doBackward(TestStatus testStatus) {
    switch (testStatus) {
      case NEW:
        return Job.TestStatus.NEW;
      case ASSIGNED:
        return Job.TestStatus.ASSIGNED;
      case RUNNING:
        return Job.TestStatus.RUNNING;
      case SUSPENDED:
        return Job.TestStatus.SUSPENDED;
      case DONE:
      case UNRECOGNIZED:
        return Job.TestStatus.DONE;
    }
    return Job.TestStatus.DONE;
  }
}
