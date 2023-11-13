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

package com.google.devtools.deviceaction.framework.devices;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.devtools.deviceaction.common.utils.Constants.APEX_SUFFIX;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.isDurationPositive;
import static com.google.devtools.mobileharness.shared.util.time.TimeUtils.toJavaDuration;
import static java.util.stream.Stream.concat;
import static org.apache.commons.lang3.ArrayUtils.nullToEmpty;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.annotations.Annotations.Configurable;
import com.google.devtools.deviceaction.common.annotations.Annotations.SpecValue;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.utils.BundletoolUtil;
import com.google.devtools.deviceaction.common.utils.LazyCached;
import com.google.devtools.deviceaction.framework.proto.AndroidPhoneSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceType;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.InstallCmdArgs;
import com.google.devtools.mobileharness.platform.android.packagemanager.ModuleInfo;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.RebootMode;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.PostSetDmVerityDeviceOp;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.commons.lang3.ArrayUtils;

/** A {@link Device} class for Android phones. */
@Configurable(specType = AndroidPhoneSpec.class)
public class AndroidPhone implements Device {

  private static final String STAGED = "--staged";
  private static final String STAGED_READY_TIMEOUT = "--staged-ready-timeout";
  private static final String TIMEOUT_MILLIS_FLAG = "--timeout-millis=";
  private static final String REBOOT_SIGN = "INFO: Please reboot device to complete installation.";
  private static final String SUCCESS_SIGN = "Success";
  private static final String GOOGLE = "google";
  private static final String DEV_KEYS = "dev-keys";
  private static final String EXECUTION_ERROR = "EXECUTION_ERROR";
  private static final String USERDEBUG = "userdebug";
  private static final String LOCALHOST_PREFIX = "localhost:";

  @VisibleForTesting static final Duration DEFAULT_DEVICE_READY_TIMEOUT = Duration.ofMinutes(5);
  @VisibleForTesting static final Duration DEFAULT_AWAIT_FOR_DISCONNECT = Duration.ofSeconds(30);
  @VisibleForTesting static final Duration DEFAULT_REBOOT_TIMEOUT = Duration.ofMinutes(2);
  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidFileUtil androidFileUtil;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;

  private final AndroidSystemStateUtil androidSystemStateUtil;
  private final BundletoolUtil bundletoolUtil;

  private final Sleeper sleeper;

  private final String uuid;

  private final AndroidPhoneSpec spec;

  private final LazyCached<Path> deviceSpecFileProvider =
      new LazyCached<Path>() {
        @Override
        protected Path provide() throws DeviceActionException, InterruptedException {
          return bundletoolUtil.generateDeviceSpecFile(uuid);
        }
      };

  private final LoadingCache<AndroidProperty, String> propertyCache =
      CacheBuilder.newBuilder()
          .build(
              new CacheLoader<AndroidProperty, String>() {
                @Override
                public String load(AndroidProperty property)
                    throws DeviceActionException, InterruptedException {
                  try {
                    return androidAdbUtil.getProperty(uuid, property);
                  } catch (MobileHarnessException e) {
                    throw new DeviceActionException(e, "Failed to get the property %s.", property);
                  }
                }
              });

  protected AndroidPhone(
      AndroidAdbUtil androidAdbUtil,
      AndroidFileUtil androidFileUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidSystemStateUtil androidSystemStateUtil,
      BundletoolUtil bundletoolUtil,
      Sleeper sleeper,
      String uuid,
      AndroidPhoneSpec deviceSpec) {
    this.androidAdbUtil = androidAdbUtil;
    this.androidFileUtil = androidFileUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.androidSystemStateUtil = androidSystemStateUtil;
    this.bundletoolUtil = bundletoolUtil;
    this.sleeper = sleeper;
    this.uuid = uuid;
    this.spec = deviceSpec;
  }

  @Override
  public String getUuid() {
    return uuid;
  }

  @Override
  public DeviceType getDeviceType() {
    return DeviceType.ANDROID_PHONE;
  }

  public SortedSet<PackageInfo> listPackages() throws DeviceActionException, InterruptedException {
    try {
      return androidPackageManagerUtil.listPackageInfos(uuid);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to list the packages.");
    }
  }

  public SortedSet<PackageInfo> listApexPackages()
      throws DeviceActionException, InterruptedException {
    try {
      return androidPackageManagerUtil.listApexPackageInfos(uuid);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to list the apex packages.");
    }
  }

  public SortedSet<ModuleInfo> listModules() throws DeviceActionException, InterruptedException {
    try {
      return androidPackageManagerUtil.listModuleInfos(uuid);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to list the modules.");
    }
  }

