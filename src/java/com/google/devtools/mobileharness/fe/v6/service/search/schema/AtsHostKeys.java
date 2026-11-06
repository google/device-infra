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

package com.google.devtools.mobileharness.fe.v6.service.search.schema;

import com.google.common.collect.ImmutableList;

/**
 * Standalone ATS (OSS) exclusive host key descriptors (Group 2).
 *
 * <p>Kept separate from {@link HostKeys} so that catalog holds only the universal common keys and
 * the shared helper methods; this catalog holds the keys that exist only in the standalone ATS
 * deployment.
 */
public final class AtsHostKeys {

  /**
   * Default Wi-Fi SSID configured on the host ({@code LabConfig.wifi_config}), fed by ConfigService
   * so it contributes no {@code GetLabInfo} mask.
   */
  public static final HostKeyDescriptor DEFAULT_WIFI =
      HostKeyDescriptor.builder()
          .setId(HostKeys.PREFIX_HOST_FIELD + "default_wifi")
          .setDisplay(KeyDisplay.of("Default Wi-Fi"))
          .setAliases(
              "wifi",
              "wi-fi",
              "default wifi",
              "default wi-fi",
              "wifi ssid",
              "wi-fi ssid",
              "ssid",
              "host wifi")
          .build();

  /** Group 2: Standalone ATS exclusive host keys. */
  public static final ImmutableList<HostKeyDescriptor> ATS_HOST_KEYS =
      ImmutableList.of(DEFAULT_WIFI);

  private AtsHostKeys() {}
}
