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

  private final LogProject logProject;

  public static StackdriverLogUploaderParameters of(LogProject logProject) {
    return new StackdriverLogUploaderParameters(logProject);
  }

  private StackdriverLogUploaderParameters(LogProject logProject) {
    this.logProject = logProject;
  }

  @Override
  public Module getLogUploaderModule() {
    return new StackdriverLogUploaderModule(getSecretFileName());
  }

  /** Gets the client secret file name based on the type name. */
  private String getSecretFileName() {
    return String.format("stackdriver_client_secret_for_%s", logProject.getProjectName());
  }
}
