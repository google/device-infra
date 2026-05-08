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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import java.util.stream.Stream;

/** Wrapper around {@link DeviceQuerier} for allocation diagnostics. */
public class DiagnosticDeviceQuerier {

  private static final ImmutableList<FieldDescriptor> DEFAULT_DEVICE_INFO_FIELDS_TO_QUERY =
      ImmutableList.of(
              DeviceQuery.DeviceInfo.ID_FIELD_NUMBER,
              DeviceQuery.DeviceInfo.STATUS_FIELD_NUMBER,
              DeviceQuery.DeviceInfo.OWNER_FIELD_NUMBER,
              DeviceQuery.DeviceInfo.EXECUTOR_FIELD_NUMBER,
              DeviceQuery.DeviceInfo.TYPE_FIELD_NUMBER,
              DeviceQuery.DeviceInfo.DRIVER_FIELD_NUMBER,
              DeviceQuery.DeviceInfo.DIMENSION_FIELD_NUMBER)
          .stream()
          .map(fieldNumber -> DeviceQuery.DeviceInfo.getDescriptor().findFieldByNumber(fieldNumber))
          .collect(toImmutableList());

  private final DeviceQuerier deviceQuerier;

  public DiagnosticDeviceQuerier(DeviceQuerier deviceQuerier) {
    this.deviceQuerier = deviceQuerier;
  }

  public DeviceQuery.DeviceQueryResult queryDevice(
      JobScheduleUnit job, DeviceQuery.DeviceQueryFilter filter)
      throws MobileHarnessException, InterruptedException {
    return deviceQuerier.queryDevice(
        filter,
        getDeviceInfoFieldsToQuery(job),
        getDimensionNamesToQuery(job),
        ImmutableList.of(getJobDriver(job)),
        getJobDecorators(job));
  }

  private static ImmutableList<String> getDimensionNamesToQuery(JobScheduleUnit job) {
    return Stream.of(
            job.dimensions().getAll().keySet().stream(),
            job.subDeviceSpecs().getAllSubDevices().stream()
                .flatMap(subDevice -> subDevice.dimensions().getAll().keySet().stream()),
            Stream.of(
                Ascii.toLowerCase(Dimension.Name.HOST_IP.name()),
                Ascii.toLowerCase(Dimension.Name.HOST_NAME.name())))
        .flatMap(s -> s)
        .distinct()
        .collect(toImmutableList());
  }

  private static String getJobDriver(JobScheduleUnit job) {
    return job.type().getDriver();
  }

  private static ImmutableList<String> getJobDecorators(JobScheduleUnit job) {
    return Stream.concat(
            job.type().getDecoratorList().stream(),
            job.subDeviceSpecs().getAllSubDevices().stream()
                .flatMap(subDevice -> subDevice.decorators().getAll().stream()))
        .distinct()
        .collect(toImmutableList());
  }

  private static ImmutableList<FieldDescriptor> getDeviceInfoFieldsToQuery(JobScheduleUnit job) {
    ImmutableList.Builder<FieldDescriptor> deviceInfoFields = ImmutableList.builder();
    deviceInfoFields.addAll(DEFAULT_DEVICE_INFO_FIELDS_TO_QUERY);

    if (!getJobDecorators(job).isEmpty()) {
      deviceInfoFields.add(
          DeviceQuery.DeviceInfo.getDescriptor()
              .findFieldByNumber(DeviceQuery.DeviceInfo.DECORATOR_FIELD_NUMBER));
    }

    return deviceInfoFields.build();
  }
}
