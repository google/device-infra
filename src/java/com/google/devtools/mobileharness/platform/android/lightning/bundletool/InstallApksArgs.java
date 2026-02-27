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

package com.google.devtools.mobileharness.platform.android.lightning.bundletool;

import com.google.auto.value.AutoBuilder;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Arguments for {@code bundletool install-apks}.
 *
 * <p>See https://developer.android.com/tools/bundletool for detailed usage of each argument
 */
public record InstallApksArgs(
    Path apks,
    boolean allowDowngrade,
    boolean allowTestOnly,
    boolean grantRuntimePermissions,
    ImmutableList<String> deviceGroups,
    Duration commandTimeout,
    String deviceId,
    Optional<Duration> adbCommandTimeout) {

  ImmutableList<String> toBundletoolCommand(String adbPath) {
    ImmutableList.Builder<String> args =
        ImmutableList.<String>builder()
            .add("install-apks")
            .add("--adb=" + adbPath)
            .add("--apks=" + apks);
    if (allowDowngrade) {
      args.add("--allow-downgrade");
    }
    if (allowTestOnly) {
      args.add("--allow-test-only");
    }
    if (grantRuntimePermissions) {
      args.add("--grant-runtime-permissions");
    }
    if (!deviceGroups.isEmpty()) {
      args.add("--device-groups=" + Joiner.on(",").join(deviceGroups));
    }
    if (!deviceId.isEmpty()) {
      args.add("--device-id=" + deviceId);
    }
    adbCommandTimeout.ifPresent(timeout -> args.add("--timeout-millis=" + timeout.toMillis()));
    return args.build();
  }

  public static Builder builder() {
    return new AutoBuilder_InstallApksArgs_Builder()
        .setAllowDowngrade(false)
        .setAllowTestOnly(false)
        .setGrantRuntimePermissions(false)
        .setDeviceId("")
        .setCommandTimeout(Duration.ofMinutes(10));
  }

  /** Builder for {@link InstallApksArgs}. */
  @AutoBuilder
  public abstract static class Builder {

    /** The required Apk Set file to install. */
    public abstract Builder setApks(Path apks);

    /** Whether to allow downgrading the app (default false). */
    public abstract Builder setAllowDowngrade(boolean allowDowngrade);

    /** Whether to allow test only apps (default false). */
    public abstract Builder setAllowTestOnly(boolean allowTestOnly);

    /** Whether to grant runtime permissions (default false). */
    public abstract Builder setGrantRuntimePermissions(boolean grantRuntimePermissions);

    /** Device groups the device belongs to for matching modules (default empty). */
    public abstract ImmutableList.Builder<String> deviceGroupsBuilder();

    @CanIgnoreReturnValue
    public Builder addDeviceGroups(String deviceGroup) {
      deviceGroupsBuilder().add(deviceGroup);
      return this;
    }

    /** Device ID to install APKs on (default empty). */
    public abstract Builder setDeviceId(String deviceId);

    /** Timeout for ADB commands (default 10 minutes). */
    public abstract Builder setAdbCommandTimeout(Duration adbCommandTimeout);

    /** Timeout for the Bundletoolcommand (default 10 minutes). */
    public abstract Builder setCommandTimeout(Duration commandTimeout);

    public abstract InstallApksArgs build();
  }
}
