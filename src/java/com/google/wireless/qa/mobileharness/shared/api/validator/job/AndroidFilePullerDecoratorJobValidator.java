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

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidFilePullerSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidFilePullerDecoratorSpec;
import java.util.ArrayList;
import java.util.List;

/** Job validator for {@link AndroidFilePullerDecorator} driver decorator. */
public class AndroidFilePullerDecoratorJobValidator
    implements JobValidator, SpecConfigable<AndroidFilePullerDecoratorSpec> {

  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    List<AndroidFilePullerDecoratorSpec> specs = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    try {
      specs =
          job.combinedSpecForDevices(
              this,
              subDeviceSpec ->
                  subDeviceSpec.decorators().getAll().contains("AndroidFilePullerDecorator"));
    } catch (MobileHarnessException e) {
      errors.add("Could not load job specs: " + e);
      return errors;
    }

    for (AndroidFilePullerDecoratorSpec spec : specs) {
      String commaSeparatedPaths = spec.getFilePathOnDevice();
      if (Strings.isNullOrEmpty(commaSeparatedPaths)) {
        errors.add(
            "Missing required parameter [file_path_on_device] for AndroidFilePullerDecorator");
        continue;
      }
      List<String> paths =
          Splitter.on(AndroidFilePullerSpec.PATH_DELIMITER).splitToList(commaSeparatedPaths);
      for (String path : paths) {
        if (StrUtil.isEmptyOrWhitespace(path)) {
          errors.add(
              String.format(
                  "Empty path in parameter file_path_on_device. Please provide valid paths. You"
                      + " may accidentally add two delimiter '%s' together without a valid path"
                      + " between them. Or you may accidently add a demiliter '%s' at the"
                      + " beginning or end of the value.",
                  AndroidFilePullerSpec.PATH_DELIMITER, AndroidFilePullerSpec.PATH_DELIMITER));
        }
      }
    }

    return errors;
  }
}
