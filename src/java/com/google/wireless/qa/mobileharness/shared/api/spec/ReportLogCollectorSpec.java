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

/** Specs for ReportLogCollectorDecorator. */
@SuppressWarnings("InterfaceWithOnlyStatics") // Implemented by the corresponding decorator
public interface ReportLogCollectorSpec {

  @ParamAnnotation(
      required = false,
      help = "The directory on the device to copy to the results dir")
  String PARAM_REPORT_LOG_SRC_DIR = "report_log_src_dir";

  @ParamAnnotation(required = false, help = "The directory under the result to store the files")
  String PARAM_REPORT_LOG_DEST_DIR = "report_log_dest_dir";

  @ParamAnnotation(required = false, help = "The temp directory containing host-side report logs")
  String PARAM_REPORT_LOG_TEMP_DIR = "report_log_temp_dir";

  @ParamAnnotation(required = false, help = "Create unique directory for each device")
  String PARAM_REPORT_LOG_DEVICE_DIR = "report_log_device_dir";
}
