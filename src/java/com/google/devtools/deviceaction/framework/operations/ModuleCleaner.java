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

import com.google.common.collect.ImmutableList;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.utils.Conditions;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;

/** An {@link Operation} to clean up staged sessions. */
public class ModuleCleaner implements Operation {
  private static final ImmutableList<String> SESSION_DIRS =
      ImmutableList.of("/data/apex/active/", "/data/app-staging/", "/data/apex/sessions/");

  private final AndroidPhone device;

  public ModuleCleaner(AndroidPhone device) {
    this.device = device;
  }

  /** Cleans up all staged or activated sessions. */
  public void cleanUpSessions() throws DeviceActionException, InterruptedException {
    Conditions.checkState(
        device.isUserdebug(), ErrorType.CUSTOMER_ISSUE, "The device should be userdebug!");
    boolean reboot = false;
    for (String dir : SESSION_DIRS) {
      if (!device.listFiles(dir).isEmpty()) {
        device.removeFiles(allFilesUnderDir(dir));
        reboot = true;
      }
    }
    if (reboot) {
      device.reboot();
    }
  }

  private static String allFilesUnderDir(String dirPath) {
    return dirPath + "*";
  }
}
