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

package com.google.wireless.qa.mobileharness.shared.android;

import java.util.Optional;

/** Mobile Harness Managed Android Package List */
public enum AndroidPackages {
  AGSA("com.google.android.googlequicksearchbox", null),
  CHROME("com.android.chrome", null),
  GMS("com.google.android.gms", null),
  GSF("com.google.android.gsf", null),
  LAUNCHER_1("com.android.launcher", null),
  LAUNCHER_3("com.android.launcher3", null),
  LAUNCHER_GEL("com.google.android.launcher", null),
  MH_DAEMON(
      "com.google.wireless.qa.mobileharness.tool.android.daemon",
      "com.google.wireless.qa.mobileharness.tool.android.daemon.DaemonActivity"),
  MH_SWITCH_LANGUAGE(
      "com.google.wireless.qa.mobileharness.tool.android.language", ".SwitchLanguageUtil"),
  MH_MOCK_LOCATION(
      "com.google.wireless.qa.mobileharness.tool.android.location", ".MockLocationActivity"),
  MONKEY("com.android.commands.monkey", null),
  STATUSBARHIDER("com.google.android.apps.internal.statusbarhider", ".StatusbarHiderActivity"),
  VRCORE("com.google.vr.vrcore", null);

  private final String packageName;

  private final String activityName;

  AndroidPackages(String packageName, String activityName) {
    this.packageName = packageName;
    this.activityName = activityName;
  }

  public String getPackageName() {
    return packageName;
  }

  public Optional<String> getActivityName() {
    return Optional.ofNullable(activityName);
  }
}
