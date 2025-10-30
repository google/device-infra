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

package com.google.devtools.mobileharness.infra.master.central.model.lab;

import static com.google.devtools.mobileharness.shared.constant.device.DeviceConstants.HEALTHY_STATUS;

import com.google.common.base.Ascii;
import com.google.devtools.mobileharness.api.model.proto.Device;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.infra.master.central.proto.Device.DeviceCondition;
import com.google.devtools.mobileharness.infra.master.central.proto.Device.DeviceConditionOrBuilder;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import com.google.wireless.qa.mobileharness.shared.proto.Common;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Utils for processing {@link DeviceCondition}. */
public final class DeviceConditionUtil {
  /** If the last modify time of a non-MISSING lab/device exceed this, will be mark as MISSING. */
  public static final Duration EXPIRATION_THRESHOLD = Duration.ofMinutes(4);

  /** If the devices that disappear within this after registering, will be removed directly. */
  public static final Duration DIRECTLY_REMOVAL_THRESHOLD = Duration.ofMinutes(5);

  private static final String ANDROID_REAL_DEVICE_TYPE = "AndroidRealDevice";
  private static final String IOS_REAL_DEVICE_TYPE = "IosRealDevice";
  private static final String CLOUD_TF_AVD_DEVICE_TYPE = "CloudTFAvdDevice";

  /**
   * Summarizes the final device status.
   *
   * <p>Note this method doesn't check whether the device is expired. Checks {@link
   * #isExpired(DeviceCondition)} if necessary.
   */
  public static DeviceStatus summarizeStatus(DeviceConditionOrBuilder deviceCondition) {
    if (deviceCondition.hasAllocatedTestLocator()) {
      return DeviceStatus.BUSY;
    } else if (deviceCondition.getDirty()
        && !deviceCondition.getStatusFromLab().equals(DeviceStatus.MISSING)) {
      return DeviceStatus.DIRTY;
    } else {
      return deviceCondition.getStatusFromLab();
    }
  }

  /**
   * Merges the status from MH lab and Device Master. It's for client and FE to query device status.
   */
  public static DeviceStatus mergeMnmDeviceStatusIfAny(
      DeviceConditionOrBuilder deviceCondition, DeviceStatus defaultStatusFromDm) {
    DeviceStatus summarizedMhLabStatus = summarizeStatus(deviceCondition);
    DeviceStatus statusFromDm =
        deviceCondition.hasStatusFromDm() ? deviceCondition.getStatusFromDm() : defaultStatusFromDm;

    // The array is ordered. Use the preceding status if it is either Lab or DM status.
    DeviceStatus[] deviceStatuses =
        new DeviceStatus[] {
          DeviceStatus.MISSING,
          DeviceStatus.LAMEDUCK,
          DeviceStatus.FAILED,
          DeviceStatus.PREPPING,
          DeviceStatus.BUSY,
          DeviceStatus.DYING,
          DeviceStatus.INIT,
          DeviceStatus.DIRTY,
          DeviceStatus.IDLE
        };
    for (DeviceStatus deviceStatus : deviceStatuses) {
      if (summarizedMhLabStatus == deviceStatus || statusFromDm == deviceStatus) {
        return deviceStatus;
      }
    }
    return DeviceStatus.UNRECOGNIZED;
  }

  /**
   * Summarizes the last modify time of the current device status.
   *
   * <p>Note this method doesn't check whether the device is expired. Checks {@link
   * #isExpired(DeviceCondition)} if necessary.
   */
  public static Instant summarizeStatusModifyTime(DeviceConditionOrBuilder deviceCondition) {
    if (deviceCondition.hasAllocatedTestLocator()) {
      return Instant.ofEpochMilli(deviceCondition.getAllocationModifyTimeMsFromMaster());
    } else if (deviceCondition.getDirty()) {
      return Instant.ofEpochMilli(deviceCondition.getDirtyModifyTimeMsFromMaster());
    } else {
      return Instant.ofEpochMilli(
          Long.max(
              Long.max(
                  deviceCondition.getAllocationModifyTimeMsFromMaster(),
                  deviceCondition.getDirtyModifyTimeMsFromMaster()),
              deviceCondition.getStatusFromLabModifyTimeMsFromMaster()));
    }
  }

