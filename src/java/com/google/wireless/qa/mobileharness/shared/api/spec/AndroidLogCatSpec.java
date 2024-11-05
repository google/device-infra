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

package com.google.wireless.qa.mobileharness.shared.api.spec;

import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;

/** Specs for AndroidLogCatDecorator. */
public interface AndroidLogCatSpec {

  @ParamAnnotation(
      required = false,
      help =
          "By default, will merge the device log to the test log. If set to 'true', will save the "
              + "log in file and send back to client or Sponge.\nNote: for Adb command output, it "
              + "uses \"\\r\\n\" as line separator on SDK<=23, while uses \"\\n\" as line "
              + "separator on SDK>23. And logs are saved to file transparently.")
  String PARAM_LOG_TO_FILE = "log_to_file";

  @ParamAnnotation(
      required = false,
      help =
          "By default, we only dump the device log after the test is done. If set to 'true', will "
              + "fork a new thead to dump the real-time device log asynchronously when the test is "
              + "running.")
  String PARAM_ASYNC_LOG = "async_log";

  @ParamAnnotation(
      required = false,
      help =
          "A series of tag[:priority], where tag is a log component tag (or '*' for all), and "
              + "priority is:"
              + "<ul>"
              + "<li>V Verbose</li>"
              + "<li>D Debug</li>"
              + "<li>I Info</li>"
              + "<li>W Warn</li>"
              + "<li>E Error</li>"
              + "<li>F Fatal</li>"
              + "<li>S Silent (suppress all output)</li>"
              + "</ul>"
              + "'*' means '*:Debug', and tag by itself means 'tag:Verbose'. If not specified, it "
              + "means '*:Warn'.")
  String PARAM_LOG_FILTER_SPECS = "log_filter_specs";

  @ParamAnnotation(
      required = false,
      help =
          "The logcat options. e.g. '-b main -v time'. See "
              + "<a href='http://developer.android.com/tools/help/logcat.html'>logcat help</a> "
              + "for more detail.")
  String PARAM_LOG_OPTIONS = "log_options";

  @ParamAnnotation(
      required = false,
      help = "Whether clear the log before running the test. By default it is True.")
  String PARAM_CLEAR_LOG = "clear_log";

  @ParamAnnotation(
      required = false,
      help = "The size of the log buffer. If not specified, use default.")
  String PARAM_LOG_BUFFER_SIZE_KB = "log_buffer_size_kb";

  @ParamAnnotation(
      required = false,
      help =
          "Setting of logcat buffer is only allowed in API 21+ and throws and exception if older."
              + "If true, the exception is ignored and we proceed with the test.")
  String PARAM_IGNORE_LOG_BUFFER_SIZE_SET_FAILURE = "ignore_log_buffer_size_set_failure";

  /** Minimum log buffer size. Logcat supports buffer size from 64K to 16M (=16384K). */
  int LOG_BUFFER_SIZE_MIN_KB = 64;

  /** Maximum log buffer size. */
  int LOG_BUFFER_SIZE_MAX_KB = 16384;
}
