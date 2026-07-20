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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.instrumentation.parser.AmInstrumentationParser;
import com.google.devtools.mobileharness.platform.android.instrumentation.parser.AmInstrumentationResultBuilder;
import com.google.devtools.mobileharness.platform.android.instrumentation.parser.TestTimeTrackerKt;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestResult;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestStatus;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.DevicePropertyInfo;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.StepSkippableLifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.DeviceInfoCollectorDecoratorSpec;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Inject;

/** Decorator for collecting device information. */
@DecoratorAnnotation(help = "For collecting device info from device.")
public class DeviceInfoCollectorDecorator extends StepSkippableLifecycleDecorator
    implements SpecConfigable<DeviceInfoCollectorDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String PROPERTY_COLLECTED = "device_info_collected";
  private static final String STATE_INSTALLED = "installed";

  private final AndroidFileUtil androidFileUtil;
  private final LocalFileUtil localFileUtil;
  private final AndroidAdbUtil androidAdbUtil;
  private final ApkInstaller apkInstaller;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final AndroidInstrumentationUtil androidInstrumentationUtil;

  @Inject
  DeviceInfoCollectorDecorator(
      Driver decorated,
      TestInfo testInfo,
      AndroidFileUtil androidFileUtil,
      LocalFileUtil localFileUtil,
      AndroidAdbUtil androidAdbUtil,
      ApkInstaller apkInstaller,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidInstrumentationUtil androidInstrumentationUtil)
      throws MobileHarnessException {
    super(decorated, testInfo);
    this.androidFileUtil = androidFileUtil;
    this.localFileUtil = localFileUtil;
    this.androidAdbUtil = androidAdbUtil;
    this.apkInstaller = apkInstaller;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.androidInstrumentationUtil = androidInstrumentationUtil;
  }

  @Override
  protected void skippableSetUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();

    if (testInfo.properties().has(PROPERTY_COLLECTED)) {
      testInfo.log().atInfo().alsoTo(logger).log("Device info already collected, skipping.");
      return;
    }

    DeviceInfoCollectorDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, deviceId);

    if (spec.getSkipDeviceInfo() && !spec.getForceCollectDeviceInfo()) {
      testInfo.log().atInfo().alsoTo(logger).log("Skip device info collection.");
      return;
    }

    String apk = spec.getApk();
    String packageName = spec.getPackageName();
    String xtsTestDir = spec.getXtsTestDir();

    if (apk.isEmpty() || packageName.isEmpty() || xtsTestDir.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_INFO_COLLECTOR_DECORATOR_INVALID_PARAMETER,
          "Missing required parameters (apk, package_name, xts_test_dir) in spec.");
    }

    // 1. Collect device properties host-side
    collectDeviceProperties(testInfo, deviceId);

    // 2. Run instrumentation to collect device info device-side

    String apkPath = locateApk(xtsTestDir, apk);
    testInfo.log().atInfo().alsoTo(logger).log("Located APK: %s", apkPath);

    // Uninstall existing package if present.
    testInfo.log().atInfo().alsoTo(logger).log("Uninstalling package if present: %s", packageName);
    apkInstaller.uninstallApk(getDevice(), packageName, /* logFailures= */ false, testInfo.log());

    // Install the APK.
    testInfo.log().atInfo().alsoTo(logger).log("Installing APK: %s", apkPath);
    apkInstaller.installApk(
        getDevice(),
        ApkInstallArgs.builder()
            .addApkPaths(apkPath)
            .setGrantPermissions(true)
            .setForceQueryable(true)
            .build(),
        testInfo.log());
    setState(testInfo.jobInfo(), deviceId, STATE_INSTALLED, "true");

    runCollectDeviceInfoTests(testInfo, deviceId, packageName, apkPath);

    // 3. Pull files from device
    String srcDir = spec.getSrcDir();
    String destDir = spec.getDestDir();
    if (destDir.isEmpty()) {
      destDir = "device-info-files";
    }

    String hostDestDir = PathUtil.join(testInfo.getGenFileDir(), destDir);
    localFileUtil.prepareDir(new File(hostDestDir).getParent());

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Pulling device info files from %s to %s", srcDir, hostDestDir);
    try {
      if (androidFileUtil.isFileOrDirExisted(deviceId, srcDir)) {
        String info = androidFileUtil.pull(deviceId, srcDir, hostDestDir);
        testInfo.log().atInfo().alsoTo(logger).log("%s", info);
      } else {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Source directory %s does not exist on device.", srcDir);
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_INFO_COLLECTOR_DECORATOR_PULL_FILE_ERROR,
          "Failed to pull device info files",
          e);
    }

    testInfo.properties().add(PROPERTY_COLLECTED, "true");
  }

  @Override
  protected void skippableTearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();
    DeviceInfoCollectorDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, deviceId);
    String packageName = spec.getPackageName();

    Optional<String> installed = getState(testInfo.jobInfo(), deviceId, STATE_INSTALLED);
    if (installed.isPresent() && installed.get().equals("true") && !packageName.isEmpty()) {
      testInfo.log().atInfo().alsoTo(logger).log("Uninstalling package: %s", packageName);
      apkInstaller.uninstallApk(getDevice(), packageName, /* logFailures= */ false, testInfo.log());
      setState(testInfo.jobInfo(), deviceId, STATE_INSTALLED, "false");
    }
  }

  private void collectDeviceProperties(TestInfo testInfo, String deviceId)
      throws InterruptedException {
    int apiLevel = 0;
    try {
      apiLevel = androidSystemSettingUtil.getDeviceSdkVersion(deviceId);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("Failed to get device SDK version: %s", e.getMessage());
    }

    DevicePropertyInfo devicePropertyInfo =
        DevicePropertyInfo.newBuilder()
            .abi("ro.product.cpu.abi")
            .abi2("ro.product.cpu.abi2")
            .abis("ro.product.cpu.abilist")
            .abis32("ro.product.cpu.abilist32")
            .abis64("ro.product.cpu.abilist64")
            .board("ro.product.board")
            .brand("ro.product.brand")
            .device("ro.product.device")
            .fingerprint("ro.build.fingerprint")
            .vendorFingerprint("ro.vendor.build.fingerprint")
            .bootimageFingerprint("ro.bootimage.build.fingerprint")
            .id("ro.build.id")
            .manufacturer("ro.product.manufacturer")
            .model("ro.product.model")
            .product("ro.product.name")
            .referenceFingerprint("ro.build.reference.fingerprint")
            .serial("ro.serialno")
            .tags("ro.build.tags")
            .type("ro.build.type")
            .versionBaseOs("ro.build.version.base_os")
            .versionRelease("ro.build.version.release")
            .versionSdk("ro.build.version.sdk")
            .versionSecurityPatch("ro.build.version.security_patch")
            .versionIncremental("ro.build.version.incremental")
            .versionSdkFull(apiLevel >= 36 ? "ro.build.version.sdk_full" : "ro.build.version.sdk")
            .build();

    for (Map.Entry<String, String> entry :
        devicePropertyInfo.getPropertytMapWithPrefix("cts:build_").entrySet()) {
      String propName = entry.getValue();
      String propValue = "";
      if (propName != null) {
        try {
          String val = androidAdbUtil.getProperty(deviceId, ImmutableList.of(propName));
          if (val != null) {
            propValue = val.trim();
          }
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log("Failed to get property %s: %s", propName, e.getMessage());
        }
      }
      testInfo.properties().add(entry.getKey(), propValue);
    }
  }

  private String locateApk(String xtsTestDir, String apk) throws MobileHarnessException {
    if (!localFileUtil.isDirExist(xtsTestDir)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_INFO_COLLECTOR_DECORATOR_APK_NOT_FOUND,
          "xts_test_dir does not exist: " + xtsTestDir);
    }

    File directApkFile = new File(xtsTestDir, apk);
    if (localFileUtil.isFileExist(directApkFile.getAbsolutePath())) {
      return directApkFile.getAbsolutePath();
    }

    List<String> files = localFileUtil.listFilePaths(xtsTestDir, /* recursively= */ true);
    Optional<String> locatedPath =
        files.stream().filter(f -> f.endsWith(File.separator + apk) || f.equals(apk)).findFirst();

    if (locatedPath.isPresent()) {
      return locatedPath.get();
    }

    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_DEVICE_INFO_COLLECTOR_DECORATOR_APK_NOT_FOUND,
        String.format("Unable to find APK %s in %s", apk, xtsTestDir));
  }

  private void runCollectDeviceInfoTests(
      TestInfo testInfo, String deviceId, String packageName, String apkPath)
      throws MobileHarnessException, InterruptedException {
    int deviceSdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(deviceId);
    String runnerName =
        androidInstrumentationUtil.getTestRunnerClassName(
            testInfo, deviceId, packageName, apkPath, /* analyzeApk= */ false);

    TestSuiteResult.Builder testSuiteResultBuilder = TestSuiteResult.newBuilder();
    AmInstrumentationParser amInstrumentationParser =
        new AmInstrumentationParser(
            ImmutableSet.of(new AmInstrumentationResultBuilder(testSuiteResultBuilder)),
            () -> TestTimeTrackerKt.TestTimeTracker(Instant::now));

    Supplier<LineCallback> lineCallbackFactory =
        () -> {
          testSuiteResultBuilder.clear();
          return new LineCallback() {
            @Override
            public LineCallback.Response onLine(String line) {
              amInstrumentationParser.parse(line);
              return LineCallback.Response.empty();
            }
          };
        };

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

    Duration timeout = testInfo.timer().remainingTimeJava();
    testInfo.log().atInfo().alsoTo(logger).log("Running instrumentation tests: %s", setting);
    androidInstrumentationUtil.instrument(
        deviceId, deviceSdkVersion, setting, timeout, lineCallbackFactory);

    TestSuiteResult result = testSuiteResultBuilder.build();
    ImmutableList<TestResult> failedTests =
        result.getTestResultList().stream()
            .filter(
                r ->
                    r.getTestStatus() == TestStatus.FAILED || r.getTestStatus() == TestStatus.ERROR)
            .collect(toImmutableList());

    if (!failedTests.isEmpty()) {
      StringBuilder failureDetails = new StringBuilder();
      for (TestResult failedTest : failedTests) {
        failureDetails.append(
            String.format(
                "Test Class: %s, Test Method: %s, Failure Message: %s\n",
                failedTest.getTestCase().getTestClass(),
                failedTest.getTestCase().getTestMethod(),
                failedTest.hasError() ? failedTest.getError().getErrorMessage() : "unknown"));
      }
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("Device info collection tests failed:\n%s", failureDetails);
    }
  }
}
