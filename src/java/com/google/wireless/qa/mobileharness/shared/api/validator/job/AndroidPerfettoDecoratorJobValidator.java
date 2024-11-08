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

package com.google.wireless.qa.mobileharness.shared.api.validator.job;

import static com.google.wireless.qa.mobileharness.shared.api.step.android.PerfettoStep.FILE_PERFETTO_CONFIG;
import static com.google.wireless.qa.mobileharness.shared.api.step.android.PerfettoStep.PARAM_PERFETTO_BUFFER_SIZE;
import static com.google.wireless.qa.mobileharness.shared.api.step.android.PerfettoStep.PARAM_PERFETTO_PACKAGE_NAME;
import static com.google.wireless.qa.mobileharness.shared.api.step.android.PerfettoStep.PARAM_PERFETTO_RUNNING_TIME;
import static com.google.wireless.qa.mobileharness.shared.api.step.android.PerfettoStep.PARAM_PERFETTO_TAGS;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Validates job parameters for the {@code AndroidPerfettoDecorator}. */
public class AndroidPerfettoDecoratorJobValidator implements JobValidator {

  @Override
  public List<String> validate(JobInfo job) {
    List<String> errors = new ArrayList<>();

    Optional<String> configParamError = verifyEitherConfigOrParamExists(job);
    configParamError.ifPresent(errors::add);

    return errors;
  }

  private Optional<String> verifyEitherConfigOrParamExists(JobInfo job) {
    boolean hasAnyPerfettoParam =
        job.params().get(PARAM_PERFETTO_TAGS) != null
            || job.params().get(PARAM_PERFETTO_RUNNING_TIME) != null
            || job.params().get(PARAM_PERFETTO_PACKAGE_NAME) != null
            || job.params().get(PARAM_PERFETTO_BUFFER_SIZE) != null;
    boolean hasConfig = false;
    try {
      hasConfig = job.files().getSingle(FILE_PERFETTO_CONFIG) != null;
    } catch (MobileHarnessException e) {
      if (e.getErrorId() == BasicErrorId.JOB_OR_TEST_FILE_MULTI_PATHS) {
        return Optional.of("Only one perfetto config file is accepted");
      }
    }
    if (hasAnyPerfettoParam && hasConfig) {
      return Optional.of(
          "Using Perfetto config file will ignore all perfetto params. Please just use config or"
              + " params.");
    }
    return Optional.empty();
  }
}
