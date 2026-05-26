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

package com.google.devtools.mobileharness.infra.ats.console.util.command;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.controller.device.proto.DeviceFilterSetting;
import com.google.devtools.mobileharness.infra.controller.device.proto.DeviceListFilter;
import com.google.devtools.mobileharness.infra.controller.device.proto.ExplicitFilter;
import com.google.devtools.mobileharness.infra.controller.device.proto.Universal;
import java.util.List;

/** Utility to parse device allowlist and blocklist parameters from console arguments. */
public final class DeviceFilterParser {

  private static final String ALLOWLIST_OPTION = "--xts_device_allowlist=";
  private static final String BLOCKLIST_OPTION = "--xts_device_blocklist=";
  private static final String UNIVERSAL_SET_WILDCARD = "*";

  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

  /** Structured parse result containing the device filter settings and remaining command args. */
  public record DeviceFilterInfo(
      DeviceFilterSetting deviceFilterSetting, ImmutableList<String> remainingArgs) {}

  /**
   * Parses the device allowlist and blocklist parameters from the given arguments, and returns a
   * {@link DeviceFilterInfo} containing the settings and remaining arguments.
   */
  public static DeviceFilterInfo parse(List<String> args) {
    ImmutableList.Builder<String> remainingArgs = ImmutableList.builder();
    List<String> allowlist = null;
    List<String> blocklist = null;

    for (String arg : args) {
      if (arg.startsWith(ALLOWLIST_OPTION)) {
        String value = arg.substring(ALLOWLIST_OPTION.length());
        allowlist = COMMA_SPLITTER.splitToList(value);
      } else if (arg.startsWith(BLOCKLIST_OPTION)) {
        String value = arg.substring(BLOCKLIST_OPTION.length());
        blocklist = COMMA_SPLITTER.splitToList(value);
      } else {
        remainingArgs.add(arg);
      }
    }

    DeviceFilterSetting.Builder settingBuilder = DeviceFilterSetting.newBuilder();
    if (allowlist != null) {
      settingBuilder.setAllowlistFilter(createDeviceListFilter(allowlist, ALLOWLIST_OPTION));
    }
    if (blocklist != null) {
      settingBuilder.setBlocklistFilter(createDeviceListFilter(blocklist, BLOCKLIST_OPTION));
    }

    return new DeviceFilterInfo(settingBuilder.build(), remainingArgs.build());
  }

  private static DeviceListFilter createDeviceListFilter(List<String> ids, String optionName) {
    if (ids.contains(UNIVERSAL_SET_WILDCARD)) {
      checkArgument(
          ids.size() == 1,
          "Wildcard '*' cannot be mixed with other device IDs inside %s, got: %s",
          optionName,
          ids);
      return DeviceListFilter.newBuilder().setUniversal(Universal.getDefaultInstance()).build();
    }
    return DeviceListFilter.newBuilder()
        .setExplicitFilter(ExplicitFilter.newBuilder().addAllControlIds(ids))
        .build();
  }

  private DeviceFilterParser() {}
}
