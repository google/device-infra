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

import com.google.common.collect.ImmutableList;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.mobileharness.api.model.job.out.TouchableTiming;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Errors;
import com.google.wireless.qa.mobileharness.shared.model.job.out.JobOutInternalFactory;
import java.util.Collection;

/**
 * Utility class to convert a {@link Errors} to a list of {@link ExceptionDetail}s in forward and
 * backward.
 */
final class ErrorsConverter {

  private ErrorsConverter() {}

  /** Gets a {@link Errors} by the given log, timing and collection of {@link ExceptionDetail}s. */
  static Errors fromProto(
      LogCollector<?> log, TouchableTiming timing, Collection<ExceptionDetail> exceptionDetails) {
    return JobOutInternalFactory.createErrors(
        com.google.devtools.mobileharness.api.model.job.out.JobOutInternalFactory.createWarnings(
            log, timing, exceptionDetails));
  }

  /** Gets a list of {@link ExceptionDetail}s by the given {@link Errors}. */
  static ImmutableList<ExceptionDetail> toProto(Errors errors) {
    return errors.toWarnings().getAll();
  }
}
