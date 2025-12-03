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

package com.google.devtools.mobileharness.fe.v6.service.host.provider;

import com.google.auto.value.AutoValue;
import java.time.Instant;
import java.util.Optional;

/** Release information for a host, including lab and daemon server details. */
@AutoValue
public abstract class HostReleaseInfo {

  /** Details about a specific server component's release. */
  @AutoValue
  public abstract static class ComponentInfo {
    public abstract Optional<String> version();

    public abstract Optional<String> status(); // Raw status from HostInfoService

    public abstract Optional<Instant> updateTime();

    public static Builder builder() {
      return new AutoValue_HostReleaseInfo_ComponentInfo.Builder();
    }

    /** Builder for {@link ComponentInfo}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setVersion(String value);

      public abstract Builder setStatus(String value);

      public abstract Builder setUpdateTime(Instant value);

      public abstract ComponentInfo build();
    }
  }

  /** Release info for the Lab Server component. */
  public abstract Optional<ComponentInfo> labServerReleaseInfo();

  /** Release info for the Daemon Server component. */
  public abstract Optional<ComponentInfo> daemonServerReleaseInfo();

  /** The lab type of the host, from HostInfoService's HostAttributes.LabType enum name. */
  public abstract Optional<String> labType();

  public static Builder builder() {
    return new AutoValue_HostReleaseInfo.Builder()
        .setLabServerReleaseInfo(Optional.empty())
        .setDaemonServerReleaseInfo(Optional.empty())
        .setLabType(Optional.empty());
  }

  /** Builder for {@link HostReleaseInfo}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setLabServerReleaseInfo(Optional<ComponentInfo> value);

    public abstract Builder setDaemonServerReleaseInfo(Optional<ComponentInfo> value);

    public abstract Builder setLabType(Optional<String> value);

    public abstract HostReleaseInfo build();
  }
}
