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
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.Provider;
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

  @Inject
  DeviceDetailsRetriever(
      DeviceQuerier deviceQuerier,
      Provider<AndroidAdbInternalUtil> androidAdbInternalUtilProvider,
      Provider<AndroidAdbUtil> androidAdbUtilProvider) {
    this.deviceQuerier = deviceQuerier;
    this.androidAdbInternalUtilProvider = androidAdbInternalUtilProvider;
    this.androidAdbUtilProvider = androidAdbUtilProvider;
  }

  /** Gets details of all Android devices. */
  public ImmutableMap<String, DeviceDetails> getAllAndroidDevices(
      SessionRequestInfo sessionRequestInfo) throws MobileHarnessException, InterruptedException {
    if (Flags.instance().enableAtsMode.getNonNull()) {
      return getAllAndroidDevicesFromMaster();
    } else {
      return getAllLocalAndroidDevices(sessionRequestInfo);
    }
  }

  /** Gets details of all local Android devices. */
  public ImmutableMap<String, DeviceDetails> getAllLocalAndroidDevices(
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
                  Optional<String> productType = getDeviceProductType(deviceId);
                  if (productType.isPresent()) {
                    deviceDetails.setProductType(productType.get());
                  }
                  Optional<String> productVariant = getDeviceProductVariant(deviceId);
                  if (productVariant.isPresent()) {
                    deviceDetails.setProductVariant(productVariant.get());
                  }
                }
                ImmutableMap.Builder<String, String> collectedDeviceProperties =
                    ImmutableMap.builder();
                sessionRequestInfo
                    .deviceProperties()
                    .keySet()
                    .forEach(
                        propName -> {
                          Optional<String> propertyValue = getDeviceProperty(deviceId, propName);
                          if (propertyValue.isPresent()) {
                            collectedDeviceProperties.put(propName, propertyValue.get());
                          }
                        });
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

  private ImmutableMap<String, DeviceDetails> getAllAndroidDevicesFromMaster()
      throws MobileHarnessException, InterruptedException {
    DeviceQueryResult queryResult;
    try {
      queryResult = deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_COMMAND_QUERY_DEVICE_ERROR, "Failed to query device", e);
    }
    return queryResult.getDeviceInfoList().stream()
        .filter(
            deviceInfo ->
                deviceInfo.getTypeList().stream()
                    .anyMatch(deviceType -> deviceType.startsWith("Android")))
        .map(deviceInfo -> DeviceDetails.builder().setId(deviceInfo.getId()).build())
        // TODO: add more device info to the DeviceDetails for ATS 2.0
        .collect(toImmutableMap(DeviceDetails::id, identity()));
  }
}
