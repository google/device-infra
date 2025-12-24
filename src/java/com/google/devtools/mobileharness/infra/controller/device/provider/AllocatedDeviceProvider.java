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

package com.google.devtools.mobileharness.infra.controller.device.provider;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.DeviceInfo;
import com.google.devtools.mobileharness.api.model.lab.LiteDeviceInfoFactory;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.DeviceFactory;
import java.util.List;
import javax.inject.Inject;

/** Provides devices created from DeviceFeatures. */
public class AllocatedDeviceProvider implements DeviceProvider {

  private final DeviceFactory deviceFactory;

  @Inject
  AllocatedDeviceProvider(DeviceFactory deviceFactory) {
    this.deviceFactory = deviceFactory;
  }

  @Override
  public ImmutableList<Device> getDevices(
      List<String> deviceIds, List<DeviceFeature> deviceFeatures) throws MobileHarnessException {
    if (deviceIds.size() != deviceFeatures.size()) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_DEVICE_PROVIDER_DEVICE_FEATURE_SIZE_MISMATCH,
          "deviceIds and deviceFeatures size mismatch");
    }
    ImmutableList.Builder<Device> devices = ImmutableList.builder();
    for (int i = 0; i < deviceIds.size(); i++) {
      devices.add(createDeviceFromFeature(deviceIds.get(i), deviceFeatures.get(i)));
    }
    return devices.build();
  }

  private Device createDeviceFromFeature(String deviceId, DeviceFeature feature)
      throws MobileHarnessException {
    String deviceClassName =
        feature.getCompositeDimension().getSupportedDimensionList().stream()
            .filter(d -> d.getName().equals("device_class_name"))
            .findFirst()
            .map(DeviceDimension::getValue)
            .orElseThrow(
                () ->
                    new MobileHarnessException(
                        InfraErrorId.LAB_DEVICE_PROVIDER_GET_DEVICE_ERROR,
                        "Device class_name not found in dimensions"));
    Class<? extends Device> deviceClass = ClassUtil.getDeviceClass(deviceClassName);
    DeviceInfo isolatedInfo = LiteDeviceInfoFactory.create(deviceId);
    feature
        .getCompositeDimension()
        .getSupportedDimensionList()
        .forEach(d -> isolatedInfo.dimensions().supported().add(d.getName(), d.getValue()));
    feature
        .getCompositeDimension()
        .getRequiredDimensionList()
        .forEach(d -> isolatedInfo.dimensions().required().add(d.getName(), d.getValue()));
    feature
        .getProperties()
        .getPropertyList()
        .forEach(p -> isolatedInfo.properties().put(p.getName(), p.getValue()));

    return deviceFactory.createDevice(deviceClass, deviceId, isolatedInfo);
  }
}
