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

package com.google.devtools.mobileharness.infra.ats.common;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper.DeviceInfo;
import com.google.inject.Provider;
import java.util.Optional;
import javax.inject.Inject;

/** Implementation of {@link LocalDeviceUtil} that operates on real local devices. */
public class LocalDeviceUtilImpl implements LocalDeviceUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DeviceDetailsRetriever deviceDetailsRetriever;
  private final Provider<AndroidAdbUtil> androidAdbUtilProvider;

  @Inject
  LocalDeviceUtilImpl(
      DeviceDetailsRetriever deviceDetailsRetriever,
      Provider<AndroidAdbUtil> androidAdbUtilProvider) {
    this.deviceDetailsRetriever = deviceDetailsRetriever;
    this.androidAdbUtilProvider = androidAdbUtilProvider;
  }

  @Override
  public ImmutableSet<DeviceDetails> getLocalAvailableDevices(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    ImmutableMap<String, DeviceDetails> allAndroidDevices =
        deviceDetailsRetriever.getAllLocalAndroidDevicesWithNeededDetails(sessionRequestInfo);
    logger.atInfo().log("All android devices: %s", allAndroidDevices.keySet());

    DeviceSelectionOptions.Builder optionsBuilder =
        DeviceSelectionOptions.builder()
            .setSerials(sessionRequestInfo.deviceSerials())
            .setExcludeSerials(sessionRequestInfo.excludeDeviceSerials())
            .setProductTypes(sessionRequestInfo.productTypes())
            .setDeviceProperties(sessionRequestInfo.deviceProperties());
    sessionRequestInfo.maxBatteryLevel().ifPresent(optionsBuilder::setMaxBatteryLevel);
    sessionRequestInfo.minBatteryLevel().ifPresent(optionsBuilder::setMinBatteryLevel);
    sessionRequestInfo.maxBatteryTemperature().ifPresent(optionsBuilder::setMaxBatteryTemperature);
    sessionRequestInfo.minSdkLevel().ifPresent(optionsBuilder::setMinSdkLevel);
    sessionRequestInfo.maxSdkLevel().ifPresent(optionsBuilder::setMaxSdkLevel);
    DeviceSelectionOptions deviceSelectionOptions = optionsBuilder.build();

    ImmutableSet<DeviceDetails> availableDevices =
        allAndroidDevices.values().stream()
            .filter(deviceDetails -> DeviceSelection.matches(deviceDetails, deviceSelectionOptions))
            .collect(toImmutableSet());

    if (availableDevices.isEmpty()) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.OLCS_NO_AVAILABLE_DEVICE,
          "No available device is found.",
          /* cause= */ null);
    }
    return availableDevices;
  }

  @Override
  public Optional<DeviceInfo> getDeviceInfoFromLocal(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    ImmutableSet<String> allLocalAndroidDevices =
        deviceDetailsRetriever
            .getAllLocalAndroidDevicesWithNeededDetails(sessionRequestInfo)
            .keySet();
    if (!allLocalAndroidDevices.isEmpty()) {
      Optional<String> deviceSerial;
      if (sessionRequestInfo.deviceSerials().isEmpty()) {
        deviceSerial = allLocalAndroidDevices.stream().findFirst();
      } else {
        deviceSerial =
            sessionRequestInfo.deviceSerials().stream()
                .filter(allLocalAndroidDevices::contains)
                .findFirst();
      }
      if (deviceSerial.isEmpty()) {
        logger
            .atInfo()
            .with(IMPORTANCE, IMPORTANT)
            .log(
                "No match local Android devices, return empty device info. Detected all local"
                    + " Android devices: %s",
                allLocalAndroidDevices);
        return Optional.empty();
      }

      String abiList =
          androidAdbUtilProvider.get().getProperty(deviceSerial.get(), AndroidProperty.ABILIST);
      String abi =
          androidAdbUtilProvider.get().getProperty(deviceSerial.get(), AndroidProperty.ABI);
      return Optional.of(
          DeviceInfo.builder()
              .setDeviceId(deviceSerial.get())
              .setSupportedAbiList(abiList)
              .setSupportedAbi(abi)
              .build());
    }
    logger
        .atInfo()
        .with(IMPORTANCE, IMPORTANT)
        .log("Detected no local Android devices, return empty device info.");
    return Optional.empty();
  }
}
