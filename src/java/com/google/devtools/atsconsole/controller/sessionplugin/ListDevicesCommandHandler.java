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

package com.google.devtools.atsconsole.controller.sessionplugin;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.devtools.atsconsole.controller.proto.DeviceDescriptorProto.DeviceDescriptor;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.ListDevicesCommand;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/** Handler for "list devices" commands. */
class ListDevicesCommandHandler {

  private static final ImmutableList<String> HEADERS =
      ImmutableList.of("Serial", "State", "Allocation", "Product", "Variant", "Build", "Battery");
  private static final ImmutableList<String> HEADERS_ALL =
      ImmutableList.<String>builder().addAll(HEADERS).add("class", "TestDeviceState").build();
  private static final Comparator<DeviceDescriptor> DEVICE_DESCRIPTOR_COMPARATOR =
      Comparator.comparing(DeviceDescriptor::getAllocationState)
          .thenComparing(DeviceDescriptor::getSerial);

  AtsSessionPluginOutput handle(ListDevicesCommand command) {
    boolean listAllDevices = command.getListAllDevices();
    ImmutableList<String> headers = listAllDevices ? HEADERS_ALL : HEADERS;
    ImmutableList<DeviceDescriptor> devices = listDevices();
    ImmutableList<ImmutableList<String>> table =
        Stream.concat(
                Stream.of(headers),
                devices.stream()
                    .sorted(DEVICE_DESCRIPTOR_COMPARATOR)
                    .map(device -> formatDeviceDescriptor(device, listAllDevices))
                    .flatMap(Optional::stream))
            .collect(toImmutableList());
    String result = formatTable(table);
    return AtsSessionPluginOutput.newBuilder()
        .setSuccess(Success.newBuilder().setOutputMessage(result))
        .build();
  }

  @VisibleForTesting
  ImmutableList<DeviceDescriptor> listDevices() {
    return ImmutableList.of();
  }

  /** See com.android.tradefed.device.DeviceManager. */
  private static Optional<ImmutableList<String>> formatDeviceDescriptor(
      DeviceDescriptor deviceDescriptor, boolean listAllDevices) {
    if (!listAllDevices
        && deviceDescriptor.getIsStubDevice()
        && !deviceDescriptor.getAllocationState().equals("Allocated")) {
      return Optional.empty();
    }
    ImmutableList.Builder<String> result = ImmutableList.builder();
    String serial =
        deviceDescriptor.getDisplaySerial().isEmpty()
            ? deviceDescriptor.getSerial()
            : deviceDescriptor.getDisplaySerial();
    result.add(
        serial,
        deviceDescriptor.getDeviceState(),
        deviceDescriptor.getAllocationState(),
        deviceDescriptor.getProduct(),
        deviceDescriptor.getProductVariant(),
        deviceDescriptor.getBuildId(),
        deviceDescriptor.getBatteryLevel());
    if (listAllDevices) {
      result.add(deviceDescriptor.getDeviceClass(), deviceDescriptor.getTestDeviceState());
    }
    return Optional.of(result.build());
  }

  private static String formatTable(ImmutableList<ImmutableList<String>> table) {
    return table.stream().map(row -> String.join("\t", row)).collect(joining("\n"));
  }
}
