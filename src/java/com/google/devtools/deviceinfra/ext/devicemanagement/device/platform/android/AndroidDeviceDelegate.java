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

package com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.BaseDeviceHelper;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.platform.android.app.ActivityManager;
import com.google.devtools.mobileharness.platform.android.device.AndroidDeviceHelper;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.shared.constant.PackageConstants;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.android.AndroidPackages;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.BaseDevice;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.util.ScreenResolution;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Utility class to control an Android device (including Android real device, emulator). */
public abstract class AndroidDeviceDelegate {
  /**
   * When adding a new decorator, driver or dimension to an Android device via this class, please
   * check the following things in order:
   *
   * <ul>
   *   <li>If the new decorator should be supported by ALL Android root/non-root devices in all lab
   *       types, add it to {@link #basicAndroidDecoratorConfiguration()}.
   *   <li>If the new dimension should be supported by STANDARD Android root/non-root devices in all
   *       lab types, add it to {@link #basicAndroidDimensionConfiguration(boolean)}.
   *   <li>If the new driver should be supported by STANDARD Android root/non-root devices in all
   *       lab types, add it to {@link #basicAndroidDriverConfiguration()}.
   *   <li>If the new decorator, driver or dimension should be supported by STANDARD Android
   *       root/non-root devices based on lab types, add it to {@link
   *       #additionalAndroidDeviceConfiguration(boolean)}.
   *   <li>If the new decorator, driver or dimension should be supported by STANDARD Android rooted
   *       devices only, add it to {@link #rootedAndroidDeviceConfiguration(String)}.
   * </ul>
   *
   * Context:
   *
   * <pre>#1 Currently STANDARD Android devices include AndroidRealDevice and AndroidLocalEmulator.
   * </pre>
   *
   * <pre>#2 ALL Android devices mean all device types extending from AndroidDevice.</pre>
   */
  @ParamAnnotation(
      required = false,
      help =
          "Whether to enable/disable dex pre-verification before running "
              + "each test. By default, it is false so dex_pre_verification will be disabled.")
  @VisibleForTesting
  static final String PARAM_DEX_PRE_VERIFICATION = "dex_pre_verification";

  /** Packages generated by MH that should be cleared before each test */
  @VisibleForTesting
  static final String[] androidUnexpectedPackages =
      new String[] {"com.google.android.apps.internal.statusbarhider"};

  private static final String PROPERTY_NAME_LOCALE_PATTERN = "\\w{2}-\\w{2}";

  @VisibleForTesting static final Duration WAIT_FOR_DEVICE_TIMEOUT = Duration.ofMinutes(15);

  // Cache the device when waitForDevice and waitUntilReady.
  private static final Duration CACHE_TIMEOUT = WAIT_FOR_DEVICE_TIMEOUT.plusMinutes(5);

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String deviceId;

  private final ActivityManager am;
  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidSystemStateUtil androidSystemStateUtil;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final AndroidProcessUtil androidProcessUtil;
  private final AndroidDeviceHelper androidDeviceHelper;
  private final AndroidSystemSpecUtil androidSystemSpecUtil;

  protected final BaseDevice device;

  protected AndroidDeviceDelegate(
      BaseDevice device,
      ActivityManager am,
      AndroidAdbUtil androidAdbUtil,
      AndroidSystemStateUtil androidSystemStateUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidProcessUtil androidProcessUtil,
      AndroidSystemSpecUtil androidSystemSpecUtil) {
    this.device = device;
    this.am = am;
    this.androidAdbUtil = androidAdbUtil;
    this.androidSystemStateUtil = androidSystemStateUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.androidProcessUtil = androidProcessUtil;
    this.deviceId = device.getDeviceId();
    this.androidDeviceHelper = new AndroidDeviceHelper(androidAdbUtil);
    this.androidSystemSpecUtil = androidSystemSpecUtil;
  }

  /** Ensures device is booted up and ready to respond. */
  public void ensureDeviceReady() throws MobileHarnessException, InterruptedException {
    try {
      DeviceCache.getInstance().cache(deviceId, device.getClass().getSimpleName(), CACHE_TIMEOUT);
      androidSystemStateUtil.waitForState(
          deviceId, DeviceConnectionState.DEVICE, WAIT_FOR_DEVICE_TIMEOUT);
      androidSystemStateUtil.waitUntilReady(deviceId);
    } finally {
      DeviceCache.getInstance().invalidateCache(deviceId);
    }
  }

