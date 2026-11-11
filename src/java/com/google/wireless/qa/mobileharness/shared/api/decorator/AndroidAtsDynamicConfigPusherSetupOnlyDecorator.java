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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.base.Splitter.MapSplitter;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.DynamicConfig;
import com.google.devtools.mobileharness.platform.android.xts.config.DynamicConfigResolver;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.SetupOnlyDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.util.PhaseSkippableDecoratorUtil;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec.TestTarget;
import com.google.wireless.qa.mobileharness.shared.proto.state.decorator.AndroidAtsDynamicConfigPusherDecoratorState;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

/**
 * Setup-only decorator of {@link AndroidAtsDynamicConfigPusherDecorator}. See {@link
 * AndroidAtsDynamicConfigPusherDecorator} for more details.
 */
@DecoratorAnnotation(help = "Decorator to push dynamic config files from config repository.")
public class AndroidAtsDynamicConfigPusherSetupOnlyDecorator extends SetupOnlyDecorator
    implements SpecConfigable<AndroidAtsDynamicConfigPusherDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String MANAGED_FILE_CONTENT_PROVIDER_APK_RES_PATH =
      "/com/google/devtools/mobileharness/platform/android/app/binary/contentprovider/ManagedFileContentProvider.apk";

  private final DynamicConfigResolver dynamicConfigResolver;
  private final AndroidFileUtil androidFileUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final ApkInstaller apkInstaller;
  private final ResUtil resUtil;
  private final MapSplitter xtsSuiteInfoSplitter = Splitter.on(",").withKeyValueSeparator("=");

  @Inject
  AndroidAtsDynamicConfigPusherSetupOnlyDecorator(
      Driver decorated,
      TestInfo testInfo,
      DynamicConfigResolver dynamicConfigResolver,
      AndroidFileUtil androidFileUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      ApkInstaller apkInstaller,
      ResUtil resUtil) {
    super(decorated, testInfo);
    this.dynamicConfigResolver = dynamicConfigResolver;
    this.androidFileUtil = androidFileUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.apkInstaller = apkInstaller;
    this.resUtil = resUtil;
  }

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();
    AndroidAtsDynamicConfigPusherDecoratorSpec spec =
        testInfo.jobInfo().combinedSpec(this, deviceId);

    Map<String, String> xtsSuiteInfoMap =
        spec.getXtsSuiteInfo().isEmpty()
            ? new HashMap<>()
            : xtsSuiteInfoSplitter.split(spec.getXtsSuiteInfo());
    String suiteName = xtsSuiteInfoMap.get("suite_name");
    String suiteVersion = xtsSuiteInfoMap.getOrDefault("suite_version", "");
    if (spec.getConfigFilename().isEmpty()) {
      if (suiteName == null) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_INVALID_PARAMETER,
            "suite_name is missing from xtsSuiteInfo");
      }
      spec = spec.toBuilder().setConfigFilename(Ascii.toLowerCase(suiteName)).build();
    }
    if (spec.getVersion().isEmpty()) {
      spec = spec.toBuilder().setVersion(suiteVersion).build();
    }

    File hostFile = dynamicConfigResolver.resolveFile(spec, suiteName);
    AndroidAtsDynamicConfigPusherDecoratorState.Builder stateBuilder =
        AndroidAtsDynamicConfigPusherDecoratorState.newBuilder();
    if (spec.getTarget().equals(TestTarget.DEVICE)) {
      String deviceDest =
          String.format(
              "%s%s.dynamic", DynamicConfig.CONFIG_FOLDER_ON_DEVICE, spec.getConfigFilename());
      androidFileUtil.push(
          deviceId,
          androidSystemSettingUtil.getDeviceSdkVersion(deviceId),
          hostFile.getAbsolutePath(),
          deviceDest);
      logger.atInfo().log("Pushed dynamic config file %s to device %s", deviceDest, deviceId);
      stateBuilder.setDeviceFilePushedPath(deviceDest);
      PhaseSkippableDecoratorUtil.setState(testInfo, deviceId, stateBuilder.build());
    }
    String apkPath =
        resUtil.getResourceFile(
            AndroidAtsDynamicConfigPusherSetupOnlyDecorator.class,
            MANAGED_FILE_CONTENT_PROVIDER_APK_RES_PATH);
    String contentProvider =
        apkInstaller.installApkIfNotExist(
            getDevice(), ApkInstallArgs.builder().addApkPaths(apkPath).build(), getTest().log());

    stateBuilder.setContentProvider(contentProvider);
    PhaseSkippableDecoratorUtil.setState(testInfo, deviceId, stateBuilder.build());

    return SetupResult.continueDecorated();
  }
}
