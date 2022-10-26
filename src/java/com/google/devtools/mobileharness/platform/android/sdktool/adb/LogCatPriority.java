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

package com.google.devtools.mobileharness.platform.android.sdktool.adb;

/**
 * Log priority of logcat. When specifies a priority in logcat, any log messages with the same or
 * higher priority will be shown.
 */
public enum LogCatPriority {
  VERBOSE,
  DEBUG,
  INFO,
  WARN,
  ERROR,
  FATAL,
  SILENT;
}
