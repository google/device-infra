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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.deviceaction.common.utils.Constants.APEX_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.APKS_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.APK_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.ZIP_SUFFIX;
import static java.util.Arrays.asList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.annotations.Annotations.Configurable;
import com.google.devtools.deviceaction.common.annotations.Annotations.FilePath;
import com.google.devtools.deviceaction.common.annotations.Annotations.SpecValue;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.AndroidPackage;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.deviceaction.framework.operations.ModuleCleaner;
import com.google.devtools.deviceaction.framework.operations.ModuleInstaller;
import com.google.devtools.deviceaction.framework.operations.ModulePusher;
import com.google.devtools.deviceaction.framework.proto.action.InstallMainlineSpec;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.io.File;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** An {@link Action} to install mainline modules. */
@Configurable(specType = InstallMainlineSpec.class)
public class InstallMainline implements Action {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TAG_MAINLINE_MODULES = "mainline_modules";
  private static final String TAG_TRAIN_FOLDER = "train_folder";
  private static final String TAG_APKS_ZIPS = "apks_zips";
  // Package Watchdog may roll back the mainline modules if issues are observed.
  // In particular, it will look for native crashes in the first 5 min after the device boots.
  private static final Duration WAIT_FOR_POSSIBLE_ROLLBACK = Duration.ofMinutes(5);

  private final PackageUpdateTracker packageUpdateTracker;
  private final ModuleCleaner moduleCleaner;
  private final ModuleInstaller moduleInstaller;
  private final ModulePusher modulePusher;
  private final AndroidPhone device;
  private final InstallMainlineSpec spec;
  private final ImmutableMultimap<String, File> localFiles;

  private final Sleeper sleeper;

  public InstallMainline(
      PackageUpdateTracker packageUpdateTracker,
      ModuleCleaner moduleCleaner,
      ModuleInstaller moduleInstaller,
      ModulePusher modulePusher,
      InstallMainlineSpec spec,
      AndroidPhone device,
      ImmutableMultimap<String, File> localFiles,
      Sleeper sleeper) {
    this.packageUpdateTracker = packageUpdateTracker;
    this.moduleCleaner = moduleCleaner;
    this.moduleInstaller = moduleInstaller;
    this.modulePusher = modulePusher;
    this.spec = spec;
    this.device = device;
    this.localFiles = localFiles;
    this.sleeper = sleeper;
  }

  /**
   * Installs mainline packages to the device.
   *
   * <p>There are three ways to provide install packages. Use tag mainline_modules for individual
   * apk or apex packages. Use tag train_folder for a folder containing multiple packages. Use tag
   * apks_zips for train zips.
   */
  @Override
  public void perform() throws DeviceActionException, InterruptedException {
    final int sdkVersion = device.getSdkVersion();
    if (sdkVersion < AndroidVersion.ANDROID_10.getStartSdkVersion() /* API=29*/) {
      logger.atInfo().log(
          "The sdk version %d of the devices is below Q. Not mainline modules to update.",
          sdkVersion);
      return;
    }

    if (cleanUpSessions()) {
      moduleCleaner.cleanUpSessions();
    }

    ImmutableList<File> trainsInZip = getTrainsInZip();
    if (!trainsInZip.isEmpty()) {
      logger.atInfo().log(
          "Sideload trains in zip files. Skip the analysis of each individual package and assume"
              + " the train is signed by release key.");
      moduleInstaller.sideloadTrains(trainsInZip, enableRollback());
      return;
    }

    ImmutableMap<AndroidPackage, AndroidPackage> toInstall =
        packageUpdateTracker.getPackageUpdateMap(getPackageFiles());
    if (toInstall.isEmpty()) {
      logger.atInfo().log("No mainline module to update.");
      return;
    }

    if (needPush()) {
      modulePusher.pushModules(toInstall, softRebootAfterPush());
      if (!skipCheckVersionAfterPush() && !softRebootAfterPush()) {
        packageUpdateTracker.checkVersionsUpdated();
      }
    }

    moduleInstaller.installModules(toInstall.keySet(), enableRollback());
    packageUpdateTracker.checkVersionsUpdatedAndActivated();

    if (enableRollback() && checkRollback()) {
      logger.atInfo().log(
          "Wait for %d min to make sure the modules are not rolled back by package watchdog.",
          WAIT_FOR_POSSIBLE_ROLLBACK.toMinutes());
      sleeper.sleep(WAIT_FOR_POSSIBLE_ROLLBACK);
      try {
        packageUpdateTracker.checkVersionsUpdated();
      } catch (DeviceActionException e) {
        if (Objects.equals(e.getErrorId().type(), ErrorType.INFRA_ISSUE)) {
          logger.atSevere().withCause(e).log(
              "The version got rolled back within %d min. Please check device logcat for the"
                  + " rollback cause.",
              WAIT_FOR_POSSIBLE_ROLLBACK.toMinutes());
        }
        throw e;
      }
    }
    logger.atInfo().log("All packages have been updated successfully!");
  }

  private ImmutableList<File> getTrainsInZip() {
    return apksZips().stream()
        .filter(f -> f.getName().endsWith(ZIP_SUFFIX))
        .collect(toImmutableList());
  }

  private ImmutableSet<File> getPackageFiles() throws DeviceActionException {
    if (localFiles.containsKey(TAG_MAINLINE_MODULES)) {
      return ImmutableSet.copyOf(mainlineModules());
    } else {
      Optional<File> trainFolderOp = trainFolder();
      if (trainFolderOp.isPresent()) {
        return packageUpdateTracker.getAllFilesInDir(
            trainFolderOp.get(), asList(APKS_SUFFIX, APEX_SUFFIX, APK_SUFFIX));
      }
    }
    return ImmutableSet.of();
  }

  @VisibleForTesting
  boolean needPush() throws DeviceActionException {
    // Need to push if the signs conflict.
    return device.devKeySigned() ^ devKeySigned();
  }

  @FilePath(tag = TAG_MAINLINE_MODULES)
  private ImmutableList<File> mainlineModules() {
    return localFiles.get(TAG_MAINLINE_MODULES).asList();
  }

  @FilePath(tag = TAG_TRAIN_FOLDER)
  private Optional<File> trainFolder() {
    return localFiles.get(TAG_TRAIN_FOLDER).stream().filter(File::isDirectory).findAny();
  }

  @FilePath(tag = TAG_APKS_ZIPS)
  private ImmutableList<File> apksZips() {
    return localFiles.get(TAG_APKS_ZIPS).asList();
  }

  @SpecValue(field = "enable_rollback")
  private boolean enableRollback() {
    return spec.getEnableRollback();
  }

  @SpecValue(field = "clean_up_sessions")
  boolean cleanUpSessions() {
    return spec.getCleanUpSessions();
  }

  @SpecValue(field = "dev_key_signed")
  private boolean devKeySigned() {
    return spec.getDevKeySigned();
  }

  @SpecValue(field = "skip_check_version_after_push")
  private boolean skipCheckVersionAfterPush() {
    return spec.getSkipCheckVersionAfterPush();
  }

  @SpecValue(field = "check_rollback")
  private boolean checkRollback() {
    return spec.getCheckRollback();
  }

  @SpecValue(field = "soft_reboot_after_push")
  private boolean softRebootAfterPush() {
    return spec.getSoftRebootAfterPush();
  }
}
