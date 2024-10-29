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

/** Spec for AndroidFilerPusherDecorator. */
public interface AndroidFilePusherSpec {
  @ParamAnnotation(
      required = true,
      help =
          "Param for specifying the files/dirs to be pushed from host to device. "
              + "Format: tag_of_src_path_on_host1[:des_path_on_device1]"
              + "[,tag_of_src_path_on_host2[:des_path_on_device2]].... "
              + "If the files/dirs exist on device before running test, they will be overrided. "
              + "If the path on device is not specified, a default path will be used based on the "
              + "device management system. "
              + "The tag in above context points to the key of given file list defined in "
              + "mobile_test, see http://screenshot/fxgrC1h5XMH.png. "
              + "Note that if the tag_of_src_path_on_host1 is a file, and the des_path_on_device1 "
              + "is a non-exist directory, i.e., ending with '/', the push fails unless you set "
              + "prepare_des_dir_when_src_is_file to true. If tag_of_src_path_on_host1 is a "
              + "direcotry, the existence of des_path_on_device1 won't affect the push result.")
  public static final String PARAM_PUSH_FILES = "push_files";

  @ParamAnnotation(
      required = false,
      help =
          "DEPRECATED: This param is ignored. Set flag cache_pushed_files to true instead. "
              + "Whether to skip pushing files if the files are pushed before. "
              + "By default it is disabled. Note it won't verify the change of files on device. "
              + "They can be changed by other decorators or test codes. Use it at your own risk.")
  public static final String PARAM_CACHE_FILES = "cache_files";

  @ParamAnnotation(
      required = false,
      help =
          "If the files/dirs going to push exist in the device "
              + "before running test, whether to delete them before the test. By default, "
              + "this is set to false. You can set it to true to enable it.")
  public static final String PARAM_REMOVE_FILES_BEFORE_PUSH = "remove_files_before_push";

  @ParamAnnotation(
      required = false,
      help =
          "If the source of push_files is a file, and the destination ends with \"/\", set it true"
              + " create the destination direcotry if it doesn't exist. It will not double created"
              + " the directory, so it is safe to set it true if you are not sure whether the"
              + " target directory exists.")
  public static final String PARAM_PREPARE_DES_DIR_WHEN_SRC_IS_FILE =
      "prepare_des_dir_when_src_is_file";

  @ParamAnnotation(
      required = false,
      help =
          "Param for declaring the timeout(seconds) of the files/dirs to be pushed "
              + "from host to device."
              + "Default is 5 minutes. See at com.google.wireless.qa.mobileharness"
              + ".shared.command.CommandExecutor.DEFAULT_COMMAND_TIMEOUT_MS.")
  public static final String PARAM_PUSH_TIMEOUT_SEC = "push_timeout_sec";

  public static final String FILE_MAP_KEY_VALUE_DELIMITER = ":";
  public static final String FILE_ENTRY_DELIMITER = ",";
  public static final String DEFAULT_PATH_ON_DEVICE = "mh/";
  public static final int INVALID_PUSH_TIMEOUT = -1;
}
