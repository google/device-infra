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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.deviceaction.common.utils.Constants.APEX_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.APKS_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.APK_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.ZIP_SUFFIX;
import static java.util.stream.Stream.concat;

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
import com.google.devtools.deviceaction.common.utils.AaptUtil;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.deviceaction.framework.operations.ModuleCleaner;
import com.google.devtools.deviceaction.framework.operations.ModuleInstaller;
import com.google.devtools.deviceaction.framework.operations.ModulePusher;
import com.google.devtools.deviceaction.framework.proto.action.InstallMainlineSpec;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.packagemanager.ModuleInfo;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** An {@code Action} to install mainline modules. */
@Configurable(specType = InstallMainlineSpec.class)
public class InstallMainline implements Action {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String ACTIVATED_APEX_SOURCEDIR_PREFIX = "data";

  private static final ImmutableSet<String> PACKAGES_WITH_INVALID_DUMP_INFO =
      ImmutableSet.of("com.google.mainline.primary.libs");
  private static final String TAG_MAINLINE_MODULES = "mainline_modules";
  private static final String TAG_TRAIN_FOLDER = "train_folder";
  private static final String TAG_APKS_ZIPS = "apks_zips";

  private final InstallMainlineSpec spec;
  private final AndroidPhone device;
  private final ModuleCleaner moduleCleaner;
  private final ModuleInstaller moduleInstaller;
  private final ModulePusher modulePusher;
  private final AaptUtil aaptUtil;
  private final LocalFileUtil localFileUtil;

  private final ImmutableMultimap<String, File> localFiles;

  /* Keys are package names. */
  private ImmutableMap<String, AndroidPackage> mapToSourcePackages;
  /* Keys are package names. */
  private ImmutableMap<String, AndroidPackage> mapToOnDevicePackages;

  public InstallMainline(
      ModuleCleaner moduleCleaner,
      ModuleInstaller moduleInstaller,
      ModulePusher modulePusher,
      InstallMainlineSpec spec,
      AndroidPhone device,
      AaptUtil aaptUtil,
      LocalFileUtil localFileUtil,
      ImmutableMultimap<String, File> localFiles) {
    this.moduleCleaner = moduleCleaner;
    this.moduleInstaller = moduleInstaller;
    this.modulePusher = modulePusher;
    this.spec = spec;
    this.device = device;
    this.aaptUtil = aaptUtil;
    this.localFileUtil = localFileUtil;
    this.localFiles = localFiles;
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

    setupStatus();
    if (mapToSourcePackages.isEmpty()) {
      logger.atInfo().log("No mainline module to update.");
      return;
    }

    if (needPush()) {
      modulePusher.pushModules(mapToSourcePackages, mapToOnDevicePackages);
      checkStatus(/* checkSourceDir= */ false);
    }

    moduleInstaller.installModules(mapToSourcePackages.values(), enableRollback());
    checkStatus(/* checkSourceDir= */ true);
  }

  private ImmutableList<File> getTrainsInZip() {
    return apksZips().stream()
        .filter(f -> f.getName().endsWith(ZIP_SUFFIX))
        .collect(toImmutableList());
  }

  private void setupStatus() throws DeviceActionException, InterruptedException {
    ImmutableMap<String, PackageInfo> installedPackageMap = getInstalledPackageMap();
    ImmutableSet<String> installedModulePackageNames =
        device.listModules().stream().map(ModuleInfo::packageName).collect(toImmutableSet());

    ImmutableMap.Builder<String, AndroidPackage> sourceBuilder = ImmutableMap.builder();
    ImmutableMap.Builder<String, AndroidPackage> onDeviceBuilder = ImmutableMap.builder();
    ImmutableSet<File> sourceFiles = getPackageFiles();
    logger.atInfo().log("Get all source files %s", sourceFiles);
    validateFiles(sourceFiles);
    for (File sourceFile : sourceFiles) {
      AndroidPackage sourcePackage = getPackageFromFile(sourceFile);
      String packageName = sourcePackage.info().packageName();
      if (installedPackageMap.containsKey(packageName)
          && installedModulePackageNames.contains(packageName)) {
        sourceBuilder.put(packageName, sourcePackage);
        onDeviceBuilder.put(packageName, getPackageOnDevice(installedPackageMap.get(packageName)));
      }
    }
    mapToSourcePackages = sourceBuilder.buildOrThrow();
    mapToOnDevicePackages = onDeviceBuilder.buildOrThrow();
    for (String packageName : mapToSourcePackages.keySet()) {
      logger.atInfo().log(
          "To update the module package from %s to %s",
          mapToOnDevicePackages.get(packageName), mapToSourcePackages.get(packageName));
    }
  }

