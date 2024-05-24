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

  /** If the key is present, the session was aborted when it was running. */
  public static final String PROPERTY_KEY_SESSION_ABORTED_WHEN_RUNNING =
      "olc_server_session_aborted_when_running";

  /** A string property to identify the client of a session. */
  public static final String PROPERTY_KEY_SESSION_CLIENT_ID = "olc_server_session_client_id";

  /**
   * If a session add this key to its properties (by its session plugins or in the request), then
   * after the session ends, a log file will be copied to the given path, which contains all logs of
   * the server during the session execution.
   *
   * <p>The plugins or the caller of the session are responsible for preparing the parent dir.
   *
   * <p>The copy operation may happen with a short latency after the session becomes finished.
   */
  public static final String PROPERTY_KEY_SERVER_SESSION_LOG_PATH =
      "olc_server_session_log_file_path";

  /**
   * A property to indicate that at least one test in the session has started (received
   * TestStartingEvent). It is used to detect whether a running session is safe to remove.
   */
  public static final String PROPERTY_KEY_SESSION_CONTAIN_STARTED_TEST =
      "olc_server_session_contain_started_test";

  /** A property to indicate the ID of the corresponding command. */
  public static final String PROPERTY_KEY_COMMAND_ID = "olc_server_session_command_id";

  private SessionProperties() {}
}
