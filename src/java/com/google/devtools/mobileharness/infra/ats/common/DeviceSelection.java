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

import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/** Util to select devices with selection criteria. */
public class DeviceSelection {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String VARIANT_SEPARATOR = ":";

  private DeviceSelection() {}

  /** Checks if the given device with {@code deviceDetails} is a match for the provided options. */
  public static boolean matches(DeviceDetails deviceDetails, DeviceSelectionOptions options) {
    ImmutableSet<String> serials = ImmutableSet.copyOf(options.serials());
    if (!serials.isEmpty() && !serials.contains(deviceDetails.id())) {
      return false;
    }
    ImmutableSet<String> excludeSerials = ImmutableSet.copyOf(options.excludeSerials());
    if (excludeSerials.contains(deviceDetails.id())) {
      return false;
    }

    SetMultimap<String, String> productVariants = splitOnVariant(options.productTypes());
    Set<String> productTypes = productVariants.keySet();
    if (!productTypes.isEmpty()) {
      Optional<String> deviceProductType = deviceDetails.productType();
      if (deviceProductType.isEmpty()) {
        logger.atInfo().log("Found no product type for device %s.", deviceDetails.id());
        return false;
      }
      if (productTypes.contains(deviceProductType.get())) {
        // Check variant
        Optional<String> deviceProductVariant = deviceDetails.productVariant();
        ImmutableSet<String> variants =
            productVariants.get(deviceProductType.get()).stream()
                .filter(v -> v != null)
                .collect(toImmutableSet());
        if (deviceProductVariant.isEmpty() && !variants.isEmpty()) {
          logger.atInfo().log("Found no product variant for device %s.", deviceDetails.id());
          return false;
        }
        if (!variants.isEmpty()
            && deviceProductVariant.isPresent()
            && !variants.contains(deviceProductVariant.get())) {
          logger.atInfo().log(
              "Device %s product variant (%s) does not match requested variants(%s)",
              deviceDetails.id(), deviceProductVariant.get(), variants);
          return false;
        }
      } else {
        logger.atInfo().log(
            "Device %s product type (%s) does not match requested product types(%s).",
            deviceDetails.id(), deviceProductType.get(), productTypes);
        return false;
      }
    }

    if (options.maxBatteryLevel().isPresent() || options.minBatteryLevel().isPresent()) {
      Optional<Integer> deviceBatteryLevel = deviceDetails.batteryLevel();
      if (deviceBatteryLevel.isEmpty()) {
        logger.atInfo().log("Found no battery level for device %s.", deviceDetails.id());
        return false;
      }
      if (options.maxBatteryLevel().isPresent()
          && deviceBatteryLevel.get() > options.maxBatteryLevel().get()) {
        logger.atInfo().log(
            "Device %s battery level (%s) is higher than requested max battery level(%s)",
            deviceDetails.id(), deviceBatteryLevel.get(), options.maxBatteryLevel().get());
        return false;
      }
      if (options.minBatteryLevel().isPresent()
          && deviceBatteryLevel.get() < options.minBatteryLevel().get()) {
        logger.atInfo().log(
            "Device %s battery level (%s) is lower than requested min battery level(%s)",
            deviceDetails.id(), deviceBatteryLevel.get(), options.minBatteryLevel().get());
        return false;
      }
    }

    if (options.maxBatteryTemperature().isPresent()) {
      Optional<Integer> deviceBatteryTemperature = deviceDetails.batteryTemperature();
      if (deviceBatteryTemperature.isEmpty()) {
        logger.atInfo().log("Found no battery temperature for device %s.", deviceDetails.id());
        return false;
      }
      if (deviceBatteryTemperature.get() > options.maxBatteryTemperature().get()) {
        logger.atInfo().log(
            "Device %s battery temperature (%s) is higher than requested max battery"
                + " temperature(%s)",
            deviceDetails.id(),
            deviceBatteryTemperature.get(),
            options.maxBatteryTemperature().get());
        return false;
      }
    }

    if (options.minSdkLevel().isPresent() || options.maxSdkLevel().isPresent()) {
      Optional<Integer> deviceSdkLevel = deviceDetails.sdkVersion();
      if (deviceSdkLevel.isEmpty()) {
        logger.atInfo().log("Found no sdk level for device %s.", deviceDetails.id());
        return false;
      }
      if (options.minSdkLevel().isPresent() && deviceSdkLevel.get() < options.minSdkLevel().get()) {
        logger.atInfo().log(
            "Device %s sdk level (%s) is lower than requested min sdk level(%s)",
            deviceDetails.id(), deviceSdkLevel.get(), options.minSdkLevel().get());
        return false;
      }
      if (options.maxSdkLevel().isPresent() && deviceSdkLevel.get() > options.maxSdkLevel().get()) {
        logger.atInfo().log(
            "Device %s sdk level (%s) is higher than requested max sdk level(%s)",
            deviceDetails.id(), deviceSdkLevel.get(), options.maxSdkLevel().get());
        return false;
      }
    }

    ImmutableMap<String, String> deviceProperties = deviceDetails.deviceProperties();
    for (Entry<String, String> propEntry : options.deviceProperties().entrySet()) {
      @Nullable String deviceProperty = deviceProperties.getOrDefault(propEntry.getKey(), null);
      if (!propEntry.getValue().equals(deviceProperty)) {
        logger.atInfo().log(
            "Device %s property (%s) value(%s) does not match requested value(%s)",
            deviceDetails.id(), propEntry.getKey(), deviceProperty, propEntry.getValue());
        return false;
      }
    }

    return true;
  }

  private static SetMultimap<String, String> splitOnVariant(List<String> products) {
    SetMultimap<String, String> splitProducts = HashMultimap.create();
    for (String prod : products) {
      List<String> parts = Splitter.on(VARIANT_SEPARATOR).splitToList(prod);
      if (parts.size() == 1) {
        splitProducts.put(parts.get(0), null);
      } else if (parts.size() == 2) {
        // A variant was specified as product:variant
        splitProducts.put(parts.get(0), parts.get(1));
      } else {
        throw new IllegalArgumentException(
            String.format(
                "The product type filter \"%s\" "
                    + "is invalid.  It must contain 0 or 1 '%s' characters, not %d.",
                prod, VARIANT_SEPARATOR, parts.size()));
      }
    }
    return splitProducts;
  }
}