  /**
   * Set up the Android device.
   *
   * @param isRooted whether the device is rooted
   * @param extraDimensions extra dimensions added to the device
   */
  public void setUp(boolean isRooted, @Nullable Multimap<Dimension.Name, String> extraDimensions)
      throws MobileHarnessException, InterruptedException {
    BaseDeviceHelper.setUp(device, BaseDevice.class, extraDimensions);

    // Adds all the system properties to its dimensions.
    androidDeviceHelper.updateAndroidPropertyDimensions(device);

    // Adds language and locale dimension from ActivityManager if any of them is missing.
    checkLocaleLanguageDimensions();

    // Adds system spec dimensions.
    updateSystemSpecDimensions();

    // Adds drivers/decorators only after the properties are read. Because the validators of the
    // drivers/decorators may depend on those properties.
    basicAndroidDeviceConfiguration(isRooted);
    additionalAndroidDeviceConfiguration(ifEnableFullStackFeatures());
    if (isRooted) {
      rootedAndroidDeviceConfiguration(deviceId);
    }
  }

  /** Fetches language and locale if missing. */
  @VisibleForTesting
  void checkLocaleLanguageDimensions() throws InterruptedException {
    boolean hasLoc = hasDimension(AndroidProperty.LOCALE);
    boolean hasLan = hasDimension(AndroidProperty.LANGUAGE);
    if (hasLoc && hasLan) {
      return;
    } else if (hasLoc && updateLanguageDimensionFromLocale()) {
      return;
    } else if (updateLocaleDimensionFromLanguageRegion()) {
      return;
    } else {
      updateLocaleLanguageDimensionsFromAm();
    }
  }

  @VisibleForTesting
  void updateSystemSpecDimensions() throws InterruptedException {
    androidSystemSpecUtil
        .getKernelReleaseNumber(deviceId)
        .ifPresent(this::updateKernelReleaseDimensions);
    androidSystemSpecUtil
        .getDisplayPanelVendor(deviceId)
        .ifPresent(vendor -> device.updateDimension(Dimension.Name.DISPLAY_PANEL_VENDOR, vendor));
  }

  private void updateKernelReleaseDimensions(String kernelReleaseNumber) {
    device.updateDimension(
        Ascii.toLowerCase(Dimension.Name.KERNEL_RELEASE_NUMBER.name()), kernelReleaseNumber);
    device.updateDimension(
        Ascii.toLowerCase(Dimension.Name.IS_GKI_KERNEL.name()),
        AndroidSystemSpecUtil.isGkiKernel(kernelReleaseNumber) ? "true" : "false");
  }

  private boolean hasDimension(AndroidProperty key) {
    ImmutableSet<String> values =
        ImmutableSet.copyOf(device.getDimension(Ascii.toLowerCase(key.name())));
    return !values.isEmpty();
  }

  private boolean updateDimension(AndroidProperty key, String value) {
    ImmutableSet<String> values = androidDeviceHelper.maybeLowerCaseProperty(key, value);
    return device.updateDimension(Ascii.toLowerCase(key.name()), values.toArray(new String[0]));
  }

  /** Updates language from locale. Return true if success. */
  private boolean updateLanguageDimensionFromLocale() {
    ImmutableSet<String> locales =
        ImmutableSet.copyOf(device.getDimension(Ascii.toLowerCase(AndroidProperty.LOCALE.name())));
    String loc = Iterables.get(locales, 0);
    if (loc != null && loc.matches(PROPERTY_NAME_LOCALE_PATTERN)) {
      ActivityManager.Locale locale = ActivityManager.Locale.create(loc);
      updateDimension(AndroidProperty.LANGUAGE, locale.language());
      return true;
    }
    return false;
  }

  /** Fetches locale from activity manager. Return true if success. */
  private boolean updateLocaleLanguageDimensionsFromAm() throws InterruptedException {
    try {
      ActivityManager.Locale locale = am.getLocale(deviceId);
      updateDimension(AndroidProperty.LANGUAGE, locale.language());
      updateDimension(AndroidProperty.LOCALE, locale.locale());
      logger.atInfo().log("Update device %s locale and language from ActivityManager.", deviceId);
      return true;
    } catch (MobileHarnessException ex) {
      logger.atWarning().log(
          "Update device %s Locale and Language fails: %s",
          deviceId, MoreThrowables.shortDebugString(ex));
      return false;
    }
  }

