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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.model.proto.Device.TempDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceActions;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceHeaderInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.FlashActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.FlashButtonParams;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HostInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.QuarantineInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.RemoteControlButtonState;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Builder for {@link DeviceHeaderInfo}. */
public class DeviceHeaderBuilder {

  @Inject
  DeviceHeaderBuilder() {}

  public DeviceHeaderInfo buildDeviceHeaderInfo(DeviceInfo deviceInfo) {
    return DeviceHeaderInfo.newBuilder()
        .setId(deviceInfo.getDeviceLocator().getId())
        .setHost(
            HostInfo.newBuilder()
                .setName(deviceInfo.getDeviceLocator().getLabLocator().getHostName())
                .setIp(deviceInfo.getDeviceLocator().getLabLocator().getIp()))
        .setQuarantine(buildQuarantineInfo(deviceInfo))
        .setActions(buildDeviceActions(deviceInfo))
        .build();
  }

  private QuarantineInfo buildQuarantineInfo(DeviceInfo deviceInfo) {
    Optional<TempDimension> quarantineTempDim =
        deviceInfo.getDeviceCondition().getTempDimensionList().stream()
            .filter(
                dim ->
                    dim.getDimension().getName().equals("quarantined")
                        && dim.getDimension()
                            .getValue()
                            .toLowerCase(Locale.ROOT)
                            .equals(Dimension.Value.TRUE))
            .findFirst();

    boolean inTemp = quarantineTempDim.isPresent();

    boolean inRequired =
        deviceInfo.getDeviceFeature().getCompositeDimension().getRequiredDimensionList().stream()
            .anyMatch(
                dim ->
                    dim.getName().equals("quarantined")
                        && dim.getValue().equals(Dimension.Value.TRUE));

    boolean isQuarantined = inTemp || inRequired;
    QuarantineInfo.Builder builder = QuarantineInfo.newBuilder().setIsQuarantined(isQuarantined);

    if (inTemp && quarantineTempDim.get().getExpireTimestampMs() > 0) {
      builder.setExpiry(Timestamps.fromMillis(quarantineTempDim.get().getExpireTimestampMs()));
    }

    return builder.build();
  }

  private DeviceActions buildDeviceActions(DeviceInfo deviceInfo) {
    String status = deviceInfo.getDeviceStatus().toString();
    List<String> types = deviceInfo.getDeviceFeature().getTypeList();
    boolean isMissing = status.equals(DeviceStatus.MISSING.name());
    boolean isIdle = status.equals(DeviceStatus.IDLE.name());

    List<DeviceDimension> supportedDims =
        deviceInfo.getDeviceFeature().getCompositeDimension().getSupportedDimensionList();
    List<DeviceDimension> requiredDims =
        deviceInfo.getDeviceFeature().getCompositeDimension().getRequiredDimensionList();

    boolean isTestBed = types.contains("TestbedDevice");
    boolean isAndroidRealDevice = types.contains("AndroidRealDevice") && !isTestBed;
    boolean isAndroidFlashableDevice = types.contains("AndroidFlashableDevice");
    boolean isAbnormalDevice = types.contains("AbnormalTestbedDevice");
    boolean isFailedDevice = types.contains("FailedDevice");

    boolean isSharedDevice =
        requiredDims.stream()
            .anyMatch(
                d ->
                    d.getName().equals(Dimension.Name.POOL.lowerCaseName())
                        && d.getValue().equals(Dimension.Value.POOL_SHARED));

    boolean isMacHost =
        Stream.concat(supportedDims.stream(), requiredDims.stream())
            .anyMatch(
                d ->
                    d.getName().equals(Dimension.Name.HOST_OS.lowerCaseName())
                        && d.getValue().contains("Mac OS"));

    boolean isScreenshotSupported =
        Stream.concat(supportedDims.stream(), requiredDims.stream())
            .anyMatch(d -> d.getName().equals(Dimension.Name.SCREENSHOT_ABLE.lowerCaseName()));

    boolean isDeviceQuarantined = buildQuarantineInfo(deviceInfo).getIsQuarantined();

    return DeviceActions.newBuilder()
        .setScreenshot(getScreenshotState(isMissing, isScreenshotSupported))
        .setLogcat(getLogcatState(isAndroidRealDevice, isMissing, isSharedDevice))
        .setFlash(
            getFlashState(
                isAndroidRealDevice,
                isAndroidFlashableDevice,
                isSharedDevice,
                isTestBed,
                isIdle,
                types))
        .setRemoteControl(
            getRemoteControlState(
                isAndroidRealDevice, isIdle, isAbnormalDevice, isFailedDevice, isMacHost))
        .setQuarantine(getQuarantineButtonState(isDeviceQuarantined))
        .build();
  }

