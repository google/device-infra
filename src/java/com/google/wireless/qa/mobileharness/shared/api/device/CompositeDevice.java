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

package com.google.wireless.qa.mobileharness.shared.api.device;

import com.google.common.collect.ImmutableSet;

/** A logical {@link Device} which represents multiple physical devices. */
@Deprecated
public interface CompositeDevice extends Device {

  /**
   * Gets the subdevices managed by this {@link CompositeDevice}.
   *
   * <p>The devices may be of any type and do not have to all be of the same type.
   *
   * @return an ImmutableSet of devices managed by this {@link CompositeDevice}. Note that
   *     ImmutableSet preserves the order of entries.
   */
  ImmutableSet<Device> getManagedDevices();
}
