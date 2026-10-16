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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.DynamicConfig;
import com.google.devtools.mobileharness.platform.android.xts.config.DynamicConfigHandler;
import com.google.devtools.mobileharness.platform.android.xts.config.DynamicConfigResolver;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
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
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.xmlpull.v1.XmlPullParserException;

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

  private final LocalFileUtil localFileUtil;
  private final AndroidFileUtil androidFileUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final ApkInstaller apkInstaller;
  private final ResUtil resUtil;
  private final DynamicConfigResolver dynamicConfigResolver;
  private final MapSplitter xtsSuiteInfoSplitter = Splitter.on(",").withKeyValueSeparator("=");

  @Inject
  AndroidAtsDynamicConfigPusherSetupOnlyDecorator(
      Driver decorated,
      TestInfo testInfo,
      LocalFileUtil localFileUtil,
      AndroidFileUtil androidFileUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      ApkInstaller apkInstaller,
      ResUtil resUtil,
      DynamicConfigResolver dynamicConfigResolver) {
    super(decorated, testInfo);
    this.localFileUtil = localFileUtil;
    this.androidFileUtil = androidFileUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.apkInstaller = apkInstaller;
    this.resUtil = resUtil;
    this.dynamicConfigResolver = dynamicConfigResolver;
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

    File hostFile =
        mergeConfigFiles(testInfo, spec, getLocalConfigFile(spec, suiteName), suiteName);
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

  /**
   * Gets the local config file.
   *
   * <p>If {@code extractFromResource} is true, it attempts to extract the dynamic config file from
   * the suite's tradefed JAR.
   *
   * <p>If {@code extractFromResource} is false, it searches for the dynamic config file directly in
   * the XTS test directory.
   */
  private File getLocalConfigFile(
      AndroidAtsDynamicConfigPusherDecoratorSpec spec, @Nullable String suiteName)
      throws MobileHarnessException {
    String lookupName =
        spec.getExtractFromResource()
            ? (spec.getDynamicResourceName().isEmpty()
                ? spec.getConfigFilename()
                : spec.getDynamicResourceName())
            : (spec.getDynamicConfigName().isEmpty()
                ? spec.getConfigFilename()
                : spec.getDynamicConfigName());
    String fileName = String.format("%s.dynamic", lookupName);

    if (spec.getExtractFromResource()) {
      File localConfigFile =
          suiteName == null
              ? null
              : dynamicConfigResolver.extractFromTradefedJar(
                  suiteName, spec.getXtsTestDir(), lookupName);
      if (localConfigFile == null) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_LOCAL_CONFIG_NOT_FOUND,
            String.format("Fail to find '%s' in tradefed jar", fileName));
      }
      return localConfigFile;
    }

    List<File> files = localFileUtil.listFiles(spec.getXtsTestDir(), /* recursively= */ true);
    return files.stream()
        .filter(file -> file.getName().equals(fileName))
        .findFirst()
        .orElseThrow(() ->
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_LOCAL_CONFIG_NOT_FOUND,
                String.format("Config file %s not found.", fileName)));
  }

  /** Merges the local config file with the server config, and removes the extracted local file. */
  private File mergeConfigFiles(
      TestInfo testInfo,
      AndroidAtsDynamicConfigPusherDecoratorSpec spec,
      File localConfigFile,
      @Nullable String suiteName)
      throws MobileHarnessException, InterruptedException {
    try {
      ImmutableMap<String, ImmutableList<String>> apfeConfig = null;
      if (spec.getHasServerSideConfig()) {
        if (suiteName == null) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_INVALID_PARAMETER,
              "suite_name is missing from xtsSuiteInfo");
        }
        String sessionId = testInfo.jobInfo().properties().getOptional(Job.SESSION_ID).orElse("");
        apfeConfig =
            dynamicConfigResolver.fetchServerConfig(
                spec.getConfigUrl(),
                spec.getApiKey(),
                suiteName,
                spec.getConfigFilename(),
                spec.getVersion(),
                sessionId,
                /* timeout= */ null);
      }
      return DynamicConfigHandler.getMergedDynamicConfigFile(
          localConfigFile, apfeConfig, spec.getConfigFilename(), new HashMap<>());
    } catch (IOException | XmlPullParserException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_MERGE_DYNAMIC_CONFIG_ERROR,
          "Failed to merged dynamic config file.",
          e);
    } finally {
      if (spec.getExtractFromResource()) {
        try {
          localFileUtil.removeFileOrDir(localConfigFile.toPath());
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log(
              "Failed to remove extracted config file %s", localConfigFile.getName());
        }
      }
    }
  }
}
