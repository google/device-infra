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

import static com.google.devtools.deviceaction.common.utils.Conditions.checkArgument;
import static com.google.devtools.deviceaction.common.utils.Constants.APEX_SUFFIX;
import static com.google.devtools.deviceaction.common.utils.Constants.APK_SUFFIX;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toJavaDuration;
import static java.util.Arrays.asList;

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
import com.google.devtools.deviceaction.common.utils.Conditions;
import com.google.devtools.deviceaction.framework.devices.AndroidPhone;
import com.google.devtools.deviceaction.framework.operations.ImageZipFlasher;
import com.google.devtools.deviceaction.framework.operations.ModulePusher;
import com.google.devtools.deviceaction.framework.operations.OtaSideloader;
import com.google.devtools.deviceaction.framework.proto.action.ResetOption;
import com.google.devtools.deviceaction.framework.proto.action.ResetSpec;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import java.io.File;
import java.time.Duration;
import java.util.Optional;

/**
 * An {@link Action} to reset a device.
 *
 * <p>It applies different reset options to different device builds. The params are specified by
 * {@link ResetSpec}.
 */
@Configurable(specType = ResetSpec.class)
public class Reset implements Action {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TAG_RECOVERY_MODULES = "recovery_modules";

  private static final String TAG_OTA_PACKAGE = "ota_package";

  private static final String TAG_IMAGE_ZIP = "image_zip";

  private final PackageUpdateTracker packageUpdateTracker;

  private final ModulePusher modulePusher;

  private final OtaSideloader otaSideloader;

  private final ImageZipFlasher imageZipFlasher;

  private final AndroidPhone device;

  private final ResetSpec spec;

  private final ImmutableMultimap<String, File> localFiles;

  Reset(
      PackageUpdateTracker packageUpdateTracker,
      ModulePusher modulePusher,
      OtaSideloader otaSideloader,
      ImageZipFlasher imageZipFlasher,
      ResetSpec spec,
      AndroidPhone device,
      ImmutableMultimap<String, File> localFiles) {
    this.packageUpdateTracker = packageUpdateTracker;
    this.modulePusher = modulePusher;
    this.otaSideloader = otaSideloader;
    this.imageZipFlasher = imageZipFlasher;
    this.spec = spec;
    this.device = device;
    this.localFiles = localFiles;
  }

  /** Performs the reset to the device. */
  @Override
  public void perform() throws DeviceActionException, InterruptedException {
    logger.atInfo().log("Start to reset the device %s with spec:\n%s", device.getUuid(), spec);
    switch (resetOption()) {
      case TEST_HARNESS:
        device.enableTestharness();
        break;
      case OTA_SIDELOAD:
        checkArgument(
            otaPackage().isPresent(), ErrorType.CUSTOMER_ISSUE, "OTA package not provided.");
        otaSideloader.sideload(otaPackage().get(), sideloadTimeout(), useAutoReboot());
        break;
      case FASTBOOT_WITH_PARTITION_IMAGES:
        checkArgument(
            imageZip().isPresent(), ErrorType.CUSTOMER_ISSUE, "Image zip file is not provided.");
        imageZipFlasher.flashDevice(imageZip().get(), flashScript(), flashTimeout());
        break;
      default:
        throw new DeviceActionException(
            "NOT_SUPPORTED", ErrorType.CUSTOMER_ISSUE, "The reset option is not supported yet.");
    }

    if (needPreloadModulesRecovery()) {
      final int sdkVersion = device.getSdkVersion();
      Conditions.checkState(
          sdkVersion >= AndroidVersion.ANDROID_10.getStartSdkVersion(),
          ErrorType.CUSTOMER_ISSUE,
          "No mainline modules for sdk < 29. So no module recovery.");
      ImmutableMap<AndroidPackage, AndroidPackage> toInstall =
          packageUpdateTracker.getPackageUpdateMap(getAllModuleFiles());
      modulePusher.pushModules(toInstall);
      packageUpdateTracker.checkVersionsUpdated();
    }

    device.enableTestharness();
    logger.atInfo().log("The device %s is reset successfully!", device.getUuid());
  }

  private ImmutableSet<File> getAllModuleFiles() throws DeviceActionException {
    Optional<File> dirOp = recoveryModules();
    if (dirOp.isPresent()) {
      return packageUpdateTracker.getAllFilesInDir(dirOp.get(), asList(APK_SUFFIX, APEX_SUFFIX));
    }
    return ImmutableSet.of();
  }

  @FilePath(tag = TAG_RECOVERY_MODULES)
  private Optional<File> recoveryModules() {
    return localFiles.get(TAG_RECOVERY_MODULES).stream().filter(File::isDirectory).findAny();
  }

  @FilePath(tag = TAG_OTA_PACKAGE)
  private Optional<File> otaPackage() {
    return localFiles.get(TAG_OTA_PACKAGE).stream().filter(File::isFile).findAny();
  }

  @FilePath(tag = TAG_IMAGE_ZIP)
  private Optional<File> imageZip() {
    return localFiles.get(TAG_IMAGE_ZIP).stream().filter(File::isFile).findAny();
  }

  @SpecValue(field = "reset_option")
  private ResetOption resetOption() {
    return spec.getResetOption();
  }

  @SpecValue(field = "need_preload_modules_recovery")
  private boolean needPreloadModulesRecovery() {
    return spec.getNeedPreloadModulesRecovery();
  }

  @SpecValue(field = "flash_script")
  private String flashScript() {
    return spec.getFlashScript();
  }

  @SpecValue(field = "flash_timeout")
  public Duration flashTimeout() {
    return toJavaDuration(spec.getFlashTimeout());
  }

  @SpecValue(field = "sideload_timeout")
  public Duration sideloadTimeout() {
    return toJavaDuration(spec.getSideloadTimeout());
  }

  @SpecValue(field = "use_auto_reboot")
  public boolean useAutoReboot() {
    return spec.getUseAutoReboot();
  }
}
