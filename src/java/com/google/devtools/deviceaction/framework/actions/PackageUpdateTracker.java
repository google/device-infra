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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.deviceaction.common.utils.Constants.APEX_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.APKS_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.APK_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Verify.verify;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNullElse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.AndroidPackage;
import com.google.devtools.deviceaction.common.utils.AaptUtil;
import com.google.devtools.deviceaction.common.utils.Conditions;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.packagemanager.ModuleInfo;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * A class that tracks package update.
 *
 * <p>It gets the package info before installation and checks update after the installation.
 */
class PackageUpdateTracker {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String ACTIVATED_APEX_SOURCE_DIR_PREFIX = "/data";

  private static final ImmutableSet<String> PACKAGES_WITH_INVALID_DUMP_INFO =
      ImmutableSet.of("com.google.mainline.primary.libs");

  private final AndroidPhone device;
  private final AaptUtil aaptUtil;
  private final LocalFileUtil localFileUtil;

  // The set of new packages to install on the device.
  private ImmutableSet<AndroidPackage> sourcePackages = ImmutableSet.of();

  public PackageUpdateTracker(AndroidPhone device, AaptUtil aaptUtil, LocalFileUtil localFileUtil) {
    this.device = device;
    this.aaptUtil = aaptUtil;
    this.localFileUtil = localFileUtil;
  }

  /** Gets all files with suffix in {@code suffixes} in the dir {@code dirFile}. */
  ImmutableSet<File> getAllFilesInDir(File dirFile, Collection<String> suffixes)
      throws DeviceActionException {
    try {
      return ImmutableSet.copyOf(
          localFileUtil.listFiles(
              dirFile.getAbsolutePath(),
              /* recursively= */ true,
              f -> filterBySuffixes(suffixes).test(f)));
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(
          e, "Failed to list files under %s", dirFile.getAbsolutePath());
    }
  }

  /**
   * Gets the map for package update. The packages should be updated from the values (old) to the
   * keys (new).
   *
   * <p>For each package to update, it gets the info {@link AndroidPackage} for installation and the
   * info of the old package on the device. The info for the new package will be saved internally
   * and will be used to check if the update is successful. If there are multiple files
   * corresponding to the same package, they wil be merged into a single {@link AndroidPackage}.
   *
   * @return a map from the new package to install to the old package on device.
   * @throws DeviceActionException if both apks files and (apk/apex) files are provided. A mixture
   *     of different installation files are not supported because they have different installation
   *     methods.
   */
  ImmutableMap<AndroidPackage, AndroidPackage> getPackageUpdateMap(ImmutableSet<File> packageFiles)
      throws DeviceActionException, InterruptedException {
    logger.atInfo().log("Get all source files %s", packageFiles);
    validateFiles(packageFiles);

    ImmutableMap<String, PackageInfo> installedPackageMap = device.getInstalledPackageMap();
    ImmutableSet<String> installedModulePackageNames =
        device.listModules().stream().map(ModuleInfo::packageName).collect(toImmutableSet());
    Predicate<String> isInstalledModule =
        name -> installedPackageMap.containsKey(name) && installedModulePackageNames.contains(name);
    ImmutableMap<String, AndroidPackage> mapToSourcePackages =
        buildSourcePackageMap(packageFiles, isInstalledModule);
    sourcePackages = ImmutableSet.copyOf(mapToSourcePackages.values());

    ImmutableMap.Builder<AndroidPackage, AndroidPackage> builder = ImmutableMap.builder();
    for (AndroidPackage toInstall : sourcePackages) {
      AndroidPackage onDevicePackage =
          getAndroidPackageOnDevice(installedPackageMap.get(toInstall.info().packageName()));
      builder.put(toInstall, onDevicePackage);
      logger.atInfo().log("To update the module package from %s to %s", onDevicePackage, toInstall);
    }
    return builder.buildOrThrow();
  }

  /** Checks if the versions are updated as expected. */
  void checkVersionsUpdated() throws DeviceActionException, InterruptedException {
    checkStatus(/* checkSourceDir= */ false);
  }

  /** Checks if the versions are updated and the packages are loaded to /data partition. */
  void checkVersionsUpdatedAndActivated() throws DeviceActionException, InterruptedException {
    checkStatus(/* checkSourceDir= */ true);
  }

  private ImmutableMap<String, AndroidPackage> buildSourcePackageMap(
      ImmutableSet<File> sourceFiles, Predicate<String> isInstalledModule)
      throws DeviceActionException, InterruptedException {
    // In case of split apk, there might be multiple apk files for the same package name.
    ImmutableListMultimap.Builder<String, AndroidPackage> sourceMultiBuilder =
        ImmutableListMultimap.builder();
    for (File sourceFile : sourceFiles) {
      AndroidPackage sourcePackage = getAndroidPackageFromFile(sourceFile);
      String packageName = sourcePackage.info().packageName();
      if (isInstalledModule.test(packageName)) {
        sourceMultiBuilder.put(packageName, sourcePackage);
      }
    }
    return mergeMultimap(sourceMultiBuilder.build());
  }