  private ActionButtonState getScreenshotState(boolean isMissing, boolean isScreenshotSupported) {
    boolean screenshotEnabled = !isMissing && isScreenshotSupported;
    String screenshotTooltip =
        screenshotEnabled
            ? "Take a screenshot of the device"
            : "It's only supported to take a screenshot when the device is online and the"
                + " \"screenshot_able\" dimension is existing for this device.";
    return ActionButtonState.newBuilder()
        .setVisible(true)
        .setEnabled(screenshotEnabled)
        .setTooltip(screenshotTooltip)
        .build();
  }

  private ActionButtonState getLogcatState(
      boolean isAndroidRealDevice, boolean isMissing, boolean isSharedDevice) {
    boolean logcatEnabled = isAndroidRealDevice && !isMissing && !isSharedDevice;
    String logcatTooltip =
        logcatEnabled
            ? "Get device logcat"
            : "It's only supported to get the device logcat on the satellite Android devices.";
    return ActionButtonState.newBuilder()
        .setVisible(true)
        .setEnabled(logcatEnabled)
        .setTooltip(logcatTooltip)
        .build();
  }

  private FlashActionButtonState getFlashState(
      boolean isAndroidRealDevice,
      boolean isAndroidFlashableDevice,
      boolean isSharedDevice,
      boolean isTestBed,
      boolean isIdle,
      List<String> types) {
    boolean flashVisible = isAndroidRealDevice || isAndroidFlashableDevice;
    boolean flashEnabled = flashVisible && !isSharedDevice && !isTestBed && isIdle;
    String flashTooltip = "Flash the device";
    if (!flashEnabled) {
      if (isSharedDevice || isTestBed) {
        flashTooltip = "Device flashing is only supported on satellite lab Android devices.";
      } else if (!isIdle) {
        flashTooltip =
            "Device flash is only allowed on IDLE device. Please wait for device idle and retry.";
      }
    }
    return FlashActionButtonState.newBuilder()
        .setState(
            ActionButtonState.newBuilder()
                .setVisible(flashVisible)
                .setEnabled(flashEnabled)
                .setTooltip(flashTooltip))
        .setParams(
            FlashButtonParams.newBuilder()
                .setDeviceType(types.stream().findFirst().orElse("AndroidRealDevice")))
        .build();
  }

  private RemoteControlButtonState getRemoteControlState(
      boolean isAndroidRealDevice,
      boolean isIdle,
      boolean isAbnormalDevice,
      boolean isFailedDevice,
      boolean isMacHost) {
    // Assuming isAcidDevice equivalent to isAndroidRealDevice for now
    boolean isAcidDevice = isAndroidRealDevice;
    boolean rcEnabled =
        isAcidDevice && isIdle && !isAbnormalDevice && !isFailedDevice && !isMacHost;
    String rcTooltip = "Start a remote control session";
    if (!rcEnabled) {
      if (!isAcidDevice) {
        rcTooltip =
            "Remote control is only supported on Android real devices which enable Moreto(API level"
                + " is between 10 and 26).";
      } else if (!isIdle) {
        rcTooltip =
            "Remote control is only allowed on IDLE device. Please wait for device idle and retry.";
      } else if (isFailedDevice) {
        rcTooltip = "Remote control is disallowed on FailedDevice.";
      } else if (isMacHost) {
        rcTooltip = "Remote control is only supported on lab hosts with Linux OS.";
      }
    }
    return RemoteControlButtonState.newBuilder()
        .setState(
            ActionButtonState.newBuilder()
                .setVisible(true)
                .setEnabled(rcEnabled)
                .setTooltip(rcTooltip))
        .build();
  }

  private ActionButtonState getQuarantineButtonState(boolean isDeviceQuarantined) {
    return ActionButtonState.newBuilder()
        .setVisible(true)
        .setEnabled(true)
        .setTooltip(isDeviceQuarantined ? "Unquarantine the device" : "Quarantine the device")
        .build();
  }
}
