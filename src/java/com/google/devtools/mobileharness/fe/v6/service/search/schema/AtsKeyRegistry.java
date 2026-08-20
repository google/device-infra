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
import javax.inject.Inject;

/**
 * Key registry for the standalone ATS (OSS) deployment.
 *
 * <p>Composes Group 1 (Universal Common Keys) and Group 2 (Standalone ATS WiFi SSID).
 */
public final class AtsKeyRegistry extends KeyRegistry {

  /** Group 2: Standalone ATS exclusive keys. */
  private static final ImmutableList<DeviceKeyDescriptor> ATS_EXTRA_DEVICE_KEYS =
      ImmutableList.of(DeviceKeys.WIFI_SSID);

  @Inject
  AtsKeyRegistry() {
    super(ATS_EXTRA_DEVICE_KEYS, ImmutableList.of());
  }
}
