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

package com.google.wireless.qa.mobileharness.shared.api.device;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AndroidJitEmulatorUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.Timeout;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.validator.ValidatorFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.nio.file.Path;
import java.time.Duration;
import javax.annotation.Nullable;

/** Android emulator device class. This emulator is created by acloud on demand of test requests. */
public class AndroidJitEmulator extends AndroidDevice {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String JIT_EMULATOR_IMAGE_ZIP = "JIT_EMULATOR_IMAGE_ZIP";
  public static final String JIT_EMULATOR_HOST_PACKAGE = "JIT_EMULATOR_HOST_PACKAGE";
  public static final String JIT_EMULATOR_ACLOUD = "JIT_EMULATOR_ACLOUD";

  // TODO: Move to a shared class.
  public static final int EMULATOR_BASE_PORT = 6520;
  public static final int EMULATOR_INSTANCE_ID_BASE = 1;

  private final String deviceId;
  private final AndroidAdbUtil androidAdbUtil;
  private final LocalFileUtil localFileUtil;
  private final CommandExecutor commandExecutor;

  public AndroidJitEmulator(String deviceId) {
    this(
        deviceId,
        ApiConfig.getInstance(),
        new ValidatorFactory(),
        new CommandExecutor(),
        new AndroidAdbUtil(),
        new LocalFileUtil());
  }

  @VisibleForTesting
  AndroidJitEmulator(
      String deviceId,
      @Nullable ApiConfig apiConfig,
      @Nullable ValidatorFactory validatorFactory,
      CommandExecutor commandExecutor,
      AndroidAdbUtil androidAdbUtil,
      LocalFileUtil localFileUtil) {
    super(deviceId, apiConfig, validatorFactory);
    this.deviceId = deviceId;
    this.commandExecutor = commandExecutor;
    this.androidAdbUtil = androidAdbUtil;
    this.localFileUtil = localFileUtil;
  }

  @Override
  public void setUp() throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("JIT emulator start setup. %s", deviceId);
    addDimension(Ascii.toLowerCase(AndroidProperty.ABILIST.name()), "x86_64,arm64-v8a");
    addDimension(Ascii.toLowerCase(AndroidProperty.ABI.name()), "arm64-v8a");
    addSupportedDriver("NoOpDriver");
    addSupportedDriver("AndroidInstrumentation");
    addSupportedDriver("MoblyTest");
    addSupportedDriver("XtsTradefedTest");

    addSupportedDecorator("AndroidHdVideoDecorator");
    addSupportedDecorator("AndroidDeviceSettingsDecorator");
    addSupportedDecorator("AndroidAdbShellDecorator");

    addSupportedDeviceType(AndroidJitEmulator.class.getSimpleName());
    addSupportedDeviceType(AndroidDevice.class.getSimpleName());
    basicAndroidDecoratorConfiguration();

    logger.atInfo().log("JIT emulator %s is Ready", deviceId);
  }

  @Override
  public void preRunTest(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    if (Flags.instance().noopJitEmulator.getNonNull()) {
      logger.atInfo().log(
          "JIT emulator %s is no-op. Tradefed will be responsible for actually launching the"
              + " emulator.",
          deviceId);
      return;
    }
    String imgZip = testInfo.jobInfo().files().getSingle("device");
    String hostPkg = testInfo.jobInfo().files().getSingle("cvd-host_package.tar.gz");
    Path imageDir = Path.of(testInfo.getTmpFileDir()).resolve("image_dir");
    localFileUtil.prepareDir(imageDir);

    Path hostPkgDir = Path.of(testInfo.getTmpFileDir()).resolve("cvd-host_package");
    String acloudPath = Flags.instance().acloudPath.getNonNull();
    localFileUtil.prepareDir(hostPkgDir);
    localFileUtil.unzipFile(imgZip, imageDir.toString());

    Command unzipHostPkgCmd = Command.of("tar", "-xvzf", hostPkg, "-C", hostPkgDir.toString());
    String unzipHostPkgOutput = commandExecutor.run(unzipHostPkgCmd);
    logger.atFine().log("unzipHostPkgOutput: %s", unzipHostPkgOutput);

    localFileUtil.grantFileOrDirFullAccessRecursively(imageDir);
    localFileUtil.grantFileOrDirFullAccessRecursively(hostPkgDir);
    localFileUtil.grantFileOrDirFullAccess(acloudPath);

    Command command =
        Command.of(
                acloudPath,
                "create",
                "--local-instance",
                AndroidJitEmulatorUtil.getAcloudInstanceId(getDeviceId()),
                "--local-image",
                imageDir.toString(),
                "--local-tool",
                hostPkgDir.toString(),
                "--yes",
                "--no-autoconnect",
                "--skip-pre-run-check")
            .timeout(Timeout.fixed(Duration.ofMinutes(2)));
    String output = commandExecutor.run(command);
    logger.atInfo().log("acloud create output: %s", output);
    String deviceIpAddress = getDeviceId();
    // Attempt to establish an ADB connection to a device's IP address, with retry mechanism
    androidAdbUtil.connect(deviceIpAddress);
  }

  @CanIgnoreReturnValue
  @Override
  public PostTestDeviceOp postRunTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    if (Flags.instance().noopJitEmulator.getNonNull()) {
      logger.atInfo().log("JIT emulator %s is no-op", deviceId);
      return PostTestDeviceOp.NONE;
    }
    String acloudPath = Flags.instance().acloudPath.getNonNull();
    localFileUtil.grantFileOrDirFullAccess(acloudPath);
    Command command =
        Command.of(
            acloudPath,
            "delete",
            "--instance-names",
            "local-instance-" + AndroidJitEmulatorUtil.getAcloudInstanceId(getDeviceId()));
    String output = commandExecutor.run(command);
    logger.atInfo().log("acloud delete output: %s", output);
    return PostTestDeviceOp.NONE;
  }

  @Override
  public boolean canReboot() {
    return false;
  }

  @Override
  public void reboot() throws MobileHarnessException, InterruptedException {
    logger.atSevere().log("Unexpected attempt to reboot a non-rebootable device");
  }

  @Override
  public boolean isRooted() {
    //  We suppose all emulators are rooted.
    return true;
  }
}