  /** If language and region are not empty, locale = language-region. Return true if success. */
  private boolean updateLocaleDimensionFromLanguageRegion() {
    ImmutableSet<AndroidProperty> keySet =
        ImmutableSet.of(AndroidProperty.LANGUAGE, AndroidProperty.REGION);
    StringBuilder locale = new StringBuilder();
    for (AndroidProperty key : keySet) {
      ImmutableSet<String> values =
          ImmutableSet.copyOf(device.getDimension(Ascii.toLowerCase(key.name())));
      if (!values.isEmpty()) {
        if (locale.length() > 0) {
          locale.append('-');
        }
        locale.append(Iterables.get(values, 0));
      }
    }
    if (locale.toString().matches(PROPERTY_NAME_LOCALE_PATTERN)) {
      updateDimension(AndroidProperty.LOCALE, locale.toString());
      logger.atInfo().log("Update device %s locale from language and region.", deviceId);
      return true;
    }
    return false;
  }

  /**
   * Checks the device and updates device dimension if needed.
   *
   * @return {@code true} if any device dimension is changed.
   */
  public boolean checkDevice() throws MobileHarnessException, InterruptedException {
    updateCustomizedDimensions();

    // Update GMS version in the dimension because specific lab may update gms version when reset
    // the device.
    boolean isDimensionChanged = updateGmsVersionDimensions();

    // Update system property dimension in case some property changed.
    isDimensionChanged =
        androidDeviceHelper.updateAndroidPropertyDimensions(device) || isDimensionChanged;
    if (device.getIntegerProperty(AndroidDeviceHelper.PROPERTY_NAME_CACHED_SDK_VERSION).isEmpty()) {
      return device.removeDimension(Ascii.toLowerCase(AndroidProperty.SDK_VERSION.name()))
          || isDimensionChanged;
    } else if (device.getIntegerProperty(AndroidDeviceHelper.PROPERTY_NAME_CACHED_SDK_VERSION).get()
        >= 18) {
      return updateGServicesAndroidId(deviceId) || isDimensionChanged;
    }
    return isDimensionChanged;
  }

