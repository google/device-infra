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
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.DynamicConfig;
import com.google.devtools.mobileharness.platform.android.xts.config.DynamicConfigHandler;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.gson.JsonSyntaxException;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.StepSkippableLifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec.TestTarget;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Decorator to push dynamic config files from config repository. Partially branched from {@code
 * com.android.compatibility.common.tradefed.targetprep.DynamicConfigPusher} from Android codebase.
 */
@DecoratorAnnotation(help = "Decorator to push dynamic config files from config repository.")
public class AndroidAtsDynamicConfigPusherDecorator extends StepSkippableLifecycleDecorator
    implements SpecConfigable<AndroidAtsDynamicConfigPusherDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String MANAGED_FILE_CONTENT_PROVIDER_APK_RES_PATH =
      "/com/google/devtools/mobileharness/platform/android/app/binary/contentprovider/ManagedFileContentProvider.apk";

  private static final String STATE_KEY_DEVICE_FILE_PUSHED_PATH = "device_file_pushed_path";
  private static final String STATE_KEY_CONTENT_PROVIDER = "content_provider";

  private final LocalFileUtil localFileUtil;
  private final AndroidFileUtil androidFileUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final ApkInstaller apkInstaller;
  private final ResUtil resUtil;
  private final MapSplitter xtsSuiteInfoSplitter = Splitter.on(",").withKeyValueSeparator("=");

  @Inject
  AndroidAtsDynamicConfigPusherDecorator(
      Driver decorated,
      TestInfo testInfo,
      LocalFileUtil localFileUtil,
      AndroidFileUtil androidFileUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      ApkInstaller apkInstaller,
      ResUtil resUtil)
      throws MobileHarnessException {
    super(decorated, testInfo);
    this.localFileUtil = localFileUtil;
    this.androidFileUtil = androidFileUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.apkInstaller = apkInstaller;
    this.resUtil = resUtil;
  }

  @Override
  protected void skippableSetUp(SetupContext context)
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

    File localConfigFile = getLocalConfigFile(spec, suiteName);
    String apfeConfig = fetchApfeConfig(spec, xtsSuiteInfoMap);
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
      setState(testInfo.jobInfo(), deviceId, STATE_KEY_DEVICE_FILE_PUSHED_PATH, deviceDest);
      logger.atInfo().log("Pushed dynamic config file %s to device %s", deviceDest, deviceId);
    }
    String apkPath =
        resUtil.getResourceFile(
            AndroidAtsDynamicConfigPusherDecorator.class,
            MANAGED_FILE_CONTENT_PROVIDER_APK_RES_PATH);
    String contentProvider =
        apkInstaller.installApkIfNotExist(
            getDevice(), ApkInstallArgs.builder().setApkPath(apkPath).build(), getTest().log());
    setState(testInfo.jobInfo(), deviceId, STATE_KEY_CONTENT_PROVIDER, contentProvider);
  }

  @Override
  protected void skippableTearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();
    AndroidAtsDynamicConfigPusherDecoratorSpec spec =
        testInfo.jobInfo().combinedSpec(this, deviceId);

    Optional<String> deviceFilePushedPathOpt =
        getState(testInfo.jobInfo(), deviceId, STATE_KEY_DEVICE_FILE_PUSHED_PATH);
    Optional<String> contentProviderOpt =
        getState(testInfo.jobInfo(), deviceId, STATE_KEY_CONTENT_PROVIDER);

    if (deviceFilePushedPathOpt.isPresent() && spec.getCleanup()) {
      String path = deviceFilePushedPathOpt.get();
      try {
        androidFileUtil.removeFiles(deviceId, path);
        logger.atInfo().log("Cleaned up dynamic config file %s on device %s", path, deviceId);
      } catch (MobileHarnessException | RuntimeException | Error e) {
        logger.atWarning().withCause(e).log("Failed to clean up pushed file %s", path);
      }
    }
    if (contentProviderOpt.isPresent()
        && !contentProviderOpt.get().isEmpty()
        && spec.getCleanup()) {
      apkInstaller.uninstallApk(
          getDevice(), contentProviderOpt.get(), /* logFailures= */ true, /* log= */ null);
    }
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
      if (suiteName != null && !spec.getXtsTestDir().isEmpty()) {
        Path xtsRootDir = Path.of(spec.getXtsTestDir()).getParent().getParent();
        File toolsDir = XtsDirUtil.getXtsToolsDir(xtsRootDir, suiteName).toFile();
        File tradefedJar = new File(toolsDir, Ascii.toLowerCase(suiteName) + "-tradefed.jar");
        if (tradefedJar.exists()) {
          try (ZipFile zipFile = new ZipFile(tradefedJar)) {
            ZipEntry entry = zipFile.getEntry(fileName);
            if (entry != null) {
              try (InputStream inputStream = zipFile.getInputStream(entry)) {
                File localConfigFile = File.createTempFile(lookupName, ".dynamic");
                Files.copy(
                    inputStream, localConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return localConfigFile;
              }
            }
          } catch (IOException e) {
            logger.atWarning().withCause(e).log(
                "Failed to read from %s", tradefedJar.getAbsolutePath());
          }
        }
      }

      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_LOCAL_CONFIG_NOT_FOUND,
          String.format("Fail to find '%s' in tradefed jar", fileName));
    }

    List<File> files = localFileUtil.listFiles(spec.getXtsTestDir(), /* recursively= */ true);
    return files.stream()
        .filter(file -> file.getName().equals(fileName))
        .findFirst()
        .orElseThrow(
            () ->
                new MobileHarnessException(
                    AndroidErrorId
                        .ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_LOCAL_CONFIG_NOT_FOUND,
                    String.format("Config file %s not found.", fileName)));
  }

  @Nullable
  private String fetchApfeConfig(
      AndroidAtsDynamicConfigPusherDecoratorSpec spec, Map<String, String> xtsSuiteInfoMap)
      throws MobileHarnessException {
    if (!spec.getHasServerSideConfig()) {
      return null;
    }
    String suiteName = xtsSuiteInfoMap.get("suite_name");
    if (suiteName == null) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ATS_DYNAMIC_CONFIG_PUSHER_DECORATOR_INVALID_PARAMETER,
          "suite_name is missing from xtsSuiteInfo");
    }

    try {
      // TODO: Support to read URL from UrlReplacement.xml. More context in
      // com.android.compatibility.common.util.UrlReplacement.java
      String requestUrl =
          spec.getConfigUrl()
              .replace("{suite-name}", suiteName.toUpperCase(Locale.ROOT))
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
