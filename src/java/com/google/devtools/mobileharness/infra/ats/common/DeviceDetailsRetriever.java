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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.Provider;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.util.Optional;
import javax.inject.Inject;

/** Util to retrieve device details. */
public class DeviceDetailsRetriever {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DeviceQuerier deviceQuerier;
  private final Provider<AndroidAdbInternalUtil> androidAdbInternalUtilProvider;
  private final Provider<AndroidAdbUtil> androidAdbUtilProvider;
  private final Provider<AndroidSystemSettingUtil> androidSystemSettingUtilProvider;

  @Inject
  DeviceDetailsRetriever(
      DeviceQuerier deviceQuerier,
      Provider<AndroidAdbInternalUtil> androidAdbInternalUtilProvider,
      Provider<AndroidAdbUtil> androidAdbUtilProvider,
      Provider<AndroidSystemSettingUtil> androidSystemSettingUtilProvider) {
    this.deviceQuerier = deviceQuerier;
    this.androidAdbInternalUtilProvider = androidAdbInternalUtilProvider;
    this.androidAdbUtilProvider = androidAdbUtilProvider;
    this.androidSystemSettingUtilProvider = androidSystemSettingUtilProvider;
  }

  /**
   * Gets details of all Android devices. The detail only contain needed field as specified in the
   * sessionRequestInfo.
   */
  public ImmutableMap<String, DeviceDetails> getAllAndroidDevicesWithNeededDetails(
      SessionRequestInfo sessionRequestInfo) throws MobileHarnessException, InterruptedException {
    if (Flags.instance().enableAtsMode.getNonNull()) {
      return getAllAndroidDevicesFromMaster();
    } else {
      return getAllLocalAndroidDevicesWithNeededDetails(sessionRequestInfo);
    }
  }

  /**
   * Gets details of all local Android devices. The detail only contain needed field as specified in
   * the sessionRequestInfo.
   */
  public ImmutableMap<String, DeviceDetails> getAllLocalAndroidDevicesWithNeededDetails(
      SessionRequestInfo sessionRequestInfo) throws MobileHarnessException, InterruptedException {
    if (Flags.instance().detectAdbDevice.getNonNull()) {
      return androidAdbInternalUtilProvider
          .get()
          .getDeviceSerialsByState(DeviceState.DEVICE, /* timeout= */ null)
          .stream()
          .map(
              deviceId -> {
                DeviceDetails.Builder deviceDetails = DeviceDetails.builder().setId(deviceId);
                if (!sessionRequestInfo.productTypes().isEmpty()) {
                  getDeviceProductType(deviceId).ifPresent(deviceDetails::setProductType);
                  getDeviceProductVariant(deviceId).ifPresent(deviceDetails::setProductVariant);
                }
                if (sessionRequestInfo.maxSdkLevel().isPresent()
                    || sessionRequestInfo.minSdkLevel().isPresent()) {
                  getDeviceSdkVersion(deviceId).ifPresent(deviceDetails::setSdkVersion);
                }
                if (sessionRequestInfo.maxBatteryLevel().isPresent()
                    || sessionRequestInfo.minBatteryLevel().isPresent()) {
                  getDeviceBatteryLevel(deviceId).ifPresent(deviceDetails::setBatteryLevel);
                }
                if (sessionRequestInfo.maxBatteryTemperature().isPresent()) {
                  getDeviceBatteryTemperature(deviceId)
                      .ifPresent(deviceDetails::setBatteryTemperature);
                }
                ImmutableMap.Builder<String, String> collectedDeviceProperties =
                    ImmutableMap.builder();
                sessionRequestInfo
                    .deviceProperties()
                    .keySet()
                    .forEach(
                        propName ->
                            getDeviceProperty(deviceId, propName)
                                .ifPresent(s -> collectedDeviceProperties.put(propName, s)));
                return deviceDetails
                    .setDeviceProperties(collectedDeviceProperties.buildOrThrow())
                    .build();
              })
          .collect(toImmutableMap(DeviceDetails::id, identity()));
    } else {
      return ImmutableMap.of();
    }
  }