  /**
   * Set up the Android device before running the test.
   *
   * @param testInfo the test info
   * @param isRooted if the device is rooted
   */
  public void preRunTest(TestInfo testInfo, boolean isRooted)
      throws MobileHarnessException, InterruptedException {
    try {
      if (Flags.instance().enableDeviceSystemSettingsChange.getNonNull() && isRooted) {
        boolean enableDexPreVerification =
            testInfo.jobInfo().params().isTrue(PARAM_DEX_PRE_VERIFICATION);
        // Set dex pre-verification only for rooted device.
        androidSystemSettingUtil.setDexPreVerification(deviceId, enableDexPreVerification);
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "%s device %s dex pre-verification",
                enableDexPreVerification ? "Enabled" : "Disabled", deviceId);
      }

      stopUnexpectedProcessOnDevice(testInfo);
      // Before test start, clear the logcat from last run.
      // Keep this clearLog unified to avoid conflict from multiple callers in decorators.
      androidAdbUtil.clearLog(deviceId);
    } catch (MobileHarnessException e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_DEVICE_DELEGATE_TEST_PREP_ERROR, e.getMessage(), e),
              logger);
    }
  }

  /** Returns the cached ABI of the device. */
  public Optional<String> getCachedAbi() {
    return Optional.ofNullable(device.getProperty(AndroidDeviceHelper.PROPERTY_NAME_CACHED_ABI));
  }

  /** Returns the cached sdk version of the device. */
  public Optional<Integer> getCachedSdkVersion() {
    return device.getIntegerProperty(AndroidDeviceHelper.PROPERTY_NAME_CACHED_SDK_VERSION);
  }

  /** Returns the cached screen density of this device. */
  public Optional<Integer> getCachedScreenDensity() {
    return device.getIntegerProperty(AndroidDeviceHelper.PROPERTY_NAME_CACHED_SCREEN_DENSITY);
  }

  /**
   * Updates the dimensions "gservices_android_id".
   *
   * @return true if its value has changed
   */
  @CanIgnoreReturnValue
  public boolean updateGServicesAndroidId(String deviceId) throws InterruptedException {
    return false;
  }

  /**
   * Stop unexpected process which may left from previous test It won't hurt anything if the process
   * is not there on device
   */
  private void stopUnexpectedProcessOnDevice(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    int sdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(deviceId);
    for (String packageName : androidUnexpectedPackages) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Stop package on device %s before test started: %s", deviceId, packageName);
      androidProcessUtil.stopApplication(
          UtilArgs.builder().setSerial(deviceId).setSdkVersion(sdkVersion).build(), packageName);
    }
  }

  /** Gets property value. */
  public String getPropertyValue(String deviceId, AndroidProperty key)
      throws MobileHarnessException, InterruptedException {
    return androidDeviceHelper.getPropertyValue(deviceId, key);
  }

  /**
   * Removes the customized dimension if it is set as default value.
   *
   * @return whether the customized dimension needs to be updated.
   */
  private boolean checkCustomizedDimension(Dimension.Name name) {
    List<String> customizedDimension = device.getDimension(name);
    if (customizedDimension.isEmpty()) {
      return false;
    }
    // User has deleted the dimension from config file
    if (customizedDimension.size() == 1
        && !customizedDimension.get(0).equals(Dimension.Value.CUSTOMIZED_DEFAULT)) {
      device.removeDimension(name);
      return false;
    }
    return true;
  }

  /**
   * Updates dimensions related to application version on device.
   *
   * @return whether the application version is changed.
   */
  private boolean updateAppVersion(String deviceId, Dimension.Name name, String packageName)
      throws InterruptedException {
    String versionName = "";
    try {
      versionName = androidPackageManagerUtil.getAppVersionName(deviceId, packageName);
    } catch (MobileHarnessException e) {
      logger.atInfo().log("Application %s version not found on device %s", packageName, deviceId);
    }
    // if the application is not installed, the versionName could be NULL.
    // Remove the dimension.
    if (versionName == null) {
      return device.removeDimension(name);
    }
    if (device.updateDimension(name, versionName)) {
      logger.atInfo().log(
          "Update dimension %s to: %s, device_id=%s",
          Ascii.toLowerCase(name.name()), versionName, deviceId);
      return true;
    }
    return false;
  }

  private void updateCustomizedDimensions() throws InterruptedException {
    if (checkCustomizedDimension(Dimension.Name.AGSA_VERSION)) {
      updateAppVersion(
          deviceId, Dimension.Name.AGSA_VERSION, AndroidPackages.AGSA.getPackageName());
    }
    if (checkCustomizedDimension(Dimension.Name.CHROME_VERSION)) {
      updateAppVersion(
          deviceId, Dimension.Name.CHROME_VERSION, AndroidPackages.CHROME.getPackageName());
    }
  }

  private boolean updateGmsVersionDimensions() throws InterruptedException {
    return updateAppVersion(
        deviceId, Dimension.Name.GMS_VERSION, AndroidPackages.GMS.getPackageName());
  }

  /**
   * List of decorators/drivers, dimensions that should be supported by standard Android
   * root/non-root devices in all lab types.
   */
  public void basicAndroidDeviceConfiguration(boolean isRooted) throws InterruptedException {
    basicAndroidDimensionConfiguration(isRooted);
    basicAndroidDriverConfiguration();
    basicAndroidDecoratorConfiguration();
  }

  /**
   * Dimensions that should be supported by standard Android root/non-root devices in all lab types.
   */
  private void basicAndroidDimensionConfiguration(boolean isRooted) {
    // Checks root.
    device.addDimension(Dimension.Name.ROOTED, String.valueOf(isRooted));
    device.addDimension(Dimension.Name.OS, Dimension.Value.ANDROID);
  }

  /**
   * Drivers that should be supported by standard Android root/non-root devices in all lab types.
   */
  private void basicAndroidDriverConfiguration() throws InterruptedException {
    // Adds general drivers.
    // keep-sorted start
    device.addSupportedDriver("AndroidForegroundServiceMessenger");
    device.addSupportedDriver("AndroidInstrumentation");
    device.addSupportedDriver("AndroidNativeBin");
    device.addSupportedDriver("AndroidRoboTest");
    device.addSupportedDriver("MoblyTest");
    device.addSupportedDriver("NoOpDriver");
    device.addSupportedDriver("SlateDriver");
    device.addSupportedDriver("TradefedTest");
    // keep-sorted end
  }

  /** Decorators that should be supported by ALL Android root/non-root devices in all lab types. */
  public void basicAndroidDecoratorConfiguration() throws InterruptedException {
    // Adds general decorators.
    // keep-sorted start
    device.addSupportedDecorator("AndroidAccountDecorator");
    device.addSupportedDecorator("AndroidAdbShellDecorator");
    device.addSupportedDecorator("AndroidAtsDynamicConfigPusherDecorator");
    device.addSupportedDecorator("AndroidCleanAppsDecorator");
    device.addSupportedDecorator("AndroidDeviceFeaturesCheckDecorator");
    // Advanced device settings. This decorator is only full tested on AndroidRealDevice. Some
    // settings may not be supported for emulators.
    device.addSupportedDecorator("AndroidDeviceSettingsDecorator");
    device.addSupportedDecorator("AndroidFilePullerDecorator");
    device.addSupportedDecorator("AndroidFilePusherDecorator");
    device.addSupportedDecorator("AndroidInstallAppsDecorator");
    device.addSupportedDecorator("AndroidLabTestSupportSettingsDecorator");
    device.addSupportedDecorator("AndroidLogCatDecorator");
    device.addSupportedDecorator("AndroidLogcatMonitoringDecorator");
    device.addSupportedDecorator("AndroidMainlineModulesCheckDecorator");
    device.addSupportedDecorator("AndroidMinSdkVersionCheckDecorator");
    device.addSupportedDecorator("AndroidNetworkActivityLoggingDecorator");
    device.addSupportedDecorator("AndroidOrientationDecorator");
    device.addSupportedDecorator("AndroidShippingApiLevelCheckDecorator");
    device.addSupportedDecorator("AndroidShowInstructionDecorator");
    device.addSupportedDecorator("AndroidStartAppsDecorator");
    device.addSupportedDecorator("AndroidSwitchLanguageDecorator");
    device.addSupportedDecorator("AndroidSwitchUserDecorator");
  }

  /**
   * List of additional decorators/drivers, dimensions that should be supported by standard Android
   * root/non-root devices.
   *
   * <p>Devices like OxygenDevice don't have this additional Android device configuration, they have
   * theirs own customized configuration.
   *
   * @param fullStackFeaturesEnabled more dimensions, supported decorators and drivers will be added
   *     if full stack features are enabled
   */
  private void additionalAndroidDeviceConfiguration(boolean fullStackFeaturesEnabled)
      throws InterruptedException {
    device.addSupportedDecorator("NoOpDecorator");
    // *********************************************************************************************
    // The following features are only enabled in full stack labs or Local Mode.
    // *********************************************************************************************

    if (!fullStackFeaturesEnabled) {
      return;
    }

    // For OSS
    device.addSupportedDriver("MoblyAospTest");

    // Gets the current override size of the screen of the device.
    try {
      ScreenResolution screenResolution = androidSystemSettingUtil.getScreenResolution(deviceId);
      logger.atInfo().log("Device %s screen resolution: %s", deviceId, screenResolution);
      device.addDimension(
          Dimension.Name.SCREEN_SIZE,
          String.format("%sx%s", screenResolution.curWidth(), screenResolution.curHeight()));
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get screen size for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e));
    }
  }

  /**
   * List of decorators/drivers and dimensions that should be supported by standard Android rooted
   * devices only.
   */
  private void rootedAndroidDeviceConfiguration(String deviceId) throws InterruptedException {

    // *********************************************************************************************
    // The following features are only enabled in full stack labs or Local Mode.
    // *********************************************************************************************

    if (!ifEnableFullStackFeatures()) {
      return;
    }

    if (device.getIntegerProperty(AndroidDeviceHelper.PROPERTY_NAME_CACHED_SDK_VERSION).orElse(0)
        >= 18) {
      updateGServicesAndroidId(deviceId);
    }

    // Gets the version of Google play service on the device.
    try {
      String version =
          androidPackageManagerUtil.getAppVersionName(deviceId, PackageConstants.PACKAGE_NAME_GMS);
      if (!Strings.isNullOrEmpty(version)) {
        logger.atInfo().log("Got device %s GMS version: %s", deviceId, version);
        device.addDimension(Dimension.Name.GMS_VERSION, version);
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get device %s GMS version: %s", deviceId, MoreThrowables.shortDebugString(e));
    }
  }

  /**
   * Whether it will enable full stack features for the lab.
   *
   * <p>Subclass can override it to decide whether enable full stack features. Currently it behaves
   * differently for different labs types, for example, Satellite Lab, Local Mode and OSS Lab enable
   * full stack features, and only Core Lab enables part of them.
   */
  protected abstract boolean ifEnableFullStackFeatures();
}
