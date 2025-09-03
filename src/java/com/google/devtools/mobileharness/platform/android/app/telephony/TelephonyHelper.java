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

package com.google.devtools.mobileharness.platform.android.app.telephony;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.Boolean.parseBoolean;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A utility to use and get information related to the telephony. */
public class TelephonyHelper {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TELEPHONY_UTIL_APK_RES_PATH =
      "/com/google/devtools/mobileharness/platform/android/app/binary/telephony/TelephonyUtility.apk";
  private static final String TELEPHONY_UTIL_PACKAGE_NAME = "android.telephony.utility";
  private static final String TELEPHONY_UTIL_CLASS_NAME = ".SimCardUtil";
  private static final String AJUR_RUNNER = "androidx.test.runner.AndroidJUnitRunner";

  private static final Pattern INSTRUMENTATION_STATUS_PATTERN =
      Pattern.compile("INSTRUMENTATION_STATUS: (\\w+)=(.*)");
  private static final Pattern NEW_LINE_PATTERN = Pattern.compile("\\r?\\n");

  private static final String SIM_STATE_KEY = "sim_state";
  private static final String CARRIER_PRIVILEGES_KEY = "has_carried_privileges";
  private static final String SECURED_ELEMENT_KEY = "has_secured_element";
  private static final String SE_SERVICE_KEY = "has_se_service";

  private static final String SIM_READY_STATE = "5";
  private static final String GSM_OPERATOR_PROP = "gsm.sim.operator.numeric";
  private static final String ORANGE_SIM_ID = "20801";
  private static final String THALES_GEMALTO_SIM_ID = "00101";
  private static final ImmutableList<String> SECURE_ELEMENT_SIM_IDS =
      ImmutableList.of(ORANGE_SIM_ID, THALES_GEMALTO_SIM_ID);

  private final AndroidInstrumentationUtil androidInstrumentationUtil;
  private final ApkInstaller apkInstaller;
  private final Supplier<String> apkPathSupplier;

  /** An information holder for the sim card related information. */
  @AutoValue
  public abstract static class SimCardInformation {
    public abstract String simState();

    public abstract boolean carrierPrivileges();

    public abstract boolean hasSecuredElement();

    public abstract boolean hasSeService();

    public static Builder builder() {
      return new AutoValue_TelephonyHelper_SimCardInformation.Builder()
          .setSimState("")
          .setCarrierPrivileges(false)
          .setHasSecuredElement(false)
          .setHasSeService(false);
    }

    /** Builder for {@link SimCardInformation}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setSimState(String simState);

      public abstract Builder setCarrierPrivileges(boolean carrierPrivileges);

      public abstract Builder setHasSecuredElement(boolean hasSecuredElement);

      public abstract Builder setHasSeService(boolean hasSeService);

      public abstract SimCardInformation build();
    }
  }

  public TelephonyHelper() {
    this(new AndroidInstrumentationUtil(), new ApkInstaller(), new ResUtil());
  }

  @VisibleForTesting
  TelephonyHelper(
      AndroidInstrumentationUtil androidInstrumentationUtil,
      ApkInstaller apkInstaller,
      ResUtil resUtil) {
    this.androidInstrumentationUtil = androidInstrumentationUtil;
    this.apkInstaller = apkInstaller;
    this.apkPathSupplier =
        Suppliers.memoize(
            () -> {
              try {
                return resUtil.getResourceFile(TelephonyHelper.class, TELEPHONY_UTIL_APK_RES_PATH);
              } catch (MobileHarnessException e) {
                logger.atWarning().withCause(e).log(
                    "Failed to get the telephony util apk resource file at %s",
                    TELEPHONY_UTIL_APK_RES_PATH);
                return null;
              }
            });
  }

  /** Updates the SIM card type dimension based on the device's SIM card information. */
  public void updateSimDimensions(Device device)
      throws MobileHarnessException, InterruptedException {
    SimCardInformation simCardInfo = getSimInfo(device);
    ImmutableList.Builder<String> dimensions = ImmutableList.builder();
    if (simCardInfo.simState().equals(SIM_READY_STATE)) {
      dimensions.add(Dimension.SimCardTypeValue.SIM_CARD.name());
    }
    if (simCardInfo.carrierPrivileges()) {
      dimensions.add(Dimension.SimCardTypeValue.UICC_SIM_CARD.name());
    }
    if (simCardInfo.hasSecuredElement() && simCardInfo.hasSeService()) {
      String operatorProperties = device.info().properties().get(GSM_OPERATOR_PROP).orElse("");
      // Check whether operator/sim properties contain any secure element SIM IDs.
      // TODO: Create a mechanism of checking whether a SIM card
      // matches the requirements of secure element test.
      if (Splitter.on(',')
          .splitToStream(operatorProperties)
          .anyMatch(SECURE_ELEMENT_SIM_IDS::contains)) {
        dimensions.add(Dimension.SimCardTypeValue.SECURE_ELEMENT_SIM_CARD.name());
      }
    }
    ImmutableList<String> dimensionsList = dimensions.build();
    if (!dimensionsList.isEmpty()) {
      device.updateDimension(Dimension.Name.SIM_CARD_TYPE, dimensionsList.toArray(new String[0]));
    }
  }

