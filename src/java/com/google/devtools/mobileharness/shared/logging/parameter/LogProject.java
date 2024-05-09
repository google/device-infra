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

import com.google.devtools.mobileharness.shared.util.flags.Flags;

/** The log project in stackdriver. */
public enum LogProject {
  LAB_SERVER("lab_server");

  private final String projectName;

  LogProject(String projectName) {
    this.projectName = projectName;
  }

  /** Gets the project name. */
  public String getProjectName() {
    return projectName;
  }

  /** Gets the log name. */
  public String getLogName() {
    return String.format(
        "/projects/%s/logs/%s",
        Flags.instance().stackdriverGcpProjectName.getNonNull(), projectName);
  }

  /** Gets the resource type. */
  public String getResourceType() {
    return Flags.instance().stackdriverResourceType.getNonNull();
  }
}