  private Optional<String> getDeviceProductType(String deviceId) {
    try {
      return Optional.of(
          androidAdbUtilProvider.get().getProperty(deviceId, AndroidProperty.PRODUCT_BOARD));
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get product type for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e));
    } catch (InterruptedException e) {
      logger.atWarning().log(
          "Interrupted when getting product type for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e));
      Thread.currentThread().interrupt();
    }
    return Optional.empty();
  }

  private Optional<String> getDeviceProductVariant(String deviceId) {
    try {
      return Optional.of(
          androidAdbUtilProvider.get().getProperty(deviceId, AndroidProperty.DEVICE));
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get product variant for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e));
    } catch (InterruptedException e) {
      logger.atWarning().log(
          "Interrupted when getting product variant for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e));
      Thread.currentThread().interrupt();
    }
    return Optional.empty();
  }

  private Optional<Integer> getDeviceSdkVersion(String deviceId) {
    try {
      return Optional.of(androidSystemSettingUtilProvider.get().getDeviceSdkVersion(deviceId));
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get SDK version for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e));
    } catch (InterruptedException e) {
      logger.atWarning().log(
          "Interrupted when getting SDK version for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e));
      Thread.currentThread().interrupt();
    }
    return Optional.empty();
  }

  private Optional<String> getDeviceProperty(String deviceId, String propertyName) {
    try {
      return Optional.of(
          androidAdbUtilProvider.get().getProperty(deviceId, ImmutableList.of(propertyName)));
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get value of property %s for device %s: %s",
          propertyName, deviceId, MoreThrowables.shortDebugString(e));
    } catch (InterruptedException e) {
      logger.atWarning().log(
          "Interrupted when getting value of property %s for device %s: %s",
          propertyName, deviceId, MoreThrowables.shortDebugString(e));
      Thread.currentThread().interrupt();
    }
    return Optional.empty();
  }

  private Optional<Integer> getDeviceBatteryLevel(String deviceId) {
    try {
      return androidSystemSettingUtilProvider.get().getBatteryLevel(deviceId);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get battery level for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e));
    } catch (InterruptedException e) {
      logger.atWarning().log(
          "Interrupted when getting battery level for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e));
      Thread.currentThread().interrupt();
    }
    return Optional.empty();
  }

  private Optional<Integer> getDeviceBatteryTemperature(String deviceId) {
    try {
      return androidSystemSettingUtilProvider.get().getBatteryTemperature(deviceId);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get battery temperature for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e));
    } catch (InterruptedException e) {
      logger.atWarning().log(
          "Interrupted when getting battery temperature for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e));
      Thread.currentThread().interrupt();
    }
    return Optional.empty();
  }

  private ImmutableMap<String, DeviceDetails> getAllAndroidDevicesFromMaster()
      throws MobileHarnessException, InterruptedException {
    DeviceQueryResult queryResult;
    try {
      queryResult = deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_COMMAND_QUERY_DEVICE_ERROR, "Failed to query device", e);
    }
    return queryResult.getDeviceInfoList().stream()
        .filter(
            deviceInfo ->
                deviceInfo.getTypeList().stream()
                    .anyMatch(deviceType -> deviceType.startsWith("Android")))
        .map(
            deviceInfo -> {
              DeviceDetails.Builder deviceDetails =
                  DeviceDetails.builder().setId(deviceInfo.getId());
              deviceInfo.getDimensionList().stream()
                  .filter(
                      dimension -> dimension.getName().equals(Ascii.toLowerCase(Name.UUID.name())))
                  .findFirst()
                  .ifPresent(dimension -> deviceDetails.setUuid(dimension.getValue()));
              return deviceDetails.build();
            })
        // TODO: add more device info to the DeviceDetails for ATS 2.0
        .collect(toImmutableMap(DeviceDetails::id, identity()));
  }
}
