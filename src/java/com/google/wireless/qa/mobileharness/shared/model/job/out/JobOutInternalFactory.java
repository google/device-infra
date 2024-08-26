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

package com.google.wireless.qa.mobileharness.shared.model.job.out;

import com.google.devtools.mobileharness.api.model.job.out.TouchableTiming;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.devtools.mobileharness.service.moss.proto.Slg.PropertiesProto;
import com.google.devtools.mobileharness.service.moss.proto.Slg.RemoteFilesProto;
import com.google.devtools.mobileharness.service.moss.proto.Slg.ResultProto;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;

/**
 * The factory to help create instances under this package. It's only used internally to support
 * making job/test data models persistent.
 */
public final class JobOutInternalFactory {

  private JobOutInternalFactory() {}

  /** Creates a {@link Errors} instance by the given {@link Warnings}. */
  public static Errors createErrors(Warnings warnings) {
    return new Errors(warnings);
  }

  /**
   * Creates a {@link Properties} instance by the given {@link Timing} and {@link PropertiesProto}.
   */
  public static Properties createProperties(Timing timing, PropertiesProto propertiesProto) {
    return new Properties(timing, propertiesProto);
  }

  /**
   * Creates a {@link RemoteFiles} instance by the given {@link Timing} and {@link
   * RemoteFilesProto}.
   */
  public static RemoteFiles createRemoteFiles(Timing timing, RemoteFilesProto remoteFilesProto) {
    return new RemoteFiles(timing, remoteFilesProto);
  }

  /**
   * Creates a {@link Result} instance by the given {@link Timing}, {@link Params} and {@link
   * ResultProto}.
   */
  public static Result createResult(Timing timing, Params params, ResultProto resultProto) {
    return new Result(timing, params, resultProto);
  }

  /** Creates a {@link Status} instance by the given {@link Timing} and {@link TestStatus}. */
  public static Status createStatus(Timing timing, TestStatus testStatus) {
    return new Status(timing, testStatus);
  }

  /** Creates a {@link Timing} instance by the given {@link TouchableTiming}. */
  public static Timing createTiming(TouchableTiming newTiming) {
    return new Timing(newTiming);
  }
}
