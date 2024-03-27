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

package com.google.devtools.mobileharness.infra.client.longrunningservice.constant;

/** Constants of session properties. */
public class SessionProperties {

  /**
   * If a session add this key to its properties (by its session plugins or in the request), then
   * after the session ends, a log file will be copied to the given path, which contains all logs of
   * the server during the session execution. The plugins or the caller of the session are
   * responsible for preparing the parent dir.
   */
  public static final String PROPERTY_KEY_SERVER_SESSION_LOG_PATH =
      "olc_server_session_log_file_path";

  private SessionProperties() {}
}
