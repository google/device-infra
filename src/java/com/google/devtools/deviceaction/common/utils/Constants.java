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

package com.google.devtools.deviceaction.common.utils;

/** A utility class to share constants. */
public final class Constants {

  public static final String APEX_SUFFIX = ".apex";

  public static final String APK_SUFFIX = ".apk";

  public static final String APKS_SUFFIX = ".apks";

  public static final String CAPEX_SUFFIX = ".capex";

  public static final String ZIP_SUFFIX = ".zip";

  public static final String DEVICE_CONFIG_KEY = "device_config";

  public static final String SERIAL_KEY = "serial";

  /** Prefix indicating a GCS file. */
  public static final String GCS_PREFIX = "gcs:";

  /** Separators for properties in flags. */
  public static final char PROPERTY_SEPARATOR = '&';

  static final String GS_PREFIX = "gs://";

  static final String GS_DELIMITER = "/";

  private Constants() {}
}
