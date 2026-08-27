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

import javax.inject.Inject;

/**
 * Device-search key registry for the standalone ATS (OSS) deployment.
 *
 * <p>Composes Group 1 (universal common device keys and projected common host keys) with Group 2
 * (Standalone ATS WiFi SSID).
 */
public final class AtsDeviceKeyRegistry extends DeviceKeyRegistry {

  @Inject
  AtsDeviceKeyRegistry() {
    super(AtsDeviceKeys.ATS_DEVICE_KEYS);
  }
}