  public Path getDeviceSpecFilePath() throws DeviceActionException, InterruptedException {
    return deviceSpecFileProvider.call();
  }

  public int getSdkVersion() throws DeviceActionException {
    return Integer.parseInt(getProperty(AndroidProperty.SDK_VERSION));
  }

  public boolean isUserdebug() throws DeviceActionException {
    return Ascii.equalsIgnoreCase(getProperty(AndroidProperty.BUILD_TYPE), USERDEBUG);
  }

  /**
   * Removes all files and directories that match the {@code fileOrDirPathPattern} regex pattern.
   */
  public void removeFiles(String fileOrDirPathPattern)
      throws DeviceActionException, InterruptedException {
    try {
      androidFileUtil.removeFiles(uuid, fileOrDirPathPattern);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to remove the file.");
    }
  }

  public SortedSet<String> listFiles(String filePath)
      throws DeviceActionException, InterruptedException {
    try {
      return androidFileUtil.listFilesInOrder(uuid, filePath);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to list the files.");
    }
  }

  public ImmutableList<String> getAllInstalledPaths(String packageName)
      throws DeviceActionException, InterruptedException {
    try {
      return androidPackageManagerUtil.getAllInstalledPaths(
          UtilArgs.builder().setSerial(uuid).build(), packageName);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to get path for package %s.", packageName);
    }
  }

  /** Installs apk or apex packages. Returns {@code true} if reboot is needed. */
  public boolean installPackages(Multimap<String, File> packageFiles, String... extraArgs)
      throws DeviceActionException, InterruptedException {
    Multimap<String, String> packageMap =
        Multimaps.transformValues(packageFiles, File::getAbsolutePath);
    ImmutableList.Builder<String> extraArgsBuilder = ImmutableList.builder();
    extraArgsBuilder.add(extraArgs);
    boolean reboot = containsApex(packageMap.values());
    if (reboot) {
      extraArgsBuilder.add(STAGED);
    }
    Duration stagedReady = stageReadyTimeout();
    if (isDurationPositive(stagedReady)) {
      extraArgsBuilder.add(STAGED_READY_TIMEOUT, String.valueOf(stagedReady.toMillis()));
    }
    InstallCmdArgs installCmdArgs =
        InstallCmdArgs.builder().setExtraArgs(extraArgsBuilder.build()).build();

    try {
      androidPackageManagerUtil.installMultiPackage(
          UtilArgs.builder().setSerial(uuid).setSdkVersion(getSdkVersion()).build(),
          installCmdArgs,
          packageMap,
          positiveOrElse(extraWaitForStaging(), /* elseValue= */ null),
          /* installTimeout= */ null);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to install packages %s.", packageMap);
    }
    return reboot;
  }

  /** Installs packages from apks. Returns {@code true} if reboot is needed. */
  public boolean installBundledPackages(List<File> apksList, String... extraArgs)
      throws DeviceActionException, InterruptedException {
    extraArgs = addArgsForInstallMultiApks(extraArgs);
    return needReboot(bundletoolUtil.installMultiApks(uuid, apksList, extraArgs));
  }

  /** Installs zipped train. Returns {@code true} if reboot is needed. */
  public boolean installZippedTrain(File train, String... extraArgs)
      throws DeviceActionException, InterruptedException {
    extraArgs = addArgsForInstallMultiApks(extraArgs);
    return needReboot(bundletoolUtil.installApksZip(uuid, train, extraArgs));
  }

  /** Fully reboots the device until it gets ready. */
  public void reboot() throws DeviceActionException, InterruptedException {
    reboot(rebootTimeout());
  }

  /** Fully reboots the device until it gets ready. */
  public void reboot(Duration timeout) throws DeviceActionException, InterruptedException {
    reboot(RebootMode.SYSTEM_IMAGE, timeout);
  }

  /** Reboots the device to a specific mode and waits of the completeness. */
  public void reboot(RebootMode mode) throws DeviceActionException, InterruptedException {
    reboot(mode, rebootTimeout());
  }

  /** Reboots the device to a specific mode and waits of the completeness. */
  private void reboot(RebootMode mode, Duration timeout)
      throws DeviceActionException, InterruptedException {
    try {
      androidSystemStateUtil.reboot(uuid, mode);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to reboot %s to %s", uuid, mode);
    }
    waitForDisconnect(rebootAwait());
    waitUntilReady(mode, timeout);
  }

