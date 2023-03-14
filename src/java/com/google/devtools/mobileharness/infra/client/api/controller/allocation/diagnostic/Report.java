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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import javax.annotation.Nullable;

/** A human readable explanation of the cause of an allocation failure. */
public interface Report {

  /** Returns whether there was a single resource that could satisfy all requirements of the job. */
  boolean hasPerfectMatch();

  /** Returns the {@link Result} of the report. */
  Result getResult();

  /** Diagnostic result which contains the readable report as well as the error type. */
  @AutoValue
  abstract class Result {
    public static Result create(
        ErrorId errorId, String readableReport, @Nullable MobileHarnessException cause) {
      return new AutoValue_Report_Result(errorId, readableReport, cause);
    }

    // The top error id.
    public abstract ErrorId errorId();

    public abstract String readableReport();

    // The cause of the result if any.
    @Nullable
    public abstract MobileHarnessException cause();
  }

  String DECORATORS_NOT_SUPPORTED = "DECORATORS_NOT_SUPPORTED";
  String DIMENSIONS_NOT_SATISFIED = "DIMENSIONS_NOT_SATISFIED";
  String DIMENSIONS_NOT_SUPPORTED = "DIMENSIONS_NOT_SUPPORTED";
  String DRIVER_NOT_SUPPORTED = "DRIVER_NOT_SUPPORTED";
  String DEVICE_TYPE_NOT_SUPPORTED = "DEVICE_TYPE_NOT_SUPPORTED";
  String NO_ACCESS = "NO_ACCESS";
  String NOT_IDLE = "NOT_IDLE";
  String MISSING = "DEVICE_IS_MISSING";
  String LINE_SEPARATOR = "========================================";
}
