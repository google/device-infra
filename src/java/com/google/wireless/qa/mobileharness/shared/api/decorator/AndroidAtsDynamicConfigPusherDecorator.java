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

import static java.nio.charset.StandardCharsets.UTF_8;

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
import com.google.devtools.mobileharness.platform.android.xts.config.DynamicConfigHandler;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.gson.JsonSyntaxException;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec.TestTarget;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Decorator to push dynamic config files from config repository. Partially branched from {@code
 * com.android.compatibility.common.tradefed.targetprep.DynamicConfigPusher} from Android codebase.
 */
@DecoratorAnnotation(help = "Decorator to push dynamic config files from config repository.")
public class AndroidAtsDynamicConfigPusherDecorator extends BaseDecorator
    implements SpecConfigable<AndroidAtsDynamicConfigPusherDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String MANAGED_FILE_CONTENT_PROVIDER_APK_RES_PATH =
      "/com/google/devtools/mobileharness/platform/android/app/binary/contentprovider/ManagedFileContentProvider.apk";

  private final LocalFileUtil localFileUtil;
  private final AndroidFileUtil androidFileUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final ApkInstaller apkInstaller;
  private final ResUtil resUtil;
  private final MapSplitter xtsSuiteInfoSplitter = Splitter.on(",").withKeyValueSeparator("=");

  private Map<String, String> xtsSuiteInfoMap;
  private String deviceFilePushedPath;

  @Inject
  AndroidAtsDynamicConfigPusherDecorator(
      Driver decorated,
      TestInfo testInfo,
      LocalFileUtil localFileUtil,
      AndroidFileUtil androidFileUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      ApkInstaller apkInstaller,
      ResUtil resUtil) {
    super(decorated, testInfo);
    this.localFileUtil = localFileUtil;
    this.androidFileUtil = androidFileUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.apkInstaller = apkInstaller;
    this.resUtil = resUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    AndroidAtsDynamicConfigPusherDecoratorSpec spec =
        testInfo.jobInfo().combinedSpec(this, deviceId);

    xtsSuiteInfoMap = xtsSuiteInfoSplitter.split(spec.getXtsSuiteInfo());
    String suiteName = xtsSuiteInfoMap.get("suite_name");
    String suiteVersion = xtsSuiteInfoMap.getOrDefault("suite_version", "");
    if (spec.getConfigFilename().isEmpty()) {
      spec = spec.toBuilder().setConfigFilename(Ascii.toLowerCase(suiteName)).build();
    }
    if (spec.getVersion().isEmpty()) {
      spec = spec.toBuilder().setVersion(suiteVersion).build();
    }

    File localConfigFile = getLocalConfigFile(spec);
    String apfeConfig = fetchApfeConfig(spec);
    File hostFile = mergeConfigFiles(spec, localConfigFile, apfeConfig);
    if (spec.getTarget().equals(TestTarget.DEVICE)) {
      String deviceDest =
          String.format(
              "%s%s.dynamic", DynamicConfig.CONFIG_FOLDER_ON_DEVICE, spec.getConfigFilename());
      androidFileUtil.push(
          deviceId,
          androidSystemSettingUtil.getDeviceSdkVersion(deviceId),
          hostFile.getAbsolutePath(),
          deviceDest);
      deviceFilePushedPath = deviceDest;
      logger.atInfo().log("Pushed dynamic config file %s to device %s", deviceDest, deviceId);
    }
    String apkPath =
        resUtil.getResourceFile(
            AndroidAtsDynamicConfigPusherDecorator.class,
            MANAGED_FILE_CONTENT_PROVIDER_APK_RES_PATH);
    String contentProvider =
        apkInstaller.installApkIfNotExist(
            getDevice(), ApkInstallArgs.builder().setApkPath(apkPath).build(), getTest().log());

    // TODO: Add host target support and config read support.
    try {
      getDecorated().run(testInfo);
    } finally {
      if (deviceFilePushedPath != null && spec.getCleanup()) {
        try {
          androidFileUtil.removeFiles(deviceId, deviceFilePushedPath);
          logger.atInfo().log(
              "Cleaned up dynamic config file %s on device %s", deviceFilePushedPath, deviceId);
        } catch (MobileHarnessException | RuntimeException | Error e) {
          logger.atWarning().withCause(e).log(
              "Failed to clean up pushed file %s", deviceFilePushedPath);
        } catch (InterruptedException e) {
          logger.atWarning().withCause(e).log(
              "Interrupted when cleaning up pushed file %s,", deviceFilePushedPath);
          Thread.currentThread().interrupt();
        }
      }
      if (!contentProvider.isEmpty() && spec.getCleanup()) {
        apkInstaller.uninstallApk(
            getDevice(), contentProvider, /* logFailures= */ true, /* log= */ null);
      }
    }
  }

  private File getLocalConfigFile(AndroidAtsDynamicConfigPusherDecoratorSpec spec)
      throws MobileHarnessException {
    if (spec.getExtractFromResource()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_PARAM_NOT_SUPPORTED,
          "Extract from resource is not supported yet.");
    }

    String lookupName =
        String.format(
            "%s.dynamic",
            spec.getDynamicConfigName().isEmpty()
                ? spec.getConfigFilename()
                : spec.getDynamicConfigName());
    List<File> files = localFileUtil.listFiles(spec.getXtsTestDir(), /* recursively= */ true);
    return files.stream()
        .filter(file -> file.getName().equals(lookupName))
        .findFirst()
        .orElseThrow(
            () ->
                new MobileHarnessException(
                    AndroidErrorId
                        .ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_LOCAL_CONFIG_NOT_FOUND,
                    String.format("Config file %s not found.", lookupName)));
  }

  private String fetchApfeConfig(AndroidAtsDynamicConfigPusherDecoratorSpec spec)
      throws MobileHarnessException {
    if (!spec.getHasServerSideConfig()) {
      return "";
    }

    try {
      // TODO: Support to read URL from UrlReplacement.xml. More context in
      // com.android.compatibility.common.util.UrlReplacement.java
      String requestUrl =
          spec.getConfigUrl()
              .replace("{suite-name}", xtsSuiteInfoMap.get("suite_name").toUpperCase(Locale.ROOT))
              .replace("{module}", spec.getConfigFilename())
              .replace("{version}", spec.getVersion())
              .replace("{api-key}", spec.getApiKey());
      URL request = new URI(requestUrl).toURL();
      return new String(request.openStream().readAllBytes(), UTF_8);
    } catch (IOException | URISyntaxException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_PARTNER_SERVER_ERROR,
          "Failed to access android partner remote server over internet.",
          e);
    }
  }

  private File mergeConfigFiles(
      AndroidAtsDynamicConfigPusherDecoratorSpec spec,
      File localConfigFile,
      String apfeConfigInJson)
      throws MobileHarnessException, InterruptedException {
    File hostFile = null;
    try {
      hostFile =
          DynamicConfigHandler.getMergedDynamicConfigFile(
              localConfigFile, apfeConfigInJson, spec.getConfigFilename(), new HashMap<>());
      return hostFile;
    } catch (IOException | XmlPullParserException | JsonSyntaxException e) {
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