  public void enableTestharness() throws DeviceActionException, InterruptedException {
    try {
      Duration awaitTime = positiveOrElse(testharnessBootAwait(), /* elseValue= */ null);
      androidSystemStateUtil.factoryResetViaTestHarness(uuid, awaitTime);
      // We also do the extra wait for a local emulator, although it is unnecessary because we don't
      // have a good way to tell the proxy mode from a local emulator.
      if (isProxyModeOrLocalEmulator() && isDurationPositive(extraWaitForProxyMode())) {
        sleeper.sleep(extraWaitForProxyMode());
      }
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to enable testharness.");
    }
    waitUntilReady(testharnessBootTimeout());
  }

  /**
   * Restarts the Zygote process.
   *
   * <p>Essentially, it is executing adb commands
   *
   * <pre>{@code adb shell stop && adb shell start}</pre>
   */
  public void softReboot() throws InterruptedException, DeviceActionException {
    try {
      androidSystemStateUtil.softReboot(uuid);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to soft reboot %s", uuid);
    }
    // Wait for the device to be disconnected after reboot command.
    sleeper.sleep(positiveOrElse(rebootAwait(), DEFAULT_AWAIT_FOR_DISCONNECT));
    waitUntilReady(rebootTimeout());
  }

  /** Sideloads the OTA package to the device. */
  public void sideload(File otaPackage, Duration timeout, Duration waitTime)
      throws DeviceActionException, InterruptedException {
    try {
      androidSystemStateUtil.sideload(uuid, otaPackage, timeout, waitTime);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to sideload %s to %s", otaPackage, uuid);
    }
  }

  /** Waits for the device fully ready. */
  public void waitUntilReady() throws DeviceActionException, InterruptedException {
    waitUntilReady(rebootTimeout());
  }

  /** Waits for the device fully ready. */
  public void waitUntilReady(Duration timeout) throws DeviceActionException, InterruptedException {
    waitUntilReady(RebootMode.SYSTEM_IMAGE, timeout);
  }

  /** Waits for the reboot complete. */
  public void waitUntilReady(RebootMode mode) throws DeviceActionException, InterruptedException {
    waitUntilReady(mode, rebootTimeout());
  }

  /**
   * Waits for the reboot complete.
   *
   * <p>If the mode is {@link RebootMode.SYSTEM_IMAGE}, wait until the device is fully ready.
   */
  public void waitUntilReady(RebootMode mode, Duration timeout)
      throws DeviceActionException, InterruptedException {
    try {
      switch (mode) {
          // Handle bootloader case using fastboot.
        case BOOTLOADER:
          return;
        case RECOVERY:
        case SIDELOAD:
        case SIDELOAD_AUTO_REBOOT:
          androidSystemStateUtil.waitForState(
              uuid, mode.getTargetState(), positiveOrElse(timeout, DEFAULT_REBOOT_TIMEOUT));
          return;
        case SYSTEM_IMAGE:
          androidSystemStateUtil.waitForState(
              uuid, mode.getTargetState(), positiveOrElse(timeout, DEFAULT_REBOOT_TIMEOUT));
          androidSystemStateUtil.waitUntilReady(
              uuid, positiveOrElse(timeout, DEFAULT_DEVICE_READY_TIMEOUT));
          return;
      }
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to reboot device %s to %s", uuid, mode);
    }
  }

  public void becomeRoot() throws DeviceActionException, InterruptedException {
    try {
      androidSystemStateUtil.becomeRoot(uuid);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Fail to root.");
    }
  }

  public void push(Path srcOnHost, Path desOnDevice)
      throws DeviceActionException, InterruptedException {
    try {
      androidFileUtil.push(uuid, getSdkVersion(), srcOnHost.toString(), desOnDevice.toString());
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(
          e, "Failed to push %s to %s on %s", srcOnHost, desOnDevice, uuid);
    }
  }

  public void remount() throws DeviceActionException, InterruptedException {
    becomeRoot();
    try {
      androidFileUtil.remount(uuid, /* checkResults= */ true);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to remount");
    }
  }

  /** Disables verity and reboots if needed. */
  public void disableVerity() throws DeviceActionException, InterruptedException {
    PostSetDmVerityDeviceOp operation;
    try {
      operation = androidSystemSettingUtil.setDmVerityChecking(uuid, false);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to disable verity.");
    }
    if (operation.equals(PostSetDmVerityDeviceOp.REBOOT)) {
      reboot();
    }
  }

  /** Extracts installation files for the device from an apks file. */
  public ImmutableList<File> extractFilesFromApks(File packageFile)
      throws DeviceActionException, InterruptedException {
    Path deviceSpecFilePath = getDeviceSpecFilePath();
    File extractDir = bundletoolUtil.extractApks(packageFile, deviceSpecFilePath).toFile();
    File[] extracted = nullToEmpty(extractDir.listFiles(), File[].class);
    return ImmutableList.copyOf(extracted);
  }

