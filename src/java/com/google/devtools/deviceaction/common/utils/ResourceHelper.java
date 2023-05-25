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

import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import java.nio.file.Path;
import java.util.Optional;

/** A helper class to provide resources. */
public interface ResourceHelper {

  /**
   * Gets a directory for all temporary files.
   *
   * @throws DeviceActionException if the dir doesn't exist.
   */
  Path getTmpFileDir() throws DeviceActionException;

  /**
   * Gets a directory for all generated files.
   *
   * @throws DeviceActionException if the dir doesn't exist.
   */
  Path getGenFileDir() throws DeviceActionException;

  /** Gets the java binary path. */
  Path getJavaBin();

  /** Gets an {@link Aapt} if possible. */
  Optional<Aapt> getAapt();

  /** Gets an {@link Adb} if possible. */
  Optional<Adb> getAdb();

  /** Gets the path to bundletool jar. */
  Optional<Path> getBundletoolJar();

  /** Gets a credential file. */
  Optional<Path> getCredFile();

  /** Gets a {@link CommandExecutor}. */
  CommandExecutor getCommandExecutor();
}