  /**
   * Get the information related to sim card from a given device.
   *
   * @param device The device under tests
   * @return A {@link SimCardInformation} object populated with the sim card info.
   */
  private SimCardInformation getSimInfo(Device device)
      throws MobileHarnessException, InterruptedException {
    String apkPath = apkPathSupplier.get();
    if (isNullOrEmpty(apkPath)) {
      return SimCardInformation.builder().build();
    }

    logger.atInfo().log("Installing telephony util apk on device %s", device.getDeviceId());
    try {
      apkInstaller.installApkIfNotExist(
          device,
          ApkInstallArgs.builder()
              .setApkPath(apkPath)
              .setGrantPermissions(true)
              .setSkipIfCached(false)
              .build(),
          /* log= */ null);

      String instrumentationOutput =
          androidInstrumentationUtil.instrument(
              device.getDeviceId(),
              /* deviceSdkVersion= */ null,
              AndroidInstrumentationSetting.create(
                  TELEPHONY_UTIL_PACKAGE_NAME,
                  AJUR_RUNNER,
                  TELEPHONY_UTIL_PACKAGE_NAME + TELEPHONY_UTIL_CLASS_NAME,
                  /* otherOptions= */ null,
                  /* async= */ false,
                  /* showRawResults= */ false,
                  /* prefixAndroidTest= */ false,
                  /* noIsolatedStorage= */ false,
                  /* useTestStorageService= */ false,
                  /* enableCoverage= */ false),
              /* timeout= */ Duration.ofMinutes(1));

      SimCardInformation.Builder simCardInfoBuilder = SimCardInformation.builder();
      Splitter.on(NEW_LINE_PATTERN)
          .trimResults()
          .omitEmptyStrings()
          .split(instrumentationOutput)
          .forEach(line -> parseInstrumentationOutputLine(line, simCardInfoBuilder));
      return simCardInfoBuilder.build();
    } finally {
      logger.atInfo().log("Uninstalling telephony util apk on device %s", device.getDeviceId());
      apkInstaller.uninstallApk(
          device, TELEPHONY_UTIL_PACKAGE_NAME, /* logFailures= */ true, /* log= */ null);
    }
  }

  private static void parseInstrumentationOutputLine(
      String line, SimCardInformation.Builder simCardInfoBuilder) {
    Matcher matcher = INSTRUMENTATION_STATUS_PATTERN.matcher(line);
    if (!matcher.matches()) {
      return;
    }
    String key = matcher.group(1);
    String value = matcher.group(2);
    switch (key) {
      case SIM_STATE_KEY:
        simCardInfoBuilder.setSimState(value);
        break;
      case CARRIER_PRIVILEGES_KEY:
        simCardInfoBuilder.setCarrierPrivileges(parseBoolean(value));
        break;
      case SECURED_ELEMENT_KEY:
        simCardInfoBuilder.setHasSecuredElement(parseBoolean(value));
        break;
      case SE_SERVICE_KEY:
        simCardInfoBuilder.setHasSeService(parseBoolean(value));
        break;
      default:
        logger.atWarning().log("Unknown key in instrumentation output: %s", key);
        break;
    }
  }
}
