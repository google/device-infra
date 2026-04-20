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

package com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;

/** Context for remote control eligibility calculation. */
@AutoValue
public abstract class RemoteControlEligibilityContext {

  public abstract boolean isMultipleSelection();

  public abstract boolean isSubDevice();

  public abstract boolean hasCommSubDevice();

  public abstract DeviceStatus deviceStatus();

  public abstract ImmutableSet<String> drivers();

  public abstract ImmutableSet<String> types();

  public abstract ImmutableMap<String, String> dimensions();

  public abstract String username();

  public abstract ImmutableList<String> ownersAndExecutors();

  public static Builder builder() {
    return new AutoValue_RemoteControlEligibilityContext.Builder()
        .setIsMultipleSelection(false)
        .setIsSubDevice(false)
        .setHasCommSubDevice(false)
        .setDeviceStatus(DeviceStatus.IDLE)
        .setDrivers(ImmutableSet.of())
        .setTypes(ImmutableSet.of())
        .setDimensions(ImmutableMap.of())
        .setUsername("")
        .setOwnersAndExecutors(ImmutableList.of());
  }

  /** Builder for {@link RemoteControlEligibilityContext}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setIsMultipleSelection(boolean isMultipleSelection);

    public abstract Builder setIsSubDevice(boolean isSubDevice);

    public abstract Builder setHasCommSubDevice(boolean hasCommSubDevice);

    public abstract Builder setDeviceStatus(DeviceStatus deviceStatus);

    public abstract Builder setDrivers(ImmutableSet<String> drivers);

    public abstract Builder setTypes(ImmutableSet<String> types);

    public abstract Builder setDimensions(ImmutableMap<String, String> dimensions);

    public abstract Builder setUsername(String username);

    public abstract Builder setOwnersAndExecutors(ImmutableList<String> ownersAndExecutors);

    public abstract RemoteControlEligibilityContext build();
  }
}
