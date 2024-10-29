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

/** The keys of android build spec. */
public enum AndroidBuildSpecKey {
  /** Numeric build ID (2989192), candidate name ("NRC58D"), or "latest". */
  BUILD_ID,
  /**
   * Build type: submitted, pending, external, train. If BUILD_ID is "latest" and BUILD_TYPE not
   * specified, BUILD_TYPE will default to submitted.
   */
  BUILD_TYPE,
  /** Optional: if BUILD_ID is "latest", identifies an ota group (e.g. droidfood). */
  OTA_GROUP,
  /** Git branch to fetch from (e.g. nyc_release) */
  BRANCH,
  /** Build target to download (e.g. shamu-userdebug) */
  TARGET,
  /**
   * Name of build output file to download (e.g. bullhead-img.zip). If the file is under a
   * directory, full path should be listed here (e.g. apks/file-name.apk).
   */
  FILE,
  /**
   * Optional: if FILE is a zip, extract it and return this file from inside it. You can specify a
   * list of particular files being extracted from zip file by using delimiter ";;", for example,
   * "android-build::...,file=device-tests.zip,unzip_file=path/to/file1;;path/to/file2;;..."
   */
  UNZIP_FILE;
}
