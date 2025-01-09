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

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.app.binary.interactive.Constants;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.shared.util.command.linecallback.ScanSignalOutputCallback;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidShowInstructionDecoratorSpec;
import java.time.Duration;
import javax.inject.Inject;

/** Decorator for showing the instruction on the device. */
// TODO: add a doc for this decorator.
@DecoratorAnnotation(
    help =
        "Decorator for showing a setup instruction on the device for interactive tests. After the"
            + " user clicks the OK button or the timeout is reached, the dialog will be closed and"
            + " the test will continue.")
public class AndroidShowInstructionDecorator extends BaseDecorator
    implements SpecConfigable<AndroidShowInstructionDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String INSTRUCTION_DIALOG_APK_RES_PATH =
      "/com/google/devtools/mobileharness/platform/android/app/binary/interactive/InstructionDialog.apk";
  private static final String INSTRUCTION_DIALOG_PACKAGE_NAME =
      "com.google.devtools.mobileharness.platform.android.app.binary.interactive";
  private static final String INSTRUCTION_DIALOG_ACTIVITY = ".InstructionDialogActivity";

  private final Adb adb;
  private final AndroidProcessUtil androidProcessUtil;
  private final ApkInstaller apkInstaller;
  private final ResUtil resUtil;

  @Inject
  AndroidShowInstructionDecorator(
      Driver decorated,
      TestInfo testInfo,
      Adb adb,
      AndroidProcessUtil androidProcessUtil,
      ApkInstaller apkInstaller,
      ResUtil resUtil) {
    super(decorated, testInfo);
    this.adb = adb;
    this.androidProcessUtil = androidProcessUtil;
    this.apkInstaller = apkInstaller;
    this.resUtil = resUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws InterruptedException, MobileHarnessException {
    String serial = getDevice().getDeviceId();
    AndroidShowInstructionDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, serial);

    if (!spec.getSkipInstruction()) {
      try {
        showInstruction(serial, spec);
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atWarning()
            .withCause(e)
            .alsoTo(logger)
            .log("Failed to show the instruction. Continue the test.");
      }
    }
    getDecorated().run(testInfo);
  }

  private void showInstruction(String serial, AndroidShowInstructionDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    String path =
        resUtil.getResourceFile(
            AndroidShowInstructionDecorator.class, INSTRUCTION_DIALOG_APK_RES_PATH);
    try {
      getTest()
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Install instruction dialog to device %s", getDevice().getDeviceId());
      apkInstaller.installApkIfNotExist(
          getDevice(), ApkInstallArgs.builder().setApkPath(path).build(), getTest().log());
      String currentTime = adb.runShellWithRetry(serial, "date '+%Y-%m-%d %H:%M:%S.%3N'").trim();
      androidProcessUtil.startApplication(
          serial,
          INSTRUCTION_DIALOG_PACKAGE_NAME,
          INSTRUCTION_DIALOG_ACTIVITY,
          ImmutableMap.of(
              Constants.TITLE,
              spec.getInstructionTitle(),
              Constants.INSTRUCTION,
              spec.getInstructionContent()),
          /* clearTop= */ true);
      waitForClose(
          serial, spec.getInstructionTitle(), currentTime, spec.getInstructionDialogTimeoutSec());
    } finally {
      apkInstaller.uninstallApk(
          getDevice(), INSTRUCTION_DIALOG_PACKAGE_NAME, /* logFailures= */ true, getTest().log());
    }
  }

  private void waitForClose(String serial, String title, String currentTime, long timeoutSec)
      throws InterruptedException {
    try {
      Duration timeout =
          timeoutSec == -1 ? Duration.ofSeconds(Integer.MAX_VALUE) : Duration.ofSeconds(timeoutSec);
      var unused =
          adb.run(
              serial,
              new String[] {"logcat", "-T", currentTime, "InstructionDialog:I", "*:S"},
              timeout,
              new ScanSignalOutputCallback(
                  String.format("Instruction for %s closed", title), /* stopOnSignal= */ true));
    } catch (MobileHarnessException e) {
      getTest()
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("No user interaction before timeout. Continue the test.");
    }
  }
}
