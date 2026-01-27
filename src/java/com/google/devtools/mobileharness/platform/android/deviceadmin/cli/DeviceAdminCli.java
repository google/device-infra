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

package com.google.devtools.mobileharness.platform.android.deviceadmin.cli;

import com.google.common.flags.Flag;
import com.google.common.flags.FlagSpec;
import com.google.common.flags.Flags;
import com.google.inject.Guice;
import com.google.inject.Injector;
import javax.inject.Inject;

/** The CLI for controlling device admin app behaviors. */
final class DeviceAdminCli {
  public enum Action {
    NOOP, // do nothing
    INSTALL, // install the device admin app to the device
    ENABLE, // enable the device admin app through "set-device-owner"
    LOCK, // enables user restrictions and hides apps
    UNLOCK // remove user restrictions and allows the device to be reset
  }

  @FlagSpec(name = "action", help = "The action to operation")
  private static final Flag<Action> action = Flag.value(Action.NOOP);

  @FlagSpec(name = "serial", help = "Device serial ID")
  private static final Flag<String> serial = Flag.value("");

  @FlagSpec(name = "admin_app_path", help = "Path to the device admin apk")
  private static final Flag<String> adminAppPath = Flag.value("");

  @Inject
  private DeviceAdminCli() {}

  private void runOperation(String serial, Action action) {
    // TODO Invoke operation util to run the operation.
  }

  public static void main(String[] args) {
    Flags.parse(args);

    if (action.get() == Action.NOOP) {
      throw new IllegalArgumentException("--action must be specified");
    }
    if (serial.get().isEmpty()) {
      throw new IllegalArgumentException("--device_id must be specified");
    }
    if (action.get() == Action.INSTALL && adminAppPath.get().isEmpty()) {
      throw new IllegalArgumentException(
          "--admin_app_path must be specified when --action=INSTALL");
    }

    Injector injector = Guice.createInjector(new DeviceAdminCliModule());
    DeviceAdminCli cli = injector.getInstance(DeviceAdminCli.class);

    cli.runOperation(serial.get(), action.get());
  }
}
