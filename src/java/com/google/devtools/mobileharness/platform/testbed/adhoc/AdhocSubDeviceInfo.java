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

package com.google.devtools.mobileharness.platform.testbed.adhoc;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.testbed.config.BaseSubDeviceInfo;
import com.google.devtools.mobileharness.platform.testbed.config.SubDeviceInfo;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.Map;

/** The {@link SubDeviceInfo} class used in ad hoc testbed creation. */
class AdhocSubDeviceInfo extends BaseSubDeviceInfo {

  AdhocSubDeviceInfo(
      String id,
      Class<? extends Device> deviceType,
      Multimap<String, String> dimensions,
      Map<String, Object> properties)
      throws MobileHarnessException {
    super(id, deviceType, ImmutableSet.of(), dimensions, properties);
  }
}
