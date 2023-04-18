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

import static com.google.common.collect.Multimaps.toMultimap;
import static com.google.common.time.Durations.isPositive;
import static com.google.devtools.deviceaction.common.utils.Constants.APKS_SUFFIX;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.AndroidPackage;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import java.io.File;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/** An {@link Operation} to install mainline modules to the device. */
public class ModuleInstaller implements Operation {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String ENABLE_ROLLBACK = "--enable-rollback";
  private final AndroidPhone device;
  private final Sleeper sleeper;

  public ModuleInstaller(AndroidPhone device, Sleeper sleeper) {
    this.device = device;
    this.sleeper = sleeper;
  }

  /** Installs a collection of packages. */
  public void installModules(Collection<AndroidPackage> packagesToInstall, boolean enableRollback)
      throws DeviceActionException, InterruptedException {
    String[] extraArgs = enableRollback ? new String[] {ENABLE_ROLLBACK} : new String[] {};
    boolean needActivation;
    if (containsApks(packagesToInstall)) {
      // If contains any apks, we assume all packages are provided as apks files and install them
      // using bundletool.
      List<File> bundledPackages =
          packagesToInstall.stream()
              .map(AndroidPackage::apksFile)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .collect(toList());
      needActivation = device.installBundledPackages(bundledPackages, extraArgs);
    } else {
      // Assume all files are apk or apex and install them using adb.
      Multimap<String, File> packageMap =
          packagesToInstall.stream()
              .flatMap(ap -> ap.files().stream().map(f -> Map.entry(ap.info().packageName(), f)))
              .collect(
                  toMultimap(
                      Entry::getKey,
                      Entry::getValue,
                      MultimapBuilder.hashKeys().arrayListValues()::build));
      needActivation = device.installPackages(packageMap, extraArgs);
    }

    if (needActivation) {
      activateStagedInstall();
    }
  }

  /** Installs multiple zipped trains. */
  public void sideloadTrains(List<File> zipFiles, boolean enableRollback)
      throws DeviceActionException, InterruptedException {
    for (File zipFile : zipFiles) {
      sideloadSingleTrain(zipFile, enableRollback);
    }
  }

  private void sideloadSingleTrain(File zipFile, boolean enableRollback)
      throws DeviceActionException, InterruptedException {
    boolean needActivation = false;
    if (enableRollback) {
      needActivation = device.installZippedTrain(zipFile, ENABLE_ROLLBACK);
    }

    if (needActivation) {
      activateStagedInstall();
    }
  }

  /** Returns if collection {@code packages} contains any .apks file. */
  private boolean containsApks(Collection<AndroidPackage> packages) {
    return packages.stream()
        .anyMatch(ap -> ap.apksFile().filter(f -> f.getName().endsWith(APKS_SUFFIX)).isPresent());
  }

  /** Boots the device to activate the updated apex modules. */
  private void activateStagedInstall() throws DeviceActionException, InterruptedException {
    logger.atInfo().log("Activate the staged installation.");
    Duration extraWaitForStaging = device.extraWaitForStaging();
    if (isPositive(extraWaitForStaging)) {
      sleeper.sleep(extraWaitForStaging);
    }
    device.reboot();
  }
}
