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

package com.google.devtools.mobileharness.infra.ats.common;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils.TokenizationException;
import java.util.List;

/** Utility for device infra service. */
public class DeviceInfraServiceUtil {

  private static final String DEVICE_INFRA_SERVICE_FLAGS_PROPERTY_KEY =
      "DEVICE_INFRA_SERVICE_FLAGS";

  /**
   * Parses device infra service flags from system property.
   *
   * @implSpec do not use logger since flags has not been parsed
   */
  public static FlagsString getDeviceInfraServiceFlagsFromSystemProperty() {
    String property = System.getProperties().getProperty(DEVICE_INFRA_SERVICE_FLAGS_PROPERTY_KEY);
    if (isNullOrEmpty(property)) {
      return FlagsString.of("", ImmutableList.of());
    }
    try {
      return FlagsString.of(property, ShellUtils.tokenize(property));
    } catch (TokenizationException e) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid device infra flags property value, key=[%s], value=[%s]",
              DEVICE_INFRA_SERVICE_FLAGS_PROPERTY_KEY, property),
          e);
    }
  }

  /**
   * Parses the list of device infra service flag strings and saves values to registered flags.
   *
   * @param flags the list of flag strings
   * @implSpec do not use logger since flags has not been parsed
   */
  public static void parseFlags(List<String> flags) {
    String[] flagArray = flags.toArray(new String[0]);
    Flags.parse(flagArray);
  }

  private DeviceInfraServiceUtil() {}
}