  private void checkStatus(boolean checkSourceDir)
      throws DeviceActionException, InterruptedException {
    ImmutableMap<String, PackageInfo> onDeviceMap = device.getInstalledPackageMap();
    for (AndroidPackage androidPackage : sourcePackages) {
      String packageName = androidPackage.info().packageName();
      if (PACKAGES_WITH_INVALID_DUMP_INFO.contains(packageName)) {
        continue;
      }
      long expectedVersion = androidPackage.info().versionCode();
      PackageInfo installed = onDeviceMap.get(packageName);
      verify(installed != null, "Module %s is not installed!", packageName);
      long actualVersion = installed.versionCode();
      logger.atInfo().log(
          "The expected version for %s is %d and the actual is %d",
          packageName, expectedVersion, actualVersion);
      verify(expectedVersion == actualVersion, "Module is not updated!");
      if (checkSourceDir) {
        Conditions.checkState(
            installed.sourceDir().startsWith(ACTIVATED_APEX_SOURCE_DIR_PREFIX),
            ErrorType.INFRA_ISSUE,
            "Module %s is not activated!",
            installed);
      }
    }
  }

  /**
   * Merges all {@link AndroidPackage}s with the same package name into a single {@link
   * AndroidPackage}.
   */
  private static ImmutableMap<String, AndroidPackage> mergeMultimap(
      ImmutableListMultimap<String, AndroidPackage> listMultimap) {
    ImmutableMap.Builder<String, AndroidPackage> builder = ImmutableMap.builder();
    for (String packageName : listMultimap.keySet()) {
      listMultimap.get(packageName).stream()
          .reduce(PackageUpdateTracker::mergePackages)
          .ifPresent(v -> builder.put(packageName, v));
    }
    return builder.buildOrThrow();
  }

  private static AndroidPackage mergePackages(AndroidPackage first, AndroidPackage second) {
    return first.toBuilder().setIsSplit(true).addFiles(second.files()).build();
  }

  private static void validateFiles(Collection<File> toValidate) throws DeviceActionException {
    if (toValidate.isEmpty()) {
      logger.atInfo().log("No test files.");
      return;
    }
    if (allMatchFormats(toValidate, ImmutableList.of(APKS_SUFFIX))) {
      logger.atInfo().log("Test files contain only apks files.");
      return;
    }
    if (allMatchFormats(toValidate, asList(APK_SUFFIX, APEX_SUFFIX))) {
      logger.atInfo().log("Test files contain only apk or apex files.");
      return;
    }
    throw new DeviceActionException(
        "INVALID_FILE_FORMAT",
        ErrorType.CUSTOMER_ISSUE,
        "There are mixed formats in the list of mainline packages " + toValidate);
  }

  private AndroidPackage getAndroidPackageFromFile(File packageFile)
      throws DeviceActionException, InterruptedException {
    AndroidPackage.Builder builder = AndroidPackage.builder();
    File baseFile = packageFile;
    if (packageFile.getName().endsWith(APKS_SUFFIX)) {
      builder.setApksFile(packageFile);
      ImmutableList<File> extracted = device.extractFilesFromApks(packageFile);
      builder.addFiles(extracted);
      builder.setIsSplit(extracted.size() > 1);
      baseFile = extracted.get(0);
    } else {
      builder.addFiles(packageFile);
      builder.setIsSplit(false);
    }
    return builder.setInfo(aaptUtil.getPackageInfo(baseFile)).build();
  }

  private AndroidPackage getAndroidPackageOnDevice(PackageInfo packageInfo)
      throws DeviceActionException, InterruptedException {
    AndroidPackage.Builder builder = AndroidPackage.builder().setInfo(packageInfo);
    String packageName = packageInfo.packageName();
    ImmutableList<File> files =
        device.getAllInstalledPaths(packageName).stream().map(File::new).collect(toImmutableList());
    builder.addFiles(files).setIsSplit(files.size() > 1);
    return builder.build();
  }

  private static boolean allMatchFormats(Collection<File> files, Collection<String> suffixes) {
    Predicate<File> predicate = filterBySuffixes(suffixes);
    return files.stream().allMatch(predicate);
  }

  private static Predicate<File> filterBySuffixes(Collection<String> suffixes) {
    Predicate<File> predicate = null;
    for (String suffix : suffixes) {
      if (predicate == null) {
        predicate = f -> f.getName().endsWith(suffix);
      } else {
        predicate = predicate.or(f -> f.getName().endsWith(suffix));
      }
    }
    return requireNonNullElse(predicate, f -> true);
  }
}
