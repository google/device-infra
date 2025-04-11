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

package com.google.devtools.mobileharness.platform.android.app.mtaastools;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Instantiator for installing and using mtaas tools app.
 *
 * <p>This class provides a centralized way to install the mtaas tools app, ensuring that the app is
 * installed correctly and that the necessary permissions are granted. It also provides a convenient
 * way to check if a device is a member of the checkin group.
 */
public class MtaasToolsInstantiator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  static final String MTAAS_TOOLS_APK_RES_PATH = "/com/google/android/apps/mtaas/tools/tools.apk";
  static final String MTAAS_TOOLS_PACKAGE_NAME = "com.google.android.apps.mtaas.tools";
  static final String MTAAS_GSERVICES_FLAG_NAME = "mtaas.device";

  private static final Supplier<Optional<String>> MTAAS_TOOLS_APK_PATH =
      Suppliers.memoize(MtaasToolsInstantiator::getMtaasToolsApkPath);

  private final AndroidInstrumentationUtil androidInstrumentationUtil;
  private final ApkInstaller apkInstaller;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;

  public MtaasToolsInstantiator() {
    this(new AndroidInstrumentationUtil(), new ApkInstaller(), new AndroidSystemSettingUtil());
  }

  @VisibleForTesting
  MtaasToolsInstantiator(
      AndroidInstrumentationUtil androidInstrumentationUtil,
      ApkInstaller apkInstaller,
      AndroidSystemSettingUtil androidSystemSettingUtil) {
    this.androidInstrumentationUtil = androidInstrumentationUtil;
    this.apkInstaller = apkInstaller;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
  }

  /**
   * Installs the mtaas tools app on the device.
   *
   * @param device the device to install the app on
   * @throws MobileHarnessException if failed to install the app
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public void install(Device device) throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Installing mtaas tools on device %s", device.getDeviceId());
    String apkPath =
        MTAAS_TOOLS_APK_PATH
            .get()
            .orElseThrow(
                () ->
                    new MobileHarnessException(
                        AndroidErrorId.ANDROID_MTAAS_TOOLS_APK_NOT_FOUND,
                        "Failed to get mtaas tools apk path"));
    apkInstaller.installApkIfNotExist(
        device,
        ApkInstallArgs.builder().setApkPath(apkPath).setGrantPermissions(true).build(),
        null);
  }

  /**
   * Checks if the device is a member of the checkin group.
   *
   * @param device the device to check
   * @return true if the device is a member of the checkin group, false otherwise
   * @throws MobileHarnessException if failed to check the checkin group
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public boolean isMemberOfCheckinGroup(Device device)
      throws MobileHarnessException, InterruptedException {
    int sdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(device.getDeviceId());
    String instrumentationOutput =
        androidInstrumentationUtil.instrument(
            device.getDeviceId(),
            sdkVersion,
            AndroidInstrumentationSetting.create(
                MTAAS_TOOLS_PACKAGE_NAME,
                ".instrumentations.GetGservicesFlag",
                /* className= */ null,
                ImmutableMap.of("flag", MTAAS_GSERVICES_FLAG_NAME, "defaultValue", "false"),
                /* async= */ false,
                /* showRawResults= */ false,
                /* prefixAndroidTest= */ false,
                /* noIsolatedStorage= */ false,
                /* useTestStorageService= */ false,
                /* enableCoverage= */ false),
            /* timeout= */ Duration.ofMinutes(1));
    return instrumentationOutput.contains(MTAAS_GSERVICES_FLAG_NAME + "=true");
  }

  private static Optional<String> getMtaasToolsApkPath() {
    ResUtil resUtil = new ResUtil();
    return resUtil
        .getExternalResourceFile(MTAAS_TOOLS_APK_RES_PATH)
        .or(
            () -> {
              try {
                return Optional.of(
                    resUtil.getResourceFile(
                        MtaasToolsInstantiator.class, MTAAS_TOOLS_APK_RES_PATH));
              } catch (MobileHarnessException e) {
                logger.atWarning().withCause(e).log("Failed to get mtaas tools apk path");
                return Optional.empty();
              }
            });
  }
}
