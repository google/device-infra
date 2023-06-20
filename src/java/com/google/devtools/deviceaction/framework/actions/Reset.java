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

package com.google.devtools.deviceaction.framework.actions;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceaction.common.annotations.Annotations.Configurable;
import com.google.devtools.deviceaction.common.annotations.Annotations.FilePath;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.deviceaction.framework.operations.ModulePusher;
import com.google.devtools.deviceaction.framework.proto.action.ResetSpec;
import java.io.File;
import java.util.Optional;

/**
 * An {@link Action} to reset a device.
 *
 * <p>It applies different reset options to different device builds. The params are specified by
 * {@link ResetSpec}.
 *
 * <p>TODO: b/288065604 - Implements the class.
 */
@Configurable(specType = ResetSpec.class)
public final class Reset implements Action {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TAG_RECOVERY_MODULES = "recovery_modules";

  private final ResetSpec spec;
  private final AndroidPhone device;
  // TODO: b/288065604 - Will be used in implementation.
  @SuppressWarnings("unused")
  private final ModulePusher modulePusher;

  private final ImmutableMultimap<String, File> localFiles;

  Reset(
      ModulePusher modulePusher,
      ResetSpec spec,
      AndroidPhone device,
      ImmutableMultimap<String, File> localFiles) {
    this.modulePusher = modulePusher;
    this.spec = spec;
    this.device = device;
    this.localFiles = localFiles;
  }

  @Override
  public void perform() throws DeviceActionException, InterruptedException {
    logger.atInfo().log("Start to reset the device %s with spec:\n%s", device.getUuid(), spec);
  }

  // TODO: b/288065604 - Will be used in implementation.
  @SuppressWarnings("unused")
  @FilePath(tag = TAG_RECOVERY_MODULES)
  private Optional<File> recoveryModules() {
    return localFiles.get(TAG_RECOVERY_MODULES).stream().filter(File::isDirectory).findAny();
  }
}
