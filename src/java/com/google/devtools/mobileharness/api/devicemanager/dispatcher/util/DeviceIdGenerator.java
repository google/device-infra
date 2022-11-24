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

package com.google.devtools.mobileharness.api.devicemanager.dispatcher.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AndroidEmulatorIds;
import com.google.devtools.mobileharness.shared.util.network.NetworkUtil;
import java.util.List;
import java.util.UUID;

/** Utils for generating device id. */
public class DeviceIdGenerator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String USB_ID_PREFIX = "usb:";
  private final NetworkUtil netUtil;
  private final ApiConfig apiConfig;

  public DeviceIdGenerator() {
    this(ApiConfig.getInstance(), new NetworkUtil());
  }

  @VisibleForTesting
  DeviceIdGenerator(ApiConfig apiConfig, NetworkUtil netUtil) {
    this.netUtil = netUtil;
    this.apiConfig = apiConfig;
  }

  /**
   * the deviceControlId may look like "192.168.10.20" or "192.168.10.20:5555", but the
   * overTcpDeviceControlIds may look like "192.168.10.20".
   */
  private boolean isOverTcpDevice(String deviceControlId) {
    for (String deviceId : apiConfig.getOverTcpDeviceControlIds()) {
      if (deviceControlId.contentEquals(deviceId)
          || deviceControlId.startsWith(deviceId.concat(":"))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Generates the Android device id from device control id, adds the lab host name as the prefix of
   * the uuid if it's an emulator or it's a usb id.
   *
   * @param deviceControlId the device control id from detector.
   */
  public DeviceId getAndroidDeviceId(String deviceControlId) {
    if (AndroidEmulatorIds.isAndroidEmulator(deviceControlId)
        || deviceControlId.startsWith(USB_ID_PREFIX)
        || isOverTcpDevice(deviceControlId)) {
      String prefix;
      try {
        prefix = getLocalHostName();
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to generate AndroidDevice uuid, use random uuid instead.");
        prefix = UUID.randomUUID().toString();
      }
      if (AndroidEmulatorIds.isAndroidEmulator(deviceControlId)) {
        List<String> word = Splitter.on(':').splitToList(deviceControlId);
        if (word.size() > 1) {
          return DeviceId.of(
              deviceControlId, deviceControlId.replaceFirst(word.get(0), prefix), true);
        } else {
          // should not happen.
          return DeviceId.of(deviceControlId, UUID.randomUUID().toString(), true);
        }
      } else {
        return DeviceId.of(deviceControlId, prefix + ":" + deviceControlId, true);
      }
    } else {
      // For b/178422520, some devices control id contains "/", it will broke some MH services, so
      // for uuid we replace all "/" to "-".
      return DeviceId.of(deviceControlId, deviceControlId.replace('/', '-'), false);
    }
  }

  /**
   * Generates the USB device id from device control id, adds the lab host name as the prefix of the
   * uuid if it's a usb id.
   *
   * @param deviceControlId the device control id from detector.
   */
  public DeviceId getUsbDeviceId(String deviceControlId) {
    if (deviceControlId.startsWith(USB_ID_PREFIX)) {
      String prefix;
      try {
        prefix = getLocalHostName();
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to generate UsbDevice uuid, use random uuid instead.");
        prefix = UUID.randomUUID().toString();
      }
      return DeviceId.of(
          deviceControlId, prefix + ":" + deviceControlId, /* isUuidVolatile= */ true);
    } else {
      return DeviceId.of(deviceControlId, deviceControlId, /* isUuidVolatile= */ false);
    }
  }

  /**
   * The default deviceId for sshable device is its ip address which could have conflict with other
   * host network. Add host name to generate unique uuid.
   *
   * @param deviceControlId the device control id from detector.
   */
  public DeviceId getSshDeviceId(String deviceControlId) {
    try {
      return DeviceId.of(
          deviceControlId, String.format("%s:%s", getLocalHostName(), deviceControlId), false);
    } catch (MobileHarnessException e) {
      // Should not happen.
      logger.atWarning().withCause(e).log(
          "Failed to generate sshable EmbeddedLinuxDevice uuid, use random uuid instead.");
      return DeviceId.of(deviceControlId, UUID.randomUUID().toString(), false);
    }
  }

  /**
   * The default deviceId for video device is its file descriptor which could have conflict with
   * other host network. Add host name to generate unique uuid.
   *
   * @param deviceControlId the device control id from detector.
   */
  public DeviceId getVideoDeviceId(String deviceControlId) {
    try {
      // uuid shouldn't contain '/', otherwise it may break generated urls.
      return DeviceId.of(
          deviceControlId,
          String.format("%s:%s", getLocalHostName(), deviceControlId).replace('/', '-'),
          false);
    } catch (MobileHarnessException e) {
      throw new AssertionError("Failed to generate VideoDevice uuid.", e);
    }
  }

  /**
   * Generates the Linux device id from device control id, currently use the local host name as the
   * uuid.
   *
   * @param deviceControlId the device control id from detector.
   */
  public DeviceId getLinuxDeviceId(String deviceControlId) {
    try {
      return DeviceId.of(deviceControlId, getLocalHostName(), false);
    } catch (MobileHarnessException e) {
      // Should not happen.
      logger.atWarning().withCause(e).log(
          "Failed to generate LinuxDevice uuid, use random uuid instead.");
      return DeviceId.of(deviceControlId, UUID.randomUUID().toString(), false);
    }
  }

  /**
   * Generates the NoOp device id from device control id, adds the lab host name as the prefix of
   * the uuid.
   *
   * @param deviceControlId the device control id from detector.
   */
  public DeviceId getNoOpDeviceId(String deviceControlId) {
    try {
      return DeviceId.of(
          deviceControlId, String.format("%s:%s", getLocalHostName(), deviceControlId), true);
    } catch (MobileHarnessException e) {
      // Should not happen.
      logger.atWarning().withCause(e).log(
          "Failed to generate NoOpDevice uuid, use random uuid instead..");
      return DeviceId.of(deviceControlId, UUID.randomUUID().toString(), true);
    }
  }

  private String getLocalHostName() throws MobileHarnessException {
    try {
      return netUtil.getLocalHostName();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(BasicErrorId.LOCAL_NETWORK_ERROR, e.getMessage(), e);
    }
  }
}
