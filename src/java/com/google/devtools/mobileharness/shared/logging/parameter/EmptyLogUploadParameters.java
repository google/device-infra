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

package com.google.devtools.mobileharness.shared.logging.parameter;

import com.google.devtools.mobileharness.shared.logging.controller.uploader.EmptyLogUploaderModule;
import com.google.inject.Module;

/**
 * The empty implementation of log uploader parameters, we will not upload any logs to remote if
 * it's specified.
 */
public class EmptyLogUploadParameters implements LogUploaderParameters {
  public static EmptyLogUploadParameters of() {
    return new EmptyLogUploadParameters();
  }

  @Override
  public Module getLogUploaderModule() {
    return new EmptyLogUploaderModule();
  }
}
