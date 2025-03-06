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

package com.google.devtools.mobileharness.service.deviceconfig.util.generator;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.shared.util.message.ProtoUtil;
import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.protobuf.Int32Value;
import com.google.wireless.qa.mobileharness.shared.proto.Config;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** The utility class to help generating {@link DeviceConfig} instances. */
public final class DeviceConfigGenerator {

  private DeviceConfigGenerator() {}

  /**
   * Generates a {@link DeviceConfig} from the given old model {@link Config.DeviceConfig}.
   *
   * @param maxConsecutiveTest the max consecutive tests allowed between two reboots, usually got
   *     from the old {@code ApiConfig} of the lab this device is currently connecting to, will not
   *     set the field in {@link DeviceConfig} if the input is null
   * @param maxConsecutiveFail the max consecutive failed tests for all drivers, usually got from
   *     the old {@code ApiConfig} of the lab this device is currently connecting to, will not set
   *     the field in {@link DeviceConfig} if the input is null
   */
  public static DeviceConfig fromOldDeviceConfig(
      Config.DeviceConfig oldDeviceConfig,
      String uuid,
      @Nullable Integer maxConsecutiveTest,
      @Nullable Integer maxConsecutiveFail) {
    BasicDeviceConfig.Builder basicDeviceConfig =
        BasicDeviceConfig.newBuilder()
            .addAllOwner(oldDeviceConfig.getOwnerList())
            .addAllExecutor(oldDeviceConfig.getExecutorList());

    if (oldDeviceConfig.hasDefaultWifi()) {
      WifiConfig.Builder wifiConfig = WifiConfig.newBuilder();
      ProtoUtil.convert(oldDeviceConfig.getDefaultWifi(), wifiConfig);
      basicDeviceConfig.setDefaultWifi(wifiConfig);
    }

    if (oldDeviceConfig.hasCompositeDimension() || oldDeviceConfig.getDimensionCount() > 0) {
      DeviceCompositeDimension deviceCompositeDimension =
          oldDeviceConfig.getCompositeDimension().toBuilder()
              .addAllSupportedDimension(
                  StrPairUtil.convertToDeviceDimension(oldDeviceConfig.getDimensionList()))
              .build();
      basicDeviceConfig.setCompositeDimension(deviceCompositeDimension);
    }
    if (oldDeviceConfig.hasMaxConsecutiveTest()) {
      basicDeviceConfig.setMaxConsecutiveTest(
          Int32Value.of(oldDeviceConfig.getMaxConsecutiveTest()));
    } else if (maxConsecutiveTest != null) {
      basicDeviceConfig.setMaxConsecutiveTest(Int32Value.of(maxConsecutiveTest));
    }

    if (oldDeviceConfig.hasMaxConsecutiveFail()) {
      basicDeviceConfig.setMaxConsecutiveFail(
          Int32Value.of(oldDeviceConfig.getMaxConsecutiveFail()));
    } else if (maxConsecutiveFail != null) {
      basicDeviceConfig.setMaxConsecutiveFail(Int32Value.of(maxConsecutiveFail));
    }
    return DeviceConfig.newBuilder().setUuid(uuid).setBasicConfig(basicDeviceConfig).build();
  }

  /**
   * Merges the firstDeviceConfig and secondDeviceConfig.
   *
   * <p>It merges owner, supported dimensions and required dimensions. When do merge, the duplicated
   * items are removed. It also copied secondDeviceConfig's wifi setting to firstDeviceConfig if
   * it's not set in firstDeviceConfig.
   */
  public static BasicDeviceConfig mergeDeviceConfig(
      BasicDeviceConfig firstDeviceConfig, BasicDeviceConfig secondDeviceConfig) {
    BasicDeviceConfig.Builder firstDeviceConfigBuilder = firstDeviceConfig.toBuilder();
    firstDeviceConfigBuilder
        .clearOwner()
        .addAllOwner(
            Stream.concat(
                    firstDeviceConfig.getOwnerList().stream(),
                    secondDeviceConfig.getOwnerList().stream())
                .distinct()
                .collect(toImmutableList()))
        .clearExecutor()
        .addAllExecutor(
            Stream.concat(
                    firstDeviceConfig.getExecutorList().stream(),
                    secondDeviceConfig.getExecutorList().stream())
                .distinct()
                .collect(Collectors.toList()));
    if (firstDeviceConfig.hasCompositeDimension() || secondDeviceConfig.hasCompositeDimension()) {
      firstDeviceConfigBuilder
          .getCompositeDimensionBuilder()
          .clearRequiredDimension()
          .addAllRequiredDimension(
              Stream.concat(
                      firstDeviceConfig.getCompositeDimension().getRequiredDimensionList().stream(),
                      secondDeviceConfig
                          .getCompositeDimension()
                          .getRequiredDimensionList()
                          .stream())
                  .distinct()
                  .collect(Collectors.toList()))
          .clearSupportedDimension()
          .addAllSupportedDimension(
              Stream.concat(
                      firstDeviceConfig
                          .getCompositeDimension()
                          .getSupportedDimensionList()
                          .stream(),
                      secondDeviceConfig
                          .getCompositeDimension()
                          .getSupportedDimensionList()
                          .stream())
                  .distinct()
                  .collect(Collectors.toList()));
    }
    if (firstDeviceConfig.getDefaultWifi().getSsid().isEmpty()
        && !secondDeviceConfig.getDefaultWifi().getSsid().isEmpty()) {
      firstDeviceConfigBuilder.setDefaultWifi(secondDeviceConfig.getDefaultWifi());
    }
    return firstDeviceConfigBuilder.build();
  }

  /** Generates a old {@link Config.DeviceConfig} from the given {@link DeviceConfig}. */
  public static Config.DeviceConfig toOldDeviceConfig(
      DeviceConfig deviceConfig, String deviceControlId) {
    Config.DeviceConfig.Builder oldDeviceConfig =
        Config.DeviceConfig.newBuilder().setId(deviceControlId);
    BasicDeviceConfig basicConfig = deviceConfig.getBasicConfig();
    oldDeviceConfig.addAllOwner(basicConfig.getOwnerList());
    oldDeviceConfig.addAllExecutor(basicConfig.getExecutorList());
    if (basicConfig.hasDefaultWifi()) {
      Config.WifiConfig.Builder oldWifiConfig = Config.WifiConfig.newBuilder();
      ProtoUtil.convert(basicConfig.getDefaultWifi(), oldWifiConfig);
      oldDeviceConfig.setDefaultWifi(oldWifiConfig);
    }
    if (basicConfig.hasCompositeDimension()) {
      oldDeviceConfig.setCompositeDimension(
          basicConfig.getCompositeDimension().toBuilder().clearSupportedDimension().build());
      oldDeviceConfig.addAllDimension(
          StrPairUtil.convertFromDeviceDimension(
              basicConfig.getCompositeDimension().getSupportedDimensionList()));
    }
    if (basicConfig.hasMaxConsecutiveTest()) {
      oldDeviceConfig.setMaxConsecutiveTest(basicConfig.getMaxConsecutiveTest().getValue());
    }
    if (basicConfig.hasMaxConsecutiveFail()) {
      oldDeviceConfig.setMaxConsecutiveFail(basicConfig.getMaxConsecutiveFail().getValue());
    }
    return oldDeviceConfig.build();
  }
}
