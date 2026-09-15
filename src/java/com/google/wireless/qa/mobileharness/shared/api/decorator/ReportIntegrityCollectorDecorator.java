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

import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidRemoteProvisioningUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.SetupOnlyDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.ReportIntegrityCollectorDecoratorSpec;
import java.io.File;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import javax.inject.Inject;

/**
 * A decorator that collects data from devices to validate report integrity.
 *
 * <p>Besides the VBMeta digest and the CSR values which are read from the device directly, it also
 * installs and instruments a device info APK (e.g. {@code ReportIntegrityInfo.apk}) to collect the
 * {@code verified_boot_hash} values reported by keystore attestation.
 */
@DecoratorAnnotation(help = "Collects data from devices to validate report integrity.")
public class ReportIntegrityCollectorDecorator extends SetupOnlyDecorator
    implements SpecConfigable<ReportIntegrityCollectorDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String VB_META_DIGEST = "ro.boot.vbmeta.digest";

  private static final String KEYSTORE_ATTESTATION_FILE_NAME =
      "KeystoreAttestationDeviceInfo.deviceinfo.json";
  private static final String ROOT_OF_TRUST = "root_of_trust";
  private static final String VERIFIED_BOOT_HASH = "verified_boot_hash";

  /** Name of the directory under the test tmp dir which holds the pulled device info files. */
  private static final String PULLED_DEVICE_INFO_DIR_NAME = "report_integrity_device_info_files";

  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidRemoteProvisioningUtil androidRemoteProvisioningUtil;
  private final ApkInstaller apkInstaller;
  private final AndroidFileUtil androidFileUtil;
  private final LocalFileUtil localFileUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final AndroidInstrumentationUtil androidInstrumentationUtil;

  @Inject
  ReportIntegrityCollectorDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      AndroidAdbUtil androidAdbUtil,
      AndroidRemoteProvisioningUtil androidRemoteProvisioningUtil,
      ApkInstaller apkInstaller,
      AndroidFileUtil androidFileUtil,
      LocalFileUtil localFileUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidInstrumentationUtil androidInstrumentationUtil) {
    super(decoratedDriver, testInfo);
    this.androidAdbUtil = androidAdbUtil;
    this.androidRemoteProvisioningUtil = androidRemoteProvisioningUtil;
    this.apkInstaller = apkInstaller;
    this.androidFileUtil = androidFileUtil;
    this.localFileUtil = localFileUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.androidInstrumentationUtil = androidInstrumentationUtil;
  }

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();

    String deviceId = getDevice().getDeviceId();

    // Collect verified boot hash values. It is best effort so that a missing or misbehaving device
    // info APK never fails the test.
    collectVerifiedBootHashes(testInfo, deviceId, testInfo.jobInfo().combinedSpec(this, deviceId));

    // Collect VBMeta data.
    String vbMetaDigest = androidAdbUtil.getProperty(deviceId, ImmutableList.of(VB_META_DIGEST));
    testInfo.properties().add("cts:build_vb_meta_digest", vbMetaDigest);

    // Collect csr values.
    try {
      Map<String, byte[]> instanceNameToCsrMap =
          androidRemoteProvisioningUtil.getInstanceNameToCsr(deviceId);
      for (Map.Entry<String, byte[]> instanceNameToCsr : instanceNameToCsrMap.entrySet()) {
        String instanceName = nullToEmpty(instanceNameToCsr.getKey());
        String csr = Base64.getEncoder().encodeToString(instanceNameToCsr.getValue());
        testInfo.properties().add("cts:csr_" + instanceName, csr);
      }
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .at(Level.WARNING)
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to collect csr values from %s", deviceId);
    }
    return SetupResult.continueDecorated();
  }

  /**
   * Installs and instruments the device info APK, then adds the {@code verified_boot_hash} values
   * it reports to the test properties.
   *
   * <p>All failures are logged instead of being thrown, since the report is still usable without
   * these values.
   */
  private void collectVerifiedBootHashes(
      TestInfo testInfo, String deviceId, ReportIntegrityCollectorDecoratorSpec spec)
      throws InterruptedException {
    String apk = spec.getApk();
    String packageName = spec.getPackageName();
    if (apk.isEmpty() || packageName.isEmpty()) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("No apk/package_name configured, skips collecting verified boot hash values.");
      return;
    }

    try {
      String apkPath = locateApk(spec.getXtsTestDir(), apk);
      testInfo.log().atInfo().alsoTo(logger).log("Located APK: %s", apkPath);

      apkInstaller.uninstallApk(getDevice(), packageName, /* logFailures= */ false, testInfo.log());
      apkInstaller.installApk(
          getDevice(),
          ApkInstallArgs.builder()
              .addApkPaths(apkPath)
              .setGrantPermissions(true)
              .setForceQueryable(true)
              .build(),
          testInfo.log());
      try {
        runDeviceInfoInstrumentation(testInfo, deviceId, packageName, apkPath);
        Optional<String> deviceInfoFile = pullKeystoreAttestationFile(testInfo, deviceId, spec);
        if (deviceInfoFile.isPresent()) {
          addVerifiedBootHashProperties(testInfo, deviceInfoFile.get());
        }
      } finally {
        // Uninstall right away instead of leaving the APK on the device for the whole session.
        // This matches TradeFed, where ApkInstrumentationPreparer installs and uninstalls within
        // a single run(). The pulled files live under /sdcard and survive the uninstallation.
        apkInstaller.uninstallApk(
            getDevice(), packageName, /* logFailures= */ true, testInfo.log());
      }
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .at(Level.WARNING)
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to collect verified boot hash values from %s", deviceId);
    }
  }

  private String locateApk(String xtsTestDir, String apk) throws MobileHarnessException {
    if (xtsTestDir.isEmpty() || !localFileUtil.isDirExist(xtsTestDir)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_APK_NOT_FOUND,
          "xts_test_dir does not exist: " + xtsTestDir);
    }

    File directApkFile = new File(xtsTestDir, apk);
    if (localFileUtil.isFileExist(directApkFile.getAbsolutePath())) {
      return directApkFile.getAbsolutePath();
    }

    List<String> files = localFileUtil.listFilePaths(xtsTestDir, /* recursively= */ true);
    Optional<String> locatedPath =
        files.stream().filter(f -> f.endsWith(File.separator + apk)).findFirst();
    if (locatedPath.isPresent()) {
      return locatedPath.get();
    }

    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_APK_NOT_FOUND,
        String.format("Unable to find APK %s in %s", apk, xtsTestDir));
  }

  private void runDeviceInfoInstrumentation(
      TestInfo testInfo, String deviceId, String packageName, String apkPath)
      throws MobileHarnessException, InterruptedException {
    int deviceSdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(deviceId);
    String runnerName =
        androidInstrumentationUtil.getTestRunnerClassName(
            testInfo, deviceId, packageName, apkPath, /* analyzeApk= */ false);

    AndroidInstrumentationSetting setting =
        AndroidInstrumentationSetting.create(
            packageName,
            runnerName,
            /* className= */ null,
            /* otherOptions= */ null,
            /* async= */ false,
            /* showRawResults= */ true,
            /* prefixAndroidTest= */ false,
            /* noIsolatedStorage= */ true,
            /* useTestStorageService= */ false,
            /* enableCoverage= */ false);

    testInfo.log().atInfo().alsoTo(logger).log("Running instrumentation: %s", setting);
    androidInstrumentationUtil.instrument(
        deviceId, deviceSdkVersion, setting, testInfo.timer().remainingTimeJava());
  }

  /**
   * Pulls the device info files generated by the APK into the test tmp dir, and returns the path of
   * the keystore attestation device info file if it exists.
   *
   * <p>The files are pulled into the tmp dir instead of the gen file dir on purpose: they are only
   * needed to extract the properties below and should not show up in the test result.
   */
  private Optional<String> pullKeystoreAttestationFile(
      TestInfo testInfo, String deviceId, ReportIntegrityCollectorDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    String srcDir = spec.getSrcDir();
    if (!androidFileUtil.isFileOrDirExisted(deviceId, srcDir)) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("Source directory %s does not exist on device %s.", srcDir, deviceId);
      return Optional.empty();
    }

    String hostDestDir = PathUtil.join(testInfo.getTmpFileDir(), PULLED_DEVICE_INFO_DIR_NAME);
    localFileUtil.prepareDir(hostDestDir);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("%s", androidFileUtil.pull(deviceId, srcDir, hostDestDir));

    return localFileUtil.listFilePaths(hostDestDir, /* recursively= */ true).stream()
        .filter(path -> path.endsWith(File.separator + KEYSTORE_ATTESTATION_FILE_NAME))
        .findFirst();
  }

  /**
   * Adds a {@code cts:<instance>.root_of_trust.verified_boot_hash} test property for every
   * attestation instance found in the given keystore attestation device info file.
   */
  private void addVerifiedBootHashProperties(TestInfo testInfo, String deviceInfoFile)
      throws MobileHarnessException {
    JsonObject deviceInfo;
    try {
      deviceInfo = JsonParser.parseString(localFileUtil.readFile(deviceInfoFile)).getAsJsonObject();
    } catch (RuntimeException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REPORT_INTEGRITY_DECORATOR_DEVICE_INFO_PARSE_ERROR,
          "Failed to parse " + deviceInfoFile,
          e);
    }

    for (Map.Entry<String, JsonElement> attestation : deviceInfo.entrySet()) {
      if (!attestation.getValue().isJsonObject()) {
        continue;
      }
      JsonObject rootOfTrust =
          attestation.getValue().getAsJsonObject().getAsJsonObject(ROOT_OF_TRUST);
      if (rootOfTrust == null || !rootOfTrust.has(VERIFIED_BOOT_HASH)) {
        continue;
      }
      testInfo
          .properties()
          .add(
              String.format(
                  "cts:%s.%s.%s", attestation.getKey(), ROOT_OF_TRUST, VERIFIED_BOOT_HASH),
              rootOfTrust.get(VERIFIED_BOOT_HASH).getAsString());
    }
  }
}
