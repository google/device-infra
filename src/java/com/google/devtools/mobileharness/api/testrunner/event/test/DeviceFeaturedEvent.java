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

package com.google.devtools.mobileharness.api.testrunner.event.test;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;

/** Event which contains the feature information of the devices of a test. */
interface DeviceFeaturedEvent {

  DeviceFeature getDeviceFeature();

  /**
   * Gets {@link DeviceInfo} of all devices of the test.
   *
   * @since MH client 4.31.0 and MH lab 4.268.0
   */
  ImmutableList<DeviceInfo> getAllDeviceInfos();
}
