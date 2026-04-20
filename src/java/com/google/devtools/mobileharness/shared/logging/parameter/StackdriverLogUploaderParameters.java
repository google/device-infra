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

import com.google.devtools.mobileharness.shared.logging.controller.uploader.StackdriverLogUploaderModule;
import com.google.inject.Module;

/**
 * The Stackdriver log uploader parameters, we will upload the logs to Stackdriver if it's
 * specified.
 */
public class StackdriverLogUploaderParameters implements LogUploaderParameters {

  public static StackdriverLogUploaderParameters create() {
    return new StackdriverLogUploaderParameters();
  }

  private StackdriverLogUploaderParameters() {}

  @Override
  public Module getLogUploaderModule() {
    return new StackdriverLogUploaderModule();
  }
}
