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

package com.google.devtools.mobileharness.infra.client.api.controller.device;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.Beta;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.LabInfo;
import com.google.wireless.qa.mobileharness.shared.proto.DeviceQuery.DeviceFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import java.util.List;
import javax.annotation.Nullable;

/** Device querier for querying the information of the available devices/labs. */
public interface DeviceQuerier {
  /**
   * Queries device information. It is highly recommended to provide the device filter to reduce the
   * size of the query result and improve the performance.
   *
   * @param deviceFilter if provided, only the devices that matched this filter will be returned
   * @return the information of the devices grouped by labs
   * @throws MobileHarnessException if failed to query the device info
   * @throws InterruptedException if the current thread is interrupted
   * @deprecated Use {@link #queryDevice(DeviceQueryFilter)} instead
   */
  @Deprecated
  default List<LabInfo> getDeviceInfos(@Nullable DeviceFilter deviceFilter)
      throws MobileHarnessException, InterruptedException {
    throw new UnsupportedOperationException();
  }

  /**
   * Queries device information.
   *
   * <p>It is highly recommended to set the device query filter to reduce the size of the query
   * result and improve the performance.
   *
   * @see DeviceQueryFilter
   */
  DeviceQueryResult queryDevice(DeviceQueryFilter deviceQueryFilter)
      throws MobileHarnessException, InterruptedException;

  /** Queries device information asyncly. */
  ListenableFuture<DeviceQueryResult> queryDeviceAsync(DeviceQueryFilter deviceQueryFilter)
      throws MobileHarnessException, InterruptedException;

  /**
   * Queries device information and returns available devices grouped by lab.
   *
   * <p>NOTE: Please do not use this method. The Lab/Device RPC API is going to be updated in the
   * near future and this method will likely not be supported after the update.
   */
  @Beta
  List<LabQueryResult> queryDevicesByLab(DeviceQueryFilter filter)
      throws MobileHarnessException, InterruptedException;

  /** Device results for a single lab host. */
  @AutoValue
  abstract class LabQueryResult {
    /** The hostname of the lab host this set of results is for. */
    public abstract String hostname();

    /** The devices available at this lab host. */
    public abstract ImmutableList<DeviceInfo> devices();

    public static LabQueryResult create(
        String hostname, String ipAddress, DeviceQueryResult deviceQueryResult) {
      return new AutoValue_DeviceQuerier_LabQueryResult(
          hostname,
          deviceQueryResult.getDeviceInfoList().stream()
              .map(device -> addHostAndIpIfMissing(device, hostname, ipAddress))
              .map(device -> new DeviceInfo(device))
              .collect(ImmutableList.toImmutableList()));
    }

    public static LabQueryResult create(String hostname, List<DeviceInfo> devices) {
      return new AutoValue_DeviceQuerier_LabQueryResult(hostname, ImmutableList.copyOf(devices));
    }

    private static DeviceQuery.DeviceInfo addHostAndIpIfMissing(
        DeviceQuery.DeviceInfo deviceInfo, String hostname, String ipAddress) {
      DeviceQuery.DeviceInfo.Builder builder = deviceInfo.toBuilder();
      if (!Strings.isNullOrEmpty(ipAddress) && !containsDimension(deviceInfo, Name.HOST_IP)) {
        builder.addDimension(
            Dimension.newBuilder()
                .setName(Ascii.toLowerCase(Name.HOST_IP.name()))
                .setValue(ipAddress)
                .setRequired(false)
                .build());
      }
      if (!Strings.isNullOrEmpty(hostname) && !containsDimension(deviceInfo, Name.HOST_NAME)) {
        builder.addDimension(
            Dimension.newBuilder()
                .setName(Ascii.toLowerCase(Name.HOST_NAME.name()))
                .setValue(hostname)
                .setRequired(false)
                .build());
      }
      return builder.build();
    }

    private static boolean containsDimension(
        DeviceQuery.DeviceInfo deviceInfo, Name dimensionName) {
      String lowerCaseName = Ascii.toLowerCase(dimensionName.toString());
      for (Dimension dimension : deviceInfo.getDimensionList()) {
        if (lowerCaseName.equals(Ascii.toLowerCase(dimension.getName()))) {
          return true;
        }
      }
      return false;
    }
  }
}