  /**
   * Whether the device is IDLE.
   *
   * <p>Note this method doesn't check whether the device is expired. Checks {@link
   * #isExpired(DeviceCondition)} if necessary.
   */
  public static boolean isIdle(DeviceConditionOrBuilder deviceCondition) {
    return summarizeStatus(deviceCondition) == DeviceStatus.IDLE;
  }

  /**
   * Whether the device is ready, no matter it is IDLE or BUSY.
   *
   * <p>Note this method doesn't check whether the device is expired. Checks {@link
   * #isExpired(DeviceCondition)} if necessary.
   */
  public static boolean isReady(DeviceConditionOrBuilder deviceCondition) {
    DeviceStatus status = summarizeStatus(deviceCondition);
    return (status == DeviceStatus.IDLE || status == DeviceStatus.BUSY);
  }

  /**
   * Whether the device is in {@link HEALTHY_STATUS}.
   *
   * <p>Note this method doesn't check whether the device is expired. Checks {@link
   * #isExpired(DeviceCondition)} if necessary.
   */
  public static boolean isInHealthyStatus(DeviceConditionOrBuilder deviceCondition) {
    DeviceStatus status = summarizeStatus(deviceCondition);
    return HEALTHY_STATUS.contains(status);
  }

  /**
   * Checks whether the timestamp of a non-MISSING device is older than {@link
   * #EXPIRATION_THRESHOLD}. If yes, it is considered expired and need to be marked as MISSING.
   */
  public static boolean isExpired(DeviceCondition condition) {
    Instant timestamp = Instant.ofEpochMilli(condition.getSyncTimeMsFromMaster());
    return !condition.getStatusFromLab().equals(DeviceStatus.MISSING)
        && timestamp.plus(EXPIRATION_THRESHOLD).isBefore(InstantSource.system().instant());
  }

  /**
   * Checks whether the device last sync time is older than {#EXPIRATION_THRESHOLD} and signup
   * timestamp is newer than {@link #DIRECTLY_REMOVAL_THRESHOLD}. If so, we will directly remove it
   * instead of set it as MISSING.
   */
  public static boolean shouldDirectlyRemoveWithoutStayingInMissing(DeviceCondition condition) {
    Instant lastSyncTimestamp = Instant.ofEpochMilli(condition.getSyncTimeMsFromMaster());
    Instant firstSignUpTimestamp = Instant.ofEpochMilli(condition.getFirstSignUpTimeMsFromMaster());
    return lastSyncTimestamp.plus(EXPIRATION_THRESHOLD).isBefore(InstantSource.system().instant())
        && firstSignUpTimestamp
            .plus(DIRECTLY_REMOVAL_THRESHOLD)
            .isAfter(InstantSource.system().instant());
  }

  /**
   * Checks whether the device is MISSING and its timestamp is older than {@link
   * #missingDeviceRemovalThreshold} or the device appears in other labs. If so, we will totally
   * remove it from the Master Central DB.
   */
  public static boolean shouldRemoveMissingDevice(
      DeviceDao deviceDao, Set<String> aliveDevicesInOtherLabs) {
    return deviceDao.condition().getStatusFromLab().equals(DeviceStatus.MISSING)
        && (exceedRemovalThreshold(deviceDao)
            || aliveDevicesInOtherLabs.contains(deviceDao.locator().id()));
  }

  private static boolean exceedRemovalThreshold(DeviceDao deviceDao) {
    Instant timestamp = Instant.ofEpochMilli(deviceDao.condition().getSyncTimeMsFromMaster());
    if (deviceDao.profile().getFeature().getTypeList().contains(CLOUD_TF_AVD_DEVICE_TYPE)) {
      return timestamp
          .plus(Flags.instance().ephemeralRemovalThreshold.get())
          .isBefore(InstantSource.system().instant());
    } else {
      return timestamp
          .plus(Flags.instance().deviceRemovalThreshold.get())
          .isBefore(InstantSource.system().instant());
    }
  }

