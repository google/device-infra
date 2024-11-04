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

package com.google.wireless.qa.mobileharness.shared.api.spec;

import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidFilePullerDecoratorSpec;

/** Spec for AndroidFilePullerDecorator. */
public interface AndroidFilePullerSpec {
  @ParamAnnotation(
      required = true,
      help =
          "The file/dir paths of the gen files on device. "
              + "You can specify multiple file/dir paths and separate them with ','. "
              + "If the files/dirs exist before running test, they will be deleted before the test "
              + "by default.")
  String PARAM_FILE_PATH_ON_DEVICE =
      AndroidFilePullerDecoratorSpec.getDescriptor()
          .findFieldByNumber(AndroidFilePullerDecoratorSpec.FILE_PATH_ON_DEVICE_FIELD_NUMBER)
          .getName();

  @ParamAnnotation(
      required = false,
      help =
          "If the files/dirs going to pull exist in the device "
              + "before running test, whether to deleted them before the test. By default, "
              + "this is set to true. You can set it to false to disable it.")
  String PARAM_REMOVE_FILES_BEFORE_TEST =
      AndroidFilePullerDecoratorSpec.getDescriptor()
          .findFieldByNumber(AndroidFilePullerDecoratorSpec.REMOVE_FILES_BEFORE_TEST_FIELD_NUMBER)
          .getName();

  @ParamAnnotation(
      required = false,
      help = "Whether to ignore the error when the files don't exist. True by default.")
  String PARAM_IGNORE_FILES_NOT_EXIST =
      AndroidFilePullerDecoratorSpec.getDescriptor()
          .findFieldByNumber(
              AndroidFilePullerDecoratorSpec.SKIP_PULLING_NON_EXIST_FILES_FIELD_NUMBER)
          .getName();

  /** Delimiter for separating multiple paths. */
  String PATH_DELIMITER = ",";
}