  public boolean devKeySigned() throws DeviceActionException {
    return Ascii.equalsIgnoreCase(brand(), GOOGLE)
        && Ascii.equalsIgnoreCase(getProperty(AndroidProperty.SIGN), DEV_KEYS);
  }

  /**
   * Gets a map of package infos for packages installed on the device (include both apk and apex).
   *
   * @return a map from the package names to the package info.
   */
  public ImmutableMap<String, PackageInfo> getInstalledPackageMap()
      throws DeviceActionException, InterruptedException {
    return concat(listPackages().stream(), listApexPackages().stream())
        .collect(toImmutableMap(PackageInfo::packageName, Function.identity()));
  }

  @SpecValue(field = "brand")
  public String brand() {
    return spec.getBrand();
  }

  @SpecValue(field = "reboot_await")
  public Duration rebootAwait() {
    return toJavaDuration(spec.getRebootAwait());
  }

  @SpecValue(field = "reboot_timeout")
  public Duration rebootTimeout() {
    return toJavaDuration(spec.getRebootTimeout());
  }

  @SpecValue(field = "testharness_boot_await")
  public Duration testharnessBootAwait() {
    return toJavaDuration(spec.getTestharnessBootAwait());
  }

  @SpecValue(field = "testharness_boot_timeout")
  public Duration testharnessBootTimeout() {
    return toJavaDuration(spec.getTestharnessBootTimeout());
  }

  @SpecValue(field = "stage_ready_timeout")
  public Duration stageReadyTimeout() {
    return toJavaDuration(spec.getStagedReadyTimeout());
  }

  @SpecValue(field = "extra_wait_for_staging")
  public Duration extraWaitForStaging() {
    return toJavaDuration(spec.getExtraWaitForStaging());
  }

  @SpecValue(field = "extra_wait_for_proxy_mode")
  public Duration extraWaitForProxyMode() {
    return toJavaDuration(spec.getExtraWaitForProxyMode());
  }

  @SpecValue(field = "need_disable_package_cache")
  public boolean needDisablePackageCache() {
    return spec.getNeedDisablePackageCache();
  }

  @SpecValue(field = "reload_by_factory_reset")
  public boolean reloadByFactoryReset() {
    return spec.getReloadByFactoryReset();
  }

  @SpecValue(field = "module_dir_on_device")
  public ImmutableMap<String, String> moduleDirOnDevice() {
    return ImmutableMap.copyOf(spec.getModuleDirOnDeviceMap());
  }

  @Nullable
  private static Duration positiveOrElse(Duration duration, @Nullable Duration elseValue) {
    return Optional.of(duration).filter(TimeUtils::isDurationPositive).orElse(elseValue);
  }

  private static boolean containsApex(Collection<String> values) {
    return values.stream().anyMatch(f -> f.endsWith(APEX_SUFFIX));
  }

  private void waitForDisconnect(Duration timeout)
      throws InterruptedException, DeviceActionException {
    // Wait for the device to be disconnected after reboot command.
    try {
      androidSystemStateUtil.waitForState(
          uuid,
          DeviceConnectionState.DISCONNECT,
          positiveOrElse(timeout, DEFAULT_AWAIT_FOR_DISCONNECT));
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to detect disconnection of %s", uuid);
    }
  }

  private String[] addArgsForInstallMultiApks(String[] extraArgs) throws DeviceActionException {
    extraArgs = ArrayUtils.insert(extraArgs.length, extraArgs, STAGED);
    // Flag --timeout-millis applies to Android 12+
    if (isDurationPositive(stageReadyTimeout())
        && getSdkVersion() >= AndroidVersion.ANDROID_12.getStartSdkVersion()) {
      extraArgs =
          ArrayUtils.insert(
              extraArgs.length, extraArgs, TIMEOUT_MILLIS_FLAG + stageReadyTimeout().toMillis());
    }
    return extraArgs;
  }

  private static boolean needReboot(String output) throws DeviceActionException {
    if (!output.contains(SUCCESS_SIGN)) {
      throw new DeviceActionException("INSTALLATION_ERROR", ErrorType.UNCLASSIFIED, output);
    }
    return output.contains(REBOOT_SIGN);
  }

  private String getProperty(AndroidProperty property) throws DeviceActionException {
    try {
      return propertyCache.get(property);
    } catch (ExecutionException e) {
      throw new DeviceActionException(
          EXECUTION_ERROR, ErrorType.INFRA_ISSUE, "Failed to get cache property.", e);
    }
  }

  // A device shown as localhost:xxx maybe in proxy mode or a local emulator.
  private boolean isProxyModeOrLocalEmulator() {
    return uuid.startsWith(LOCALHOST_PREFIX);
  }
}