  /** Returns whether {@link DeviceDao} is shared pool device */
  public static boolean isSharedPoolDevice(DeviceDao device) {
    Device.DeviceDimension sharedPoolDimension =
        Device.DeviceDimension.newBuilder()
            .setName(Ascii.toLowerCase(Name.POOL.toString()))
            .setValue(Value.POOL_SHARED)
            .build();
    return device
            .profile()
            .getFeature()
            .getCompositeDimension()
            .getRequiredDimensionList()
            .contains(sharedPoolDimension)
        || device
            .profile()
            .getFeature()
            .getCompositeDimension()
            .getSupportedDimensionList()
            .contains(sharedPoolDimension);
  }

  /** Returns the device model */
  public static Optional<String> getDeviceModel(DeviceDao device) {
    return getDeviceDimension(device, Ascii.toLowerCase(Name.MODEL.name()));
  }

  /** Returns the device version */
  public static Optional<String> getDeviceVersion(DeviceDao device) {
    List<String> types = device.profile().getFeature().getTypeList();
    if (types.contains(ANDROID_REAL_DEVICE_TYPE)) {
      return getDeviceDimension(device, Ascii.toLowerCase(Name.SDK_VERSION.name()));
    } else if (types.contains(IOS_REAL_DEVICE_TYPE)) {
      return getDeviceDimension(device, Ascii.toLowerCase(Name.SOFTWARE_VERSION.name()));
    }
    return Optional.empty();
  }

  /** Reports the detail of the device condition. */
  public static String getReport(DeviceConditionOrBuilder deviceCondition) {
    return String.format(
        "%s/SYNC@%s/LAB_BECOME_IDLE@%s/DM_BECOME_IDLE@%s/%s_IN_LAB@%s_CHANGE@%s/"
            + "FIRST_SIGN_UP@%s/%s_IN_DM/%s",

        // DIRTY info:
        deviceCondition.getDirty()
            ? "DIRTY@"
                + TimeUtils.toDateShortString(
                    Instant.ofEpochMilli(deviceCondition.getDirtyModifyTimeMsFromMaster()))
            : "CLEAN",
        // Last sync time:
        TimeUtils.toDateShortString(
            Instant.ofEpochMilli(deviceCondition.getSyncTimeMsFromMaster())),

        // When it becomes IDLE in Master:
        deviceCondition.getBecomeIdleTimeMsFromMaster() > 0
            ? TimeUtils.toDateShortString(
                Instant.ofEpochMilli(deviceCondition.getBecomeIdleTimeMsFromMaster()))
            : "NEVER",

        // When it becomes IDLE in DM:
        deviceCondition.getBecomeIdleTimeMsFromDm() > 0
            ? TimeUtils.toDateShortString(
                Instant.ofEpochMilli(deviceCondition.getBecomeIdleTimeMsFromDm()))
            : "UNKNOWN",

        // Status from Lab:
        deviceCondition.getStatusFromLab(),
        TimeUtils.toDateShortString(Instant.ofEpochMilli(deviceCondition.getSyncTimeMsFromLab())),
        TimeUtils.toDateShortString(
            Instant.ofEpochMilli(deviceCondition.getStatusFromLabModifyTimeMsFromMaster())),
        TimeUtils.toDateShortString(
            Instant.ofEpochMilli(deviceCondition.getFirstSignUpTimeMsFromMaster())),

        // Status from DM:
        deviceCondition.getStatusFromDm(),

        // Allocation info:
        deviceCondition.hasAllocatedTestLocator()
            ? "TEST_"
                + deviceCondition.getAllocatedTestLocator().getId()
                + "@"
                + TimeUtils.toDateShortString(
                    Instant.ofEpochMilli(deviceCondition.getAllocationModifyTimeMsFromMaster()))
            : "NO_TEST");
  }

  /** Gets the removal threshold. */
  public static Duration getRemovalThreshold() {
    return Flags.instance().deviceRemovalThreshold.getNonNull();
  }

  private static Optional<String> getDeviceDimension(DeviceDao device, String dimension) {
    return StrPairUtil.convertFromDeviceDimension(
            device.profile().getFeature().getCompositeDimension().getSupportedDimensionList())
        .stream()
        .filter(d -> dimension.equals(d.getName()))
        .findFirst()
        .map(Common.StrPair::getValue);
  }

  private DeviceConditionUtil() {}
}
