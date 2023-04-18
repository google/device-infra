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

package com.google.devtools.deviceaction.framework.operations;

import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;

/** An {@link Operation} to clean up staged sessions. */
public class ModuleCleaner implements Operation {

  private static final String APEX_DATA_DIR = "/data/apex/active/";
  private static final String STAGING_DATA_DIR = "/data/app-staging/";
  private static final String SESSION_DATA_DIR = "/data/apex/sessions/";

  private final AndroidPhone device;

  public ModuleCleaner(AndroidPhone device) {
    this.device = device;
  }

  /** Cleans up installed packages. */
  public void cleanUpSessions() throws DeviceActionException, InterruptedException {
    boolean reboot = false;

    if (!device.listFiles(APEX_DATA_DIR).isEmpty()) {
      device.removeFiles(APEX_DATA_DIR + "*");
      reboot = true;
    }
    if (!device.listFiles(STAGING_DATA_DIR).isEmpty()) {
      device.removeFiles(STAGING_DATA_DIR + "*");
      reboot = true;
    }
    if (!device.listFiles(SESSION_DATA_DIR).isEmpty()) {
      device.removeFiles(SESSION_DATA_DIR + "*");
      reboot = true;
    }
    if (reboot) {
      device.reboot();
    }
  }
}
