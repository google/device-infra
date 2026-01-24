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

package com.google.devtools.mobileharness.infra.controller.device.config;

import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;

/** Interface for merging device configs. */
public interface BasicDeviceConfigMerger {

  /**
   * Whether the merger is enabled.
   *
   * <p>Do not call {@link #merge(BasicDeviceConfigSet)} if this method returns false.
   */
  default boolean enabled() {
    return false;
  }

  /**
   * Merges the given device configs.
   *
   * <p>Do not call this method if {@link #enabled()} is false. The output is undefined.
   */
  default BasicDeviceConfig merge(BasicDeviceConfigSet basicDeviceConfigSet) {
    return BasicDeviceConfig.getDefaultInstance();
  }
}
