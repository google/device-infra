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

package com.google.devtools.mobileharness.platform.android.logcat;

/** Interface indicating an event detecting while analyzing logcat. */
public sealed interface LogcatEvent {
  /** Category of processes detected during crashes */
  enum ProcessCategory {
    FAILURE,
    ERROR,
    IGNORED,
    OTHER;
  }

  /** Types of crashes */
  enum CrashType {
    ANDROID_RUNTIME,
    NATIVE,
    ANR,
    UNKNOWN;
  }

  /** Record class to hold the crashed process information from logcat. */
  record CrashedProcess(String name, int pid, ProcessCategory category, CrashType type) {}

  /** Record class holding details of process that crashed. */
  record CrashEvent(CrashedProcess process, String crashLogs) implements LogcatEvent {}

  /** Record class holding device event detected in logcat. */
  record DeviceEvent(String eventName, String tag, String logLines) implements LogcatEvent {}
}
