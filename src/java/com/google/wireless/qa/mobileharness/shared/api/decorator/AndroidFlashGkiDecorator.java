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

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.stream.Collectors.toCollection;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Enums.FastbootProperty;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.api.testrunner.step.android.DeviceInitializationStep;
import com.google.devtools.mobileharness.api.testrunner.step.android.InitializationArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.RebootMode;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.PostSetDmVerityDeviceOp;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.CommandTimeoutException;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager;
import com.google.devtools.mobileharness.shared.util.quota.QuotaManager.Lease;
import com.google.devtools.mobileharness.shared.util.quota.proto.Quota.QuotaKey;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.StepAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidFlashGkiDecoratorSpec;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Flashes the GKI images after the base platform flash. */
@DecoratorAnnotation(help = "Flashes the GKI images after the base platform flash.")
public class AndroidFlashGkiDecorator extends LifecycleDecorator
    implements SpecConfigable<AndroidFlashGkiDecoratorSpec> {
  @AutoValue
  abstract static class FlashImages {
    abstract Optional<File> vendorBootImage();

    abstract Optional<File> vendorKernelBootImage();

    abstract Optional<File> dtbImage();

    abstract Optional<File> initramfsImage();

    abstract Optional<File> dtboImage();

    abstract File gkiBootImage();

    abstract Optional<File> vendorDlkmImage();

    abstract Optional<File> systemDlkmImage();

    abstract Optional<File> vbmetaImage();

    static Builder builder() {
      return new AutoValue_AndroidFlashGkiDecorator_FlashImages.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setVendorBootImage(File value);

      abstract Builder setVendorKernelBootImage(File value);

      abstract Builder setDtbImage(File value);

      abstract Builder setInitramfsImage(File value);

      abstract Builder setDtboImage(File value);

      abstract Builder setGkiBootImage(File value);

      abstract Builder setVendorDlkmImage(File value);

      abstract Builder setSystemDlkmImage(File value);

      abstract Builder setVbmetaImage(File value);

      abstract FlashImages build();
    }
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String AVBTOOL = "bin/avbtool";
  private static final String MKBOOTIMG = "bin/mkbootimg";
  // Wait time for device state to stabilize in millisecond
  private static final Duration STATE_STABILIZATION_WAIT_TIME = Duration.ofSeconds(10);
  private static final Pattern STRING_PATTERN = Pattern.compile("\\s+");
  private static final Pattern ADDRESS_PATTERN = Pattern.compile("^fastboot://localhost:(\\d+)$");
  private static final Pattern KERNEL_VERSION_PATTERN =
      Pattern.compile("(\\d+\\.\\d+)\\.(\\d+)(?:-rc\\d+)?-(android\\d+|mainline)-.*-ab(\\w+)");

  private final CommandExecutor commandExecutor;
  private final LocalFileUtil localFileUtil;
  private final AndroidSystemStateUtil androidSystemStateUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final Fastboot fastboot;
  private final Sleeper sleeper;
  private final AndroidAdbUtil androidAdbUtil;
  private final Adb adb;
  private final ApkInstaller apkInstaller;
  private final QuotaManager quotaManager;
  private final DeviceCache deviceCache;

  private ArrayList<String> flashOptions = new ArrayList<>();

  @StepAnnotation DeviceInitializationStep deviceInitializationStep;

  @Inject
  AndroidFlashGkiDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      CommandExecutor commandExecutor,
      LocalFileUtil localFileUtil,
      AndroidSystemStateUtil androidSystemStateUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      Fastboot fastboot,
      Sleeper sleeper,
      AndroidAdbUtil androidAdbUtil,
      Adb adb,
      ApkInstaller apkInstaller,
      DeviceInitializationStep deviceInitializationStep) {
    this(
        decoratedDriver,
        testInfo,
        commandExecutor,
        localFileUtil,
        androidSystemStateUtil,
        androidSystemSettingUtil,
        fastboot,
        sleeper,
        androidAdbUtil,
        adb,
        apkInstaller,
        QuotaManager.getInstance(),
        DeviceCache.getInstance(),
        deviceInitializationStep);
  }

  @VisibleForTesting
  AndroidFlashGkiDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      CommandExecutor commandExecutor,
      LocalFileUtil localFileUtil,
      AndroidSystemStateUtil androidSystemStateUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      Fastboot fastboot,
      Sleeper sleeper,
      AndroidAdbUtil androidAdbUtil,
      Adb adb,
      ApkInstaller apkInstaller,
      QuotaManager quotaManager,
      DeviceCache deviceCache,
      DeviceInitializationStep deviceInitializationStep) {
    super(decoratedDriver, testInfo);
    this.commandExecutor = commandExecutor;
    this.localFileUtil = localFileUtil;
    this.androidSystemStateUtil = androidSystemStateUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.fastboot = fastboot;
    this.sleeper = sleeper;
    this.androidAdbUtil = androidAdbUtil;
    this.adb = adb;
    this.apkInstaller = apkInstaller;
    this.quotaManager = quotaManager;
    this.deviceCache = deviceCache;
    this.deviceInitializationStep = deviceInitializationStep;
  }

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    Device device = getDevice();
    String deviceId = device.getDeviceId();
    AndroidFlashGkiDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, deviceId);

    flashOptions =
        spec.getFastbootFlashOptionsList().stream()
            .map(String::trim)
            .collect(toCollection(ArrayList::new));
    FlashImages flashImages = prepareFlashImages(testInfo, spec);
    if (spec.getAddHashFooter()) {
      addHashFooter(testInfo, spec, deviceId, flashImages.gkiBootImage());
    }
    validateKernelVersion(testInfo, spec, deviceId, flashImages);
    flashGki(testInfo, spec, flashImages);
    return SetupResult.continueDecorated();
  }

  @Override
  protected void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    Device device = getDevice();
    String deviceId = device.getDeviceId();
    invalidateCacheDevice(deviceId);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Finished AndroidFlashGkiDecorator on device %s", deviceId);
  }

  private FlashImages prepareFlashImages(TestInfo testInfo, AndroidFlashGkiDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    FlashImages.Builder flashImagesBuilder =
        FlashImages.builder().setGkiBootImage(getOrBuildGkiBootImg(testInfo, spec));

    if (!spec.getVendorBootImage().isEmpty()) {
      flashImagesBuilder.setVendorBootImage(new File(spec.getVendorBootImage()));
    }
    if (!spec.getVendorKernelBootImage().isEmpty()) {
      flashImagesBuilder.setVendorKernelBootImage(new File(spec.getVendorKernelBootImage()));
    }
    if (!spec.getDtbImage().isEmpty()) {
      flashImagesBuilder.setDtbImage(new File(spec.getDtbImage()));
    }
    if (!spec.getInitramfsImage().isEmpty()) {
      flashImagesBuilder.setInitramfsImage(new File(spec.getInitramfsImage()));
    }
    if (!spec.getDtboImage().isEmpty()) {
      flashImagesBuilder.setDtboImage(new File(spec.getDtboImage()));
    }
    if (!spec.getVendorDlkmImage().isEmpty()) {
      flashImagesBuilder.setVendorDlkmImage(new File(spec.getVendorDlkmImage()));
    }
    if (!spec.getSystemDlkmImage().isEmpty()) {
      flashImagesBuilder.setSystemDlkmImage(new File(spec.getSystemDlkmImage()));
    }
    if (!spec.getVbmetaImage().isEmpty()) {
      flashImagesBuilder.setVbmetaImage(new File(spec.getVbmetaImage()));
    }

    return flashImagesBuilder.build();
  }

  private File getOrBuildGkiBootImg(TestInfo testInfo, AndroidFlashGkiDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    String gkiBootImagePath = spec.getGkiBootImage();
    String bootImageFileName = spec.getBootImageFile();
    File gkiBootImage = null;
    if (!gkiBootImagePath.isEmpty() && !bootImageFileName.isEmpty()) {
      gkiBootImage =
          getRequestedFile(testInfo, bootImageFileName, Path.of(gkiBootImagePath).toFile());
      return gkiBootImage;
    }

    String kernelImagePath = spec.getKernelImage();
    if (kernelImagePath.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_FILE_NOT_FOUND,
          "kernel_image is not provided. Can not generate GKI boot.img.");
    }

    String ramdiskImagePath = spec.getRamdiskImage();
    if (ramdiskImagePath.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_FILE_NOT_FOUND,
          "ramdisk_image is not provided. Can not generate GKI boot.img.");
    }

    String otatoolsZipPath = spec.getOtatoolsZip();
    if (otatoolsZipPath.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_FILE_NOT_FOUND,
          "otatools_zip is not provided. Can not generate GKI boot.img.");
    }

    File mkbootimg = getRequestedFile(testInfo, MKBOOTIMG, Path.of(otatoolsZipPath).toFile());
    File tmpDir = new File(testInfo.getTmpFileDir());
    mkbootimg.setExecutable(true, false);
    gkiBootImage =
        new File(localFileUtil.createTempFile(tmpDir.toPath(), "boot", ".img").toString());
    String cmd =
        String.format(
            "%s --kernel %s --header_version %d --base 0x00000000 "
                + "--pagesize 4096 --ramdisk %s -o %s",
            mkbootimg.getPath(),
            kernelImagePath,
            spec.getBootHeaderVersion(),
            ramdiskImagePath,
            gkiBootImage.getPath());
    CommandResult unusedResult = executeHostCommand(testInfo, cmd);
    long gkiBootImageSize = localFileUtil.getFileSize(gkiBootImage.toPath());
    testInfo.log().atInfo().alsoTo(logger).log("The GKI boot.img is of size %d", gkiBootImageSize);

    if (gkiBootImageSize == 0) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_GENERATE_GKI_BOOT_IMG_ERROR,
          "The mkbootimg tool didn't generate a valid boot.img.");
    }
    return gkiBootImage;
  }

  private void addHashFooter(
      TestInfo testInfo, AndroidFlashGkiDecoratorSpec spec, String deviceId, File gkiBootImage)
      throws MobileHarnessException, InterruptedException {
    if (gkiBootImage == null) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_FILE_NOT_FOUND,
          "gki_boot_image is not provided. Can not add hash footer to it.");
    }
    String otatoolsZipPath = spec.getOtatoolsZip();
    if (otatoolsZipPath.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_FILE_NOT_FOUND,
          "otatools_zip is not provided. Can not add hash footer to GKI boot.img.");
    }
    File avbtool = getRequestedFile(testInfo, AVBTOOL, Path.of(otatoolsZipPath).toFile());
    avbtool.setExecutable(true, false);
    File bootImgKey =
        getRequestedFile(testInfo, spec.getBootImageKeyPath(), Path.of(otatoolsZipPath).toFile());

    String androidVersion =
        androidAdbUtil.getProperty(deviceId, ImmutableList.of("ro.build.version.release")).trim();
    if (isNullOrEmpty(androidVersion)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_GET_ANDROID_PROPERTY_ERROR,
          "Can not get android version from property ro.build.version.release.");
    }
    String securityPatchLevel = spec.getSecurityPatchLevel();
    if (isNullOrEmpty(securityPatchLevel)) {
      securityPatchLevel =
          androidAdbUtil
              .getProperty(deviceId, ImmutableList.of("ro.build.version.security_patch"))
              .trim();
      if (isNullOrEmpty(securityPatchLevel)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_GET_ANDROID_PROPERTY_ERROR,
            "--security-patch-level is not provided. Can not get security patch version"
                + " from property ro.build.version.security_patch.");
      }
    }

    String command = String.format("du -b %s", gkiBootImage.getPath());
    CommandResult unusedCmdResult = executeHostCommand(testInfo, command);
    String partitionSize = Splitter.on(STRING_PATTERN).splitToList(unusedCmdResult.stdout()).get(0);
    testInfo.log().atInfo().alsoTo(logger).log("Boot image partition size: %s", partitionSize);
    String cmd =
        String.format(
            "%s add_hash_footer --image %s --partition_size %s "
                + "--algorithm %s "
                + "--key %s "
                + "--partition_name boot "
                + "--prop com.android.build.boot.os_version:%s "
                + "--prop com.android.build.boot.security_patch:%s",
            avbtool.getPath(),
            gkiBootImage.getPath(),
            partitionSize,
            spec.getBootImageAlgorithm(),
            bootImgKey.getPath(),
            androidVersion,
            securityPatchLevel);
    CommandResult unusedCmdResult2 = executeHostCommand(testInfo, cmd);
  }

  private void validateKernelVersion(
      TestInfo testInfo,
      AndroidFlashGkiDecoratorSpec spec,
      String deviceId,
      FlashImages flashImages)
      throws MobileHarnessException, InterruptedException {
    if (spec.getGkiInfoTxt().isEmpty() || !localFileUtil.isFileExist(spec.getGkiInfoTxt())) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("Will not validate kernel version since gki-info.txt is not provided.");
      return;
    }
    File gkiInfoTxt = new File(spec.getGkiInfoTxt());
    String[] bootImgKernelVersion = getKernelVersionFromImage(testInfo, gkiInfoTxt);
    if (bootImgKernelVersion == null) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_GET_KERNEL_VERSION_ERROR,
          "Could not extract kernel version from " + gkiInfoTxt.getName());
    }

    if (flashImages.vendorBootImage() == null
        && flashImages.vendorKernelBootImage() == null
        && flashImages.initramfsImage() == null) {
      String[] deviceKernelVersion = getDeviceKernelVersion(testInfo, deviceId);
      if (deviceKernelVersion != null) {
        validateKernelCompatibility(
            testInfo, spec, "Device", deviceKernelVersion, bootImgKernelVersion);
      } else {
        // Some device has non-compatible kernel version string
        if (spec.getValidateDeviceKernelVersion()) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_GET_KERNEL_VERSION_ERROR,
              "Could not get kernel version from " + deviceId);
        }
      }
    }

    ImmutableList<Optional<File>> imagesToValidate =
        ImmutableList.of(
            flashImages.vendorBootImage(),
            flashImages.vendorKernelBootImage(),
            flashImages.initramfsImage(),
            flashImages.vendorDlkmImage(),
            flashImages.systemDlkmImage());

    for (Optional<File> image : imagesToValidate) {
      if (image.isPresent() && localFileUtil.isFileExist(image.get().toPath())) {
        String[] imgKernelVersion = getKernelVersionFromImage(testInfo, image.get());
        if (imgKernelVersion == null) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_GET_KERNEL_VERSION_ERROR,
              "Could not extract kernel version from " + image.get().getName());
        }
        validateKernelCompatibility(
            testInfo, spec, image.get().getName(), imgKernelVersion, bootImgKernelVersion);
      }
    }
  }

  @VisibleForTesting
  protected void validateKernelCompatibility(
      TestInfo testInfo,
      AndroidFlashGkiDecoratorSpec spec,
      String imageName,
      String[] imageVersion,
      String[] bootVersion)
      throws MobileHarnessException {
    if (!bootVersion[0].equals(imageVersion[0]) || !bootVersion[2].equals(imageVersion[2])) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_KERNEL_NOT_COMPATIBLE,
          String.format(
              "Boot image kernel version %s doesn't match %s kernel version %s",
              Arrays.toString(bootVersion), imageName, Arrays.toString(imageVersion)));
    }
    if (!spec.getValidateKernelVersionPatchLevel()) {
      return;
    }

    try {
      int bootPatchLevel = Integer.parseInt(bootVersion[1]);
      int imagePatchLevel = Integer.parseInt(imageVersion[1]);
      if (bootPatchLevel < imagePatchLevel) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_KERNEL_NOT_COMPATIBLE,
            String.format(
                "Boot image kernel patch level %d is less than %s kernel patch" + " level %d",
                bootPatchLevel, imageName, imagePatchLevel));
      }
    } catch (NumberFormatException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("Could not parse patch level: %s or %s", bootVersion[1], imageVersion[1]);
    }
  }

  @VisibleForTesting
  @Nullable
  protected String[] getKernelVersionFromImage(TestInfo testInfo, File image)
      throws InterruptedException {
    // Android kernel version pattern in the boot/vendor_boot/system_dlkm/vendor_dlkm image
    // files
    // For example: 6.12.63-android16-6-gf2758f0da7bc-ab14892992-4k
    CommandResult result = null;
    try {
      String cmd =
          String.format(
              "strings %s | grep -E -o"
                  + " '[0-9].[0-9]+.[0-9]+(-rc[0-9]+)?-(android|mainline).*-ab.*' | awk"
                  + " 'length($0) > max {max = length($0); line = $0} END {print line}'",
              image.getAbsolutePath());
      result =
          commandExecutor.exec(Command.of("/bin/bash", "-c", cmd).timeout(Duration.ofMinutes(5)));
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log("Could not extract kernel version from %s", image.getAbsolutePath());
      return null;
    }

    return parseKernelVersion(testInfo, image.getAbsolutePath(), result.stdout().trim());
  }

  @VisibleForTesting
  @Nullable
  protected String[] getDeviceKernelVersion(TestInfo testInfo, String deviceId)
      throws MobileHarnessException, InterruptedException {
    String kernelInfo = adb.runShellWithRetry(deviceId, "uname -a");
    if (kernelInfo.isEmpty()) {
      return null;
    }
    return parseKernelVersion(testInfo, deviceId, kernelInfo.trim());
  }

  @VisibleForTesting
  @Nullable
  protected String[] parseKernelVersion(
      TestInfo testInfo, String source, String kernelVersionString) {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("%s has kernel version string: %s", source, kernelVersionString);
    Matcher matcher = KERNEL_VERSION_PATTERN.matcher(kernelVersionString);
    if (matcher.find()) {
      String kernelVersion = matcher.group(3);
      if (!kernelVersion.equals("mainline")) {
        kernelVersion += "-" + matcher.group(1);
      } else {
        kernelVersion = "android-mainline";
      }
      String patchLevel = matcher.group(2);
      String buildId = matcher.group(4);
      String pageSize = "16k";
      if (kernelVersionString.contains("-4k")
          || kernelVersion.startsWith("android12-")
          || kernelVersion.startsWith("android13-")
          || kernelVersion.startsWith("android14-")) {
        pageSize = "4k";
      }
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "%s has kernel version: %s, patchLevel: %s, buildId: %s, pageSize: %s",
              source, kernelVersion, patchLevel, buildId, pageSize);
      return new String[] {kernelVersion, patchLevel, pageSize};
    }
    testInfo
        .log()
        .atWarning()
        .alsoTo(logger)
        .log("%s has invalid kernel version string: %s", source, kernelVersionString);
    return null;
  }

  private void flashGki(
      TestInfo testInfo, AndroidFlashGkiDecoratorSpec spec, FlashImages flashImages)
      throws MobileHarnessException, InterruptedException {
    Device device = getDevice();
    String deviceId = device.getDeviceId();
    String physicalSerial = deviceId;
    boolean flashLogicalPartitionFirst = spec.getFlashLogicalPartitionFirst();
    if (isRemoteProxyDevice(deviceId)) {
      physicalSerial = androidAdbUtil.getProperty(deviceId, AndroidProperty.SERIAL);
    }
    testInfo.log().atInfo().alsoTo(logger).log("Flashing GKI images");
    if (androidSystemStateUtil.isOnline(deviceId)) {
      if (spec.getDisableVerity()) {
        androidSystemStateUtil.becomeRoot(deviceId);
        PostSetDmVerityDeviceOp unusedOp =
            androidSystemSettingUtil.setDmVerityChecking(deviceId, /* enabled= */ false);
      }
    } else {
      // Only flash logical partition first if the device boot up in user space properly before we
      // start.
      flashLogicalPartitionFirst = false;
    }

    // Ensure snapuserd isn't running
    waitForSnapuserd(deviceId, Duration.ofMinutes(10));

    boolean quotaAcquired = false;
    testInfo.log().atInfo().alsoTo(logger).log("Acquiring fastboot flash device quota.");
    try (Lease ignored = quotaManager.acquire(QuotaKey.FASTBOOT_FLASH_DEVICE, 1)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Device %s acquired a flash quota. Allocated %d tokens.", physicalSerial, 1);
      quotaAcquired = true;
      cacheDevice(deviceId, Duration.ofMinutes(30).toMillis());

      testInfo.log().atInfo().alsoTo(logger).log("Rebooting to bootloader");
      androidSystemStateUtil.reboot(deviceId, RebootMode.BOOTLOADER);
      // Wait for device to show in pontis.
      sleeper.sleep(Duration.ofSeconds(10));

      String fastbootSerial = findFastbootSerial(testInfo, deviceId, physicalSerial);
      if (spec.getWipeDeviceBeforeGkiFlash()) {
        apkInstaller.clearInstalledApkProperties(device);
        String unused = fastboot.wipe(fastbootSerial, null);
        sleeper.sleep(STATE_STABILIZATION_WAIT_TIME);
      }
      if (spec.getOemDisableVerity()) {
        // Run oem disable-verity command at best effort
        try {
          String unused = executeFastbootCommand(testInfo, fastbootSerial, "oem", "disable-verity");
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log("fastboot oem disable-verity not supported.");
        }
      }
      if (spec.getDisableVerification()) {
        // Run fastboot oem disable-verification command at best effort
        try {
          String unused =
              executeFastbootCommand(testInfo, fastbootSerial, "oem", "disable-verification");
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log("fastboot oem disable-verification not supported.");
          if (!flashOptions.contains("--disable-verification")) {
            flashOptions.add("--disable-verification");
          }
        }
      }

      // Run additional fastboot command
      for (String cmd : spec.getAdditionalFastbootCommandList()) {
        cmd = cmd.trim();
        if (cmd.isEmpty()) {
          continue;
        }
        try {
          String unused = executeFastbootCommand(testInfo, fastbootSerial, cmd.split("\\s+"));
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .withCause(e)
              .log("fastboot command %s is not supported.", cmd);
        }
      }

      if (flashLogicalPartitionFirst) {
        flashLogicalPartitions(testInfo, spec, fastbootSerial, flashImages);
      }

      String unused2 =
          executeFastbootCommand(
              testInfo, fastbootSerial, "flash", "boot", flashImages.gkiBootImage().getPath());
      if (flashImages.vendorBootImage().isPresent()) {
        String unused =
            executeFastbootCommand(
                testInfo,
                fastbootSerial,
                "flash",
                "vendor_boot",
                flashImages.vendorBootImage().get().getPath());
      }
      if (flashImages.vendorKernelBootImage().isPresent()) {
        String unused =
            executeFastbootCommand(
                testInfo,
                fastbootSerial,
                "flash",
                "vendor_kernel_boot",
                flashImages.vendorKernelBootImage().get().getPath());
      }
      if (flashImages.dtbImage().isPresent() && flashImages.initramfsImage().isPresent()) {
        String unused =
            executeFastbootCommand(
                testInfo,
                fastbootSerial,
                "flash",
                "--dtb",
                flashImages.dtbImage().get().getPath(),
                "vendor_boot:dlkm",
                flashImages.initramfsImage().get().getPath());
      }
      if (flashImages.dtboImage().isPresent()) {
        String unused =
            executeFastbootCommand(
                testInfo, fastbootSerial, "flash", "dtbo", flashImages.dtboImage().get().getPath());
      }
      if (flashImages.vbmetaImage().isPresent()) {
        String unused =
            executeFastbootCommand(
                testInfo,
                fastbootSerial,
                "flash",
                "vbmeta",
                flashImages.vbmetaImage().get().getPath());
      }

      if (!flashLogicalPartitionFirst) {
        flashLogicalPartitions(testInfo, spec, fastbootSerial, flashImages);
      }

      if (spec.getPostRebootDeviceIntoUserSpace()) {
        // Wait some time after flashing the image.
        sleeper.sleep(STATE_STABILIZATION_WAIT_TIME);
        fastboot.reboot(fastbootSerial);
        initializeDevice(device, testInfo);
        if (androidSystemStateUtil.becomeRoot(deviceId)) {
          androidSystemSettingUtil.setSystemTimeToHost(
              deviceId, androidSystemSettingUtil.getDeviceSdkVersion(deviceId));
        }
        // postBootSetup
        androidSystemStateUtil.becomeRoot(deviceId);
      }
    } catch (InterruptedException e) {
      if (quotaAcquired) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Failed to acquire fastboot flash device quota.");
      }
      throw e;
    }
  }

  private void waitForSnapuserd(String deviceId, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    long startTime = InstantSource.system().instant().toEpochMilli();
    while (Instant.now()
        .minusMillis(startTime)
        .isBefore(Instant.ofEpochMilli(timeout.toMillis()))) {
      try {
        String unused = adb.runShell(deviceId, "ps -ef | grep [s]napuserd");
        sleeper.sleep(Duration.ofMillis(2500));
      } catch (MobileHarnessException e) {
        // snapuserd is not running.
        return;
      }
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_SNAPUSERD_NOT_STOPPED,
        "snapuserd is still running after timeout.");
  }

  void cacheDevice(String deviceId, long expireTimeMs) {
    deviceCache.cache(
        deviceId, getDevice().getClass().getSimpleName(), Duration.ofMillis(expireTimeMs));
  }

  void invalidateCacheDevice(String deviceId) {
    deviceCache.invalidateCache(deviceId);
  }

  private String findFastbootSerial(TestInfo testInfo, String deviceId, String physicalSerial)
      throws MobileHarnessException, InterruptedException {
    if (!isRemoteProxyDevice(deviceId)) {
      return physicalSerial;
    }

    String fastbootSerial = null;
    List<String> stdoutLines = new ArrayList<>();
    Command command =
        Command.of("pontis", "trackdevices")
            .timeout(Duration.ofSeconds(5))
            .onStdout(LineCallback.does(stdoutLines::add))
            .redirectStderr(false);
    try {
      CommandResult unusedResult = commandExecutor.exec(command);
    } catch (CommandTimeoutException e) {
      // Process killed after timeout, normal behavior.
      for (int i = 0; i + 2 < stdoutLines.size(); i += 3) {
        String label = stdoutLines.get(i).trim();
        String serial = stdoutLines.get(i + 1).trim(); // physical serial
        String address = stdoutLines.get(i + 2).trim();
        if (serial.equals(physicalSerial) && label.contains("fastboot")) {
          testInfo.log().atInfo().alsoTo(logger).log("  Device address: %s", address);
          Matcher matcher = ADDRESS_PATTERN.matcher(address);
          if (matcher.matches()) {
            fastbootSerial = "tcp:127.0.0.1:" + matcher.group(1);
            testInfo.log().atInfo().alsoTo(logger).log("  Fastboot serial: %s", fastbootSerial);
            break;
          }
        }
      }
    } catch (CommandException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to start pontis trackdevices process");
    }
    if (fastbootSerial == null) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_FASTBOOT_SERIAL_NOT_FOUND,
          "Failed to find fastboot serial for device " + physicalSerial);
    }
    return fastbootSerial;
  }

  private static boolean isRemoteProxyDevice(String deviceId) {
    return deviceId.contains("localhost") || deviceId.contains("127.0.0.1");
  }

  private boolean isInFastbootdState(String fastbootSerial)
      throws MobileHarnessException, InterruptedException {
    String output = fastboot.getVar(fastbootSerial, FastbootProperty.IS_USERSPACE).trim();
    return output.contains("yes");
  }

  private void initializeDevice(Device device, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    deviceInitializationStep.initializeDevice(
        device,
        testInfo,
        /* skipUpdateDevicePropertiesAndDimensions= */ false,
        /* skipCacheDevice= */ true,
        InitializationArgs.builder().setSkipSetupWizard(true).build());
  }

  private void flashLogicalPartitions(
      TestInfo testInfo,
      AndroidFlashGkiDecoratorSpec spec,
      String fastbootSerial,
      FlashImages flashImages)
      throws MobileHarnessException, InterruptedException {
    List<String> additionalFastbootdCommands = spec.getAdditionalFastbootdCommandList();
    if (flashImages.vendorDlkmImage().isEmpty()
        && flashImages.systemDlkmImage().isEmpty()
        && additionalFastbootdCommands.isEmpty()) {
      return;
    }

    if (flashImages.vendorDlkmImage().isPresent()) {
      if (spec.getSupportFastbootd() && !isInFastbootdState(fastbootSerial)) {
        String unused = executeFastbootCommand(testInfo, fastbootSerial, "reboot", "fastboot");
      }
      String unused =
          executeFastbootCommand(
              testInfo,
              fastbootSerial,
              "flash",
              "vendor_dlkm",
              flashImages.vendorDlkmImage().get().getPath());
    }
    if (flashImages.systemDlkmImage().isPresent()) {
      if (spec.getSupportFastbootd() && !isInFastbootdState(fastbootSerial)) {
        String unused = executeFastbootCommand(testInfo, fastbootSerial, "reboot", "fastboot");
      }
      String unused =
          executeFastbootCommand(
              testInfo,
              fastbootSerial,
              "flash",
              "system_dlkm",
              flashImages.systemDlkmImage().get().getPath());
    }

    // Run additional fastbootd commands.
    if (!additionalFastbootdCommands.isEmpty()) {
      if (spec.getSupportFastbootd() && !isInFastbootdState(fastbootSerial)) {
        String unused = executeFastbootCommand(testInfo, fastbootSerial, "reboot", "fastboot");
      }
      for (String cmd : additionalFastbootdCommands) {
        cmd = cmd.trim();
        if (cmd.isEmpty()) {
          continue;
        }
        try {
          String unused2 = executeFastbootCommand(testInfo, fastbootSerial, cmd.split("\\s+"));
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .withCause(e)
              .log("fastbootd command %s is not supported.", cmd);
        }
      }
    }

    String unused2 = executeFastbootCommand(testInfo, fastbootSerial, "reboot", "bootloader");
    // Wait for device to show in pontis.
    sleeper.sleep(Duration.ofSeconds(10));
  }

  private File getRequestedFile(TestInfo testInfo, String requestedFileName, File sourceFile)
      throws MobileHarnessException, InterruptedException {
    Path requestedFilePath = null;
    String baseFileName = new File(requestedFileName).getName();

    if (sourceFile.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
      Path tmpDirPath = Path.of(testInfo.getTmpFileDir());
      Path destDirPath = tmpDirPath.resolve(sourceFile.getName() + "_zip");
      localFileUtil.prepareDir(destDirPath);
      localFileUtil.unzipFile(sourceFile.toPath(), destDirPath);
      requestedFilePath = destDirPath.resolve(requestedFileName);
      if (!localFileUtil.isFileExist(requestedFilePath)) {
        // Search for the file with basename regex within the zip archive.
        Pattern pattern = Pattern.compile(baseFileName);
        List<File> foundFiles =
            localFileUtil.listFiles(
                sourceFile.getPath(),
                /* recursively= */ true,
                file -> pattern.matcher(file.getName()).matches());
        if (foundFiles.isEmpty()) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_FILE_NOT_FOUND,
              String.format(
                  "Could not find any file matching regex '%s' within zip archive '%s'",
                  baseFileName, sourceFile.getPath()));
        }
        requestedFilePath = foundFiles.get(0).toPath();
      }
    } else if (localFileUtil.isDirExist(sourceFile.getPath())) {
      Pattern pattern = Pattern.compile(baseFileName);
      List<File> foundFiles =
          localFileUtil.listFiles(
              sourceFile.getPath(),
              /* recursively= */ true,
              file -> pattern.matcher(file.getName()).matches());
      if (foundFiles.isEmpty()) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_FILE_NOT_FOUND,
            String.format(
                "Could not find any file matching regex '%s' within directory '%s'",
                baseFileName, sourceFile.getPath()));
      }
      requestedFilePath = foundFiles.get(0).toPath();
    } else {
      requestedFilePath = sourceFile.toPath();
    }
    if (requestedFilePath == null || !localFileUtil.isFileExist(requestedFilePath)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_FILE_NOT_FOUND,
          String.format(
              "Requested file with file_name %s does not exist in provided %s.",
              requestedFileName, sourceFile.getPath()));
    }
    return requestedFilePath.toFile();
  }

  private CommandResult executeHostCommand(TestInfo testInfo, final String commandString)
      throws MobileHarnessException, InterruptedException {
    Command command = Command.of(commandString.split("\\s+")).timeout(Duration.ofMinutes(5));
    try {
      CommandResult result = commandExecutor.exec(command);
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Command %s finished successfully, stdout = [%s].",
              commandString, result.stdout().trim());
      return result;
    } catch (CommandFailureException e) {
      CommandResult result = e.result();
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_EXECUTE_HOST_COMMAND_ERROR,
          String.format(
              "Command %s failed, stdout = [%s], stderr = [%s].",
              commandString, result.stdout().trim(), result.stderr().trim()),
          e);
    }
  }

  private String executeFastbootCommand(TestInfo testInfo, String fastbootSerial, String... cmdArgs)
      throws MobileHarnessException, InterruptedException {
    List<String> fastbootCmdArgs = new ArrayList<>();
    if (cmdArgs[0].equals("flash")) {
      fastbootCmdArgs.addAll(flashOptions);
    }
    Collections.addAll(fastbootCmdArgs, cmdArgs);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Executing fastboot command: %s on %s",
            String.join(" ", fastbootCmdArgs), fastbootSerial);
    String output =
        fastboot.runWithRetry(
            fastbootSerial,
            fastbootCmdArgs.toArray(new String[0]),
            /* timeout= */ Duration.ofMinutes(10),
            false);
    if (output.contains("FAILED")) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_FLASH_GKI_DECORATOR_EXECUTE_FASTBOOT_COMMAND_ERROR,
          String.format("Fastboot return 0 but output indicates failure: %s", output));
    }
    return output;
  }
}
