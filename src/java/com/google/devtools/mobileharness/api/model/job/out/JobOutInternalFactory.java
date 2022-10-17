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

package com.google.devtools.mobileharness.api.model.job.out;

import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.mobileharness.service.moss.proto.Result.TimeDetail;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import java.util.Collection;

/**
 * The factory to help create instances under this package. It's only used internally to support
 * making job/test data models persistent.
 */
public final class JobOutInternalFactory {

  private JobOutInternalFactory() {}

  /** Creates a {@link TouchableTiming} instance by the given {@link TimeDetail}. */
  public static TouchableTiming createTouchableTiming(TimeDetail timeDetail) {
    return new TouchableTiming(timeDetail);
  }

  /**
   * Creates a {@link Warnings} instance by the given job/test log, {@link TouchableTiming} and a
   * collection of {@link ExceptionDetail}s
   */
  public static Warnings createWarnings(
      LogCollector<?> log, TouchableTiming timing, Collection<ExceptionDetail> exceptionDetails) {
    return new Warnings(log, timing, exceptionDetails);
  }
}