  private ImmutableSet<File> getPackageFiles() throws DeviceActionException {
    ImmutableSet.Builder<File> builder = ImmutableSet.builder();
    if (localFiles.containsKey(TAG_MAINLINE_MODULES)) {
      builder.addAll(mainlineModules());
    } else {
      Optional<File> trainFolderOp = trainFolder();
      if (trainFolderOp.isPresent()) {
        builder.addAll(getAllFiles(trainFolderOp.get(), APKS_SUFFIX));
      }
    }
    return builder.build();
  }

  private void validateFiles(Collection<File> toValidate) throws DeviceActionException {
    if (allMatchFormats(toValidate, APKS_SUFFIX)) {
      logger.atInfo().log("Test files contain only apks files.");
      return;
    } else if (allMatchFormats(toValidate, APK_SUFFIX, APEX_SUFFIX)) {
      logger.atInfo().log("Test files contain only apk or apex files.");
      return;
    }
    throw new DeviceActionException(
        "INVALID_FILE_FORMAT",
        ErrorType.CUSTOMER_ISSUE,
        "There are mixed formats in the list of mainline packages " + toValidate);
  }

  private AndroidPackage getPackageFromFile(File packageFile)
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

  private AndroidPackage getPackageOnDevice(PackageInfo packageInfo)
      throws DeviceActionException, InterruptedException {
    AndroidPackage.Builder builder = AndroidPackage.builder().setInfo(packageInfo);
    String packageName = packageInfo.packageName();
    ImmutableList<File> files =
        device.getAllInstalledPaths(packageName).stream().map(File::new).collect(toImmutableList());
    builder.addFiles(files).setIsSplit(files.size() > 1);
    return builder.build();
  }

  private List<File> getAllFiles(File dirFile, String suffix) throws DeviceActionException {
    try {
      return localFileUtil.listFiles(
          dirFile.getAbsolutePath(), /* recursively= */ true, f -> f.getName().endsWith(suffix));
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(
          e, "Failed to list files under %s", dirFile.getAbsolutePath());
    }
  }

  private void checkStatus(boolean checkSourceDir)
      throws DeviceActionException, InterruptedException {
    ImmutableMap<String, PackageInfo> onDeviceMap = getInstalledPackageMap();
    for (String packageName : mapToSourcePackages.keySet()) {
      if (PACKAGES_WITH_INVALID_DUMP_INFO.contains(packageName)) {
        continue;
      }
      long expectedVersion = mapToSourcePackages.get(packageName).info().versionCode();
      long actualVersion = onDeviceMap.get(packageName).versionCode();
      logger.atInfo().log(
          "The expected version for %s is %d and the actural is %d",
          packageName, expectedVersion, actualVersion);
      if (expectedVersion != actualVersion) {
        throw new DeviceActionException(
            "UNEXPECTED_BEHAVIOR", ErrorType.INFRA_ISSUE, "Module is not updated!");
      }
      if (checkSourceDir
          && !onDeviceMap
              .get(packageName)
              .sourceDir()
              .startsWith(ACTIVATED_APEX_SOURCEDIR_PREFIX)) {
        throw new DeviceActionException(
            "UNEXPECTED_BEHAVIOR",
            ErrorType.INFRA_ISSUE,
            "Module " + onDeviceMap.get(packageName) + " is not activated!");
      }
    }
  }

  private ImmutableMap<String, PackageInfo> getInstalledPackageMap()
      throws DeviceActionException, InterruptedException {
    return concat(device.listPackages().stream(), device.listApexPackages().stream())
        .collect(toImmutableMap(PackageInfo::packageName, Function.identity()));
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

  private static boolean allMatchFormats(
      Collection<File> files, String suffix, String... suffixes) {
    Predicate<File> predicate = f -> f.getName().endsWith(suffix);
    for (String otherSuffix : suffixes) {
      predicate = predicate.or(f -> f.getName().endsWith(otherSuffix));
    }
    return files.stream().allMatch(predicate);
  }
}
