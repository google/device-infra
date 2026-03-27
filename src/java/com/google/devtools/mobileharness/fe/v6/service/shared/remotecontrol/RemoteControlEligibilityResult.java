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
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DeviceProxyType;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.IneligibilityReasonCode;
import java.util.Optional;

/** Result of a remote control eligibility check. */
@AutoValue
public abstract class RemoteControlEligibilityResult {

  public abstract boolean isEligible();

  public abstract Optional<IneligibilityReasonCode> reasonCode();

  public abstract Optional<String> reasonMessage();

  public abstract ImmutableList<DeviceProxyType> supportedProxyTypes();

  public static Builder builder() {
    return new AutoValue_RemoteControlEligibilityResult.Builder()
        .setIsEligible(true)
        .setSupportedProxyTypes(ImmutableList.of());
  }

  /** Builder for {@link RemoteControlEligibilityResult}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setIsEligible(boolean isEligible);

    public abstract Builder setReasonCode(IneligibilityReasonCode reasonCode);

    public abstract Builder setReasonMessage(String reasonMessage);

    public abstract Builder setSupportedProxyTypes(
        ImmutableList<DeviceProxyType> supportedProxyTypes);

    public abstract RemoteControlEligibilityResult build();
  }
}
