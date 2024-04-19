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

import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;

/** Dir constants for OLC server. */
public class OlcServerDirs {

  private static final String LOG_DIR_NAME = "olc_server_log";

  public static String getLogDir() {
    return PathUtil.join(DirCommon.getPublicDirRoot(), getLogDirName());
  }

  public static String getLogDirName() {
    return LOG_DIR_NAME;
  }

  private OlcServerDirs() {}
}
