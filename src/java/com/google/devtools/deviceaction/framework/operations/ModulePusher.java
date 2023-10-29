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

import static com.google.devtools.deviceaction.common.utils.Constants.APEX_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.CAPEX_SUFFIX;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.AndroidPackage;
import com.google.devtools.deviceaction.common.utils.Conditions;
import com.google.devtools.deviceaction.common.utils.LazyCached;
import com.google.devtools.deviceaction.common.utils.ResourceHelper;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.deviceaction.framework.proto.ResourcePath;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;

/** An {@link Operation} to push mainline modules to the device. */
public class ModulePusher implements Operation {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /* Temporary folder to store renamed packages. */
  private static final String RENAME_DIR_PREFIX = "renamed";
  /* System apex folder on device. */
  private static final String SYSTEM_APEX_DIR = "/system/apex/";
  /* System partition on device. */
  private static final String SYSTEM_DIR = "/system/";
  /* Package cache folder on device. */
  private static final String PACKAGE_CACHE = "/data/system/package_cache/";

  private final AndroidPhone device;
  private final LocalFileUtil fileUtil;

  /* Helper to provide local resources. */
  private final ResourceHelper resourceHelper;

  private final LazyCached<ImmutableMap<String, Path>> systemApexPathsProvider =
      new LazyCached<ImmutableMap<String, Path>>() {
        @Override
        protected ImmutableMap<String, Path> provide()
            throws DeviceActionException, InterruptedException {
          return getApexPathsUnderSystem();
        }
      };

  public ModulePusher(AndroidPhone device, LocalFileUtil fileUtil, ResourceHelper resourceHelper) {
    this.device = device;
    this.fileUtil = fileUtil;
    this.resourceHelper = resourceHelper;
  }

  /**
   * Updates the mainline modules by pushing them to the device.
   *
   * @param packageMap a map of packages to update. The key is the package to push and the value is
   *     the package installed.
   */
  public void pushModules(Map<AndroidPackage, AndroidPackage> packageMap)
      throws DeviceActionException, InterruptedException {
    pushModules(packageMap, /* softReboot= */ false);
  }

  /**
   * Updates the mainline modules by pushing them to the device.
   *
   * @param packageMap a map of packages to update. The key is the package to push and the value is
   *     the package installed.
   * @param softReboot do soft reboot after pushing modules.
   */
  public void pushModules(Map<AndroidPackage, AndroidPackage> packageMap, boolean softReboot)
      throws DeviceActionException, InterruptedException {
    Conditions.checkState(
        device.isUserdebug(), ErrorType.CUSTOMER_ISSUE, "The device should be of userdebug type.");
    setupDevice();

    Path tmpDirOnHost = createRenameDir(resourceHelper.getTmpFileDir());
    for (AndroidPackage source : packageMap.keySet()) {
      String packageName = source.info().packageName();
      PackageInfo pushedInfo =
          pushModule(
              source,
              packageMap.get(source),
              tmpDirOnHost.resolve(packageName)); // Each package has its own tmp dir.
      logger.atInfo().log("Pushed package %s.", pushedInfo);
    }

    if (softReboot) {
      device.softReboot();
    } else {
      activatePushdedModules();
    }
  }

  /** Prepares the device for file overwriting. */
  @VisibleForTesting
  void setupDevice() throws DeviceActionException, InterruptedException {
    device.becomeRoot();
    device.disableVerity();
    device.reboot();
    // Remount the file systems.
    device.remount();
    device.reboot();
    // Remount the file systems again.
    device.remount();
  }

  /** Pushes the {@code sourcePackage} to overwrite the original package on device. */
  @VisibleForTesting
  PackageInfo pushModule(AndroidPackage sourcePackage, AndroidPackage onDevice, Path tmpDirOnHost)
      throws DeviceActionException, InterruptedException {
    logger.atInfo().log("To Update module from %s to %s", onDevice, sourcePackage);
    ImmutableList<File> moduleFiles = sourcePackage.files();
    ResourcePath targetOnDevice = getTargetOnDevice(onDevice);
    // If the target is dir, then use the parent dir to overwrite
    Path source =
        targetOnDevice.getIsDirectory()
            ? moduleFiles.get(0).toPath().getParent()
            : moduleFiles.get(0).toPath();
    Path targetOnHost =
        prepareTargetOnHost(source, tmpDirOnHost, Path.of(targetOnDevice.getPath()).getFileName());
    try {
      fileUtil.touchFileOrDir(targetOnHost, /* ifCreateNewFile= */ false);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(
          e, "Failed to update the modified time of source file %s.", targetOnHost);
    }
    device.push(targetOnHost, Path.of(targetOnDevice.getPath()));
    return sourcePackage.info();
  }

  /** Activates the pushed modules. */
  private void activatePushdedModules() throws DeviceActionException, InterruptedException {
    if (device.needDisablePackageCache()) {
      device.removeFiles(PACKAGE_CACHE);
    }
    if (device.reloadByFactoryReset()) {
      device.enableTestharness();
    } else {
      device.reboot();
    }
  }

