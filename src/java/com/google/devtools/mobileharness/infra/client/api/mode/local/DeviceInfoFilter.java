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

package com.google.devtools.mobileharness.infra.client.api.mode.local;

import com.google.common.base.Ascii;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Filter for filtering {@link DeviceInfo} based on {@link DeviceQueryFilter}. */
public class DeviceInfoFilter implements Predicate<DeviceInfo> {

  private final DeviceQueryFilter deviceQueryFilter;

  public DeviceInfoFilter(DeviceQueryFilter deviceQueryFilter) {
    this.deviceQueryFilter = deviceQueryFilter;
  }

  @Override
  public boolean test(DeviceInfo deviceInfo) {
    return deviceQueryFilter.getOwnerRegexList().stream()
            .allMatch(
                ownerRegex ->
                    matchAttribute(
                        ownerRegex,
                        deviceInfo.getOwnerList().isEmpty()
                            ? Stream.of("public")
                            : deviceInfo.getOwnerList().stream()))
        && deviceQueryFilter.getTypeRegexList().stream()
            .allMatch(typeRegex -> matchAttribute(typeRegex, deviceInfo.getTypeList().stream()))
        && deviceQueryFilter.getDriverRegexList().stream()
            .allMatch(
                driverRegex -> matchAttribute(driverRegex, deviceInfo.getDriverList().stream()))
        && deviceQueryFilter.getDecoratorRegexList().stream()
            .allMatch(
                decoratorRegex ->
                    matchAttribute(decoratorRegex, deviceInfo.getDecoratorList().stream()))
        && (deviceQueryFilter.getStatusList().isEmpty()
            || deviceQueryFilter.getStatusList().stream()
                .anyMatch(status -> Ascii.equalsIgnoreCase(status, deviceInfo.getStatus())))
        && deviceQueryFilter.getDimensionFilterList().stream()
            .allMatch(
                dimensionFilter ->
                    matchAttribute(
                        dimensionFilter.getValueRegex(),
                        deviceInfo.getDimensionList().stream()
                            .filter(
                                deviceDimension ->
                                    deviceDimension.getName().equals(dimensionFilter.getName()))
                            .map(Dimension::getValue)));
  }

  private static boolean matchAttribute(String attributeRegex, Stream<String> attributes) {
    Pattern attributePattern = Pattern.compile(attributeRegex);
    return attributes.anyMatch(attribute -> attributePattern.matcher(attribute).matches());
  }
}