  /** Gets the file info of the package installed on the device. */
  @VisibleForTesting
  ResourcePath getTargetOnDevice(AndroidPackage onDevice)
      throws DeviceActionException, InterruptedException {
    Conditions.checkArgument(
        !onDevice.files().isEmpty(),
        ErrorType.INFRA_ISSUE,
        "The package %s doesn't have files!",
        onDevice);
    String packageName = onDevice.info().packageName();
    if (onDevice.info().isApex()) {
      if (onDevice.files().size() == 1
          && onDevice.files().get(0).getAbsolutePath().startsWith(SYSTEM_DIR)) {
        return ResourcePath.newBuilder()
            .setPath(onDevice.files().get(0).getAbsolutePath())
            .setIsDirectory(false)
            .build();
      }
      // The Q and R build may not show apex path.
      // But we know the system apex path is always /system/apex/<package name>.apex
      if (device.getSdkVersion() < AndroidVersion.ANDROID_12.getStartSdkVersion()) {
        return ResourcePath.newBuilder()
            .setPath(Paths.get(SYSTEM_APEX_DIR, getDefaultFileNameForApex(packageName)).toString())
            .setIsDirectory(false)
            .build();
      }
      // If the sourceDir is not under /system/, find the corresponding apex file under
      // /system/apex/.
      ImmutableMap<String, Path> systemApexPaths = systemApexPathsProvider.call();
      if (systemApexPaths.containsKey(onDevice.info().packageName())) {
        return ResourcePath.newBuilder()
            .setPath(requireNonNull(systemApexPaths.get(onDevice.info().packageName())).toString())
            .setIsDirectory(false)
            .build();
      }
      throw new DeviceActionException(
          "SYSTEM_APEX_NOT_FOUND",
          ErrorType.DEPENDENCY_ISSUE,
          "Not find the system package for " + onDevice);
    }
    if (onDevice.isSplit()) {
      Path parent =
          Optional.ofNullable(device.moduleDirOnDevice().get(packageName))
              .map(Path::of)
              .orElse(onDevice.files().get(0).toPath().getParent());
      device.removeFiles(parent.toString());
      return ResourcePath.newBuilder().setPath(parent.toString()).setIsDirectory(true).build();
    }
    return ResourcePath.newBuilder()
        .setPath(onDevice.files().get(0).getAbsolutePath())
        .setIsDirectory(false)
        .build();
  }

  private ImmutableMap<String, Path> getApexPathsUnderSystem()
      throws DeviceActionException, InterruptedException {
    ImmutableMap.Builder<String, Path> builder = ImmutableMap.builder();
    SortedSet<String> apexFiles = device.listFiles(SYSTEM_APEX_DIR);
    logger.atInfo().log("List apex under %s: %s.", SYSTEM_APEX_DIR, apexFiles);
    for (String fileName : apexFiles) {
      int endIndex = -1;
      // The package name is contained as a prefix of the file name.
      if (fileName.contains("_")) {
        endIndex = fileName.indexOf('_');
      } else if (fileName.contains(APEX_SUFFIX)) {
        endIndex = fileName.indexOf(APEX_SUFFIX);
      } else if (fileName.contains(CAPEX_SUFFIX)) {
        endIndex = fileName.indexOf(CAPEX_SUFFIX);
      }
      if (endIndex > 0) {
        builder.put(fileName.substring(0, endIndex), Paths.get(SYSTEM_APEX_DIR, fileName));
      } else {
        logger.atWarning().log("Got unexpected filename %s under %s", fileName, SYSTEM_APEX_DIR);
      }
    }
    return builder.buildOrThrow();
  }

  private static String getDefaultFileNameForApex(String packageName) {
    return String.format("%s.apex", packageName);
  }

  private static Path createRenameDir(Path parentDir) throws DeviceActionException {
    try {
      return Files.createTempDirectory(parentDir, RENAME_DIR_PREFIX);
    } catch (IOException e) {
      throw new DeviceActionException(
          "TEMP_DIR_CREATION_ERROR",
          ErrorType.INFRA_ISSUE,
          "Failed to create temp dir under " + parentDir,
          e);
    }
  }

  @VisibleForTesting
  Path prepareTargetOnHost(Path source, Path targetDir, Path targetFileName)
      throws InterruptedException, DeviceActionException {
    try {
      fileUtil.prepareDir(targetDir);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to create target dir %s", targetDir);
    }
    Path copiedFile = copyFileToDir(source, targetDir);
    if (targetFileName.equals(copiedFile.getFileName())) {
      logger.atInfo().log("The file names are the same. Skip renaming.");
      return copiedFile;
    }
    try {
      return Files.move(copiedFile, copiedFile.resolveSibling(targetFileName));
    } catch (IOException e) {
      throw new DeviceActionException(
          "FILE_RENAME_ERROR",
          ErrorType.INFRA_ISSUE,
          String.format("Failed to rename %s to %s", copiedFile, targetFileName),
          e);
    }
  }

  private Path copyFileToDir(Path source, Path targetDir)
      throws InterruptedException, DeviceActionException {
    try {
      fileUtil.copyFileOrDir(source, targetDir);
      Path copiedPath = targetDir.resolve(source.getFileName());
      logger.atInfo().log("Get %s", copiedPath);
      return copiedPath;
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(
          e, String.format("Failed to copy %s to %s.", source, targetDir));
    }
  }
}
